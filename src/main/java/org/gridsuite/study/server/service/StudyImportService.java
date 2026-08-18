/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.studyexport.NetworkModificationImportInfos;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.gridsuite.study.server.service.StudyExportService.NETWORK_MODIFICATIONS_JSON;
import static org.gridsuite.study.server.service.StudyExportService.NETWORK_MODIFICATION_FILTERS_JSON;
import static org.gridsuite.study.server.service.StudyExportService.NETWORK_MODIFICATION_LOAD_FLOW_PARAMETERS_JSON;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class StudyImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyImportService.class);

    private final StudyService studyService;
    private final StudyRepository studyRepository;
    private final RootNetworkService rootNetworkService;
    private final NetworkModificationService networkModificationService;
    private final CaseService caseService;
    private final NotificationService notificationService;
    private final LoadFlowRestService loadFlowRestService;
    private final ObjectMapper objectMapper;

    public StudyImportService(StudyService studyService, StudyRepository studyRepository, RootNetworkService rootNetworkService,
                              NetworkModificationService networkModificationService, CaseService caseService, NotificationService notificationService,
                              LoadFlowRestService loadFlowRestService, ObjectMapper objectMapper) {
        this.studyService = studyService;
        this.studyRepository = studyRepository;
        this.rootNetworkService = rootNetworkService;
        this.networkModificationService = networkModificationService;
        this.caseService = caseService;
        this.notificationService = notificationService;
        this.loadFlowRestService = loadFlowRestService;
        this.objectMapper = objectMapper;
    }

    public void importStudy(TreeExportInfos treeExportInfos, MultipartFile modificationsArchive, String userId) {
        if (treeExportInfos.rootNetworks().isEmpty()) {
            throw new StudyException(NOT_FOUND, "No root network found in import archive");
        }
        List<RootNetworkInfos> orderedRootNetworks = treeExportInfos.rootNetworks().stream()
                .sorted(Comparator.comparing(RootNetworkExportInfos::index))
                .map(this::toRootNetworkInfos)
                .toList();

        NetworkModificationsArchiveContent archiveContent = readModificationsArchive(modificationsArchive);
        Map<UUID, UUID> modificationGroupUuidMapping = importModificationGroups(treeExportInfos.nodeTree(), archiveContent);

        StudyEntity studyEntity = studyService.createStudyEntityWithTree(treeExportInfos.studyUuid(), userId, treeExportInfos.nodeTree(), modificationGroupUuidMapping);
        studyEntity.setRootNetworkOrder(orderedRootNetworks.stream().map(RootNetworkInfos::getId).toList());
        studyRepository.save(studyEntity);

        notificationService.emitStudyCreationStarted(studyEntity.getId(), userId);
        int successfulRequests = 0;
        for (RootNetworkInfos rootNetworkInfos : orderedRootNetworks) {
            try {
                caseService.assertCaseExists(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid());
                studyService.createRootNetworkRequest(studyEntity.getId(), rootNetworkInfos, userId, CaseImportAction.ROOT_NETWORK_CREATION_FOR_STUDY_IMPORT);
                successfulRequests++;
            } catch (Exception e) {
                LOGGER.error(String.format("Could not request root network '%s' for imported study '%s'", rootNetworkInfos.getName(), studyEntity.getId()), e);
            }
        }
        if (successfulRequests == 0) {
            studyService.deleteStudyIfNotCreationInProgress(studyEntity.getId(), userId);
            notificationService.emitStudyCreationError(studyEntity.getId(), userId, "Could not request any root network for imported study");
        }
    }

    public void checkFinishedStudyImport(UUID studyUuid, String userId) {
        if (rootNetworkService.countRootNetworkCreationRequests(studyUuid) == 0) {
            studyRepository.findById(studyUuid).ifPresent(studyEntity -> {
                studyEntity.setRootNetworkOrder(null);
                studyRepository.save(studyEntity);
            });
            notificationService.emitStudyCreationFinished(studyUuid, userId);
        }
    }

    private RootNetworkInfos toRootNetworkInfos(RootNetworkExportInfos rootNetworkExportInfos) {
        CaseInfos caseInfos = rootNetworkExportInfos.caseInfos();
        return RootNetworkInfos.builder()
                .id(UUID.randomUUID())
                .name(rootNetworkExportInfos.name())
                .tag(rootNetworkExportInfos.tag())
                .caseInfos(new CaseInfos(null, caseInfos.getCaseUuid(), caseInfos.getCaseName(), caseInfos.getCaseFormat()))
                .importParameters(rootNetworkExportInfos.importParameters())
                .build();
    }

    private record NetworkModificationsArchiveContent(
            Map<UUID, List<JsonNode>> modificationsByGroup,
            Map<UUID, AbstractFilter> filtersByOldId,
            Map<UUID, JsonNode> loadFlowParametersByOldId) {
    }

    private record ModificationsGroupExport(@JsonProperty("modifications") List<JsonNode> modifications) {
    }

    private NetworkModificationsArchiveContent readModificationsArchive(MultipartFile modificationsArchive) {
        Map<String, byte[]> entriesByName = new HashMap<>();
        try (ZipInputStream zipIn = new ZipInputStream(modificationsArchive.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entriesByName.put(entry.getName(), zipIn.readAllBytes());
            }
        } catch (IOException e) {
            throw new StudyException(NOT_FOUND, "Could not read modifications archive: " + e.getMessage());
        }
        try {
            Map<UUID, ModificationsGroupExport> modificationsRoot = entriesByName.containsKey(NETWORK_MODIFICATIONS_JSON)
                    ? objectMapper.readValue(entriesByName.get(NETWORK_MODIFICATIONS_JSON), new TypeReference<Map<UUID, ModificationsGroupExport>>() { })
                    : Map.of();
            Map<UUID, List<JsonNode>> modificationsByGroup = modificationsRoot.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().modifications() == null ? List.of() : entry.getValue().modifications()));

            Map<UUID, AbstractFilter> filtersByOldId = entriesByName.containsKey(NETWORK_MODIFICATION_FILTERS_JSON)
                    ? objectMapper.readValue(entriesByName.get(NETWORK_MODIFICATION_FILTERS_JSON), new TypeReference<Map<UUID, AbstractFilter>>() { })
                    : Map.of();

            Map<UUID, JsonNode> loadFlowParametersByOldId = entriesByName.containsKey(NETWORK_MODIFICATION_LOAD_FLOW_PARAMETERS_JSON)
                    ? objectMapper.readValue(entriesByName.get(NETWORK_MODIFICATION_LOAD_FLOW_PARAMETERS_JSON), new TypeReference<Map<UUID, JsonNode>>() { })
                    : Map.of();

            return new NetworkModificationsArchiveContent(modificationsByGroup, filtersByOldId, loadFlowParametersByOldId);
        } catch (IOException e) {
            throw new StudyException(NOT_FOUND, "Invalid modifications archive content: " + e.getMessage());
        }
    }

    private Map<UUID, UUID> importModificationGroups(NodeTreeExportInfos nodeTree, NetworkModificationsArchiveContent archiveContent) {
        Map<UUID, UUID> modificationGroupUuidMapping = new HashMap<>();
        if (nodeTree == null) {
            return modificationGroupUuidMapping;
        }
        List<UUID> createdLoadFlowParametersIds = new ArrayList<>();
        try {
            Map<UUID, UUID> loadFlowParametersIdMapping = recreateLoadFlowParameters(archiveContent.loadFlowParametersByOldId(), createdLoadFlowParametersIds);
            CollectionUtils.emptyIfNull(nodeTree.children()).forEach(child ->
                    importModificationGroupsRecursively(child, archiveContent, loadFlowParametersIdMapping, modificationGroupUuidMapping));
        } catch (Exception e) {
            modificationGroupUuidMapping.values().forEach(newGroupUuid -> {
                try {
                    networkModificationService.deleteModifications(newGroupUuid);
                } catch (Exception cleanupException) {
                    LOGGER.error(String.format("Could not clean up orphaned modification group '%s' after import failure", newGroupUuid), cleanupException);
                }
            });
            createdLoadFlowParametersIds.forEach(loadFlowParametersId -> {
                try {
                    loadFlowRestService.deleteParameters(loadFlowParametersId);
                } catch (Exception cleanupException) {
                    LOGGER.error(String.format("Could not clean up orphaned load flow parameters '%s' after import failure", loadFlowParametersId), cleanupException);
                }
            });
            throw e;
        }
        return modificationGroupUuidMapping;
    }

    private Map<UUID, UUID> recreateLoadFlowParameters(Map<UUID, JsonNode> loadFlowParametersByOldId, List<UUID> createdLoadFlowParametersIds) {
        Map<UUID, UUID> loadFlowParametersIdMapping = new LinkedHashMap<>();
        for (Map.Entry<UUID, JsonNode> entry : loadFlowParametersByOldId.entrySet()) {
            UUID newLoadFlowParametersId = loadFlowRestService.createLoadFlowParameters(entry.getValue().toString());
            createdLoadFlowParametersIds.add(newLoadFlowParametersId);
            loadFlowParametersIdMapping.put(entry.getKey(), newLoadFlowParametersId);
        }
        return loadFlowParametersIdMapping;
    }

    private void importModificationGroupsRecursively(NodeTreeExportInfos exportNode, NetworkModificationsArchiveContent archiveContent,
                                                      Map<UUID, UUID> loadFlowParametersIdMapping, Map<UUID, UUID> modificationGroupUuidMapping) {
        studyService.toNetworkModificationNodeType(exportNode.nodeType());
        if (exportNode.modificationGroupUuid() != null) {
            UUID oldGroupUuid = exportNode.modificationGroupUuid();
            UUID newGroupUuid = UUID.randomUUID();
            List<JsonNode> modifications = archiveContent.modificationsByGroup().getOrDefault(oldGroupUuid, List.of());
            NetworkModificationImportInfos importInfos = new NetworkModificationImportInfos(modifications, archiveContent.filtersByOldId(), loadFlowParametersIdMapping);
            networkModificationService.importNetworkModifications(newGroupUuid, importInfos);
            modificationGroupUuidMapping.put(oldGroupUuid, newGroupUuid);
        }
        CollectionUtils.emptyIfNull(exportNode.children()).forEach(child ->
                importModificationGroupsRecursively(child, archiveContent, loadFlowParametersIdMapping, modificationGroupUuidMapping));
    }
}
