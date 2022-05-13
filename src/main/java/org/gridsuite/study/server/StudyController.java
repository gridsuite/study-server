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
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.beans.PropertyEditorSupport;
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
    private final NetworkService networkStoreService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public StudyController(StudyService studyService, NetworkService networkStoreService, ReportService reportService, NetworkModificationTreeService networkModificationTreeService) {
        this.studyService = studyService;
        this.reportService = reportService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkStoreService = networkStoreService;
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

    @InitBinder
    public void initBinder(WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(EquipmentInfosService.FieldSelector.class,
            new MyEnumConverter<>(EquipmentInfosService.FieldSelector.class));
    }

    @GetMapping(value = "/studies")
    @Operation(summary = "Get all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of studies")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> getStudyList() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudies());
    }

    @GetMapping(value = "/study_creation_requests")
    @Operation(summary = "Get all study creation requests for a user")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of study creation requests")})
    public ResponseEntity<Flux<BasicStudyInfos>> getStudyCreationRequestList() {
        Flux<BasicStudyInfos> studies = studyService.getStudiesCreationRequests();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studies);
    }

    @GetMapping(value = "/studies/metadata")
    @Operation(summary = "Get studies metadata")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of studies metadata")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> getStudyListMetadata(@RequestParam("ids") List<UUID> uuids) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudiesMetadata(uuids));
    }

    @PostMapping(value = "/studies/cases/{caseUuid}")
    @Operation(summary = "create a study from an existing case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The id of the network imported"),
        @ApiResponse(responseCode = "409", description = "The study already exist or the case doesn't exists")})
    public ResponseEntity<Mono<BasicStudyInfos>> createStudyFromExistingCase(@PathVariable("caseUuid") UUID caseUuid,
                                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(caseUuid, userId, studyUuid)
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(studyService.assertCaseExists(caseUuid).then(createStudy));
    }

    @PostMapping(value = "/studies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "create a study and import the case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The id of the network imported"),
        @ApiResponse(responseCode = "409", description = "The study already exist"),
        @ApiResponse(responseCode = "500", description = "The storage is down or a file with the same name already exists")})
    public ResponseEntity<Mono<BasicStudyInfos>> createStudy(@RequestPart("caseFile") FilePart caseFile,
                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(Mono.just(caseFile), userId, studyUuid)
                .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        return ResponseEntity.ok().body(createStudy);
    }

    @GetMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "get a study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The study information"),
        @ApiResponse(responseCode = "404", description = "The study doesn't exist")})
    public ResponseEntity<Mono<StudyInfos>> getStudy(@PathVariable("studyUuid") UUID studyUuid) {
        Mono<StudyInfos> studyMono = studyService.getStudyInfos(studyUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyMono.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
    }

    @DeleteMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "delete the study")
    @ApiResponse(responseCode = "200", description = "Study deleted")
    public ResponseEntity<Mono<Void>> deleteStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                  @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<Mono<byte[]>> getVoltageLevelDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(studyService.getVoltageLevelSvg(studyUuid, voltageLevelId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponse(responseCode = "200", description = "The svg and metadata")
    public ResponseEntity<Mono<String>> getVoltageLevelDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelSvgAndMetadata(studyUuid, voltageLevelId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels")
    @Operation(summary = "get the voltage levels for a given network")
    @ApiResponse(responseCode = "200", description = "The voltage level list of the network")
    public ResponseEntity<Mono<List<VoltageLevelInfos>>> getNetworkVoltageLevels(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevels(studyUuid, nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses")
    @Operation(summary = "get buses the for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The buses list of the network for given voltage level")
    public ResponseEntity<Mono<List<IdentifiableInfos>>> getVoltageLevelBuses(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelBuses(studyUuid, nodeUuid, voltageLevelId));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/busbar-sections")
    @Operation(summary = "get the busbar sections for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The busbar sections list of the network for given voltage level")
    public ResponseEntity<Mono<List<IdentifiableInfos>>> getVoltageLevelBusbarSections(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelBusbarSections(studyUuid, nodeUuid, voltageLevelId));
    }

    @GetMapping(value = "/studies/{studyUuid}/geo-data/lines")
    @Operation(summary = "Get Network lines graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lines graphics")})
    public ResponseEntity<Mono<String>> getLinesGraphics(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkStoreService.getNetworkUuid(studyUuid).flatMap(studyService::getLinesGraphics));
    }

    @GetMapping(value = "/studies/{studyUuid}/geo-data/substations")
    @Operation(summary = "Get Network substations graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substations graphics")})
    public ResponseEntity<Mono<String>> getSubstationsGraphic(
            @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkStoreService.getNetworkUuid(studyUuid).flatMap(studyService::getSubstationsGraphics));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines")
    @Operation(summary = "Get Network lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lines data")})
    public ResponseEntity<Mono<String>> getLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLinesMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines/{lineId}")
    @Operation(summary = "Get specific line description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line data")})
    public ResponseEntity<Mono<String>> getLineMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "line id") @PathVariable("lineId") String lineId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLineMapData(studyUuid, nodeUuid, lineId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations")
    @Operation(summary = "Get Network substations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substations data")})
    public ResponseEntity<Mono<String>> getSubstationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations/{substationId}")
    @Operation(summary = "Get specific substation description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation data")})
    public ResponseEntity<Mono<String>> getSubstationMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "substation Id") @PathVariable("substationId") String substationId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationMapData(studyUuid, nodeUuid, substationId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers")
    @Operation(summary = "Get Network 2 windings transformers description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of 2 windings transformers data")})
    public ResponseEntity<Mono<String>> getTwoWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getTwoWindingsTransformersMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers/{twoWindingsTransformerId}")
    @Operation(summary = "Get specific two windings transformer description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The two windings transformer data")})
    public ResponseEntity<Mono<String>> getTwoWindingsTransformerMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "two windings transformer id") @PathVariable("twoWindingsTransformerId") String twoWindingsTransformerId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getTwoWindingsTransformerMapData(studyUuid, nodeUuid, twoWindingsTransformerId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/3-windings-transformers")
    @Operation(summary = "Get Network 3 windings transformers description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of 3 windings transformers data")})
    public ResponseEntity<Mono<String>> getThreeWindingsTransformersMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getThreeWindingsTransformersMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators")
    @Operation(summary = "Get Network generators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of generators data")})
    public ResponseEntity<Mono<String>> getGeneratorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getGeneratorsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators/{generatorId}")
    @Operation(summary = "Get specific generator description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The generator data")})
    public ResponseEntity<Mono<String>> getGeneratorMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "generator id") @PathVariable("generatorId") String generatorId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getGeneratorMapData(studyUuid, nodeUuid, generatorId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/batteries")
    @Operation(summary = "Get Network batteries description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of batteries data")})
    public ResponseEntity<Mono<String>> getBatteriesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getBatteriesMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/dangling-lines")
    @Operation(summary = "Get Network dangling lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of dangling lines data")})
    public ResponseEntity<Mono<String>> getDanglingLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getDanglingLinesMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines")
    @Operation(summary = "Get Network hvdc lines description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of hvdc lines data")})
    public ResponseEntity<Mono<String>> getHvdcLinesMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getHvdcLinesMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lcc-converter-stations")
    @Operation(summary = "Get Network lcc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of lcc converter stations data")})
    public ResponseEntity<Mono<String>> getLccConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLccConverterStationsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/vsc-converter-stations")
    @Operation(summary = "Get Network vsc converter stations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of vsc converter stations data")})
    public ResponseEntity<Mono<String>> getVscConverterStationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVscConverterStationsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads")
    @Operation(summary = "Get Network loads description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of loads data")})
    public ResponseEntity<Mono<String>> getLoadsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLoadsMapData(studyUuid, nodeUuid, substationsIds, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads/{loadId}")
    @Operation(summary = "Get specific load description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load data")})
    public ResponseEntity<Mono<String>> getLoadMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "load id") @PathVariable("loadId") String loadId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLoadMapData(studyUuid, nodeUuid, loadId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators")
    @Operation(summary = "Get Network shunt compensators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of shunt compensators data")})
    public ResponseEntity<Mono<String>> getShuntCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getShuntCompensatorsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators/{shuntCompensatorId}")
    @Operation(summary = "Get specific shunt compensator description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The shunt compensator data")})
    public ResponseEntity<Mono<String>> getShuntCompensatorMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "shunt compensator id") @PathVariable("shuntCompensatorId") String shuntCompensatorId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getShuntCompensatorMapData(studyUuid, nodeUuid, shuntCompensatorId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/static-var-compensators")
    @Operation(summary = "Get Network static var compensators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of static var compensators data")})
    public ResponseEntity<Mono<String>> getStaticVarCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStaticVarCompensatorsMapData(studyUuid, nodeUuid, substationsIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}")
    @Operation(summary = "Get specific voltage level description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level data")})
    public ResponseEntity<Mono<String>> getVoltageLevelMapData(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltage level id") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "true") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelMapData(studyUuid, nodeUuid, voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/all")
    @Operation(summary = "Get Network equipments description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipments data")})
    public ResponseEntity<Mono<String>> getAllMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getAllMapData(studyUuid, nodeUuid, substationsIds));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}")
    @Operation(summary = "update a switch position")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The switch is updated")})
    public ResponseEntity<Mono<Void>> changeSwitchState(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("switchId") String switchId,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @Parameter(description = "Switch open state") @RequestParam("open") boolean open) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
                .then(studyService.changeSwitchState(studyUuid, switchId, open, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy")
    @Operation(summary = "change an equipment state in the network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment is updated")})
    public ResponseEntity<Mono<Void>> applyGroovyScript(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @RequestBody String groovyScript) {

        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid)).then(studyService.applyGroovyScript(studyUuid, groovyScript, nodeUuid).then()));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationUuid}")
    @Operation(summary = "move network modification before another")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification order is updated")})
    public ResponseEntity<Mono<Void>> moveModification(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @PathVariable("modificationUuid") UUID modificationUuid,
                                                        @Nullable @Parameter(description = "move before, if no value move to end") @RequestParam(value = "beforeUuid") UUID beforeUuid) {

        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
            .then(studyService.reorderModification(studyUuid, nodeUuid, modificationUuid, beforeUuid)).then());
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Change the given line status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Line status changed")})
    public ResponseEntity<Mono<Void>> changeLineStatus(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("lineId") String lineId,
            @RequestBody String status) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid)).then(studyService.changeLineStatus(studyUuid, lineId, status, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run")
    @Operation(summary = "run loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has started")})
    public ResponseEntity<Mono<Void>> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertLoadFlowRunnable(nodeUuid))
                .then(studyService.runLoadFlow(studyUuid, nodeUuid)));
    }

    @GetMapping(value = "/export-network-formats")
    @Operation(summary = "get the available export format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The available export format")})
    public ResponseEntity<Mono<Collection<String>>> getExportFormats() {
        Mono<Collection<String>> formatsMono = studyService.getExportFormats();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(formatsMono);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}")
    @Operation(summary = "export the study's network in the given format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network in the given format")})
    public Mono<ResponseEntity<byte[]>> exportNetwork(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("format") String format) {

        Mono<ExportNetworkInfos> exportNetworkInfosMono = studyService.assertRootNodeOrBuiltNode(studyUuid, nodeUuid).then(studyService.exportNetwork(studyUuid, nodeUuid, format));
        return exportNetworkInfosMono.map(exportNetworkInfos -> {
            HttpHeaders header = new HttpHeaders();
            header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getFileName(), StandardCharsets.UTF_8).build());
            return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
        });
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run")
    @Operation(summary = "run security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has started")})
    public ResponseEntity<Mono<UUID>> runSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames,
                                                          @RequestBody(required = false) String parameters) {
        List<String> nonNullcontingencyListNames = contingencyListNames != null ? contingencyListNames : Collections.emptyList();
        String nonNullParameters = Objects.toString(parameters, "");
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.runSecurityAnalysis(studyUuid, nonNullcontingencyListNames, nonNullParameters, nodeUuid)));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result")
    @Operation(summary = "Get a security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                  @Parameter(description = "Limit types") @RequestParam(name = "limitType", required = false) List<String> limitTypes) {
        List<String> nonNullLimitTypes = limitTypes != null ? limitTypes : Collections.emptyList();
        return studyService.getSecurityAnalysisResult(nodeUuid, nonNullLimitTypes)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/contingency-count")
    @Operation(summary = "Get contingency count for a list of contingency list on a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The contingency count")})
    public Mono<ResponseEntity<Integer>> getContingencyCount(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames) {
        List<String> nonNullContingencyListNames = contingencyListNames != null ? contingencyListNames : Collections.emptyList();
        return studyService.getContingencyCount(studyUuid, nonNullContingencyListNames, nodeUuid)
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

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<Mono<byte[]>> getSubstationDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(studyService.getSubstationSvg(studyUuid, substationId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), substationLayout, nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponse(responseCode = "200", description = "The svg and metadata")
    public ResponseEntity<Mono<String>> getSubstationDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @Parameter(description = "useName") @RequestParam(name = "useName", defaultValue = "false") boolean useName,
            @Parameter(description = "centerLabel") @RequestParam(name = "centerLabel", defaultValue = "false") boolean centerLabel,
            @Parameter(description = "diagonalLabel") @RequestParam(name = "diagonalLabel", defaultValue = "false") boolean diagonalLabel,
            @Parameter(description = "topologicalColoring") @RequestParam(name = "topologicalColoring", defaultValue = "false") boolean topologicalColoring,
            @Parameter(description = "substationLayout") @RequestParam(name = "substationLayout", defaultValue = "horizontal") String substationLayout,
            @Parameter(description = "component library name") @RequestParam(name = "componentLibrary", required = false) String componentLibrary) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationSvgAndMetadata(studyUuid, substationId,
            new DiagramParameters(useName, centerLabel, diagonalLabel, topologicalColoring, componentLibrary), substationLayout, nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/status")
    @Operation(summary = "Get the security analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis status"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis status has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return studyService.getSecurityAnalysisStatus(nodeUuid)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Mono<Void>> stopSecurityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().body(studyService.stopSecurityAnalysis(studyUuid, nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get study report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report for study"), @ApiResponse(responseCode = "404", description = "The study not found")})
    public ResponseEntity<Mono<ReporterModel>> getReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkStoreService.getNetworkUuid(studyUuid).flatMap(reportService::getReport));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/report")
    @Operation(summary = "Delete study report")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report for study deleted"), @ApiResponse(responseCode = "404", description = "The study not found")})
    public ResponseEntity<Mono<Void>> deleteReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkStoreService.getNetworkUuid(studyUuid).flatMap(reportService::deleteReport));
    }

    @GetMapping(value = "/svg-component-libraries")
    @Operation(summary = "Get a list of the available svg component libraries")
    @ApiResponse(responseCode = "200", description = "The list of the available svg component libraries")
    public ResponseEntity<Mono<List<String>>> getAvailableSvgComponentLibraries() {
        Mono<List<String>> libraries = studyService.getAvailableSvgComponentLibraries();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(libraries);
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads")
    @Operation(summary = "create a load in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load has been created")})
    public ResponseEntity<Mono<Void>> createLoad(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String createLoadAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
                .then(studyService.createEquipment(studyUuid, createLoadAttributes, ModificationType.LOAD_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads")
    @Operation(summary = "modify a load in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load has been modified")})
    public ResponseEntity<Mono<Void>> modifyLoad(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String modifyLoadAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
                .then(studyService.modifyEquipment(studyUuid, modifyLoadAttributes, ModificationType.LOAD_MODIFICATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-creation")
    @Operation(summary = "update a load creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load creation has been updated")})
    public ResponseEntity<Mono<Void>> updateLoadCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("modificationUuid") UUID modificationUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestBody String createLoadAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentCreation(studyUuid, createLoadAttributes, ModificationType.LOAD_CREATION, nodeUuid, modificationUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-modification")
    @Operation(summary = "update a load modification in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load modification has been updated")})
    public ResponseEntity<Mono<Void>> updateLoadModification(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("modificationUuid") UUID modificationUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestBody String modifyLoadAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentModification(studyUuid, modifyLoadAttributes, ModificationType.LOAD_MODIFICATION, nodeUuid, modificationUuid)));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification")
    @Operation(summary = "delete network modifications")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications have been deleted")})
    public ResponseEntity<Mono<Void>> deleteModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                          @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestParam(value = "modificationsUuids") List<UUID> modificationsUuids) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
            .then(studyService.deleteModifications(studyUuid, nodeUuid, modificationsUuids)));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search studies in elasticsearch")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of studies found")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> searchStudies(@Parameter(description = "Lucene query") @RequestParam(value = "q") String query) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.searchStudies(query));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search equipments in elasticsearch")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of equipments found"),
        @ApiResponse(responseCode = "404", description = "The study not found"),
        @ApiResponse(responseCode = "400", description = "The fieLd selector is unknown")
    })
    public ResponseEntity<Flux<EquipmentInfos>> searchEquipments(
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
    public ResponseEntity<Mono<AbstractNode>> createNode(@RequestBody AbstractNode node,
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
    public ResponseEntity<Mono<Void>> deleteNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @Parameter(description = "id of child to remove") @PathVariable("id") UUID nodeId,
                                                 @Parameter(description = "deleteChildren") @RequestParam(value = "deleteChildren", defaultValue = "false") boolean deleteChildren) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.deleteNode(studyUuid, nodeId, deleteChildren));
    }

    @GetMapping(value = "/studies/{studyUuid}/tree")
    @Operation(summary = "get network modification tree for the given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "network modification tree"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public Mono<ResponseEntity<RootNode>> getNetworkModificationTree(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return networkModificationTreeService.getStudyTree(studyUuid)
            .map(result -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/studies/{studyUuid}/tree/nodes")
    @Operation(summary = "update node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the node has been updated"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Mono<Void>> updateNode(@RequestBody AbstractNode node,
                                                 @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(networkModificationTreeService.updateNode(studyUuid, node));
    }

    @GetMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "get simplified node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "simplified nodes (without children"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public Mono<ResponseEntity<AbstractNode>> getNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "node uuid") @PathVariable("id") UUID nodeId) {
        return networkModificationTreeService.getSimpleNode(studyUuid, nodeId)
            .map(result -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}")
    @Operation(summary = "Delete equipment in study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment was deleted"), @ApiResponse(responseCode = "404", description = "The study not found")})
    public ResponseEntity<Mono<Void>> deleteEquipment(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @Parameter(description = "Equipment type") @PathVariable("equipmentType") String equipmentType,
                                                      @Parameter(description = "Equipment id") @PathVariable("equipmentId") String equipmentId) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid)).then(studyService.deleteEquipment(studyUuid, equipmentType, equipmentId, nodeUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators")
    @Operation(summary = "create a generator in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The generator has been created")})
    public ResponseEntity<Mono<Void>> createGenerator(@PathVariable("studyUuid") UUID studyUuid,
                                                      @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @RequestBody String createGeneratorAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
            .then(studyService.createEquipment(studyUuid, createGeneratorAttributes, ModificationType.GENERATOR_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/generators-creation")
    @Operation(summary = "update a generator creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The generator creation has been updated.")})
    public ResponseEntity<Mono<Void>> updateGeneratorCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                              @PathVariable("modificationUuid") UUID modificationUuid,
                                                              @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestBody String createGeneratorAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentCreation(studyUuid, createGeneratorAttributes, ModificationType.GENERATOR_CREATION, nodeUuid, modificationUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators")
    @Operation(summary = "create a shunt-compensator in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The shunt-compensator has been created")})
    public ResponseEntity<Mono<Void>> createShuntCompensator(@PathVariable("studyUuid") UUID studyUuid,
                                                             @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestBody String createShuntCompensatorAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
            .then(studyService.createEquipment(studyUuid, createShuntCompensatorAttributes, ModificationType.SHUNT_COMPENSATOR_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/shunt-compensators-creation")
    @Operation(summary = "update a shunt-compensator creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The shunt-compensator creation has been updated.")})
    public ResponseEntity<Mono<Void>> updateShuntCompensatorCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                                     @PathVariable("modificationUuid") UUID modificationUuid,
                                                                     @PathVariable("nodeUuid") UUID nodeUuid,
                                                              @RequestBody String createShuntCompensatorAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentCreation(studyUuid, createShuntCompensatorAttributes, ModificationType.SHUNT_COMPENSATOR_CREATION, nodeUuid, modificationUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines")
    @Operation(summary = "create a line in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line has been created")})
    public ResponseEntity<Mono<Void>> createLine(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String createLineAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
            .then(studyService.createEquipment(studyUuid, createLineAttributes, ModificationType.LINE_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/lines-creation")
    @Operation(summary = "update a line creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line creation has been updated.")})
    public ResponseEntity<Mono<Void>> updateLineCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("modificationUuid") UUID modificationUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                                     @RequestBody String createLineAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentCreation(studyUuid, createLineAttributes, ModificationType.LINE_CREATION, nodeUuid, modificationUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers")
    @Operation(summary = "create a two windings transformer in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The two windings transformer has been created")})
    public ResponseEntity<Mono<Void>> createTwoWindingsTransformer(@PathVariable("studyUuid") UUID studyUuid,
                                                                   @PathVariable("nodeUuid") UUID nodeUuid,
                                                                   @RequestBody String createTwoWindingsTransformerAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
                .then(studyService.createEquipment(studyUuid, createTwoWindingsTransformerAttributes, ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/two-windings-transformers-creation")
    @Operation(summary = "update a two windings transformer creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The two windings transformer creation has been updated.")})
    public ResponseEntity<Mono<Void>> updateTwoWindingsTransformerCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                                           @PathVariable("modificationUuid") UUID modificationUuid,
                                                                           @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestBody String createTwoWindingsTransformerAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentCreation(studyUuid, createTwoWindingsTransformerAttributes, ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, nodeUuid, modificationUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations")
    @Operation(summary = "create a substation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation has been created")})
    public ResponseEntity<Mono<Void>> createSubstation(@PathVariable("studyUuid") UUID studyUuid,
                                                                   @PathVariable("nodeUuid") UUID nodeUuid,
                                                                   @RequestBody String createSubstationAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
                .then(studyService.createEquipment(studyUuid, createSubstationAttributes, ModificationType.SUBSTATION_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/substations-creation")
    @Operation(summary = "update a substation creation in the study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation creation has been updated")})
    public ResponseEntity<Mono<Void>> updateSubstationCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                               @PathVariable("modificationUuid") UUID modificationUuid,
                                                               @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @RequestBody String createSubstationAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentCreation(studyUuid, createSubstationAttributes, ModificationType.SUBSTATION_CREATION, nodeUuid, modificationUuid)));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels")
    @Operation(summary = "create a voltage level in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level has been created")})
    public ResponseEntity<Mono<Void>> createVoltageLevel(@PathVariable("studyUuid") UUID studyUuid,
        @PathVariable("nodeUuid") UUID nodeUuid,
        @RequestBody String createVoltageLevelAttributes) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
            .then(studyService.createEquipment(studyUuid, createVoltageLevelAttributes, ModificationType.VOLTAGE_LEVEL_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/voltage-levels-creation")
    @Operation(summary = "update a voltage level creation in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level creation has been updated.")})
    public ResponseEntity<Mono<Void>> updateVoltageLevelCreation(@PathVariable("studyUuid") UUID studyUuid,
                                                                 @PathVariable("modificationUuid") UUID modificationUuid,
                                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                                           @RequestBody String createVoltageLevelAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.updateEquipmentCreation(studyUuid, createVoltageLevelAttributes, ModificationType.VOLTAGE_LEVEL_CREATION, nodeUuid, modificationUuid)));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos")
    @Operation(summary = "get the load flow information (status and result) on study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The load flow informations"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Mono<LoadFlowInfos>> getLoadFlowInfos(@PathVariable("studyUuid") UUID studyUuid,
                                                                @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLoadFlowInfos(studyUuid, nodeUuid)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/build")
    @Operation(summary = "build a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been built"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
                           @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Mono<Void>> buildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid).then(studyService.buildNode(studyUuid, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/build/stop")
    @Operation(summary = "stop a node build")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The build has been stopped"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Mono<Void>> stopBuild(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().body(studyService.stopBuild(studyUuid, nodeUuid));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}")
    @Operation(summary = "Activate/Deactivate a modification in a modification group associated with a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification has been activated/deactivated"),
                           @ApiResponse(responseCode = "404", description = "The study/node/modification doesn't exist")})
    public ResponseEntity<Mono<Void>> changeModificationActiveState(@PathVariable("studyUuid") UUID studyUuid,
                                                                    @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @PathVariable("modificationUuid") UUID modificationUuid,
                                                                    @Parameter(description = "active") @RequestParam("active") boolean active) {
        return ResponseEntity.ok().body(studyService.assertCanModifyNode(nodeUuid).then(studyService.assertComputationNotRunning(nodeUuid))
            .then(studyService.changeModificationActiveState(studyUuid, nodeUuid, modificationUuid, active)));
    }

    @GetMapping(value = "/loadflow-default-provider")
    @Operation(summary = "get load flow default provider value")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the load flow default provider value has been found"))
    public ResponseEntity<Mono<String>> getDefaultLoadflowProvider() {
        return ResponseEntity.ok().body(Mono.fromCallable(studyService::getDefaultLoadflowProviderValue));
    }

    @PostMapping(value = "/studies/{studyUuid}/reindex-all")
    @Operation(summary = "reindex the study")
    @ApiResponse(responseCode = "200", description = "Study reindexed")
    public ResponseEntity<Mono<Void>> reindexStudy(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.reindexStudy(studyUuid));
    }
}
