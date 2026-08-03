/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.shortcircuit;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/shortcircuit")
public class ShortCircuitParametersController {
    private final ShortCircuitService shortCircuitService;

    public ShortCircuitParametersController(ShortCircuitService shortCircuitService) {
        this.shortCircuitService = shortCircuitService;
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file", produces = "application/json")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return shortCircuitService.downloadDebugFile(resultUuid);
    }

    @GetMapping(value = "/parameters/specific-parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSpecificParameters() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(shortCircuitService.getSpecificParameters());
    }

    @GetMapping(value = "/parameters/{parameterUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(shortCircuitService.getParameters(parameterUuid));
    }

    @PutMapping(value = "/parameters/{parameterUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateParameters(@PathVariable UUID parameterUuid, @RequestBody(required = false) String parameters) {
        shortCircuitService.updateParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }
}
