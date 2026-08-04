/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.shortcircuit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.shortcircuit.FaultResultsMode;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.gridsuite.study.server.utils.ResultParameters;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.SHORT_CIRCUIT;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit")
@Tag(name = "Study server - Short Circuit")
public class ShortCircuitController {
    private final StudyService studyService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final ShortCircuitService shortCircuitService;
    private final NodeActivityService nodeActivityService;

    public ShortCircuitController(StudyService studyService, RootNetworkNodeInfoService rootNetworkNodeInfoService, ShortCircuitService shortCircuitService, NodeActivityService nodeActivityService) {
        this.studyService = studyService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.shortCircuitService = shortCircuitService;
        this.nodeActivityService = nodeActivityService;
    }

    @PutMapping(value = "/run")
    @Operation(summary = "run short circuit analysis on study")
    @ApiResponse(responseCode = "200", description = "The short circuit analysis has started")
    public ResponseEntity<Void> runShortCircuit(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestParam(value = "busId", required = false) Optional<String> busId,
            @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(SHORT_CIRCUIT, userId);
        nodeActivityService.setNodeActivityUntilResult(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> shortCircuitService.runShortCircuit(studyUuid, nodeUuid, rootNetworkUuid, busId, debug, userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis has been stopped")})
    public ResponseEntity<Void> stopShortCircuitAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                         @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopShortCircuitAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a short circuit analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis result"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis has not been found")})
    public ResponseEntity<String> getShortCircuitResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "root network UUID") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                        @Parameter(description = "node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @Parameter(description = "BASIC (faults without limits and feeders), " +
                                                                "FULL (faults with both), " +
                                                                "WITH_LIMIT_VIOLATIONS (like FULL but only those with limit violations) or " +
                                                                "NONE (no fault)") @RequestParam(name = "mode", required = false, defaultValue = "FULL") FaultResultsMode mode,
                                                        @Parameter(description = "type") @RequestParam(value = "type", required = false, defaultValue = "ALL_BUSES") ShortcircuitAnalysisType type,
                                                        @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                        @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                        @Parameter(description = "If we wanted the paged version of the results or not") @RequestParam(name = "paged", required = false,
                                                                defaultValue = "false") boolean paged,
                                                        Pageable pageable) {
        String result = rootNetworkNodeInfoService.getShortCircuitAnalysisResult(new ResultParameters(rootNetworkUuid, nodeUuid), mode, type, filters, globalFilters, paged, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the short circuit analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis status"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis status has not been found")})
    public ResponseEntity<String> getShortCircuitAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                @Parameter(description = "type") @RequestParam(value = "type", required = false,
                                                                        defaultValue = "ALL_BUSES") ShortcircuitAnalysisType type) {
        String result = rootNetworkNodeInfoService.getShortCircuitAnalysisStatus(nodeUuid, rootNetworkUuid, type);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/result/csv")
    @Operation(summary = "Get a short circuit analysis csv result")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis csv export"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis has not been found")})
    public ResponseEntity<byte[]> getShortCircuitAnalysisCsvResult(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "type") @RequestParam(value = "type") ShortcircuitAnalysisType type,
            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
            @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
            @Parameter(description = "headersCsv") @RequestBody String headersCsv,
            Sort sort) {
        return ResponseEntity.ok().body(rootNetworkNodeInfoService.getShortCircuitAnalysisCsvResult(nodeUuid, rootNetworkUuid, type, filters, globalFilters, sort, headersCsv));
    }
}
