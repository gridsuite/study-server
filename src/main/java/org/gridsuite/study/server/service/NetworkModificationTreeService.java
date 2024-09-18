/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.networkmodificationtree.AbstractNodeRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.NetworkModificationNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.RootNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SIMULATION;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.dto.ComputationType.NON_EVACUATED_ENERGY_ANALYSIS;
import static org.gridsuite.study.server.dto.ComputationType.SECURITY_ANALYSIS;
import static org.gridsuite.study.server.dto.ComputationType.SENSITIVITY_ANALYSIS;
import static org.gridsuite.study.server.dto.ComputationType.SHORT_CIRCUIT;
import static org.gridsuite.study.server.dto.ComputationType.SHORT_CIRCUIT_ONE_BUS;
import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;
import static org.gridsuite.study.server.dto.ComputationType.STATE_ESTIMATION;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
@Service
public class NetworkModificationTreeService {

    public static final String ROOT_NODE_NAME = "Root";
    private static final String FIRST_VARIANT_ID = "first_variant_id";

    private final EnumMap<NodeType, AbstractNodeRepositoryProxy<?, ?, ?>> repositories = new EnumMap<>(NodeType.class);

    private final NodeRepository nodesRepository;

    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    private final NetworkModificationService networkModificationService;
    private final NotificationService notificationService;

    private final NetworkModificationTreeService self;

    public NetworkModificationTreeService(NodeRepository nodesRepository,
                                          RootNodeInfoRepository rootNodeInfoRepository,
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                                          NotificationService notificationService,
                                          NetworkModificationService networkModificationService,
                                          @Lazy NetworkModificationTreeService networkModificationTreeService
    ) {
        this.nodesRepository = nodesRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.networkModificationService = networkModificationService;
        this.notificationService = notificationService;
        repositories.put(NodeType.ROOT, new RootNodeInfoRepositoryProxy(rootNodeInfoRepository));
        repositories.put(NodeType.NETWORK_MODIFICATION, new NetworkModificationNodeInfoRepositoryProxy(networkModificationNodeInfoRepository));
        this.self = networkModificationTreeService;
    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public AbstractNode createNode(UUID studyUuid, UUID nodeId, AbstractNode nodeInfo, InsertMode insertMode, String userId) {
        Optional<NodeEntity> referenceNode = nodesRepository.findById(nodeId);
        return referenceNode.map(reference -> {
            assertNodeNameNotExist(studyUuid, nodeInfo.getName());

            if (insertMode.equals(InsertMode.BEFORE) && reference.getType().equals(NodeType.ROOT)) {
                throw new StudyException(NOT_ALLOWED);
            }
            NodeEntity parent = insertMode.equals(InsertMode.BEFORE) ? reference.getParentNode() : reference;
            NodeEntity node = nodesRepository.save(new NodeEntity(null, parent, nodeInfo.getType(), reference.getStudy(), false, null));
            nodeInfo.setId(node.getIdNode());
            repositories.get(node.getType()).createNodeInfo(nodeInfo);

            if (insertMode.equals(InsertMode.BEFORE)) {
                reference.setParentNode(node);
            } else if (insertMode.equals(InsertMode.AFTER)) {
                nodesRepository.findAllByParentNodeIdNode(nodeId).stream()
                        .filter(n -> !n.getIdNode().equals(node.getIdNode()))
                        .forEach(child -> child.setParentNode(node));
            }
            notificationService.emitNodeInserted(self.getStudyUuidForNodeId(nodeId), parent.getIdNode(), node.getIdNode(), insertMode, nodeId);
            // userId is null when creating initial nodes, we don't need to send element update notifications in this case
            if (userId != null) {
                notificationService.emitElementUpdated(studyUuid, userId);
            }
            return nodeInfo;
        }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public UUID duplicateStudyNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        NodeEntity anchorNode = nodesRepository.findById(anchorNodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        NodeEntity parent = insertMode == InsertMode.BEFORE ? anchorNode.getParentNode() : anchorNode;
        UUID newNodeUUID = duplicateNode(nodeToCopyUuid, anchorNodeUuid, insertMode);
        notificationService.emitNodeInserted(anchorNode.getStudy().getId(), parent.getIdNode(), newNodeUUID, insertMode, anchorNodeUuid);
        return newNodeUUID;
    }

    private UUID duplicateNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        Optional<NodeEntity> anchorNodeOpt = nodesRepository.findById(anchorNodeUuid);
        NodeEntity anchorNodeEntity = anchorNodeOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (insertMode.equals(InsertMode.BEFORE) && anchorNodeEntity.getType().equals(NodeType.ROOT)) {
            throw new StudyException(NOT_ALLOWED);
        }

        Optional<NodeEntity> nodeToCopyOpt = nodesRepository.findById(nodeToCopyUuid);
        NodeEntity nodeToCopyEntity = nodeToCopyOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));

        UUID newGroupUuid = UUID.randomUUID();
        UUID modificationGroupUuid = self.getModificationGroupUuid(nodeToCopyUuid);
        UUID newReportUuid = UUID.randomUUID();
        //First we create the modification group
        networkModificationService.createModifications(modificationGroupUuid, newGroupUuid);

        NodeEntity parent = insertMode.equals(InsertMode.BEFORE) ?
                anchorNodeEntity.getParentNode() : anchorNodeEntity;
        //Then we create the node
        NodeEntity node = nodesRepository.save(new NodeEntity(null, parent, nodeToCopyEntity.getType(), anchorNodeEntity.getStudy(), false, null));

        if (insertMode.equals(InsertMode.BEFORE)) {
            anchorNodeEntity.setParentNode(node);
        } else if (insertMode.equals(InsertMode.AFTER)) {
            nodesRepository.findAllByParentNodeIdNode(anchorNodeUuid).stream()
                    .filter(n -> !n.getIdNode().equals(node.getIdNode()))
                    .forEach(child -> child.setParentNode(node));
        }

        //And the modification node info
        NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity = networkModificationNodeInfoRepository.findById(nodeToCopyUuid).orElseThrow(() -> new StudyException(GET_MODIFICATIONS_FAILED));
        NetworkModificationNodeInfoEntity newNetworkModificationNodeInfoEntity = new NetworkModificationNodeInfoEntity(
                newGroupUuid,
                UUID.randomUUID().toString(),
                new HashSet<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                NodeBuildStatus.from(BuildStatus.NOT_BUILT).toEntity(),
                new HashMap<>(),
                new HashMap<>()
        );
        UUID studyUuid = anchorNodeEntity.getStudy().getId();
        newNetworkModificationNodeInfoEntity.setName(getSuffixedNodeName(studyUuid, networkModificationNodeInfoEntity.getName()));
        newNetworkModificationNodeInfoEntity.setDescription(networkModificationNodeInfoEntity.getDescription());
        newNetworkModificationNodeInfoEntity.setIdNode(node.getIdNode());
        newNetworkModificationNodeInfoEntity.setModificationReports(new HashMap<>(Map.of(node.getIdNode(), newReportUuid)));
        networkModificationNodeInfoRepository.save(newNetworkModificationNodeInfoEntity);

        return node.getIdNode();
    }

    @Transactional
    public UUID duplicateStudySubtree(UUID parentNodeToCopyUuid, UUID anchorNodeUuid, Set<UUID> newlyCreatedNodes) {
        List<NodeEntity> children = getChildrenByParentUuid(parentNodeToCopyUuid);
        UUID newParentUuid = duplicateNode(parentNodeToCopyUuid, anchorNodeUuid, InsertMode.CHILD);
        newlyCreatedNodes.add(newParentUuid);

        children.forEach(child -> {
            if (!newlyCreatedNodes.contains(child.getIdNode())) {
                self.duplicateStudySubtree(child.getIdNode(), newParentUuid, newlyCreatedNodes);
            }
        });
        return newParentUuid;
    }

    @Transactional
    public void moveStudyNode(UUID nodeToMoveUuid, UUID anchorNodeUuid, InsertMode insertMode) {        //if we try to move a node around itself, nothing happens
        if (nodeToMoveUuid.equals(anchorNodeUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        NodeEntity anchorNode = nodesRepository.findById(anchorNodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        NodeEntity parent;
        if (insertMode == InsertMode.BEFORE) {
            if (anchorNode.getType() == NodeType.ROOT) {
                throw new StudyException(NOT_ALLOWED);
            }
            parent = anchorNode.getParentNode();
            if (parent.getIdNode().equals(nodeToMoveUuid)) {
                // If anchor's previous parent is the node to move, we use anchor's grandparent.
                parent = parent.getParentNode();
            }
        } else {
            parent = anchorNode;
        }
        UUID studyUuid = moveNode(nodeToMoveUuid, anchorNodeUuid, insertMode);
        notificationService.emitNodeMoved(studyUuid, parent.getIdNode(), nodeToMoveUuid, insertMode, anchorNodeUuid);
    }

    private UUID moveNode(UUID nodeToMoveUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        Optional<NodeEntity> nodeToMoveOpt = nodesRepository.findById(nodeToMoveUuid);
        NodeEntity nodeToMoveEntity = nodeToMoveOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));

        nodesRepository.findAllByParentNodeIdNode(nodeToMoveUuid)
                .forEach(child -> child.setParentNode(nodeToMoveEntity.getParentNode()));

        Optional<NodeEntity> anchorNodeOpt = nodesRepository.findById(anchorNodeUuid);
        NodeEntity anchorNodeEntity = anchorNodeOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));

        if (insertMode.equals(InsertMode.BEFORE) && anchorNodeEntity.getType().equals(NodeType.ROOT)) {
            throw new StudyException(NOT_ALLOWED);
        }

        NodeEntity parent = insertMode.equals(InsertMode.BEFORE) ?
                anchorNodeEntity.getParentNode() : anchorNodeEntity;

        if (insertMode.equals(InsertMode.BEFORE)) {
            anchorNodeEntity.setParentNode(nodeToMoveEntity);
        } else if (insertMode.equals(InsertMode.AFTER)) {
            nodesRepository.findAllByParentNodeIdNode(anchorNodeUuid).stream()
                    .filter(n -> !n.getIdNode().equals(nodeToMoveEntity.getIdNode()))
                    .forEach(child -> child.setParentNode(nodeToMoveEntity));
        }

        nodeToMoveEntity.setParentNode(parent);
        return anchorNodeEntity.getStudy().getId();
    }

    @Transactional
    public void moveStudySubtree(UUID parentNodeToMoveUuid, UUID anchorNodeUuid) {
        List<NodeEntity> children = getChildrenByParentUuid(parentNodeToMoveUuid);
        moveNode(parentNodeToMoveUuid, anchorNodeUuid, InsertMode.CHILD);
        children.forEach(child -> self.moveStudySubtree(child.getIdNode(), parentNodeToMoveUuid));
    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public List<UUID> doDeleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren, DeleteNodeInfos deleteNodeInfos) {
        List<UUID> removedNodes = new ArrayList<>();
        UUID studyId = self.getStudyUuidForNodeId(nodeId);
        deleteNodes(nodeId, deleteChildren, false, removedNodes, deleteNodeInfos);
        notificationService.emitNodesDeleted(studyId, removedNodes, deleteChildren);
        return removedNodes;
    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public void doStashNode(UUID studyUuid, UUID nodeId, boolean stashChildren) {
        List<UUID> stashedNodes = new ArrayList<>();
        UUID studyId = self.getStudyUuidForNodeId(nodeId);
        stashNodes(nodeId, stashChildren, stashedNodes, true);
        notificationService.emitNodesDeleted(studyId, stashedNodes, stashChildren);
    }

    @Transactional(readOnly = true)
    public UUID getStudyUuidForNodeId(UUID id) {
        Optional<NodeEntity> node = nodesRepository.findById(id);
        return node.orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND)).getStudy().getId();
    }

    private void stashNodes(UUID id, boolean stashChildren, List<UUID> stashedNodes, boolean firstIteration) {
        Optional<NodeEntity> optNodeToStash = nodesRepository.findById(id);
        optNodeToStash.ifPresent(nodeToStash -> {
            UUID modificationGroupUuid = self.getModificationGroupUuid(nodeToStash.getIdNode());
            networkModificationService.deleteStashedModifications(modificationGroupUuid);
            if (!stashChildren) {
                nodesRepository.findAllByParentNodeIdNode(id).forEach(node -> node.setParentNode(nodeToStash.getParentNode()));
            } else {
                nodesRepository.findAllByParentNodeIdNode(id)
                        .forEach(child -> stashNodes(child.getIdNode(), true, stashedNodes, false));
            }
            stashedNodes.add(id);
            nodeToStash.setStashed(true);
            nodeToStash.setStashDate(Instant.now());
            //We only unlink the first deleted node so the rest of the tree is still connected as it was
            if (firstIteration) {
                nodeToStash.setParentNode(null);
            }
        });
    }

    private void deleteNodes(UUID id, boolean deleteChildren, boolean allowDeleteRoot, List<UUID> removedNodes, DeleteNodeInfos deleteNodeInfos) {
        Optional<NodeEntity> optNodeToDelete = nodesRepository.findById(id);
        optNodeToDelete.ifPresent(nodeToDelete -> {
            /* root cannot be deleted by accident */
            if (!allowDeleteRoot && nodeToDelete.getType() == NodeType.ROOT) {
                throw new StudyException(CANT_DELETE_ROOT_NODE);
            }

            UUID modificationGroupUuid = repositories.get(nodeToDelete.getType()).getModificationGroupUuid(id);
            deleteNodeInfos.addModificationGroupUuid(modificationGroupUuid);

            // delete all modification reports
            repositories.get(nodeToDelete.getType()).getModificationReports(nodeToDelete.getIdNode()).entrySet().stream().forEach(entry -> {
                deleteNodeInfos.addReportUuid(entry.getValue());
            });

            // delete all computation reports
            repositories.get(nodeToDelete.getType()).getComputationReports(nodeToDelete.getIdNode()).entrySet().stream().forEach(entry -> {
                deleteNodeInfos.addReportUuid(entry.getValue());
            });

            String variantId = repositories.get(nodeToDelete.getType()).getVariantId(id);
            if (!StringUtils.isBlank(variantId)) {
                deleteNodeInfos.addVariantId(variantId);
            }

            UUID loadFlowResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, LOAD_FLOW);
            if (loadFlowResultUuid != null) {
                deleteNodeInfos.addLoadFlowResultUuid(loadFlowResultUuid);
            }

            UUID securityAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, SECURITY_ANALYSIS);
            if (securityAnalysisResultUuid != null) {
                deleteNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
            }

            UUID sensitivityAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, SENSITIVITY_ANALYSIS);
            if (sensitivityAnalysisResultUuid != null) {
                deleteNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
            }

            UUID nonEvacuatedEnergyResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, NON_EVACUATED_ENERGY_ANALYSIS);
            if (nonEvacuatedEnergyResultUuid != null) {
                deleteNodeInfos.addNonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid);
            }

            UUID shortCircuitAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, SHORT_CIRCUIT);
            if (shortCircuitAnalysisResultUuid != null) {
                deleteNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
            }

            UUID oneBusShortCircuitAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, SHORT_CIRCUIT_ONE_BUS);
            if (oneBusShortCircuitAnalysisResultUuid != null) {
                deleteNodeInfos.addOneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid);
            }

            UUID voltageInitResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, VOLTAGE_INITIALIZATION);
            if (voltageInitResultUuid != null) {
                deleteNodeInfos.addVoltageInitResultUuid(voltageInitResultUuid);
            }

            UUID dynamicSimulationResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, DYNAMIC_SIMULATION);
            if (dynamicSimulationResultUuid != null) {
                deleteNodeInfos.addDynamicSimulationResultUuid(dynamicSimulationResultUuid);
            }

            UUID stateEstimationResultUuid = repositories.get(nodeToDelete.getType()).getComputationResultUuid(id, STATE_ESTIMATION);
            if (stateEstimationResultUuid != null) {
                deleteNodeInfos.addStateEstimationResultUuid(stateEstimationResultUuid);
            }

            if (!deleteChildren) {
                nodesRepository.findAllByParentNodeIdNode(id).forEach(node -> node.setParentNode(nodeToDelete.getParentNode()));
            } else {
                nodesRepository.findAllByParentNodeIdNode(id)
                        .forEach(child -> deleteNodes(child.getIdNode(), true, false, removedNodes, deleteNodeInfos));
            }
            removedNodes.add(id);
            repositories.get(nodeToDelete.getType()).deleteByNodeId(id);
            nodesRepository.delete(nodeToDelete);
        });
    }

    public List<NodeEntity> getChildrenByParentUuid(UUID parentUuid) {
        return nodesRepository.findAllByParentNodeIdNode(parentUuid);
    }

    @Transactional
    public void doDeleteTree(UUID studyId) {
        try {
            List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
            repositories.forEach((key, repository) ->
                    repository.deleteAll(
                            nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet()))
            );
            nodesRepository.deleteAll(nodes);
        } catch (EntityNotFoundException ignored) {
            // nothing to do
        }
    }

    @Transactional
    public NodeEntity createRoot(StudyEntity study, UUID importReportUuid) {
        NodeEntity node = nodesRepository.save(new NodeEntity(null, null, NodeType.ROOT, study, false, null));
        var root = RootNode.builder()
                .studyId(study.getId())
                .id(node.getIdNode())
                .name(ROOT_NODE_NAME)
                .readOnly(true)
                .reportUuid(importReportUuid)
                .build();
        repositories.get(node.getType()).createNodeInfo(root);
        return node;
    }

    @Transactional
    public RootNode getStudyTree(UUID studyId) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
        if (nodes.isEmpty()) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }
        Map<UUID, AbstractNode> fullMap = new HashMap<>();
        repositories.forEach((key, repository) ->
                fullMap.putAll(repository.getAll(nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet()))));

        nodes.stream()
                .filter(n -> n.getParentNode() != null)
                .forEach(node -> fullMap.get(node.getParentNode().getIdNode()).getChildren().add(fullMap.get(node.getIdNode())));
        var root = (RootNode) fullMap.get(nodes.stream().filter(n -> n.getType().equals(NodeType.ROOT)).findFirst().orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND)).getIdNode());
        if (root != null) {
            root.setStudyId(studyId);
        }
        return root;
    }

    @Transactional
    public NetworkModificationNode getStudySubtree(UUID studyId, UUID parentNodeUuid) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
        Map<UUID, AbstractNode> fullMap = new HashMap<>();
        repositories.forEach((key, repository) ->
                fullMap.putAll(repository.getAll(nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet()))));

        nodes.stream()
                .filter(n -> n.getParentNode() != null)
                .forEach(node -> fullMap.get(node.getParentNode().getIdNode()).getChildren().add(fullMap.get(node.getIdNode())));
        return (NetworkModificationNode) fullMap.get(parentNodeUuid);
    }

    @Transactional
    public void cloneStudyTree(AbstractNode nodeToDuplicate, UUID nodeParentId, StudyEntity study) {
        UUID rootId = null;
        if (NodeType.ROOT.equals(nodeToDuplicate.getType())) {
            rootId = getStudyRootNodeUuid(study.getId());
        }
        UUID referenceParentNodeId = rootId != null ? rootId : nodeParentId;

        nodeToDuplicate.getChildren().forEach(sourceNode -> {
            UUID newModificationGroupId = UUID.randomUUID();
            UUID newReportUuid = UUID.randomUUID();
            UUID nextParentId = null;

            if (sourceNode instanceof NetworkModificationNode model) {
                UUID modificationGroupToDuplicateId = model.getModificationGroupUuid();
                model.setModificationGroupUuid(newModificationGroupId);
                model.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
                model.setLoadFlowResultUuid(null);
                model.setSecurityAnalysisResultUuid(null);
                model.setSensitivityAnalysisResultUuid(null);
                model.setNonEvacuatedEnergyResultUuid(null);
                model.setShortCircuitAnalysisResultUuid(null);
                model.setOneBusShortCircuitAnalysisResultUuid(null);
                model.setVoltageInitResultUuid(null);
                model.setStateEstimationResultUuid(null);
                model.setModificationReports(new HashMap<>(Map.of(model.getId(), newReportUuid)));
                model.setComputationsReports(new HashMap<>());

                nextParentId = self.createNode(study.getId(), referenceParentNodeId, model, InsertMode.CHILD, null).getId();
                networkModificationService.createModifications(modificationGroupToDuplicateId, newModificationGroupId);
            }
            if (nextParentId != null) {
                self.cloneStudyTree(sourceNode, nextParentId, study);
            }
        });
    }

    @Transactional
    public void createBasicTree(StudyEntity studyEntity, UUID importReportUuid) {
        // create 2 nodes : root node, modification node 0
        NodeEntity rootNodeEntity = self.createRoot(studyEntity, importReportUuid);
        NetworkModificationNode modificationNode = NetworkModificationNode
                .builder()
                .name("N1")
                .variantId(FIRST_VARIANT_ID)
                .nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT))
                .build();
        self.createNode(studyEntity.getId(), rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER, null);
    }

    @Transactional
    public void updateNode(UUID studyUuid, AbstractNode node, String userId) {
        NetworkModificationNodeInfoEntity networkModificationNode = networkModificationNodeInfoRepository.findById(node.getId()).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (!networkModificationNode.getName().equals(node.getName())) {
            assertNodeNameNotExist(studyUuid, node.getName());
        }
        repositories.get(node.getType()).updateNode(node);
        if (isRenameNode(node)) {
            notificationService.emitNodeRenamed(self.getStudyUuidForNodeId(node.getId()), node.getId());
        } else {
            notificationService.emitNodesChanged(self.getStudyUuidForNodeId(node.getId()), Collections.singletonList(node.getId()));
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private boolean isRenameNode(AbstractNode node) {
        NetworkModificationNode renameNode = NetworkModificationNode.builder()
                .id(node.getId())
                .name(node.getName())
                .type(node.getType())
                .build();
        return renameNode.equals(node);
    }

    private NodeEntity getNodeEntity(UUID nodeId) {
        return nodesRepository.findById(nodeId).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    private AbstractNode getSimpleNode(UUID nodeId) {
        return nodesRepository.findById(nodeId).map(n -> repositories.get(n.getType()).getNode(nodeId)).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public AbstractNode getNode(UUID nodeId) {
        AbstractNode node = getSimpleNode(nodeId);
        nodesRepository.findAllByParentNodeIdNode(node.getId()).stream().map(NodeEntity::getIdNode).forEach(node.getChildrenIds()::add);
        return node;
    }

    public UUID getStudyRootNodeUuid(UUID studyId) {
        return nodesRepository.findByStudyIdAndType(studyId, NodeType.ROOT).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND)).getIdNode();
    }

    private void assertNodeNameNotExist(UUID studyUuid, String nodeName) {
        if (self.isNodeNameExists(studyUuid, nodeName)) {
            throw new StudyException(NODE_NAME_ALREADY_EXIST);
        }
    }

    @Transactional(readOnly = true)
    public boolean isNodeNameExists(UUID studyUuid, String nodeName) {
        return ROOT_NODE_NAME.equals(nodeName) || !networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(studyUuid, nodeName).stream().filter(abstractNodeInfoEntity -> !abstractNodeInfoEntity.getNode().isStashed()).toList().isEmpty();
    }

    @Transactional(readOnly = true)
    public String getUniqueNodeName(UUID studyUuid) {
        int counter = 1;
        List<String> studyNodeNames = networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid)
                .stream()
                .filter(s -> !s.getNode().isStashed())
                .map(AbstractNodeInfoEntity::getName)
                .toList();

        String namePrefix = "N";
        String uniqueName = StringUtils.EMPTY;
        while (StringUtils.EMPTY.equals(uniqueName) || studyNodeNames.contains(uniqueName)) {
            uniqueName = namePrefix + counter;
            ++counter;
        }

        return uniqueName;
    }

    private String getSuffixedNodeName(UUID studyUuid, String nodeName) {
        List<String> studyNodeNames = networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid)
                .stream()
                .map(AbstractNodeInfoEntity::getName)
                .toList();

        String uniqueName = nodeName;
        int i = 1;
        while (studyNodeNames.contains(uniqueName)) {
            uniqueName = nodeName + " (" + i + ")";
            i++;
        }
        return uniqueName;
    }

    @Transactional
    public String getVariantId(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getVariantId(nodeUuid)).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UUID getModificationGroupUuid(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getModificationGroupUuid(nodeUuid)).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    // Return json string because modification dtos are not available here
    @Transactional(readOnly = true)
    public String getNetworkModifications(@NonNull UUID nodeUuid, boolean onlyStashed, boolean onlyMetadata) {
        return networkModificationService.getModifications(self.getModificationGroupUuid(nodeUuid), onlyStashed, onlyMetadata);
    }

    private Integer getNetworkModificationsCount(@NonNull UUID nodeUuid, boolean stashed) {
        return networkModificationService.getModificationsCount(self.getModificationGroupUuid(nodeUuid), stashed);
    }

    @Transactional(readOnly = true)
    public boolean hasModifications(@NonNull UUID nodeUuid, boolean stashed) {
        return getNetworkModificationsCount(nodeUuid, stashed) > 0;
    }

    @Transactional
    public UUID getReportUuid(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getReportUuid(nodeUuid)).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<NodeModificationInfos> getAllNodesModificationInfos(UUID studyUuid) {
        List<NodeModificationInfos> nodesModificationInfos = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            NodeModificationInfos nodeModificationInfos = repositories.get(n.getType()).getNodeModificationInfos(n.getIdNode());
            if (nodeModificationInfos != null) {
                nodesModificationInfos.add(nodeModificationInfos);
            }
        });
        return nodesModificationInfos;
    }

    public List<Pair<AbstractNode, Integer>> getStashedNodes(UUID studyUuid) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyIdAndStashedAndParentNodeIdNodeOrderByStashDateDesc(studyUuid, true, null);
        List<Pair<AbstractNode, Integer>> result = new ArrayList<>();
        repositories.get(NodeType.NETWORK_MODIFICATION).getAllInOrder(nodes.stream().map(NodeEntity::getIdNode).toList())
                .forEach(abstractNode -> {
                    ArrayList<UUID> children = new ArrayList<>();
                    doGetChildren(abstractNode.getId(), children);
                    result.add(Pair.of(abstractNode, children.size()));
                });
        return result;
    }

    @Transactional
    public void restoreNode(UUID studyId, List<UUID> nodeIds, UUID anchorNodeId) {
        for (UUID nodeId : nodeIds) {
            NodeEntity nodeToRestore = nodesRepository.findById(nodeId).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
            NodeEntity anchorNode = nodesRepository.findById(anchorNodeId).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
            NetworkModificationNodeInfoEntity modificationNodeToRestore = networkModificationNodeInfoRepository.findById(nodeToRestore.getIdNode()).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
            if (self.isNodeNameExists(studyId, modificationNodeToRestore.getName())) {
                String newName = getSuffixedNodeName(studyId, modificationNodeToRestore.getName());
                modificationNodeToRestore.setName(newName);
                networkModificationNodeInfoRepository.save(modificationNodeToRestore);
            }
            nodeToRestore.setParentNode(anchorNode);
            nodeToRestore.setStashed(false);
            nodeToRestore.setStashDate(null);
            nodesRepository.save(nodeToRestore);
            notificationService.emitNodeInserted(studyId, anchorNodeId, nodeId, InsertMode.AFTER, anchorNodeId);
            restoreNodeChildren(studyId, nodeId);
        }
    }

    @Transactional
    public void updateComputationReportUuid(UUID nodeUuid, ComputationType computationType, UUID reportUuid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateComputationReportUuid(nodeUuid, reportUuid, computationType));
    }

    @Transactional
    public Map<String, UUID> getComputationReports(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getComputationReports(nodeUuid)).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    @Transactional
    public void setModificationReports(UUID nodeUuid, Map<UUID, UUID> modificationReports) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            repositories.get(n.getType()).setModificationReports(nodeUuid, modificationReports);
        });
    }

    @Transactional
    public Map<UUID, UUID> getModificationReports(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getModificationReports(nodeUuid)).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    private void restoreNodeChildren(UUID studyId, UUID parentNodeId) {
        nodesRepository.findAllByParentNodeIdNode(parentNodeId).forEach(nodeEntity -> {
            NetworkModificationNodeInfoEntity modificationNodeToRestore = networkModificationNodeInfoRepository.findById(nodeEntity.getIdNode()).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
            if (self.isNodeNameExists(studyId, modificationNodeToRestore.getName())) {
                String newName = getSuffixedNodeName(studyId, modificationNodeToRestore.getName());
                modificationNodeToRestore.setName(newName);
                networkModificationNodeInfoRepository.save(modificationNodeToRestore);
            }
            nodeEntity.setStashed(false);
            nodeEntity.setStashDate(null);
            nodesRepository.save(nodeEntity);
            notificationService.emitNodeInserted(studyId, parentNodeId, nodeEntity.getIdNode(), InsertMode.AFTER, parentNodeId);
            restoreNodeChildren(studyId, nodeEntity.getIdNode());
        });
    }

    public List<NodeEntity> getAllNodes(UUID studyUuid) {
        return nodesRepository.findAllByStudyId(studyUuid);
    }

    @Transactional
    public void updateComputationResultUuid(UUID nodeUuid, UUID computationResultUuid, ComputationType computationType) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateComputationResultUuid(nodeUuid, computationResultUuid, computationType));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getComputationResultUuid(UUID nodeUuid, ComputationType computationType) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getComputationResultUuid(nodeUuid, computationType));
    }

    @Transactional(readOnly = true)
    public List<UUID> getComputationResultUuids(UUID studyUuid, ComputationType computationType) {
        List<UUID> uuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            UUID uuid = repositories.get(n.getType()).getComputationResultUuid(n.getIdNode(), computationType);
            if (uuid != null) {
                uuids.add(uuid);
            }
        });
        return uuids;
    }

    private void getSecurityAnalysisResultUuids(UUID nodeUuid, List<UUID> uuids) {
        nodesRepository.findById(nodeUuid).flatMap(n -> Optional.ofNullable(repositories.get(n.getType()).getComputationResultUuid(nodeUuid, SECURITY_ANALYSIS))).ifPresent(uuids::add);
        nodesRepository.findAllByParentNodeIdNode(nodeUuid)
            .forEach(child -> getSecurityAnalysisResultUuids(child.getIdNode(), uuids));
    }

    @Transactional(readOnly = true)
    public List<UUID> getSecurityAnalysisResultUuidsFromNode(UUID nodeUuid) {
        List<UUID> uuids = new ArrayList<>();
        getSecurityAnalysisResultUuids(nodeUuid, uuids);
        return uuids;
    }

    @Transactional(readOnly = true)
    public List<UUID> getShortCircuitResultUuids(UUID studyUuid) {
        List<UUID> uuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            // we need to check one bus and all bus
            UUID uuidOneBus = repositories.get(n.getType()).getComputationResultUuid(n.getIdNode(), SHORT_CIRCUIT_ONE_BUS);
            UUID uuidAllBus = repositories.get(n.getType()).getComputationResultUuid(n.getIdNode(), SHORT_CIRCUIT);
            if (uuidOneBus != null) {
                uuids.add(uuidOneBus);
            }
            if (uuidAllBus != null) {
                uuids.add(uuidAllBus);
            }
        });
        return uuids;
    }

    private void getBuildInfos(NodeEntity nodeEntity, BuildInfos buildInfos) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            if (modificationNode.getModificationsToExclude() != null) {
                buildInfos.addModificationsToExclude(modificationNode.getModificationsToExclude());
            }
            if (!modificationNode.getNodeBuildStatus().isBuilt()) {
                UUID reportUuid = getReportUuid(nodeEntity.getIdNode());
                buildInfos.insertModificationInfos(modificationNode.getModificationGroupUuid(), new ReportInfos(reportUuid, modificationNode.getId()));
                getBuildInfos(nodeEntity.getParentNode(), buildInfos);
            } else {
                buildInfos.setOriginVariantId(self.getVariantId(nodeEntity.getIdNode()));
            }
        }
    }

    @Transactional
    public BuildInfos getBuildInfos(UUID nodeUuid) {
        BuildInfos buildInfos = new BuildInfos();

        nodesRepository.findById(nodeUuid).ifPresentOrElse(entity -> {
            if (entity.getType() != NodeType.NETWORK_MODIFICATION) {  // nodeUuid must be a modification node
                throw new StudyException(BAD_NODE_TYPE, "The node " + entity.getIdNode() + " is not a modification node");
            } else {
                buildInfos.setDestinationVariantId(self.getVariantId(nodeUuid));
                getBuildInfos(entity, buildInfos);
            }
        }, () -> {
            throw new StudyException(ELEMENT_NOT_FOUND);
        });

        return buildInfos;
    }

    private void fillInvalidateNodeInfos(NodeEntity node, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus,
                                         boolean deleteVoltageInitResults) {
        if (!invalidateOnlyChildrenBuildStatus) {
            // we want to delete associated report and variant in this case
            repositories.get(node.getType()).getModificationReports(node.getIdNode()).entrySet().stream().forEach(entry -> {
                invalidateNodeInfos.addReportUuid(entry.getValue());
            });
            invalidateNodeInfos.addVariantId(repositories.get(node.getType()).getVariantId(node.getIdNode()));
        }

        // we want to delete associated computation reports exept for voltage initialization : only if deleteVoltageInitResults is true
        repositories.get(node.getType()).getComputationReports(node.getIdNode()).entrySet().stream().forEach(entry -> {
            if (deleteVoltageInitResults || !VOLTAGE_INITIALIZATION.name().equals(entry.getKey())) {
                invalidateNodeInfos.addReportUuid(entry.getValue());
            }
        });

        UUID loadFlowResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), LOAD_FLOW);
        if (loadFlowResultUuid != null) {
            invalidateNodeInfos.addLoadFlowResultUuid(loadFlowResultUuid);
        }

        UUID securityAnalysisResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), SECURITY_ANALYSIS);
        if (securityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        }

        UUID sensitivityAnalysisResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), SENSITIVITY_ANALYSIS);
        if (sensitivityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
        }

        UUID nonEvacuatedEnergyResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), NON_EVACUATED_ENERGY_ANALYSIS);
        if (nonEvacuatedEnergyResultUuid != null) {
            invalidateNodeInfos.addNonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid);
        }

        UUID shortCircuitAnalysisResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), SHORT_CIRCUIT);
        if (shortCircuitAnalysisResultUuid != null) {
            invalidateNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
        }

        UUID oneBusShortCircuitAnalysisResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), SHORT_CIRCUIT_ONE_BUS);
        if (oneBusShortCircuitAnalysisResultUuid != null) {
            invalidateNodeInfos.addOneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid);
        }

        if (deleteVoltageInitResults) {
            UUID voltageInitResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), VOLTAGE_INITIALIZATION);
            if (voltageInitResultUuid != null) {
                invalidateNodeInfos.addVoltageInitResultUuid(voltageInitResultUuid);
            }
        }

        UUID stateEstimationResultUuid = repositories.get(node.getType()).getComputationResultUuid(node.getIdNode(), STATE_ESTIMATION);
        if (stateEstimationResultUuid != null) {
            invalidateNodeInfos.addStateEstimationResultUuid(stateEstimationResultUuid);
        }
    }

    @Transactional
    public void invalidateBuild(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos, boolean deleteVoltageInitResults) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            invalidateNodeProper(n, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes, deleteVoltageInitResults);
            invalidateChildrenBuildStatus(n, changedNodes, invalidateNodeInfos, deleteVoltageInitResults);
        });

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
    }

    @Transactional
    // method used when moving a node to invalidate it without impacting other nodes
    public void invalidateBuildOfNodeOnly(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos, boolean deleteVoltageInitResults) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n ->
                invalidateNodeProper(n, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes, deleteVoltageInitResults)
        );

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
    }

    private void invalidateChildrenBuildStatus(NodeEntity nodeEntity, List<UUID> changedNodes, InvalidateNodeInfos invalidateNodeInfos,
                                               boolean deleteVoltageInitResults) {
        nodesRepository.findAllByParentNodeIdNode(nodeEntity.getIdNode())
                .forEach(child -> {
                    invalidateNodeProper(child, invalidateNodeInfos, false, changedNodes, deleteVoltageInitResults);
                    invalidateChildrenBuildStatus(child, changedNodes, invalidateNodeInfos, deleteVoltageInitResults);
                });
    }

    private void invalidateNodeProper(NodeEntity child, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus,
                                      List<UUID> changedNodes, boolean deleteVoltageInitResults) {
        UUID childUuid = child.getIdNode();
        // No need to invalidate a node with a status different of "BUILT"
        AbstractNodeRepositoryProxy<?, ?, ?> nodeRepository = repositories.get(child.getType());
        if (nodeRepository.getNodeBuildStatus(child.getIdNode()).isBuilt()) {
            fillInvalidateNodeInfos(child, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, deleteVoltageInitResults);
            if (!invalidateOnlyChildrenBuildStatus) {
                nodeRepository.invalidateNodeBuildStatus(childUuid, changedNodes);
            }
            nodeRepository.updateComputationResultUuid(childUuid, null, LOAD_FLOW);
            nodeRepository.updateComputationResultUuid(childUuid, null, SECURITY_ANALYSIS);
            nodeRepository.updateComputationResultUuid(childUuid, null, SENSITIVITY_ANALYSIS);
            nodeRepository.updateComputationResultUuid(childUuid, null, NON_EVACUATED_ENERGY_ANALYSIS);
            nodeRepository.updateComputationResultUuid(childUuid, null, SHORT_CIRCUIT);
            nodeRepository.updateComputationResultUuid(childUuid, null, SHORT_CIRCUIT_ONE_BUS);
            if (deleteVoltageInitResults) {
                nodeRepository.updateComputationResultUuid(childUuid, null, VOLTAGE_INITIALIZATION);
            }
            nodeRepository.updateComputationResultUuid(childUuid, null, STATE_ESTIMATION);
        }
    }

    @Transactional
    public void updateNodeBuildStatus(UUID nodeUuid, NodeBuildStatus nodeBuildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        AbstractNodeRepositoryProxy<?, ?, ?> nodeRepositoryProxy = repositories.get(nodeEntity.getType());
        NodeBuildStatus currentNodeStatus = nodeRepositoryProxy.getNodeBuildStatus(nodeEntity.getIdNode());

        BuildStatus newGlobalStatus;
        BuildStatus newLocalStatus;
        if (nodeBuildStatus.isBuilt()) {
            newLocalStatus = nodeBuildStatus.getLocalBuildStatus().max(currentNodeStatus.getLocalBuildStatus());
            NodeEntity previousBuiltNode = doGetLastParentNodeBuilt(nodeEntity);
            BuildStatus previousGlobalBuildStatus = repositories.get(previousBuiltNode.getType()).getNodeBuildStatus(previousBuiltNode.getIdNode()).getGlobalBuildStatus();
            newGlobalStatus = nodeBuildStatus.getGlobalBuildStatus().max(previousGlobalBuildStatus);
        } else {
            newLocalStatus = nodeBuildStatus.getLocalBuildStatus();
            newGlobalStatus = nodeBuildStatus.getGlobalBuildStatus();
        }
        NodeBuildStatus newNodeStatus = NodeBuildStatus.from(newLocalStatus, newGlobalStatus);
        if (newNodeStatus.equals(currentNodeStatus)) {
            return;
        }

        nodeRepositoryProxy.updateNodeBuildStatus(nodeUuid, newNodeStatus, changedNodes);
        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes);
    }

    @Transactional(readOnly = true)
    public NodeBuildStatus getNodeBuildStatus(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getNodeBuildStatus(nodeUuid)).orElse(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getParentNodeUuid(UUID nodeUuid) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        return (nodeEntity.getType() == NodeType.ROOT) ? Optional.empty() : Optional.of(nodeEntity.getParentNode().getIdNode());
    }

    @Transactional(readOnly = true)
    public UUID doGetLastParentNodeBuiltUuid(UUID nodeUuid) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        return doGetLastParentNodeBuilt(nodeEntity).getIdNode();
    }

    private NodeEntity doGetLastParentNodeBuilt(NodeEntity nodeEntity) {
        if (nodeEntity.getType() == NodeType.ROOT) {
            return nodeEntity;
        } else if (self.getNodeBuildStatus(nodeEntity.getIdNode()).isBuilt()) {
            return nodeEntity;
        } else {
            return doGetLastParentNodeBuilt(nodeEntity.getParentNode());
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAncestor(UUID nodeUuid, UUID ancestorNodeUuid) {
        if (nodeUuid.equals(ancestorNodeUuid)) {
            return true;
        }
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        if (nodeEntity.getType() == NodeType.ROOT) {
            return false;
        } else {
            return self.hasAncestor(nodeEntity.getParentNode().getIdNode(), ancestorNodeUuid);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Boolean> isReadOnly(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).isReadOnly(nodeUuid));
    }

    @Transactional
    public void handleExcludeModification(UUID nodeUuid, UUID modificationUUid, boolean active) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).handleExcludeModification(nodeUuid, modificationUUid, active));
    }

    @Transactional
    public void removeModificationsToExclude(UUID nodeUuid, List<UUID> modificationUUid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).removeModificationsToExclude(nodeUuid, modificationUUid));
    }

    @Transactional
    public NodeModificationInfos getNodeModificationInfos(UUID nodeUuid) {
        NodeModificationInfos nodeModificationInfos = nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getNodeModificationInfos(nodeUuid)).orElse(null);
        if (nodeModificationInfos == null) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }
        return nodeModificationInfos;
    }

    @Transactional(readOnly = true)
    public List<UUID> getChildren(UUID id) {
        List<UUID> children = new ArrayList<>();
        doGetChildren(id, children);
        return children;
    }

    private void doGetChildren(UUID id, List<UUID> children) {
        Optional<NodeEntity> optNode = nodesRepository.findById(id);
        optNode.ifPresent(node -> nodesRepository.findAllByParentNodeIdNode(id)
                .forEach(child -> {
                    children.add(child.getIdNode());
                    doGetChildren(child.getIdNode(), children);
                }));
    }

    // only used for tests
    @Transactional
    public UUID getParentNode(UUID nodeUuid, NodeType nodeType) {
        Optional<UUID> parentNodeUuidOpt = doGetParentNode(nodeUuid, nodeType);
        if (parentNodeUuidOpt.isEmpty()) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }

        return parentNodeUuidOpt.get();
    }

    private Optional<UUID> doGetParentNode(UUID nodeUuid, NodeType nodeType) {
        NodeEntity nodeEntity = nodesRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        if (nodeEntity.getType() == NodeType.ROOT && nodeType != NodeType.ROOT) {
            return Optional.empty();
        }
        if (nodeEntity.getType() == NodeType.ROOT || nodeEntity.getParentNode().getType() == nodeType) {
            return Optional.of(nodeEntity.getParentNode() != null ? nodeEntity.getParentNode().getIdNode() : nodeEntity.getIdNode());
        } else {
            return doGetParentNode(nodeEntity.getParentNode().getIdNode(), nodeType);
        }
    }

    public long countBuiltNodes(UUID studyUuid) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyIdAndTypeAndStashed(studyUuid, NodeType.NETWORK_MODIFICATION, false);
        // perform N queries, but it's fast: 25 ms for 400 nodes
        return nodes.stream().filter(n -> repositories.get(n.getType()).getNodeBuildStatus(n.getIdNode()).isBuilt()).count();
    }
}
