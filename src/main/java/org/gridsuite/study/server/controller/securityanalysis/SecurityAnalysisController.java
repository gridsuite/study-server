/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.controller.securityanalysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.SECURITY_ANALYSIS;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis")
@Tag(name = "Study server - Security analysis")
public class SecurityAnalysisController {
    private final StudyService studyService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final SecurityAnalysisService securityAnalysisService;
    private final NodeActivityService nodeActivityService;

    public SecurityAnalysisController(StudyService studyService, RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                      SecurityAnalysisService securityAnalysisService, NodeActivityService nodeActivityService) {
        this.studyService = studyService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.securityAnalysisService = securityAnalysisService;
        this.nodeActivityService = nodeActivityService;
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has started")})
    public ResponseEntity<Void> runSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                    @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                    @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(SECURITY_ANALYSIS, userId);
        nodeActivityService.runWithNodeActivity(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> securityAnalysisService.runSecurityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public ResponseEntity<String> getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                            @Parameter(description = "result type") @RequestParam(name = "resultType") SecurityAnalysisResultType resultType,
                                                            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                            @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                            Pageable pageable) {
        String result = rootNetworkNodeInfoService.getSecurityAnalysisResult(nodeUuid, rootNetworkUuid, resultType, filters, globalFilters, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/result/csv")
    @Operation(summary = "Get a security analysis result on study - CSV export")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result csv export"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public byte[] getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                            @Parameter(description = "result type") @RequestParam(name = "resultType") SecurityAnalysisResultType resultType,
                                            @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                            @Parameter(description = "Csv translation (JSON)") @RequestBody String csvTranslations,
                                            @Parameter(description = "Sort parameters") Sort sort) {
        return rootNetworkNodeInfoService.getSecurityAnalysisResultCsv(nodeUuid, rootNetworkUuid, resultType, globalFilters, filters, sort, csvTranslations);
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the security analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis status"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis status has not been found")})
    public ResponseEntity<String> getSecurityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = rootNetworkNodeInfoService.getSecurityAnalysisStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Void> stopSecurityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopSecurityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

}
