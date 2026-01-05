/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.diagramgridlayout.nad.NadConfigInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
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

    private final StudyRepository studyRepository;
    private final StudyConfigService studyConfigService;
    private final NotificationService notificationService;
    private final SingleLineDiagramService singleLineDiagramService;
    private final ObjectMapper objectMapper;

    public WorkspaceService(StudyRepository studyRepository,
                           StudyConfigService studyConfigService,
                           NotificationService notificationService,
                           SingleLineDiagramService singleLineDiagramService,
                           ObjectMapper objectMapper) {
        this.studyRepository = studyRepository;
        this.studyConfigService = studyConfigService;
        this.notificationService = notificationService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.objectMapper = objectMapper;
    }

    private StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .orElseThrow(() -> new StudyException(NOT_FOUND, "Study " + studyUuid + " not found"));
    }

    private UUID getWorkspacesConfigUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getWorkspacesConfigUuid() == null) {
            studyEntity.setWorkspacesConfigUuid(studyConfigService.createDefaultWorkspacesConfig());
        }
        return studyEntity.getWorkspacesConfigUuid();
    }

    public void removeWorkspacesConfig(@Nullable UUID workspacesConfigUuid) {
        if (workspacesConfigUuid != null) {
            try {
                studyConfigService.deleteWorkspacesConfig(workspacesConfigUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove workspaces config with uuid:" + workspacesConfigUuid, e);
            }
        }
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
    public void renameWorkspace(UUID studyUuid, UUID workspaceId, String name) {
        StudyEntity studyEntity = getStudy(studyUuid);
        studyConfigService.renameWorkspace(studyEntity.getWorkspacesConfigUuid(), workspaceId, name);
        notificationService.emitWorkspaceUpdated(studyUuid, workspaceId);
    }

    public String getWorkspacePanels(UUID studyUuid, UUID workspaceId, List<String> ids) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyConfigService.getWorkspacePanels(studyEntity.getWorkspacesConfigUuid(), workspaceId, ids);
    }

    @Transactional
    public void createOrUpdateWorkspacePanels(UUID studyUuid, UUID workspaceId, String panelsDto) {
        StudyEntity studyEntity = getStudy(studyUuid);
        studyConfigService.createOrUpdateWorkspacePanels(studyEntity.getWorkspacesConfigUuid(), workspaceId, panelsDto);
        String panelIds = extractWorkspacePanelIds(panelsDto);
        notificationService.emitWorkspacePanelsUpdated(studyUuid, workspaceId, panelIds);
    }

    @Transactional
    public void deleteWorkspacePanels(UUID studyUuid, UUID workspaceId, String panelIds) {
        StudyEntity studyEntity = getStudy(studyUuid);

        // Delete panels and get saved NAD config UUIDs
        List<UUID> savedNadConfigUuids = studyConfigService.deleteWorkspacePanels(
            studyEntity.getWorkspacesConfigUuid(),
            workspaceId,
            panelIds
        );

        // Delete saved NAD configs from single-line-diagram server
        if (!savedNadConfigUuids.isEmpty()) {
            singleLineDiagramService.deleteDiagramConfigs(savedNadConfigUuids);
        }

        notificationService.emitWorkspacePanelsDeleted(studyUuid, workspaceId, panelIds);
    }

    @Transactional
    public UUID saveNadConfig(UUID studyUuid, UUID workspaceId, NadConfigInfos nadConfigInfos) {
        getStudy(studyUuid);
        // Save NAD config to single-line-diagram server
        UUID configUuid = nadConfigInfos.getId();
        if (configUuid == null) {
            nadConfigInfos.setId(UUID.randomUUID());
            singleLineDiagramService.createDiagramConfigs(List.of(nadConfigInfos));
        } else {
            singleLineDiagramService.updateNadConfig(nadConfigInfos);
        }

        UUID savedNadConfigUuid = nadConfigInfos.getId();
        // Emit notification
        notificationService.emitWorkspaceNadConfigUpdated(studyUuid, savedNadConfigUuid);

        return savedNadConfigUuid;
    }

    @Transactional
    public void deleteNadConfig(UUID studyUuid, UUID savedNadConfigUuid) {
        getStudy(studyUuid);
        // Delete NAD config from single-line-diagram server
        singleLineDiagramService.deleteDiagramConfigs(List.of(savedNadConfigUuid));
    }

    private String extractWorkspacePanelIds(String panelsDto) {
        try {
            List<Map<String, Object>> panels = objectMapper.readValue(panelsDto, new TypeReference<>() { });
            List<String> panelIds = panels.stream()
                .map(panel -> panel.get("id"))
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
