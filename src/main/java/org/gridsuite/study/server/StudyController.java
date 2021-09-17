/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.loadflow.LoadFlowParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION)
@Tag(name = "Study server")
public class StudyController {

    private final StudyService studyService;
    private final ReportService reportService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public StudyController(StudyService studyService, ReportService reportService, NetworkModificationTreeService networkModificationTreeService) {
        this.studyService = studyService;
        this.reportService = reportService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @GetMapping(value = "/studies")
    @Operation(summary = "Get all studies for a user")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of studies")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> getStudyList(@RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyList(userId));
    }

    @GetMapping(value = "/study_creation_requests")
    @Operation(summary = "Get all study creation requests for a user")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of study creation requests")})
    public ResponseEntity<Flux<BasicStudyInfos>> getStudyCreationRequestList(@RequestHeader("userId") String userId) {
        Flux<BasicStudyInfos> studies = studyService.getStudyCreationRequests(userId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studies);
    }

    @GetMapping(value = "/studies/metadata")
    @Operation(summary = "Get studies metadata")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of studies metadata")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> getStudyListMetadata(@RequestHeader("userId") String userId, @RequestHeader("uuids") List<UUID> uuids) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyListMetadata(uuids, userId));
    }

    @PostMapping(value = "/studies/{studyName}/cases/{caseUuid}")
    @Operation(summary = "create a study from an existing case")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The id of the network imported"),
            @ApiResponse(responseCode = "409", description = "The study already exist or the case doesn't exists")})
    public ResponseEntity<Mono<BasicStudyInfos>> createStudyFromExistingCase(@PathVariable("studyName") String studyName,
                                                                             @PathVariable("caseUuid") UUID caseUuid,
                                                                             @RequestParam("description") String description,
                                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                                             @RequestParam("isPrivate") Boolean isPrivate,
                                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(studyName, caseUuid, description, userId, isPrivate, studyUuid)
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(studyService.assertCaseExists(caseUuid).then(createStudy));
    }

    @PostMapping(value = "/studies/{studyName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "create a study and import the case")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The id of the network imported"),
            @ApiResponse(responseCode = "409", description = "The study already exist"),
            @ApiResponse(responseCode = "500", description = "The storage is down or a file with the same name already exists")})
    public ResponseEntity<Mono<BasicStudyInfos>> createStudy(@PathVariable("studyName") String studyName,
                                                             @RequestPart("caseFile") FilePart caseFile,
                                                             @RequestParam("description") String description,
                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                             @RequestParam("isPrivate") Boolean isPrivate,
                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(studyName, Mono.just(caseFile), description, userId, isPrivate, studyUuid)
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(createStudy);
    }

    @GetMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "get a study")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The study information"),
            @ApiResponse(responseCode = "404", description = "The study doesn't exist")})
    public ResponseEntity<Mono<StudyInfos>> getStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                     @RequestHeader("userId") String headerUserId) {
        Mono<StudyInfos> studyMono = studyService.getCurrentUserStudy(studyUuid, headerUserId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyMono.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/exists")
    @Operation(summary = "Check if the study exists")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "If the study exists or not.")})
    public ResponseEntity<Mono<Boolean>> studyExists(@PathVariable("studyName") String studyName,
                                                     @PathVariable("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.studyExists(studyName, userId));
    }

    @DeleteMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "delete the study")
    @ApiResponse(responseCode = "200", description = "Study deleted")
    public ResponseEntity<Mono<Void>> deleteStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                  @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<Mono<byte[]>> getVoltageLevelDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid -> studyService.getVoltageLevelSvg(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(responseCode = "200", description = "The svg and metadata")
    public ResponseEntity<Mono<String>> getVoltageLevelDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid -> studyService.getVoltageLevelSvgAndMetadata(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/voltage-levels")
    @Operation(summary = "get the voltage levels for a given network")
    @ApiResponse(responseCode = "200", description = "The voltage level list of the network")
    public ResponseEntity<Mono<List<VoltageLevelInfos>>> getNetworkVoltageLevels(
            @PathVariable("studyUuid") UUID studyUuid) {

        Mono<UUID> networkUuid = studyService.getNetworkUuid(studyUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkUuid.flatMap(studyService::getNetworkVoltageLevels));
    }

    @GetMapping(value = "/studies/{studyUuid}/geo-data/lines")
    @Operation(summary = "Get Network lines graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lines graphics")})
    public ResponseEntity<Mono<String>> getLinesGraphics(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(studyService::getLinesGraphics));
    }

    @GetMapping(value = "/studies/{studyUuid}/geo-data/substations")
    @Operation(summary = "Get Network substations graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substations graphics")})
    public ResponseEntity<Mono<String>> getSubstationsGraphic(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(studyService::getSubstationsGraphics));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/lines")
    @Operation(summary = "Get Network lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lines data")})
    public ResponseEntity<Mono<String>> getLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getLinesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/substations")
    @Operation(summary = "Get Network substations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substations data")})
    public ResponseEntity<Mono<String>> getSubstationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getSubstationsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/2-windings-transformers")
    @Operation(summary = "Get Network 2 windings transformers description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of 2 windings transformers data")})
    public ResponseEntity<Mono<String>> getTwoWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getTwoWindingsTransformersMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/3-windings-transformers")
    @Operation(summary = "Get Network 3 windings transformers description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of 3 windings transformers data")})
    public ResponseEntity<Mono<String>> getThreeWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getThreeWindingsTransformersMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/generators")
    @Operation(summary = "Get Network generators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of generators data")})
    public ResponseEntity<Mono<String>> getGeneratorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getGeneratorsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/batteries")
    @Operation(summary = "Get Network batteries description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of batteries data")})
    public ResponseEntity<Mono<String>> getBatteriesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getBatteriesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/dangling-lines")
    @Operation(summary = "Get Network dangling lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of dangling lines data")})
    public ResponseEntity<Mono<String>> getDanglingLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getDanglingLinesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/hvdc-lines")
    @Operation(summary = "Get Network hvdc lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of hvdc lines data")})
    public ResponseEntity<Mono<String>> getHvdcLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getHvdcLinesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/lcc-converter-stations")
    @Operation(summary = "Get Network lcc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lcc converter stations data")})
    public ResponseEntity<Mono<String>> getLccConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getLccConverterStationsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/vsc-converter-stations")
    @Operation(summary = "Get Network vsc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of vsc converter stations data")})
    public ResponseEntity<Mono<String>> getVscConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getVscConverterStationsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/loads")
    @Operation(summary = "Get Network loads description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of loads data")})
    public ResponseEntity<Mono<String>> getLoadsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getLoadsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/shunt-compensators")
    @Operation(summary = "Get Network shunt compensators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of shunt compensators data")})
    public ResponseEntity<Mono<String>> getShuntCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getShuntCompensatorsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/static-var-compensators")
    @Operation(summary = "Get Network static var compensators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of static var compensators data")})
    public ResponseEntity<Mono<String>> getStaticVarCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getStaticVarCompensatorsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/all")
    @Operation(summary = "Get Network equipments description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipments data")})
    public ResponseEntity<Mono<String>> getAllMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getAllMapData(uuid, substationsIds)));
    }

    @PutMapping(value = "/studies/{studyUuid}/network-modification/switches/{switchId}")
    @Operation(summary = "update a switch position")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The switch is updated")})
    public ResponseEntity<Mono<Void>> changeSwitchState(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("switchId") String switchId,
                                                        @RequestParam("open") boolean open) {

        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(studyUuid)
                .then(studyService.changeSwitchState(studyUuid, switchId, open)));
    }

    @PutMapping(value = "/studies/{studyUuid}/network-modification/groovy")
    @Operation(summary = "change an equipment state in the network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment is updated")})
    public ResponseEntity<Mono<Void>> applyGroovyScript(@PathVariable("studyUuid") UUID studyUuid,
                                                        @RequestBody String groovyScript) {

        return ResponseEntity.ok().body(studyService.applyGroovyScript(studyUuid, groovyScript).then());
    }

    @GetMapping(value = "/studies/{studyUuid}/network/modifications")
    @Operation(summary = "Get all network modifications")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of network modifications")})
    public ResponseEntity<Flux<ModificationInfos>> getModifications(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getModifications(studyUuid));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/network/modifications")
    @Operation(summary = "Delete all network modifications")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Network modifications deleted")})
    public ResponseEntity<Mono<Void>> deleteModifications(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.deleteModifications(studyUuid));
    }

    @PutMapping(value = "/studies/{studyUuid}/network-modification/lines/{lineId}/status", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Change the given line status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Line status changed")})
    public ResponseEntity<Mono<Void>> changeLineStatus(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("lineId") String lineId,
            @RequestBody String status) {
        return ResponseEntity.ok().body(studyService.changeLineStatus(studyUuid, lineId, status));
    }

    @PutMapping(value = "/studies/{studyUuid}/loadflow/run")
    @Operation(summary = "run loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has started")})
    public ResponseEntity<Mono<Void>> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().body(studyService.assertLoadFlowRunnable(studyUuid)
                .then(studyService.runLoadFlow(studyUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/rename")
    @Operation(summary = "Update the study name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated study")})
    public ResponseEntity<Mono<CreatedStudyBasicInfos>> renameStudy(@RequestHeader("userId") String headerUserId,
                                                                    @PathVariable("studyUuid") UUID studyUuid,
                                                                    @RequestBody RenameStudyAttributes renameStudyAttributes) {

        Mono<CreatedStudyBasicInfos> studyMono = studyService.renameStudy(studyUuid, headerUserId, renameStudyAttributes.getNewStudyName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyMono);
    }

    @PostMapping(value = "/studies/{studyUuid}/public")
    @Operation(summary = "set study to public")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The switch is public")})
    public ResponseEntity<Mono<StudyInfos>> makeStudyPublic(@PathVariable("studyUuid") UUID studyUuid,
                                                            @RequestHeader("userId") String headerUserId) {

        return ResponseEntity.ok().body(studyService.changeStudyAccessRights(studyUuid, headerUserId, false));
    }

    @PostMapping(value = "/studies/{studyUuid}/private")
    @Operation(summary = "set study to private")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study is private")})
    public ResponseEntity<Mono<StudyInfos>> makeStudyPrivate(@PathVariable("studyUuid") UUID studyUuid,
                                                             @RequestHeader("userId") String headerUserId) {

        return ResponseEntity.ok().body(studyService.changeStudyAccessRights(studyUuid, headerUserId, true));
    }

    @GetMapping(value = "/export-network-formats")
    @Operation(summary = "get the available export format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The available export format")})
    public ResponseEntity<Mono<Collection<String>>> getExportFormats() {
        Mono<Collection<String>> formatsMono = studyService.getExportFormats();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(formatsMono);
    }

    @GetMapping(value = "/studies/{studyUuid}/export-network/{format}")
    @Operation(summary = "export the study's network in the given format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network in the given format")})
    public Mono<ResponseEntity<byte[]>> exportNetwork(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("format") String format) {

        Mono<ExportNetworkInfos> exportNetworkInfosMono = studyService.exportNetwork(studyUuid, format);
        return exportNetworkInfosMono.map(exportNetworkInfos -> {
            HttpHeaders header = new HttpHeaders();
            header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getFileName(), StandardCharsets.UTF_8).build());
            return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
        });
    }

    @PostMapping(value = "/studies/{studyUuid}/security-analysis/run")
    @Operation(summary = "run security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has started")})
    public ResponseEntity<Mono<UUID>> runSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contigencyListNames,
                                                          @RequestBody(required = false) String parameters) {
        List<String> nonNullcontingencyListNames = contigencyListNames != null ? contigencyListNames : Collections.emptyList();
        String nonNullParameters = Objects.toString(parameters, "");
        return ResponseEntity.ok().body(studyService.runSecurityAnalysis(studyUuid, nonNullcontingencyListNames, nonNullParameters));
    }

    @GetMapping(value = "/studies/{studyUuid}/security-analysis/result")
    @Operation(summary = "Get a security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result"),
            @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "Limit types") @RequestParam(name = "limitType", required = false) List<String> limitTypes) {
        List<String> nonNullLimitTypes = limitTypes != null ? limitTypes : Collections.emptyList();
        return studyService.getSecurityAnalysisResult(studyUuid, nonNullLimitTypes)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/studies/{studyUuid}/contingency-count")
    @Operation(summary = "Get contingency count for a list of contingency list on a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The contingency count")})
    public Mono<ResponseEntity<Integer>> getContingencyCount(@Parameter(description = "Study name") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contigencyListNames) {
        List<String> nonNullcontigencyListNames = contigencyListNames != null ? contigencyListNames : Collections.emptyList();
        return studyService.getContingencyCount(studyUuid, nonNullcontigencyListNames)
                .map(count -> ResponseEntity.ok().body(count));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "set loadflow parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters are set")})
    public ResponseEntity<Mono<Void>> setLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) LoadFlowParameters lfParameter) {
        return ResponseEntity.ok().body(studyService.setLoadFlowParameters(studyUuid, lfParameter));
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "Get loadflow parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters")})
    public ResponseEntity<Mono<LoadFlowParameters>> getLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowParameters(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "set load flow provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow provider is set")})
    public ResponseEntity<Mono<Void>> setLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                          @RequestBody(required = false) String provider) {
        return ResponseEntity.ok().body(studyService.updateLoadFlowProvider(studyUuid, provider));
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "Get load flow provider for a specified study, empty string means default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow provider is returned")})
    public ResponseEntity<Mono<String>> getLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowProvider(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/substations/{substationId}/svg")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<Mono<byte[]>> getSubstationDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("substationId") String substationId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid ->
                studyService.getSubstationSvg(uuid, substationId, useName, centerLabel, diagonalLabel, topologicalColoring, substationLayout, componentLibrary)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/substations/{substationId}/svg-and-metadata")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponse(responseCode = "200", description = "The svg and metadata")
    public ResponseEntity<Mono<String>> getSubstationDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("substationId") String substationId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid ->
                studyService.getSubstationSvgAndMetadata(uuid, substationId, useName, centerLabel, diagonalLabel, topologicalColoring, substationLayout, componentLibrary)));
    }

    @GetMapping(value = "/studies/{studyUuid}/security-analysis/status")
    @Operation(summary = "Get the security analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis status"),
            @ApiResponse(responseCode = "404", description = "The security analysis status has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid) {
        return studyService.getSecurityAnalysisStatus(studyUuid)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/studies/{studyUuid}/security-analysis/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Mono<Void>> stopSecurityAnalysis(@Parameter(description = "Study name") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.stopSecurityAnalysis(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get study report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report for study"), @ApiResponse(responseCode = "404", description = "The study not found")})
    public ResponseEntity<Mono<ReporterModel>> getReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(reportService::getReport));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/report")
    @Operation(summary = "Delete study report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report for study deleted"), @ApiResponse(responseCode = "404", description = "The study not found")})
    public ResponseEntity<Mono<Void>> deleteReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(reportService::deleteReport));
    }

    @GetMapping(value = "/svg-component-libraries")
    @Operation(summary = "Get a list of the available svg component libraries")
    @ApiResponse(responseCode = "200", description = "The list of the available svg component libraries")
    public ResponseEntity<Mono<List<String>>> getAvailableSvgComponentLibraries() {
        Mono<List<String>> libraries = studyService.getAvailableSvgComponentLibraries();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(libraries);
    }

    @PostMapping(value = "/tree/nodes/{id}")
    @Operation(summary = "Create a node as before / after the given node ID")
    @ApiResponse(responseCode = "200", description = "The node has been added")
    public ResponseEntity<Mono<AbstractNode>> createNode(@RequestBody AbstractNode node,
                                                         @Parameter(description = "parent id of the node created") @PathVariable(name = "id") UUID referenceId,
                                                         @Parameter(description = "node is inserted before the given node ID") @RequestParam(name = "mode", required = false, defaultValue = "CHILD") InsertMode insertMode
                                                         ) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationTreeService.createNode(referenceId, node, insertMode));
    }

    @DeleteMapping(value = "/tree/nodes/{id}")
    @Operation(summary = "Delete node with given id")
    @ApiResponse(responseCode = "200", description = "the nodes have been successfully deleted")
    public ResponseEntity<Mono<Void>> deleteNode(@Parameter(description = "id of child to remove") @PathVariable UUID id,
                                                 @Parameter(description = "deleteChildren")  @RequestParam(value = "deleteChildren", defaultValue = "false") boolean deleteChildren) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationTreeService.deleteNode(id, deleteChildren));
    }

    @GetMapping(value = "/tree/{id}")
    @Operation(summary = "get network modification tree for the given study")
    @ApiResponse(responseCode = "200", description = "network modification tree")
    public Mono<ResponseEntity<RootNode>> getNetworkModificationTree(@Parameter(description = "study uuid") @PathVariable("id") UUID id) {
        return networkModificationTreeService.getStudyTree(id)
            .map(result -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/tree/nodes")
    @Operation(summary = "update node")
    @ApiResponse(responseCode = "200", description = "the node has been updated")
    public ResponseEntity<Mono<Void>> updateNode(@RequestBody AbstractNode node) {
        return ResponseEntity.ok().body(networkModificationTreeService.updateNode(node));
    }

    @GetMapping(value = "/tree/nodes/{id}")
    @Operation(summary = "get simplified node")
    @ApiResponse(responseCode = "200", description = "simplified nodes (without children")
    public Mono<ResponseEntity<AbstractNode>> getNode(@Parameter(description = "node uuid") @PathVariable("id") UUID id) {
        return networkModificationTreeService.getSimpleNode(id)
            .map(result -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
