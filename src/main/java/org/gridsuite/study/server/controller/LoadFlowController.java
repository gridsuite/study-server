/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.service.LoadFlowService;
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
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/loadflow")
public class LoadFlowController {
    private final LoadFlowService loadFlowService;

    public LoadFlowController(LoadFlowService loadFlowService) {
        this.loadFlowService = loadFlowService;
    }

    @GetMapping(value = "/providers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowService.getProviders());
    }

    @GetMapping(value = "/specific-parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSpecificParameters() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowService.getSpecificParameters());
    }

    @GetMapping(value = "/parameters/default-limit-reductions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getDefaultLimitReductions() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowService.getDefaultLimitReductions());
    }

    @GetMapping(value = "/parameters/{parameterUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoadFlowParametersInfos> getLoadFlowParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowService.getLoadFlowParameters(parameterUuid));
    }

    @PutMapping(value = "/parameters/{parameterUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateLoadFlowParameters(@PathVariable UUID parameterUuid,
                                                         @RequestBody(required = false) String parameters) {
        loadFlowService.updateLoadFlowParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }
}
