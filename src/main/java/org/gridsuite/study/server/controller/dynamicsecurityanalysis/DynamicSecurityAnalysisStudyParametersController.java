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
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/dynamic-security-analysis")
@Tag(name = "Study server - Dynamic security analysis parameters")
public class DynamicSecurityAnalysisStudyParametersController {
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    public DynamicSecurityAnalysisStudyParametersController(DynamicSecurityAnalysisService dynamicSecurityAnalysisService) {
        this.dynamicSecurityAnalysisService = dynamicSecurityAnalysisService;
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "Set dynamic security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis parameters are set")})
    @PreAuthorize("@authorizationService.canWrite(#studyUuid)")
    public ResponseEntity<Void> setDynamicSecurityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody String dsaParameter) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        dynamicSecurityAnalysisService.setDynamicSecurityAnalysisParameters(studyUuid, dsaParameter, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/parameters/reset")
    @Operation(summary = "Reset dynamic security analysis parameters on study to the user profile or default ones")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis parameters are reset"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    @PreAuthorize("@authorizationService.canWrite(#studyUuid)")
    public ResponseEntity<Void> resetDynamicSecurityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return dynamicSecurityAnalysisService.resetDynamicSecurityAnalysisParameters(studyUuid, userId) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get dynamic security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis parameters")})
    @PreAuthorize("@authorizationService.canRead(#studyUuid)")
    public ResponseEntity<String> getDynamicSecurityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(dynamicSecurityAnalysisService.getDynamicSecurityAnalysisParameters(studyUuid));
    }

    @GetMapping(value = "/provider")
    @Operation(summary = "Get dynamic security analysis provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis provider is returned")})
    @PreAuthorize("@authorizationService.canRead(#studyUuid)")
    public ResponseEntity<String> getDynamicSecurityAnalysisProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(dynamicSecurityAnalysisService.getDynamicSecurityAnalysisProvider(studyUuid));
    }
}
