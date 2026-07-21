/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.DynamicMappingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/dynamic-mapping")
public class DynamicMappingController {
    private final DynamicMappingService dynamicMappingService;

    public DynamicMappingController(DynamicMappingService dynamicMappingService) {
        this.dynamicMappingService = dynamicMappingService;
    }

    @GetMapping(value = "/mappings/{mappingId}/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getMappedModels(@PathVariable UUID mappingId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicMappingService.getMappedModels(mappingId));
    }
}
