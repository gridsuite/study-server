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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
    @PreAuthorize("@authorizationService.canRead(#studyUuid)")
    public ResponseEntity<String> getSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(securityAnalysisService.getSecurityAnalysisParametersValues(studyUuid));
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "set security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis parameters are set")})
    @PreAuthorize("@authorizationService.canWrite(#studyUuid)")
    public ResponseEntity<Void> setSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody String securityAnalysisParametersValues) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        securityAnalysisService.setSecurityAnalysisParametersValues(studyUuid, securityAnalysisParametersValues, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/parameters/reset")
    @Operation(summary = "Reset security analysis parameters on study to the user profile or default ones")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The security analysis parameters were reset"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    @PreAuthorize("@authorizationService.canWrite(#studyUuid)")
    public ResponseEntity<Void> resetSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return securityAnalysisService.resetSecurityAnalysisParametersValues(studyUuid, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }
}
