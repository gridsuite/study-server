/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.loadflow.LoadFlowParameters;
import io.swagger.annotations.*;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
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
@Api(value = "Study server")
public class StudyController {

    private final StudyService studyService;
    private final ReportService reportService;

    public static final String TMP_LEGACY_DIRECTORY = "11111111-2222-3333-4444-555555555555";

    public StudyController(StudyService studyService, ReportService reportService) {
        this.studyService = studyService;
        this.reportService = reportService;
    }

    @GetMapping(value = "/studies")
    @ApiOperation(value = "Get all studies for a user")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of studies")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> getStudyList(@RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyList(userId));
    }

    @GetMapping(value = "/study_creation_requests")
    @ApiOperation(value = "Get all study creation requests for a user")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of study creation requests")})
    public ResponseEntity<Flux<BasicStudyInfos>> getStudyCreationRequestList(@RequestHeader("userId") String userId) {
        Flux<BasicStudyInfos> studies = studyService.getStudyCreationRequests(userId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studies);
    }

    @GetMapping(value = "/studies/metadata")
    @ApiOperation(value = "Get studies metadata")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of studies metadata")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> getStudyListMetadata(@RequestHeader("userId") String userId, @RequestHeader("uuids") List<UUID> uuids) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyListMetadata(uuids, userId));
    }

    @PostMapping(value = "/studies/{studyName}/cases/{caseUuid}")
    @ApiOperation(value = "create a study from an existing case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist or the case doesn't exists")})
    public ResponseEntity<Mono<BasicStudyInfos>> createStudyFromExistingCase(@PathVariable("studyName") String studyName,
                                                                             @PathVariable("caseUuid") UUID caseUuid,
                                                                             @RequestParam("description") String description,
                                                                             @RequestParam(required = false, value = "parentDirectoryUuid", defaultValue = TMP_LEGACY_DIRECTORY) UUID parentDirectoryUuid,
                                                                             @RequestParam("isPrivate") Boolean isPrivate,
                                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(studyName, caseUuid, description, userId, isPrivate, parentDirectoryUuid)
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(studyService.assertCaseExists(caseUuid).then(createStudy));
    }

    @PostMapping(value = "/studies/{studyName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(value = "create a study and import the case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist"),
            @ApiResponse(code = 500, message = "The storage is down or a file with the same name already exists")})
    public ResponseEntity<Mono<BasicStudyInfos>> createStudy(@PathVariable("studyName") String studyName,
                                                             @RequestPart("caseFile") FilePart caseFile,
                                                             @RequestParam("description") String description,
                                                             @RequestParam(required = false, value = "parentDirectoryUuid", defaultValue = TMP_LEGACY_DIRECTORY) UUID parentDirectoryUuid,
                                                             @RequestParam("isPrivate") Boolean isPrivate,
                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(studyName, Mono.just(caseFile), description, userId, isPrivate, parentDirectoryUuid)
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(createStudy);
    }

    @GetMapping(value = "/studies/{studyUuid}")
    @ApiOperation(value = "get a study")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The study information"),
            @ApiResponse(code = 404, message = "The study doesn't exist")})
    public ResponseEntity<Mono<StudyInfos>> getStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                     @RequestHeader("userId") String headerUserId) {
        Mono<StudyInfos> studyMono = studyService.getCurrentUserStudy(studyUuid, headerUserId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyMono.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/exists")
    @ApiOperation(value = "Check if the study exists", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "If the study exists or not.")})
    public ResponseEntity<Mono<Boolean>> studyExists(@PathVariable("studyName") String studyName,
                                                     @PathVariable("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.studyExists(studyName, userId));
    }

    @DeleteMapping(value = "/studies/{studyUuid}")
    @ApiOperation(value = "delete the study")
    @ApiResponse(code = 200, message = "Study deleted")
    public ResponseEntity<Mono<Void>> deleteStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                  @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(code = 200, message = "The svg")
    public ResponseEntity<Mono<byte[]>> getVoltageLevelDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid -> studyService.getVoltageLevelSvg(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level", produces = "application/json")
    @ApiResponse(code = 200, message = "The svg and metadata")
    public ResponseEntity<Mono<String>> getVoltageLevelDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid -> studyService.getVoltageLevelSvgAndMetadata(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/voltage-levels")
    @ApiOperation(value = "get the voltage levels for a given network")
    @ApiResponse(code = 200, message = "The voltage level list of the network")
    public ResponseEntity<Mono<List<VoltageLevelInfos>>> getNetworkVoltageLevels(
            @PathVariable("studyUuid") UUID studyUuid) {

        Mono<UUID> networkUuid = studyService.getNetworkUuid(studyUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkUuid.flatMap(studyService::getNetworkVoltageLevels));
    }

    @GetMapping(value = "/studies/{studyUuid}/geo-data/lines")
    @ApiOperation(value = "Get Network lines graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines graphics")})
    public ResponseEntity<Mono<String>> getLinesGraphics(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(studyService::getLinesGraphics));
    }

    @GetMapping(value = "/studies/{studyUuid}/geo-data/substations")
    @ApiOperation(value = "Get Network substations graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations graphics")})
    public ResponseEntity<Mono<String>> getSubstationsGraphic(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(studyService::getSubstationsGraphics));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/lines")
    @ApiOperation(value = "Get Network lines description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines data")})
    public ResponseEntity<Mono<String>> getLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getLinesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/substations")
    @ApiOperation(value = "Get Network substations description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations data")})
    public ResponseEntity<Mono<String>> getSubstationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getSubstationsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/2-windings-transformers")
    @ApiOperation(value = "Get Network 2 windings transformers description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of 2 windings transformers data")})
    public ResponseEntity<Mono<String>> getTwoWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getTwoWindingsTransformersMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/3-windings-transformers")
    @ApiOperation(value = "Get Network 3 windings transformers description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of 3 windings transformers data")})
    public ResponseEntity<Mono<String>> getThreeWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getThreeWindingsTransformersMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/generators")
    @ApiOperation(value = "Get Network generators description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of generators data")})
    public ResponseEntity<Mono<String>> getGeneratorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getGeneratorsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/batteries")
    @ApiOperation(value = "Get Network batteries description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of batteries data")})
    public ResponseEntity<Mono<String>> getBatteriesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getBatteriesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/dangling-lines")
    @ApiOperation(value = "Get Network dangling lines description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of dangling lines data")})
    public ResponseEntity<Mono<String>> getDanglingLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getDanglingLinesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/hvdc-lines")
    @ApiOperation(value = "Get Network hvdc lines description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of hvdc lines data")})
    public ResponseEntity<Mono<String>> getHvdcLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getHvdcLinesMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/lcc-converter-stations")
    @ApiOperation(value = "Get Network lcc converter stations description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lcc converter stations data")})
    public ResponseEntity<Mono<String>> getLccConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getLccConverterStationsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/vsc-converter-stations")
    @ApiOperation(value = "Get Network vsc converter stations description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of vsc converter stations data")})
    public ResponseEntity<Mono<String>> getVscConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getVscConverterStationsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/loads")
    @ApiOperation(value = "Get Network loads description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of loads data")})
    public ResponseEntity<Mono<String>> getLoadsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getLoadsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/shunt-compensators")
    @ApiOperation(value = "Get Network shunt compensators description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of shunt compensators data")})
    public ResponseEntity<Mono<String>> getShuntCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getShuntCompensatorsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/static-var-compensators")
    @ApiOperation(value = "Get Network static var compensators description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of static var compensators data")})
    public ResponseEntity<Mono<String>> getStaticVarCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getStaticVarCompensatorsMapData(uuid, substationsIds)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network-map/all")
    @ApiOperation(value = "Get Network equipments description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of equipments data")})
    public ResponseEntity<Mono<String>> getAllMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @ApiParam(value = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid)
                .flatMap(uuid -> studyService.getAllMapData(uuid, substationsIds)));
    }

    @PutMapping(value = "/studies/{studyUuid}/network-modification/switches/{switchId}")
    @ApiOperation(value = "update a switch position", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The switch is updated")})
    public ResponseEntity<Mono<Void>> changeSwitchState(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("switchId") String switchId,
                                                        @RequestParam("open") boolean open) {

        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(studyUuid)
                .then(studyService.changeSwitchState(studyUuid, switchId, open)));
    }

    @PutMapping(value = "/studies/{studyUuid}/network-modification/groovy")
    @ApiOperation(value = "change an equipment state in the network", produces = "application/text")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The equipment is updated")})
    public ResponseEntity<Mono<Void>> applyGroovyScript(@PathVariable("studyUuid") UUID studyUuid,
                                                        @RequestBody String groovyScript) {

        return ResponseEntity.ok().body(studyService.applyGroovyScript(studyUuid, groovyScript).then());
    }

    @GetMapping(value = "/studies/{studyUuid}/network/modifications")
    @ApiOperation(value = "Get all network modifications", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of network modifications")})
    public ResponseEntity<Flux<ModificationInfos>> getModifications(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getModifications(studyUuid));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/network/modifications")
    @ApiOperation(value = "Delete all network modifications")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Network modifications deleted")})
    public ResponseEntity<Mono<Void>> deleteModifications(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.deleteModifications(studyUuid));
    }

    @PutMapping(value = "/studies/{studyUuid}/network-modification/lines/{lineId}/status", consumes = MediaType.TEXT_PLAIN_VALUE)
    @ApiOperation(value = "Change the given line status", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Line status changed")})
    public ResponseEntity<Mono<Void>> changeLineStatus(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("lineId") String lineId,
            @RequestBody(required = true) String status) {
        return ResponseEntity.ok().body(studyService.changeLineStatus(studyUuid, lineId, status));
    }

    @PutMapping(value = "/studies/{studyUuid}/loadflow/run")
    @ApiOperation(value = "run loadflow on study", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The loadflow has started")})
    public ResponseEntity<Mono<Void>> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().body(studyService.assertLoadFlowRunnable(studyUuid)
                .then(studyService.runLoadFlow(studyUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/rename")
    @ApiOperation(value = "Update the study name", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The updated study")})
    public ResponseEntity<Mono<CreatedStudyBasicInfos>> renameStudy(@RequestHeader("userId") String headerUserId,
                                                                    @PathVariable("studyUuid") UUID studyUuid,
                                                                    @RequestBody RenameStudyAttributes renameStudyAttributes) {

        Mono<CreatedStudyBasicInfos> studyMono = studyService.renameStudy(studyUuid, headerUserId, renameStudyAttributes.getNewStudyName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyMono);
    }

    @PostMapping(value = "/studies/{studyUuid}/public")
    @ApiOperation(value = "set study to public", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The switch is public")})
    public ResponseEntity<Mono<StudyInfos>> makeStudyPublic(@PathVariable("studyUuid") UUID studyUuid,
                                                            @RequestHeader("userId") String headerUserId) {

        return ResponseEntity.ok().body(studyService.changeStudyAccessRights(studyUuid, headerUserId, false));
    }

    @PostMapping(value = "/studies/{studyUuid}/private")
    @ApiOperation(value = "set study to private", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The study is private")})
    public ResponseEntity<Mono<StudyInfos>> makeStudyPrivate(@PathVariable("studyUuid") UUID studyUuid,
                                                             @RequestHeader("userId") String headerUserId) {

        return ResponseEntity.ok().body(studyService.changeStudyAccessRights(studyUuid, headerUserId, true));
    }

    @GetMapping(value = "/export-network-formats")
    @ApiOperation(value = "get the available export format", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The available export format")})
    public ResponseEntity<Mono<Collection<String>>> getExportFormats() {
        Mono<Collection<String>> formatsMono = studyService.getExportFormats();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(formatsMono);
    }

    @GetMapping(value = "/studies/{studyUuid}/export-network/{format}")
    @ApiOperation(value = "export the study's network in the given format", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The network in the given format")})
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
    @ApiOperation(value = "run security analysis on study", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The security analysis has started")})
    public ResponseEntity<Mono<UUID>> runSecurityAnalysis(@ApiParam(value = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                          @ApiParam(value = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contigencyListNames,
                                                          @RequestBody(required = false) String parameters) {
        List<String> nonNullcontingencyListNames = contigencyListNames != null ? contigencyListNames : Collections.emptyList();
        String nonNullParameters = Objects.toString(parameters, "");
        return ResponseEntity.ok().body(studyService.runSecurityAnalysis(studyUuid, nonNullcontingencyListNames, nonNullParameters));
    }

    @GetMapping(value = "/studies/{studyUuid}/security-analysis/result")
    @ApiOperation(value = "Get a security analysis result on study", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The security analysis result"),
            @ApiResponse(code = 404, message = "The security analysis has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisResult(@ApiParam(value = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @ApiParam(value = "Limit types") @RequestParam(name = "limitType", required = false) List<String> limitTypes) {
        List<String> nonNullLimitTypes = limitTypes != null ? limitTypes : Collections.emptyList();
        return studyService.getSecurityAnalysisResult(studyUuid, nonNullLimitTypes)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/studies/{studyUuid}/contingency-count")
    @ApiOperation(value = "Get contingency count for a list of contingency list on a study", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The contingency count")})
    public Mono<ResponseEntity<Integer>> getContingencyCount(@ApiParam(value = "Study name") @PathVariable("studyUuid") UUID studyUuid,
                                                             @ApiParam(value = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contigencyListNames) {
        List<String> nonNullcontigencyListNames = contigencyListNames != null ? contigencyListNames : Collections.emptyList();
        return studyService.getContingencyCount(studyUuid, nonNullcontigencyListNames)
                .map(count -> ResponseEntity.ok().body(count));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @ApiOperation(value = "set loadflow parameters on study, reset to default ones if empty body", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The loadflow parameters are set")})
    public ResponseEntity<Mono<Void>> setLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) LoadFlowParameters lfParameter) {
        return ResponseEntity.ok().body(studyService.setLoadFlowParameters(studyUuid, lfParameter));
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @ApiOperation(value = "Get loadflow parameters on study", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The loadflow parameters")})
    public ResponseEntity<Mono<LoadFlowParameters>> getLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowParameters(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @ApiOperation(value = "set load flow provider for the specified study, no body means reset to default provider", consumes = MediaType.TEXT_PLAIN_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The load flow provider is set")})
    public ResponseEntity<Mono<Void>> setLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                          @RequestBody(required = false) String provider) {
        return ResponseEntity.ok().body(studyService.updateLoadFlowProvider(studyUuid, provider));
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @ApiOperation(value = "Get load flow provider for a specified study, empty string means default provider", produces = MediaType.TEXT_PLAIN_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The load flow provider is returned")})
    public ResponseEntity<Mono<String>> getLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowProvider(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/substations/{substationId}/svg")
    @ApiOperation(value = "get the substation diagram for the given network and substation")
    @ApiResponse(code = 200, message = "The svg")
    public ResponseEntity<Mono<byte[]>> getSubstationDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("substationId") String substationId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @ApiParam(value = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid ->
                studyService.getSubstationSvg(uuid, substationId, useName, centerLabel, diagonalLabel, topologicalColoring, substationLayout)));
    }

    @GetMapping(value = "/studies/{studyUuid}/network/substations/{substationId}/svg-and-metadata")
    @ApiOperation(value = "get the substation diagram for the given network and substation", produces = "application/json")
    @ApiResponse(code = 200, message = "The svg and metadata")
    public ResponseEntity<Mono<String>> getSubstationDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("substationId") String substationId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @ApiParam(value = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(uuid ->
                studyService.getSubstationSvgAndMetadata(uuid, substationId, useName, centerLabel, diagonalLabel, topologicalColoring, substationLayout)));
    }

    @GetMapping(value = "/studies/{studyUuid}/security-analysis/status")
    @ApiOperation(value = "Get the security analysis status on study", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The security analysis status"),
            @ApiResponse(code = 404, message = "The security analysis status has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisStatus(@ApiParam(value = "Study UUID") @PathVariable("studyUuid") UUID studyUuid) {
        return studyService.getSecurityAnalysisStatus(studyUuid)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/studies/{studyUuid}/security-analysis/stop")
    @ApiOperation(value = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The security analysis has been stopped")})
    public ResponseEntity<Mono<Void>> stopSecurityAnalysis(@ApiParam(value = "Study name") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.stopSecurityAnalysis(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get study report")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The report for study"), @ApiResponse(code = 404, message = "The study not found")})
    public ResponseEntity<Mono<ReporterModel>> getReport(@ApiParam(value = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(reportService::getReport));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/report")
    @ApiOperation(value = "Delete merge report")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The report for study deleted"), @ApiResponse(code = 404, message = "The study not found")})
    public ResponseEntity<Mono<Void>> deleteReport(@ApiParam(value = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyUuid).flatMap(reportService::deleteReport));
    }

}
