/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicsimulation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation")
@Tag(name = "Study server - Dynamic simulation events")
public class DynamicSimulationEventsController {
    private final StudyService studyService;

    public DynamicSimulationEventsController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping(value = "/events")
    @Operation(summary = "Get dynamic simulation events from a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation events was returned"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<EventInfos>> getDynamicSimulationEvents(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                       @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<EventInfos> dynamicSimulationEvents = studyService.getDynamicSimulationEvents(nodeUuid);
        return ResponseEntity.ok().body(dynamicSimulationEvents);
    }

    @GetMapping(value = "/events", params = {"equipmentId"})
    @Operation(summary = "Get dynamic simulation event from a node with a given equipment id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation event was returned"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<EventInfos> getDynamicSimulationEvent(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                @Parameter(description = "Equipment id") @RequestParam(value = "equipmentId") String equipmentId) {
        EventInfos dynamicSimulationEvent = studyService.getDynamicSimulationEvent(nodeUuid, equipmentId);
        return dynamicSimulationEvent != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSimulationEvent) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/events")
    @Operation(summary = "Create a dynamic simulation event for a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The network event was created"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> createDynamicSimulationEvent(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestBody EventInfos event,
                                                             @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateNodeInStudy(studyUuid, nodeUuid);
        studyService.createDynamicSimulationEvent(studyUuid, nodeUuid, userId, event);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/events")
    @Operation(summary = "Update a dynamic simulation event for a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation event was updated"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateDynamicSimulationEvent(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestBody EventInfos event,
                                                             @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateNodeInStudy(studyUuid, nodeUuid);
        studyService.updateDynamicSimulationEvent(studyUuid, nodeUuid, userId, event);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/events")
    @Operation(summary = "Delete dynamic simulation events for a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation events was deleted"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteDynamicSimulationEvents(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                              @Parameter(description = "Dynamic simulation event UUIDs") @RequestParam("eventUuids") List<UUID> eventUuids,
                                                              @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateNodeInStudy(studyUuid, nodeUuid);
        studyService.deleteDynamicSimulationEvents(studyUuid, nodeUuid, userId, eventUuids);
        return ResponseEntity.ok().build();
    }
}
