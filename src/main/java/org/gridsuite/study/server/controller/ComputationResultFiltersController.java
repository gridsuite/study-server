/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
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
import jakarta.validation.Valid;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/computation-result-filters")
@Tag(name = "Study server - Computation result filters")
public class ComputationResultFiltersController {
    private final StudyService studyService;

    public ComputationResultFiltersController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping()
    @Operation(summary = "Get study's computation result filters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The computation result filters")})
    public ResponseEntity<String> getComputationResultFilters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getComputationResultFilters(studyUuid));
    }

    @PostMapping("/{id}/global-filters")
    @Operation(summary = "Set global filters",
            description = "Replaces all existing global filters with the provided list for a computation result")
    @ApiResponse(responseCode = "204", description = "Global filters set successfully")
    @ApiResponse(responseCode = "404", description = "computation result global filters not found")
    public ResponseEntity<Void> setGlobalFiltersForComputationResult(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the global filters") @PathVariable UUID id,
            @RequestBody String filters) {
        studyService.setGlobalFiltersForComputationResult(studyUuid, id, filters);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/columns/{columnUuid}")
    @Operation(summary = "Update a column", description = "Updates an existing column")
    @ApiResponse(responseCode = "204", description = "Column updated")
    public ResponseEntity<Void> updateColumns(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the spreadsheet config") @PathVariable UUID id,
            @Parameter(description = "ID of the column to update") @PathVariable UUID columnUuid,
            @Valid @RequestBody String columnInfos) {
        studyService.updateColumns(studyUuid, id, columnUuid, columnInfos);
        return ResponseEntity.noContent().build();
    }
}
