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
            @PathVariable UUID studyUuid) {
        return ResponseEntity.ok(workspaceService.getWorkspaces(studyUuid));
    }

    @GetMapping("/{workspaceId}")
    @Operation(summary = "Get a specific workspace")
    @ApiResponse(responseCode = "200", description = "Workspace retrieved")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<String> getWorkspace(
            @PathVariable UUID studyUuid,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(workspaceService.getWorkspace(studyUuid, workspaceId));
    }

    @PutMapping("/{workspaceId}/name")
    @Operation(summary = "Rename a workspace")
    @ApiResponse(responseCode = "204", description = "Workspace renamed")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> renameWorkspace(
            @PathVariable UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody String name,
            @RequestHeader(value = "clientId", required = false) String clientId) {
        workspaceService.renameWorkspace(studyUuid, workspaceId, name, clientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/panels")
    @Operation(summary = "Get panels from a workspace")
    @ApiResponse(responseCode = "200", description = "Panels retrieved")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<String> getPanels(
            @PathVariable UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) List<String> panelIds) {
        return ResponseEntity.ok(workspaceService.getWorkspacePanels(studyUuid, workspaceId, panelIds));
    }

    @PostMapping("/{workspaceId}/panels")
    @Operation(summary = "Create or update panels in a workspace")
    @ApiResponse(responseCode = "204", description = "Panels created or updated")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> createOrUpdatePanels(
            @PathVariable UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody String panelsDto,
            @RequestHeader(value = "clientId", required = false) String clientId) {
        workspaceService.createOrUpdateWorkspacePanels(studyUuid, workspaceId, panelsDto, clientId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{workspaceId}/panels")
    @Operation(summary = "Delete panels from a workspace")
    @ApiResponse(responseCode = "204", description = "Panels deleted")
    @ApiResponse(responseCode = "404", description = "Study or workspace not found")
    public ResponseEntity<Void> deletePanels(
            @PathVariable UUID studyUuid,
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) String panelIds,
            @RequestHeader(value = "clientId", required = false) String clientId) {
        workspaceService.deleteWorkspacePanels(studyUuid, workspaceId, panelIds, clientId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workspaceId}/panels/{panelId}/current-nad-config")
    @Operation(summary = "Save NAD config")
    @ApiResponse(responseCode = "201", description = "NAD config saved")
    @ApiResponse(responseCode = "404", description = "Study not found")
    public ResponseEntity<UUID> saveNadConfig(
            @PathVariable UUID studyUuid,
            @PathVariable UUID workspaceId,
            @PathVariable UUID panelId,
            @RequestBody Map<String, Object> nadConfigData,
            @RequestHeader(value = "clientId", required = false) String clientId) {
        UUID configUuid = workspaceService.saveNadConfig(studyUuid, workspaceId, panelId, nadConfigData, clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(configUuid);
    }

    @DeleteMapping("/{workspaceId}/panels/{panelId}/current-nad-config")
    @Operation(summary = "Delete current NAD config")
    @ApiResponse(responseCode = "204", description = "Current NAD config deleted")
    @ApiResponse(responseCode = "404", description = "Study not found")
    public ResponseEntity<Void> deleteNadConfig(
            @PathVariable UUID studyUuid,
            @PathVariable UUID workspaceId,
            @PathVariable UUID panelId) {
        workspaceService.deleteNadConfig(studyUuid, workspaceId, panelId);
        return ResponseEntity.noContent().build();
    }
}
