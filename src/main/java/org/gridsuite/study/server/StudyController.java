/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.RenameStudyAttributes;
import org.gridsuite.study.server.dto.StudyInfos;
import org.gridsuite.study.server.dto.VoltageLevelAttributes;
import org.gridsuite.study.server.repository.Study;
import io.swagger.annotations.*;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION)
@Transactional
@Api(value = "Study server")
@ComponentScan(basePackageClasses = StudyService.class)
public class StudyController {

    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping(value = "/studies")
    @ApiOperation(value = "Get all studies")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of studies")})
    public ResponseEntity<Flux<StudyInfos>> getStudyList() {
        Flux<StudyInfos> studies = studyService.getStudyList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studies);
    }

    @PostMapping(value = "/studies/{studyName}/cases/{caseUuid}")
    @ApiOperation(value = "create a study from an existing case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist or the case doesn't exists")})
    public Mono<ResponseEntity<Void>> createStudyFromExistingCase(@PathVariable("studyName") String studyName,
                                                    @PathVariable("caseUuid") UUID caseUuid,
                                                    @RequestParam("description") String description) {
        return Mono.when(studyService.assertStudyNotExists(studyName), studyService.assertCaseExists(caseUuid))
                .then(studyService.createStudy(studyName, caseUuid, description).map(s -> ResponseEntity.ok().build()));
    }

    @PostMapping(value = "/studies/{studyName}")
    @ApiOperation(value = "create a study and import the case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist"),
            @ApiResponse(code = 500, message = "The storage is down or a file with the same name already exists")})
    public Mono<ResponseEntity<Void>> createStudy(@PathVariable("studyName") String studyName,
                                    @RequestParam("caseFile") MultipartFile caseFile,
                                    @RequestParam("description") String description) {
        return studyService.assertStudyNotExists(studyName)
                .flatMap(e -> studyService.createStudy(studyName, caseFile, description).map(s -> ResponseEntity.ok().build()));
    }

    @GetMapping(value = "/studies/{studyName}")
    @ApiOperation(value = "get a study")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The study information"),
            @ApiResponse(code = 404, message = "The study doesn't exist")})
    public Mono<ResponseEntity<Study>> getStudy(@PathVariable("studyName") String studyName) {
        Mono<Study> studyMono = studyService.getStudy(studyName);
        return studyMono.map(s -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(s))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @DeleteMapping(value = "/studies/{studyName}")
    @ApiOperation(value = "delete the study")
    @ApiResponse(code = 200, message = "Study deleted")
    public Mono<ResponseEntity<Void>> deleteStudy(@PathVariable("studyName") String studyName) {
        return studyService.deleteStudy(studyName).map(e -> ResponseEntity.ok().build());
    }

    @GetMapping(value = "/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(code = 200, message = "The svg")
    public Mono<ResponseEntity<byte[]>> getVoltageLevelDiagram(
            @PathVariable("studyName") String studyName,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring) {
        return studyService.getStudyUuid(studyName)
                .flatMap(uuid -> studyService.getVoltageLevelSvg(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring))
                .map(svg -> ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(svg))
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level", produces = "application/json")
    @ApiResponse(code = 200, message = "The svg and metadata")
    public Mono<ResponseEntity<String>> getVoltageLevelDiagramAndMetadata(
            @PathVariable("studyName") String studyName,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @ApiParam(value = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @ApiParam(value = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @ApiParam(value = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @ApiParam(value = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring) {
        return studyService.getStudyUuid(studyName)
                .flatMap(uuid -> studyService.getVoltageLevelSvgAndMetadata(uuid, voltageLevelId, useName, centerLabel, diagonalLabel, topologicalColoring))
                .map(svgAndMetadata -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(svgAndMetadata))
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/studies/{studyName}/network/voltage-levels")
    @ApiOperation(value = "get the voltage levels for a given network")
    @ApiResponse(code = 200, message = "The voltage level list of the network")
    public Mono<ResponseEntity<List<VoltageLevelAttributes>>> getNetworkVoltageLevels(@PathVariable("studyName") String studyName) {
        Mono<UUID> networkUuid = studyService.getStudyUuid(studyName);
        return networkUuid.map(studyService::getNetworkVoltageLevels)
        .map(vls -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(vls));
    }

    @GetMapping(value = "/studies/{studyName}/geo-data/lines")
    @ApiOperation(value = "Get Network lines graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines graphics")})
    public Mono<ResponseEntity<String>> getLinesGraphics(@PathVariable("studyName") String studyName) {
        return studyService.getStudyUuid(studyName)
                .flatMap(studyService::getSubstationsGraphics)
                .map(lineGraphics -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(lineGraphics));
    }

    @GetMapping(value = "/studies/{studyName}/geo-data/substations")
    @ApiOperation(value = "Get Network substations graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations graphics")})
    public Mono<ResponseEntity<String>> getSubstationsGraphic(@PathVariable("studyName") String studyName) {
        return studyService.getStudyUuid(studyName)
                .flatMap(studyService::getSubstationsGraphics)
                .map(substationGraphics -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(substationGraphics));
    }

    @GetMapping(value = "/studies/{studyName}/network-map/lines")
    @ApiOperation(value = "Get Network lines description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines graphics")})
    public Mono<ResponseEntity<String>> getLinesMapData(@PathVariable("studyName") String studyName) {
        return studyService.getStudyUuid(studyName)
                .flatMap(studyService::getLinesMapData)
                .map(linesMapData -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linesMapData));
    }

    @GetMapping(value = "/studies/{studyName}/network-map/substations")
    @ApiOperation(value = "Get Network substations description", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations graphics")})
    public Mono<ResponseEntity<String>> getSubstationsMapData(@PathVariable("studyName") String studyName) {
        return studyService.getStudyUuid(studyName)
                .flatMap(studyService::getSubstationsMapData)
                .map(substationMapData -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(substationMapData));
    }

    @PutMapping(value = "/studies/{studyName}/network-modification/switches/{switchId}")
    @ApiOperation(value = "update a switch position", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The switch is updated")})
    public Mono<ResponseEntity<Void>> changeSwitchState(@PathVariable("studyName") String studyName,
                                                          @PathVariable("switchId") String switchId,
                                                          @RequestParam("open") boolean open) {
        return studyService.changeSwitchState(studyName, switchId, open).map(e -> ResponseEntity.ok().build());
    }

    @PutMapping(value = "/studies/{studyName}/loadflow/run")
    @ApiOperation(value = "run loadflow on study", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The loadflow has started")})
    public Mono<ResponseEntity<Void>> runLoadFlow(@PathVariable("studyName") String studyName) {
        return studyService.runLoadFlow(studyName).map(e -> ResponseEntity.ok().build());
    }

    @PostMapping(value = "/studies/{studyName}/rename")
    @ApiOperation(value = "Update the study name", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The updated study")})
    public Mono<ResponseEntity<Study>> renameStudy(@PathVariable("studyName") String studyName,
                                                   @RequestBody RenameStudyAttributes renameStudyAttributes) {
        Mono<Study> studyMono = studyService.renameStudy(studyName, renameStudyAttributes.getNewStudyName());
        return studyMono.map(study -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(study))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }
}

