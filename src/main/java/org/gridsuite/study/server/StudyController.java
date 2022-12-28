/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException.Type;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

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
    private final NetworkConversionService networkConversionService;
    private final SecurityAnalysisService securityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final ShortCircuitService shortCircuitService;
    private final CaseService caseService;

    public StudyController(StudyService studyService,
            NetworkService networkStoreService,
            NetworkModificationTreeService networkModificationTreeService,
            SingleLineDiagramService singleLineDiagramService,
            NetworkConversionService networkConversionService,
            SecurityAnalysisService securityAnalysisService,
            SensitivityAnalysisService sensitivityAnalysisService,
            ShortCircuitService shortCircuitService,
            CaseService caseService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkStoreService = networkStoreService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.securityAnalysisService = securityAnalysisService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.shortCircuitService = shortCircuitService;
        this.caseService = caseService;
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

    @GetMapping(value = "/studies/{studyUuid}/case/name")
    @Operation(summary = "Get study case name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study case name"),
                           @ApiResponse(responseCode = "204", description = "The study has no case name attached")})
    public ResponseEntity<String> getStudyCaseName(@PathVariable("studyUuid") UUID studyUuid) {
        String studyCaseName = studyService.getStudyCaseName(studyUuid);
        return StringUtils.isEmpty(studyCaseName) ? ResponseEntity.noContent().build() : ResponseEntity.ok().body(studyCaseName);
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
        @ApiResponse(responseCode = "409", description = "The study already exists or the case doesn't exist")})
    public ResponseEntity<BasicStudyInfos> createStudyFromExistingCase(@PathVariable("caseUuid") UUID caseUuid,
                                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                                             @RequestBody(required = false) Map<String, Object> importParameters,
                                                                             @RequestHeader("userId") String userId) {
        caseService.assertCaseExists(caseUuid);
        BasicStudyInfos createStudy = studyService.createStudy(caseUuid, userId, studyUuid, importParameters);
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

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes", params = {"nodeToCopyUuid", "referenceNodeUuid", "insertMode"})
    @Operation(summary = "duplicate a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node was successfully created"),
        @ApiResponse(responseCode = "403", description = "The node can't be copied above the root node"),
        @ApiResponse(responseCode = "404", description = "The source study or node doesn't exist")})
    public ResponseEntity<Void> duplicateNode(@PathVariable("studyUuid") UUID studyUuid,
                                              @Parameter(description = "The node we want to copy") @RequestParam("nodeToCopyUuid") UUID nodeToCopyUuid,
                                              @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
                                              @Parameter(description = "the position where the node will be pasted relative to the reference node") @RequestParam(name = "insertMode") InsertMode insertMode,
                                              @RequestHeader("userId") String userId) {
        studyService.duplicateStudyNode(studyUuid, nodeToCopyUuid, referenceNodeUuid, insertMode, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes", params = {"nodeToCutUuid", "referenceNodeUuid", "insertMode"})
    @Operation(summary = "cut and paste a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node was successfully created"),
        @ApiResponse(responseCode = "403", description = "The node can't be copied above the root node nor around itself"),
        @ApiResponse(responseCode = "404", description = "The source study or node doesn't exist")})
    public ResponseEntity<Void> cutAndPasteNode(@PathVariable("studyUuid") UUID studyUuid,
                                              @Parameter(description = "The node we want to cut") @RequestParam("nodeToCutUuid") UUID nodeToCutUuid,
                                              @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
                                              @Parameter(description = "the position where the node will be pasted relative to the reference node") @RequestParam(name = "insertMode") InsertMode insertMode,
                                              @RequestHeader("userId") String userId) {
        studyService.moveStudyNode(studyUuid, nodeToCutUuid, referenceNodeUuid, insertMode, userId);
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
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary,
            @Parameter(description = "Sld display mode") @RequestParam(name = "sldDisplayMode", defaultValue = "STATE_VARIABLE") StudyConstants.SldDisplayMode sldDisplayMode,
            @Parameter(description = "language") @RequestParam(name = "language", defaultValue = "en") String language) {
        DiagramParameters diagramParameters = DiagramParameters.builder()
                .useName(useName)
                .labelCentered(centerLabel)
                .diagonalLabel(diagonalLabel)
                .topologicalColoring(topologicalColoring)
                .componentLibrary(componentLibrary)
                .sldDisplayMode(sldDisplayMode)
                .language(language)
                .build();
        byte[] result = studyService.getVoltageLevelSvg(
                studyUuid,
                voltageLevelId,
                diagramParameters,
                nodeUuid);
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
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary,
            @Parameter(description = "Sld display mode") @RequestParam(name = "sldDisplayMode", defaultValue = "STATE_VARIABLE") StudyConstants.SldDisplayMode sldDisplayMode,
            @Parameter(description = "language") @RequestParam(name = "language", defaultValue = "en") String language) {
        DiagramParameters diagramParameters = DiagramParameters.builder()
                .useName(useName)
                .labelCentered(centerLabel)
                .diagonalLabel(diagonalLabel)
                .topologicalColoring(topologicalColoring)
                .componentLibrary(componentLibrary)
                .sldDisplayMode(sldDisplayMode)
                .language(language)
                .build();
        String result = studyService.getVoltageLevelSvgAndMetadata(
                studyUuid,
                voltageLevelId,
                diagramParameters,
                nodeUuid);
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
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getTwoWindingsTransformersMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
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
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getThreeWindingsTransformersMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
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
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getBatteriesMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/dangling-lines")
    @Operation(summary = "Get Network dangling lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of dangling lines data")})
    public ResponseEntity<String> getDanglingLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getDanglingLinesMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines")
    @Operation(summary = "Get Network hvdc lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of hvdc lines data")})
    public ResponseEntity<String> getHvdcLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getHvdcLinesMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lcc-converter-stations")
    @Operation(summary = "Get Network lcc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lcc converter stations data")})
    public ResponseEntity<String> getLccConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLccConverterStationsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/vsc-converter-stations")
    @Operation(summary = "Get Network vsc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of vsc converter stations data")})
    public ResponseEntity<String> getVscConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVscConverterStationsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
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
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getShuntCompensatorsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
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
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStaticVarCompensatorsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
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

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels-equipments")
    @Operation(summary = "Get equipment of voltage levels")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage levels data")})
    public ResponseEntity<String> getVoltageLevelsAndEquipments(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelsAndEquipment(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
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

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationUuid}")
    @Operation(summary = "move network modification before another")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification order is updated")})
    public ResponseEntity<Void> moveModification(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @PathVariable("modificationUuid") UUID modificationUuid,
                                                        @Nullable @Parameter(description = "move before, if no value move to end") @RequestParam(value = "beforeUuid") UUID beforeUuid,
                                                        @RequestHeader("userId") String userId) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.moveModifications(studyUuid, nodeUuid, nodeUuid, List.of(modificationUuid), beforeUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "For a list of network modifications passed in body, copy or cut, then append them to target node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification list has been updated. Modifications in failure are returned.")})
    public ResponseEntity<String> moveOrCopyModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestParam("action") UpdateModificationAction action,
                                                         @Nullable @RequestParam("originNodeUuid") UUID originNodeUuid,
                                                         @RequestBody List<UUID> modificationsToCopyUuidList,
                                                         @RequestHeader("userId") String userId) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        if (originNodeUuid != null) {
            studyService.assertCanModifyNode(studyUuid, originNodeUuid);
        }
        String failureIds;
        switch (action) {
            case COPY:
                failureIds = studyService.duplicateModifications(studyUuid, nodeUuid, modificationsToCopyUuidList, userId);
                break;
            case MOVE:
                failureIds = studyService.moveModifications(studyUuid, nodeUuid, originNodeUuid, modificationsToCopyUuidList, null, userId);
                break;
            default:
                throw new StudyException(Type.UNKNOWN_ACTION_TYPE);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(failureIds);
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

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run")
    @Operation(summary = "run short circuit analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis has started")})
    public ResponseEntity<UUID> runShortCircuit(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        return ResponseEntity.ok().body(studyService.runShortCircuit(studyUuid, nodeUuid));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Void> stopShortCircuitAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        shortCircuitService.stopShortCircuitAnalysis(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result")
    @Operation(summary = "Get a short circuit analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis result"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis has not been found")})
    public ResponseEntity<String> getShortCircuitResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = shortCircuitService.getShortCircuitAnalysisResult(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/status")
    @Operation(summary = "Get the short circuit analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis status"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis status has not been found")})
    public ResponseEntity<String> getShortCircuitAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = shortCircuitService.getShortCircuitAnalysisStatus(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/export-network-formats")
    @Operation(summary = "get the available export format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The available export format")})
    public ResponseEntity<String> getExportFormats() {
        String formatsJson = networkConversionService.getExportFormats();
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
        String result = securityAnalysisService.getSecurityAnalysisResult(nodeUuid, nonNullLimitTypes);
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
            @RequestBody(required = false) LoadFlowParameters lfParameter,
            @RequestHeader("userId") String userId) {
        studyService.setLoadFlowParameters(studyUuid, lfParameter, userId);
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
                                                          @RequestBody(required = false) String provider,
                                                          @RequestHeader("userId") String userId) {
        studyService.updateLoadFlowProvider(studyUuid, provider, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "Get load flow provider for a specified study, empty string means default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow provider is returned")})
    public ResponseEntity<String> getLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowProvider(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/short-circuit-analysis/parameters")
    @Operation(summary = "set short-circuit analysis parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters are set")})
    public ResponseEntity<Void> setShortCircuitParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) ShortCircuitParameters shortCircuitParameters,
            @RequestHeader("userId") String userId) {
        studyService.setShortCircuitParameters(studyUuid, shortCircuitParameters, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/short-circuit-analysis/parameters")
    @Operation(summary = "Get short-circuit analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters")})
    public ResponseEntity<ShortCircuitParameters> getShortCircuitParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getShortCircuitParameters(studyUuid));
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
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary,
            @Parameter(description = "language") @RequestParam(name = "language", defaultValue = "en") String language) {
        DiagramParameters diagramParameters = DiagramParameters.builder()
                .useName(useName)
                .labelCentered(centerLabel)
                .diagonalLabel(diagonalLabel)
                .topologicalColoring(topologicalColoring)
                .componentLibrary(componentLibrary)
                .language(language)
                .build();
        byte[] result = studyService.getSubstationSvg(studyUuid, substationId,
                diagramParameters, substationLayout, nodeUuid);
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
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary,
            @Parameter(description = "language") @RequestParam(name = "language", defaultValue = "en") String language) {
        DiagramParameters diagramParameters = DiagramParameters.builder()
                .useName(useName)
                .labelCentered(centerLabel)
                .diagonalLabel(diagonalLabel)
                .topologicalColoring(topologicalColoring)
                .componentLibrary(componentLibrary)
                .language(language)
                .build();
        String result = studyService.getSubstationSvgAndMetadata(
                studyUuid,
                substationId,
                diagramParameters,
                substationLayout,
                nodeUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-area-diagram")
    @Operation(summary = "get the network area diagram for the given network and voltage levels")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<String> getNeworkAreaDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Voltage levels ids") @RequestParam(name = "voltageLevelsIds") List<String> voltageLevelsIds,
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
        SecurityAnalysisStatus status = securityAnalysisService.getSecurityAnalysisStatus(nodeUuid);
        return status != null ? ResponseEntity.ok().body(status.name()) :
                ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Void> stopSecurityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        securityAnalysisService.stopSecurityAnalysis(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get node report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<ReporterModel>> getNodeReport(@Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "Node only report") @RequestParam(value = "nodeOnlyReport", required = false, defaultValue = "true") boolean nodeOnlyReport) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNodeReport(nodeUuid, nodeOnlyReport));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/report")
    @Operation(summary = "Delete node report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report has been deleted"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteNodeReport(@Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.deleteNodeReport(nodeUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/svg-component-libraries")
    @Operation(summary = "Get a list of the available svg component libraries")
    @ApiResponse(responseCode = "200", description = "The list of the available svg component libraries")
    public ResponseEntity<List<String>> getAvailableSvgComponentLibraries() {
        List<String> libraries = singleLineDiagramService.getAvailableSvgComponentLibraries();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(libraries);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get network modifications from a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications was returned"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<String> getNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        // Return json string because modification dtos are not available here
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(networkModificationTreeService.getNetworkModifications(nodeUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications")
    @Operation(summary = "Create a network modification for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was created"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> createNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader("userId") String userId) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.createNetworkModification(studyUuid, modificationAttributes, nodeUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications/{uuid}")
    @Operation(summary = "Update a modification in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was updated"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Network modification UUID") @PathVariable("uuid") UUID networkModificationUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader("userId") String userId) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.updateNetworkModification(studyUuid, modificationAttributes, nodeUuid, networkModificationUuid, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications")
    @Operation(summary = "Delete network modifications for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications was deleted"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                           @Parameter(description = "Network modification UUIDs") @RequestParam("uuids") List<UUID> networkModificationUuids,
                                                           @RequestHeader("userId") String userId) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.deleteNetworkModifications(studyUuid, nodeUuid, networkModificationUuids, userId);
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
                                                         @Parameter(description = "node is inserted before the given node ID") @RequestParam(name = "mode", required = false, defaultValue = "CHILD") InsertMode insertMode,
                                                         @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationTreeService.createNode(studyUuid, referenceId, node, insertMode, userId));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "Delete node with given id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the nodes have been successfully deleted"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> deleteNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @Parameter(description = "id of child to remove") @PathVariable("id") UUID nodeId,
                                                 @Parameter(description = "deleteChildren") @RequestParam(value = "deleteChildren", defaultValue = "false") boolean deleteChildren,
                                                 @RequestHeader("userId") String userId) {
        studyService.deleteNode(studyUuid, nodeId, deleteChildren, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/tree")
    @Operation(summary = "Get network modification tree for the given study")
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
                                                 @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @RequestHeader("userId") String userId) {
        networkModificationTreeService.updateNode(studyUuid, node, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "get simplified node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "simplified nodes (without children"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<AbstractNode> getNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "node uuid") @PathVariable("id") UUID nodeId) {
        AbstractNode node = networkModificationTreeService.getNode(nodeId);
        return node != null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(node)
                : ResponseEntity.notFound().build();
    }

    @RequestMapping(value = "/studies/{studyUuid}/nodes", method = RequestMethod.HEAD)
    @Operation(summary = "Test if a node name exists")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "node name exists"),
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
        studyService.stopBuild(nodeUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}")
    @Operation(summary = "Activate/Deactivate a modification in a modification group associated with a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification has been activated/deactivated"),
                           @ApiResponse(responseCode = "404", description = "The study/node/modification doesn't exist")})
    public ResponseEntity<Void> changeModificationActiveState(@PathVariable("studyUuid") UUID studyUuid,
                                                              @PathVariable("nodeUuid") UUID nodeUuid,
                                                              @PathVariable("modificationUuid") UUID modificationUuid,
                                                              @Parameter(description = "active") @RequestParam("active") boolean active,
                                                              @RequestHeader("userId") String userId) {
        studyService.assertCanModifyNode(studyUuid, nodeUuid);
        studyService.changeModificationActiveState(studyUuid, nodeUuid, modificationUuid, active, userId);
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

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run")
    @Operation(summary = "run sensitivity analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has started")})
    public ResponseEntity<UUID> runSensitivityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @RequestBody String sensitivityAnalysisInput) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        return ResponseEntity.ok().body(studyService.runSensitivityAnalysis(studyUuid, nodeUuid, sensitivityAnalysisInput));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result")
    @Operation(summary = "Get a sensitivity analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = sensitivityAnalysisService.getSensitivityAnalysisResult(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/status")
    @Operation(summary = "Get the sensitivity analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis status"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis status has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = sensitivityAnalysisService.getSensitivityAnalysisStatus(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/stop")
    @Operation(summary = "stop sensitivity analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has been stopped")})
    public ResponseEntity<Void> stopSensitivityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        sensitivityAnalysisService.stopSensitivityAnalysis(studyUuid, nodeUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/map-equipments")
    @Operation(summary = "Get network map equipments data")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The lists of lines and substations data")})
    public ResponseEntity<String> getMapEquipments(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getMapEquipments(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/mappings")
    @Operation(summary = "Get all mapping of dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All mappings of dynamic simulation"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation mappings"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation mappings has not been found")})
    public ResponseEntity<List<MappingInfos>> getDynamicSimulationMappingNames(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                               @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<MappingInfos> mappings = studyService.getDynamicSimulationMappings(nodeUuid);
        return mappings != null ? ResponseEntity.ok().body(mappings) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/run")
    @Operation(summary = "run dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation has started")})
    public ResponseEntity<UUID> runDynamicSimulation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @Parameter(description = "mappingName") @RequestParam("mappingName") String mappingName,
                                                     @RequestBody(required = false) String parameters) {
        String nonNullParameters = Objects.toString(parameters, "");
        studyService.assertIsNodeNotReadOnly(nodeUuid);

        return ResponseEntity.ok().body(studyService.runDynamicSimulation(studyUuid, nodeUuid, nonNullParameters, mappingName));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeseries")
    @Operation(summary = "Get all time series of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All time series of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation has been done yet"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<String> getDynamicSimulationTimeSeriesResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = studyService.getDynamicSimulationTimeSeries(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeline")
    @Operation(summary = "Get a timeline of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The timeline of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation has been done yet"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<String> getDynamicSimulationTimeLineResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = studyService.getDynamicSimulationTimeLine(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/status")
    @Operation(summary = "Get the status of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The status of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation has been done yet"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<String> getDynamicSimulationStatusResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                   @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = studyService.getDynamicSimulationStatus(nodeUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    enum UpdateModificationAction {
        MOVE, COPY
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
}
