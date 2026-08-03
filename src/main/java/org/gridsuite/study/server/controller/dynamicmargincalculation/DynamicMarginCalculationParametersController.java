/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicmargincalculation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/dynamic-margin-calculation")
@Tag(name = "Study server - Dynamic margin calculation")
public class DynamicMarginCalculationParametersController {

    private final DynamicMarginCalculationService dynamicMarginCalculationService;

    public DynamicMarginCalculationParametersController(DynamicMarginCalculationService dynamicMarginCalculationService) {
        this.dynamicMarginCalculationService = dynamicMarginCalculationService;
    }

    @GetMapping(value = "/provider")
    @Operation(summary = "Get dynamic margin calculation provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic margin calculation provider is returned")})
    public ResponseEntity<String> getDynamicMarginCalculationProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(dynamicMarginCalculationService.getDynamicMarginCalculationProvider(studyUuid));
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "Set dynamic margin calculation parameters on study, reset to default one if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic margin calculation parameters are set")})
    public ResponseEntity<Void> setDynamicMarginCalculationParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String dmcParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return dynamicMarginCalculationService.setDynamicMarginCalculationParameters(studyUuid, dmcParameter, userId) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get dynamic margin calculation parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic margin calculation parameters")})
    public ResponseEntity<String> getDynamicMarginCalculationParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestHeader(HEADER_USER_ID) String userId
    ) {
        return ResponseEntity.ok().body(dynamicMarginCalculationService.getDynamicMarginCalculationParameters(studyUuid, userId));
    }
}
