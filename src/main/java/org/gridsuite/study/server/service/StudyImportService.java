/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkLoadStatus;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class StudyImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyImportService.class);

    private final StudyService studyService;
    private final RootNetworkService rootNetworkService;
    private final CaseService caseService;
    private final NotificationService notificationService;

    public StudyImportService(StudyService studyService, RootNetworkService rootNetworkService,
                              CaseService caseService, NotificationService notificationService) {
        this.studyService = studyService;
        this.rootNetworkService = rootNetworkService;
        this.caseService = caseService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void importStudy(TreeExportInfos treeExportInfos, String userId) {
        StudyEntity studyEntity = studyService.createStudyEntityWithTree(treeExportInfos.studyUuid(), userId, treeExportInfos.nodeTree());
        List<RootNetworkExportInfos> orderedRootNetworks = treeExportInfos.rootNetworks().stream()
                .sorted(Comparator.comparing(RootNetworkExportInfos::index))
                .toList();
        List<UUID> duplicatedCaseUuids = new ArrayList<>();
        try {
            for (RootNetworkExportInfos rootNetworkInfos : orderedRootNetworks) {
                UUID newCaseUuid = caseService.duplicateCase(rootNetworkInfos.caseInfos().getCaseUuid(), true);
                duplicatedCaseUuids.add(newCaseUuid);
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
        } catch (Exception e) {
            duplicatedCaseUuids.forEach(caseUuid -> {
                try {
                    caseService.deleteCase(caseUuid);
                } catch (Exception exception) {
                    LOGGER.error(String.format("Could not clean up orphaned case '%s' after import failure", caseUuid), exception);
                }
            });
            throw e;
        }
        notificationService.emitStudyCreationFinished(studyEntity.getId(), userId);
    }
}
