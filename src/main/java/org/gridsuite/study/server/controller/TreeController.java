/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.sequence.NodeSequenceType;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.nodeactivity.NodeActivityInfos;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.TreeService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.*;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/studies/{studyUuid}/tree")
@Tag(name = "Study server - Tree controller")
public class TreeController {

    private final NetworkModificationTreeService networkModificationTreeService;
    private final NodeActivityRunnerService nodeActivityRunnerService;
    private final NodeActivityService nodeActivityService;
    private final NetworkModificationService networkModificationService;
    private final TreeService treeService;

    public TreeController(NetworkModificationTreeService networkModificationTreeService,
                          NodeActivityRunnerService nodeActivityRunnerService,
                          NodeActivityService nodeActivityService,
                          NetworkModificationService networkModificationService, TreeService treeService) {
        this.networkModificationTreeService = networkModificationTreeService;
        this.nodeActivityRunnerService = nodeActivityRunnerService;
        this.nodeActivityService = nodeActivityService;
        this.networkModificationService = networkModificationService;
        this.treeService = treeService;
    }

    @PostMapping(value = "/nodes", params = {"nodeToCutUuid", "referenceNodeUuid", "insertMode"})
    @Operation(summary = "cut and paste a node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node was successfully created"),
        @ApiResponse(responseCode = "403", description = "The node can't be copied above the root node nor around itself"),
        @ApiResponse(responseCode = "404", description = "The source study or node doesn't exist")})
    public ResponseEntity<Void> cutAndPasteNode(@PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "The node we want to cut") @RequestParam("nodeToCutUuid") UUID nodeToCutUuid,
                                                @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
                                                @Parameter(description = "The position where the node will be pasted relative to the reference node")
                                                @RequestParam(name = "insertMode") InsertMode insertMode,
                                                @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationTreeService.assertIsNodeNotReadOnly(nodeToCutUuid);
        // the node leaves its former parent, so its own children are reparented there whatever the insert mode
        List<UUID> heldNodes = Stream.concat(Stream.of(nodeToCutUuid), insertMode.isChild() ? Stream.empty() : Stream.of(referenceNodeUuid)).distinct().toList();
        nodeActivityRunnerService.runWith(EDIT_TREE, studyUuid, heldNodes,
                () -> treeService.moveStudyNode(studyUuid, nodeToCutUuid, referenceNodeUuid, insertMode, userId));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "")
    @Operation(summary = "Get network modification tree for the given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "network modification tree"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<RootNode> getNetworkModificationTree(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                               @Parameter(description = "root network uuid") @RequestParam(value = "rootNetworkUuid", required = false) UUID rootNetworkUuid) {
        RootNode rootNode = networkModificationTreeService.getStudyTreeWithReferencesInfos(studyUuid, rootNetworkUuid);
        return rootNode != null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rootNode)
                : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/node-activities")
    @Operation(summary = "Get the activities running in a study")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The running activities, empty when the study is idle"))
    public ResponseEntity<List<NodeActivityInfos>> getNodeActivities(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(nodeActivityService.getActivities(studyUuid));
    }

    @PostMapping(value = "/subtrees", params = {"subtreeToCutParentNodeUuid", "referenceNodeUuid"})
    @Operation(summary = "cut and paste a subtree")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The subtree was successfully created"),
        @ApiResponse(responseCode = "403", description = "The subtree can't be copied above the root node nor around itself"),
        @ApiResponse(responseCode = "404", description = "The source study or subtree doesn't exist")})
    public ResponseEntity<Void> cutAndPasteNodeSubtree(@PathVariable("studyUuid") UUID studyUuid,
           @Parameter(description = "The parent node of the subtree we want to cut") @RequestParam("subtreeToCutParentNodeUuid") UUID subtreeToCutParentNodeUuid,
           @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
           @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationTreeService.assertIsNodeNotReadOnly(subtreeToCutParentNodeUuid);
        // a subtree always attaches as a child at the destination, reparenting nothing there
        nodeActivityRunnerService.runWith(EDIT_TREE, studyUuid, List.of(subtreeToCutParentNodeUuid),
                () -> treeService.moveStudySubtree(studyUuid, subtreeToCutParentNodeUuid, referenceNodeUuid, userId));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/subtrees", params = {"subtreeToCopyParentNodeUuid", "referenceNodeUuid"})
    @Operation(summary = "duplicate a subtree")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The subtree was successfully created"),
        @ApiResponse(responseCode = "403", description = "The subtree can't be copied above the root node nor around itself"),
        @ApiResponse(responseCode = "404", description = "The source study or subtree doesn't exist")})
    public ResponseEntity<Void> duplicateSubtree(@Parameter(description = "The study where we want to copy the node") @PathVariable("studyUuid") UUID targetStudyUuid,
                                                 @Parameter(description = "The copied node original study") @RequestParam(value = "sourceStudyUuid") UUID sourceStudyUuid,
                                                 @Parameter(description = "The parent node of the subtree we want to cut")
                                                 @RequestParam("subtreeToCopyParentNodeUuid") UUID subtreeToCopyParentNodeUuid,
                                                 @Parameter(description = "The reference node to where we want to paste") @RequestParam("referenceNodeUuid") UUID referenceNodeUuid,
                                                 @RequestHeader(HEADER_USER_ID) String userId) {
        // added as a child of the reference, and the origin subtree is only read: nothing to hold
        treeService.duplicateStudySubtree(sourceStudyUuid, targetStudyUuid, subtreeToCopyParentNodeUuid, referenceNodeUuid, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/nodes/{id}")
    @Operation(summary = "Create a node as before / after the given node ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node has been added"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<NetworkModificationNode> createNode(@RequestBody NetworkModificationNode node,
                                                              @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                              @Parameter(description = "parent id of the node created") @PathVariable(name = "id") UUID referenceId,
                                                              @Parameter(description = "node is inserted before the given node ID") @RequestParam(name = "mode", required = false,
                                                                      defaultValue = "CHILD") InsertMode insertMode,
                                                              @RequestHeader(HEADER_USER_ID) String userId) {

        NetworkModificationNode newNode = nodeActivityRunnerService.runWith(EDIT_TREE, studyUuid, insertMode.isChild() ? List.of() : List.of(referenceId),
                () -> treeService.createNode(studyUuid, referenceId, node, insertMode, userId));
        buildNewNode(studyUuid, referenceId, newNode, userId);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(newNode);
    }

    @PostMapping(value = "/nodes/{id}", params = {"sequenceType"})
    @Operation(summary = "Create a node sequence after the given node ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node sequence has been added"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<NetworkModificationNode> createSequence(
            @Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
            @Parameter(description = "parent id of the node created") @PathVariable(name = "id") UUID referenceId,
            @Parameter(description = "sequence to create") @RequestParam("sequenceType") NodeSequenceType nodeSequenceType,
            @RequestHeader(HEADER_USER_ID) String userId) {
        // a sequence is added as a child at every level, so it reparents nothing and holds nothing
        NetworkModificationNode sequenceParentNode = treeService.createSequence(studyUuid, referenceId, nodeSequenceType, userId);
        buildNewNode(studyUuid, referenceId, sequenceParentNode, userId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(sequenceParentNode);
    }

    @DeleteMapping(value = "/nodes")
    @Operation(summary = "Delete node with given ids")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Nodes have been successfully deleted"),
        @ApiResponse(responseCode = "404", description = "The study or the nodes not found")})
    public ResponseEntity<Void> deleteNodes(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                            @Parameter(description = "ids of children to remove") @RequestParam("ids") List<UUID> nodeIds,
                                            @Parameter(description = "deleteChildren") @RequestParam(value = "deleteChildren", defaultValue = "false") boolean deleteChildren,
                                            @RequestHeader(HEADER_USER_ID) String userId) {
        nodeActivityRunnerService.runWith(DELETE_NODES, studyUuid, nodeIds,
                () -> treeService.deleteNodes(studyUuid, nodeIds, deleteChildren, userId, true));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/nodes/{id}/stash")
    @Operation(summary = "Move to trash the node with given id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The node has been successfully moved to trash"),
        @ApiResponse(responseCode = "403", description = "The node can't be moved to trash"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<Void> stashNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                          @Parameter(description = "id of child to delete (move to trash)") @PathVariable("id") UUID nodeId,
                                          @Parameter(description = "to stash a node with its children") @RequestParam(value = "stashChildren", defaultValue = "false") boolean stashChildren,
                                          @RequestHeader(HEADER_USER_ID) String userId) {
        networkModificationTreeService.assertIsNodeNotReadOnly(nodeId);
        nodeActivityRunnerService.runWith(DELETE_NODES, studyUuid, List.of(nodeId),
                () -> treeService.stashNode(studyUuid, nodeId, stashChildren, userId));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/nodes/stash")
    @Operation(summary = "Get the list of nodes in the trash for a given study")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The list of nodes in the trash")})
    public ResponseEntity<List<Pair<AbstractNode, Integer>>> getStashedNodes(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid) {
        return ResponseEntity.ok().body(treeService.getStashedNodes(studyUuid));
    }

    @PostMapping(value = "/nodes/restore")
    @Operation(summary = "restore nodes below the given anchor node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The list of nodes in the trash")})
    public ResponseEntity<Void> restoreNodes(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                             @Parameter(description = "ids of nodes to restore") @RequestParam("ids") List<UUID> nodeIds,
                                             @Parameter(description = "id of node below which the node will be restored") @RequestParam("anchorNodeId") UUID anchorNodeId,
                                             @RequestHeader(HEADER_USER_ID) String userId) {
        // the restored nodes land under the anchor, so it is held exactly as an insertion under it would be
        nodeActivityRunnerService.runWith(EDIT_TREE, studyUuid, List.of(anchorNodeId),
                () -> treeService.restoreNodes(studyUuid, nodeIds, anchorNodeId, userId));
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/nodes")
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

    @PutMapping(value = "/nodes/{parentUuid}/children-column-positions")
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

    @GetMapping(value = "/nodes/{id}")
    @Operation(summary = "get simplified node")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "simplified nodes (without children"),
        @ApiResponse(responseCode = "404", description = "The study or the node not found")})
    public ResponseEntity<AbstractNode> getNode(@Parameter(description = "study uuid") @PathVariable("studyUuid") UUID studyUuid,
                                                @Parameter(description = "node uuid") @PathVariable("id") UUID nodeId,
                                                @Parameter(description = "root network uuid") @RequestParam(value = "rootNetworkUuid", required = false) UUID rootNetworkUuid) {
        AbstractNode node = networkModificationTreeService.getNodeWithReferencesInfos(nodeId, rootNetworkUuid);
        return node != null ?
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(node)
                : ResponseEntity.notFound().build();
    }

    private void buildNewNode(UUID studyUuid, UUID referenceId, NetworkModificationNode createdNode, String userId) {
        treeService.getRootNetworksToBuildNewNode(studyUuid, referenceId, createdNode).forEach(rootNetworkUuid ->
                nodeActivityRunnerService.runWith(BUILD, studyUuid, rootNetworkUuid, List.of(createdNode.getId()),
                        () -> networkModificationService.buildNode(studyUuid, createdNode.getId(), rootNetworkUuid, userId)));
    }
}
