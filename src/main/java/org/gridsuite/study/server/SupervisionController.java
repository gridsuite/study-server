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

import java.util.UUID;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.elasticsearch.ESIndex;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
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

    private final ESIndex indexConf;

    private final ClientConfiguration elasticsearchClientConfiguration;

    public SupervisionController(SupervisionService supervisionService, ClientConfiguration elasticsearchClientConfiguration, ESIndex indexConf) {
        this.supervisionService = supervisionService;
        this.elasticsearchClientConfiguration = elasticsearchClientConfiguration;
        this.indexConf = indexConf;
    }

    @DeleteMapping(value = "/computation/results")
    @Operation(summary = "delete all results of a given computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all loadflow results have been deleted")})
    public ResponseEntity<Integer> deleteComputationResults(@Parameter(description = "Computation type") @RequestParam("type") ComputationType computationType,
                                                           @Parameter(description = "Dry run") @RequestParam("dryRun") boolean dryRun) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.deleteComputationResults(computationType, dryRun));
    }

    @GetMapping(value = "/elasticsearch-host")
    @Operation(summary = "get the elasticsearch address")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "the elasticsearch address")})
    public ResponseEntity<String> getElasticsearchHost() {
        String host = elasticsearchClientConfiguration.getEndpoints().get(0).getHostName()
                        + ":"
                        + elasticsearchClientConfiguration.getEndpoints().get(0).getPort();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(host);
    }

    @GetMapping(value = "/indexed-equipments-index-name")
    @Operation(summary = "get the indexed equipments index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed equipments index name")})
    public ResponseEntity<String> getIndexedEquipmentsIndexName() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(indexConf.getEquipmentsIndexName());
    }

    @GetMapping(value = "/indexed-tombstoned-equipments-index-name")
    @Operation(summary = "get the indexed tombstoned equipments index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed tombstoned equipments index name")})
    public ResponseEntity<String> getIndexedTombstonedEquipmentsIndexName() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(indexConf.getTombstonedEquipmentsIndexName());
    }

    @GetMapping(value = "/indexed-equipments-count")
    @Operation(summary = "get indexed equipments count for all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed equipments count")})
    public ResponseEntity<String> getIndexedEquipmentsCount() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(supervisionService.getIndexedEquipmentsCount()));
    }

    @GetMapping(value = "/indexed-tombstoned-equipments-count")
    @Operation(summary = "get indexed tombstoned equipments count for all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Tombstoned equipments count")})
    public ResponseEntity<String> getIndexedTombstonedEquipmentsCount() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(supervisionService.getIndexedTombstonedEquipmentsCount()));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/indexed-equipments")
    @Operation(summary = "delete indexed equipments and tombstoned equipments for the given study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all indexed equipments and tombstoned equipments for the given study have been deleted")})
    public ResponseEntity<String> deleteStudyIndexedEquipmentsAndTombstoned(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(supervisionService.deleteStudyIndexedEquipmentsAndTombstoned(studyUuid)));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/builds")
    @Operation(summary = "Invalidate node builds for the given study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all built nodes for the given study have been invalidated")})
    public ResponseEntity<Void> invalidateAllNodesBuilds(@PathVariable("studyUuid") UUID studyUuid) {
        supervisionService.invalidateAllNodesBuilds(studyUuid);
        return ResponseEntity.ok().build();
    }

}
