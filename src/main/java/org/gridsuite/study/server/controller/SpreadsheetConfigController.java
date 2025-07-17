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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/spreadsheet-config")
@Tag(name = "Study server - Spreadsheet configurations")
public class SpreadsheetConfigController {
    private final StudyService studyService;

    public SpreadsheetConfigController(StudyService studyService) {
        this.studyService = studyService;
    }

    @PostMapping("/{id}/columns")
    @Operation(summary = "Create a column", description = "Creates a new column")
    @ApiResponse(responseCode = "201", description = "Column created")
    public ResponseEntity<UUID> createColumn(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the spreadsheet config") @PathVariable UUID id,
            @Valid @RequestBody String columnInfos) {
        UUID newColumnId = studyService.createColumn(studyUuid, id, columnInfos);
        return ResponseEntity.status(HttpStatus.CREATED).body(newColumnId);
    }

    @PostMapping("/{id}/global-filters")
    @Operation(summary = "Set global filters",
            description = "Replaces all existing global filters with the provided list for a spreadsheet configuration")
    @ApiResponse(responseCode = "204", description = "Global filters set successfully")
    @ApiResponse(responseCode = "404", description = "Spreadsheet configuration not found")
    public ResponseEntity<Void> setGlobalFiltersForSpreadsheetConfig(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the spreadsheet config") @PathVariable UUID id,
            @RequestBody String filters) {
        studyService.setGlobalFilters(studyUuid, id, filters);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/columns/{columnUuid}")
    @Operation(summary = "Update a column", description = "Updates an existing column")
    @ApiResponse(responseCode = "204", description = "Column updated")
    public ResponseEntity<Void> updateColumn(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the spreadsheet config") @PathVariable UUID id,
            @Parameter(description = "ID of the column to update") @PathVariable UUID columnUuid,
            @Valid @RequestBody String columnInfos) {
        studyService.updateColumn(studyUuid, id, columnUuid, columnInfos);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/columns/{columnUuid}")
    @Operation(summary = "Delete a column", description = "Deletes an existing column")
    @ApiResponse(responseCode = "204", description = "Column deleted")
    @ApiResponse(responseCode = "404", description = "Column not found")
    public ResponseEntity<Void> deleteColumn(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the spreadsheet config") @PathVariable UUID id,
            @Parameter(description = "ID of the column to delete") @PathVariable UUID columnUuid) {
        studyService.deleteColumn(studyUuid, id, columnUuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/columns/{columnUuid}/duplicate")
    @Operation(summary = "duplicate a column", description = "duplicate an existing column")
    @ApiResponse(responseCode = "204", description = "Column duplicated")
    public ResponseEntity<Void> duplicateColumn(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the spreadsheet config") @PathVariable UUID id,
            @Parameter(description = "ID of the column to duplicate") @PathVariable UUID columnUuid) {
        studyService.duplicateColumn(studyUuid, id, columnUuid);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/columns/reorder")
    @Operation(summary = "Reorder columns", description = "Reorders the columns of a spreadsheet configuration")
    @ApiResponse(responseCode = "204", description = "Columns reordered")
    public ResponseEntity<Void> reorderColumns(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the spreadsheet config") @PathVariable UUID id,
            @Parameter(description = "New order of column IDs") @RequestBody List<UUID> columnOrder) {
        studyService.reorderColumns(studyUuid, id, columnOrder);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/name")
    @Operation(summary = "Rename a spreadsheet configuration",
            description = "Updates the name of an existing spreadsheet configuration")
    @ApiResponse(responseCode = "204", description = "Configuration renamed")
    @ApiResponse(responseCode = "404", description = "Configuration not found")
    public ResponseEntity<Void> renameSpreadsheetConfig(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the configuration to rename") @PathVariable UUID id,
            @Parameter(description = "New name for the configuration") @RequestBody String name) {
        studyService.renameSpreadsheetConfig(studyUuid, id, name);
        return ResponseEntity.noContent().build();
    }
}
