/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.shortcircuit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/short-circuit-analysis")
@Tag(name = "Study server - Short circuit parameters")
public class ShortCircuitControllerParameters {

    private final ShortCircuitService shortCircuitService;

    public ShortCircuitControllerParameters(ShortCircuitService shortCircuitService) {
        this.shortCircuitService = shortCircuitService;
    }

    @PostMapping(value = "/parameters", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "set short-circuit analysis parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setShortCircuitParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String shortCircuitParametersInfos,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return shortCircuitService.setShortCircuitParameters(studyUuid, shortCircuitParametersInfos, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get short-circuit analysis parameters on study")
    @ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters return by shortcircuit-server")
    public ResponseEntity<String> getShortCircuitParameters(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(shortCircuitService.getShortCircuitParametersInfo(studyUuid));
    }
}
