/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DynamicMappingService {
    private final DynamicMappingClient dynamicMappingClient;

    public DynamicMappingService(DynamicMappingClient dynamicMappingClient) {
        this.dynamicMappingClient = dynamicMappingClient;
    }

    public String getMappedModels(UUID mappingId) {
        return dynamicMappingClient.getMappedModels(mappingId);
    }
}
