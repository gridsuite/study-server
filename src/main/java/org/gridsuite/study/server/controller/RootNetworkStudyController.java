/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import com.powsybl.iidm.network.ThreeSides;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.filter.globalfilter.GlobalFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.modification.ModificationsSearchResultByNode;
import org.gridsuite.study.server.dto.networkexport.NodeExportInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.nodeactivity.NetworkModificationActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityType;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.*;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/root-networks")
@Tag(name = "Study server - Root networks")
public class RootNetworkStudyController {

    private final NetworkService networkStoreService;
    private final NetworkModificationActivityRunnerService networkModificationActivityRunnerService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final RootNetworkStudyService rootNetworkStudyService;
    private final CaseService caseService;
    private final NodeActivityRunnerService nodeActivityRunnerService;
    private final RootNetworkService rootNetworkService;
    private final NetworkModificationService networkModificationService;

    public RootNetworkStudyController(NetworkService networkStoreService,
                                      NetworkModificationActivityRunnerService networkModificationActivityRunnerService,
                                      NetworkModificationTreeService networkModificationTreeService,
                                      RootNetworkStudyService rootNetworkStudyService,
                                      CaseService caseService,
                                      NodeActivityRunnerService nodeActivityRunnerService,
                                      RootNetworkService rootNetworkService,
                                      NetworkModificationService networkModificationService) {
        this.networkStoreService = networkStoreService;
        this.networkModificationActivityRunnerService = networkModificationActivityRunnerService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.rootNetworkStudyService = rootNetworkStudyService;
        this.caseService = caseService;
        this.nodeActivityRunnerService = nodeActivityRunnerService;
        this.rootNetworkService = rootNetworkService;
        this.networkModificationService = networkModificationService;
    }

    @InitBinder
    public void initBinder(WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(EquipmentInfosService.FieldSelector.class,
            new MyEnumConverter<>(EquipmentInfosService.FieldSelector.class));
    }

    static class MyEnumConverter<E extends Enum<E>> extends PropertyEditorSupport {
        private final Class<E> enumClass;

        MyEnumConverter(Class<E> enumClass) {
            this.enumClass = enumClass;
        }

        @Override
        public void setAsText(final String text) throws IllegalArgumentException {
            try {
                E value = Enum.valueOf(enumClass, text.toUpperCase());
                setValue(value);
            } catch (IllegalArgumentException _) {
                String avail = StringUtils.join(enumClass.getEnumConstants(), ", ");
                throw new IllegalArgumentException(String.format("Enum unknown entry '%s' should be among %s", text, avail));
            }
        }
    }

    @GetMapping(value = "")
    @Operation(summary = "Get root networks for study")
    @ApiResponse(responseCode = "200", description = "List of root networks")
    public ResponseEntity<List<BasicRootNetworkInfos>> getRootNetworks(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(rootNetworkStudyService.getAllBasicRootNetworkInfos(studyUuid));
    }

    @PostMapping(value = "")
    @Operation(summary = "Create root network for study")
    @ApiResponse(responseCode = "200", description = "Root network created")
    public ResponseEntity<RootNetworkRequestInfos> createRootNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                                     @RequestBody RootNetworkInfos rootNetworkInfos,
                                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        return ResponseEntity.ok().body(rootNetworkStudyService.createRootNetworkRequest(studyUuid, rootNetworkInfos, userId));
    }

    @PutMapping(value = "/{rootNetworkUuid}")
    @Operation(summary = "update root network case")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The case is updated for a root network")})
    public ResponseEntity<Void> updateRootNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                  @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                  @RequestBody RootNetworkInfos rootNetworkInfos,
                                                  @RequestHeader(HEADER_USER_ID) String userId) {
        caseService.assertCaseExists(rootNetworkInfos.getCaseInfos() != null ? rootNetworkInfos.getCaseInfos().getOriginalCaseUuid() : null);
        if (rootNetworkInfos.hasCaseToImport()) {
            // the reimport unbuilds the whole tree of this root network, so it is held on the root node
            UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
            nodeActivityRunnerService.runWith(REIMPORT_CASE, studyUuid, rootNetworkUuid, List.of(rootNodeUuid),
                    () -> rootNetworkStudyService.updateRootNetworkRequest(studyUuid, rootNetworkInfos, userId));
        } else {
            // only the basic infos (name, tag) are updated, the tree is left untouched
            rootNetworkStudyService.updateRootNetworkRequest(studyUuid, rootNetworkInfos, userId);
        }
        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "", params = {"name"})
    @Operation(summary = "Check if a root network already exists")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The root network exists"),
        @ApiResponse(responseCode = "204", description = "The root network doesn't exist")})
    public ResponseEntity<Void> rootNetworkExists(@PathVariable("studyUuid") UUID studyUuid,
                                                  @RequestParam("name") String rootNetworkName) {
        HttpStatus status = rootNetworkService.isRootNetworkNameExistsInStudy(studyUuid, rootNetworkName) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "", params = "tag")
    @Operation(summary = "Check if a root network with this tag already exists in the given study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The tag exists"),
        @ApiResponse(responseCode = "204", description = "The tag doesn't exist")})
    public ResponseEntity<Void> rootNetworkTagExists(@PathVariable("studyUuid") UUID studyUuid,
                                                     @RequestParam("tag") String rootNetworkTag) {
        HttpStatus status = rootNetworkService.isRootNetworkTagExistsInStudy(studyUuid, rootNetworkTag) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }

    @DeleteMapping(value = "")
    @Operation(summary = "Delete root networks for study")
    @ApiResponse(responseCode = "200", description = "Root network deleted")
    public ResponseEntity<Void> deleteRootNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                  @RequestBody List<UUID> rootNetworksUuids,
                                                  @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkStudyService.deleteRootNetworks(studyUuid, rootNetworksUuids, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/{rootNetworkUuid}/case/name")
    @Operation(summary = "Get study case name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study case name"),
        @ApiResponse(responseCode = "204", description = "The study has no case name attached")})
    public ResponseEntity<String> getStudyCaseName(@PathVariable("studyUuid") UUID studyUuid, @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        String studyCaseName = rootNetworkService.getCaseName(rootNetworkUuid);
        return StringUtils.isEmpty(studyCaseName) ? ResponseEntity.noContent().build() : ResponseEntity.ok().body(studyCaseName);
    }

    @GetMapping(value = "/{rootNetworkUuid}/import-parameters", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the import parameters stored for a root network, used to (re)create its network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The root network import parameters"),
        @ApiResponse(responseCode = "404", description = "The root network doesn't exist")})
    public ResponseEntity<Map<String, Object>> getRootNetworkImportParameters(@PathVariable("studyUuid") UUID studyUuid, @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().body(rootNetworkService.getImportParameters(rootNetworkUuid));
    }

    @GetMapping(value = "/{rootNetworkUuid}/network")
    @Operation(summary = "check study root network existence")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The network does exist"),
        @ApiResponse(responseCode = "204", description = "The network doesn't exist")})
    public ResponseEntity<RootNetworkStatusInfos> checkNetworkExistence(@PathVariable("studyUuid") UUID studyUuid, @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        UUID networkUUID = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        return ResponseEntity.ok().body(new RootNetworkStatusInfos(networkStoreService.doesNetworkExist(networkUUID),
                rootNetworkService.getRootNetworkLoadStatus(rootNetworkUuid)));
    }

    @PostMapping(value = "/{rootNetworkUuid}/network", params = {"caseUuid"})
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
        rootNetworkStudyService.recreateNetwork(caseUuid, userId, studyUuid, rootNetworkUuid, caseFormat, importParameters);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/network")
    @Operation(summary = "recreate study network of a study from its case")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Study network recreation has started"),
        @ApiResponse(responseCode = "424", description = "The study's case doesn't exist")})
    public ResponseEntity<BasicStudyInfos> recreateStudyNetwork(@PathVariable("studyUuid") UUID studyUuid,
                                                                @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                @RequestHeader(HEADER_USER_ID) String userId,
                                                                @Parameter(description = "case format") @RequestParam(name = "caseFormat", required = false) String caseFormat
    ) {
        rootNetworkStudyService.recreateNetwork(userId, studyUuid, rootNetworkUuid, caseFormat);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/{rootNetworkUuid}/indexation/status")
    @Operation(summary = "check root network indexation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The root network indexation status for a study"),
        @ApiResponse(responseCode = "204", description = "The root network indexation status doesn't exist"),
        @ApiResponse(responseCode = "404", description = "The root network or network doesn't exist")})
    public ResponseEntity<String> checkRootNetworkIndexationStatus(@PathVariable("studyUuid") UUID studyUuid, @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        String result = rootNetworkStudyService.getRootNetworkIndexationStatus(studyUuid, rootNetworkUuid).name();
        return result != null ? ResponseEntity.ok().body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg"),
        @ApiResponse(responseCode = "404", description = "The voltage level has not been found")})
    public ResponseEntity<byte[]> generateVoltageLevelDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        byte[] result = rootNetworkStudyService.generateVoltageLevelSvg(
                voltageLevelId,
                nodeUuid,
                rootNetworkUuid,
                sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata")
    @Operation(summary = "get the voltage level diagram for the given network and voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg and metadata"),
        @ApiResponse(responseCode = "404", description = "The voltage level has not been found")})
    public ResponseEntity<String> generateVoltageLevelDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        String result = rootNetworkStudyService.generateVoltageLevelSvgAndMetadata(
                voltageLevelId,
                nodeUuid,
                rootNetworkUuid,
                sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses-or-busbar-sections")
    @Operation(summary = "get the buses for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The buses list of the network for given voltage level")
    public ResponseEntity<List<IdentifiableInfos>> getVoltageLevelBusesOrBusbarSections(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.getVoltageLevelBusesOrBusbarSections(nodeUuid, rootNetworkUuid, voltageLevelId,
                inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/switches")
    @Operation(summary = "get the switches for a given network and a given voltage level")
    @ApiResponse(responseCode = "200", description = "The switches list of the network for given voltage level")
    public ResponseEntity<String> getVoltageLevelSwitches(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getVoltageLevelTopologyInfos(nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode,
                "switches"));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/bus-bar-sections")
    @Operation(summary = "get bus bar sections information for a given network and voltage level")
    @ApiResponse(responseCode = "200", description = "Bus bar sections information of the given voltage level retrieved")
    public ResponseEntity<String> getVoltageLevelBusBarSections(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getVoltageLevelTopologyInfos(nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode,
                "bus-bar-sections"));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/feeder-bays")
    @Operation(summary = "get feeder bays informations for a given network and voltage level")
    @ApiResponse(responseCode = "200", description = "Feeder bays informations of the given voltage level retrieved")
    public ResponseEntity<String> getVoltageLevelFeederBays(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getVoltageLevelTopologyInfos(nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode,
                "feeder-bays"));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/substation-id")
    @Operation(summary = "get the substation ID for a given network and a given voltage level")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The substation Id for a voltageLevel")})
    public ResponseEntity<String> getVoltageLevelSubstationId(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltageLevelId") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().body(rootNetworkStudyService.getVoltageLevelSubstationId(
                studyUuid, nodeUuid, rootNetworkUuid, voltageLevelId, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcId}/shunt-compensators")
    @Operation(summary = "For a given hvdc line, get its related Shunt compensators in case of LCC converter station")
    @ApiResponse(responseCode = "200", description = "Hvdc line type and its shunt compensators on each side")
    public ResponseEntity<String> getHvdcLineShuntCompensators(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("hvdcId") String hvdcId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        String hvdcInfos = rootNetworkStudyService.getHvdcLineShuntCompensators(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, hvdcId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(hvdcInfos);
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/lines", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Network lines graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of line graphics with the given ids, all otherwise")})
    public ResponseEntity<String> getLineGraphics(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> linesIds) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getLinesGraphics(rootNetworkService.getNetworkUuid(rootNetworkUuid), nodeUuid, rootNetworkUuid, linesIds));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/substations", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Network substations graphics")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of substation graphics with the given ids, all otherwise")})
    public ResponseEntity<String> getSubstationGraphics(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> substationsIds) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getSubstationsGraphics(rootNetworkService.getNetworkUuid(rootNetworkUuid), nodeUuid, rootNetworkUuid,
                substationsIds));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/equipments-ids")
    @Operation(summary = "Get equipment ids ")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipment ids")})
    public ResponseEntity<String> getNetworkElementsIds(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody(required = false) List<String> substationsIds,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "equipment type") @RequestParam(name = "equipmentType") String equipmentType,
            @Parameter(description = "Nominal Voltages") @RequestParam(name = "nominalVoltages", required = false) List<Double> nominalVoltages) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getNetworkElementsIds(nodeUuid, rootNetworkUuid, substationsIds, inUpstreamBuiltParentNode, equipmentType,
                nominalVoltages));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements")
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

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getNetworkElementsInfos(studyUuid, nodeUuid, rootNetworkUuid, substationsIds, infoType, elementType,
                inUpstreamBuiltParentNode, nominalVoltages));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements-from-filters")
    @Operation(summary = "Get network elements infos by evaluating a list of filters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The list of network elements infos matching the filters"),
        @ApiResponse(responseCode = "404", description = "The study/root network/node is not found")
    })
    public ResponseEntity<String> getNetworkElementsInfosFromFilters(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "The equipment type to filter and return") @RequestParam(name = "equipmentType") @NonNull EquipmentType equipmentType,
            @Parameter(description = "Info type (e.g., LIST, TAB, MAP, FORM)") @RequestParam(name = "infoType", defaultValue = "LIST") String infoType,
            @RequestBody @NonNull List<UUID> filterUuids) {
        networkModificationTreeService.assertIsRootNetworkAndNodeInStudy(studyUuid, rootNetworkUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(rootNetworkStudyService.getNetworkElementsInfosFromFilters(studyUuid, nodeUuid, rootNetworkUuid, equipmentType, infoType, filterUuids));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements/{elementId}")
    @Operation(summary = "Get network elements infos")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of network elements infos")})
    public ResponseEntity<String> getNetworkElementInfos(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Element id") @PathVariable("elementId") String elementId,
            @Parameter(description = "Element type") @RequestParam(name = "elementType") String elementType,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @Parameter(description = "Info type parameters") InfoTypeParameters infoTypeParameters) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getNetworkElementInfos(studyUuid, nodeUuid, rootNetworkUuid, elementType, infoTypeParameters, elementId,
                inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/countries")
    @Operation(summary = "Get network countries")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of countries")})
    public ResponseEntity<String> getNetworkCountries(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getNetworkCountries(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/nominal-voltages")
    @Operation(summary = "Get network nominal voltages")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of nominal voltages")})
    public ResponseEntity<String> getNetworkNominalVoltages(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getNetworkNominalVoltages(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/branch-or-3wt/{equipmentId}/voltage-level-id")
    @Operation(summary = "Get Voltage level ID for a specific line or 2WT or 3WT and a given side")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage level id attached to line or 2WT or 3WT"), @ApiResponse(responseCode = "204",
            description = "No voltageLevel ID found")})
    public ResponseEntity<String> getBranchOr3WTVoltageLevelId(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("equipmentId") String equipmentId,
            @RequestParam(value = "side") ThreeSides side,
            @RequestParam(value = "inUpstreamBuiltParentNode", required = false, defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        String voltageLevelId = rootNetworkStudyService.getBranchOr3WTVoltageLevelId(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, equipmentId, side);
        return StringUtils.isEmpty(voltageLevelId) ? ResponseEntity.noContent().build() : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(voltageLevelId);
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}/equipments")
    @Operation(summary = "Get voltage level equipments")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Voltage level equipments")})
    public ResponseEntity<String> getVoltageLevelEquipments(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "voltage level id") @PathVariable("voltageLevelId") String voltageLevelId,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getVoltageLevelEquipments(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, voltageLevelId));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/all")
    @Operation(summary = "Get Network equipments description")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of equipments data")})
    public ResponseEntity<String> getAllMapData(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Substations id") @RequestParam(name = "substationId", required = false) List<String> substationsIds) {

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getAllMapData(studyUuid, nodeUuid, rootNetworkUuid, substationsIds));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}")
    @Operation(summary = "export the study's network in the given format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network in the given format")})
    public ResponseEntity<UUID> exportNetwork(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("format") String format,
            @RequestParam(name = "compression", required = false, defaultValue = "ZIP") CompressionType compression,
            @RequestParam(value = "formatParameters", required = false) String parametersJson,
            @RequestParam(value = "fileName") String fileName,
            @RequestParam(value = "exportToGridExplore", required = false, defaultValue = "false") boolean exportToGridExplore,
            @RequestParam(value = "parentDirectoryUuid", required = false) String parentDirectoryUuidStr,
            @RequestParam(value = "description", required = false) String description,
            @RequestHeader(HEADER_USER_ID) String userId) {
        rootNetworkStudyService.assertRootNodeOrBuiltNode(studyUuid, nodeUuid, rootNetworkUuid);
        UUID parentDirectoryUuid = parentDirectoryUuidStr != null ? UUID.fromString(parentDirectoryUuidStr) : null;
        UUID exportUuid = rootNetworkStudyService.exportNetwork(studyUuid,
                nodeUuid,
                rootNetworkUuid,
                new NodeExportInfos(exportToGridExplore, parentDirectoryUuid, fileName, description),
                format,
                compression,
                userId,
                parametersJson);
        return ResponseEntity.ok().body(exportUuid);
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/contingency-count")
    @Operation(summary = "Get contingency count for a list of contingency list on a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The contingency count")})
    public ResponseEntity<ContingencyCount> getContingencyCount(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                @Parameter(description = "Contingency list UUIDs") @RequestParam(name = "contingencyListIds",
                                                                        required = false) List<UUID> contingencyListIds) {
        return ResponseEntity.ok().body(CollectionUtils.isEmpty(contingencyListIds)
                ? ActionsService.EMPTY_CONTINGENCY_COUNT
                : rootNetworkStudyService.getContingencyCount(studyUuid, contingencyListIds, nodeUuid, rootNetworkUuid));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/limit-violations")
    @Operation(summary = "Get limit violations.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The limit violations")})
    public ResponseEntity<List<LimitViolationInfos>> getLimitViolations(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
                                                @Parameter(description = "JSON array of global filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
                                                                        Sort sort) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.getLimitViolations(nodeUuid, rootNetworkUuid, filters, globalFilters, sort));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values")
    @Operation(summary = "Get Enum values")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The Enum values")})
    public ResponseEntity<List<String>> getResultEnumValues(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                            @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                            @Parameter(description = "Computing Type") @RequestParam(name = "computingType") ComputationType computingType,
                                                            @Parameter(description = "Enum name") @RequestParam(name = "enumName") String enumName) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.getResultEnumValues(nodeUuid, rootNetworkUuid, computingType, enumName));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg"),
        @ApiResponse(responseCode = "404", description = "The substation has not been found")})
    public ResponseEntity<byte[]> generateSubstationDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        byte[] result = rootNetworkStudyService.generateSubstationSvg(substationId, nodeUuid, rootNetworkUuid, sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata")
    @Operation(summary = "get the substation diagram for the given network and substation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The svg and metadata"),
        @ApiResponse(responseCode = "404", description = "The substation has not been found")})
    public ResponseEntity<String> generateSubstationDiagramAndMetadata(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @PathVariable("substationId") String substationId,
            @RequestBody Map<String, Object> sldRequestInfos) {
        String result = rootNetworkStudyService.generateSubstationSvgAndMetadata(substationId, nodeUuid, rootNetworkUuid, sldRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
                ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "get the network area diagram for the given network and voltage levels")
    @ApiResponse(responseCode = "200", description = "The svg")
    public ResponseEntity<String> generateNetworkAreaDiagram(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody Map<String, Object> nadRequestInfos) {
        String result = rootNetworkStudyService.generateNetworkAreaDiagram(nodeUuid, rootNetworkUuid, nadRequestInfos);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) :
                ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get node report with its parent nodes")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The node report"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<List<Report>> getParentNodesReport(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                             @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                             @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @Parameter(description = "Node only report") @RequestParam(value = "nodeOnlyReport", required = false,
                                                                     defaultValue = "true") boolean nodeOnlyReport,
                                                             @Parameter(description = "The report Type") @RequestParam(name = "reportType") StudyService.ReportType reportType,
                                                             @Parameter(description = "Severity levels") @RequestParam(name = "severityLevels", required = false) Set<String> severityLevels) {
        networkModificationTreeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.getParentNodesReport(nodeUuid, rootNetworkUuid, nodeOnlyReport, reportType, severityLevels));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the report logs of the given node and all its parents")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report logs of the node and all its parent"), @ApiResponse(responseCode = "404",
            description = "The study/node is not found")})
    public ResponseEntity<ReportPage> getReportLogs(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                    @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                    @Parameter(description = "node id") @PathVariable("nodeUuid") UUID nodeUuid,
                                                    @Parameter(description = "report id") @RequestParam(name = "reportId", required = false) UUID reportId,
                                                    @Parameter(description = "The message filter") @RequestParam(name = "message", required = false) String messageFilter,
                                                    @Parameter(description = "Severity levels filter") @RequestParam(name = "severityLevels",
                                                            required = false) Set<String> severityLevels,
                                                    @Parameter(description = "If we wanted the paged version of the results or not") @RequestParam(name = "paged", required = false,
                                                            defaultValue = "false") boolean paged,
                                                    Pageable pageable) {
        networkModificationTreeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getReportLogs(nodeUuid, rootNetworkUuid, reportId, messageFilter, severityLevels, paged, pageable));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search", produces = MediaType.APPLICATION_JSON_VALUE)
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
        networkModificationTreeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
                rootNetworkStudyService.getSearchTermMatchesInFilteredLogs(nodeUuid, rootNetworkUuid, reportId, severityLevels, messageFilter, searchTerm, pageSize));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/report/aggregated-severities", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the report severities of the given node and all its parents")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The report severities of the node and all its parent"), @ApiResponse(responseCode = "404",
            description = "The study/node is not found")})
    public ResponseEntity<Set<String>> getParentNodesAggregatedReportSeverities(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                                @Parameter(description = "root network id") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                @Parameter(description = "node id") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                                @Parameter(description = "reportId") @RequestParam(name = "reportId", required = false) UUID reportId) {
        networkModificationTreeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.getAggregatedReportSeverities(nodeUuid, rootNetworkUuid, reportId));
    }

    @PutMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", params = "applicable")
    @Operation(summary = "Update the applicability of network modifications for a node on a specific root network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Update the applicability of network modifications on a node in a specific root network"),
        @ApiResponse(responseCode = "404", description = "The study/root network/node is not found")})
    public ResponseEntity<Void> updateNetworkModificationsApplicability(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                        @Parameter(description = "Root network UUID") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                        @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                        @Parameter(description = "Network modification UUIDs") @RequestParam("uuids") Set<UUID> networkModificationUuids,
                                                                        @Parameter(description = "New applicability value") @RequestParam(name = "applicable") Boolean applicable,
                                                                        @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationTreeService.assertIsNodeNotReadOnly(nodeUuid);
        networkModificationActivityRunnerService.runWith(studyUuid, nodeUuid, userId,
                () -> {
                    networkModificationService.updateNetworkModificationsApplicabilityInRootNetwork(studyUuid, nodeUuid, rootNetworkUuid, networkModificationUuids, userId, applicable);
                });
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/search", produces = MediaType.APPLICATION_JSON_VALUE)
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
                .body(rootNetworkStudyService.searchEquipments(nodeUuid, rootNetworkUuid, userInput, fieldSelector, equipmentType, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/{rootNetworkUuid}/modifications/indexation-infos", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search modifications in elasticsearch by equipment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of modifications found"),
        @ApiResponse(responseCode = "404", description = "The study not found"),
    })
    public ResponseEntity<List<ModificationsSearchResultByNode>> searchModifications(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "User input") @RequestParam(value = "userInput") String userInput) {
        networkModificationTreeService.assertIsStudyExist(studyUuid);
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(rootNetworkStudyService.searchModifications(rootNetworkUuid, userInput));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/build")
    @Operation(summary = "build a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been built"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
        @ApiResponse(responseCode = "403", description = "The study node can't be built")})
    public ResponseEntity<Void> buildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                          @RequestHeader(HEADER_USER_ID) String userId) {
        if (networkModificationService.isNodeBuilt(nodeUuid, rootNetworkUuid)) {
            return ResponseEntity.ok().build();
        }
        nodeActivityRunnerService.runWith(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid),
                () -> networkModificationService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/unbuild")
    @Operation(summary = "unbuild a study node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The study node has been unbuilt"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist"),
        @ApiResponse(responseCode = "403", description = "The study node is not a model node")})
    public ResponseEntity<Void> unbuildNode(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                            @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
                                            @RequestHeader(HEADER_USER_ID) String userId) {
        // unbuildStudyNode only reaches the children of a security node whose loadflow has run
        NodeActivityType unbuildType = networkModificationTreeService.isSecurityNodeWithLoadFlowDone(nodeUuid, rootNetworkUuid)
                ? UNBUILD_CHILDREN : UNBUILD;
        nodeActivityRunnerService.runWith(unbuildType, studyUuid, rootNetworkUuid, List.of(nodeUuid),
                () -> rootNetworkStudyService.unbuildStudyNode(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/build/stop")
    @Operation(summary = "stop a node build")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The build has been stopped"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Void> stopBuild(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                          @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid) {
        rootNetworkStudyService.stopBuild(nodeUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/reindex-all")
    @Operation(summary = "reindex root network")
    @ApiResponse(responseCode = "200", description = "Root network reindexed")
    public ResponseEntity<Void> reindexRootNetwork(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                   @Parameter(description = "root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid) {
        rootNetworkStudyService.reindexRootNetwork(studyUuid, rootNetworkUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications/voltage-init", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the voltage init modifications from a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init modifications was returned"), @ApiResponse(responseCode = "404",
            description = "The study/node is not found, or has no voltage init result")})
    public ResponseEntity<String> getVoltageInitModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                              @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        networkModificationTreeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.getVoltageInitModifications(nodeUuid, rootNetworkUuid));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications/voltage-init")
    @Operation(summary = "Clone the voltage init modifications, then append them to node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The voltage init modifications have been appended.")})
    public ResponseEntity<Void> insertVoltageInitModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "rootNetworkUuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                               @PathVariable("nodeUuid") UUID nodeUuid,
                                                               @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationTreeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        networkModificationTreeService.assertIsNodeNotReadOnly(nodeUuid);
        nodeActivityRunnerService.runWith(EDIT_MODIFICATIONS, studyUuid, List.of(nodeUuid),
                () -> rootNetworkStudyService.insertVoltageInitModifications(studyUuid, nodeUuid, rootNetworkUuid, userId));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/filters/evaluate")
    @Operation(summary = "Evaluate a filter to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> evaluateFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode,
            @RequestBody String filter) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.evaluateFilter(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode, filter));
    }

    @PostMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/global-filter/evaluate",
            produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Evaluate a global filter to get matched elements")
    @ApiResponse(responseCode = "200", description = "The list of matched elements")
    public ResponseEntity<List<String>> evaluateGlobalFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Node uuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "The equipments types to filter and return") @RequestParam(name = "equipmentTypes") @NonNull final List<EquipmentType> equipmentTypes,
            @RequestBody @NonNull GlobalFilter filter) {
        this.networkModificationTreeService.assertIsRootNetworkAndNodeInStudy(studyUuid, rootNetworkUuid, nodeUuid);
        return ResponseEntity.ok(rootNetworkStudyService.evaluateGlobalFilter(nodeUuid, rootNetworkUuid, equipmentTypes, filter));
    }

    @GetMapping(value = "/{rootNetworkUuid}/filters/{filterUuid}/elements")
    @Operation(summary = "Evaluate a filter on root node to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> exportFilter(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "Filter uuid to be applied") @PathVariable("filterUuid") UUID filterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.exportFilter(rootNetworkUuid, filterUuid));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/filters/elements")
    @Operation(summary = "Evaluate a list of filters on root node to get matched elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of matched elements")})
    public ResponseEntity<String> exportFilters(
            @Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "Root network uuid") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
            @Parameter(description = "nodeUuid") @PathVariable("nodeUuid") UUID nodeUuid,
            @Parameter(description = "Filters uuid to be resolved") @RequestParam("filtersUuid") List<UUID> filtersUuid,
            @Parameter(description = "Should get in upstream built node ?") @RequestParam(value = "inUpstreamBuiltParentNode", required = false,
                    defaultValue = "false") boolean inUpstreamBuiltParentNode) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.exportFilters(rootNetworkUuid, filtersUuid, nodeUuid, inUpstreamBuiltParentNode));
    }

    @GetMapping(value = "/{rootNetworkUuid}/nodes/{nodeUuid}/computations/status")
    @Operation(summary = "Get all computation status on study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all status are returned")})
    public ResponseEntity<Map<ComputationType, String>> getAllComputationsStatus(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                                 @Parameter(description = "Root network UUID") @PathVariable("rootNetworkUuid") UUID rootNetworkUuid,
                                                                                 @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNetworkStudyService.getAllComputationsStatus(studyUuid, rootNetworkUuid, nodeUuid));
    }
}
