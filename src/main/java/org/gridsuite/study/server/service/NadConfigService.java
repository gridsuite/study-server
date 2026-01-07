/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi at rte-france.com>
 */
@Service
@RequiredArgsConstructor
public class NadConfigService {

    private final SingleLineDiagramService singleLineDiagramService;

    public void deleteNadConfigs(List<UUID> nadConfigUuids) {
        if (nadConfigUuids == null || nadConfigUuids.isEmpty()) {
            return;
        }

        singleLineDiagramService.deleteDiagramConfigs(nadConfigUuids);
    }
}
