/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.supervision.SupervisionStudyInfos;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.SupervisionService;

import java.util.List;
import java.util.UUID;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
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

    private final StudyService studyService;

    private final EquipmentInfosService equipmentInfosService;

    private final RestClient restClient;

    public SupervisionController(SupervisionService supervisionService, StudyService studyService, EquipmentInfosService equipmentInfosService, RestClient restClient) {
        this.supervisionService = supervisionService;
        this.studyService = studyService;
        this.equipmentInfosService = equipmentInfosService;
        this.restClient = restClient;
    }

    @GetMapping(value = "/studies")
    @Operation(summary = "Get all the studies basic data")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of all the studies uuids, and some extra basic data")})
    public ResponseEntity<List<SupervisionStudyInfos>> getAllStudiesBasicData() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.getStudies());
    }

    @DeleteMapping(value = "/computation/results")
    @Operation(summary = "delete all results of a given computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all computation results have been deleted")})
    public ResponseEntity<Integer> deleteComputationResults(@Parameter(description = "Computation type") @RequestParam("type") ComputationType computationType,
                                                           @Parameter(description = "Dry run") @RequestParam("dryRun") boolean dryRun) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.deleteComputationResults(computationType, dryRun));
    }

    @GetMapping(value = "/elasticsearch-host")
    @Operation(summary = "get the elasticsearch address")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "the elasticsearch address")})
    public ResponseEntity<String> getElasticsearchHost() {
        HttpHost httpHost = restClient.getNodes().get(0).getHost();
        String host = httpHost.getHostName()
                        + ":"
                        + httpHost.getPort();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(host);
    }

    @GetMapping(value = "/studies/index-name")
    @Operation(summary = "get the indexed studies index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed studies index name")})
    public ResponseEntity<String> getIndexedStudiesIndexName() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(equipmentInfosService.getStudyIndexName());
    }

    @GetMapping(value = "/equipments/index-name")
    @Operation(summary = "get the indexed equipments index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed equipments index name")})
    public ResponseEntity<String> getIndexedEquipmentsIndexName() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(equipmentInfosService.getEquipmentsIndexName());
    }

    @GetMapping(value = "/tombstoned-equipments/index-name")
    @Operation(summary = "get the indexed tombstoned equipments index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed tombstoned equipments index name")})
    public ResponseEntity<String> getIndexedTombstonedEquipmentsIndexName() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(equipmentInfosService.getTombstonedEquipmentsIndexName());
    }

    @GetMapping(value = "/studies/indexation-count")
    @Operation(summary = "get indexed studies count")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed studies count")})
    public ResponseEntity<String> getIndexedStudiesCount() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(supervisionService.getIndexedStudiesCount()));
    }

    @GetMapping(value = "/equipments/indexation-count")
    @Operation(summary = "get indexed equipments count for all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed equipments count")})
    public ResponseEntity<String> getIndexedEquipmentsCount() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(supervisionService.getIndexedEquipmentsCount()));
    }

    @GetMapping(value = "/tombstoned-equipments/indexation-count")
    @Operation(summary = "get indexed tombstoned equipments count for all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Tombstoned equipments count")})
    public ResponseEntity<String> getIndexedTombstonedEquipmentsCount() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(supervisionService.getIndexedTombstonedEquipmentsCount()));
    }

    @GetMapping(value = "/orphan_indexed_network_uuids")
    @Operation(summary = "Get all orphan indexed equipments network uuids")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of orphan indexed equipments network uuids")})
    public ResponseEntity<List<UUID>> getOrphanIndexedEquipments() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getAllOrphanIndexedEquipmentsNetworkUuids());
    }

    @DeleteMapping(value = "/studies/{networkUuid}/indexed-equipments-by-network-uuid")
    @Operation(summary = "delete indexed equipments and tombstoned equipments for the given networkUuid")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all indexed equipments and tombstoned equipments for the given networkUuid have been deleted")})
    public ResponseEntity<Long> deleteNetworkUuidIndexedEquipmentsAndTombstoned(@PathVariable("networkUuid") UUID networkUuid) {
        equipmentInfosService.deleteEquipmentIndexes(networkUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/indices")
    @Operation(summary = "Recreate all Elasticsearch study indices")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Elasticsearch study indices recreated successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to recreate Elasticsearch indices")
    })
    public ResponseEntity<Void> recreateStudyIndices() {
        supervisionService.recreateStudyIndices();
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/reindex")
    @Operation(summary = "reindex the study")
    @ApiResponse(responseCode = "200", description = "Study reindexed")
    public ResponseEntity<Void> reindexStudy(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        studyService.getExistingBasicRootNetworkInfos(studyUuid).forEach(
                rootNetwork -> studyService.reindexRootNetwork(studyUuid, rootNetwork.rootNetworkUuid())
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/builds")
    @Operation(summary = "Invalidate node builds for the given study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all built nodes for the given study have been invalidated")})
    public ResponseEntity<Void> invalidateAllNodesBuilds(@PathVariable("studyUuid") UUID studyUuid) {
        supervisionService.unbuildAllNodes(studyUuid);
        return ResponseEntity.ok().build();
    }

}
