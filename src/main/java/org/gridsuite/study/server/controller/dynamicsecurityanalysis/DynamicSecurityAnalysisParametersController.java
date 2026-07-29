/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicsecurityanalysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/dynamic-security-analysis")
@Tag(name = "Study server - Dynamic security analysis parameters")
public class DynamicSecurityAnalysisParametersController {
    private final StudyService studyService;

    public DynamicSecurityAnalysisParametersController(StudyService studyService) {
        this.studyService = studyService;
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "Set dynamic security analysis parameters on study, reset to default one if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis parameters are set")})
    public ResponseEntity<Void> setDynamicSecurityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String dsaParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setDynamicSecurityAnalysisParameters(studyUuid, dsaParameter, userId) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get dynamic security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis parameters")})
    public ResponseEntity<String> getDynamicSecurityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getDynamicSecurityAnalysisParameters(studyUuid));
    }

    @GetMapping(value = "/provider")
    @Operation(summary = "Get dynamic security analysis provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis provider is returned")})
    public ResponseEntity<String> getDynamicSecurityAnalysisProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getDynamicSecurityAnalysisProvider(studyUuid));
    }
}
