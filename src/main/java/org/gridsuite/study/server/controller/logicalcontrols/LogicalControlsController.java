/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.logicalcontrols;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.logicalcontrols.LogicalControlsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/logical-controls")
@Tag(name = "Study server - Logical controls")
public class LogicalControlsController {
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final StudyService studyService;
    private final LogicalControlsService logicalControlsService;
    private final NodeActivityRunnerService nodeActivityRunnerService;

    public LogicalControlsController(RootNetworkNodeInfoService rootNetworkNodeInfoService, StudyService studyService, LogicalControlsService logicalControlsService,
                                     NodeActivityRunnerService nodeActivityRunnerService) {
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.studyService = studyService;
        this.logicalControlsService = logicalControlsService;
        this.nodeActivityRunnerService = nodeActivityRunnerService;
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run logical controls on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The logical controls has started")})
    public ResponseEntity<Void> runLogicalControls(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                   @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                   @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                   @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        // no quota on this computation
        nodeActivityRunnerService.runWith(COMPUTE, studyUuid, rootNetworkUuid, List.of(nodeUuid),
            () -> logicalControlsService.runLogicalControls(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/result")
    @Operation(summary = "Get a logical controls result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The logical controls result"),
        @ApiResponse(responseCode = "204", description = "No logical controls has been done yet"),
        @ApiResponse(responseCode = "404", description = "The logical controls has not been found")})
    public ResponseEntity<String> getLogicalControlsResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "rootNetwork Uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                           @Parameter(description = "node Uuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getLogicalControlsResult(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the logical controls status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The logical controls status"),
        @ApiResponse(responseCode = "204", description = "No logical controls has been done yet"),
        @ApiResponse(responseCode = "404", description = "The logical controls status has not been found")})
    public ResponseEntity<String> getLogicalControlsStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = rootNetworkNodeInfoService.getLogicalControlsStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }
}
