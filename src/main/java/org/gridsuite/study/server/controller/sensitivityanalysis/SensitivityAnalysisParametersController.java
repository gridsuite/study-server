/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.sensitivityanalysis;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.sensitivityanalysis.SensitivityAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/sensitivity-analysis")
public class SensitivityAnalysisParametersController {
    private final SensitivityAnalysisService sensitivityAnalysisService;

    public SensitivityAnalysisParametersController(SensitivityAnalysisService sensitivityAnalysisService) {
        this.sensitivityAnalysisService = sensitivityAnalysisService;
    }

    @GetMapping(value = "/providers")
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(sensitivityAnalysisService.getProviders());
    }

    @GetMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<String> getSensitivityAnalysisParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(sensitivityAnalysisService.getSensitivityAnalysisParametersByUuid(parameterUuid));
    }

    @PutMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<Void> updateSensitivityAnalysisParameters(@PathVariable UUID parameterUuid, @RequestBody(required = false) String parameters) {
        sensitivityAnalysisService.updateSensitivityAnalysisParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }
}
