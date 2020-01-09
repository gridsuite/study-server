/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.study.server.dto.StudyInfos;
import com.powsybl.study.server.dto.VoltageLevelAttributes;
import com.powsybl.study.server.repository.Study;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.powsybl.study.server.StudyConstants.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION)
@Transactional
@Api(value = "Study server")
@ComponentScan(basePackageClasses = StudyService.class)
public class StudyController {

    @Autowired
    private StudyService studyService;

    @GetMapping(value = "/studies")
    @ApiOperation(value = "Get all studies")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of studies")})
    public ResponseEntity<List<StudyInfos>> getStudyList() {
        List<StudyInfos> studies = studyService.getStudyList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studies);
    }

    @PostMapping(value = "/studies/{studyName}/cases/{caseName}")
    @ApiOperation(value = "create a study from an existing case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist or the case doesn't exists")})
    public ResponseEntity<Void> createStudyFromExistingCase(@PathVariable("studyName") String studyName,
                                                                  @PathVariable("caseName") String caseName,
                                                                  @RequestParam("description") String description) {

        if (studyService.studyExists(studyName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STUDY_ALREADY_EXISTS);
        }

        if (Boolean.TRUE.equals(!studyService.caseExists(caseName))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CASE_DOESNT_EXISTS);
        }
        studyService.createStudy(studyName, caseName, description);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyName}")
    @ApiOperation(value = "create a study and import the case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist"),
            @ApiResponse(code = 500, message = "The storage is down or a file with the same name already exists")})
    public ResponseEntity<Void> createStudy(@PathVariable("studyName") String studyName,
                                                  @RequestParam("caseFile") MultipartFile caseFile,
                                                  @RequestParam("description") String description) throws IOException {

        if (studyService.studyExists(studyName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STUDY_ALREADY_EXISTS);
        }

        if (studyService.studyExists(caseFile.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CASE_ALREADY_EXISTS);
        }

        studyService.createStudy(studyName, caseFile, description);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyName}")
    @ApiOperation(value = "get a study")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The study information"),
            @ApiResponse(code = 404, message = "The study doesn't exist")})
    public ResponseEntity<Study> getStudy(@PathVariable("studyName") String studyName) {
        Study study = studyService.getStudy(studyName);
        if (study == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(study);
    }

    @DeleteMapping(value = "/studies/{studyName}")
    @ApiOperation(value = "delete the study")
    @ApiResponse(code = 200, message = "Study deleted")
    public ResponseEntity<Study> deleteStudy(@PathVariable("studyName") String studyName) {
        studyService.deleteStudy(studyName);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/cases")
    @ApiOperation(value = "Get the case list")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The case list"),
            @ApiResponse(code = 500, message = "The storage is down")})
    public ResponseEntity<Map<String, String>> getCaseList() {
        Map<String, String> caseList = studyService.getCaseList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(caseList);
    }

    @GetMapping(value = "/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(code = 200, message = "The svg")
    public ResponseEntity<byte[]> getVoltageLevelDiagram(
            @PathVariable("studyName") String studyName,
            @PathVariable("voltageLevelId") String voltageLevelId) {
        UUID networkUuid = studyService.getStudyUuid(studyName);

        byte[] svg = studyService.getVoltageLevelSvg(networkUuid, voltageLevelId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(svg);
    }

    @GetMapping(value = "/studies/{studyName}/network/voltage-levels")
    @ApiOperation(value = "get the voltage levels for a given network")
    @ApiResponse(code = 200, message = "The voltage level list of the network")
    public ResponseEntity<List<VoltageLevelAttributes>> getNetworkVoltyutageLevels(@PathVariable("studyName") String studyName) {
        UUID networkUuid = studyService.getStudyUuid(studyName);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkVoltageLevels(networkUuid));

    }

    @GetMapping(value = "/studies/{studyName}/geo-data/lines")
    @ApiOperation(value = "Get Network lines graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines graphics")})
    public ResponseEntity<String> getLinesGraphics(@PathVariable("studyName") String studyName) {
        UUID networkUuid = studyService.getStudyUuid(studyName);
        String lineGraphics = studyService.getLinesGraphics(networkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(lineGraphics);
    }

    @GetMapping(value = "/studies/{studyName}/geo-data/substations")
    @ApiOperation(value = "Get Network substations graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations graphics")})
    public ResponseEntity<String> getSubstationsGraphic(@PathVariable("studyName") String studyName) {
        UUID networkUuid = studyService.getStudyUuid(studyName);
        String substationGraphics = studyService.getSubstationsGraphics(networkUuid);
        return  ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(substationGraphics);
    }

}
