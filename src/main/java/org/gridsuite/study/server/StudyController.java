/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.loadflow.LoadFlowParameters;
import io.swagger.annotations.*;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.StudyEntity;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping(value = "/studies")
    @ApiOperation(value = "Get all studies for a user")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of studies")})
    public ResponseEntity<Flux<StudyInfos>> getStudyList(@RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyList(userId));
    }

    @GetMapping(value = "/study_creation_requests")
    @ApiOperation(value = "Get all study creation requests for a user")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of study creation requests")})
    public ResponseEntity<Flux<BasicStudyInfos>> getStudyCreationRequestList(@RequestHeader("userId") String userId) {
        Flux<BasicStudyInfos> studies = studyService.getStudyCreationRequests(userId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studies);
    }

    @PostMapping(value = "/studies/{studyName}/cases/{caseUuid}")
    @ApiOperation(value = "create a study from an existing case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist or the case doesn't exists")})
    public ResponseEntity<Mono<Void>> createStudyFromExistingCase(@PathVariable("studyName") String studyName,
                                                                  @PathVariable("caseUuid") UUID caseUuid,
                                                                  @RequestParam("description") String description,
                                                                  @RequestParam("isPrivate") Boolean isPrivate,
                                                                  @RequestHeader("userId") String userId) {
        Mono<StudyEntity> createStudy = studyService.createStudy(studyName, caseUuid, description, userId, isPrivate, new LoadFlowResult())
                .subscribeOn(Schedulers.boundedElastic())
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(Mono.when(studyService.assertStudyNotExists(studyName, userId), studyService.assertCaseExists(caseUuid))
                .doOnSuccess(s -> createStudy.subscribe()));
    }

    @PostMapping(value = "/studies/{studyName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(value = "create a study and import the case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist"),
            @ApiResponse(code = 500, message = "The storage is down or a file with the same name already exists")})
    public ResponseEntity<Mono<Void>> createStudy(@PathVariable("studyName") String studyName,
                                                  @RequestPart("caseFile") FilePart caseFile,
                                                  @RequestParam("description") String description,
                                                  @RequestParam("isPrivate") Boolean isPrivate,
                                                  @RequestHeader("userId") String userId) {
        Mono<StudyEntity> createStudy = studyService.createStudy(studyName, Mono.just(caseFile), description, userId, isPrivate)
                .subscribeOn(Schedulers.boundedElastic())
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(studyService.assertStudyNotExists(studyName, userId).doOnSuccess(s -> createStudy.subscribe()));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}")
    @ApiOperation(value = "get a study")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The study information"),
            @ApiResponse(code = 404, message = "The study doesn't exist")})
    public ResponseEntity<Mono<StudyInfos>> getStudy(@PathVariable("studyName") String studyName,
                                                      @RequestHeader("userId") String headerUserId,
                                                      @PathVariable("userId") String userId) {
        Mono<StudyInfos> studyMono = studyService.getCurrentUserStudy(studyName, userId, headerUserId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyMono.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))).then(studyMono));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/exists")
    @ApiOperation(value = "Check if the study exists", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "If the study exists or not.")})
    public ResponseEntity<Mono<Boolean>> studyExists(@PathVariable("studyName") String studyName,
                                                     @PathVariable("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.studyExists(studyName, userId));
    }

    @DeleteMapping(value = "/{userId}/studies/{studyName}")
    @ApiOperation(value = "delete the study")
    @ApiResponse(code = 200, message = "Study deleted")
    public ResponseEntity<Mono<Void>> deleteStudy(@PathVariable("studyName") String studyName,
                                                  @PathVariable("userId") String userId,
                                                  @RequestHeader("userId") String headerUserId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.deleteStudy(studyName, userId, headerUserId).then());
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(code = 200, message = "The svg")
    public ResponseEntity<Mono<byte[]>> getVoltageLevelDiagram(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(studyService.getNetworkUuid(studyName, userId).flatMap(uuid -> studyService.getVoltageLevelSvg(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring)));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level", produces = "application/json")
    @ApiResponse(code = 200, message = "The svg and metadata")
    public ResponseEntity<Mono<String>> getVoltageLevelDiagramAndMetadata(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(uuid -> studyService.getVoltageLevelSvgAndMetadata(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring)));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network/voltage-levels")
    @ApiOperation(value = "get the voltage levels for a given network")
    @ApiResponse(code = 200, message = "The voltage level list of the network")
    public ResponseEntity<Mono<List<VoltageLevelAttributes>>> getNetworkVoltageLevels(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        Mono<UUID> networkUuid = studyService.getNetworkUuid(studyName, userId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkUuid.flatMap(studyService::getNetworkVoltageLevels));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/geo-data/lines")
    @ApiOperation(value = "Get Network lines graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines graphics")})
    public ResponseEntity<Mono<String>> getLinesGraphics(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(studyService::getLinesGraphics));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/geo-data/substations")
    @ApiOperation(value = "Get Network substations graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations graphics")})
    public ResponseEntity<Mono<String>> getSubstationsGraphic(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(studyService::getSubstationsGraphics));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network-map/lines")
    @ApiOperation(value = "Get Network lines description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines data")})
    public ResponseEntity<Mono<String>> getLinesMapData(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(studyService::getLinesMapData));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network-map/substations")
    @ApiOperation(value = "Get Network substations description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations data")})
    public ResponseEntity<Mono<String>> getSubstationsMapData(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(studyService::getSubstationsMapData));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network-map/2-windings-transformers")
    @ApiOperation(value = "Get Network 2 windings transformers description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of 2 windings transformers data")})
    public ResponseEntity<Mono<String>> getTwoWindingsTransformersMapData(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(studyService::getTwoWindingsTransformersMapData));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network-map/3-windings-transformers")
    @ApiOperation(value = "Get Network 3 windings transformers description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of 3 windings transformers data")})
    public ResponseEntity<Mono<String>> getThreeWindingsTransformersMapData(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(studyService::getThreeWindingsTransformersMapData));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/network-map/generators")
    @ApiOperation(value = "Get Network generators description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of generators data")})
    public ResponseEntity<Mono<String>> getGeneratorsMapData(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkUuid(studyName, userId).flatMap(studyService::getGeneratorsMapData));
    }

    @PutMapping(value = "/{userId}/studies/{studyName}/network-modification/switches/{switchId}")
    @ApiOperation(value = "update a switch position", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The switch is updated")})
    public ResponseEntity<Mono<Void>> changeSwitchState(@PathVariable("studyName") String studyName,
                                                        @PathVariable("userId") String userId,
                                                        @PathVariable("switchId") String switchId,
                                                        @RequestParam("open") boolean open) {

        return ResponseEntity.ok().body(studyService.changeSwitchState(studyName, userId, switchId, open).then());
    }

    @PutMapping(value = "/{userId}/studies/{studyName}/network-modification/lines/{lineId}/switches")
    @ApiOperation(value = "export the study's network in the given format", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The network in the given format")})
    public Mono<ResponseEntity<Boolean>> lockoutLine(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId,
            @PathVariable("lineId") String lineId,
            @RequestParam("lockout") boolean lockout) {
        return studyService.lockoutLine(studyName, userId, lineId, lockout);
    }

    @PutMapping(value = "/{userId}/studies/{studyName}/loadflow/run")
    @ApiOperation(value = "run loadflow on study", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The loadflow has started")})
    public ResponseEntity<Mono<Void>> runLoadFlow(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {

        return ResponseEntity.ok().body(Mono.when(studyService.setLoadFlowRunning(studyName, userId)).then(studyService.runLoadFlow(studyName, userId).then()));
    }

    @PostMapping(value = "/{userId}/studies/{studyName}/rename")
    @ApiOperation(value = "Update the study name", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The updated study")})
    public ResponseEntity<Mono<StudyInfos>> renameStudy(@RequestHeader("userId") String headerUserId,
                                                         @PathVariable("studyName") String studyName,
                                                         @PathVariable("userId") String userId,
                                                         @RequestBody RenameStudyAttributes renameStudyAttributes) {

        Mono<StudyInfos> studyMono = studyService.renameStudy(studyName, userId, headerUserId, renameStudyAttributes.getNewStudyName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyMono);
    }

    @GetMapping(value = "/export-network-formats")
    @ApiOperation(value = "get the available export format", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The available export format")})
    public ResponseEntity<Mono<Collection<String>>> getExportFormats() {
        Mono<Collection<String>> formatsMono = studyService.getExportFormats();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(formatsMono);
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/export-network/{format}")
    @ApiOperation(value = "export the study's network in the given format", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The network in the given format")})
    public Mono<ResponseEntity<byte[]>> exportNetwork(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId,
            @PathVariable("format") String format) {

        Mono<ExportNetworkInfos> exportNetworkInfosMono = studyService.exportNetwork(studyName, userId, format);
        return exportNetworkInfosMono.map(exportNetworkInfos -> {
            HttpHeaders header = new HttpHeaders();
            header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getFileName(), StandardCharsets.UTF_8).build());
            return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
        });
    }

    @PostMapping(value = "/{userId}/studies/{studyName}/security-analysis/run")
    @ApiOperation(value = "run security analysis on study", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The security analysis has started")})
    public ResponseEntity<Mono<UUID>> runSecurityAnalysis(@ApiParam(value = "Study name") @PathVariable("studyName") String studyName,
                                                          @ApiParam(value = "User ID") @PathVariable("userId") String userId,
                                                          @ApiParam(value = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contigencyListNames,
                                                          @RequestBody(required = false) String parameters) {
        List<String> nonNullcontingencyListNames = contigencyListNames != null ? contigencyListNames : Collections.emptyList();
        String nonNullParameters = Objects.toString(parameters, "");
        return ResponseEntity.ok().body(studyService.runSecurityAnalysis(studyName, userId, nonNullcontingencyListNames, nonNullParameters));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/security-analysis/result")
    @ApiOperation(value = "Get a security analysis result on study", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The security analysis result"),
                           @ApiResponse(code = 404, message = "The security analysis has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisResult(@ApiParam(value = "Study name") @PathVariable("studyName") String studyName,
                                                                  @ApiParam(value = "User ID") @PathVariable("userId") String userId,
                                                                  @ApiParam(value = "Limit types") @RequestParam(name = "limitType", required = false) List<String> limitTypes) {
        List<String> nonNullLimitTypes = limitTypes != null ? limitTypes : Collections.emptyList();
        return studyService.getSecurityAnalysisResult(studyName, userId, nonNullLimitTypes)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/contingency-count")
    @ApiOperation(value = "Get contingency count for a list of contingency list on a study", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The contingency count")})
    public Mono<ResponseEntity<Integer>> getContingencyCount(@ApiParam(value = "Study name") @PathVariable("studyName") String studyName,
                                                             @ApiParam(value = "User ID") @PathVariable("userId") String userId,
                                                             @ApiParam(value = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contigencyListNames) {
        List<String> nonNullcontigencyListNames = contigencyListNames != null ? contigencyListNames : Collections.emptyList();
        return studyService.getContingencyCount(studyName, userId, nonNullcontigencyListNames)
                .map(count -> ResponseEntity.ok().body(count));
    }

    @PostMapping(value = "/{userId}/studies/{studyName}/loadflow/parameters")
    @ApiOperation(value = "set loadflow parameters on study, reset to default ones if empty body", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The loadflow parameters are set")})
    public ResponseEntity<Mono<Void>> setLoadflowParameters(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId,
            @RequestBody(required = false) LoadFlowParameters lfParameter) {
        return ResponseEntity.ok().body(studyService.setLoadFlowParameters(studyName, userId, lfParameter));
    }

    @GetMapping(value = "/{userId}/studies/{studyName}/loadflow/parameters")
    @ApiOperation(value = "Get loadflow parameters on study", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The loadflow parameters")})
    public ResponseEntity<Mono<LoadFlowParameters>> getLoadflowParameters(
            @PathVariable("studyName") String studyName,
            @PathVariable("userId") String userId) {
        return ResponseEntity.ok().body(studyService.getLoadFlowParameters(studyName, userId));
    }
}
