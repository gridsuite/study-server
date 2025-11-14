/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.RequiredArgsConstructor;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.diagramgridlayout.nad.NadConfigInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.DELETE_NAD_CONFIG_FAILED;
import static org.gridsuite.study.server.StudyException.Type.SAVE_NAD_CONFIG_FAILED;

/**
 * @author Ayoub Labidi <ayoub.labidi at rte-france.com>
 */
@Service
@RequiredArgsConstructor
public class NadConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NadConfigService.class);

    private final SingleLineDiagramService singleLineDiagramService;

    public UUID saveNadConfig(NadConfigInfos nadConfigInfos) {

        UUID configUuid = nadConfigInfos.getId();

        if (configUuid == null) {
            // Create new config
            UUID newUuid = UUID.randomUUID();
            nadConfigInfos.setId(newUuid);
            try {
                singleLineDiagramService.createDiagramConfigs(List.of(nadConfigInfos));
            } catch (Exception e) {
                throw new StudyException(SAVE_NAD_CONFIG_FAILED, "Could not create NAD config: " + newUuid);
            }
            return newUuid;
        } else {
            // Update existing config
            try {
                singleLineDiagramService.updateNadConfig(nadConfigInfos);
            } catch (Exception e) {
                throw new StudyException(SAVE_NAD_CONFIG_FAILED, "Could not update NAD config: " + configUuid);
            }
            return configUuid;
        }
    }

    public void deleteNadConfig(UUID configUuid) {
        if (configUuid == null) {
            return;
        }

        try {
            singleLineDiagramService.deleteDiagramConfigs(List.of(configUuid));
        } catch (Exception e) {
            throw new StudyException(DELETE_NAD_CONFIG_FAILED, "Could not delete NAD config: " + configUuid);
        }
    }

    public void deleteNadConfigs(List<UUID> nadConfigUuids) {
        if (nadConfigUuids == null || nadConfigUuids.isEmpty()) {
            return;
        }

        try {
            singleLineDiagramService.deleteDiagramConfigs(nadConfigUuids);
        } catch (Exception e) {
            LOGGER.error("Could not delete NAD configs: {}", nadConfigUuids, e);
        }
    }
}
