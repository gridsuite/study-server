/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

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

    public StudyImportService(StudyService studyService, StudyRepository studyRepository, RootNetworkService rootNetworkService,
                              NetworkModificationService networkModificationService, CaseService caseService, NotificationService notificationService) {
        this.studyService = studyService;
        this.studyRepository = studyRepository;
        this.rootNetworkService = rootNetworkService;
        this.networkModificationService = networkModificationService;
        this.caseService = caseService;
        this.notificationService = notificationService;
    }

    public void importStudyWithCaseImportAction(TreeExportInfos treeExportInfos, String userId) {
        if (treeExportInfos.rootNetworks().isEmpty()) {
            throw new StudyException(NOT_FOUND, "No root network found in import archive");
        }
        List<RootNetworkInfos> orderedRootNetworks = treeExportInfos.rootNetworks().stream()
                .sorted(Comparator.comparing(RootNetworkExportInfos::index))
                .map(this::toRootNetworkInfos)
                .toList();

        Map<UUID, UUID> modificationGroupUuidMapping = duplicateModificationGroups(treeExportInfos.nodeTree());

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

    private Map<UUID, UUID> duplicateModificationGroups(NodeTreeExportInfos nodeTree) {
        Map<UUID, UUID> modificationGroupUuidMapping = new HashMap<>();
        if (nodeTree == null) {
            return modificationGroupUuidMapping;
        }
        try {
            CollectionUtils.emptyIfNull(nodeTree.children()).forEach(child -> duplicateModificationGroupsRecursively(child, modificationGroupUuidMapping));
        } catch (Exception e) {
            modificationGroupUuidMapping.values().forEach(newGroupUuid -> {
                try {
                    networkModificationService.deleteModifications(newGroupUuid);
                } catch (Exception cleanupException) {
                    LOGGER.error(String.format("Could not clean up orphaned modification group '%s' after import failure", newGroupUuid), cleanupException);
                }
            });
            throw e;
        }
        return modificationGroupUuidMapping;
    }

    private void duplicateModificationGroupsRecursively(NodeTreeExportInfos exportNode, Map<UUID, UUID> modificationGroupUuidMapping) {
        studyService.toNetworkModificationNodeType(exportNode.nodeType());
        if (exportNode.modificationGroupUuid() != null) {
            UUID newGroupUuid = UUID.randomUUID();
            networkModificationService.duplicateModificationsGroup(exportNode.modificationGroupUuid(), newGroupUuid);
            modificationGroupUuidMapping.put(exportNode.modificationGroupUuid(), newGroupUuid);
        }
        CollectionUtils.emptyIfNull(exportNode.children()).forEach(child -> duplicateModificationGroupsRecursively(child, modificationGroupUuidMapping));
    }
}
