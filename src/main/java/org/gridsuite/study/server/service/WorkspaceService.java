/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Ayoub Labidi <ayoub.labidi at rte-france.com>
 */
@Service
public class WorkspaceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceService.class);
    private static final String STUDY_NOT_FOUND_MESSAGE = "Study %s not found";
    private static final String PANEL_ID_KEY = "id";

    private final StudyRepository studyRepository;
    private final StudyConfigService studyConfigService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public WorkspaceService(StudyRepository studyRepository,
                           StudyConfigService studyConfigService,
                           NotificationService notificationService,
                           ObjectMapper objectMapper) {
        this.studyRepository = studyRepository;
        this.studyConfigService = studyConfigService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
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
        studyConfigService.createOrUpdateWorkspacePanels(studyEntity.getWorkspacesConfigUuid(), workspaceId, panelsDto);
        String panelIds = extractWorkspacePanelIds(panelsDto);
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
        notificationService.emitWorkspacePanelsDeleted(studyUuid, workspaceId, panelIds, clientId);
    }

    @Transactional
    public UUID saveNadConfig(UUID studyUuid, UUID workspaceId, UUID panelId, Map<String, Object> nadConfigData, String clientId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID savedNadConfigUuid = studyConfigService.saveWorkspacePanelNadConfig(
            studyEntity.getWorkspacesConfigUuid(),
            workspaceId,
            panelId,
            nadConfigData
        );
        notificationService.emitWorkspaceNadConfigUpdated(studyUuid, workspaceId, panelId, savedNadConfigUuid, clientId);
        return savedNadConfigUuid;
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

    private String extractWorkspacePanelIds(String panelsDto) {
        try {
            List<Map<String, Object>> panels = objectMapper.readValue(panelsDto, new TypeReference<>() { });
            List<String> panelIds = panels.stream()
                .map(panel -> panel.get(PANEL_ID_KEY))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
            return objectMapper.writeValueAsString(panelIds);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to extract panel IDs from DTO", e);
            return "[]";
        }
    }
}
