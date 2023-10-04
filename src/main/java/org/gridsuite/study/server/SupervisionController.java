/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.service.SupervisionService;
import org.gridsuite.study.server.dto.ComputationType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/supervision")
@Tag(name = "Study server - Supervision")
public class SupervisionController {
    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @DeleteMapping(value = "/computation/results")
    @Operation(summary = "delete all results of a given computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all loadflow results have been deleted")})
    public ResponseEntity<Integer> deleteComputationResults(@Parameter(description = "Computation type") @RequestParam("type") ComputationType computationType,
                                                           @Parameter(description = "Dry run") @RequestParam("dryRun") boolean dryRun) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.deleteComputationResults(computationType, dryRun));
    }

    @DeleteMapping(value = "/indexed-equipments")
    @Operation(summary = "delete all indexed equipments for all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all indexed equipments have been deleted")})
    public ResponseEntity<Long> deleteAllStudiesIndexedEquipments(@Parameter(description = "Dry run") @RequestParam("dryRun") boolean dryRun) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.deleteAllStudiesIndexedEquipments(dryRun));
    }
}
