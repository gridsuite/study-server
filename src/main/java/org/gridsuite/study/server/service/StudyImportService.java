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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final StudyImportService self;

    public StudyImportService(StudyService studyService, RootNetworkService rootNetworkService,
                              CaseService caseService, NotificationService notificationService, @Lazy StudyImportService self) {
        this.studyService = studyService;
        this.rootNetworkService = rootNetworkService;
        this.caseService = caseService;
        this.notificationService = notificationService;
        this.self = self;
    }

    public void importStudy(TreeExportInfos treeExportInfos, String userId) {
        StudyEntity studyEntity = self.createStudyEntityWithTree(treeExportInfos, userId);
        UUID studyUuid = studyEntity.getId();
        duplicateCaseAndCreateRootNetworks(studyUuid, treeExportInfos.rootNetworks());
        notificationService.emitStudyCreationFinished(studyUuid, userId);
    }

    @Transactional
    public StudyEntity createStudyEntityWithTree(TreeExportInfos treeExportInfos, String userId) {
        return studyService.createStudyEntityWithTree(treeExportInfos.studyUuid(), userId, treeExportInfos.nodeTree());
    }

    private void duplicateCaseAndCreateRootNetworks(UUID studyUuid, List<RootNetworkExportInfos> rootNetworksInfos) {
        List<RootNetworkExportInfos> orderedRootNetworks = rootNetworksInfos.stream().sorted(Comparator.comparing(RootNetworkExportInfos::index)).toList();
        for (RootNetworkExportInfos rootNetworkInfos : orderedRootNetworks) {
            self.duplicateCaseAndCreateRootNetwork(studyUuid, rootNetworkInfos);
        }
    }

    @Transactional
    public void duplicateCaseAndCreateRootNetwork(UUID studyUuid, RootNetworkExportInfos rootNetworkInfos) {
        StudyEntity studyEntity = studyService.getStudy(studyUuid);
        UUID newCaseUuid = caseService.duplicateCase(rootNetworkInfos.caseInfos().getCaseUuid(), false);
        try {
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
        } catch (Exception exception) {
            caseService.deleteCase(newCaseUuid);
            LOGGER.error(String.format("Could not clean up orphaned case '%s' after import failure", newCaseUuid), exception);
        }
    }
}
