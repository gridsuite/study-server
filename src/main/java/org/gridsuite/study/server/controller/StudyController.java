/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.exception.PartialResultException;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.nodeactivity.*;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.beans.PropertyEditorSupport;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION)
@Tag(name = "Study server")
public class StudyController {
    private final StudyService studyService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final SingleLineDiagramService singleLineDiagramService;
    private final NetworkConversionService networkConversionService;
    private final CaseService caseService;
    private final RemoteServicesInspector remoteServicesInspector;
    private final RootNetworkService rootNetworkService;
    private final NodeActivityRunnerService nodeActivityRunnerService;
    private final StudyExportService studyExportService;
    private final StudyImportService studyImportService;
    private final NetworkModificationService networkModificationService;

    public StudyController(StudyService studyService,
                           NetworkModificationTreeService networkModificationTreeService,
                           SingleLineDiagramService singleLineDiagramService,
                           NetworkConversionService networkConversionService,
                           CaseService caseService,
                           RemoteServicesInspector remoteServicesInspector,
                           RootNetworkService rootNetworkService,
                           NodeActivityRunnerService nodeActivityRunnerService,
                           StudyExportService studyExportService,
                           StudyImportService studyImportService,
                           NetworkModificationService networkModificationService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.caseService = caseService;
        this.remoteServicesInspector = remoteServicesInspector;
        this.rootNetworkService = rootNetworkService;
        this.nodeActivityRunnerService = nodeActivityRunnerService;
        this.studyExportService = studyExportService;
        this.studyImportService = studyImportService;
        this.networkModificationService = networkModificationService;
    }

    @InitBinder
    public void initBinder(WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(EquipmentInfosService.FieldSelector.class,
            new MyEnumConverter<>(EquipmentInfosService.FieldSelector.class));
        webdataBinder.registerCustomEditor(ModificationType.class, new MyModificationTypeConverter());
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

    @PostMapping(value = "/studies/{studyUuid}/duplicate")
    @Operation(summary = "create a study from an existing one")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The study was successfully created"),
        @ApiResponse(responseCode = "404", description = "The source study doesn't exist")})
    public ResponseEntity<UUID> duplicateStudy(@PathVariable("studyUuid") UUID studyId,
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
    public ResponseEntity<Void> deleteStudy(@PathVariable("studyUuid") UUID studyUuid,
                                            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId);
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
                                              @Parameter(description = "The position where the node will be pasted relative to the reference node")
                                                  @RequestParam(name = "insertMode") InsertMode insertMode,
                                              @RequestHeader(HEADER_USER_ID) String userId) {
        //if the source study is not set we assume it's the same as the target study
        nodeActivityRunnerService.runWith(EDIT_TREE, targetStudyUuid, insertMode.isChild() ? List.of() : List.of(referenceNodeUuid),
            () -> studyService.duplicateStudyNode(sourceStudyUuid == null ? targetStudyUuid : sourceStudyUuid, targetStudyUuid, nodeToCopyUuid, referenceNodeUuid, insertMode, userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/studies/{studyUuid}/unshare")
    @Operation(summary = "Unshare all the reference modifications and copy the referenced composites in their place")
    @ApiResponse(responseCode = "200", description = "All the reference modifications from the study have been replaced by copies of their referenced composites")
    public ResponseEntity<Void> unshareAllModificationReferences(
            @PathVariable("studyUuid") UUID studyUuid,
            @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsStudyExist(studyUuid);
        networkModificationService.unshareAllReferencesInStudy(studyUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/export-network-formats")
    @Operation(summary = "get the available export format")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The available export format")})
    public ResponseEntity<String> getExportFormats() {
        String formatsJson = networkConversionService.getExportFormats();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(formatsJson);
    }

    @GetMapping(value = "/download-file/{exportUuid}")
    @Operation(summary = "Download exported network file")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The file is downloaded"),
        @ApiResponse(responseCode = "409", description = "Export not ready yet")
    })
    public ResponseEntity<Resource> downloadExportedNetworkFile(
            @PathVariable("exportUuid") UUID exportUuid,
            @RequestHeader(HEADER_USER_ID) String userId) {
        ExportNetworkStatus status = networkModificationTreeService.getExportNetworkStatus(exportUuid);
        if (status == ExportNetworkStatus.RUNNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(null);
        }
        return networkConversionService.downloadExportedNetworkFile(exportUuid, userId);
    }

    @GetMapping(value = "/svg-component-libraries")
    @Operation(summary = "Get a list of the available svg component libraries")
    @ApiResponse(responseCode = "200", description = "The list of the available svg component libraries")
    public ResponseEntity<List<String>> getAvailableSvgComponentLibraries() {
        List<String> libraries = singleLineDiagramService.getAvailableSvgComponentLibraries();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(libraries);
    }

    @GetMapping(value = "/studies/{studyUuid}/network-modifications/references/write-access")
    @Operation(summary = "Whether the study contains shared modifications, including nested in its composites, and, if so, whether the user can write on all, some or none of them")
    @ApiResponse(responseCode = "200", description = "The write access on the shared modifications was returned")
    public ResponseEntity<SharedModificationsWriteAccess> getSharedModificationsWriteAccess(@PathVariable("studyUuid") UUID studyUuid,
                                                                                            @RequestHeader(HEADER_USER_ID) String userId) {
        return ResponseEntity.ok().body(studyService.getSharedModificationsWriteAccess(studyUuid, userId));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search studies in elasticsearch")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of studies found")})
    public ResponseEntity<List<CreatedStudyBasicInfos>> searchStudies(@Parameter(description = "Lucene query") @RequestParam(value = "q") String query) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.searchStudies(query));
    }

    @GetMapping(value = "/nodes/infos", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the name and the study of each given node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Infos of the found nodes, unknown nodes are omitted"),
    })
    public ResponseEntity<List<NodeInfos>> getNodesInfos(
            @Parameter(description = "Node UUIDs") @RequestParam("ids") List<UUID> nodeUuids) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(networkModificationTreeService.getNodesInfos(nodeUuids));
    }

    @PostMapping(value = "/studies/root-network-tags", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the root network tags of the given studies")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Root network tags by study, unknown studies are ignored"),
    })
    public ResponseEntity<Map<UUID, List<String>>> getRootNetworkTags(
            @Parameter(description = "Study UUIDs") @RequestBody List<UUID> studyUuids) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(rootNetworkService.getRootNetworkTags(studyUuids));
    }

    @GetMapping(value = "/studies/{studyUuid}/subtree")
    @Operation(summary = "Get network modification subtree for the given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "network modification subtree"),
        @ApiResponse(responseCode = "404", description = "The study or the parent node not found")})
    public ResponseEntity<NetworkModificationNode> getNetworkModificationSubtree(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                                 @Parameter(description = "parent node uuid") @RequestParam(value = "parentNodeUuid") UUID parentNodeUuid,
                                                                 @Parameter(description = "root network uuid") @RequestParam(value = "rootNetworkUuid", required = false) UUID rootNetworkUuid) {
        NetworkModificationNode parentNode = (NetworkModificationNode) networkModificationTreeService.getStudySubtreeWithReferencesInfos(studyUuid, parentNodeUuid, rootNetworkUuid);
        return parentNode != null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(parentNode)
                : ResponseEntity.notFound().build();
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

    // --- Dynamic Mapping Endpoints BEGIN --- //

    @GetMapping(value = "/studies/{studyUuid}/dynamic-mapping/network/values")
    @Operation(summary = "Fetch attribute values of the first root network of a study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of attribute values of the first root network")})
    public ResponseEntity<String> getNetworkValuesFromStudy(@PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkValuesFromStudy(studyUuid));
    }

    @PostMapping(value = "/studies/{studyUuid}/dynamic-mapping/network/matches/rule")
    @Operation(summary = "Get the equipment ids that matches the given rule.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Equipment ids that matches the given rule")})
    public ResponseEntity<String> getNetworkMatchesFromStudy(@PathVariable("studyUuid") UUID studyUuid, @RequestBody String ruleToMatch) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(studyService.getNetworkMatchesFromStudy(studyUuid, ruleToMatch));
    }

    // --- Dynamic Mapping Endpoints END --- //

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

    @GetMapping(value = "/optional-services")
    @Operation(summary = "Get all the optional services and their status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of optional services")})
    public ResponseEntity<List<ServiceStatusInfos>> getOptionalServices() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(remoteServicesInspector.getOptionalServices());
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

    static class MyModificationTypeConverter extends PropertyEditorSupport {

        MyModificationTypeConverter() {
            super();
        }

        @Override
        public void setAsText(final String text) throws IllegalArgumentException {
            setValue(ModificationType.getTypeFromUri(text));
        }
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
        @RequestBody List<NodeAlias> nodeAliases,
        @RequestHeader(HEADER_USER_ID) String userId) {
        studyService.assertIsStudyExist(studyUuid);
        studyService.updateNodeAliases(studyUuid, nodeAliases, userId);
        return ResponseEntity.ok().build();
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

    @GetMapping(value = "/studies/{studyUuid}/export/{studyName}", produces = "application/zip")
    @Operation(summary = "Export a study as a zip archive")
    @ApiResponse(responseCode = "200", description = "The study archive as zip")
    @ApiResponse(responseCode = "404", description = "Study or root network not found")
    public ResponseEntity<Resource> exportStudy(@PathVariable("studyUuid") UUID studyUuid,
                                                @PathVariable("studyName") String studyName,
                                                @RequestHeader(HEADER_USER_ID) String userId) {
        ContentDisposition contentDisposition = ContentDisposition.attachment().filename(studyName + ".zip").build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        return ResponseEntity.ok().headers(headers).body(studyExportService.exportStudy(studyUuid, userId));
    }

    @PostMapping(value = "/studies/import")
    @Operation(summary = "Create a study and its root networks from a previously exported study archive")
    @ApiResponse(responseCode = "200", description = "Study imported successfully")
    public ResponseEntity<Void> importStudy(@RequestBody TreeExportInfos treeExportInfos,
                                            @RequestHeader(HEADER_USER_ID) String userId) {
        studyImportService.importStudy(treeExportInfos, userId);
        return ResponseEntity.ok().build();
    }
}
