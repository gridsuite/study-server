/*
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

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.SingleLineDiagramService;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.http.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import java.beans.PropertyEditorSupport;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION)
@Tag(name = "Study server")
public class StudyController {

    private final StudyService studyService;
    private final NetworkService networkStoreService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final SingleLineDiagramService singleLineDiagramService;

    public StudyController(StudyService studyService,
            NetworkService networkStoreService,
            NetworkModificationTreeService networkModificationTreeService,
            SingleLineDiagramService singleLineDiagramService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkStoreService = networkStoreService;
        this.singleLineDiagramService = singleLineDiagramService;
    }

    static class MyEnumConverter<E extends Enum<E>> extends PropertyEditorSupport {
        private final Class<E> enumClass;

        public MyEnumConverter(Class<E> enumClass) {
            this.enumClass = enumClass;
        }

        @Override
        public void setAsText(final String text) throws IllegalArgumentException {
            try {
                E value = Enum.valueOf(enumClass, text.toUpperCase());
                setValue(value);
            } catch (IllegalArgumentException ex) {
                String avail = StringUtils.join(enumClass.getEnumConstants(), ", ");
                throw new IllegalArgumentException(String.format("Enum unknown entry '%s' should be among %s", text, avail));
            }
        }
    }

    static class MyModificationTypeConverter extends PropertyEditorSupport {

        public MyModificationTypeConverter() {
            super();
        }

        @Override
        public void setAsText(final String text) throws IllegalArgumentException {
            setValue(ModificationType.getTypeFromUri(text));
        }
    }

    @InitBinder
    public void initBinder(WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(EquipmentInfosService.FieldSelector.class,
            new MyEnumConverter<>(EquipmentInfosService.FieldSelector.class));
        webdataBinder.registerCustomEditor(ModificationType.class, new MyModificationTypeConverter());
    }

    @GetMapping(value = "/studies")
    @Operation(summary = "Get all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of studies")})
    public ResponseEntity<List<CreatedStudyBasicInfos>> getStudyList() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudies());
    }

    @GetMapping(value = "/studies/{studyUuid}/case/name", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get study case name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study case name")})
    public ResponseEntity<String> getStudyCaseName(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getCaseName(studyUuid));
    }

    @GetMapping(value = "/study_creation_requests")
    @Operation(summary = "Get all study creation requests for a user")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of study creation requests")})
    public ResponseEntity<List<BasicStudyInfos>> getStudyCreationRequestList() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudiesCreationRequests());
    }

    @GetMapping(value = "/studies/metadata")
    @Operation(summary = "Get studies metadata")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of studies metadata")})
    public ResponseEntity<List<CreatedStudyBasicInfos>> getStudyListMetadata(@RequestParam("ids") List<UUID> uuids) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudiesMetadata(uuids));
    }

    @PostMapping(value = "/studies/cases/{caseUuid}")
    @Operation(summary = "create a study from an existing case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The id of the network imported"),
        @ApiResponse(responseCode = "409", description = "The study already exist or the case doesn't exists")})
    public ResponseEntity<BasicStudyInfos> createStudyFromExistingCase(@PathVariable("caseUuid") UUID caseUuid,
                                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                                             @RequestBody(required = false) Map<String, Object> importParameters,
                                                                             @RequestHeader("userId") String userId) {
        studyService.assertCaseExists(caseUuid);
        BasicStudyInfos createStudy = studyService.createStudy(caseUuid, userId, studyUuid, importParameters);
        return ResponseEntity.ok().body(createStudy);
    }

    @PostMapping(value = "/studies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "create a study and import the case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The id of the network imported"),
        @ApiResponse(responseCode = "409", description = "The study already exist"),
        @ApiResponse(responseCode = "500", description = "The storage is down or a file with the same name already exists")})
    public ResponseEntity<BasicStudyInfos> createStudy(@RequestParam("caseFile") MultipartFile caseFile,
                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                             @RequestHeader("userId") String userId) {
        BasicStudyInfos createStudy = studyService.createStudy(caseFile, userId, studyUuid);
        return ResponseEntity.ok().body(createStudy);
    }

    @PostMapping(value = "/studies")
    @Operation(summary = "create a study from an existing one")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The study was successfully created"),
        @ApiResponse(responseCode = "404", description = "The source study doesn't exist")})
    public ResponseEntity<BasicStudyInfos> createStudy(@RequestParam("duplicateFrom") UUID sourceStudyUuid,
                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                             @RequestHeader("userId") String userId) {
        BasicStudyInfos createStudy = studyService.createStudy(sourceStudyUuid, studyUuid, userId);
        return createStudy != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(createStudy) :
                ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "get a study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The study information"),
        @ApiResponse(responseCode = "404", description = "The study doesn't exist")})
    public ResponseEntity<StudyInfos> getStudy(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyInfos(studyUuid));
    }

    @DeleteMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "delete the study")
    @ApiResponse(responseCode = "200", description = "Study deleted")
    public ResponseEntity<Void> deleteStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                  @RequestHeader("userId") String userId) {
        studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg"),
        @ApiResponse(responseCode = "404", description = "The voltage level has not been found")})
    public ResponseEntity<byte[]> getVoltageLevelDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        byte[] result = studyService.getVoltageLevelSvg(studyUuid, voltageLevelId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), nodeUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg and metadata"),
        @ApiResponse(responseCode = "404", description = "The voltage level has not been found")})
    public ResponseEntity<String> getVoltageLevelDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        String result = studyService.getVoltageLevelSvgAndMetadata(studyUuid, voltageLevelId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), nodeUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels")
    @Operation(summary = "get the voltage levels for a given network")
    @ApiResponse(responseCode = "200", description = "The voltage level list of the network")
    public ResponseEntity<List<VoltageLevelInfos>> getNetworkVoltageLevels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevels(studyUuid, nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses")
    @Operation(summary = "get buses the for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The buses list of the network for given voltage level")
    public ResponseEntity<List<IdentifiableInfos>> getVoltageLevelBuses(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelBuses(studyUuid, nodeUuid, voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/busbar-sections")
    @Operation(summary = "get the busbar sections for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The busbar sections list of the network for given voltage level")
    public ResponseEntity<List<IdentifiableInfos>> getVoltageLevelBusbarSections(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelBusbarSections(studyUuid, nodeUuid, voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/lines")
    @Operation(summary = "Get Network lines graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lines graphics")})
    public ResponseEntity<String> getLinesGraphics(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLinesGraphics(networkStoreService.getNetworkUuid(studyUuid), nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/substations")
    @Operation(summary = "Get Network substations graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substations graphics")})
    public ResponseEntity<String> getSubstationsGraphic(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationsGraphics(networkStoreService.getNetworkUuid(studyUuid), nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines")
    @Operation(summary = "Get Network lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lines data")})
    public ResponseEntity<String> getLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLinesMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines/{lineId}")
    @Operation(summary = "Get specific line description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line data")})
    public ResponseEntity<String> getLineMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "line id") @PathVariable("lineId") String lineId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLineMapData(studyUuid, nodeUuid, lineId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations")
    @Operation(summary = "Get Network substations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substations data")})
    public ResponseEntity<String> getSubstationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations/{substationId}")
    @Operation(summary = "Get specific substation description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation data")})
    public ResponseEntity<String> getSubstationMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "substation Id") @PathVariable("substationId") String substationId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationMapData(studyUuid, nodeUuid, substationId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers")
    @Operation(summary = "Get Network 2 windings transformers description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of 2 windings transformers data")})
    public ResponseEntity<String> getTwoWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getTwoWindingsTransformersMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers/{twoWindingsTransformerId}")
    @Operation(summary = "Get specific two windings transformer description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The two windings transformer data")})
    public ResponseEntity<String> getTwoWindingsTransformerMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "two windings transformer id") @PathVariable("twoWindingsTransformerId") String twoWindingsTransformerId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getTwoWindingsTransformerMapData(studyUuid, nodeUuid, twoWindingsTransformerId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/3-windings-transformers")
    @Operation(summary = "Get Network 3 windings transformers description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of 3 windings transformers data")})
    public ResponseEntity<String> getThreeWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getThreeWindingsTransformersMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators")
    @Operation(summary = "Get Network generators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of generators data")})
    public ResponseEntity<String> getGeneratorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getGeneratorsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators/{generatorId}")
    @Operation(summary = "Get specific generator description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The generator data")})
    public ResponseEntity<String> getGeneratorMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "generator id") @PathVariable("generatorId") String generatorId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getGeneratorMapData(studyUuid, nodeUuid, generatorId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/batteries")
    @Operation(summary = "Get Network batteries description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of batteries data")})
    public ResponseEntity<String> getBatteriesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getBatteriesMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/dangling-lines")
    @Operation(summary = "Get Network dangling lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of dangling lines data")})
    public ResponseEntity<String> getDanglingLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getDanglingLinesMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines")
    @Operation(summary = "Get Network hvdc lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of hvdc lines data")})
    public ResponseEntity<String> getHvdcLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getHvdcLinesMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lcc-converter-stations")
    @Operation(summary = "Get Network lcc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lcc converter stations data")})
    public ResponseEntity<String> getLccConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLccConverterStationsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/vsc-converter-stations")
    @Operation(summary = "Get Network vsc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of vsc converter stations data")})
    public ResponseEntity<String> getVscConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVscConverterStationsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads")
    @Operation(summary = "Get Network loads description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of loads data")})
    public ResponseEntity<String> getLoadsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLoadsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads/{loadId}")
    @Operation(summary = "Get specific load description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load data")})
    public ResponseEntity<String> getLoadMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "load id") @PathVariable("loadId") String loadId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLoadMapData(studyUuid, nodeUuid, loadId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators")
    @Operation(summary = "Get Network shunt compensators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of shunt compensators data")})
    public ResponseEntity<String> getShuntCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getShuntCompensatorsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators/{shuntCompensatorId}")
    @Operation(summary = "Get specific shunt compensator description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The shunt compensator data")})
    public ResponseEntity<String> getShuntCompensatorMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "shunt compensator id") @PathVariable("shuntCompensatorId") String shuntCompensatorId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getShuntCompensatorMapData(studyUuid, nodeUuid, shuntCompensatorId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/static-var-compensators")
    @Operation(summary = "Get Network static var compensators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of static var compensators data")})
    public ResponseEntity<String> getStaticVarCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStaticVarCompensatorsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}")
    @Operation(summary = "Get specific voltage level description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level data")})
    public ResponseEntity<String> getVoltageLevelMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltage level id") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelMapData(studyUuid, nodeUuid, voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels")
    @Operation(summary = "Get network voltage level description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage levels data")})
    public ResponseEntity<String> getVoltageLevelsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/all")
    @Operation(summary = "Get Network equipments description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipments data")})
    public ResponseEntity<String> getAllMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getAllMapData(studyUuid, nodeUuid, substationsIds));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}")
    @Operation(summary = "update a switch position")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The switch is updated")})
    public ResponseEntity<Void> changeSwitchState(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("switchId") String switchId,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @Parameter(description = "Switch open state") @RequestParam("open") boolean open) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.changeSwitchState(studyUuid, switchId, open, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy")
    @Operation(summary = "change an equipment state in the network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment is updated")})
    public ResponseEntity<Void> applyGroovyScript(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @RequestBody String groovyScript) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.applyGroovyScript(studyUuid, groovyScript, nodeUuid);

        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationUuid}")
    @Operation(summary = "move network modification before another")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification order is updated")})
    public ResponseEntity<Void> moveModification(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @PathVariable("modificationUuid") UUID modificationUuid,
                                                        @Nullable @Parameter(description = "move before, if no value move to end") @RequestParam(value = "beforeUuid") UUID beforeUuid) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.reorderModification(studyUuid, nodeUuid, modificationUuid, beforeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Change the given line status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Line status changed")})
    public ResponseEntity<Void> changeLineStatus(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("lineId") String lineId,
            @RequestBody String status) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.changeLineStatus(studyUuid, lineId, status, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run")
    @Operation(summary = "run loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has started")})
    public ResponseEntity<Void> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertLoadFlowRunnable(nodeUuid);
        studyService.runLoadFlow(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/export-network-formats")
    @Operation(summary = "get the available export format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The available export format")})
    public ResponseEntity<String> getExportFormats() {
        String formatsJson = studyService.getExportFormats();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(formatsJson);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}")
    @Operation(summary = "export the study's network in the given format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network in the given format")})
    public ResponseEntity<byte[]> exportNetwork(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("format") String format,
            @RequestParam(value = "formatParameters", required = false) String parametersJson) {

        studyService.assertRootNodeOrBuiltNode(studyUuid, nodeUuid);
        ExportNetworkInfos exportNetworkInfos = studyService.exportNetwork(studyUuid, nodeUuid, format, parametersJson);

        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getFileName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run")
    @Operation(summary = "run security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has started")})
    public ResponseEntity<UUID> runSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames,
                                                          @RequestBody(required = false) String parameters) {
        List<String> nonNullcontingencyListNames = contingencyListNames != null ? contingencyListNames : Collections.emptyList();
        String nonNullParameters = Objects.toString(parameters, "");
        studyService.assertIsNodeNotReadOnly(nodeUuid);

        return ResponseEntity.ok().body(studyService.runSecurityAnalysis(studyUuid, nonNullcontingencyListNames, nonNullParameters, nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result")
    @Operation(summary = "Get a security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public ResponseEntity<String> getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                  @Parameter(description = "Limit types") @RequestParam(name = "limitType", required = false) List<String> limitTypes) {
        List<String> nonNullLimitTypes = limitTypes != null ? limitTypes : Collections.emptyList();
        String result = studyService.getSecurityAnalysisResult(nodeUuid, nonNullLimitTypes);
        return result != null ? ResponseEntity.ok().body(result) :
               ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/contingency-count")
    @Operation(summary = "Get contingency count for a list of contingency list on a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The contingency count")})
    public ResponseEntity<Integer> getContingencyCount(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames) {
        List<String> nonNullContingencyListNames = contingencyListNames != null ? contingencyListNames : Collections.emptyList();
        return ResponseEntity.ok().body(studyService.getContingencyCount(studyUuid, nonNullContingencyListNames, nodeUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "set loadflow parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters are set")})
    public ResponseEntity<Void> setLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) LoadFlowParameters lfParameter) {
        studyService.setLoadFlowParameters(studyUuid, lfParameter);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "Get loadflow parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters")})
    public ResponseEntity<LoadFlowParameters> getLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowParameters(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "set load flow provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow provider is set")})
    public ResponseEntity<Void> setLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                          @RequestBody(required = false) String provider) {
        studyService.updateLoadFlowProvider(studyUuid, provider);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "Get load flow provider for a specified study, empty string means default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow provider is returned")})
    public ResponseEntity<String> getLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowProvider(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg"),
        @ApiResponse(responseCode = "404", description = "The substation has not been found")})
    public ResponseEntity<byte[]> getSubstationDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        byte[] result = studyService.getSubstationSvg(studyUuid, substationId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), substationLayout, nodeUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg and metadata"),
        @ApiResponse(responseCode = "404", description = "The substation has not been found")})
    public ResponseEntity<String> getSubstationDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        String result = studyService.getSubstationSvgAndMetadata(studyUuid, substationId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), substationLayout, nodeUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-area-diagram")
    @Operation(summary = "get the network area diagram for the given network and voltage levels")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<String> getNeworkAreaDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Voltage levels ids") @RequestParam(name = "voltageLevelsIds", required = true) List<String> voltageLevelsIds,
            @Parameter(description = "depth") @RequestParam(name = "depth", defaultValue = "0") int depth) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNeworkAreaDiagram(studyUuid, nodeUuid, voltageLevelsIds, depth));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/status")
    @Operation(summary = "Get the security analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis status"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis status has not been found")})
    public ResponseEntity<String> getSecurityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = studyService.getSecurityAnalysisStatus(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Void> stopSecurityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.stopSecurityAnalysis(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get node report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<ReporterModel>> getNodeReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "Node only report") @RequestParam(value = "nodeOnlyReport", required = false, defaultValue = "true") boolean nodeOnlyReport) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNodeReport(studyUuid, nodeUuid, nodeOnlyReport));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get node network modifications")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node network modifications"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<String> getNodeNetworkModifications(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        // Return json string because modification dtos are not available here
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(networkModificationTreeService.getNetworkModifications(studyUuid, nodeUuid));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/report")
    @Operation(summary = "Delete node report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report has been deleted"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteNodeReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.deleteNodeReport(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/svg-component-libraries")
    @Operation(summary = "Get a list of the available svg component libraries")
    @ApiResponse(responseCode = "200", description = "The list of the available svg component libraries")
    public ResponseEntity<List<String>> getAvailableSvgComponentLibraries() {
        List<String> libraries = singleLineDiagramService.getAvailableSvgComponentLibraries();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(libraries);
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads")
    @Operation(summary = "create a load in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load has been created")})
    public ResponseEntity<Void> createLoad(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String createLoadAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createEquipment(studyUuid, createLoadAttributes, ModificationType.LOAD_CREATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads")
    @Operation(summary = "modify a load in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load has been modified")})
    public ResponseEntity<Void> modifyLoad(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String modifyLoadAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.modifyEquipment(studyUuid, modifyLoadAttributes, ModificationType.LOAD_MODIFICATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-creation")
    @Operation(summary = "update a load creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load creation has been updated")})
    public ResponseEntity<Void> updateLoadCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("modificationUuid") UUID modificationUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String createLoadAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, createLoadAttributes, ModificationType.LOAD_CREATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-modification")
    @Operation(summary = "update a load modification in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load modification has been updated")})
    public ResponseEntity<Void> updateLoadModification(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("modificationUuid") UUID modificationUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestBody String modifyLoadAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentModification(studyUuid, modifyLoadAttributes, ModificationType.LOAD_MODIFICATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{typeModification}/{modificationUuid}")
    @Operation(summary = "update an equipment modification in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment modification has been updated")})
    public ResponseEntity<Void> updateEquipmentModification(@PathVariable("studyUuid") UUID studyUuid,
                                                                  @PathVariable("modificationUuid") UUID modificationUuid,
                                                                  @PathVariable("nodeUuid") UUID nodeUuid,
                                                                  @PathVariable("typeModification") ModificationType typeModification,
                                                                  @RequestBody String modifyEquipmentAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.updateEquipmentModification(studyUuid, modifyEquipmentAttributes, typeModification, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{typeModification}")
    @Operation(summary = "modify a generator in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification has been modified")})
    public ResponseEntity<Void> modifyEquipment(@PathVariable("studyUuid") UUID studyUuid,
                                                      @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @PathVariable("typeModification") ModificationType typeModification,
                                                      @RequestBody String modifyEquipmentAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.modifyEquipment(studyUuid, modifyEquipmentAttributes, typeModification, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification")
    @Operation(summary = "delete network modifications")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications have been deleted")})
    public ResponseEntity<Void> deleteModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                          @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestParam(value = "modificationsUuids") List<UUID> modificationsUuids) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.deleteModifications(studyUuid, nodeUuid, modificationsUuids);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search studies in elasticsearch")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of studies found")})
    public ResponseEntity<List<CreatedStudyBasicInfos>> searchStudies(@Parameter(description = "Lucene query") @RequestParam(value = "q") String query) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.searchStudies(query));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search equipments in elasticsearch")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of equipments found"),
        @ApiResponse(responseCode = "404", description = "The study not found"),
        @ApiResponse(responseCode = "400", description = "The fieLd selector is unknown")
    })
    public ResponseEntity<List<EquipmentInfos>> searchEquipments(
        @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "User input") @RequestParam(value = "userInput") String userInput,
        @Parameter(description = "What against to match") @RequestParam(value = "fieldSelector") EquipmentInfosService.FieldSelector fieldSelector,
        @Parameter(description = "Should search in upstream built node") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode,
        @Parameter(description = "Equipment type") @RequestParam(value = "equipmentType", required = false) String equipmentType) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(studyService.searchEquipments(studyUuid, nodeUuid, userInput, fieldSelector, equipmentType, inUpstreamBuiltParentNode));
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "Create a node as before / after the given node ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node has been added"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<AbstractNode> createNode(@RequestBody AbstractNode node,
                                                         @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                         @Parameter(description = "parent id of the node created") @PathVariable(name = "id") UUID referenceId,
                                                         @Parameter(description = "node is inserted before the given node ID") @RequestParam(name = "mode", required = false, defaultValue = "CHILD") InsertMode insertMode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationTreeService.createNode(studyUuid, referenceId, node, insertMode));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "Delete node with given id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the nodes have been successfully deleted"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> deleteNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @Parameter(description = "id of child to remove") @PathVariable("id") UUID nodeId,
                                                 @Parameter(description = "deleteChildren") @RequestParam(value = "deleteChildren", defaultValue = "false") boolean deleteChildren) {
        studyService.deleteNode(studyUuid, nodeId, deleteChildren);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/tree")
    @Operation(summary = "get network modification tree for the given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "network modification tree"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<RootNode> getNetworkModificationTree(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid);
        return rootNode != null ?
            ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNode)
            : ResponseEntity.notFound().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/tree/nodes")
    @Operation(summary = "update node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the node has been updated"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> updateNode(@RequestBody AbstractNode node,
                                                 @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        networkModificationTreeService.updateNode(studyUuid, node);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "get simplified node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "simplified nodes (without children"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<AbstractNode> getNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "node uuid") @PathVariable("id") UUID nodeId) {
        AbstractNode node = networkModificationTreeService.getSimpleNode(nodeId);
        return node != null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(node)
                : ResponseEntity.notFound().build();
    }

    @RequestMapping(value = "/studies/{studyUuid}/nodes", method = RequestMethod.HEAD)
    @Operation(summary = "Test if a node name exists")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "node name exist"),
        @ApiResponse(responseCode = "204", description = "node name doesn't exist"),
    })
    public ResponseEntity<Void> nodeNameExists(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                               @Parameter(description = "Node name") @RequestParam("nodeName") String nodeName) {

        return networkModificationTreeService.isNodeNameExists(studyUuid, nodeName) ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/nextUniqueName")
    @Operation(summary = "Get unique node name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "unique node name generated")})

    public ResponseEntity<String> getUniqueNodeName(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().body(networkModificationTreeService.getUniqueNodeName(studyUuid));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}")
    @Operation(summary = "Delete equipment in study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment was deleted"), @ApiResponse(responseCode = "404", description = "The study not found")})
    public ResponseEntity<Void> deleteEquipment(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @Parameter(description = "Equipment type") @PathVariable("equipmentType") String equipmentType,
                                                      @Parameter(description = "Equipment id") @PathVariable("equipmentId") String equipmentId) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.deleteEquipment(studyUuid, equipmentType, equipmentId, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators")
    @Operation(summary = "create a generator in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The generator has been created")})
    public ResponseEntity<Void> createGenerator(@PathVariable("studyUuid") UUID studyUuid,
                                                      @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @RequestBody String createGeneratorAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createEquipment(studyUuid, createGeneratorAttributes, ModificationType.GENERATOR_CREATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/generators-creation")
    @Operation(summary = "update a generator creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The generator creation has been updated.")})
    public ResponseEntity<Void> updateGeneratorCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                              @PathVariable("modificationUuid") UUID modificationUuid,
                                                              @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestBody String createGeneratorAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, createGeneratorAttributes, ModificationType.GENERATOR_CREATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators")
    @Operation(summary = "create a shunt-compensator in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The shunt-compensator has been created")})
    public ResponseEntity<Void> createShuntCompensator(@PathVariable("studyUuid") UUID studyUuid,
                                                             @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestBody String createShuntCompensatorAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createEquipment(studyUuid, createShuntCompensatorAttributes, ModificationType.SHUNT_COMPENSATOR_CREATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/shunt-compensators-creation")
    @Operation(summary = "update a shunt-compensator creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The shunt-compensator creation has been updated.")})
    public ResponseEntity<Void> updateShuntCompensatorCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                                     @PathVariable("modificationUuid") UUID modificationUuid,
                                                                     @PathVariable("nodeUuid") UUID nodeUuid,
                                                              @RequestBody String createShuntCompensatorAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, createShuntCompensatorAttributes, ModificationType.SHUNT_COMPENSATOR_CREATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines")
    @Operation(summary = "create a line in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line has been created")})
    public ResponseEntity<Void> createLine(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String createLineAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createEquipment(studyUuid, createLineAttributes, ModificationType.LINE_CREATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/lines-creation")
    @Operation(summary = "update a line creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line creation has been updated.")})
    public ResponseEntity<Void> updateLineCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("modificationUuid") UUID modificationUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                                     @RequestBody String createLineAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, createLineAttributes, ModificationType.LINE_CREATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers")
    @Operation(summary = "create a two windings transformer in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The two windings transformer has been created")})
    public ResponseEntity<Void> createTwoWindingsTransformer(@PathVariable("studyUuid") UUID studyUuid,
                                                                   @PathVariable("nodeUuid") UUID nodeUuid,
                                                                   @RequestBody String createTwoWindingsTransformerAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createEquipment(studyUuid, createTwoWindingsTransformerAttributes, ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/two-windings-transformers-creation")
    @Operation(summary = "update a two windings transformer creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The two windings transformer creation has been updated.")})
    public ResponseEntity<Void> updateTwoWindingsTransformerCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                                           @PathVariable("modificationUuid") UUID modificationUuid,
                                                                           @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestBody String createTwoWindingsTransformerAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, createTwoWindingsTransformerAttributes, ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations")
    @Operation(summary = "create a substation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation has been created")})
    public ResponseEntity<Void> createSubstation(@PathVariable("studyUuid") UUID studyUuid,
                                                                   @PathVariable("nodeUuid") UUID nodeUuid,
                                                                   @RequestBody String createSubstationAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createEquipment(studyUuid, createSubstationAttributes, ModificationType.SUBSTATION_CREATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/substations-creation")
    @Operation(summary = "update a substation creation in the study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation creation has been updated")})
    public ResponseEntity<Void> updateSubstationCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                               @PathVariable("modificationUuid") UUID modificationUuid,
                                                               @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @RequestBody String createSubstationAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, createSubstationAttributes, ModificationType.SUBSTATION_CREATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels")
    @Operation(summary = "create a voltage level in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level has been created")})
    public ResponseEntity<Void> createVoltageLevel(@PathVariable("studyUuid") UUID studyUuid,
        @PathVariable("nodeUuid") UUID nodeUuid,
        @RequestBody String createVoltageLevelAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createEquipment(studyUuid, createVoltageLevelAttributes, ModificationType.VOLTAGE_LEVEL_CREATION, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/voltage-levels-creation")
    @Operation(summary = "update a voltage level creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level creation has been updated.")})
    public ResponseEntity<Void> updateVoltageLevelCreation(@PathVariable("studyUuid") UUID studyUuid,
        @PathVariable("modificationUuid") UUID modificationUuid,
        @PathVariable("nodeUuid") UUID nodeUuid,
        @RequestBody String createVoltageLevelAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, createVoltageLevelAttributes, ModificationType.VOLTAGE_LEVEL_CREATION, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/line-splits")
    @Operation(summary = "split a line at a voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line was split at a voltage level ok")})
    public ResponseEntity<Void> lineSplitWithVoltageLevel(@PathVariable("studyUuid") UUID studyUuid,
        @PathVariable("nodeUuid") UUID nodeUuid,
        @RequestBody String lineSplitWithVoltageLevelAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.lineSplitWithVoltageLevel(studyUuid, lineSplitWithVoltageLevelAttributes, ModificationType.LINE_SPLIT_WITH_VOLTAGE_LEVEL, nodeUuid, null);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/line-splits")
    @Operation(summary = "update a line split at a voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line split at a voltage level has been updated.")})
    public ResponseEntity<Void> updateLineSplitWithVoltageLevel(@PathVariable("studyUuid") UUID studyUuid,
        @PathVariable("modificationUuid") UUID modificationUuid,
        @PathVariable("nodeUuid") UUID nodeUuid,
        @RequestBody String lineSplitWithVoltageLevelAttributes) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.lineSplitWithVoltageLevel(studyUuid, lineSplitWithVoltageLevelAttributes, ModificationType.LINE_SPLIT_WITH_VOLTAGE_LEVEL, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/line-attach")
    @Operation(summary = "attach a line to a voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line was attached to the voltage level")})
    public ResponseEntity<Void> lineAttachToVoltageLevel(@PathVariable("studyUuid") UUID studyUuid,
                                                               @PathVariable("nodeUuid") UUID nodeUuid,
                                                               @RequestBody String lineAttachToVoltageLevelAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.assertComputationNotRunning(nodeUuid);
        studyService.createEquipment(studyUuid, lineAttachToVoltageLevelAttributes, ModificationType.LINE_ATTACH_TO_VOLTAGE_LEVEL, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/line-attach")
    @Operation(summary = "update a line attach to a voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line attach to a voltage level has been updated.")})
    public ResponseEntity<Void> updateLineAttachToVoltageLevel(@PathVariable("studyUuid") UUID studyUuid,
                                                                      @PathVariable("modificationUuid") UUID modificationUuid,
                                                                      @PathVariable("nodeUuid") UUID nodeUuid,
                                                                      @RequestBody String lineAttachToVoltageLevelAttributes) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.assertComputationNotRunning(nodeUuid);
        studyService.updateEquipmentCreation(studyUuid, lineAttachToVoltageLevelAttributes, ModificationType.LINE_ATTACH_TO_VOLTAGE_LEVEL, nodeUuid, modificationUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos")
    @Operation(summary = "get the load flow information (status and result) on study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The load flow informations"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<LoadFlowInfos> getLoadFlowInfos(@PathVariable("studyUuid") UUID studyUuid,
                                                                @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLoadFlowInfos(studyUuid, nodeUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/build")
    @Operation(summary = "build a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been built"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
                           @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> buildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid);
        studyService.buildNode(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/build/stop")
    @Operation(summary = "stop a node build")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The build has been stopped"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Void> stopBuild(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.stopBuild(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}")
    @Operation(summary = "Activate/Deactivate a modification in a modification group associated with a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification has been activated/deactivated"),
                           @ApiResponse(responseCode = "404", description = "The study/node/modification doesn't exist")})
    public ResponseEntity<Void> changeModificationActiveState(@PathVariable("studyUuid") UUID studyUuid,
                                                                    @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @PathVariable("modificationUuid") UUID modificationUuid,
                                                                    @Parameter(description = "active") @RequestParam("active") boolean active) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.changeModificationActiveState(studyUuid, nodeUuid, modificationUuid, active);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/loadflow-default-provider")
    @Operation(summary = "get load flow default provider value")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the load flow default provider value has been found"))
    public ResponseEntity<String> getDefaultLoadflowProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultLoadflowProviderValue());
    }

    @PostMapping(value = "/studies/{studyUuid}/reindex-all")
    @Operation(summary = "reindex the study")
    @ApiResponse(responseCode = "200", description = "Study reindexed")
    public ResponseEntity<Void> reindexStudy(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        studyService.reindexStudy(studyUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/notification")
    @Operation(summary = "Create study related notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The notification has been sent"),
        @ApiResponse(responseCode = "400", description = "The notification type is unknown")
    })
    public ResponseEntity<Void> notify(@PathVariable("studyUuid") UUID studyUuid,
                                             @RequestParam("type") String notificationType) {
        studyService.notify(notificationType, studyUuid);
        return ResponseEntity.ok().build();
    }
}
