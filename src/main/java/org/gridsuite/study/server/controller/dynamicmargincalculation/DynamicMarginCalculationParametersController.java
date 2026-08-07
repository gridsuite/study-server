/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicmargincalculation;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/dynamic-margin-calculation")
public class DynamicMarginCalculationParametersController {
    private final DynamicMarginCalculationService dynamicMarginCalculationService;

    public DynamicMarginCalculationParametersController(DynamicMarginCalculationService dynamicMarginCalculationService) {
        this.dynamicMarginCalculationService = dynamicMarginCalculationService;
    }

    @GetMapping(value = "/providers")
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicMarginCalculationService.getProviders());
    }

    @GetMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<String> getParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicMarginCalculationService.getParameters(parameterUuid, null));
    }

    @PutMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<Void> updateParameters(@PathVariable UUID parameterUuid, @RequestBody String parameters) {
        dynamicMarginCalculationService.updateParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return dynamicMarginCalculationService.downloadDebugFile(resultUuid);
    }
}
