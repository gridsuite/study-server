/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.loadflow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.computation.LoadFlowComputationInfos;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityType;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DYNA_FLOW_PROVIDER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE_AND_UNBUILD_CHILDREN;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow")
@Tag(name = "Study server - Load flow")
public class LoadFlowController {
    private final StudyService studyService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final LoadFlowService loadFlowService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final NodeActivityRunnerService nodeActivityService;

    public LoadFlowController(StudyService studyService,
                              RootNetworkNodeInfoService rootNetworkNodeInfoService, LoadFlowService loadFlowService,
                              NetworkModificationTreeService networkModificationTreeService,
                              NodeActivityRunnerService nodeActivityService) {
        this.studyService = studyService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.loadFlowService = loadFlowService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.nodeActivityService = nodeActivityService;
    }

    @PutMapping(value = "/run")
    @Operation(summary = "run loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has started")})
    public ResponseEntity<Void> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestParam(value = "withRatioTapChangers", required = false, defaultValue = "false") boolean withRatioTapChangers,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(LOAD_FLOW, userId);
        studyService.assertCanRunOnConstructionNode(studyUuid, nodeUuid, List.of(DYNA_FLOW_PROVIDER), loadFlowService::getLoadFlowProvider);
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        // a loadflow on a security node writes solved values onto its own variant and invalidates its children
        NodeActivityType activityType = networkModificationTreeService.isSecurityNode(nodeUuid)
            ? COMPUTE_AND_UNBUILD_CHILDREN : COMPUTE;
        nodeActivityService.runWithNodeActivity(activityType, studyUuid, rootNetworkUuid, List.of(nodeUuid), () -> {
            if (prevResultUuid != null) {
                handleRerunLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, prevResultUuid, withRatioTapChangers, userId);
            } else {
                studyService.sendLoadflowRequest(studyUuid, nodeUuid, rootNetworkUuid, null, withRatioTapChangers, userId);
            }
        });
        return ResponseEntity.ok().build();
    }

    /**
     * Need to have several transactions to send notifications by step
     * Disadvantage is that it is not atomic so need a try/catch to rollback
     */
    private void handleRerunLoadFlow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID prevResultUuid, Boolean withRatioTapChangers, String userId) {
        UUID loadflowResultUuid = null;
        try {
            loadFlowService.deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, prevResultUuid);
            loadflowResultUuid = loadFlowService.createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, withRatioTapChangers);
            studyService.rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
        } catch (Exception e) {
            if (loadflowResultUuid != null) {
                loadFlowService.deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid);
            }
            throw e;
        }
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a loadflow result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow result"),
        @ApiResponse(responseCode = "204", description = "No loadflow has been done yet"),
        @ApiResponse(responseCode = "404", description = "The loadflow result has not been found")})
    public ResponseEntity<String> getLoadflowResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                    @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                    @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                    Sort sort) {
        String result = rootNetworkNodeInfoService.getLoadFlowResult(nodeUuid, rootNetworkUuid, filters, sort);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the loadflow status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status"),
        @ApiResponse(responseCode = "204", description = "No loadflow has been done yet"),
        @ApiResponse(responseCode = "404", description = "The loadflow status has not been found")})
    public ResponseEntity<String> getLoadFlowStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getLoadFlowStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) : ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/computation-infos")
    @Operation(summary = "Get the loadflow computation infos on study node and root network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow computation infos"),
        @ApiResponse(responseCode = "404", description = "The loadflow computation has not been found")})
    public ResponseEntity<LoadFlowComputationInfos> getLoadFlowComputationInfos(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkNodeInfoService.getLoadFlowComputationInfos(nodeUuid, rootNetworkUuid));
    }

    @GetMapping(value = "/modifications")
    @Operation(summary = "Get the loadflow modifications on study node and root network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow computation infos"),
        @ApiResponse(responseCode = "404", description = "The loadflow computation has not been found")})
    public ResponseEntity<String> getLoadFlowModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkNodeInfoService.getLoadFlowModifications(nodeUuid, rootNetworkUuid));
    }

    @PutMapping(value = "/stop")
    @Operation(summary = "stop loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has been stopped")})
    public ResponseEntity<Void> stopLoadFlow(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                             @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                             @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                             @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }
}
