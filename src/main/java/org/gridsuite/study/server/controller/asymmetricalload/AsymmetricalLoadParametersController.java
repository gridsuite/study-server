/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.asymmetricalload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.asymmetricalload.AsymmetricalLoadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/asymmetrical-load")
@Tag(name = "Study server - Asymmetrical Load")
public class AsymmetricalLoadParametersController {

    private final AsymmetricalLoadService asymmetricalLoadService;

    public AsymmetricalLoadParametersController(AsymmetricalLoadService asymmetricalLoadService) {
        this.asymmetricalLoadService = asymmetricalLoadService;
    }

    @GetMapping(value = "/parameters")
    @Operation(summary = "Get asymmetrical load parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The asymmetrical load parameters")})
    public ResponseEntity<String> getAsymmetricalLoadParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(asymmetricalLoadService.getAsymmetricalLoadParameters(studyUuid));
    }

    @PostMapping(value = "/parameters")
    @Operation(summary = "set asymmetrical load parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The asymmetrical load parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setAsymmetricalLoadParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String asymmetricalLoadParametersInfos,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return asymmetricalLoadService.setAsymmetricalLoadParameters(studyUuid, asymmetricalLoadParametersInfos, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }
}
