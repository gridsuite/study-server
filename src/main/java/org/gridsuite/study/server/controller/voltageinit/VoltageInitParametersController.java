/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.voltageinit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.service.voltageinit.VoltageInitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/voltage-init")
@Tag(name = "Study server - Voltage init parameters")
public class VoltageInitParametersController {

    private final VoltageInitService voltageInitService;

    public VoltageInitParametersController(VoltageInitService voltageInitService) {
        this.voltageInitService = voltageInitService;
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "Set voltage init parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setVoltageInitParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) StudyVoltageInitParameters voltageInitParameters,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return voltageInitService.setVoltageInitParameters(studyUuid, voltageInitParameters, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get voltage init parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init parameters")})
    public ResponseEntity<StudyVoltageInitParameters> getVoltageInitParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(voltageInitService.getVoltageInitParameters(studyUuid));
    }

}
