/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.voltageinit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.voltageinit.VoltageInitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init")
@Tag(name = "Study server - Voltage Init")
public class VoltageInitController {

    private final StudyService studyService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final VoltageInitService voltageInitService;
    private final NodeActivityRunnerService nodeActivityService;

    public VoltageInitController(StudyService studyService, RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                 VoltageInitService voltageInitService, NodeActivityRunnerService nodeActivityService) {
        this.studyService = studyService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.voltageInitService = voltageInitService;
        this.nodeActivityService = nodeActivityService;
    }

    @PutMapping(value = "/run")
    @Operation(summary = "run voltage init on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has started"),
        @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> runVoltageInit(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "debug") @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(VOLTAGE_INITIALIZATION, userId);
        nodeActivityService.runWithNodeActivity(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> voltageInitService.runVoltageInit(studyUuid, nodeUuid, rootNetworkUuid, userId, debug));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has been stopped")})
    public ResponseEntity<Void> stopVoltageInit(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopVoltageInit(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a voltage init result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init result"),
        @ApiResponse(responseCode = "204", description = "No voltage init has been done yet"),
        @ApiResponse(responseCode = "404", description = "The voltage init has not been found")})
    public ResponseEntity<String> getVoltageInitResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters) {
        String result = voltageInitService.getVoltageInitResult(nodeUuid, rootNetworkUuid, globalFilters);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the voltage init status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init status"),
        @ApiResponse(responseCode = "204", description = "No voltage init has been done yet"),
        @ApiResponse(responseCode = "404", description = "The voltage init status has not been found")})
    public ResponseEntity<String> getVoltageInitStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getVoltageInitStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }
}
