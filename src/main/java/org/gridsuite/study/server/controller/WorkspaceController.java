/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/workspaces")
@Tag(name = "Study server - Workspaces")
public class WorkspaceController {
    private final StudyService studyService;

    public WorkspaceController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping("")
    @Operation(summary = "Get workspaces metadata")
    @ApiResponse(responseCode = "200", description = "Workspaces metadata retrieved")
    @ApiResponse(responseCode = "404", description = "Study not found")
    public ResponseEntity<String> getWorkspaces(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok(studyService.getWorkspaces(studyUuid));
    }

    @GetMapping("/{workspaceId}")
    @Operation(summary = "Get a specific workspace")
    @ApiResponse(responseCode = "200", description = "Workspace retrieved")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<String> getWorkspace(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(studyService.getWorkspace(studyUuid, workspaceId));
    }

    @PutMapping("/{workspaceId}/name")
    @Operation(summary = "Rename a workspace")
    @ApiResponse(responseCode = "204", description = "Workspace renamed")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> renameWorkspace(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody String name) {
        studyService.renameWorkspace(studyUuid, workspaceId, name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/panels")
    @Operation(summary = "Get panels from a workspace")
    @ApiResponse(responseCode = "200", description = "Panels retrieved")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<String> getPanels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) List<String> ids) {
        return ResponseEntity.ok(studyService.getWorkspacePanels(studyUuid, workspaceId, ids));
    }

    @PostMapping("/{workspaceId}/panels")
    @Operation(summary = "Create or update panels in a workspace")
    @ApiResponse(responseCode = "204", description = "Panels created or updated")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> createOrUpdatePanels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody String panelsDto) {
        studyService.createOrUpdateWorkspacePanels(studyUuid, workspaceId, panelsDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{workspaceId}/panels")
    @Operation(summary = "Delete panels from a workspace")
    @ApiResponse(responseCode = "204", description = "Panels deleted")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> deletePanels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody String panelIds) {
        studyService.deleteWorkspacePanels(studyUuid, workspaceId, panelIds);
        return ResponseEntity.noContent().build();
    }
}
