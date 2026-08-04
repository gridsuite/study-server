/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.nodeactivity.NodeActivityInfos;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/node-activities")
@Tag(name = "Study server - Node activities")
public class NodeActivityController {
    private final NodeActivityService nodeActivityService;

    public NodeActivityController(NodeActivityService nodeActivityService) {
        this.nodeActivityService = nodeActivityService;
    }

    @GetMapping
    @Operation(summary = "Get the activities running in a study")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The running activities, empty when the study is idle"))
    public ResponseEntity<List<NodeActivityInfos>> getNodeActivities(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(nodeActivityService.getNodeActivities(studyUuid));
    }
}
