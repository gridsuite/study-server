/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicsimulation;

import com.powsybl.timeseries.DoubleTimeSeries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DYNAWO_PROVIDER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SIMULATION;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-simulation")
@Tag(name = "Study server - Dynamic simulation")
public class DynamicSimulationController {

    private final StudyService studyService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final NodeActivityRunnerService nodeActivityRunnerService;
    private final DynamicSimulationService dynamicSimulationService;

    public DynamicSimulationController(StudyService studyService, RootNetworkNodeInfoService rootNetworkNodeInfoService, DynamicSimulationService dynamicSimulationService,
                                       NodeActivityRunnerService nodeActivityRunnerService) {
        this.studyService = studyService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.nodeActivityRunnerService = nodeActivityRunnerService;
        this.dynamicSimulationService = dynamicSimulationService;
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation has started")})
    public ResponseEntity<Void> runDynamicSimulation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @Parameter(description = "debug") @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(DYNAMIC_SIMULATION, userId);
        studyService.assertCanRunOnConstructionNode(studyUuid, nodeUuid, List.of(DYNAWO_PROVIDER), dynamicSimulationService::getDynamicSimulationProvider);
        nodeActivityRunnerService.runWithNodeActivity(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> dynamicSimulationService.runDynamicSimulation(studyUuid, nodeUuid, rootNetworkUuid, userId, debug));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/result/timeseries/metadata")
    @Operation(summary = "Get list of time series metadata of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Time series metadata of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation metadata"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<TimeSeriesMetadataInfos>> getDynamicSimulationTimeSeriesMetadata(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<TimeSeriesMetadataInfos> result = rootNetworkNodeInfoService.getDynamicSimulationTimeSeriesMetadata(nodeUuid, rootNetworkUuid);
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/result/timeseries")
    @Operation(summary = "Get all time series of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All time series of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation timeseries"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<DoubleTimeSeries>> getDynamicSimulationTimeSeriesResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                                       @Parameter(description = "timeSeriesNames") @RequestParam(name = "timeSeriesNames",
                                                                                               required = false) List<String> timeSeriesNames) {
        List<DoubleTimeSeries> result = rootNetworkNodeInfoService.getDynamicSimulationTimeSeries(nodeUuid, rootNetworkUuid, timeSeriesNames);
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/result/timeline")
    @Operation(summary = "Get timeline events of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Timeline events of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation timeline events"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<TimelineEventInfos>> getDynamicSimulationTimelineResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<TimelineEventInfos> result = rootNetworkNodeInfoService.getDynamicSimulationTimeline(nodeUuid, rootNetworkUuid);
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the status of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The status of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation status"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<String> getDynamicSimulationStatus(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                             @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getDynamicSimulationStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) : ResponseEntity.noContent().build();
    }

}
