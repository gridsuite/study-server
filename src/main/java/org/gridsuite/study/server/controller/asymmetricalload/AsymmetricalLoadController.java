/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.asymmetricalload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.asymmetricalload.AsymmetricalLoadService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.ASYMMETRICAL_LOAD;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/asymmetrical-load")
@Tag(name = "Study server - Asymmetrical load")
public class AsymmetricalLoadController {
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final StudyService studyService;
    private final AsymmetricalLoadService asymmetricalLoadService;
    private final NodeActivityRunnerService nodeActivityRunnerService;

    public AsymmetricalLoadController(RootNetworkNodeInfoService rootNetworkNodeInfoService, StudyService studyService, AsymmetricalLoadService asymmetricalLoadService,
                                      NodeActivityRunnerService nodeActivityRunnerService) {
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.studyService = studyService;
        this.asymmetricalLoadService = asymmetricalLoadService;
        this.nodeActivityRunnerService = nodeActivityRunnerService;
    }

    @PostMapping(value = "/result/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a asymmetrical load result as csv")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Csv of asymmetrical load results"),
        @ApiResponse(responseCode = "204", description = "No asymmetrical load has been done yet"),
        @ApiResponse(responseCode = "404", description = "The asymmetrical load has not been found")})
    public ResponseEntity<byte[]> exportAsymmetricalLoadResultsAsCsv(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
            @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
            Sort sort, @RequestBody String csvHeaders) {
        return rootNetworkNodeInfoService.exportAsymmetricalLoadResultsAsCsv(nodeUuid, rootNetworkUuid, csvHeaders, sort, filters, globalFilters);
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run asymmetrical load on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The asymmetrical load has started")})
    public ResponseEntity<Void> runAsymmetricalLoad(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                          @RequestHeader(HEADER_USER_ID) String userId) {

        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(ASYMMETRICAL_LOAD, userId);
        nodeActivityRunnerService.runWith(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> asymmetricalLoadService.runAsymmetricalLoad(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop asymmetrical load on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The asymmetrical load has been stopped")})
    public ResponseEntity<Void> stopAsymmetricalLoad(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                           @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        rootNetworkNodeInfoService.stopAsymmetricalLoad(studyUuid, nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a asymmetrical load result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The asymmetrical load result"),
        @ApiResponse(responseCode = "204", description = "No asymmetrical load  has been done yet"),
        @ApiResponse(responseCode = "404", description = "The asymmetrical load  has not been found")})
    public ResponseEntity<String> getAsymmetricalLoadResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                  @Parameter(description = "rootNetwork Uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @Parameter(description = "node Uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                  @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                  @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                  Pageable pageable) {
        String result = rootNetworkNodeInfoService.getAsymmetricalLoadResult(nodeUuid, rootNetworkUuid, filters, globalFilters, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the asymmetrical load status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The asymmetrical load status"),
        @ApiResponse(responseCode = "204", description = "No asymmetrical load has been done yet"),
        @ApiResponse(responseCode = "404", description = "The asymmetrical load status has not been found")})
    public ResponseEntity<String> getAsymmetricalLoadStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                  @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = rootNetworkNodeInfoService.getAsymmetricalLoadStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }

}
