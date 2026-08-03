/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.securityanalysis;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/security-analysis")
public class SecurityAnalysisParametersController {
    private final SecurityAnalysisService securityAnalysisService;

    public SecurityAnalysisParametersController(SecurityAnalysisService securityAnalysisService) {
        this.securityAnalysisService = securityAnalysisService;
    }

    @GetMapping(value = "/providers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(securityAnalysisService.getProviders());
    }

    @GetMapping(value = "/parameters/{parameterUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSecurityAnalysisParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(securityAnalysisService.getSecurityAnalysisParameters(parameterUuid));
    }

    @GetMapping(value = "/parameters/default-limit-reductions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getDefaultLimitReductions() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(securityAnalysisService.getDefaultLimitReductions());
    }

    @PutMapping(value = "/parameters/{parameterUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateSecurityAnalysisParameters(@PathVariable UUID parameterUuid, @RequestBody(required = false) String parameters) {
        securityAnalysisService.updateSecurityAnalysisParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }
}
