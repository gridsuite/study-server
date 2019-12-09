/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.converter.model.NetworkIds;
import com.powsybl.study.server.dto.Study;
import com.powsybl.study.server.dto.VoltageLevelAttributes;
import infrastructure.LineGraphic;
import infrastructure.SubstationGraphic;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
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
public class StudyController {

    @Autowired
    private StudyService studyService;

    @GetMapping(value = "/studies")
    @ApiOperation(value = "Get all studies")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of studies")})
    public ResponseEntity<List<Study>> getStudyList() {
        List<Study> studies = studyService.getStudyList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(studies);
    }

    @PostMapping(value = "/studies/{studyName}/{caseName}")
    @ApiOperation(value = "create a study from an existing case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist or the case doesn't exists")})
    public ResponseEntity<NetworkIds> createStudyFromExistingCase(@PathVariable("studyName") String studyName,
                                              @PathVariable("caseName") String caseName,
                                              @RequestParam("description") String description) {

        if (studyService.studyExists(studyName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STUDY_ALREADY_EXISTS);
        }

        if (!studyService.caseExists(caseName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CASE_DOESNT_EXISTS);
        }
        NetworkIds networkIds = studyService.createStudy(studyName, caseName, description);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(networkIds);
    }

    @PostMapping(value = "/studies/{studyName}")
    @ApiOperation(value = "create a study and import the case")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "The id of the network imported"),
            @ApiResponse(code = 409, message = "The study already exist"),
            @ApiResponse(code = 500, message = "The storage is down or a file with the same name already exists")})
    public ResponseEntity<NetworkIds> createStudy(@PathVariable("studyName") String studyName,
                                                  @RequestParam("caseFile") MultipartFile caseFile,
                                                  @RequestParam("description") String description) throws IOException {

        if (studyService.studyExists(studyName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STUDY_ALREADY_EXISTS);
        }

        if (studyService.studyExists(caseFile.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CASE_ALREADY_EXISTS);
        }

        NetworkIds networkIds;
        try {
            networkIds = studyService.createStudy(studyName, caseFile, description);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(networkIds);
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
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(study);
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
        try {
            Map<String, String> caseList = studyService.getCaseList();
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(caseList);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping(value = "/svg/{networkUuid}/{voltageLevelId}")
    @ApiOperation(value = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(code = 200, message = "The svg")
    public ResponseEntity<byte[]> getVoltageLevelDiagram(
            @PathVariable("networkUuid") UUID networkUuid,
            @PathVariable("voltageLevelId") String voltageLevelId) {
        try {
            byte[] svg = studyService.getVoltageLevelSvg(networkUuid, voltageLevelId);

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(svg);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping(value = "/networks/{networkUuid}/voltage-levels")
    @ApiOperation(value = "get the voltage levels for a given network")
    @ApiResponse(code = 200, message = "The voltage level list of the network")
    public ResponseEntity<List<VoltageLevelAttributes>> getNetworkVoltyutageLevels(@PathVariable("networkUuid") UUID networkUuid) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(studyService.getNetworkVoltageLevels(networkUuid));
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping(value = "lines-graphics/{networkUuid}/")
    @ApiOperation(value = "Get Network lines graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines graphics")})
    public ResponseEntity<List<LineGraphic>> getLinesGraphics(@PathVariable("networkUuid") UUID networkUuid) {
        List<LineGraphic> lineGraphics = studyService.getLinesGraphics(networkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(lineGraphics);
    }

    @GetMapping(value = "lines-graphics-with-pagination/{networkUuid}")
    @ApiOperation(value = "Get Network Lines graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of lines graphics")})
    public ResponseEntity<List<LineGraphic>> getLinesGraphicsWithPagination(@PathVariable("networkUuid") UUID networkUuid, @RequestParam(name = "page") int page, @RequestParam(name = "size") int size) {
        List<LineGraphic> lineGraphics = studyService.getLinesGraphicsWithPagination(networkUuid, page, size);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON_UTF8).body(lineGraphics);
    }

    @GetMapping(value = "substations-graphics/{networkUuid}")
    @ApiOperation(value = "Get Network substations graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations graphics")})
    public ResponseEntity<List<SubstationGraphic>> getSubstationsGraphic(@PathVariable("networkUuid") UUID networkUuid) {
        List<SubstationGraphic> substationGraphics = studyService.getSubstationsGraphics(networkUuid);
        return  ResponseEntity.ok().body(substationGraphics);
    }

    @GetMapping(value = "substations-graphics-with-pagination/{networkUuid}")
    @ApiOperation(value = "Get Network substations graphics", produces = "application/json")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The list of substations graphics")})
    public ResponseEntity<List<SubstationGraphic>> getSubstationsGraphicsWithPagination(@PathVariable("networkUuid") UUID networkUuid, @RequestParam(name = "page") int page, @RequestParam(name = "size") int size) {
        List<SubstationGraphic> substationGraphics = studyService.getSubstationsGraphicsWithPagination(networkUuid, page, size);
        return ResponseEntity.ok().body(substationGraphics);

    }
}
