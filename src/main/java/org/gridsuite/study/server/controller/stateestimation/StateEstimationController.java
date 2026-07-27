/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.stateestimation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.STATE_ESTIMATION;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/state-estimation")
@Tag(name = "Study server - State Estimation")
public class StateEstimationController {


    private final StudyService studyService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    public StateEstimationController(StudyService studyService, RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        this.studyService = studyService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run state estimation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation has started")})
    public ResponseEntity<Void> runStateEstimation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                   @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                   @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                   @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
                                                   @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(STATE_ESTIMATION, userId);
        studyService.runStateEstimation(studyUuid, nodeUuid, rootNetworkUuid, userId, debug);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a state estimation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation result"),
            @ApiResponse(responseCode = "204", description = "No state estimation has been done yet"),
            @ApiResponse(responseCode = "404", description = "The state estimation has not been found")})
    public ResponseEntity<String> getStateEstimationResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getStateEstimationResult(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the state estimation status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation status"),
            @ApiResponse(responseCode = "204", description = "No state estimation has been done yet"),
            @ApiResponse(responseCode = "404", description = "The state estimation status has not been found")})
    public ResponseEntity<String> getStateEstimationStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = rootNetworkNodeInfoService.getStateEstimationStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop state estimation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation has been stopped")})
    public ResponseEntity<Void> stopStateEstimation(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                    @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        rootNetworkNodeInfoService.stopStateEstimation(studyUuid, nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }
}
