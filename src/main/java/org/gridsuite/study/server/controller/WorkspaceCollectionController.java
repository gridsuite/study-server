/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/workspace-collection")
@Tag(name = "Study server - Workspace collection")
public class WorkspaceCollectionController {
    private final StudyService studyService;

    public WorkspaceCollectionController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping()
    @Operation(summary = "Get study's workspace collection")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The workspace collection")})
    public ResponseEntity<String> getWorkspaceCollection(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getWorkspaceCollection(studyUuid));
    }

    @PostMapping()
    @Operation(summary = "Set workspace collection on study or reset to default one if empty body")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The workspace collection is set and returned"),
        @ApiResponse(responseCode = "204", description = "Reset to default (no content returned)")
    })
    public ResponseEntity<String> setWorkspaceCollection(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String workspaceCollection,
            @RequestHeader(HEADER_USER_ID) String userId) {
        String result = studyService.setWorkspaceCollection(studyUuid, workspaceCollection, userId);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }

    // Workspace endpoints
    @GetMapping("/workspaces")
    @Operation(summary = "Get all workspaces in a study's workspace collection")
    @ApiResponse(responseCode = "200", description = "Workspaces retrieved")
    @ApiResponse(responseCode = "404", description = "Study or workspace collection not found")
    public ResponseEntity<String> getWorkspaces(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok(studyService.getWorkspaces(studyUuid));
    }

    @GetMapping("/workspaces/{workspaceId}")
    @Operation(summary = "Get a specific workspace")
    @ApiResponse(responseCode = "200", description = "Workspace retrieved")
    @ApiResponse(responseCode = "404", description = "Study, workspace collection or workspace not found")
    public ResponseEntity<String> getWorkspace(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(studyService.getWorkspace(studyUuid, workspaceId));
    }

    @PutMapping("/workspaces/{workspaceId}")
    @Operation(summary = "Update a workspace")
    @ApiResponse(responseCode = "204", description = "Workspace updated")
    @ApiResponse(responseCode = "404", description = "Study, workspace collection or workspace not found")
    public ResponseEntity<Void> updateWorkspace(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody String workspaceDto) {
        studyService.updateWorkspace(studyUuid, workspaceId, workspaceDto);
        return ResponseEntity.noContent().build();
    }

    // Panel batch endpoints
    @GetMapping("/workspaces/{workspaceId}/panels")
    @Operation(summary = "Get panels from a workspace")
    @ApiResponse(responseCode = "200", description = "Panels retrieved")
    @ApiResponse(responseCode = "404", description = "Study, workspace collection or workspace not found")
    public ResponseEntity<String> getPanels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String ids) {
        return ResponseEntity.ok(studyService.getPanels(studyUuid, workspaceId, ids));
    }

    @PostMapping("/workspaces/{workspaceId}/panels")
    @Operation(summary = "Create or update panels in a workspace")
    @ApiResponse(responseCode = "204", description = "Panels created or updated")
    @ApiResponse(responseCode = "404", description = "Study, workspace collection or workspace not found")
    public ResponseEntity<Void> createOrUpdatePanels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody String panelsDto) {
        studyService.createOrUpdatePanels(studyUuid, workspaceId, panelsDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/workspaces/{workspaceId}/panels")
    @Operation(summary = "Delete panels from a workspace")
    @ApiResponse(responseCode = "204", description = "Panels deleted")
    @ApiResponse(responseCode = "404", description = "Study, workspace collection or workspace not found")
    public ResponseEntity<Void> deletePanels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody String panelIds) {
        studyService.deletePanels(studyUuid, workspaceId, panelIds);
        return ResponseEntity.noContent().build();
    }
}
