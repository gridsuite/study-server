/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.RequiredArgsConstructor;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.AbstractDiagramLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.MapLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayoutDetails;
import org.gridsuite.study.server.dto.diagramgridlayout.nad.NadConfigInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.gridsuite.study.server.StudyBusinessErrorCode.TOO_MANY_MAP_CARDS;
import static org.gridsuite.study.server.StudyBusinessErrorCode.TOO_MANY_NAD_CONFIGS;

/**
 * Service responsible for managing diagram grid layout operations.
 * Handles the complete lifecycle of diagram configurations including creation, update, and deletion
 * with proper cleanup of associated NAD (Network Area Diagram) configurations.
 */
@Service
@RequiredArgsConstructor
public class DiagramGridLayoutService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagramGridLayoutService.class);
    public static final int MAX_NAD_CONFIGS_ALLOWED = 3;
    public static final int MAX_MAP_CARDS_ALLOWED = 1;

    private final StudyConfigService studyConfigService;
    private final SingleLineDiagramService singleLineDiagramService;

    public DiagramGridLayout getDiagramGridLayout(@Nullable UUID diagramGridLayoutUuid) {
        if (diagramGridLayoutUuid == null) {
            return null;
        }
        // Get the simplified layout from study-config-server (this contains NAD layouts with UUIDs only)
        return studyConfigService.getDiagramGridLayout(diagramGridLayoutUuid);
    }

    public UUID createDiagramGridLayout(DiagramGridLayout diagramGridLayout) {
        // Validate NAD config count before processing
        validateNadConfigCount(diagramGridLayout);

        // Validate map card count
        validateMapCardCount(diagramGridLayout);

        // Process diagram layout and handle NAD layout details
        DiagramGridLayout processedDiagramLayout = processNADLayoutDetails(diagramGridLayout);
        return studyConfigService.saveDiagramGridLayout(processedDiagramLayout);
    }

    /**
     * Updates an existing diagram grid layout with proper cleanup of old NAD configurations.
     */
    public UUID updateDiagramGridLayout(UUID existingDiagramGridLayoutUuid, DiagramGridLayout diagramGridLayout) {
        // Validate NAD config count before processing
        validateNadConfigCount(diagramGridLayout);

        // Validate map card count
        validateMapCardCount(diagramGridLayout);

        List<UUID> oldNadConfigUuidsToDelete = collectOldNadConfigUuids(existingDiagramGridLayoutUuid);

        // Process diagram layout and handle NAD layout details
        DiagramGridLayout processedDiagramLayout = processNADLayoutDetails(diagramGridLayout);

        // Update the existing layout
        studyConfigService.updateDiagramGridLayout(existingDiagramGridLayoutUuid, processedDiagramLayout);

        // Clean up old NAD configs after successful update
        cleanupOldNadConfigs(oldNadConfigUuidsToDelete);

        return existingDiagramGridLayoutUuid;
    }

    /**
     * Removes a diagram grid layout and all associated NAD configurations.
     */
    public void removeDiagramGridLayout(@Nullable UUID diagramGridLayoutUuid) {
        if (diagramGridLayoutUuid != null) {
            try {
                // First, retrieve the diagram grid layout to get NAD config UUIDs
                DiagramGridLayout layout = studyConfigService.getDiagramGridLayout(diagramGridLayoutUuid);
                List<UUID> nadConfigUuidsToDelete = extractNadConfigUuids(layout);

                // Delete NAD configs from single-line-diagram-server if any exist
                cleanupOldNadConfigs(nadConfigUuidsToDelete);

                // Finally, delete the diagram grid layout from study-config-server
                studyConfigService.deleteDiagramGridLayout(diagramGridLayoutUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove diagram grid layout config with uuid:" + diagramGridLayoutUuid, e);
            }
        }
    }

    /**
     * Collects old NAD config UUIDs from an existing diagram grid layout.
     */
    private List<UUID> collectOldNadConfigUuids(UUID existingDiagramGridLayoutUuid) {
        try {
            DiagramGridLayout existingLayout = studyConfigService.getDiagramGridLayout(existingDiagramGridLayoutUuid);
            return extractNadConfigUuids(existingLayout);
        } catch (Exception e) {
            LOGGER.warn("Could not retrieve existing diagram grid layout for cleanup: " + existingDiagramGridLayoutUuid, e);
        }
        return new ArrayList<>();
    }

    /**
     * Extracts NAD config UUIDs from a diagram grid layout.
     */
    private List<UUID> extractNadConfigUuids(DiagramGridLayout layout) {
        if (layout != null && layout.getDiagramLayouts() != null) {
            return layout.getDiagramLayouts().stream()
                .filter(NetworkAreaDiagramLayout.class::isInstance)
                .map(diagramLayout -> ((NetworkAreaDiagramLayout) diagramLayout).getCurrentNadConfigUuid())
                .filter(Objects::nonNull)
                .toList();
        }
        return new ArrayList<>();
    }

    /**
     * Cleans up old NAD configurations by deleting them from the single-line-diagram-server.
     */
    private void cleanupOldNadConfigs(List<UUID> oldNadConfigUuidsToDelete) {
        if (!oldNadConfigUuidsToDelete.isEmpty()) {
            try {
                singleLineDiagramService.deleteMultipleDiagramConfigs(oldNadConfigUuidsToDelete);
            } catch (Exception e) {
                LOGGER.error("Could not clean up old NAD configs: " + oldNadConfigUuidsToDelete, e);
            }
        }
    }

    /**
     * Validates that the diagram grid layout does not contain more than one map card.
     */
    private void validateMapCardCount(DiagramGridLayout diagramGridLayout) {
        if (diagramGridLayout != null && diagramGridLayout.getDiagramLayouts() != null) {
            long mapCardsCount = diagramGridLayout.getDiagramLayouts().stream()
                .filter(MapLayout.class::isInstance)
                .count();

            if (mapCardsCount > MAX_MAP_CARDS_ALLOWED) {
                throw new StudyException(TOO_MANY_MAP_CARDS,
                    "Maximum " + MAX_MAP_CARDS_ALLOWED + " map card allowed, but " + mapCardsCount + " provided");
            }
        }
    }

    /**
     * Validates that the diagram grid layout does not exceed the maximum allowed NAD configurations.
     */
    private void validateNadConfigCount(DiagramGridLayout diagramGridLayout) {
        if (diagramGridLayout != null && diagramGridLayout.getDiagramLayouts() != null) {
            long nadConfigCount = diagramGridLayout.getDiagramLayouts().stream()
                .filter(layout -> layout instanceof NetworkAreaDiagramLayoutDetails ||
                                layout instanceof NetworkAreaDiagramLayout)
                .count();

            if (nadConfigCount > MAX_NAD_CONFIGS_ALLOWED) {
                throw new StudyException(TOO_MANY_NAD_CONFIGS,
                    "Maximum " + MAX_NAD_CONFIGS_ALLOWED + " NAD configurations allowed, but " + nadConfigCount + " provided");
            }
        }
    }

    /**
     * Process NAD layout details by extracting essential data and delegating storage to single-line-diagram-server.
     * This optimization reduces data transfer by only sending positions and voltageLevelIds for diagram config creation,
     * while preserving filterUuid and diagramPositions in the simplified NAD layout returned to study-config-server.
     */
    private DiagramGridLayout processNADLayoutDetails(DiagramGridLayout diagramGridLayout) {
        if (diagramGridLayout.getDiagramLayouts() == null || diagramGridLayout.getDiagramLayouts().isEmpty()) {
            return diagramGridLayout;
        }

        List<AbstractDiagramLayout> layouts = diagramGridLayout.getDiagramLayouts();
        List<NadConfigInfos> nadConfigsToCreate = new ArrayList<>();
        List<AbstractDiagramLayout> processedLayouts = new ArrayList<>();

        for (AbstractDiagramLayout layout : layouts) {
            if (layout instanceof NetworkAreaDiagramLayoutDetails nadDetails) {
                UUID preGeneratedUuid = UUID.randomUUID();
                nadConfigsToCreate.add(NadConfigInfos.builder()
                    .id(preGeneratedUuid)
                    .positions(nadDetails.getPositions())
                    .voltageLevelIds(nadDetails.getVoltageLevelIds())
                    .build());

                processedLayouts.add(NetworkAreaDiagramLayout.builder()
                    .diagramUuid(nadDetails.getDiagramUuid())
                    .diagramPositions(nadDetails.getDiagramPositions())
                    .currentNadConfigUuid(preGeneratedUuid)
                    .originalNadConfigUuid(nadDetails.getOriginalNadConfigUuid())
                    .filterUuid(nadDetails.getFilterUuid())
                    .name(nadDetails.getName())
                    .build());
            } else {
                processedLayouts.add(layout);
            }
        }

        // Batch create all NAD configs if any exist
        if (!nadConfigsToCreate.isEmpty()) {
            singleLineDiagramService.createMultipleDiagramConfigs(nadConfigsToCreate);
        }

        return DiagramGridLayout.builder()
            .diagramLayouts(processedLayouts)
            .build();
    }
}
