/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.timeseries.DoubleTimeSeries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.filter.globalfilter.GlobalFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.StudyConstants.ModificationsActionType;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.computation.LoadFlowComputationInfos;
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.nad.NadConfigInfos;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.dto.modification.ModificationsSearchResultByNode;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityFactorsIdsByGroup;
import org.gridsuite.study.server.dto.sequence.NodeSequenceType;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.exception.PartialResultException;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
import org.gridsuite.study.server.service.shortcircuit.FaultResultsMode;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.gridsuite.study.server.utils.ResultParameters;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Nullable;
import java.beans.PropertyEditorSupport;
import java.util.*;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.MOVE_NETWORK_MODIFICATION_FORBIDDEN;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;

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
    private final CaseService caseService;
    private final RemoteServicesInspector remoteServicesInspector;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final SensitivityAnalysisService sensitivityAnalysisService;

    public StudyController(StudyService studyService,
                           NetworkService networkStoreService,
                           NetworkModificationTreeService networkModificationTreeService,
                           SingleLineDiagramService singleLineDiagramService,
                           NetworkConversionService networkConversionService,
                           CaseService caseService,
                           RemoteServicesInspector remoteServicesInspector,
                           RootNetworkService rootNetworkService,
                           RootNetworkNodeInfoService rootNetworkNodeInfoService, SensitivityAnalysisService sensitivityAnalysisService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkStoreService = networkStoreService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.caseService = caseService;
        this.remoteServicesInspector = remoteServicesInspector;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
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

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/case/name")
    @Operation(summary = "Get study case name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study case name"),
                           @ApiResponse(responseCode = "204", description = "The study has no case name attached")})
    public ResponseEntity<String> getStudyCaseName(@PathVariable("studyUuid") UUID studyUuid, @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        String studyCaseName = rootNetworkService.getCaseName(rootNetworkUuid);
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
                                                       @RequestParam(required = false, value = "firstRootNetworkName", defaultValue = "") String firstRootNetworkName,
                                                       @RequestBody(required = false) Map<String, Object> importParameters,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        caseService.assertCaseExists(caseUuid);
        BasicStudyInfos createStudy = studyService.createStudy(caseUuid, userId, studyUuid, importParameters, duplicateCase, caseFormat, firstRootNetworkName);
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
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getStudyInfos(studyUuid));
    }

    @DeleteMapping(value = "/studies/{studyUuid}")
    @Operation(summary = "delete the study")
    @ApiResponse(responseCode = "200", description = "Study deleted")
    public ResponseEntity<Void> deleteStudy(@PathVariable("studyUuid") UUID studyUuid) {
        studyService.deleteStudyIfNotCreationInProgress(studyUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks")
    @Operation(summary = "Get root networks for study")
    @ApiResponse(responseCode = "200", description = "List of root networks")
    public ResponseEntity<List<BasicRootNetworkInfos>> getRootNetworks(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getAllBasicRootNetworkInfos(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks")
    @Operation(summary = "Create root network for study")
    @ApiResponse(responseCode = "200", description = "Root network created")
    public ResponseEntity<RootNetworkRequestInfos> createRootNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                                     @RequestBody RootNetworkInfos rootNetworkInfos,
                                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        return ResponseEntity.ok().body(studyService.createRootNetworkRequest(studyUuid, rootNetworkInfos, userId));
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}")
    @Operation(summary = "update root network case")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The case is updated for a root network")})
    public ResponseEntity<Void> updateRootNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                  @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @RequestBody RootNetworkInfos rootNetworkInfos,
                                                  @RequestHeader(HEADER_USER_ID) String userId) {
        caseService.assertCaseExists(rootNetworkInfos.getCaseInfos() != null ? rootNetworkInfos.getCaseInfos().getOriginalCaseUuid() : null);
        studyService.assertNoBlockedNodeInTree(networkModificationTreeService.getStudyRootNodeUuid(studyUuid), rootNetworkUuid);
        studyService.updateRootNetworkRequest(studyUuid, rootNetworkInfos, userId);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/studies/{studyUuid}/root-networks", params = {"name"})
    @Operation(summary = "Check if a root network already exists")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The root network exists"),
        @ApiResponse(responseCode = "204", description = "The root network doesn't exist")})
    public ResponseEntity<Void> rootNetworkExists(@PathVariable("studyUuid") UUID studyUuid,
                                              @RequestParam("name") String rootNetworkName) {
        HttpStatus status = rootNetworkService.isRootNetworkNameExistsInStudy(studyUuid, rootNetworkName) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/studies/{studyUuid}/root-networks", params = "tag")
    @Operation(summary = "Check if a root network with this tag already exists in the given study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The tag exists"),
        @ApiResponse(responseCode = "204", description = "The tag doesn't exist")})
    public ResponseEntity<Void> rootNetworkTagExists(@PathVariable("studyUuid") UUID studyUuid,
                                                @RequestParam("tag") String rootNetworkTag) {
        HttpStatus status = rootNetworkService.isRootNetworkTagExistsInStudy(studyUuid, rootNetworkTag) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }

    @DeleteMapping(value = "/studies/{studyUuid}/root-networks")
    @Operation(summary = "Delete root networks for study")
    @ApiResponse(responseCode = "200", description = "Root network deleted")
    public ResponseEntity<Void> deleteRootNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                    @RequestBody List<UUID> rootNetworksUuids) {
        studyService.deleteRootNetworks(studyUuid, rootNetworksUuids);
        return ResponseEntity.ok().build();
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
                                              @Parameter(description = "The position where the node will be pasted relative to the reference node") @RequestParam(name = "insertMode") InsertMode insertMode,
                                              @RequestHeader(HEADER_USER_ID) String userId) {
        //if the source study is not set we assume it's the same as the target study
        studyService.assertNoBlockedNodeInStudy(targetStudyUuid, referenceNodeUuid);
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
                                              @Parameter(description = "The position where the node will be pasted relative to the reference node") @RequestParam(name = "insertMode") InsertMode insertMode,
                                              @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeToCutUuid);
        studyService.moveStudyNode(studyUuid, nodeToCutUuid, referenceNodeUuid, insertMode, userId);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", method = RequestMethod.HEAD)
    @Operation(summary = "check study root network existence")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The network does exist"),
        @ApiResponse(responseCode = "204", description = "The network doesn't exist")})
    public ResponseEntity<Void> checkNetworkExistence(@PathVariable("studyUuid") UUID studyUuid, @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        UUID networkUUID = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        return networkStoreService.doesNetworkExist(networkUUID)
            ? ResponseEntity.ok().build()
            : ResponseEntity.noContent().build();

    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", params = {"caseUuid"})
    @Operation(summary = "recreate study network of a study from an existing case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Study network recreation has started"),
        @ApiResponse(responseCode = "424", description = "The case doesn't exist")})
    public ResponseEntity<BasicStudyInfos> recreateNetworkFromCase(@PathVariable("studyUuid") UUID studyUuid,
                                                                 @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                 @RequestBody(required = false) Map<String, Object> importParameters,
                                                                 @RequestParam(value = "caseUuid") UUID caseUuid,
                                                                 @Parameter(description = "case format") @RequestParam(name = "caseFormat", required = false) String caseFormat,
                                                                 @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.recreateNetwork(caseUuid, userId, studyUuid, rootNetworkUuid, caseFormat, importParameters);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network")
    @Operation(summary = "recreate study network of a study from its case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Study network recreation has started"),
        @ApiResponse(responseCode = "424", description = "The study's case doesn't exist")})
    public ResponseEntity<BasicStudyInfos> recreateStudyNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                                @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                @RequestHeader(HEADER_USER_ID) String userId,
                                                                @Parameter(description = "case format") @RequestParam(name = "caseFormat", required = false) String caseFormat
    ) {
        studyService.recreateNetwork(userId, studyUuid, rootNetworkUuid, caseFormat);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status")
    @Operation(summary = "check root network indexation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The root network indexation status for a study"),
        @ApiResponse(responseCode = "204", description = "The root network indexation status doesn't exist"),
        @ApiResponse(responseCode = "404", description = "The root network or network doesn't exist")})
    public ResponseEntity<String> checkRootNetworkIndexationStatus(@PathVariable("studyUuid") UUID studyUuid, @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        String result = studyService.getRootNetworkIndexationStatus(studyUuid, rootNetworkUuid).name();
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
        studyService.assertNoBlockedNodeInStudy(studyUuid, subtreeToCutParentNodeUuid);
        studyService.moveStudySubtree(studyUuid, subtreeToCutParentNodeUuid, referenceNodeUuid, userId);
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
        studyService.assertNoBlockedNodeInStudy(targetStudyUuid, referenceNodeUuid);
        studyService.duplicateStudySubtree(sourceStudyUuid, targetStudyUuid, subtreeToCopyParentNodeUuid, referenceNodeUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg"),
        @ApiResponse(responseCode = "404", description = "The voltage level has not been found")})
    public ResponseEntity<byte[]> generateVoltageLevelDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        byte[] result = studyService.generateVoltageLevelSvg(
                voltageLevelId,
                nodeUuid,
                rootNetworkUuid,
                sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(result) :
            ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg and metadata"),
        @ApiResponse(responseCode = "404", description = "The voltage level has not been found")})
    public ResponseEntity<String> generateVoltageLevelDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        String result = studyService.generateVoltageLevelSvgAndMetadata(
                voltageLevelId,
                nodeUuid,
                rootNetworkUuid,
                sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses-or-busbar-sections")
    @Operation(summary = "get the buses for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The buses list of the network for given voltage level")
    public ResponseEntity<List<IdentifiableInfos>> getVoltageLevelBusesOrBusbarSections(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelBusesOrBusbarSections(nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/switches")
    @Operation(summary = "get the switches for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The switches list of the network for given voltage level")
    public ResponseEntity<String> getVoltageLevelSwitches(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelTopologyInfos(nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode, "switches"));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/bus-bar-sections")
    @Operation(summary = "get bus bar sections information for a given network and voltage level")
    @ApiResponse(responseCode = "200", description = "Bus bar sections information of the given voltage level retrieved")
    public ResponseEntity<String> getVoltageLevelBusBarSections(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelTopologyInfos(nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode, "bus-bar-sections"));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/feeder-bays")
    @Operation(summary = "get feeder bays informations for a given network and voltage level")
    @ApiResponse(responseCode = "200", description = "Feeder bays informations of the given voltage level retrieved")
    public ResponseEntity<String> getVoltageLevelFeederBays(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelTopologyInfos(nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode, "feeder-bays"));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/substation-id")
    @Operation(summary = "get the substation ID for a given network and a given voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation Id for a voltageLevel")})
    public ResponseEntity<String> getVoltageLevelSubstationId(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltageLevelId") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().body(studyService.getVoltageLevelSubstationId(studyUuid, nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcId}/shunt-compensators")
    @Operation(summary = "For a given hvdc line, get its related Shunt compensators in case of LCC converter station")
    @ApiResponse(responseCode = "200", description = "Hvdc line type and its shunt compensators on each side")
    public ResponseEntity<String> getHvdcLineShuntCompensators(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("hvdcId") String hvdcId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        String hvdcInfos = studyService.getHvdcLineShuntCompensators(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, hvdcId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(hvdcInfos);
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/lines", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Network lines graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of line graphics with the given ids, all otherwise")})
    public ResponseEntity<String> getLineGraphics(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> linesIds) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLinesGraphics(rootNetworkService.getNetworkUuid(rootNetworkUuid), nodeUuid, rootNetworkUuid, linesIds));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/substations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Network substations graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substation graphics with the given ids, all otherwise")})
    public ResponseEntity<String> getSubstationGraphics(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> substationsIds) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getSubstationsGraphics(rootNetworkService.getNetworkUuid(rootNetworkUuid), nodeUuid, rootNetworkUuid, substationsIds));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/equipments-ids")
    @Operation(summary = "Get equipment ids ")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipment ids")})
    public ResponseEntity<String> getNetworkElementsIds(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "equipment type") @RequestParam(name = "equipmentType") String equipmentType,
            @Parameter(description = "Nominal Voltages") @RequestParam(name = "nominalVoltages", required = false) List<Double> nominalVoltages) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkElementsIds(nodeUuid, rootNetworkUuid, substationsIds, inUpstreamBuiltParentNode, equipmentType, nominalVoltages));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements")
    @Operation(summary = "Get network elements infos")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of network elements infos")})
    public ResponseEntity<String> getNetworkElementsInfos(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> substationsIds,
            @Parameter(description = "Info type") @RequestParam(name = "infoType") String infoType,
            @Parameter(description = "element type") @RequestParam(name = "elementType") String elementType,
            @Parameter(description = "Should get in upstream built node ?")
            @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "Nominal Voltages") @RequestParam(name = "nominalVoltages", required = false) List<Double> nominalVoltages) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkElementsInfos(studyUuid, nodeUuid, rootNetworkUuid, substationsIds, infoType, elementType, inUpstreamBuiltParentNode, nominalVoltages));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements-by-global-filter")
    @Operation(summary = "Get network elements infos by evaluating a global filter")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The list of network elements infos matching the filter"),
        @ApiResponse(responseCode = "404", description = "The study/root network/node is not found")
    })
    public ResponseEntity<String> getNetworkElementsInfosByGlobalFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "The equipment type to filter and return") @RequestParam(name = "equipmentType") @NonNull EquipmentType equipmentType,
            @Parameter(description = "Info type (e.g., LIST, TAB, MAP, FORM)") @RequestParam(name = "infoType", defaultValue = "LIST") String infoType,
            @RequestBody @NonNull GlobalFilter filter) {
        studyService.assertIsRootNetworkAndNodeInStudy(studyUuid, rootNetworkUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(studyService.getNetworkElementsInfosByGlobalFilter(studyUuid, nodeUuid, rootNetworkUuid, equipmentType, infoType, filter));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements/{elementId}")
    @Operation(summary = "Get network elements infos")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of network elements infos")})
    public ResponseEntity<String> getNetworkElementInfos(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Element id") @PathVariable("elementId") String elementId,
            @Parameter(description = "Element type") @RequestParam(name = "elementType") String elementType,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "Info type parameters") InfoTypeParameters infoTypeParameters) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkElementInfos(studyUuid, nodeUuid, rootNetworkUuid, elementType, infoTypeParameters, elementId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/countries")
    @Operation(summary = "Get network countries")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of countries")})
    public ResponseEntity<String> getNetworkCountries(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkCountries(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/nominal-voltages")
    @Operation(summary = "Get network nominal voltages")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of nominal voltages")})
    public ResponseEntity<String> getNetworkNominalVoltages(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkNominalVoltages(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/branch-or-3wt/{equipmentId}/voltage-level-id")
    @Operation(summary = "Get Voltage level ID for a specific line or 2WT or 3WT and a given side")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level id attached to line or 2WT or 3WT"), @ApiResponse(responseCode = "204", description = "No voltageLevel ID found")})
    public ResponseEntity<String> getBranchOr3WTVoltageLevelId(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("equipmentId") String equipmentId,
            @RequestParam(value = "side") ThreeSides side,
            @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        String voltageLevelId = studyService.getBranchOr3WTVoltageLevelId(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, equipmentId, side);
        return StringUtils.isEmpty(voltageLevelId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(voltageLevelId);
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}/equipments")
    @Operation(summary = "Get voltage level equipments")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Voltage level equipments")})
    public ResponseEntity<String> getVoltageLevelEquipments(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltage level id") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageLevelEquipments(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, voltageLevelId));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/all")
    @Operation(summary = "Get Network equipments description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipments data")})
    public ResponseEntity<String> getAllMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getAllMapData(studyUuid, nodeUuid, rootNetworkUuid, substationsIds));
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationUuid}")
    @Operation(summary = "move network modification before another")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification order is updated")})
    public ResponseEntity<Void> moveModification(@PathVariable("studyUuid") UUID studyUuid,
                                                        @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @PathVariable("modificationUuid") UUID modificationUuid,
                                                        @Nullable @Parameter(description = "move before, if no value move to end") @RequestParam(value = "beforeUuid") UUID beforeUuid,
                                                        @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        handleMoveNetworkModification(studyUuid, nodeUuid, modificationUuid, beforeUuid, userId);
        return ResponseEntity.ok().build();
    }

    private void handleMoveNetworkModification(UUID studyUuid, UUID nodeUuid, UUID modificationUuid, UUID beforeUuid, String userId) {
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeUuid);
        studyService.invalidateNodeTreeWhenMoveModification(studyUuid, nodeUuid);
        try {
            studyService.moveNetworkModifications(studyUuid, nodeUuid, nodeUuid, List.of(modificationUuid), beforeUuid, false, userId);
        } finally {
            studyService.unblockNodeTree(studyUuid, nodeUuid);
        }
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "For a list of network modifications passed in body, copy or cut, then append them to target node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification list has been updated.")})
    public ResponseEntity<Void> moveOrCopyModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestParam("action") ModificationsActionType action,
                                                         @RequestParam("originStudyUuid") UUID originStudyUuid,
                                                         @RequestParam("originNodeUuid") UUID originNodeUuid,
                                                         @RequestBody List<UUID> modificationsToCopyUuidList,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        studyService.assertIsStudyAndNodeExist(originStudyUuid, originNodeUuid);
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        switch (action) {
            case COPY, INSERT:
                handleDuplicateOrInsertNetworkModifications(studyUuid, nodeUuid, originStudyUuid, originNodeUuid, modificationsToCopyUuidList, userId, action);
                break;
            case MOVE:
                // we don't cut - paste modifications from different studies
                if (!studyUuid.equals(originStudyUuid)) {
                    throw new StudyException(MOVE_NETWORK_MODIFICATION_FORBIDDEN);
                }
                handleMoveNetworkModifications(studyUuid, nodeUuid, originNodeUuid, modificationsToCopyUuidList, userId);
                break;
        }
        return ResponseEntity.ok().build();
    }

    private void handleDuplicateOrInsertNetworkModifications(UUID targetStudyUuid, UUID targetNodeUuid, UUID originStudyUuid, UUID originNodeUuid, List<UUID> modificationsToCopyUuidList, String userId, ModificationsActionType action) {
        studyService.assertNoBlockedNodeInStudy(targetStudyUuid, targetNodeUuid);
        studyService.invalidateNodeTreeWithLF(targetStudyUuid, targetNodeUuid);
        try {
            studyService.duplicateOrInsertNetworkModifications(targetStudyUuid, targetNodeUuid, originStudyUuid, originNodeUuid, modificationsToCopyUuidList, userId, action);
        } finally {
            studyService.unblockNodeTree(targetStudyUuid, targetNodeUuid);
        }
    }

    private void handleMoveNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsToCopyUuidList, String userId) {
        studyService.assertNoBlockedNodeInStudy(studyUuid, originNodeUuid);
        studyService.assertNoBlockedNodeInStudy(studyUuid, targetNodeUuid);
        boolean isTargetInDifferentNodeTree = studyService.invalidateNodeTreeWhenMoveModifications(studyUuid, targetNodeUuid, originNodeUuid);
        try {
            studyService.moveNetworkModifications(studyUuid, targetNodeUuid, originNodeUuid, modificationsToCopyUuidList, null, isTargetInDifferentNodeTree, userId);
        } finally {
            studyService.unblockNodeTree(studyUuid, originNodeUuid);
            if (isTargetInDifferentNodeTree) {
                studyService.unblockNodeTree(studyUuid, targetNodeUuid);
            }
        }
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run")
    @Operation(summary = "run loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has started")})
    public ResponseEntity<Void> runLoadFlow(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestParam(value = "withRatioTapChangers", required = false, defaultValue = "false") boolean withRatioTapChangers,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertNoBlockedNodeInTree(nodeUuid, rootNetworkUuid);
        studyService.assertCanRunOnConstructionNode(studyUuid, nodeUuid, List.of(DYNA_FLOW_PROVIDER), studyService::getLoadFlowProvider);
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        if (prevResultUuid != null) {
            handleRerunLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, prevResultUuid, withRatioTapChangers, userId);
        } else {
            studyService.sendLoadflowRequest(studyUuid, nodeUuid, rootNetworkUuid, null, withRatioTapChangers, userId);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Need to have several transactions to send notifications by step
     * Disadvantage is that it is not atomic so need a try/catch to rollback
     */
    private void handleRerunLoadFlow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID prevResultUuid, Boolean withRatioTapChangers, String userId) {
        UUID loadflowResultUuid = null;
        try {
            studyService.deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, prevResultUuid);
            loadflowResultUuid = studyService.createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, withRatioTapChangers);
            studyService.rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
        } catch (Exception e) {
            if (loadflowResultUuid != null) {
                studyService.deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid);
            }
            throw e;
        }
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/result")
    @Operation(summary = "Get a loadflow result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow result"),
        @ApiResponse(responseCode = "204", description = "No loadflow has been done yet"),
        @ApiResponse(responseCode = "404", description = "The loadflow result has not been found")})
    public ResponseEntity<String> getLoadflowResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                    @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                    @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                    Sort sort) {
        String result = rootNetworkNodeInfoService.getLoadFlowResult(nodeUuid, rootNetworkUuid, filters, sort);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/status")
    @Operation(summary = "Get the loadflow status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status"),
        @ApiResponse(responseCode = "204", description = "No loadflow has been done yet"),
        @ApiResponse(responseCode = "404", description = "The loadflow status has not been found")})
    public ResponseEntity<String> getLoadFlowStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        LoadFlowStatus result = rootNetworkNodeInfoService.getLoadFlowStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result.name()) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/computation-infos")
    @Operation(summary = "Get the loadflow computation infos on study node and root network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow computation infos"),
        @ApiResponse(responseCode = "404", description = "The loadflow computation has not been found")})
    public ResponseEntity<LoadFlowComputationInfos> getLoadFlowComputationInfos(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkNodeInfoService.getLoadFlowComputationInfos(nodeUuid, rootNetworkUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/modifications")
    @Operation(summary = "Get the loadflow modifications on study node and root network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow computation infos"),
        @ApiResponse(responseCode = "404", description = "The loadflow computation has not been found")})
    public ResponseEntity<String> getLoadFlowModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkNodeInfoService.getLoadFlowModifications(nodeUuid, rootNetworkUuid));
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/stop")
    @Operation(summary = "stop loadflow on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has been stopped")})
    public ResponseEntity<Void> stopLoadFlow(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                             @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                             @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                             @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run")
    @Operation(summary = "run short circuit analysis on study")
    @ApiResponse(responseCode = "200", description = "The short circuit analysis has started")
    public ResponseEntity<Void> runShortCircuit(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestParam(value = "busId", required = false) Optional<String> busId,
            @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runShortCircuit(studyUuid, nodeUuid, rootNetworkUuid, busId, debug, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis has been stopped")})
    public ResponseEntity<Void> stopShortCircuitAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                         @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                         @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopShortCircuitAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result")
    @Operation(summary = "Get a short circuit analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis result"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis has not been found")})
    public ResponseEntity<String> getShortCircuitResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "root network UUID") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                        @Parameter(description = "node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @Parameter(description = "BASIC (faults without limits and feeders), " +
                                                            "FULL (faults with both), " +
                                                            "WITH_LIMIT_VIOLATIONS (like FULL but only those with limit violations) or " +
                                                            "NONE (no fault)") @RequestParam(name = "mode", required = false, defaultValue = "FULL") FaultResultsMode mode,
                                                        @Parameter(description = "type") @RequestParam(value = "type", required = false, defaultValue = "ALL_BUSES") ShortcircuitAnalysisType type,
                                                        @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                        @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                        @Parameter(description = "If we wanted the paged version of the results or not") @RequestParam(name = "paged", required = false, defaultValue = "false") boolean paged,
                                                        Pageable pageable) {
        String result = rootNetworkNodeInfoService.getShortCircuitAnalysisResult(new ResultParameters(rootNetworkUuid, nodeUuid), mode, type, filters, globalFilters, paged, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status")
    @Operation(summary = "Get the short circuit analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis status"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis status has not been found")})
    public ResponseEntity<String> getShortCircuitAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                @Parameter(description = "type") @RequestParam(value = "type", required = false, defaultValue = "ALL_BUSES") ShortcircuitAnalysisType type) {
        String result = rootNetworkNodeInfoService.getShortCircuitAnalysisStatus(nodeUuid, rootNetworkUuid, type);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result/csv")
    @Operation(summary = "Get a short circuit analysis csv result")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short circuit analysis csv export"),
        @ApiResponse(responseCode = "204", description = "No short circuit analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The short circuit analysis has not been found")})
    public ResponseEntity<byte[]> getShortCircuitAnalysisCsvResult(
            @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "type") @RequestParam(value = "type") ShortcircuitAnalysisType type,
            @Parameter(description = "headersCsv") @RequestBody String headersCsv) {
        return ResponseEntity.ok().body(rootNetworkNodeInfoService.getShortCircuitAnalysisCsvResult(nodeUuid, rootNetworkUuid, type, headersCsv));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min/result/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a pcc min result as csv")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Csv of pcc min results"),
        @ApiResponse(responseCode = "204", description = "No pcc min has been done yet"),
        @ApiResponse(responseCode = "404", description = "The pcc min has not been found")})
    public ResponseEntity<byte[]> exportPccMinResultsAsCsv(
        @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
        @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
        Sort sort, @RequestBody String csvHeaders) {
        byte[] result = rootNetworkNodeInfoService.exportPccMinResultsAsCsv(nodeUuid, rootNetworkUuid, csvHeaders, sort, filters, globalFilters);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDispositionFormData("attachment", "pcc_min_results.csv");

        return ResponseEntity
            .ok()
            .headers(responseHeaders)
            .body(result);
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/run")
    @Operation(summary = "run voltage init on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has started"),
        @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> runVoltageInit(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "debug") @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runVoltageInit(studyUuid, nodeUuid, rootNetworkUuid, userId, debug);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init has been stopped")})
    public ResponseEntity<Void> stopVoltageInit(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopVoltageInit(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/result")
    @Operation(summary = "Get a voltage init result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init result"),
        @ApiResponse(responseCode = "204", description = "No voltage init has been done yet"),
        @ApiResponse(responseCode = "404", description = "The voltage init has not been found")})
    public ResponseEntity<String> getVoltageInitResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters) {
        String result = studyService.getVoltageInitResult(nodeUuid, rootNetworkUuid, globalFilters);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/status")
    @Operation(summary = "Get the voltage init status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init status"),
        @ApiResponse(responseCode = "204", description = "No voltage init has been done yet"),
        @ApiResponse(responseCode = "404", description = "The voltage init status has not been found")})
    public ResponseEntity<String> getVoltageInitStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getVoltageInitStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/voltage-init/parameters")
    @Operation(summary = "Set voltage init parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setVoltageInitParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) StudyVoltageInitParameters voltageInitParameters,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setVoltageInitParameters(studyUuid, voltageInitParameters, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
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

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}")
    @Operation(summary = "export the study's network in the given format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network in the given format")})
    public ResponseEntity<UUID> exportNetwork(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("format") String format,
            @RequestParam(value = "formatParameters", required = false) String parametersJson,
            @RequestParam(value = "fileName") String fileName,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertRootNodeOrBuiltNode(studyUuid, nodeUuid, rootNetworkUuid);
        UUID exportUuid = studyService.exportNetwork(studyUuid, nodeUuid, rootNetworkUuid, fileName, format, userId, parametersJson);
        return ResponseEntity.ok().body(exportUuid);
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run")
    @Operation(summary = "run security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has started")})
    public ResponseEntity<Void> runSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        List<String> nonNullcontingencyListNames = contingencyListNames != null ? contingencyListNames : Collections.emptyList();
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runSecurityAnalysis(studyUuid, nonNullcontingencyListNames, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result")
    @Operation(summary = "Get a security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public ResponseEntity<String> getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                  @Parameter(description = "result type") @RequestParam(name = "resultType") SecurityAnalysisResultType resultType,
                                                                  @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                                  @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                                  Pageable pageable) {
        String result = rootNetworkNodeInfoService.getSecurityAnalysisResult(nodeUuid, rootNetworkUuid, resultType, filters, globalFilters, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
               ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv")
    @Operation(summary = "Get a security analysis result on study - CSV export")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis result csv export"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis has not been found")})
    public byte[] getSecurityAnalysisResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                           @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                           @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                           @Parameter(description = "result type") @RequestParam(name = "resultType") SecurityAnalysisResultType resultType,
                                                                           @Parameter(description = "Csv translation (JSON)") @RequestBody String csvTranslations) {
        return rootNetworkNodeInfoService.getSecurityAnalysisResultCsv(nodeUuid, rootNetworkUuid, resultType, csvTranslations);
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/contingency-count")
    @Operation(summary = "Get contingency count for a list of contingency list on a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The contingency count")})
    public ResponseEntity<Integer> getContingencyCount(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                             @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "Contingency list names") @RequestParam(name = "contingencyListName", required = false) List<String> contingencyListNames) {
        return ResponseEntity.ok().body(CollectionUtils.isEmpty(contingencyListNames) ? 0 : studyService.getContingencyCount(studyUuid, contingencyListNames, nodeUuid, rootNetworkUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/limit-violations")
    @Operation(summary = "Get limit violations.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The limit violations")})
    public ResponseEntity<List<LimitViolationInfos>> getLimitViolations(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                       @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                       @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                       Sort sort) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getLimitViolations(nodeUuid, rootNetworkUuid, filters, globalFilters, sort));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values")
    @Operation(summary = "Get Enum values")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The Enum values")})
    public ResponseEntity<List<String>> getResultEnumValues(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                    @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                    @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @Parameter(description = "Computing Type") @RequestParam(name = "computingType") ComputationType computingType,
                                                                    @Parameter(description = "Enum name") @RequestParam(name = "enumName") String enumName) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getResultEnumValues(nodeUuid, rootNetworkUuid, computingType, enumName));
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "set loadflow parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters are set"),
                           @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String lfParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertNoBlockedNodeInStudy(studyUuid, networkModificationTreeService.getStudyRootNodeUuid(studyUuid));
        return studyService.setLoadFlowParameters(studyUuid, lfParameter, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/parameters")
    @Operation(summary = "Get loadflow parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow parameters")})
    public ResponseEntity<LoadFlowParametersInfos> getLoadflowParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowParametersInfos(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/parameters/id")
    @Operation(summary = "Get loadflow parameters ID for study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The loadflow parameters ID"),
        @ApiResponse(responseCode = "404", description = "The study is not found")
    })
    public ResponseEntity<UUID> getLoadflowParametersId(@PathVariable("studyUuid") UUID studyUuid) {
        UUID parametersId = studyService.getLoadFlowParametersId(studyUuid);
        return ResponseEntity.ok().body(parametersId);
    }

    @PostMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "set load flow provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow provider is set")})
    public ResponseEntity<Void> setLoadflowProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                    @RequestBody(required = false) String provider,
                                                    @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertNoBlockedNodeInStudy(studyUuid, networkModificationTreeService.getStudyRootNodeUuid(studyUuid));
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
    @Operation(summary = "Get dynamic simulation provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation provider is returned")})
    public ResponseEntity<String> getDynamicSimulationProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getDynamicSimulationProvider(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/dynamic-security-analysis/provider")
    @Operation(summary = "Set dynamic security analysis provider for the specified study, no body means reset to default provider")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis provider is set")})
    public ResponseEntity<Void> setDynamicSecurityAnalysisProvider(@PathVariable("studyUuid") UUID studyUuid,
                                                               @RequestBody(required = false) String provider,
                                                               @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.updateDynamicSecurityAnalysisProvider(studyUuid, provider, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/dynamic-security-analysis/provider")
    @Operation(summary = "Get dynamic security analysis provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis provider is returned")})
    public ResponseEntity<String> getDynamicSecurityAnalysisProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getDynamicSecurityAnalysisProvider(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/short-circuit-analysis/parameters", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "set short-circuit analysis parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setShortCircuitParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String shortCircuitParametersInfos,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setShortCircuitParameters(studyUuid, shortCircuitParametersInfos, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/short-circuit-analysis/parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get short-circuit analysis parameters on study")
    @ApiResponse(responseCode = "200", description = "The short-circuit analysis parameters return by shortcircuit-server")
    public ResponseEntity<String> getShortCircuitParameters(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getShortCircuitParametersInfo(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg"),
        @ApiResponse(responseCode = "404", description = "The substation has not been found")})
    public ResponseEntity<byte[]> generateSubstationDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        byte[] result = studyService.generateSubstationSvg(substationId, nodeUuid, rootNetworkUuid, sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg and metadata"),
        @ApiResponse(responseCode = "404", description = "The substation has not been found")})
    public ResponseEntity<String> generateSubstationDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        String result = studyService.generateSubstationSvgAndMetadata(substationId, nodeUuid, rootNetworkUuid, sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "get the network area diagram for the given network and voltage levels")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<String> generateNetworkAreaDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody Map<String, Object> nadRequestInfos) {
        String result = studyService.generateNetworkAreaDiagram(nodeUuid, rootNetworkUuid, nadRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/status")
    @Operation(summary = "Get the security analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis status"),
        @ApiResponse(responseCode = "204", description = "No security analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The security analysis status has not been found")})
    public ResponseEntity<String> getSecurityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        SecurityAnalysisStatus status = rootNetworkNodeInfoService.getSecurityAnalysisStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status.name()) :
                ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/stop")
    @Operation(summary = "stop security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis has been stopped")})
    public ResponseEntity<Void> stopSecurityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopSecurityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get node report with its parent nodes")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<Report>> getParentNodesReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                    @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                    @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @Parameter(description = "Node only report") @RequestParam(value = "nodeOnlyReport", required = false, defaultValue = "true") boolean nodeOnlyReport,
                                                                    @Parameter(description = "The report Type") @RequestParam(name = "reportType") StudyService.ReportType reportType,
                                                                    @Parameter(description = "Severity levels") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getParentNodesReport(nodeUuid, rootNetworkUuid, nodeOnlyReport, reportType, severityLevels));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the report logs of the given node and all its parents")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report logs of the node and all its parent"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<ReportPage> getReportLogs(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                    @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                    @Parameter(description = "node id") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                    @Parameter(description = "report id") @RequestParam(name = "reportId", required = false) UUID reportId,
                                                                    @Parameter(description = "The message filter") @RequestParam(name = "message", required = false) String messageFilter,
                                                                    @Parameter(description = "Severity levels filter") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels,
                                                                    @Parameter(description = "If we wanted the paged version of the results or not") @RequestParam(name = "paged", required = false, defaultValue = "false") boolean paged,
                                                                    Pageable pageable) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getReportLogs(nodeUuid, rootNetworkUuid, reportId, messageFilter, severityLevels, paged, pageable));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get search term matches in parent nodes filtered logs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The search term matches in the parent nodes filtered logs"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")
    })
    public ResponseEntity<String> getSearchTermMatchesInFilteredLogs(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "node id") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "report id") @RequestParam(name = "reportId", required = false) UUID reportId,
            @Parameter(description = "The message filter") @RequestParam(name = "message", required = false) String messageFilter,
            @Parameter(description = "Severity levels filter") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels,
            @Parameter(description = "The search term") @RequestParam(name = "searchTerm") String searchTerm,
            @Parameter(description = "Rows per page") @RequestParam(name = "pageSize") int pageSize
    ) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                studyService.getSearchTermMatchesInFilteredLogs(nodeUuid, rootNetworkUuid, reportId, severityLevels, messageFilter, searchTerm, pageSize));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/aggregated-severities", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the report severities of the given node and all its parents")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report severities of the node and all its parent"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Set<String>> getParentNodesAggregatedReportSeverities(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                       @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                       @Parameter(description = "node id") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                       @Parameter(description = "reportId") @RequestParam(name = "reportId", required = false) UUID reportId) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getAggregatedReportSeverities(nodeUuid, rootNetworkUuid, reportId));
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
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(networkModificationTreeService.getNetworkModifications(nodeUuid, onlyStashed, onlyMetadata));
    }

    @GetMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/excluded-network-modifications", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get excluded network modifications from a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The excluded network modifications were returned"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity< List<ExcludedNetworkModifications>> getNetworkModificationsToExclude(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                       @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {

        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationTreeService.getModificationsToExclude(nodeUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications")
    @Operation(summary = "Create a network modification for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was created"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> createNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeUuid);
        handleCreateNetworkModification(studyUuid, nodeUuid, modificationAttributes, userId);
        return ResponseEntity.ok().build();
    }

    private void handleCreateNetworkModification(UUID studyUuid, UUID nodeUuid, String modificationAttributes, String userId) {
        studyService.invalidateNodeTreeWithLF(studyUuid, nodeUuid);
        try {
            studyService.createNetworkModification(studyUuid, nodeUuid, modificationAttributes, userId);
        } finally {
            studyService.unblockNodeTree(studyUuid, nodeUuid);
        }
    }

    @PutMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications/{uuid}")
    @Operation(summary = "Update a modification in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was updated"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Network modification UUID") @PathVariable("uuid") UUID networkModificationUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeUuid);
        studyService.updateNetworkModification(studyUuid, modificationAttributes, nodeUuid, networkModificationUuid, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications")
    @Operation(summary = "Delete network modifications for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications was deleted"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                           @Parameter(description = "Network modification UUIDs") @RequestParam(name = "uuids", required = false) List<UUID> networkModificationUuids,
                                                           @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.deleteNetworkModifications(studyUuid, nodeUuid, networkModificationUuids, userId);

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
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeUuid);
        if (stashed.booleanValue()) {
            studyService.stashNetworkModifications(studyUuid, nodeUuid, networkModificationUuids, userId);
        } else {
            studyService.restoreNetworkModifications(studyUuid, nodeUuid, networkModificationUuids, userId);
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
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeUuid);
        studyService.updateNetworkModificationsActivation(studyUuid, nodeUuid, networkModificationUuids, userId, activated);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", params = "activated")
    @Operation(summary = "Update 'activated' value for a network modifications for a node in a specific root network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Update the activation status for network modifications on a node in a specific root network"), @ApiResponse(responseCode = "404", description = "The study/root network/node is not found")})
    public ResponseEntity<Void> updateNetworkModificationsActivation(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                     @Parameter(description = "Root network UUID") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                     @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                     @Parameter(description = "Network modification UUIDs") @RequestParam("uuids") Set<UUID> networkModificationUuids,
                                                                     @Parameter(description = "New activated value") @RequestParam(name = "activated") Boolean activated,
                                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.assertNoBuildNoComputationForRootNetworkNode(nodeUuid, rootNetworkUuid);
        studyService.assertNoBlockedNodeInTree(nodeUuid, rootNetworkUuid);
        studyService.updateNetworkModificationsActivationInRootNetwork(studyUuid, nodeUuid, rootNetworkUuid, networkModificationUuids, userId, activated);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search studies in elasticsearch")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of studies found")})
    public ResponseEntity<List<CreatedStudyBasicInfos>> searchStudies(@Parameter(description = "Lucene query") @RequestParam(value = "q") String query) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.searchStudies(query));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search equipments in elasticsearch")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of equipments found"),
        @ApiResponse(responseCode = "404", description = "The study not found"),
        @ApiResponse(responseCode = "400", description = "The fieLd selector is unknown")
    })
    public ResponseEntity<List<EquipmentInfos>> searchEquipments(
        @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
        @Parameter(description = "User input") @RequestParam(value = "userInput") String userInput,
        @Parameter(description = "What against to match") @RequestParam(value = "fieldSelector") EquipmentInfosService.FieldSelector fieldSelector,
        @Parameter(description = "Should search in upstream built node") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
        @Parameter(description = "Equipment type") @RequestParam(value = "equipmentType", required = false) String equipmentType) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(studyService.searchEquipments(nodeUuid, rootNetworkUuid, userInput, fieldSelector, equipmentType, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/modifications/indexation-infos", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search modifications in elasticsearch by equipment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of modifications found"),
        @ApiResponse(responseCode = "404", description = "The study not found"),
    })
    public ResponseEntity<List<ModificationsSearchResultByNode>> searchModifications(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "User input") @RequestParam(value = "userInput") String userInput) {
        studyService.assertIsStudyExist(studyUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(studyService.searchModifications(rootNetworkUuid, userInput));
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

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes/{id}", params = {"sequenceType"})
    @Operation(summary = "Create a node sequence after the given node ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node sequence has been added"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<NetworkModificationNode> createSequence(
                                                              @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "parent id of the node created") @PathVariable(name = "id") UUID referenceId,
                                                              @Parameter(description = "sequence to create") @RequestParam("sequenceType") NodeSequenceType nodeSequenceType,
                                                              @RequestHeader(HEADER_USER_ID) String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.createSequence(studyUuid, referenceId, nodeSequenceType, userId));
    }

    @DeleteMapping(value = "/studies/{studyUuid}/tree/nodes")
    @Operation(summary = "Delete node with given ids")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Nodes have been successfully deleted"),
        @ApiResponse(responseCode = "404", description = "The study or the nodes not found")})
    public ResponseEntity<Void> deleteNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                           @Parameter(description = "ids of children to remove") @RequestParam("ids") List<UUID> nodeIds,
                                           @Parameter(description = "deleteChildren") @RequestParam(value = "deleteChildren", defaultValue = "false") boolean deleteChildren,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        nodeIds.stream().forEach(nodeId -> studyService.assertNoBlockedNodeInStudy(studyUuid, nodeId));
        studyService.deleteNodes(studyUuid, nodeIds, deleteChildren, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes/{id}/stash")
    @Operation(summary = "Move to trash the node with given id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node has been successfully moved to trash"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> stashNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @Parameter(description = "id of child to delete (move to trash)") @PathVariable("id") UUID nodeId,
                                                 @Parameter(description = "to stash a node with its children") @RequestParam(value = "stashChildren", defaultValue = "false") boolean stashChildren,
                                                 @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeId);
        studyService.stashNode(studyUuid, nodeId, stashChildren, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/tree/nodes/stash")
    @Operation(summary = "Get the list of nodes in the trash for a given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The list of nodes in the trash")})
    public ResponseEntity<List<Pair<AbstractNode, Integer>>> getStashedNodes(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getStashedNodes(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/tree/nodes/restore")
    @Operation(summary = "restore nodes below the given anchor node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The list of nodes in the trash")})
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
    public ResponseEntity<RootNode> getNetworkModificationTree(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "root network uuid") @RequestParam(value = "rootNetworkUuid", required = false) UUID rootNetworkUuid) {
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, rootNetworkUuid);
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
                                                                 @Parameter(description = "parent node uuid") @RequestParam(value = "parentNodeUuid") UUID parentNodeUuid,
                                                                 @Parameter(description = "root network uuid") @RequestParam(value = "rootNetworkUuid", required = false) UUID rootNetworkUuid) {
        NetworkModificationNode parentNode = (NetworkModificationNode) networkModificationTreeService.getStudySubtree(studyUuid, parentNodeUuid, rootNetworkUuid);
        return parentNode != null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(parentNode)
                : ResponseEntity.notFound().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/tree/nodes")
    @Operation(summary = "update node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node has been updated"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> updateNode(@RequestBody NetworkModificationNode node,
                                                 @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationTreeService.updateNode(studyUuid, node, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/tree/nodes/{parentUuid}/children-column-positions")
    @Operation(summary = "update children column positions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node column positions have been updated"),
        @ApiResponse(responseCode = "404", description = "The study or a node was not found")})
    public ResponseEntity<Void> updateNodesColumnPositions(@RequestBody List<NetworkModificationNode> children,
                                                 @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                 @Parameter(description = "parent node uuid") @PathVariable("parentUuid") UUID parentUuid,
                                                 @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationTreeService.updateNodesColumnPositions(studyUuid, parentUuid, children, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/tree/nodes/{id}")
    @Operation(summary = "get simplified node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "simplified nodes (without children"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<AbstractNode> getNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "node uuid") @PathVariable("id") UUID nodeId,
                                                @Parameter(description = "root network uuid") @RequestParam(value = "rootNetworkUuid", required = false) UUID rootNetworkUuid) {
        AbstractNode node = networkModificationTreeService.getNode(nodeId, rootNetworkUuid);
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

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/build")
    @Operation(summary = "build a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been built"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
                           @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> buildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                          @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertNoBlockedNodeInTree(nodeUuid, rootNetworkUuid);
        studyService.assertNoBuildNoComputationForRootNetworkNode(nodeUuid, rootNetworkUuid);
        studyService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/unbuild")
    @Operation(summary = "unbuild a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been unbuilt"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
        @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> unbuildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.assertNoBlockedNodeInTree(nodeUuid, rootNetworkUuid);
        studyService.unbuildStudyNode(studyUuid, nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nodes/unbuild-all")
    @Operation(summary = "unbuild all study nodes in all root networks")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All study nodes has been unbuilt in all root networks"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Void> unbuildAllNodes(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        try {
            studyService.assertNoBlockedNodeInStudy(studyUuid, rootNodeUuid);
            studyService.unbuildNodeTree(studyUuid, rootNodeUuid, true);
        } finally {
            studyService.unblockNodeTree(studyUuid, rootNodeUuid);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/build/stop")
    @Operation(summary = "stop a node build")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The build has been stopped"),
                           @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Void> stopBuild(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                      @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                      @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.stopBuild(nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/loadflow-default-provider")
    @Operation(summary = "get load flow default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The load flow default provider has been found"))
    public ResponseEntity<String> getDefaultLoadflowProvider(
        @RequestHeader(name = HEADER_USER_ID, required = false) String userId // not required to allow to query the system default provider without a user
    ) {
        return ResponseEntity.ok().body(studyService.getDefaultLoadflowProvider(userId));
    }

    @GetMapping(value = "/security-analysis-default-provider")
    @Operation(summary = "get security analysis default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The security analysis default provider has been found"))
    public ResponseEntity<String> getDefaultSecurityAnalysisProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultSecurityAnalysisProvider());
    }

    @GetMapping(value = "/sensitivity-analysis-default-provider")
    @Operation(summary = "get sensitivity analysis default provider value")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The sensitivity analysis default provider has been found"))
    public ResponseEntity<String> getDefaultSensitivityAnalysisProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultSensitivityAnalysisProvider());
    }

    @GetMapping(value = "/dynamic-simulation-default-provider")
    @Operation(summary = "Get dynamic simulation default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The dynamic simulation default provider has been found"))
    public ResponseEntity<String> getDefaultDynamicSimulationProvider() {
        return ResponseEntity.ok().body(studyService.getDefaultDynamicSimulationProvider());
    }

    @GetMapping(value = "/dynamic-security-analysis-default-provider")
    @Operation(summary = "Get dynamic security analysis default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The dynamic security analysis default provider has been found"))
    public ResponseEntity<String> getDefaultDynamicSecurityAnalysisProvider(@RequestHeader(HEADER_USER_ID) String userId) {
        return ResponseEntity.ok().body(studyService.getDefaultDynamicSecurityAnalysisProvider(userId));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all")
    @Operation(summary = "reindex root network")
    @ApiResponse(responseCode = "200", description = "Root network reindexed")
    public ResponseEntity<Void> reindexRootNetwork(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                             @Parameter(description = "root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        studyService.reindexRootNetwork(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/notification")
    @Operation(summary = "Create study related notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The notification has been sent"),
    })
    public ResponseEntity<Void> notify(@PathVariable("studyUuid") UUID studyUuid) {
        studyService.notify(studyUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run")
    @Operation(summary = "run sensitivity analysis on study")
        @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has started"), @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> runSensitivityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runSensitivityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result")
    @Operation(summary = "Get a sensitivity analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisResult(
        @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "results selector") @RequestParam("selector") String selector,
        @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
        @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters
    ) {
        String result = rootNetworkNodeInfoService.getSensitivityAnalysisResult(nodeUuid, rootNetworkUuid, selector, filters, globalFilters);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a sensitivity analysis result as csv")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Csv of sensitivity analysis results"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<byte[]> exportSensitivityResultsAsCsv(
        @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "results selector") @RequestParam("selector") String selector,
        @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
        @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
        @RequestBody SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        byte[] result = rootNetworkNodeInfoService.exportSensitivityResultsAsCsv(nodeUuid, rootNetworkUuid, sensitivityAnalysisCsvFileInfos, selector, filters, globalFilters);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        responseHeaders.setContentDispositionFormData("attachment", "sensitivity_results.csv");

        return ResponseEntity
                .ok()
                .headers(responseHeaders)
                .body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options")
    @Operation(summary = "Get sensitivity analysis filter options on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis filter options"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisFilterOptions(
        @Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "results selector") @RequestParam("selector") String selector) {
        String result = rootNetworkNodeInfoService.getSensitivityResultsFilterOptions(nodeUuid, rootNetworkUuid, selector);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/status")
    @Operation(summary = "Get the sensitivity analysis status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis status"),
        @ApiResponse(responseCode = "204", description = "No sensitivity analysis has been done yet"),
        @ApiResponse(responseCode = "404", description = "The sensitivity analysis status has not been found")})
    public ResponseEntity<String> getSensitivityAnalysisStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                               @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getSensitivityAnalysisStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/stop")
    @Operation(summary = "stop sensitivity analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has been stopped")})
    public ResponseEntity<Void> stopSensitivityAnalysis(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                        @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                        @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkNodeInfoService.stopSensitivityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId);
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

    @GetMapping(value = "/studies/{studyUuid}/dynamic-simulation/models")
    @Operation(summary = "Get models of dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All models of dynamic simulation"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation models"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation models has not been found")})
    public ResponseEntity<List<ModelInfos>> getDynamicSimulationModels(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid) {
        List<ModelInfos> models = studyService.getDynamicSimulationModels(studyUuid);
        return models != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(models) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/dynamic-simulation/parameters")
    @Operation(summary = "Set dynamic simulation parameters on study, reset to default ones if empty body")
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
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
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
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
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
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.deleteDynamicSimulationEvents(studyUuid, nodeUuid, userId, eventUuids);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-simulation/run")
    @Operation(summary = "run dynamic simulation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic simulation has started")})
    public ResponseEntity<Void> runDynamicSimulation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @Parameter(description = "debug") @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
                                                     @RequestBody(required = false) DynamicSimulationParametersInfos parameters,
                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertCanRunOnConstructionNode(studyUuid, nodeUuid, List.of(DYNAWO_PROVIDER), studyService::getDynamicSimulationProvider);
        studyService.runDynamicSimulation(studyUuid, nodeUuid, rootNetworkUuid, parameters, userId, debug);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeseries/metadata")
    @Operation(summary = "Get list of time series metadata of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Time series metadata of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation metadata"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<TimeSeriesMetadataInfos>> getDynamicSimulationTimeSeriesMetadata(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                                @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<TimeSeriesMetadataInfos> result = rootNetworkNodeInfoService.getDynamicSimulationTimeSeriesMetadata(nodeUuid, rootNetworkUuid);
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeseries")
    @Operation(summary = "Get all time series of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All time series of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation timeseries"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<DoubleTimeSeries>> getDynamicSimulationTimeSeriesResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                                       @Parameter(description = "timeSeriesNames") @RequestParam(name = "timeSeriesNames", required = false) List<String> timeSeriesNames) {
        List<DoubleTimeSeries> result = rootNetworkNodeInfoService.getDynamicSimulationTimeSeries(nodeUuid, rootNetworkUuid, timeSeriesNames);
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-simulation/result/timeline")
    @Operation(summary = "Get timeline events of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Timeline events of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation timeline events"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<List<TimelineEventInfos>> getDynamicSimulationTimelineResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                       @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                       @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        List<TimelineEventInfos> result = rootNetworkNodeInfoService.getDynamicSimulationTimeline(nodeUuid, rootNetworkUuid);
        return CollectionUtils.isEmpty(result) ? ResponseEntity.noContent().build() :
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-simulation/status")
    @Operation(summary = "Get the status of dynamic simulation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The status of dynamic simulation result"),
        @ApiResponse(responseCode = "204", description = "No dynamic simulation status"),
        @ApiResponse(responseCode = "404", description = "The dynamic simulation has not been found")})
    public ResponseEntity<String> getDynamicSimulationStatus(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                             @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        DynamicSimulationStatus result = rootNetworkNodeInfoService.getDynamicSimulationStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result.name()) :
                ResponseEntity.noContent().build();
    }

    // --- Dynamic Simulation Endpoints END --- //

    // --- Dynamic Security Analysis Endpoints BEGIN --- //

    @PostMapping(value = "/studies/{studyUuid}/dynamic-security-analysis/parameters")
    @Operation(summary = "Set dynamic security analysis parameters on study, reset to default one if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis parameters are set")})
    public ResponseEntity<Void> setDynamicSecurityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String dsaParameter,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setDynamicSecurityAnalysisParameters(studyUuid, dsaParameter, userId) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/dynamic-security-analysis/parameters")
    @Operation(summary = "Get dynamic security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis parameters")})
    public ResponseEntity<String> getDynamicSecurityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getDynamicSecurityAnalysisParameters(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-security-analysis/run")
    @Operation(summary = "run dynamic security analysis on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The dynamic security analysis has started")})
    public ResponseEntity<Void> runDynamicSecurityAnalysis(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                     @Parameter(description = "debug") @RequestParam(name = "debug", required = false, defaultValue = "false") boolean debug,
                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.assertCanRunOnConstructionNode(studyUuid, nodeUuid, List.of(DYNAWO_PROVIDER), studyService::getDynamicSecurityAnalysisProvider);
        studyService.runDynamicSecurityAnalysis(studyUuid, nodeUuid, rootNetworkUuid, userId, debug);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-security-analysis/status")
    @Operation(summary = "Get the status of dynamic security analysis result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The status of dynamic security analysis result"),
        @ApiResponse(responseCode = "204", description = "No dynamic security analysis status"),
        @ApiResponse(responseCode = "404", description = "The dynamic security analysis has not been found")})
    public ResponseEntity<String> getDynamicSecurityAnalysisStatus(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                          @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        DynamicSecurityAnalysisStatus result = rootNetworkNodeInfoService.getDynamicSecurityAnalysisStatus(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result.name()) :
                ResponseEntity.noContent().build();
    }

    // --- Dynamic Security Analysis Endpoints END --- //

    @GetMapping(value = "/studies/{studyUuid}/security-analysis/parameters")
    @Operation(summary = "Get security analysis parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis parameters")})
    public ResponseEntity<String> getSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getSecurityAnalysisParametersValues(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/security-analysis/parameters")
    @Operation(summary = "set security analysis parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The security analysis parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setSecurityAnalysisParametersValues(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String securityAnalysisParametersValues,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setSecurityAnalysisParametersValues(studyUuid, securityAnalysisParametersValues, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications/voltage-init", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the voltage init modifications from a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init modifications was returned"), @ApiResponse(responseCode = "404", description = "The study/node is not found, or has no voltage init result")})
    public ResponseEntity<String> getVoltageInitModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                              @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getVoltageInitModifications(nodeUuid, rootNetworkUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications/voltage-init")
    @Operation(summary = "Clone the voltage init modifications, then append them to node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init modifications have been appended.")})
    public ResponseEntity<Void> insertVoltageInitModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                               @PathVariable("nodeUuid") UUID nodeUuid,
                                                               @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        studyService.assertCanUpdateModifications(studyUuid, nodeUuid);
        studyService.assertNoBlockedNodeInStudy(studyUuid, nodeUuid);
        studyService.insertVoltageInitModifications(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/network-visualizations/parameters")
    @Operation(summary = "Get network visualization parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network visualization parameters")})
    public ResponseEntity<String> getNetworkVisualizationParametersValues(
            @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getNetworkVisualizationParametersValues(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/network-visualizations/parameters")
    @Operation(summary = "set network visualization parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network visualization parameters are set")})
    public ResponseEntity<Void> setNetworkVisualizationParametersValues(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String networkVisualizationParametersValues,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setNetworkVisualizationParametersValues(studyUuid, networkVisualizationParametersValues, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/network-visualizations/nad-positions-config", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "create a nad positions configuration using data from a csv")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The nad positions configuration created")})
    public ResponseEntity<Void> createNadPositionsConfigFromCsv(@RequestParam("file") MultipartFile file) {
        studyService.createNadPositionsConfigFromCsv(file);
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
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setSensitivityAnalysisParameters(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody(required = false) String sensitivityAnalysisParameters,
            @RequestHeader(HEADER_USER_ID) String userId) {
        return studyService.setSensitivityAnalysisParameters(studyUuid, sensitivityAnalysisParameters, userId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/factors-count")
    @Operation(summary = "Get the factors count of sensitivity parameters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The factors count of sensitivity parameters")})
    public ResponseEntity<Long> getSensitivityAnalysisFactorsCount(
            @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Is Injections Set") @RequestParam(name = "isInjectionsSet", required = false) Boolean isInjectionsSet,
            SensitivityFactorsIdsByGroup factorsIds) {
        return ResponseEntity.ok().body(sensitivityAnalysisService.getSensitivityAnalysisFactorsCount(rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid), factorsIds, isInjectionsSet));
    }

    @GetMapping(value = "/servers/infos")
    @Operation(summary = "Get the information of all backend servers (if not filter with view parameter)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The information on all known servers"),
        @ApiResponse(responseCode = "207", description = "Partial result because some servers haven't responded or threw an error"),
        @ApiResponse(responseCode = "424", description = "All requests have failed, no information retrieved")})
    public ResponseEntity<Map<String, JsonNode>> getSuiteServersInformation(
            @Parameter(description = "The view which will be used to filter the returned services") @RequestParam final Optional<FrontService> view
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
            @Parameter(description = "The view which will be used to filter the returned services") @RequestParam final Optional<FrontService> view
    ) {
        final ResponseEntity<Map<String, JsonNode>> suiteServersInfo = this.getSuiteServersInformation(view);
        return ResponseEntity.status(suiteServersInfo.getStatusCode()).body(
                remoteServicesInspector.convertServicesInfoToAboutInfo(Objects.requireNonNullElseGet(suiteServersInfo.getBody(), Map::of)));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/evaluate")
    @Operation(summary = "Evaluate a filter to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> evaluateFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @RequestBody String filter) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.evaluateFilter(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, filter));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/global-filter/evaluate",
        produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Evaluate a global filter to get matched elements")
    @ApiResponse(responseCode = "200", description = "The list of matched elements")
    public ResponseEntity<List<String>> evaluateGlobalFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "The equipments types to filter and return") @RequestParam(name = "equipmentTypes") @NonNull final List<EquipmentType> equipmentTypes,
            @RequestBody @NonNull GlobalFilter filter) {
        this.studyService.assertIsRootNetworkAndNodeInStudy(studyUuid, rootNetworkUuid, nodeUuid);
        return ResponseEntity.ok(studyService.evaluateGlobalFilter(nodeUuid, rootNetworkUuid, equipmentTypes, filter));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/filters/{filterUuid}/elements")
    @Operation(summary = "Evaluate a filter on root node to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> exportFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Filter uuid to be applied") @PathVariable("filterUuid") UUID filterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.exportFilter(rootNetworkUuid, filterUuid));
    }

    // temporary - used by grid-explore only to prevent filter conversion from dysfunctioning since it does not have access to root networks yet
    @GetMapping(value = "/studies/{studyUuid}/filters/{filterUuid}/elements")
    @Operation(summary = "Evaluate a filter on root node and first root network of study to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> exportFilterFromFirstRootNetwork(
        @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "Filter uuid to be applied") @PathVariable("filterUuid") UUID filterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.exportFilterFromFirstRootNetwork(studyUuid, filterUuid));
    }

    // temporary - used by grid-explore only to prevent filter conversion from dysfunctioning since it does not have access to root networks yet
    @PostMapping(value = "/studies/{studyUuid}/filters/elements")
    @Operation(summary = "Evaluate filters list on first root network of study to get matched identifiables elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched identifiables elements")})
    public ResponseEntity<String> evaluateFiltersOnFirstRootNetwork(
        @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
        // the body should match FiltersWithEquipmentTypes in filter-server
        @Parameter(description = "Filters to evaluate") @RequestBody String filters) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.evaluateFiltersFromFirstRootNetwork(studyUuid, filters));
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/elements")
    @Operation(summary = "Evaluate a list of filters on root node to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> exportFilters(
        @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
        @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
        @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
        @Parameter(description = "Filters uuid to be resolved") @RequestParam("filtersUuid") List<UUID> filtersUuid,
        @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.exportFilters(rootNetworkUuid, filtersUuid, nodeUuid, inUpstreamBuiltParentNode));
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/state-estimation/run")
    @Operation(summary = "run state estimation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation has started")})
    public ResponseEntity<Void> runStateEstimation(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                    @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                    @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runStateEstimation(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min/run")
    @Operation(summary = "run pcc min on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min has started")})
    public ResponseEntity<Void> runPccMin(@Parameter(description = "studyUuid") @PathVariable("studyUuid") UUID studyUuid,
                                                    @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                          @RequestHeader(HEADER_USER_ID) String userId) {

        studyService.assertIsNodeNotReadOnly(nodeUuid);
        studyService.runPccMin(studyUuid, nodeUuid, rootNetworkUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min/stop")
    @Operation(summary = "stop pcc min on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min has been stopped")})
    public ResponseEntity<Void> stopPccMin(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                    @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                    @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        rootNetworkNodeInfoService.stopPccMin(studyUuid, nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min/result")
    @Operation(summary = "Get a pcc min result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min result"),
        @ApiResponse(responseCode = "204", description = "No pcc min  has been done yet"),
        @ApiResponse(responseCode = "404", description = "The pcc min  has not been found")})
    public ResponseEntity<String> getPccMinResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                  @Parameter(description = "rootNetwork Uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @Parameter(description = "node Uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                  @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                  @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                  Pageable pageable) {
        String result = rootNetworkNodeInfoService.getPccMinResult(nodeUuid, rootNetworkUuid, filters, globalFilters, pageable);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/state-estimation/result")
    @Operation(summary = "Get a state estimation result on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation result"),
        @ApiResponse(responseCode = "204", description = "No state estimation has been done yet"),
        @ApiResponse(responseCode = "404", description = "The state estimation has not been found")})
    public ResponseEntity<String> getStateEstimationResult(@Parameter(description = "study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String result = rootNetworkNodeInfoService.getStateEstimationResult(nodeUuid, rootNetworkUuid);
        return result != null ? ResponseEntity.ok().body(result) :
            ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/state-estimation/status")
    @Operation(summary = "Get the state estimation status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation status"),
        @ApiResponse(responseCode = "204", description = "No state estimation has been done yet"),
        @ApiResponse(responseCode = "404", description = "The state estimation status has not been found")})
    public ResponseEntity<String> getStateEstimationStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = rootNetworkNodeInfoService.getStateEstimationStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min/status")
    @Operation(summary = "Get the pcc min status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min status"),
        @ApiResponse(responseCode = "204", description = "No pcc min has been done yet"),
        @ApiResponse(responseCode = "404", description = "The pcc min status has not been found")})
    public ResponseEntity<String> getPccMinStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                  @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        String status = rootNetworkNodeInfoService.getPccMinStatus(nodeUuid, rootNetworkUuid);
        return status != null ? ResponseEntity.ok().body(status) : ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/state-estimation/stop")
    @Operation(summary = "stop state estimation on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation has been stopped")})
    public ResponseEntity<Void> stopStateEstimation(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                     @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                     @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        rootNetworkNodeInfoService.stopStateEstimation(studyUuid, nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/state-estimation/parameters")
    @Operation(summary = "Get state estimation parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation parameters")})
    public ResponseEntity<String> getStateEstimationParametersValues(
        @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getStateEstimationParameters(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/state-estimation/parameters")
    @Operation(summary = "set state estimation parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The state estimation parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setStateEstimationParametersValues(
        @PathVariable("studyUuid") UUID studyUuid,
        @RequestBody(required = false) String stateEstimationParametersValues,
        @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setStateEstimationParametersValues(studyUuid, stateEstimationParametersValues, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/node-aliases")
    @Operation(summary = "Get node aliases attached to a given study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study's attached aliases")})
    public ResponseEntity<List<NodeAlias>> getNodeAliases(
        @PathVariable("studyUuid") UUID studyUuid) {
        studyService.assertIsStudyExist(studyUuid);
        return ResponseEntity.ok().body(studyService.getNodeAliases(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/node-aliases")
    @Operation(summary = "Update node aliases attached to a given study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Node aliases have been updated"), @ApiResponse(responseCode = "404", description = "Study doesn't exists")})
    public ResponseEntity<Void> setNodeAliases(
        @PathVariable("studyUuid") UUID studyUuid,
        @RequestBody List<NodeAlias> nodeAliases) {
        studyService.assertIsStudyExist(studyUuid);
        studyService.updateNodeAliases(studyUuid, nodeAliases);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/studies/{studyUuid}/loadflow/provider")
    @Operation(summary = "Get loadflow provider for a specified study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow provider is returned")})
    public ResponseEntity<String> getLoadFlowProvider(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getLoadFlowProvider(studyUuid));
    }

    @GetMapping(value = "/studies/{studyUuid}/diagram-grid-layout")
    @Operation(summary = "Get diagram grid layout of a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Diagram grid layout is returned"), @ApiResponse(responseCode = "404", description = "Study doesn't exists")})
    public ResponseEntity<DiagramGridLayout> getDiagramGridLayout(
        @PathVariable("studyUuid") UUID studyUuid) {
        studyService.assertIsStudyExist(studyUuid);
        DiagramGridLayout diagramGridLayout = studyService.getDiagramGridLayout(studyUuid);
        return diagramGridLayout != null ? ResponseEntity.ok().body(diagramGridLayout) : ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/diagram-grid-layout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save diagram grid layout of a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Diagram grid layout is saved"), @ApiResponse(responseCode = "404", description = "Study doesn't exists")})
    public ResponseEntity<UUID> saveDiagramGridLayout(
        @PathVariable("studyUuid") UUID studyUuid,
        @RequestBody DiagramGridLayout diagramGridLayout) {
        studyService.assertIsStudyExist(studyUuid);

        return ResponseEntity.ok().body(studyService.saveDiagramGridLayout(studyUuid, diagramGridLayout));
    }

    @GetMapping(value = "/studies/{studyUuid}/spreadsheet/parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get global parameters of the spreadsheets")
    @ApiResponse(responseCode = "200", description = "Get the parameters")
    @ApiResponse(responseCode = "204", description = "The study does not exist")
    public ResponseEntity<SpreadsheetParameters> getSpreadsheetParameters(@PathVariable("studyUuid") final UUID studyUuid) {
        return ResponseEntity.of(this.studyService.getSpreadsheetParameters(studyUuid));
    }

    @PutMapping(value = "/studies/{studyUuid}/spreadsheet/parameters", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update global parameters of the spreadsheets")
    @ApiResponse(responseCode = "204", description = "The parameters are updated")
    @ApiResponse(responseCode = "404", description = "The study does not exist")
    public ResponseEntity<Void> updateSpreadsheetParameters(@PathVariable("studyUuid") final UUID studyUuid,
                                                            @RequestBody final SpreadsheetParameters spreadsheetParameters) {
        return (this.studyService.updateSpreadsheetParameters(studyUuid, spreadsheetParameters) ? ResponseEntity.noContent() : ResponseEntity.notFound()).build();
    }

    @GetMapping(value = "/studies/{studyUuid}/pcc-min/parameters")
    @Operation(summary = "Get pcc min parameters on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min parameters")})
    public ResponseEntity<String> getPccMinParameters(
        @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(studyService.getPccMinParameters(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/pcc-min/parameters")
    @Operation(summary = "set pcc min parameters on study, reset to default ones if empty body")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The pcc min parameters are set"),
        @ApiResponse(responseCode = "204", description = "Reset with user profile cannot be done")})
    public ResponseEntity<Void> setPccMinParameters(
        @PathVariable("studyUuid") UUID studyUuid,
        @RequestBody(required = false) String pccMinParametersInfos,
        @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.setPccMinParameters(studyUuid, pccMinParametersInfos, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/studies/{studyUuid}/nad-configs", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save NAD config")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "NAD config is saved"),
        @ApiResponse(responseCode = "404", description = "Study does not exist")
    })
        public ResponseEntity<UUID> saveNadConfig(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestBody NadConfigInfos nadConfigData) {
        studyService.assertIsStudyExist(studyUuid);
        UUID savedUuid = studyService.saveNadConfig(studyUuid, nadConfigData);
        return ResponseEntity.ok().body(savedUuid);
    }

    @DeleteMapping(value = "/studies/{studyUuid}/nad-configs/{nadConfigUuid}")
    @Operation(summary = "Delete NAD config")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "NAD config is deleted"),
        @ApiResponse(responseCode = "404", description = "Study does not exist")
    })
    public ResponseEntity<Void> deleteNadConfig(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nadConfigUuid") UUID nadConfigUuid) {
        studyService.assertIsStudyExist(studyUuid);
        studyService.deleteNadConfig(studyUuid, nadConfigUuid);
        return ResponseEntity.noContent().build();
    }
}
