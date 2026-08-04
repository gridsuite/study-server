/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.sensitivityanalysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.sensitivityanalysis.SensitivityAnalysisRestService;
import org.gridsuite.study.server.service.sensitivityanalysis.SensitivityAnalysisService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.SENSITIVITY_ANALYSIS;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis")
@Tag(name = "Study server - Sensitivity analysis")
public class SensitivityAnalysisController {
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final StudyService studyService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final SensitivityAnalysisRestService sensitivityAnalysisRestService;
    private final RootNetworkService rootNetworkService;
    private final NodeActivityService nodeActivityService;

    public SensitivityAnalysisController(RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                         StudyService studyService,
                                         SensitivityAnalysisService sensitivityAnalysisService,
                                         NetworkModificationTreeService networkModificationTreeService,
                                         SensitivityAnalysisRestService sensitivityAnalysisRestService,
                                         RootNetworkService rootNetworkService,
                                         NodeActivityService nodeActivityService) {
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.studyService = studyService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.sensitivityAnalysisRestService = sensitivityAnalysisRestService;
        this.rootNetworkService = rootNetworkService;
        this.nodeActivityService = nodeActivityService;
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run sensitivity analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has started"), @ApiResponse(responseCode = "403",
            description = "The study node is not a model node")})
    public ResponseEntity<Void> runSensitivityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(SENSITIVITY_ANALYSIS, userId);
        nodeActivityService.setNodeActivityUntilResult(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> sensitivityAnalysisService.runSensitivityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a sensitivity analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisResult(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "results selector") @RequestParam("selector") String selector,
            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
            @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters
    ) {
        String result = rootNetworkNodeInfoService.getSensitivityAnalysisResult(nodeUuid, rootNetworkUuid, selector, filters, globalFilters);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/result/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a sensitivity analysis result as csv")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Csv of sensitivity analysis results"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<byte[]> exportSensitivityResultsAsCsv(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "results selector") @RequestParam("selector") String selector,
            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
            @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
            @RequestBody SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        byte[] result = rootNetworkNodeInfoService.exportSensitivityResultsAsCsv(nodeUuid, rootNetworkUuid, sensitivityAnalysisCsvFileInfos, selector, filters, globalFilters);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDispositionFormData("attachment", "sensitivity_results.csv");

        return ResponseEntity
                .ok()
                .headers(responseHeaders)
                .body(result);
    }

    @GetMapping(value = "/result/filter-options")
    @Operation(summary = "Get sensitivity analysis filter options on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis filter options"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisFilterOptions(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "results selector") @RequestParam("selector") String selector) {
        String result = rootNetworkNodeInfoService.getSensitivityResultsFilterOptions(nodeUuid, rootNetworkUuid, selector);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the sensitivity analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis status"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis status has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                               @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getSensitivityAnalysisStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop sensitivity analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has been stopped")})
    public ResponseEntity<Void> stopSensitivityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopSensitivityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/factor-count")
    @Operation(summary = "Get the factor count of sensitivity parameters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The factors count of sensitivity parameters")})
    public ResponseEntity<String> getSensitivityAnalysisFactorCount(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody String sensitivityAnalysisParameters) {
        return ResponseEntity.ok().body(sensitivityAnalysisRestService.getSensitivityAnalysisFactorCount(rootNetworkService.getNetworkUuid(rootNetworkUuid),
                networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid), sensitivityAnalysisParameters));
    }

}
