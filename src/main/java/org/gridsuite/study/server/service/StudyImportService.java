/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkLoadStatus;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void importStudy(TreeExportInfos treeExportInfos, String userId) {
        if (treeExportInfos.rootNetworks().isEmpty()) {
            throw new StudyException(NOT_FOUND, "No root network found in import archive");
        }
        Map<UUID, UUID> modificationGroupUuidMapping = duplicateModificationGroups(treeExportInfos.nodeTree());
        StudyEntity studyEntity = studyService.createStudyEntityWithTree(treeExportInfos.studyUuid(), userId, treeExportInfos.nodeTree(), modificationGroupUuidMapping);
        studyRepository.save(studyEntity);
        List<RootNetworkExportInfos> orderedRootNetworks = treeExportInfos.rootNetworks().stream()
                .sorted(Comparator.comparing(RootNetworkExportInfos::index))
                .toList();
        for (RootNetworkExportInfos rootNetworkInfos : orderedRootNetworks) {
            UUID newCaseUuid = caseService.duplicateCase(rootNetworkInfos.caseInfos().getCaseUuid(), true);
            RootNetworkEntity rootNetworkEntity = rootNetworkService.createRootNetwork(studyEntity, RootNetworkInfos.builder()
                    .id(UUID.randomUUID())
                    .name(rootNetworkInfos.name())
                    .tag(rootNetworkInfos.tag())
                    .caseInfos(new CaseInfos(newCaseUuid, rootNetworkInfos.caseInfos().getOriginalCaseUuid(),
                            rootNetworkInfos.caseInfos().getCaseName(), rootNetworkInfos.caseInfos().getCaseFormat()))
                    .importParameters(rootNetworkInfos.importParameters())
                    .networkInfos(new NetworkInfos(UUID.randomUUID(), ""))
                    .build());
            rootNetworkService.updateNetworkLoadStatus(rootNetworkEntity.getId(), RootNetworkLoadStatus.UNLOADED);
        }
        notificationService.emitStudyCreationFinished(studyEntity.getId(), userId);
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
