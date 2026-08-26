/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicsimulation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/dynamic-simulation")
@Tag(name = "Study server - Dynamic security analysis")
public class DynamicSimulationStudyParametersController {
    private final DynamicSimulationService dynamicSimulationService;

    public DynamicSimulationStudyParametersController(DynamicSimulationService dynamicSimulationService) {
        this.dynamicSimulationService = dynamicSimulationService;
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "Set dynamic simulation parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation parameters are set")})
    public ResponseEntity<Void> setDynamicSimulationParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String dsParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        dynamicSimulationService.setDynamicSimulationParameters(studyUuid, dsParameter, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get dynamic simulation parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation parameters")})
    public ResponseEntity<String> getDynamicSimulationParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(dynamicSimulationService.getDynamicSimulationParameters(studyUuid));
    }

    @GetMapping(value = "/provider")
    @Operation(summary = "Get dynamic simulation provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation provider is returned")})
    public ResponseEntity<String> getDynamicSimulationProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(dynamicSimulationService.getDynamicSimulationProvider(studyUuid));
    }
}
