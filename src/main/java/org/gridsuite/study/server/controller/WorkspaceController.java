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
import org.gridsuite.study.server.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/workspaces")
@Tag(name = "Study server - Workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("")
    @Operation(summary = "Get workspaces metadata")
    @ApiResponse(responseCode = "200", description = "Workspaces metadata retrieved")
    @ApiResponse(responseCode = "404", description = "Study not found")
    public ResponseEntity<String> getWorkspaces(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok(workspaceService.getWorkspaces(studyUuid));
    }

    @GetMapping("/{workspaceId}")
    @Operation(summary = "Get a specific workspace")
    @ApiResponse(responseCode = "200", description = "Workspace retrieved")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<String> getWorkspace(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(workspaceService.getWorkspace(studyUuid, workspaceId));
    }

    @PutMapping("/{workspaceId}/name")
    @Operation(summary = "Rename a workspace")
    @ApiResponse(responseCode = "204", description = "Workspace renamed")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> renameWorkspace(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody String name) {
        workspaceService.renameWorkspace(studyUuid, workspaceId, name);
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
        return ResponseEntity.ok(workspaceService.getWorkspacePanels(studyUuid, workspaceId, ids));
    }

    @PostMapping("/{workspaceId}/panels")
    @Operation(summary = "Create or update panels in a workspace")
    @ApiResponse(responseCode = "204", description = "Panels created or updated")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> createOrUpdatePanels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody String panelsDto) {
        workspaceService.createOrUpdateWorkspacePanels(studyUuid, workspaceId, panelsDto);
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
        workspaceService.deleteWorkspacePanels(studyUuid, workspaceId, panelIds);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workspaceId}/panels/{panelId}/saved-nad-config")
    @Operation(summary = "Save NAD config")
    @ApiResponse(responseCode = "201", description = "NAD config saved")
    @ApiResponse(responseCode = "404", description = "Study not found")
    public ResponseEntity<UUID> saveNadConfig(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @PathVariable UUID panelId,
            @RequestBody Map<String, Object> nadConfigData) {
        UUID savedNadConfigUuid = workspaceService.saveNadConfig(studyUuid, workspaceId, panelId, nadConfigData);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedNadConfigUuid);
    }

    @DeleteMapping("/{workspaceId}/panels/{panelId}/saved-nad-config")
    @Operation(summary = "Delete saved NAD config")
    @ApiResponse(responseCode = "204", description = "Saved NAD config deleted")
    @ApiResponse(responseCode = "404", description = "Study not found")
    public ResponseEntity<Void> deleteNadConfig(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable UUID workspaceId,
            @PathVariable UUID panelId) {
        workspaceService.deleteNadConfig(studyUuid, workspaceId, panelId);
        return ResponseEntity.noContent().build();
    }
}
