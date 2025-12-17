/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.RequiredArgsConstructor;
import org.gridsuite.study.server.repository.StudyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceService.class);

    private final StudyConfigService studyConfigService;
    private final UserAdminService userAdminService;

    public String getWorkspaceCollection(UUID studyUuid, StudyEntity studyEntity) {
        return studyConfigService.getWorkspaceCollection(getWorkspaceCollectionUuidOrElseCreateDefaults(studyEntity));
    }

    public UUID getWorkspaceCollectionUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getWorkspaceCollectionUuid() == null) {
            studyEntity.setWorkspaceCollectionUuid(studyConfigService.createDefaultWorkspaceCollection());
        }
        return studyEntity.getWorkspaceCollectionUuid();
    }

    public String setWorkspaceCollection(StudyEntity studyEntity, String workspaceCollection, String userId) {
        return createOrUpdateWorkspaceCollection(studyEntity, workspaceCollection, userId);
    }

    private String createOrUpdateWorkspaceCollection(StudyEntity studyEntity, String workspaceCollection, String userId) {
        UUID existingWorkspaceCollectionUuid = studyEntity.getWorkspaceCollectionUuid();

        if (workspaceCollection != null) {
            if (existingWorkspaceCollectionUuid == null) {
                UUID newUuid = studyConfigService.createWorkspaceCollection(workspaceCollection);
                studyEntity.setWorkspaceCollectionUuid(newUuid);
                return workspaceCollection;
            } else {
                return studyConfigService.updateWorkspaceCollection(existingWorkspaceCollectionUuid, workspaceCollection);
            }
        } else {
            // No config provided, use system default
            UUID defaultCollectionUuid = studyConfigService.createDefaultWorkspaceCollection();
            studyEntity.setWorkspaceCollectionUuid(defaultCollectionUuid);
            removeWorkspaceCollection(existingWorkspaceCollectionUuid);
            return null;
        }
    }

    public void removeWorkspaceCollection(@Nullable UUID workspaceCollectionUuid) {
        if (workspaceCollectionUuid != null) {
            try {
                studyConfigService.deleteWorkspaceCollection(workspaceCollectionUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove workspace collection with uuid:" + workspaceCollectionUuid, e);
            }
        }
    }

    // Workspace methods
    public String getWorkspaces(StudyEntity studyEntity) {
        UUID collectionUuid = getWorkspaceCollectionUuidOrElseCreateDefaults(studyEntity);
        return studyConfigService.getWorkspaces(collectionUuid);
    }

    public String getWorkspace(StudyEntity studyEntity, UUID workspaceId) {
        UUID collectionUuid = getWorkspaceCollectionUuidOrElseCreateDefaults(studyEntity);
        return studyConfigService.getWorkspace(collectionUuid, workspaceId);
    }

    public void updateWorkspace(StudyEntity studyEntity, UUID workspaceId, String workspaceDto) {
        UUID collectionUuid = getWorkspaceCollectionUuidOrElseCreateDefaults(studyEntity);
        studyConfigService.updateWorkspace(collectionUuid, workspaceId, workspaceDto);
    }

    public String getPanels(StudyEntity studyEntity, UUID workspaceId, String ids) {
        UUID collectionUuid = getWorkspaceCollectionUuidOrElseCreateDefaults(studyEntity);
        return studyConfigService.getPanels(collectionUuid, workspaceId, ids);
    }

    public void createOrUpdatePanels(StudyEntity studyEntity, UUID workspaceId, String panelsDto) {
        UUID collectionUuid = getWorkspaceCollectionUuidOrElseCreateDefaults(studyEntity);
        studyConfigService.createOrUpdatePanels(collectionUuid, workspaceId, panelsDto);
    }

    public void deletePanels(StudyEntity studyEntity, UUID workspaceId, String panelIds) {
        UUID collectionUuid = getWorkspaceCollectionUuidOrElseCreateDefaults(studyEntity);
        studyConfigService.deletePanels(collectionUuid, workspaceId, panelIds);
    }
}
