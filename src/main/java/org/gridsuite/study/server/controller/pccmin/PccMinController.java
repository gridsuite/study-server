/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.pccmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.pccmin.PccMinService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.PCC_MIN;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min")
@Tag(name = "Study server - Pcc min")
public class PccMinController {
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final StudyService studyService;
    private final PccMinService pccMinService;
    private final NodeActivityRunnerService nodeActivityService;

    public PccMinController(RootNetworkNodeInfoService rootNetworkNodeInfoService, StudyService studyService, PccMinService pccMinService, NodeActivityRunnerService nodeActivityService) {
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.studyService = studyService;
        this.pccMinService = pccMinService;
        this.nodeActivityService = nodeActivityService;
    }

    @PostMapping(value = "/result/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a pcc min result as csv")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Csv of pcc min results"),
        @ApiResponse(responseCode = "204", description = "No pcc min has been done yet"),
        @ApiResponse(responseCode = "404", description = "The pcc min has not been found")})
    public ResponseEntity<byte[]> exportPccMinResultsAsCsv(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
            @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
            Sort sort, @RequestBody String csvHeaders) {
        return rootNetworkNodeInfoService.exportPccMinResultsAsCsv(nodeUuid, rootNetworkUuid, csvHeaders, sort, filters, globalFilters);
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run pcc min on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min has started")})
    public ResponseEntity<Void> runPccMin(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                          @RequestHeader(HEADER_USER_ID) String userId) {

        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(PCC_MIN, userId);
        nodeActivityService.runWithNodeActivity(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> pccMinService.runPccMin(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop pcc min on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min has been stopped")})
    public ResponseEntity<Void> stopPccMin(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                           @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        rootNetworkNodeInfoService.stopPccMin(studyUuid, nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a pcc min result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min result"),
        @ApiResponse(responseCode = "204", description = "No pcc min  has been done yet"),
        @ApiResponse(responseCode = "404", description = "The pcc min  has not been found")})
    public ResponseEntity<String> getPccMinResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                  @Parameter(description = "rootNetwork Uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @Parameter(description = "node Uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                  @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                  @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                  Pageable pageable) {
        String result = rootNetworkNodeInfoService.getPccMinResult(nodeUuid, rootNetworkUuid, filters, globalFilters, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the pcc min status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min status"),
        @ApiResponse(responseCode = "204", description = "No pcc min has been done yet"),
        @ApiResponse(responseCode = "404", description = "The pcc min status has not been found")})
    public ResponseEntity<String> getPccMinStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                  @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = rootNetworkNodeInfoService.getPccMinStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }

}
