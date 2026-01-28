/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Ayoub Labidi <ayoub.labidi at rte-france.com>
 */
@Service
public class WorkspaceService {
    private static final String STUDY_NOT_FOUND_MESSAGE = "Study %s not found";

    private final StudyRepository studyRepository;
    private final StudyConfigService studyConfigService;
    private final NotificationService notificationService;

    public WorkspaceService(StudyRepository studyRepository,
                           StudyConfigService studyConfigService,
                           NotificationService notificationService) {
        this.studyRepository = studyRepository;
        this.studyConfigService = studyConfigService;
        this.notificationService = notificationService;
    }

    private StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .orElseThrow(() -> new StudyException(NOT_FOUND, String.format(STUDY_NOT_FOUND_MESSAGE, studyUuid)));
    }

    private UUID getWorkspacesConfigUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getWorkspacesConfigUuid() == null) {
            studyEntity.setWorkspacesConfigUuid(studyConfigService.createDefaultWorkspacesConfig());
        }
        return studyEntity.getWorkspacesConfigUuid();
    }

    @Transactional
    public String getWorkspaces(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID workspacesConfigUuid = getWorkspacesConfigUuidOrElseCreateDefaults(studyEntity);
        return studyConfigService.getWorkspaces(workspacesConfigUuid);
    }

    public String getWorkspace(UUID studyUuid, UUID workspaceId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyConfigService.getWorkspace(studyEntity.getWorkspacesConfigUuid(), workspaceId);
    }

    @Transactional
    public void renameWorkspace(UUID studyUuid, UUID workspaceId, String name, String clientId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        studyConfigService.renameWorkspace(studyEntity.getWorkspacesConfigUuid(), workspaceId, name);
        notificationService.emitWorkspaceUpdated(studyUuid, workspaceId, clientId);
    }

    public String getWorkspacePanels(UUID studyUuid, UUID workspaceId, List<String> panelIds) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyConfigService.getWorkspacePanels(studyEntity.getWorkspacesConfigUuid(), workspaceId, panelIds);
    }

    @Transactional
    public void createOrUpdateWorkspacePanels(UUID studyUuid, UUID workspaceId, String panelsDto, String clientId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        String panelIds = studyConfigService.createOrUpdateWorkspacePanels(studyEntity.getWorkspacesConfigUuid(), workspaceId, panelsDto);
        notificationService.emitWorkspacePanelsUpdated(studyUuid, workspaceId, panelIds, clientId);
    }

    @Transactional
    public void deleteWorkspacePanels(UUID studyUuid, UUID workspaceId, String panelIds, String clientId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        studyConfigService.deleteWorkspacePanels(
            studyEntity.getWorkspacesConfigUuid(),
            workspaceId,
            panelIds
        );
        String notificationPayload = (panelIds != null) ? panelIds : "[]";
        notificationService.emitWorkspacePanelsDeleted(studyUuid, workspaceId, notificationPayload, clientId);
    }

    @Transactional
    public UUID saveNadConfig(UUID studyUuid, UUID workspaceId, UUID panelId, Map<String, Object> nadConfigData, String clientId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID configUuid = studyConfigService.saveWorkspacePanelNadConfig(
            studyEntity.getWorkspacesConfigUuid(),
            workspaceId,
            panelId,
            nadConfigData
        );
        notificationService.emitWorkspaceNadConfigUpdated(studyUuid, workspaceId, panelId, configUuid, clientId);
        return configUuid;
    }

    @Transactional
    public void deleteNadConfig(UUID studyUuid, UUID workspaceId, UUID panelId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        studyConfigService.deleteWorkspacePanelNadConfig(
            studyEntity.getWorkspacesConfigUuid(),
            workspaceId,
            panelId
        );
    }
}
