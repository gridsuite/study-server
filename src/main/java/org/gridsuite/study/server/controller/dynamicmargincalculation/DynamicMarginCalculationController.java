/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicmargincalculation;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DYNAWO_PROVIDER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_MARGIN_CALCULATION;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-margin-calculation")
@Tag(name = "Study server - Dynamic margin calculation")
public class DynamicMarginCalculationController {

    private final StudyService studyService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final DynamicMarginCalculationService dynamicMarginCalculationService;

    public DynamicMarginCalculationController(StudyService studyService,
                                              RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                              DynamicMarginCalculationService dynamicMarginCalculationService) {
        this.studyService = studyService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.dynamicMarginCalculationService = dynamicMarginCalculationService;
    }

    @PostMapping(value = "/run")
    @Operation(summary = "run dynamic margin calculation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic margin calculation has started")})
    public ResponseEntity<Void> runDynamicMarginCalculation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                            @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                            @Parameter(description = "debug") @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
                                                            @RequestHeader(HEADER_USER_ID) String userId) throws JsonProcessingException {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertOnQuotasAvailability(DYNAMIC_MARGIN_CALCULATION, userId);
        studyService.assertCanRunOnConstructionNode(studyUuid, nodeUuid, List.of(DYNAWO_PROVIDER), dynamicMarginCalculationService::getDynamicMarginCalculationProvider);
        dynamicMarginCalculationService.runDynamicMarginCalculation(studyUuid, nodeUuid, rootNetworkUuid, userId, debug);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/status")
    @Operation(summary = "Get the status of dynamic margin calculation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The status of dynamic margin calculation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic margin calculation status"),
        @ApiResponse(responseCode = "404", description = "The dynamic margin calculation has not been found")})
    public ResponseEntity<String> getDynamicMarginCalculationStatus(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                    @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getDynamicMarginCalculationStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) : ResponseEntity.noContent().build();
    }
}
