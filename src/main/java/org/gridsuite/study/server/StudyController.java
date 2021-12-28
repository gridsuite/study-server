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

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final NetworkStoreService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public StudyController(StudyService studyService, NetworkStoreService networkStoreService, NetworkModificationService networkModificationService, ReportService reportService, NetworkModificationTreeService networkModificationTreeService) {
        this.studyService = studyService;
        this.reportService = reportService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkStoreService = networkStoreService;
        this.networkModificationService = networkModificationService;
    }

    static class MyEnumConverter<E extends Enum<E>> extends PropertyEditorSupport {
        private final Class<E> enumClass;

        public MyEnumConverter(Class<E> enumClass) {
            this.enumClass = enumClass;
        }

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
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> getStudyListMetadata(@RequestParam("id") List<UUID> uuids) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyListMetadata(uuids));
    }

    @PostMapping(value = "/studies/cases/{caseUuid}")
    @Operation(summary = "create a study from an existing case")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The id of the network imported"),
            @ApiResponse(responseCode = "409", description = "The study already exist or the case doesn't exists")})
    public ResponseEntity<Mono<BasicStudyInfos>> createStudyFromExistingCase(@PathVariable("caseUuid") UUID caseUuid,
                                                                             @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                                             @RequestParam("isPrivate") Boolean isPrivate,
                                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(caseUuid, userId, isPrivate, studyUuid)
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
                                                             @RequestParam("isPrivate") Boolean isPrivate,
                                                             @RequestHeader("userId") String userId) {
        Mono<BasicStudyInfos> createStudy = studyService.createStudy(Mono.just(caseFile), userId, isPrivate, studyUuid)
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

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations")
    @Operation(summary = "Get Network substations description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substations data")})
    public ResponseEntity<Mono<String>> getSubstationsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationsMapData(studyUuid, nodeUuid, substationsIds));
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
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLoadsMapData(studyUuid, nodeUuid, substationsIds));
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

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/static-var-compensators")
    @Operation(summary = "Get Network static var compensators description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of static var compensators data")})
    public ResponseEntity<Mono<String>> getStaticVarCompensatorsMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStaticVarCompensatorsMapData(studyUuid, nodeUuid, substationsIds));
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
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.changeSwitchState(studyUuid, switchId, open, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy")
    @Operation(summary = "change an equipment state in the network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment is updated")})
    public ResponseEntity<Mono<Void>> applyGroovyScript(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @RequestBody String groovyScript) {

        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid).then(studyService.applyGroovyScript(studyUuid, groovyScript, nodeUuid).then()));
    }

    @GetMapping(value = "/studies/{groupUuid}/network/modifications")
    @Operation(summary = "Get all network modifications")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of network modifications")})
    public ResponseEntity<Flux<ModificationInfos>> getModifications(@PathVariable("groupUuid") UUID groupUuid) {
        return ResponseEntity.ok().body(networkModificationService.getModifications(groupUuid));
    }

    @DeleteMapping(value = "/studies/{groupUuid}/network/modifications")
    @Operation(summary = "Delete all network modifications")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Network modifications deleted")})
    public ResponseEntity<Mono<Void>> deleteModifications(@PathVariable("groupUuid") UUID groupUuid) {
        return ResponseEntity.ok().body(networkModificationService.deleteModifications(groupUuid));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Change the given line status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Line status changed")})
    public ResponseEntity<Mono<Void>> changeLineStatus(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("lineId") String lineId,
            @RequestBody String status) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid).then(studyService.changeLineStatus(studyUuid, lineId, status, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run")
    @Operation(summary = "run loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has started")})
    public ResponseEntity<Mono<Void>> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().body(studyService.assertLoadFlowRunnable(nodeUuid)
                .then(studyService.runLoadFlow(studyUuid, nodeUuid)));
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

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run")
    @Operation(summary = "run security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has started")})
    public ResponseEntity<Mono<UUID>> runSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames,
                                                          @RequestBody(required = false) String parameters) {
        List<String> nonNullcontingencyListNames = contingencyListNames != null ? contingencyListNames : Collections.emptyList();
        String nonNullParameters = Objects.toString(parameters, "");
        return ResponseEntity.ok().body(studyService.runSecurityAnalysis(studyUuid, nonNullcontingencyListNames, nonNullParameters, nodeUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result")
    @Operation(summary = "Get a security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result"),
            @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                  @Parameter(description = "Limit types") @RequestParam(name = "limitType", required = false) List<String> limitTypes) {
        List<String> nonNullLimitTypes = limitTypes != null ? limitTypes : Collections.emptyList();
        return studyService.getSecurityAnalysisResult(nodeUuid, nonNullLimitTypes)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.notFound().build());
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
            @ApiResponse(responseCode = "404", description = "The security analysis status has not been found")})
    public Mono<ResponseEntity<String>> getSecurityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return studyService.getSecurityAnalysisStatus(nodeUuid)
                .map(result -> ResponseEntity.ok().body(result))
                .defaultIfEmpty(ResponseEntity.notFound().build());
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

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads")
    @Operation(summary = "create a load in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load has been created")})
    public ResponseEntity<Mono<Void>> createLoad(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String createLoadAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
            .then(studyService.createEquipment(studyUuid, createLoadAttributes, ModificationType.LOAD_CREATION, nodeUuid)));
    }

    @GetMapping(value = "/studies/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search studies in elasticsearch")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of studies found")})
    public ResponseEntity<Flux<CreatedStudyBasicInfos>> searchStudies(@Parameter(description = "Lucene query") @RequestParam(value = "q") String query) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.searchStudies(query));
    }

    @GetMapping(value = "/studies/{studyUuid}/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search equipments in elasticsearch")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of equipments found"),
        @ApiResponse(responseCode = "404", description = "The study not found"),
        @ApiResponse(responseCode = "400", description = "The fieLd selector is unknown")
    })
    public ResponseEntity<Flux<EquipmentInfos>> searchEquipments(
        @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "User input") @RequestParam(value = "userInput") String userInput,
        @Parameter(description = "What against to match") @RequestParam(value = "fieldSelector") EquipmentInfosService.FieldSelector fieldSelector) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(studyService.searchEquipments(studyUuid, userInput, fieldSelector));
    }

    @PostMapping(value = "/tree/nodes/{id}")
    @Operation(summary = "Create a node as before / after the given node ID")
    @ApiResponse(responseCode = "200", description = "The node has been added")
    public ResponseEntity<Mono<AbstractNode>> createNode(@RequestBody AbstractNode node,
                                                         @Parameter(description = "parent id of the node created") @PathVariable(name = "id") UUID referenceId,
                                                         @Parameter(description = "node is inserted before the given node ID") @RequestParam(name = "mode", required = false, defaultValue = "CHILD") InsertMode insertMode) {
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

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}")
    @Operation(summary = "Delete equipment in study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The equipment was deleted"), @ApiResponse(responseCode = "404", description = "The study not found")})
    public ResponseEntity<Mono<Void>> deleteEquipment(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @Parameter(description = "Equipment type") @PathVariable("equipmentType") String equipmentType,
                                                      @Parameter(description = "Equipment id") @PathVariable("equipmentId") String equipmentId) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid).then(studyService.deleteEquipment(studyUuid, equipmentType, equipmentId, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators")
    @Operation(summary = "create a generator in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The generator has been created")})
    public ResponseEntity<Mono<Void>> createGenerator(@PathVariable("studyUuid") UUID studyUuid,
                                                      @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @RequestBody String createGeneratorAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
            .then(studyService.createEquipment(studyUuid, createGeneratorAttributes, ModificationType.GENERATOR_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines")
    @Operation(summary = "create a line in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The line has been created")})
    public ResponseEntity<Mono<Void>> createLine(@PathVariable("studyUuid") UUID studyUuid,
                                                 @PathVariable("nodeUuid") UUID nodeUuid,
                                                 @RequestBody String createLineAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
            .then(studyService.createEquipment(studyUuid, createLineAttributes, ModificationType.LINE_CREATION, nodeUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformer")
    @Operation(summary = "create a two windings transformer in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The two windings transformer has been created")})
    public ResponseEntity<Mono<Void>> createTwoWindingsTransformer(@PathVariable("studyUuid") UUID studyUuid,
                                                                   @PathVariable("nodeUuid") UUID nodeUuid,
                                                                   @RequestBody String createTwoWindingsTransformerAttributes) {
        return ResponseEntity.ok().body(studyService.assertComputationNotRunning(nodeUuid)
                .then(studyService.createEquipment(studyUuid, createTwoWindingsTransformerAttributes, ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, nodeUuid)));
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
}
