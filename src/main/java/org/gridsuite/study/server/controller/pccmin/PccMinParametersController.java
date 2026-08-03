/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.pccmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.pccmin.PccMinService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/pcc-min")
@Tag(name = "Study server - Pcc min")
public class PccMinParametersController {

    private final PccMinService pccMinService;

    public PccMinParametersController(PccMinService pccMinService) {
        this.pccMinService = pccMinService;
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get pcc min parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min parameters")})
    public ResponseEntity<String> getPccMinParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(pccMinService.getPccMinParameters(studyUuid));
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "set pcc min parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setPccMinParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String pccMinParametersInfos,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return pccMinService.setPccMinParameters(studyUuid, pccMinParametersInfos, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }
}
