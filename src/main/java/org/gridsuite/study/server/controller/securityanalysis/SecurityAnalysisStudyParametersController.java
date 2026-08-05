/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.controller.securityanalysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/security-analysis")
@Tag(name = "Study server - Security analysis parameters")
public class SecurityAnalysisStudyParametersController {
    private final SecurityAnalysisService securityAnalysisService;

    public SecurityAnalysisStudyParametersController(SecurityAnalysisService securityAnalysisService) {
        this.securityAnalysisService = securityAnalysisService;
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis parameters")})
    public ResponseEntity<String> getSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(securityAnalysisService.getSecurityAnalysisParametersValues(studyUuid));
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "set security analysis parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String securityAnalysisParametersValues,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return securityAnalysisService.setSecurityAnalysisParametersValues(studyUuid, securityAnalysisParametersValues, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }
}
