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
import org.gridsuite.study.server.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/spreadsheet-config-collection")
@Tag(name = "Study server - Spreadsheet collections")
public class SpreadsheetConfigCollectionController {
    private final StudyService studyService;

    public SpreadsheetConfigCollectionController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping()
    @Operation(summary = "Get study's spreadsheet config collection")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The spreadsheet config collection")})
    public ResponseEntity<String> getSpreadsheetConfigCollection(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getSpreadsheetConfigCollection(studyUuid));
    }

    @PutMapping()
    @Operation(summary = "Update study's spreadsheet config collection, with replace or append mode")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The updated spreadsheet config collection"),
        @ApiResponse(responseCode = "404", description = "The study or the collection doesn't exist")
    })
    public ResponseEntity<String> updateSpreadsheetConfigCollection(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestParam("collectionUuid") UUID collectionUuid,
            @RequestParam(value = "append", required = false, defaultValue = "false") Boolean appendMode) {
        return ResponseEntity.ok().body(studyService.updateSpreadsheetConfigCollection(studyUuid, collectionUuid, appendMode));
    }

    @PostMapping()
    @Operation(summary = "Set spreadsheet config collection on study, reset to default one if empty body")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The spreadsheet config collection is set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")
    })
    public ResponseEntity<Void> setSpreadsheetConfigCollection(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String configCollection,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setSpreadsheetConfigCollection(studyUuid, configCollection, userId) ?
                ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/spreadsheet-configs")
    @Operation(summary = "Add a spreadsheet configuration to a collection",
            description = "Adds a new spreadsheet configuration to a collection")
    @ApiResponse(responseCode = "201", description = "Configuration added")
    @ApiResponse(responseCode = "404", description = "Configuration collection not found")
    public ResponseEntity<UUID> addSpreadsheetConfigToCollection(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the configuration collection") @PathVariable UUID id,
            @Parameter(description = "Configuration to add") @Valid @RequestBody String configurationDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studyService.addSpreadsheetConfigToCollection(studyUuid, id, configurationDto));
    }

    @DeleteMapping("/{id}/spreadsheet-configs/{configId}")
    @Operation(summary = "Remove a spreadsheet configuration from a collection",
            description = "Removes an existing spreadsheet configuration from a collection")
    @ApiResponse(responseCode = "204", description = "Configuration removed")
    @ApiResponse(responseCode = "404", description = "Configuration collection or configuration not found")
    public ResponseEntity<Void> removeSpreadsheetConfigFromCollection(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the configuration collection") @PathVariable UUID id,
            @Parameter(description = "ID of the configuration to remove") @PathVariable UUID configId) {
        studyService.removeSpreadsheetConfigFromCollection(studyUuid, id, configId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reorder")
    @Operation(summary = "Reorder spreadsheet configs in a collection",
            description = "Updates the order of spreadsheet configs within a collection")
    @ApiResponse(responseCode = "204", description = "Order updated")
    @ApiResponse(responseCode = "404", description = "Collection not found")
    public ResponseEntity<Void> reorderSpreadsheetConfigs(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "ID of the configuration collection") @PathVariable UUID id,
            @Valid @RequestBody List<UUID> newOrder) {
        studyService.reorderSpreadsheetConfigs(studyUuid, id, newOrder);
        return ResponseEntity.noContent().build();
    }
}
