/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.timeseries.DoubleTimeSeries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException.Type;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyParametersInfos;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityFactorsIdsByGroup;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.exception.PartialResultException;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
import org.gridsuite.study.server.service.shortcircuit.FaultResultsMode;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Nullable;
import java.beans.PropertyEditorSupport;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;

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
    private final NonEvacuatedEnergyService nonEvacuatedEnergyService;
    private final ShortCircuitService shortCircuitService;
    private final VoltageInitService voltageInitService;
    private final LoadFlowService loadflowService;
    private final CaseService caseService;
    private final RemoteServicesInspector remoteServicesInspector;
    private final StateEstimationService stateEstimationService;
    private final RootNetworkService rootNetworkService;

    public StudyController(StudyService studyService,
                           NetworkService networkStoreService,
                           NetworkModificationTreeService networkModificationTreeService,
                           SingleLineDiagramService singleLineDiagramService,
                           NetworkConversionService networkConversionService,
                           SecurityAnalysisService securityAnalysisService,
                           SensitivityAnalysisService sensitivityAnalysisService,
                           NonEvacuatedEnergyService nonEvacuatedEnergyService,
                           ShortCircuitService shortCircuitService,
                           VoltageInitService voltageInitService,
                           LoadFlowService loadflowService,
                           CaseService caseService,
                           RemoteServicesInspector remoteServicesInspector,
                           StateEstimationService stateEstimationService, RootNetworkService rootNetworkService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkStoreService = networkStoreService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.securityAnalysisService = securityAnalysisService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.nonEvacuatedEnergyService = nonEvacuatedEnergyService;
        this.shortCircuitService = shortCircuitService;
        this.voltageInitService = voltageInitService;
        this.loadflowService = loadflowService;
        this.caseService = caseService;
        this.remoteServicesInspector = remoteServicesInspector;
        this.stateEstimationService = stateEstimationService;
        this.rootNetworkService = rootNetworkService;
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
        String studyCaseName = rootNetworkService.getCaseName(studyService.getStudyFirstRootNetworkUuid(studyUuid));
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
    public ResponseEntity<BasicStudyInfos> createStudy(@PathVariable("caseUuid") UUID caseUuid,
                                                       @RequestParam(value = CASE_FORMAT) String caseFormat,
                                                       @RequestParam(required = false, value = "studyUuid") UUID studyUuid,
                                                       @RequestParam(required = false, value = "duplicateCase", defaultValue = "false") Boolean duplicateCase,
                                                       @RequestBody(required = false) Map<String, Object> importParameters,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        caseService.assertCaseExists(caseUuid);
        BasicStudyInfos createStudy = studyService.createStudy(caseUuid, userId, studyUuid, importParameters, duplicateCase, caseFormat);
        return ResponseEntity.ok().body(createStudy);
    }

    @PostMapping(value = "/studies", params = "duplicateFrom")
    @Operation(summary = "create a study from an existing one")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The study was successfully created"),
        @ApiResponse(responseCode = "404", description = "The source study doesn't exist")})
    public ResponseEntity<UUID> duplicateStudy(@RequestParam("duplicateFrom") UUID studyId,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        UUID newStudyId = studyService.duplicateStudy(studyId, userId);
        return newStudyId != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(newStudyId) :
                ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "get a study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The study information"),
        @ApiResponse(responseCode = "404", description = "The study doesn't exist")})
    public ResponseEntity<CreatedStudyBasicInfos> getStudy(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyInfos(studyUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid)));
    }

    @DeleteMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "delete the study")
    @ApiResponse(responseCode = "200", description = "Study deleted")
    public ResponseEntity<Void> deleteStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                  @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks")
    @Operation(summary = "Create root network for study")
    @ApiResponse(responseCode = "200", description = "Root network created")
    public ResponseEntity<RootNetworkCreationRequestInfos> createRootNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                                              @RequestParam(value = CASE_UUID) UUID caseUuid,
                                                                              @RequestParam(value = CASE_FORMAT) String caseFormat,
                                                                              @RequestBody(required = false) Map<String, Object> importParameters,
                                                                              @RequestHeader(HEADER_USER_ID) String userId) {
        return ResponseEntity.ok().body(studyService.createRootNetwork(studyUuid, caseUuid, caseFormat, importParameters, userId));
    }

    @PostMapping(value = "/studies/{targetStudyUuid}/tree/nodes", params = {"nodeToCopyUuid", "referenceNodeUuid", "insertMode"})
    @Operation(summary = "duplicate a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node was successfully created"),
        @ApiResponse(responseCode = "403", description = "The node can't be copied above the root node"),
        @ApiResponse(responseCode = "404", description = "The source study or node doesn't exist")})
    public ResponseEntity<Void> duplicateNode(@Parameter(description = "The study where we want to copy the node") @PathVariable("targetStudyUuid") UUID targetStudyUuid,
                                              @Parameter(description = "The copied node original study") @RequestParam(value = "sourceStudyUuid", required = false) UUID sourceStudyUuid,
                                              @Parameter(description = "The node we want to copy") @RequestParam("nodeToCopyUuid") UUID nodeToCopyUuid,
                                              @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
                                              @Parameter(description = "the position where the node will be pasted relative to the reference node") @RequestParam(name = "insertMode") InsertMode insertMode,
                                              @RequestHeader(HEADER_USER_ID) String userId) {
        //if the source study is not set we assume it's the same as the target study
        studyService.duplicateStudyNode(sourceStudyUuid == null ? targetStudyUuid : sourceStudyUuid, targetStudyUuid, nodeToCopyUuid, referenceNodeUuid, insertMode, userId);
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
                                              @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.moveStudyNode(studyUuid, nodeToCutUuid, rootNetworkUuid, referenceNodeUuid, insertMode, userId);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/studies/{studyUuid}/network", method = RequestMethod.HEAD)
    @Operation(summary = "check study root network existence")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The network does exist"),
        @ApiResponse(responseCode = "204", description = "The network doesn't exist")})
    public ResponseEntity<Void> checkNetworkExistence(@PathVariable("studyUuid") UUID studyUuid) {
        UUID networkUUID = rootNetworkService.getNetworkUuid(studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return networkStoreService.doesNetworkExist(networkUUID)
            ? ResponseEntity.ok().build()
            : ResponseEntity.noContent().build();

    }

    @PostMapping(value = "/studies/{studyUuid}/network", params = {"caseUuid"})
    @Operation(summary = "recreate study network of a study from an existing case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Study network recreation has started"),
        @ApiResponse(responseCode = "424", description = "The case doesn't exist")})
    public ResponseEntity<BasicStudyInfos> recreateStudyNetworkFromCase(@PathVariable("studyUuid") UUID studyUuid,
                                                                 @RequestBody(required = false) Map<String, Object> importParameters,
                                                                 @RequestParam(value = "caseUuid") UUID caseUuid,
                                                                 @Parameter(description = "case format") @RequestParam(name = "caseFormat", required = false) String caseFormat,
                                                                 @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.recreateStudyRootNetwork(caseUuid, userId, studyUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), caseFormat, importParameters);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/network")
    @Operation(summary = "recreate study network of a study from its case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Study network recreation has started"),
        @ApiResponse(responseCode = "424", description = "The study's case doesn't exist")})
    public ResponseEntity<BasicStudyInfos> recreateStudyNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                                @RequestHeader(HEADER_USER_ID) String userId,
                                                                @Parameter(description = "case format") @RequestParam(name = "caseFormat", required = false) String caseFormat
    ) {
        studyService.recreateStudyRootNetwork(userId, studyUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), caseFormat);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/indexation/status")
    @Operation(summary = "check study indexation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The study indexation status"),
        @ApiResponse(responseCode = "204", description = "The study indexation status doesn't exist"),
        @ApiResponse(responseCode = "404", description = "The study or network doesn't exist")})
    public ResponseEntity<String> checkStudyIndexationStatus(@PathVariable("studyUuid") UUID studyUuid) {
        String result = studyService.getStudyIndexationStatus(studyUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid)).name();
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/subtrees", params = {"subtreeToCutParentNodeUuid", "referenceNodeUuid"})
    @Operation(summary = "cut and paste a subtree")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The subtree was successfully created"),
        @ApiResponse(responseCode = "403", description = "The subtree can't be copied above the root node nor around itself"),
        @ApiResponse(responseCode = "404", description = "The source study or subtree doesn't exist")})
    public ResponseEntity<Void> cutAndPasteNodeSubtree(@PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "The parent node of the subtree we want to cut") @RequestParam("subtreeToCutParentNodeUuid") UUID subtreeToCutParentNodeUuid,
                                                @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
                                                @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.moveStudySubtree(studyUuid, subtreeToCutParentNodeUuid, rootNetworkUuid, referenceNodeUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/subtrees", params = {"subtreeToCopyParentNodeUuid", "referenceNodeUuid"})
    @Operation(summary = "duplicate a subtree")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The subtree was successfully created"),
        @ApiResponse(responseCode = "403", description = "The subtree can't be copied above the root node nor around itself"),
        @ApiResponse(responseCode = "404", description = "The source study or subtree doesn't exist")})
    public ResponseEntity<Void> duplicateSubtree(@Parameter(description = "The study where we want to copy the node") @PathVariable("studyUuid") UUID targetStudyUuid,
                                                 @Parameter(description = "The copied node original study") @RequestParam(value = "sourceStudyUuid") UUID sourceStudyUuid,
                                                       @Parameter(description = "The parent node of the subtree we want to cut") @RequestParam("subtreeToCopyParentNodeUuid") UUID subtreeToCopyParentNodeUuid,
                                                       @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.duplicateStudySubtree(sourceStudyUuid, targetStudyUuid, subtreeToCopyParentNodeUuid, referenceNodeUuid, userId);
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
                voltageLevelId,
                diagramParameters,
                nodeUuid,
                studyService.getStudyFirstRootNetworkUuid(studyUuid));
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
                voltageLevelId,
                diagramParameters,
                nodeUuid,
                studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses-or-busbar-sections")
    @Operation(summary = "get buses the for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The buses list of the network for given voltage level")
    public ResponseEntity<List<IdentifiableInfos>> getVoltageLevelBusesOrBusbarSections(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelBusesOrBusbarSections(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/substation-id")
    @Operation(summary = "get the substation ID for a given network and a given voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation Id for a voltageLevel")})
    public ResponseEntity<String> getVoltageLevelSubstationId(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltageLevelId") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().body(studyService.getVoltageLevelSubstationId(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcId}/shunt-compensators")
    @Operation(summary = "For a given hvdc line, get its related Shunt compensators in case of LCC converter station")
    @ApiResponse(responseCode = "200", description = "Hvdc line type and its shunt compensators on each side")
    public ResponseEntity<String> getHvdcLineShuntCompensators(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("hvdcId") String hvdcId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        String hvdcInfos = studyService.getHvdcLineShuntCompensators(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), inUpstreamBuiltParentNode, hvdcId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(hvdcInfos);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/lines")
    @Operation(summary = "Get Network lines graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of line graphics with the given ids, all otherwise")})
    public ResponseEntity<String> getLineGraphics(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Lines ids") @RequestParam(name = "lineId", required = false) List<String> linesIds) {
        UUID firstRootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLinesGraphics(rootNetworkService.getNetworkUuid(firstRootNetworkUuid), nodeUuid, firstRootNetworkUuid, linesIds));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/substations")
    @Operation(summary = "Get Network substations graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substation graphics with the given ids, all otherwise")})
    public ResponseEntity<String> getSubstationGraphics(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {
        UUID firstRootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationsGraphics(rootNetworkService.getNetworkUuid(firstRootNetworkUuid), nodeUuid, firstRootNetworkUuid, substationsIds));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/equipments-ids")
    @Operation(summary = "Get equipment ids ")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipment ids")})
    public ResponseEntity<String> getNetworkElementsIds(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "equipment type") @RequestParam(name = "equipmentType") String equipmentType,
            @Parameter(description = "Nominal Voltages") @RequestParam(name = "nominalVoltages", required = false) List<Double> nominalVoltages) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkElementsIds(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), substationsIds, inUpstreamBuiltParentNode, equipmentType, nominalVoltages));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/elements")
    @Operation(summary = "Get network elements infos")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of network elements infos")})
    public ResponseEntity<String> getNetworkElementsInfos(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> substationsIds,
            @Parameter(description = "Info type") @RequestParam(name = "infoType") String infoType,
            @Parameter(description = "element type") @RequestParam(name = "elementType") String elementType,
            @Parameter(description = "Should get in upstream built node ?")
            @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "Nominal Voltages") @RequestParam(name = "nominalVoltages", required = false) List<Double> nominalVoltages) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkElementsInfos(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), substationsIds, infoType, elementType, inUpstreamBuiltParentNode, nominalVoltages));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network/elements/{elementId}")
    @Operation(summary = "Get network elements infos")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of network elements infos")})
    public ResponseEntity<String> getNetworkElementInfos(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Element id") @PathVariable("elementId") String elementId,
            @Parameter(description = "Element type") @RequestParam(name = "elementType") String elementType,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "Info type parameters") InfoTypeParameters infoTypeParameters) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkElementInfos(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), elementType, infoTypeParameters, elementId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/countries")
    @Operation(summary = "Get network countries")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of countries")})
    public ResponseEntity<String> getNetworkCountries(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkCountries(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/nominal-voltages")
    @Operation(summary = "Get network nominal voltages")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of nominal voltages")})
    public ResponseEntity<String> getNetworkNominalVoltages(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkNominalVoltages(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/branch-or-3wt/{equipmentId}/voltage-level-id")
    @Operation(summary = "Get Voltage level ID for a specific line or 2WT or 3WT and a given side")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level id attached to line or 2WT or 3WT"), @ApiResponse(responseCode = "204", description = "No voltageLevel ID found")})
    public ResponseEntity<String> getBranchOr3WTVoltageLevelId(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("equipmentId") String equipmentId,
            @RequestParam(value = "side") ThreeSides side,
            @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        String voltageLevelId = studyService.getBranchOr3WTVoltageLevelId(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), inUpstreamBuiltParentNode, equipmentId, side);
        return StringUtils.isEmpty(voltageLevelId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(voltageLevelId);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}/equipments")
    @Operation(summary = "Get voltage level equipments")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Voltage level equipments")})
    public ResponseEntity<String> getVoltageLevelEquipments(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltage level id") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelEquipments(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), substationsIds, inUpstreamBuiltParentNode, voltageLevelId));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-map/all")
    @Operation(summary = "Get Network equipments description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipments data")})
    public ResponseEntity<String> getAllMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getAllMapData(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), substationsIds));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationUuid}")
    @Operation(summary = "move network modification before another")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification order is updated")})
    public ResponseEntity<Void> moveModification(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @PathVariable("modificationUuid") UUID modificationUuid,
                                                        @Nullable @Parameter(description = "move before, if no value move to end") @RequestParam(value = "beforeUuid") UUID beforeUuid,
                                                        @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.moveModifications(studyUuid, nodeUuid, nodeUuid, rootNetworkUuid, List.of(modificationUuid), beforeUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "For a list of network modifications passed in body, copy or cut, then append them to target node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification list has been updated.")})
    public ResponseEntity<Void> moveOrCopyModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestParam("action") StudyConstants.ModificationsActionType action,
                                                         @Nullable @RequestParam("originNodeUuid") UUID originNodeUuid,
                                                         @RequestBody List<UUID> modificationsToCopyUuidList,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        if (originNodeUuid != null) {
            studyService.assertIsNodeExist(studyUuid, originNodeUuid);
            studyService.assertCanModifyNode(studyUuid, originNodeUuid, rootNetworkUuid);
        }
        switch (action) {
            case COPY, INSERT:
                studyService.createModifications(studyUuid, nodeUuid, rootNetworkUuid, modificationsToCopyUuidList, userId, action);
                break;
            case MOVE:
                studyService.moveModifications(studyUuid, nodeUuid, originNodeUuid, rootNetworkUuid, modificationsToCopyUuidList, null, userId);
                break;
            default:
                throw new StudyException(Type.UNKNOWN_ACTION_TYPE);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run")
    @Operation(summary = "run loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has started")})
    public ResponseEntity<Void> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "The limit reduction") @RequestParam(name = "limitReduction", required = false) Float limitReduction,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runLoadFlow(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId, limitReduction);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/result")
    @Operation(summary = "Get a loadflow result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow result"),
        @ApiResponse(responseCode = "204", description = "No loadflow has been done yet"),
        @ApiResponse(responseCode = "404", description = "The loadflow result has not been found")})
    public ResponseEntity<String> getLoadflowResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                    @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                    Sort sort) {
        String result = loadflowService.getLoadFlowResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), filters, sort);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/status")
    @Operation(summary = "Get the loadflow status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status"),
        @ApiResponse(responseCode = "204", description = "No loadflow has been done yet"),
        @ApiResponse(responseCode = "404", description = "The loadflow status has not been found")})
    public ResponseEntity<String> getLoadFlowStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = loadflowService.getLoadFlowStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/stop")
    @Operation(summary = "stop loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has been stopped")})
    public ResponseEntity<Void> stopLoadFlow(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                             @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                             @RequestHeader(HEADER_USER_ID) String userId) {
        loadflowService.stopLoadFlow(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run")
    @Operation(summary = "run short circuit analysis on study")
    @ApiResponse(responseCode = "200", description = "The short circuit analysis has started")
    public ResponseEntity<Void> runShortCircuit(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestParam(value = "busId", required = false) Optional<String> busId,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runShortCircuit(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), busId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis has been stopped")})
    public ResponseEntity<Void> stopShortCircuitAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                         @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        shortCircuitService.stopShortCircuitAnalysis(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result")
    @Operation(summary = "Get a short circuit analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis result"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis has not been found")})
    public ResponseEntity<String> getShortCircuitResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @Parameter(description = "BASIC (faults without limits and feeders), " +
                                                            "FULL (faults with both), " +
                                                            "WITH_LIMIT_VIOLATIONS (like FULL but only those with limit violations) or " +
                                                            "NONE (no fault)") @RequestParam(name = "mode", required = false, defaultValue = "FULL") FaultResultsMode mode,
                                                        @Parameter(description = "type") @RequestParam(value = "type", required = false, defaultValue = "ALL_BUSES") ShortcircuitAnalysisType type,
                                                        @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                        @Parameter(description = "If we wanted the paged version of the results or not") @RequestParam(name = "paged", required = false, defaultValue = "false") boolean paged,
                                                        Pageable pageable) {
        String result = shortCircuitService.getShortCircuitAnalysisResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), mode, type, filters, paged, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/status")
    @Operation(summary = "Get the short circuit analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis status"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis status has not been found")})
    public ResponseEntity<String> getShortCircuitAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                @Parameter(description = "type") @RequestParam(value = "type", required = false, defaultValue = "ALL_BUSES") ShortcircuitAnalysisType type) {
        String result = shortCircuitService.getShortCircuitAnalysisStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), type);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result/csv")
    @Operation(summary = "Get a short circuit analysis csv result")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis csv export"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis has not been found")})
    public ResponseEntity<byte[]> getShortCircuitAnalysisCsvResult(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "type") @RequestParam(value = "type") ShortcircuitAnalysisType type,
            @Parameter(description = "headersCsv") @RequestBody String headersCsv) {
        return ResponseEntity.ok().body(shortCircuitService.getShortCircuitAnalysisCsvResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), type, headersCsv));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run")
    @Operation(summary = "run voltage init on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has started"),
        @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> runVoltageInit(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runVoltageInit(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has been stopped")})
    public ResponseEntity<Void> stopVoltageInit(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                @RequestHeader(HEADER_USER_ID) String userId) {
        voltageInitService.stopVoltageInit(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/result")
    @Operation(summary = "Get a voltage init result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init result"),
        @ApiResponse(responseCode = "204", description = "No voltage init has been done yet"),
        @ApiResponse(responseCode = "404", description = "The voltage init has not been found")})
    public ResponseEntity<String> getVoltageInitResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = voltageInitService.getVoltageInitResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/status")
    @Operation(summary = "Get the voltage init status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init status"),
        @ApiResponse(responseCode = "204", description = "No voltage init has been done yet"),
        @ApiResponse(responseCode = "404", description = "The voltage init status has not been found")})
    public ResponseEntity<String> getVoltageInitStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = voltageInitService.getVoltageInitStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/voltage-init/parameters")
    @Operation(summary = "Set voltage init parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init parameters are set")})
    public ResponseEntity<Void> setVoltageInitParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) StudyVoltageInitParameters voltageInitParameters,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setVoltageInitParameters(studyUuid, voltageInitParameters, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/voltage-init/parameters")
    @Operation(summary = "Get voltage init parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init parameters")})
    public ResponseEntity<StudyVoltageInitParameters> getVoltageInitParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getVoltageInitParameters(studyUuid));
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
            @RequestParam(value = "formatParameters", required = false) String parametersJson,
            @RequestParam(value = "fileName") String fileName) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertRootNodeOrBuiltNode(studyUuid, nodeUuid, rootNetworkUuid);
        ExportNetworkInfos exportNetworkInfos = studyService.exportNetwork(nodeUuid, rootNetworkUuid, format, parametersJson, fileName);

        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename(exportNetworkInfos.getFileName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(header).contentType(MediaType.APPLICATION_OCTET_STREAM).body(exportNetworkInfos.getNetworkData());
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run")
    @Operation(summary = "run security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has started")})
    public ResponseEntity<Void> runSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        List<String> nonNullcontingencyListNames = contingencyListNames != null ? contingencyListNames : Collections.emptyList();
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runSecurityAnalysis(studyUuid, nonNullcontingencyListNames, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result")
    @Operation(summary = "Get a security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public ResponseEntity<String> getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                  @Parameter(description = "result type") @RequestParam(name = "resultType") SecurityAnalysisResultType resultType,
                                                                  @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                                  Pageable pageable) {
        String result = securityAnalysisService.getSecurityAnalysisResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), resultType, filters, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
               ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result/csv")
    @Operation(summary = "Get a security analysis result on study - CSV export")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result csv export"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public byte[] getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                           @Parameter(description = "result type") @RequestParam(name = "resultType") SecurityAnalysisResultType resultType,
                                                                           @Parameter(description = "Csv translation (JSON)") @RequestBody String csvTranslations) {
        return securityAnalysisService.getSecurityAnalysisResultCsv(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), resultType, csvTranslations);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/contingency-count")
    @Operation(summary = "Get contingency count for a list of contingency list on a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The contingency count")})
    public ResponseEntity<Integer> getContingencyCount(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames) {
        return ResponseEntity.ok().body(CollectionUtils.isEmpty(contingencyListNames) ? 0 : studyService.getContingencyCount(studyUuid, contingencyListNames, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid)));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/limit-violations")
    @Operation(summary = "Get limit violations.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The limit violations")})
    public ResponseEntity<List<LimitViolationInfos>> getLimitViolations(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                       @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                       Sort sort) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLimitViolations(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), filters, globalFilters, sort));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/computation/result/enum-values")
    @Operation(summary = "Get Enum values")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The Enum values")})
    public ResponseEntity<List<String>> getResultEnumValues(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                    @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @Parameter(description = "Computing Type") @RequestParam(name = "computingType") ComputationType computingType,
                                                                    @Parameter(description = "Enum name") @RequestParam(name = "enumName") String enumName) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getResultEnumValues(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), computingType, enumName));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "set loadflow parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters are set"),
                           @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String lfParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setLoadFlowParameters(studyUuid, lfParameter, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "Get loadflow parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters")})
    public ResponseEntity<LoadFlowParametersInfos> getLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowParametersInfos(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "set load flow provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow provider is set")})
    public ResponseEntity<Void> setLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                    @RequestBody(required = false) String provider,
                                                    @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.updateLoadFlowProvider(studyUuid, provider, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/security-analysis/provider")
    @Operation(summary = "set security analysis provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis provider is set")})
    public ResponseEntity<Void> setSecurityAnalysisProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                            @RequestBody(required = false) String provider,
                                                            @RequestHeader("userId") String userId) {
        studyService.updateSecurityAnalysisProvider(studyUuid, provider, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/dynamic-simulation/provider")
    @Operation(summary = "Set dynamic simulation provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation provider is set")})
    public ResponseEntity<Void> setDynamicSimulationProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                               @RequestBody(required = false) String provider,
                                                               @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.updateDynamicSimulationProvider(studyUuid, provider, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/dynamic-simulation/provider")
    @Operation(summary = "Get dynamic simulation provider for a specified study, empty string means default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation provider is returned")})
    public ResponseEntity<String> getDynamicSimulationProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getDynamicSimulationProvider(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/short-circuit-analysis/parameters", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "set short-circuit analysis parameters on study, reset to default ones if empty body")
    @ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters are set")
    public ResponseEntity<Void> setShortCircuitParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String shortCircuitParametersInfos,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setShortCircuitParameters(studyUuid, shortCircuitParametersInfos, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/short-circuit-analysis/parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get short-circuit analysis parameters on study")
    @ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters return by shortcircuit-server")
    public ResponseEntity<String> getShortCircuitParameters(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getShortCircuitParametersInfo(studyUuid));
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
        byte[] result = studyService.getSubstationSvg(substationId,
                diagramParameters, substationLayout, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
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
                substationId,
                diagramParameters,
                substationLayout,
                nodeUuid,
                studyService.getStudyFirstRootNetworkUuid(studyUuid));
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
            @Parameter(description = "depth") @RequestParam(name = "depth", defaultValue = "0") int depth,
            @Parameter(description = "Initialize NAD with Geographical Data") @RequestParam(name = "withGeoData", defaultValue = "true") boolean withGeoData) {
        String result = studyService.getNetworkAreaDiagram(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), voltageLevelsIds, depth, withGeoData);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/status")
    @Operation(summary = "Get the security analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis status"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis status has not been found")})
    public ResponseEntity<String> getSecurityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        SecurityAnalysisStatus status = securityAnalysisService.getSecurityAnalysisStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return status != null ? ResponseEntity.ok().body(status.name()) :
                ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Void> stopSecurityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        securityAnalysisService.stopSecurityAnalysis(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get node report with its parent nodes")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<Report>> getParentNodesReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                    @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @Parameter(description = "Node only report") @RequestParam(value = "nodeOnlyReport", required = false, defaultValue = "true") boolean nodeOnlyReport,
                                                                    @Parameter(description = "The report Type") @RequestParam(name = "reportType") StudyService.ReportType reportType,
                                                                    @Parameter(description = "Severity levels") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getParentNodesReport(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), nodeOnlyReport, reportType, severityLevels));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/report/{reportId}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get node report logs")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report logs"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<ReportLog>> getNodeReportLogs(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "node id") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "reportId") @PathVariable("reportId") String reportId,
                                                             @Parameter(description = "the message filter") @RequestParam(name = "message", required = false) String messageFilter,
                                                             @Parameter(description = "Severity levels filter") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getReportLogs(reportId, messageFilter, severityLevels));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/report/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the report logs of the given node and all its parents")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report logs of the node and all its parent"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<ReportLog>> getParentNodesReportLogs(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                    @Parameter(description = "node id") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @Parameter(description = "the message filter") @RequestParam(name = "message", required = false) String messageFilter,
                                                                    @Parameter(description = "Severity levels filter") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getParentNodesReportLogs(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), messageFilter, severityLevels));
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
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestParam(name = "onlyStashed", required = false, defaultValue = "false") Boolean onlyStashed,
                                                          @Parameter(description = "Only metadata") @RequestParam(name = "onlyMetadata", required = false, defaultValue = "false") Boolean onlyMetadata) {
        // Return json string because modification dtos are not available here
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(networkModificationTreeService.getNetworkModifications(nodeUuid, onlyStashed, onlyMetadata));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications")
    @Operation(summary = "Create a network modification for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was created"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> createNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.createNetworkModification(studyUuid, modificationAttributes, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications/{uuid}")
    @Operation(summary = "Update a modification in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was updated"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Network modification UUID") @PathVariable("uuid") UUID networkModificationUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.updateNetworkModification(studyUuid, modificationAttributes, nodeUuid, rootNetworkUuid, networkModificationUuid, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications")
    @Operation(summary = "Delete network modifications for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications was deleted"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                           @Parameter(description = "Network modification UUIDs") @RequestParam(name = "uuids", required = false) List<UUID> networkModificationUuids,
                                                           @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.deleteNetworkModifications(studyUuid, nodeUuid, rootNetworkUuid, networkModificationUuids, userId);

        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", params = "stashed")
    @Operation(summary = "Stash network modifications for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications were stashed / restored "), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> stashNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                               @Parameter(description = "Network modification UUIDs") @RequestParam("uuids") List<UUID> networkModificationUuids,
                                                               @Parameter(description = "Stashed Modification") @RequestParam(name = "stashed", required = true) Boolean stashed,
                                                               @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        if (stashed.booleanValue()) {
            studyService.stashNetworkModifications(studyUuid, nodeUuid, rootNetworkUuid, networkModificationUuids, userId);
        } else {
            studyService.restoreNetworkModifications(studyUuid, nodeUuid, rootNetworkUuid, networkModificationUuids, userId);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", params = "activated")
    @Operation(summary = "Update 'activated' value for a network modifications for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Update the activation status for network modifications on a node"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateNetworkModificationsActivation(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Network modification UUIDs") @RequestParam("uuids") List<UUID> networkModificationUuids,
                                                          @Parameter(description = "New activated value") @RequestParam(name = "activated", required = true) Boolean activated,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.updateNetworkModificationsActivation(studyUuid, nodeUuid, rootNetworkUuid, networkModificationUuids, userId, activated);
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
        @Parameter(description = "Should search in upstream built node") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
        @Parameter(description = "Equipment type") @RequestParam(value = "equipmentType", required = false) String equipmentType) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(studyService.searchEquipments(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userInput, fieldSelector, equipmentType, inUpstreamBuiltParentNode));
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "Create a node as before / after the given node ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node has been added"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<NetworkModificationNode> createNode(@RequestBody NetworkModificationNode node,
                                                         @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                         @Parameter(description = "parent id of the node created") @PathVariable(name = "id") UUID referenceId,
                                                         @Parameter(description = "node is inserted before the given node ID") @RequestParam(name = "mode", required = false, defaultValue = "CHILD") InsertMode insertMode,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.createNode(studyUuid, referenceId, node, insertMode, userId));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/tree/nodes")
    @Operation(summary = "Delete node with given ids")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the nodes have been successfully deleted"),
        @ApiResponse(responseCode = "404", description = "The study or the nodes not found")})
    public ResponseEntity<Void> deleteNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                           @Parameter(description = "ids of children to remove") @RequestParam("ids") List<UUID> nodeIds,
                                           @Parameter(description = "deleteChildren") @RequestParam(value = "deleteChildren", defaultValue = "false") boolean deleteChildren,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.deleteNodes(studyUuid, nodeIds, rootNetworkUuid, deleteChildren, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes/{id}/stash")
    @Operation(summary = "Move to trash the node with given id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the nodes have been successfully moved to trash"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> stashNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @Parameter(description = "id of child to delete (move to trash)") @PathVariable("id") UUID nodeId,
                                                 @Parameter(description = "stashChildren") @RequestParam(value = "stashChildren", defaultValue = "false") boolean stashChildren,
                                                 @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.stashNode(studyUuid, nodeId, rootNetworkUuid, stashChildren, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/tree/nodes/stash")
    @Operation(summary = "Get the list of nodes in the trash for a given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the list of nodes in the trash")})
    public ResponseEntity<List<Pair<AbstractNode, Integer>>> getStashedNodes(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getStashedNodes(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes/restore")
    @Operation(summary = "restore nodes below the given anchor node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the list of nodes in the trash")})
    public ResponseEntity<Void> restoreNodes(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                            @Parameter(description = "ids of nodes to restore") @RequestParam("ids") List<UUID> nodeIds,
                                            @Parameter(description = "id of node below which the node will be restored") @RequestParam("anchorNodeId") UUID anchorNodeId) {
        studyService.restoreNodes(studyUuid, nodeIds, anchorNodeId);
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

    @GetMapping(value = "/studies/{studyUuid}/subtree")
    @Operation(summary = "Get network modification subtree for the given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "network modification subtree"),
        @ApiResponse(responseCode = "404", description = "The study or the parent node not found")})
    public ResponseEntity<NetworkModificationNode> getNetworkModificationSubtree(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                 @Parameter(description = "parent node uuid") @RequestParam(value = "parentNodeUuid") UUID parentNodeUuid) {
        NetworkModificationNode parentNode = (NetworkModificationNode) networkModificationTreeService.getStudySubtree(studyUuid, parentNodeUuid);
        return parentNode != null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(parentNode)
                : ResponseEntity.notFound().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/tree/nodes")
    @Operation(summary = "update node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the node has been updated"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> updateNode(@RequestBody NetworkModificationNode node,
                                                 @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @RequestHeader(HEADER_USER_ID) String userId) {
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

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/build")
    @Operation(summary = "build a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been built"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
                           @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> buildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                          @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertNoBuildNoComputation(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/unbuild")
    @Operation(summary = "unbuild a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been unbuilt"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
        @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> unbuildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.unbuildNode(studyUuid, nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/build/stop")
    @Operation(summary = "stop a node build")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The build has been stopped"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Void> stopBuild(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.stopBuild(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/loadflow-default-provider")
    @Operation(summary = "get load flow default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the load flow default provider has been found"))
    public ResponseEntity<String> getDefaultLoadflowProvider(
        @RequestHeader(name = HEADER_USER_ID, required = false) String userId // not required to allow to query the system default provider without a user
    ) {
        return ResponseEntity.ok().body(studyService.getDefaultLoadflowProvider(userId));
    }

    @GetMapping(value = "/security-analysis-default-provider")
    @Operation(summary = "get security analysis default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the security analysis default provider has been found"))
    public ResponseEntity<String> getDefaultSecurityAnalysisProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultSecurityAnalysisProvider());
    }

    @GetMapping(value = "/sensitivity-analysis-default-provider")
    @Operation(summary = "get sensitivity analysis default provider value")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the sensitivity analysis default provider has been found"))
    public ResponseEntity<String> getDefaultSensitivityAnalysisProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultSensitivityAnalysisProvider());
    }

    @GetMapping(value = "/dynamic-simulation-default-provider")
    @Operation(summary = "get dynamic simulation default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the dynamic simulation default provider has been found"))
    public ResponseEntity<String> getDefaultDynamicSimulationProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultDynamicSimulationProvider());
    }

    @PostMapping(value = "/studies/{studyUuid}/reindex-all")
    @Operation(summary = "reindex the study")
    @ApiResponse(responseCode = "200", description = "Study reindexed")
    public ResponseEntity<Void> reindexStudy(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        studyService.reindexStudy(studyUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
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
        @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has started"), @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> runSensitivityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runSensitivityAnalysis(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result")
    @Operation(summary = "Get a sensitivity analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisResult(
        @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "results selector") @RequestParam("selector") String selector) {
        String result = sensitivityAnalysisService.getSensitivityAnalysisResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), selector);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a sensitivity analysis result as csv")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Csv of sensitivity analysis results"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<byte[]> exportSensitivityResultsAsCsv(
        @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @RequestBody SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        byte[] result = sensitivityAnalysisService.exportSensitivityResultsAsCsv(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), sensitivityAnalysisCsvFileInfos);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDispositionFormData("attachment", "sensitivity_results.csv");

        return ResponseEntity
                .ok()
                .headers(responseHeaders)
                .body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options")
    @Operation(summary = "Get sensitivity analysis filter options on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis filter options"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisFilterOptions(
        @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "results selector") @RequestParam("selector") String selector) {
        String result = sensitivityAnalysisService.getSensitivityResultsFilterOptions(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), selector);
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
        String result = sensitivityAnalysisService.getSensitivityAnalysisStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/stop")
    @Operation(summary = "stop sensitivity analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has been stopped")})
    public ResponseEntity<Void> stopSensitivityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @RequestHeader(HEADER_USER_ID) String userId) {
        sensitivityAnalysisService.stopSensitivityAnalysis(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    // --- Dynamic Simulation Endpoints BEGIN --- //

    @GetMapping(value = "/studies/{studyUuid}/dynamic-simulation/mappings")
    @Operation(summary = "Get all mapping of dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All mappings of dynamic simulation"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation mappings"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation mappings has not been found")})
    public ResponseEntity<List<MappingInfos>> getDynamicSimulationMappings(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid) {
        List<MappingInfos> mappings = studyService.getDynamicSimulationMappings(studyUuid);
        return mappings != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mappings) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/models")
    @Operation(summary = "Get models of dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All models of dynamic simulation"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation models"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation models has not been found")})
    public ResponseEntity<List<ModelInfos>> getDynamicSimulationModels(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<ModelInfos> models = studyService.getDynamicSimulationModels(studyUuid, nodeUuid);
        return models != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(models) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/dynamic-simulation/parameters")
    @Operation(summary = "set dynamic simulation parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation parameters are set")})
    public ResponseEntity<Void> setDynamicSimulationParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) DynamicSimulationParametersInfos dsParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setDynamicSimulationParameters(studyUuid, dsParameter, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/dynamic-simulation/parameters")
    @Operation(summary = "Get dynamic simulation parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation parameters")})
    public ResponseEntity<DynamicSimulationParametersInfos> getDynamicSimulationParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getDynamicSimulationParameters(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/events")
    @Operation(summary = "Get dynamic simulation events from a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation events was returned"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<EventInfos>> getDynamicSimulationEvents(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                       @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<EventInfos> dynamicSimulationEvents = studyService.getDynamicSimulationEvents(nodeUuid);
        return ResponseEntity.ok().body(dynamicSimulationEvents);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/events", params = {"equipmentId"})
    @Operation(summary = "Get dynamic simulation event from a node with a given equipment id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation event was returned"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<EventInfos> getDynamicSimulationEvent(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                               @Parameter(description = "Equipment id") @RequestParam(value = "equipmentId") String equipmentId) {
        EventInfos dynamicSimulationEvent = studyService.getDynamicSimulationEvent(nodeUuid, equipmentId);
        return dynamicSimulationEvent != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSimulationEvent) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/events")
    @Operation(summary = "Create a dynamic simulation event for a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The network event was created"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> createDynamicSimulationEvent(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestBody EventInfos event,
                                                             @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.createDynamicSimulationEvent(studyUuid, nodeUuid, userId, event);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/events")
    @Operation(summary = "Update a dynamic simulation event for a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation event was updated"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateDynamicSimulationEvent(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestBody EventInfos event,
                                                             @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.updateDynamicSimulationEvent(studyUuid, nodeUuid, userId, event);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/events")
    @Operation(summary = "Delete dynamic simulation events for a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The dynamic simulation events was deleted"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteDynamicSimulationEvents(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                              @Parameter(description = "Dynamic simulation event UUIDs") @RequestParam("eventUuids") List<UUID> eventUuids,
                                                              @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.deleteDynamicSimulationEvents(studyUuid, nodeUuid, userId, eventUuids);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/run")
    @Operation(summary = "run dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation has started")})
    public ResponseEntity<Void> runDynamicSimulation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @RequestBody(required = false) DynamicSimulationParametersInfos parameters,
                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runDynamicSimulation(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), parameters, userId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeseries/metadata")
    @Operation(summary = "Get list of time series metadata of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Time series metadata of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation metadata"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<TimeSeriesMetadataInfos>> getDynamicSimulationTimeSeriesMetadata(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<TimeSeriesMetadataInfos> result = studyService.getDynamicSimulationTimeSeriesMetadata(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeseries")
    @Operation(summary = "Get all time series of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All time series of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation timeseries"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<DoubleTimeSeries>> getDynamicSimulationTimeSeriesResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                                       @Parameter(description = "timeSeriesNames") @RequestParam(name = "timeSeriesNames", required = false) List<String> timeSeriesNames) {
        List<DoubleTimeSeries> result = studyService.getDynamicSimulationTimeSeries(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), timeSeriesNames);
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeline")
    @Operation(summary = "Get timeline events of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Timeline events of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation timeline events"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<TimelineEventInfos>> getDynamicSimulationTimelineResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<TimelineEventInfos> result = studyService.getDynamicSimulationTimeline(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/status")
    @Operation(summary = "Get the status of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The status of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation status"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<DynamicSimulationStatus> getDynamicSimulationStatus(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        DynamicSimulationStatus result = studyService.getDynamicSimulationStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
                ResponseEntity.noContent().build();
    }

    // --- Dynamic Simulation Endpoints END --- //

    @GetMapping(value = "/studies/{studyUuid}/security-analysis/parameters")
    @Operation(summary = "Get security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis parameters")})
    public ResponseEntity<String> getSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getSecurityAnalysisParametersValues(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/security-analysis/parameters")
    @Operation(summary = "set security analysis parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis parameters are set")})
    public ResponseEntity<Void> setSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String securityAnalysisParametersValues,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setSecurityAnalysisParametersValues(studyUuid, securityAnalysisParametersValues, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/modifications", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get the voltage init modifications from a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init modifications was returned"), @ApiResponse(responseCode = "404", description = "The study/node is not found, or has no voltage init result")})
    public ResponseEntity<String> getVoltageInitModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(studyService.getVoltageInitModifications(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid)));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/modifications", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Clone the voltage init modifications, then append them to node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init modifications have been appended.")})
    public ResponseEntity<Void> copyVoltageInitModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                             @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        UUID rootNetworkUuid = studyService.getStudyFirstRootNetworkUuid(studyUuid);
        studyService.assertCanModifyNode(studyUuid, nodeUuid, rootNetworkUuid);
        studyService.copyVoltageInitModifications(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/optional-services")
    @Operation(summary = "Get all the optional services and their status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of optional services")})
    public ResponseEntity<List<ServiceStatusInfos>> getOptionalServices() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(remoteServicesInspector.getOptionalServices());
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

    @GetMapping(value = "/studies/{studyUuid}/sensitivity-analysis/parameters")
    @Operation(summary = "Get sensitivity analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis parameters")})
    public ResponseEntity<String> getSensitivityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getSensitivityAnalysisParameters(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/sensitivity-analysis/parameters")
    @Operation(summary = "set sensitivity analysis parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis parameters are set")})
    public ResponseEntity<Void> setSensitivityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String sensitivityAnalysisParameters,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setSensitivityAnalysisParameters(studyUuid, sensitivityAnalysisParameters, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/factors-count")
    @Operation(summary = "Get the factors count of sensitivity parameters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The factors count of sensitivity parameters")})
    public ResponseEntity<Long> getSensitivityAnalysisFactorsCount(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Is Injections Set") @RequestParam(name = "isInjectionsSet", required = false) Boolean isInjectionsSet,
            SensitivityFactorsIdsByGroup factorsIds) {
        return ResponseEntity.ok().body(sensitivityAnalysisService.getSensitivityAnalysisFactorsCount(rootNetworkService.getNetworkUuid(studyService.getStudyFirstRootNetworkUuid(studyUuid)),
            networkModificationTreeService.getVariantId(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid)), factorsIds, isInjectionsSet));
    }

    @PutMapping(value = "/studies/{studyUuid}/loadflow/invalidate-status")
    @Operation(summary = "Invalidate loadflow status on study nodes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The loadflow status has been invalidated on all study nodes"),
        @ApiResponse(responseCode = "404", description = "The study is not found")})
    public ResponseEntity<Void> invalidateLoadFlowStatus(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.invalidateLoadFlowStatus(studyUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/short-circuit/invalidate-status")
    @Operation(summary = "Invalidate short circuit status on study nodes")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit status has been invalidated on all study nodes"),
                           @ApiResponse(responseCode = "404", description = "The study is not found")})
    public ResponseEntity<Void> invalidateShortCircuitStatus(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        studyService.invalidateShortCircuitStatus(studyUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/run")
    @Operation(summary = "run sensitivity analysis non evacuated energy on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy has started")})
    public ResponseEntity<UUID> runNonEvacuatedEnergy(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                      @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        return ResponseEntity.ok().body(studyService.runNonEvacuatedEnergy(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/result")
    @Operation(summary = "Get a sensitivity analysis non evacuated energy result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy result"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis non evacuated energy has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis non evacuated energy has not been found")})
    public ResponseEntity<String> getNonEvacuatedEnergyResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = nonEvacuatedEnergyService.getNonEvacuatedEnergyResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/status")
    @Operation(summary = "Get the sensitivity analysis non evacuated energy status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy status"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis non evacuated energy has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis status non evacuated energy has not been found")})
    public ResponseEntity<String> getNonEvacuatedEnergyStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = nonEvacuatedEnergyService.getNonEvacuatedEnergyStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/stop")
    @Operation(summary = "stop sensitivity analysis non evacuated energy on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy has been stopped")})
    public ResponseEntity<Void> stopNonEvacuatedEnergy(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        nonEvacuatedEnergyService.stopNonEvacuatedEnergy(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/non-evacuated-energy/parameters")
    @Operation(summary = "Get sensitivity analysis non evacuated energy parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy parameters")})
    public ResponseEntity<NonEvacuatedEnergyParametersInfos> getNonEvacuatedEnergyParametersInfos(
        @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getNonEvacuatedEnergyParametersInfos(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/non-evacuated-energy/parameters")
    @Operation(summary = "set sensitivity analysis non evacuated energy parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis parameters non evacuated energy are set")})
    public ResponseEntity<Void> setNonEvacuatedEnergyParametersInfos(
        @PathVariable("studyUuid") UUID studyUuid,
        @RequestBody(required = false) NonEvacuatedEnergyParametersInfos nonEvacuatedEnergyParametersInfos,
        @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setNonEvacuatedEnergyParametersInfos(studyUuid, nonEvacuatedEnergyParametersInfos, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/non-evacuated-energy/provider")
    @Operation(summary = "set sensitivity analysis non evacuated energy provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy provider is set")})
    public ResponseEntity<Void> setNonEvacuatedEnergyProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                               @RequestBody(required = false) String provider,
                                                               @RequestHeader("userId") String userId) {
        studyService.updateNonEvacuatedEnergyProvider(studyUuid, provider, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/non-evacuated-energy/provider")
    @Operation(summary = "Get sensitivity analysis non evacuated energy provider for a specified study, empty string means default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy provider is returned")})
    public ResponseEntity<String> getNonEvacuatedEnergyProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getNonEvacuatedEnergyProvider(studyUuid));
    }

    @GetMapping(value = "/non-evacuated-energy-default-provider")
    @Operation(summary = "get sensitivity analysis non evacuated energy default provider value")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the sensitivity analysis non evacuated energy default provider has been found"))
    public ResponseEntity<String> getDefaultNonEvacuatedEnergyProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultNonEvacuatedEnergyProvider());
    }

    @GetMapping(value = "/servers/infos")
    @Operation(summary = "Get the information of all backend servers (if not filter with view parameter)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The information on all known servers"),
        @ApiResponse(responseCode = "207", description = "Partial result because some servers haven't responded or threw an error"),
        @ApiResponse(responseCode = "424", description = "All requests have failed, no information retrieved")})
    public ResponseEntity<Map<String, JsonNode>> getSuiteServersInformation(
            @Parameter(description = "the view which will be used to filter the returned services") @RequestParam final Optional<FrontService> view
    ) { //Map<String, Info> from springboot-actuator
        try {
            return ResponseEntity.ok(remoteServicesInspector.getServicesInfo(view.orElse(null)));
        } catch (final PartialResultException e) {
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body((Map<String, JsonNode>) e.getResult());
        }
    }

    @GetMapping(value = "/servers/about")
    @Operation(summary = "Get the aggregated about information from all (if not filter with view parameter) backend servers")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The information on all known servers"),
        @ApiResponse(responseCode = "207", description = "Partial result because some servers haven't responded or threw an error"),
        @ApiResponse(responseCode = "424", description = "All requests have failed, no information retrieved")})
    public ResponseEntity<AboutInfo[]> getSuiteAboutInformation(
            @Parameter(description = "the view which will be used to filter the returned services") @RequestParam final Optional<FrontService> view
    ) {
        final ResponseEntity<Map<String, JsonNode>> suiteServersInfo = this.getSuiteServersInformation(view);
        return ResponseEntity.status(suiteServersInfo.getStatusCode()).body(
                remoteServicesInspector.convertServicesInfoToAboutInfo(Objects.requireNonNullElseGet(suiteServersInfo.getBody(), Map::of)));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/filters/evaluate")
    @Operation(summary = "Evaluate a filter to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> evaluateFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @RequestBody String filter) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.evaluateFilter(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), inUpstreamBuiltParentNode, filter));
    }

    @GetMapping(value = "/studies/{studyUuid}/filters/{filterUuid}/elements")
    @Operation(summary = "Evaluate a filter on root node to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> exportFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Filter uuid to be applied") @PathVariable("filterUuid") UUID filterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.exportFilter(studyService.getStudyFirstRootNetworkUuid(studyUuid), filterUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/state-estimation/run")
    @Operation(summary = "run state estimation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation has started")})
    public ResponseEntity<Void> runStateEstimation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                    @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runStateEstimation(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/state-estimation/result")
    @Operation(summary = "Get a state estimation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation result"),
        @ApiResponse(responseCode = "204", description = "No state estimation has been done yet"),
        @ApiResponse(responseCode = "404", description = "The state estimation has not been found")})
    public ResponseEntity<String> getStateEstimationResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = stateEstimationService.getStateEstimationResult(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/state-estimation/status")
    @Operation(summary = "Get the state estimation status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation status"),
        @ApiResponse(responseCode = "204", description = "No state estimation has been done yet"),
        @ApiResponse(responseCode = "404", description = "The state estimation status has not been found")})
    public ResponseEntity<String> getStateEstimationStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = stateEstimationService.getStateEstimationStatus(nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/state-estimation/stop")
    @Operation(summary = "stop state estimation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation has been stopped")})
    public ResponseEntity<Void> stopStateEstimation(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        stateEstimationService.stopStateEstimation(studyUuid, nodeUuid, studyService.getStudyFirstRootNetworkUuid(studyUuid));
        return ResponseEntity.ok().build();
    }
}
