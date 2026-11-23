package org.gridsuite.study.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.modification.CompositeInfos;
import org.gridsuite.study.server.dto.modification.ModificationCopyInfos;
import org.gridsuite.study.server.dto.modification.ModificationMoveInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.nodeactivity.NetworkModificationActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NodeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.MOVE_NETWORK_MODIFICATION_FORBIDDEN;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.*;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/nodes")
public class NodeController {

    private final NodeService nodeService;
    private final NetworkModificationActivityRunnerService networkModificationActivityRunnerService;
    private final NetworkModificationService networkModificationService;
    private final NodeActivityRunnerService nodeActivityRunnerService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public NodeController(NodeService nodeService,
                          NetworkModificationActivityRunnerService networkModificationActivityRunnerService,
                          NetworkModificationService networkModificationService,
                          NodeActivityRunnerService nodeActivityRunnerService,
                          NetworkModificationTreeService networkModificationTreeService) {
        this.nodeService = nodeService;
        this.networkModificationActivityRunnerService = networkModificationActivityRunnerService;
        this.networkModificationService = networkModificationService;
        this.nodeActivityRunnerService = nodeActivityRunnerService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @PostMapping(value = "/{nodeUuid}/composite-modification", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "For a list of network modifications passed in body, assemble them into a new composite modification")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The composite modification has been created.")})
    public ResponseEntity<UUID> assembleModificationsIntoNewComposite(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestBody List<UUID> modificationsUuids,
            @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        UUID newCompositeUuid = networkModificationActivityRunnerService.runWith(studyUuid, nodeUuid, userId,
                () -> {
                    networkModificationService.invalidateNodeTreeWhenMoveModification(studyUuid, nodeUuid);
                    return networkModificationService.assembleModificationsIntoComposite(studyUuid, nodeUuid, modificationsUuids, userId);
                });
        return ResponseEntity.ok().body(newCompositeUuid);
    }

    @PostMapping(value = "/{nodeUuid}/network-modifications/{modificationUuid}/share")
    @Operation(summary = "Move a composite modification of the node into a directory, replacing it by a reference to it")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The composite modification has been shared.")})
    public ResponseEntity<Void> shareCompositeModification(@PathVariable("studyUuid") UUID studyUuid,
                                                           @PathVariable("nodeUuid") UUID nodeUuid,
                                                           @PathVariable("modificationUuid") UUID modificationUuid,
                                                           @RequestParam("name") String name,
                                                           @RequestParam(value = "description", required = false) String description,
                                                           @RequestParam("parentDirectoryUuid") UUID parentDirectoryUuid,
                                                           @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        nodeService.shareCompositeNetworkModification(studyUuid, nodeUuid, modificationUuid, name, description, parentDirectoryUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}/composite-modifications", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "For a list of composite network modifications passed in body, insert them into the target node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The composite modification list has been inserted.")})
    public ResponseEntity<Void> insertCompositeModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                             @PathVariable("nodeUuid") UUID nodeUuid,
                                                             @RequestParam("action") StudyConstants.CompositeModificationsActionType action,
                                                             @RequestBody List<CompositeInfos> compositeInfos,
                                                             @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        handleInsertCompositeNetworkModifications(studyUuid, nodeUuid, compositeInfos, userId, action);
        return ResponseEntity.ok().build();
    }

    private void handleInsertCompositeNetworkModifications(
            UUID targetStudyUuid,
            UUID targetNodeUuid,
            List<CompositeInfos> compositeInfos,
            String userId,
            StudyConstants.CompositeModificationsActionType action) {
        nodeActivityRunnerService.runWith(EDIT_MODIFICATIONS, targetStudyUuid, List.of(targetNodeUuid),
                () -> nodeService.insertCompositeNetworkModifications(targetStudyUuid, targetNodeUuid, compositeInfos, userId, action));
    }

    @GetMapping(value = "/{nodeUuid}/network-modifications", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get network modifications from a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications was returned"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<String> getNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestParam(name = "onlyStashed", required = false, defaultValue = "false") Boolean onlyStashed,
                                                          @Parameter(description = "Only metadata") @RequestParam(name = "onlyMetadata", required = false,
                                                                  defaultValue = "false") Boolean onlyMetadata,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(networkModificationTreeService.getNetworkModifications(nodeUuid, onlyStashed, onlyMetadata, userId));
    }

    @GetMapping(value = "/{nodeUuid}/network-modifications/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get network modifications to export for a given node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications to export was returned"), @ApiResponse(responseCode = "404",
            description = "The study/node is not found")})
    public ResponseEntity<String> getExportedNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                  @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(nodeService.getExportedNetworkModifications(studyUuid, nodeUuid));
    }

    @PostMapping(value = "/{nodeUuid}/network-modifications")
    @Operation(summary = "Create a network modification for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was created"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> createNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        networkModificationActivityRunnerService.runWith(studyUuid, nodeUuid, userId,
                () -> {
                    networkModificationTreeService.invalidateNodeTreeWithLF(studyUuid, nodeUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode.ALL);
                    networkModificationService.createNetworkModification(studyUuid, nodeUuid, modificationAttributes, userId);
                });
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}/network-modifications/{uuid}")
    @Operation(summary = "Update a modification in the study network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modification was updated"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateNetworkModification(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Network modification UUID") @PathVariable("uuid") UUID networkModificationUuid,
                                                          @RequestBody String modificationAttributes,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        networkModificationActivityRunnerService.runWith(studyUuid, nodeUuid, userId,
                () -> networkModificationService.updateNetworkModification(studyUuid, modificationAttributes, nodeUuid, networkModificationUuid, userId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/{nodeUuid}/network-modifications")
    @Operation(summary = "Delete network modifications for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications was deleted"), @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> deleteNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                           @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                           @Parameter(description = "Network modification UUIDs") @RequestParam(name = "uuids") List<UUID> networkModificationUuids,
                                                           @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        nodeService.deleteNetworkModifications(studyUuid, nodeUuid, networkModificationUuids, userId);

        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}/network-modifications", params = "stashed")
    @Operation(summary = "Stash or restore network modifications for a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The network modifications were stashed / restored "), @ApiResponse(responseCode = "404",
            description = "The study/node is not found")})
    public ResponseEntity<Void> stashNetworkModifications(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                          @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                          @Parameter(description = "Network modification UUIDs") @RequestParam("uuids") List<UUID> networkModificationUuids,
                                                          @Parameter(description = "Stashed Modification") @RequestParam(name = "stashed", required = true) Boolean stashed,
                                                          @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        networkModificationActivityRunnerService.runWith(studyUuid, nodeUuid, userId,
                () -> {
                    if (stashed.booleanValue()) {
                        networkModificationService.stashNetworkModifications(studyUuid, nodeUuid, networkModificationUuids, userId);
                    } else {
                        networkModificationService.restoreNetworkModifications(studyUuid, nodeUuid, networkModificationUuids, userId);
                    }
                });
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}/network-modifications", params = "active")
    @Operation(summary = "Activates or deactivates network modifications of a node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The activation of the network modifications has been updated"), @ApiResponse(responseCode = "404",
            description = "The study/node is not found")})
    public ResponseEntity<Void> updateNetworkModificationsActivation(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                     @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                     @Parameter(description = "Network modification UUIDs") @RequestParam("uuids") List<UUID> networkModificationUuids,
                                                                     @Parameter(description = "Activate or deactivate the modifications") @RequestParam("active") boolean active,
                                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        networkModificationActivityRunnerService.runWith(studyUuid, nodeUuid, userId,
                () -> {
                    networkModificationService.updateNetworkModificationsActivation(studyUuid, nodeUuid, networkModificationUuids, userId, active);
                });
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}/network-modifications/{modificationUuid}/name-and-description")
    @Operation(summary = "Updates the name and the description of a network modification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Update the name and the description of a network modification"),
        @ApiResponse(responseCode = "404", description = "The study/node is not found")})
    public ResponseEntity<Void> updateModificationNameAndDescription(@Parameter(description = "Study UUID") @PathVariable("studyUuid") UUID studyUuid,
                                                                     @Parameter(description = "Node UUID") @PathVariable("nodeUuid") UUID nodeUuid,
                                                                     @PathVariable UUID modificationUuid,
                                                                     @RequestBody String metadata,
                                                                     @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationService.updateNetworkModificationNameAndDescription(studyUuid, nodeUuid, modificationUuid, metadata, userId);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "", method = RequestMethod.HEAD)
    @Operation(summary = "Test if a node name exists")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "node name exists"),
        @ApiResponse(responseCode = "204", description = "node name doesn't exist"),
    })
    public ResponseEntity<Void> nodeNameExists(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                               @Parameter(description = "Node name") @RequestParam("nodeName") String nodeName) {

        return networkModificationTreeService.isNodeNameExists(studyUuid, nodeName) ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/nextUniqueName")
    @Operation(summary = "Get unique node name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "unique node name generated")})

    public ResponseEntity<String> getUniqueNodeName(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid) {

        return ResponseEntity.ok().body(networkModificationTreeService.getUniqueNodeName(studyUuid));
    }

    @PostMapping(value = "/unbuild-all")
    @Operation(summary = "unbuild all study nodes in all root networks")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All study nodes has been unbuilt in all root networks"),
        @ApiResponse(responseCode = "404", description = "The study or node doesn't exist")})
    public ResponseEntity<Void> unbuildAllNodes(@Parameter(description = "Study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @RequestHeader(HEADER_USER_ID) String userId) {
        UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        nodeActivityRunnerService.runWith(UNBUILD_ALL, studyUuid, List.of(rootNodeUuid),
                () -> nodeService.unbuildNodeTree(studyUuid, rootNodeUuid, userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "For a list of network modifications passed in body, copy or cut, then append them to target node")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modification list has been updated.")})
    public ResponseEntity<Void> copyModifications(@PathVariable("studyUuid") UUID studyUuid,
                                                  @PathVariable("nodeUuid") UUID nodeUuid,
                                                  @RequestBody List<ModificationCopyInfos> modificationInfos,
                                                  @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        List<UUID> modificationsToCopyUuidList = modificationInfos.stream().map(ModificationCopyInfos::modificationUuid).toList();
        handleDuplicateNetworkModifications(studyUuid, nodeUuid, modificationsToCopyUuidList, userId);
        return ResponseEntity.ok().build();
    }

    private void handleDuplicateNetworkModifications(UUID targetStudyUuid, UUID targetNodeUuid, List<UUID> modificationsToCopyUuidList, String userId) {
        nodeActivityRunnerService.runWith(EDIT_MODIFICATIONS, targetStudyUuid, List.of(targetNodeUuid),
                () -> nodeService.duplicateNetworkModifications(targetStudyUuid, targetNodeUuid, modificationsToCopyUuidList, userId));
    }

    @PutMapping(value = "/{nodeUuid}/network-modifications/move")
    @Operation(summary = "Move modifications within or between containers, possibly from another node of the same study")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The modifications have been moved")})
    public ResponseEntity<Void> moveModifications(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID targetNodeUuid,
            @Parameter(description = "Node the modifications come from, defaults to the target node") @RequestParam(value = "originNodeUuid", required = false) UUID originNodeUuid,
            @RequestBody List<ModificationMoveInfos> modificationMoveInfos,
            @RequestHeader(HEADER_USER_ID) String userId) {
        UUID sourceNodeUuid = Objects.requireNonNullElse(originNodeUuid, targetNodeUuid);
        nodeService.assertIsStudyAndNodeExist(studyUuid, targetNodeUuid);
        nodeService.assertIsNodeExist(studyUuid, sourceNodeUuid, new StudyException(MOVE_NETWORK_MODIFICATION_FORBIDDEN));
        nodeService.assertIsNodeNotReadOnly(targetNodeUuid);
        networkModificationActivityRunnerService.runWith(studyUuid, targetNodeUuid, sourceNodeUuid, userId,
                () -> {
                    boolean isTargetInDifferentNodeTree = networkModificationService.invalidateNodeTreeWhenMoveModifications(studyUuid, targetNodeUuid, sourceNodeUuid);
                    networkModificationService.moveNetworkModifications(studyUuid, sourceNodeUuid, targetNodeUuid, modificationMoveInfos, isTargetInDifferentNodeTree, userId);
                });
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}/network-modifications/{modificationUuid}/unshare")
    @Operation(summary = "Unshare a reference modification and copy the referenced composite in its place")
    @ApiResponse(responseCode = "200", description = "The reference modification has been replaced by a copy of its referenced composite")
    public ResponseEntity<Void> unshareModificationReference(
            @PathVariable("modificationUuid") UUID modificationUuid,
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        nodeActivityRunnerService.runWith(UNSHARE_MODIFICATIONS, studyUuid, List.of(nodeUuid),
                () -> networkModificationService.unshareModificationReferences(studyUuid, Map.of(nodeUuid, List.of(modificationUuid)), userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{nodeUuid}/unshare")
    @Operation(summary = "Unshare all the reference modifications of a node and copy the referenced composites in their place")
    @ApiResponse(responseCode = "200", description = "All the reference modifications from the node have been replaced by copies of their referenced composites")
    public ResponseEntity<Void> unshareAllModificationReferencesInNode(
            @PathVariable("studyUuid") UUID studyUuid,
            @PathVariable("nodeUuid") UUID nodeUuid,
            @RequestHeader(HEADER_USER_ID) String userId) {
        nodeService.assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        nodeService.assertIsNodeNotReadOnly(nodeUuid);
        networkModificationService.unshareAllReferencesInNode(studyUuid, nodeUuid, userId);
        return ResponseEntity.ok().build();
    }
}
