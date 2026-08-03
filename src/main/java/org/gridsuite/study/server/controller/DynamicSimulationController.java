/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/dynamic-simulation")
public class DynamicSimulationController {
    private final DynamicSimulationService dynamicSimulationService;

    public DynamicSimulationController(DynamicSimulationService dynamicSimulationService) {
        this.dynamicSimulationService = dynamicSimulationService;
    }

    @GetMapping(value = "/providers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSimulationService.getProviders());
    }

    @GetMapping(value = "/parameters/{parameterUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSimulationService.getParameters(parameterUuid));
    }

    @PutMapping(value = "/parameters/{parameterUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateParameters(@PathVariable UUID parameterUuid, @RequestBody String parameters) {
        dynamicSimulationService.updateParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file", produces = "application/json")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return dynamicSimulationService.downloadDebugFile(resultUuid);
    }
}
