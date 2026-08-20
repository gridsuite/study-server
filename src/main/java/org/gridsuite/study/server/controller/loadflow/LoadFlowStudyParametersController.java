/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.loadflow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.UNBUILD_ALL;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/loadflow")
@Tag(name = "Study server - Load flow parameters")
public class LoadFlowStudyParametersController {
    private final StudyService studyService;
    private final NodeActivityRunnerService nodeActivityService;
    private final LoadFlowService loadFlowService;

    public LoadFlowStudyParametersController(StudyService studyService,
                                             NodeActivityRunnerService nodeActivityService, LoadFlowService loadFlowService) {
        this.studyService = studyService;
        this.nodeActivityService = nodeActivityService;
        this.loadFlowService = loadFlowService;
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "set loadflow parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters are set"),
                           @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String lfParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        // only what this actually unbuilds: the security nodes holding a loadflow result, and their children
        boolean userProfileIssue = nodeActivityService.runWithNodeActivity(UNBUILD_ALL, studyUuid,
            studyService.getNodesInvalidatedByLoadFlowParameters(studyUuid),
            () -> studyService.setLoadFlowParameters(studyUuid, lfParameter, userId));
        return userProfileIssue ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get loadflow parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters")})
    public ResponseEntity<LoadFlowParametersInfos> getLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(loadFlowService.getLoadFlowParametersInfos(studyUuid));
    }

    @GetMapping(value = "/parameters/id")
    @Operation(summary = "Get loadflow parameters ID for study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The loadflow parameters ID"),
        @ApiResponse(responseCode = "404", description = "The study is not found")
    })
    public ResponseEntity<UUID> getLoadflowParametersId(@PathVariable("studyUuid") UUID studyUuid) {
        UUID parametersId = loadFlowService.getLoadFlowParametersId(studyUuid);
        return ResponseEntity.ok().body(parametersId);
    }

    @GetMapping(value = "/provider")
    @Operation(summary = "Get loadflow provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow provider is returned")})
    public ResponseEntity<String> getLoadFlowProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(loadFlowService.getLoadFlowProvider(studyUuid));
    }
}
