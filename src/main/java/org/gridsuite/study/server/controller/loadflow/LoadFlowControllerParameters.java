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
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/loadflow")
@Tag(name = "Study server - Load flow")
public class LoadFlowControllerParameters {
    private final StudyService studyService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public LoadFlowControllerParameters(StudyService studyService,
                                        NetworkModificationTreeService networkModificationTreeService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "set loadflow parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters are set"),
                           @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String lfParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertNoBlockedNodeInStudy(studyUuid, networkModificationTreeService.getStudyRootNodeUuid(studyUuid));
        return studyService.setLoadFlowParameters(studyUuid, lfParameter, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get loadflow parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters")})
    public ResponseEntity<LoadFlowParametersInfos> getLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowParametersInfos(studyUuid));
    }

    @GetMapping(value = "/parameters/id")
    @Operation(summary = "Get loadflow parameters ID for study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The loadflow parameters ID"),
        @ApiResponse(responseCode = "404", description = "The study is not found")
    })
    public ResponseEntity<UUID> getLoadflowParametersId(@PathVariable("studyUuid") UUID studyUuid) {
        UUID parametersId = studyService.getLoadFlowParametersId(studyUuid);
        return ResponseEntity.ok().body(parametersId);
    }

    @GetMapping(value = "/provider")
    @Operation(summary = "Get loadflow provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow provider is returned")})
    public ResponseEntity<String> getLoadFlowProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowProvider(studyUuid));
    }
}
