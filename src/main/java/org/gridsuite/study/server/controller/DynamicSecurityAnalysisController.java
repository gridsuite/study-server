/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/dynamic-security-analysis")
public class DynamicSecurityAnalysisController {
    private final DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient;
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    public DynamicSecurityAnalysisController(DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient,
                                             DynamicSecurityAnalysisService dynamicSecurityAnalysisService) {
        this.dynamicSecurityAnalysisClient = dynamicSecurityAnalysisClient;
        this.dynamicSecurityAnalysisService = dynamicSecurityAnalysisService;
    }

    @GetMapping(value = "/providers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSecurityAnalysisService.getProviders());
    }

    @GetMapping(value = "/parameters/{parameterUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSecurityAnalysisService.getParameters(parameterUuid));
    }

    @PutMapping(value = "/parameters/{parameterUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateParameters(@PathVariable UUID parameterUuid,
                                                @RequestBody String parameters) {
        dynamicSecurityAnalysisService.updateParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file", produces = "application/json")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return dynamicSecurityAnalysisClient.downloadDebugFile(resultUuid);
    }
}
