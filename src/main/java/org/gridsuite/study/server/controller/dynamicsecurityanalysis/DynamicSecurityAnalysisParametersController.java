/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicsecurityanalysis;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/dynamic-security-analysis")
public class DynamicSecurityAnalysisParametersController {
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    public DynamicSecurityAnalysisParametersController(DynamicSecurityAnalysisService dynamicSecurityAnalysisService) {
        this.dynamicSecurityAnalysisService = dynamicSecurityAnalysisService;
    }

    @GetMapping(value = "/providers")
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSecurityAnalysisService.getProviders());
    }

    @GetMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<String> getParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSecurityAnalysisService.getParameters(parameterUuid));
    }

    @PutMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<Void> updateParameters(@PathVariable UUID parameterUuid, @RequestBody String parameters) {
        dynamicSecurityAnalysisService.updateParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return dynamicSecurityAnalysisService.downloadDebugFile(resultUuid);
    }
}
