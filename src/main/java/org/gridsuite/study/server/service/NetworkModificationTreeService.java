/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import jakarta.persistence.EntityNotFoundException;
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
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.dto.ComputationType.*;

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
    private final RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    private final NetworkModificationTreeService self;
    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkService rootNetworkService;
    private final RootNodeInfoRepository rootNodeInfoRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    public NetworkModificationTreeService(NodeRepository nodesRepository,
                                          RootNodeInfoRepository rootNodeInfoRepository,
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                                          NotificationService notificationService,
                                          NetworkModificationService networkModificationService,
                                          RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository,
                                          @Lazy NetworkModificationTreeService networkModificationTreeService,
                                          RootNetworkRepository rootNetworkRepository,
                                          RootNetworkService rootNetworkService,
                                          RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        this.nodesRepository = nodesRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.networkModificationService = networkModificationService;
        this.notificationService = notificationService;
        this.rootNetworkNodeInfoRepository = rootNetworkNodeInfoRepository;
        repositories.put(NodeType.ROOT, new RootNodeInfoRepositoryProxy(rootNodeInfoRepository));
        repositories.put(NodeType.NETWORK_MODIFICATION, new NetworkModificationNodeInfoRepositoryProxy(networkModificationNodeInfoRepository));
        this.self = networkModificationTreeService;
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkService = rootNetworkService;
        this.rootNodeInfoRepository = rootNodeInfoRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    private NodeEntity createNetworkModificationNode(StudyEntity study, NodeEntity parentNode, NetworkModificationNode networkModificationNode) {
        NodeEntity newNode = nodesRepository.save(new NodeEntity(null, parentNode, NodeType.NETWORK_MODIFICATION, study, false, null));
        if (networkModificationNode.getModificationGroupUuid() == null) {
            networkModificationNode.setModificationGroupUuid(UUID.randomUUID());
        }

        networkModificationNodeInfoRepository.save(
            NetworkModificationNodeInfoEntity.builder()
                .modificationGroupUuid(networkModificationNode.getModificationGroupUuid())
                .idNode(newNode.getIdNode())
                .name(networkModificationNode.getName())
                .description(networkModificationNode.getDescription())
                .build()
        );
        return newNode;
    }

    // TODO test if studyUuid exist and have a node <nodeId>
    private NetworkModificationNode createAndInsertNode(StudyEntity study, UUID nodeId, NetworkModificationNode nodeInfo, InsertMode insertMode, String userId) {
        Optional<NodeEntity> referenceNode = nodesRepository.findById(nodeId);
        return referenceNode.map(reference -> {
            assertNodeNameNotExist(study.getId(), nodeInfo.getName());

            if (insertMode.equals(InsertMode.BEFORE) && reference.getType().equals(NodeType.ROOT)) {
                throw new StudyException(NOT_ALLOWED);
            }
            NodeEntity parent = insertMode.equals(InsertMode.BEFORE) ? reference.getParentNode() : reference;
            NodeEntity node = createNetworkModificationNode(study, parent, nodeInfo);
            nodeInfo.setId(node.getIdNode());

            if (insertMode.equals(InsertMode.BEFORE)) {
                reference.setParentNode(node);
            } else if (insertMode.equals(InsertMode.AFTER)) {
                nodesRepository.findAllByParentNodeIdNode(nodeId).stream()
                    .filter(n -> !n.getIdNode().equals(node.getIdNode()))
                    .forEach(child -> child.setParentNode(node));
            }
            notificationService.emitNodeInserted(study.getId(), parent.getIdNode(), node.getIdNode(), insertMode, nodeId);
            // userId is null when creating initial nodes, we don't need to send element update notifications in this case
            if (userId != null) {
                notificationService.emitElementUpdated(study.getId(), userId);
            }
            return nodeInfo;
        }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public NetworkModificationNode createNode(@NonNull StudyEntity study, @NonNull UUID nodeId, @NonNull NetworkModificationNode nodeInfo, @NonNull InsertMode insertMode, String userId) {
        // create new node
        // TODO return entity because newNode == nodeInfo
        NetworkModificationNode newNode = createAndInsertNode(study, nodeId, nodeInfo, insertMode, userId);

        NetworkModificationNodeInfoEntity newNodeInfoEntity = networkModificationNodeInfoRepository.getReferenceById(newNode.getId());
        rootNetworkNodeInfoService.createNodeLinks(study.getId(), newNodeInfoEntity, nodeInfo);

        return newNode;
    }

    @Transactional
    public UUID duplicateStudyNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, UUID rootNetworkUuid, InsertMode insertMode) {
        NodeEntity anchorNode = nodesRepository.findById(anchorNodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        NodeEntity parent = insertMode == InsertMode.BEFORE ? anchorNode.getParentNode() : anchorNode;
        UUID newNodeUUID = duplicateNode(nodeToCopyUuid, anchorNodeUuid, rootNetworkUuid, insertMode);
        notificationService.emitNodeInserted(anchorNode.getStudy().getId(), parent.getIdNode(), newNodeUUID, insertMode, anchorNodeUuid);
        return newNodeUUID;
    }

    private UUID duplicateNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, UUID rootNetworkUuid, InsertMode insertMode) {
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
        RootNetworkEntity rootNetworkEntity = rootNetworkRepository.findById(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        UUID studyUuid = anchorNodeEntity.getStudy().getId();
        NetworkModificationNodeInfoEntity newNetworkModificationNodeInfoEntity = NetworkModificationNodeInfoEntity.builder()
            .modificationGroupUuid(newGroupUuid)
            .name(getSuffixedNodeName(studyUuid, networkModificationNodeInfoEntity.getName()))
            .description(networkModificationNodeInfoEntity.getDescription())
            .idNode(node.getIdNode())
            .build();

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = RootNetworkNodeInfoEntity.builder()
            .variantId(UUID.randomUUID().toString())
            .nodeBuildStatus(NodeBuildStatusEmbeddable.from(BuildStatus.NOT_BUILT))
            .modificationReports(new HashMap<>(Map.of(node.getIdNode(), newReportUuid)))
            .modificationsToExclude(new HashSet<>())
            .build();
        newNetworkModificationNodeInfoEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);

        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity);
        rootNetworkRepository.save(rootNetworkEntity);
        networkModificationNodeInfoRepository.save(newNetworkModificationNodeInfoEntity);

        return node.getIdNode();
    }

    @Transactional
    public UUID duplicateStudySubtree(UUID parentNodeToCopyUuid, UUID anchorNodeUuid, UUID rootNetworkUuid, Set<UUID> newlyCreatedNodes) {
        List<NodeEntity> children = getChildrenByParentUuid(parentNodeToCopyUuid);
        UUID newParentUuid = duplicateNode(parentNodeToCopyUuid, anchorNodeUuid, rootNetworkUuid, InsertMode.CHILD);
        newlyCreatedNodes.add(newParentUuid);

        children.forEach(child -> {
            if (!newlyCreatedNodes.contains(child.getIdNode())) {
                self.duplicateStudySubtree(child.getIdNode(), newParentUuid, rootNetworkUuid, newlyCreatedNodes);
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

            UUID modificationGroupUuid = self.getModificationGroupUuid(id);
            deleteNodeInfos.addModificationGroupUuid(modificationGroupUuid);

            //get all rootnetworknodeinfo info linked to node
            List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findAllByNodeInfoId(id);
            rootNetworkNodeInfoEntity.forEach(tpNodeinfo -> {
                tpNodeinfo.getModificationReports().forEach((key, value) -> deleteNodeInfos.addReportUuid(value));
                tpNodeinfo.getComputationReports().forEach((key, value) -> deleteNodeInfos.addReportUuid(value));

                String variantId = tpNodeinfo.getVariantId();
                if (!StringUtils.isBlank(variantId)) {
                    deleteNodeInfos.addVariantId(variantId);
                }

                UUID loadFlowResultUuid = getComputationResultUuid(tpNodeinfo, LOAD_FLOW);
                if (loadFlowResultUuid != null) {
                    deleteNodeInfos.addLoadFlowResultUuid(loadFlowResultUuid);
                }

                UUID securityAnalysisResultUuid = getComputationResultUuid(tpNodeinfo, SECURITY_ANALYSIS);
                if (securityAnalysisResultUuid != null) {
                    deleteNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
                }

                UUID sensitivityAnalysisResultUuid = getComputationResultUuid(tpNodeinfo, SENSITIVITY_ANALYSIS);
                if (sensitivityAnalysisResultUuid != null) {
                    deleteNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
                }

                UUID nonEvacuatedEnergyResultUuid = getComputationResultUuid(tpNodeinfo, NON_EVACUATED_ENERGY_ANALYSIS);
                if (nonEvacuatedEnergyResultUuid != null) {
                    deleteNodeInfos.addNonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid);
                }

                UUID shortCircuitAnalysisResultUuid = getComputationResultUuid(tpNodeinfo, SHORT_CIRCUIT);
                if (shortCircuitAnalysisResultUuid != null) {
                    deleteNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
                }

                UUID oneBusShortCircuitAnalysisResultUuid = getComputationResultUuid(tpNodeinfo, SHORT_CIRCUIT_ONE_BUS);
                if (oneBusShortCircuitAnalysisResultUuid != null) {
                    deleteNodeInfos.addOneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid);
                }

                UUID voltageInitResultUuid = getComputationResultUuid(tpNodeinfo, VOLTAGE_INITIALIZATION);
                if (voltageInitResultUuid != null) {
                    deleteNodeInfos.addVoltageInitResultUuid(voltageInitResultUuid);
                }

                UUID dynamicSimulationResultUuid = getComputationResultUuid(tpNodeinfo, DYNAMIC_SIMULATION);
                if (dynamicSimulationResultUuid != null) {
                    deleteNodeInfos.addDynamicSimulationResultUuid(dynamicSimulationResultUuid);
                }

                UUID stateEstimationResultUuid = getComputationResultUuid(tpNodeinfo, STATE_ESTIMATION);
                if (stateEstimationResultUuid != null) {
                    deleteNodeInfos.addStateEstimationResultUuid(stateEstimationResultUuid);
                }
            });

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
    public NodeEntity createRoot(StudyEntity study) {
        NodeEntity node = nodesRepository.save(new NodeEntity(null, null, NodeType.ROOT, study, false, null));
        rootNodeInfoRepository.save(
            RootNodeInfoEntity.builder()
                .idNode(node.getIdNode())
                .name(ROOT_NODE_NAME)
                .readOnly(true)
                .build()
        );

        return node;
    }

    @Transactional
    public RootNode getStudyTree(UUID studyId) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
        if (nodes.isEmpty()) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }
        RootNetworkEntity rootNetworkEntity = rootNetworkRepository.findAllByStudyId(studyId).stream().findFirst().orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));

        List<AbstractNode> allNodeInfos = new ArrayList<>();
        repositories.forEach((key, repository) -> allNodeInfos.addAll(repository.getAll(
            nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet()))));
        completeNodeInfos(allNodeInfos, rootNetworkEntity);
        Map<UUID, AbstractNode> fullMap = allNodeInfos.stream().collect(Collectors.toMap(AbstractNode::getId, Function.identity()));

        nodes.stream()
            .filter(n -> n.getParentNode() != null)
            .forEach(node -> fullMap.get(node.getParentNode().getIdNode()).getChildren().add(fullMap.get(node.getIdNode())));
        var root = (RootNode) fullMap.get(nodes.stream().filter(n -> n.getType().equals(NodeType.ROOT)).findFirst().orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND)).getIdNode());
        if (root != null) {
            root.setStudyId(studyId);
        }
        return root;
    }

    private void completeNodeInfos(List<AbstractNode> nodes, RootNetworkEntity rootNetworkEntity) {
        nodes.forEach(nodeInfo -> {
            if (nodeInfo instanceof RootNode rootNode) {
                rootNode.setReportUuid(rootNetworkEntity.getReportUuid());
            } else {
                ((NetworkModificationNode) nodeInfo).completeDtoFromRootNetworkNodeInfo(rootNetworkService.getRootNetworkNodeInfo(nodeInfo.getId(), rootNetworkEntity.getId()).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND)));
            }
        });
    }

    @Transactional
    public NetworkModificationNode getStudySubtree(UUID studyId, UUID rootNetworkUuid, UUID parentNodeUuid) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
        RootNetworkEntity rootNetworkEntity = rootNetworkRepository.findById(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));

        List<AbstractNode> allNodeInfos = new ArrayList<>();
        repositories.forEach((key, repository) -> {
            allNodeInfos.addAll(repository.getAll(
                nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet())));
        });
        completeNodeInfos(allNodeInfos, rootNetworkEntity);
        Map<UUID, AbstractNode> fullMap = allNodeInfos.stream().collect(Collectors.toMap(AbstractNode::getId, Function.identity()));

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

                nextParentId = self.createNode(study, referenceParentNodeId, model, InsertMode.CHILD, null).getId();
                networkModificationService.createModifications(modificationGroupToDuplicateId, newModificationGroupId);
            }
            if (nextParentId != null) {
                self.cloneStudyTree(sourceNode, nextParentId, study);
            }
        });
    }

    @Transactional
    public void createBasicTree(StudyEntity studyEntity) {
        // create 2 nodes : root node, modification node N1
        NodeEntity rootNodeEntity = self.createRoot(studyEntity);
        NetworkModificationNode modificationNode = NetworkModificationNode
                .builder()
                .name("N1")
                .variantId(FIRST_VARIANT_ID)
                .nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT, BuildStatus.BUILT))
                .build();

        createNode(studyEntity, rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER, null);
    }

    @Transactional
    public void updateNode(UUID studyUuid, NetworkModificationNode node, String userId) {
        NetworkModificationNodeInfoEntity networkModificationNodeEntity = networkModificationNodeInfoRepository.findById(node.getId()).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (!networkModificationNodeEntity.getName().equals(node.getName())) {
            assertNodeNameNotExist(studyUuid, node.getName());
        }

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findAllByNodeInfoId(networkModificationNodeEntity.getId()).stream().findFirst().orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        if (node.getVoltageInitResultUuid() != null) {
            rootNetworkNodeInfoEntity.setVoltageInitResultUuid(node.getVoltageInitResultUuid());
        }
        if (node.getOneBusShortCircuitAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setOneBusShortCircuitAnalysisResultUuid(node.getOneBusShortCircuitAnalysisResultUuid());
        }
        if (node.getShortCircuitAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setShortCircuitAnalysisResultUuid(node.getShortCircuitAnalysisResultUuid());
        }
        if (node.getSecurityAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setSecurityAnalysisResultUuid(node.getSecurityAnalysisResultUuid());
        }
        if (node.getStateEstimationResultUuid() != null) {
            rootNetworkNodeInfoEntity.setStateEstimationResultUuid(node.getStateEstimationResultUuid());
        }
        if (node.getNonEvacuatedEnergyResultUuid() != null) {
            rootNetworkNodeInfoEntity.setNonEvacuatedEnergyResultUuid(node.getNonEvacuatedEnergyResultUuid());
        }
        if (node.getSensitivityAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setSensitivityAnalysisResultUuid(node.getSensitivityAnalysisResultUuid());
        }
        if (node.getLoadFlowResultUuid() != null) {
            rootNetworkNodeInfoEntity.setLoadFlowResultUuid(node.getLoadFlowResultUuid());
        }
        if (node.getNodeBuildStatus() != null) {
            rootNetworkNodeInfoEntity.setNodeBuildStatus(node.getNodeBuildStatus().toEntity());
        }
        if (node.getVariantId() != null) {
            rootNetworkNodeInfoEntity.setVariantId(node.getVariantId());
        }
        if (node.getModificationReports() != null) {
            rootNetworkNodeInfoEntity.setModificationReports(node.getModificationReports());
        }
        if (node.getComputationsReports() != null) {
            rootNetworkNodeInfoEntity.setComputationReports(node.getComputationsReports());
        }
        if (node.getName() != null) {
            networkModificationNodeEntity.setName(node.getName());
        }
        if (node.getDescription() != null) {
            networkModificationNodeEntity.setDescription(node.getDescription());
        }

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
    public String getVariantId(UUID nodeUuid, UUID rootNetworkUuid) {
        NodeEntity nodeEntity = nodesRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        // we will use the network initial variant if node is of type ROOT
        if (nodeEntity.getType().equals(NodeType.ROOT)) {
            return "";
        }

        return rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND)).getVariantId();
    }

    @Transactional(readOnly = true)
    public UUID getModificationGroupUuid(UUID nodeUuid) {
        return networkModificationNodeInfoRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND)).getModificationGroupUuid();
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
    public UUID getReportUuid(UUID nodeUuid, UUID rootNetworkUuid) {
        NodeEntity nodeEntity = nodesRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (nodeEntity.getType().equals(NodeType.ROOT)) {
            return rootNetworkRepository.findById(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND)).getReportUuid();
        } else {
            return rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND)).getModificationReports().get(nodeUuid);
        }
    }

    @Transactional(readOnly = true)
    public List<NodeModificationInfos> getAllNodesModificationInfos(UUID studyUuid) {
        List<NodeModificationInfos> nodesModificationInfos = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            if (n.getType().equals(NodeType.ROOT)) {
                rootNetworkRepository.findAllByStudyId(studyUuid).forEach(tp -> {
                    NodeModificationInfos nodeModificationInfos = NodeModificationInfos.builder()
                        .id(n.getIdNode())
                        .reportUuid(tp.getReportUuid())
                        .build();

                    nodesModificationInfos.add(nodeModificationInfos);
                });
            } else {
                rootNetworkNodeInfoRepository.findAllByNodeInfoId(n.getIdNode()).forEach(
                    tpNodeInfo -> {
                        NodeModificationInfos nodeModificationInfos = NodeModificationInfos.builder()
                            .id(n.getIdNode())
                            .reportUuid(tpNodeInfo.getModificationReports().get(n.getIdNode()))
                            .dynamicSimulationUuid(tpNodeInfo.getDynamicSimulationResultUuid())
                            .loadFlowUuid(tpNodeInfo.getLoadFlowResultUuid())
                            .nonEvacuatedEnergyUuid(tpNodeInfo.getNonEvacuatedEnergyResultUuid())
                            .oneBusShortCircuitAnalysisUuid(tpNodeInfo.getOneBusShortCircuitAnalysisResultUuid())
                            .shortCircuitAnalysisUuid(tpNodeInfo.getShortCircuitAnalysisResultUuid())
                            .sensitivityAnalysisUuid(tpNodeInfo.getSensitivityAnalysisResultUuid())
                            .voltageInitUuid(tpNodeInfo.getVoltageInitResultUuid())
                            .stateEstimationUuid(tpNodeInfo.getStateEstimationResultUuid())
                            .securityAnalysisUuid(tpNodeInfo.getSecurityAnalysisResultUuid())
                            .variantId(tpNodeInfo.getVariantId())
                            .modificationGroupUuid(getModificationGroupUuid(n.getIdNode()))
                            .build();

                        nodesModificationInfos.add(nodeModificationInfos);
                    }
                );
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
    public void updateComputationReportUuid(UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType, UUID reportUuid) {
        rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).ifPresent(tpNodeInfo -> tpNodeInfo.getComputationReports().put(computationType.name(), reportUuid));
    }

    @Transactional
    public Map<String, UUID> getComputationReports(UUID nodeUuid, UUID rootNetworkUuid) {
        return rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND)).getComputationReports();
    }

    @Transactional
    public void setModificationReports(UUID nodeUuid, UUID rootNetworkUuid, Map<UUID, UUID> modificationReports) {
        rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).ifPresent(tpNodeInfo -> tpNodeInfo.setModificationReports(modificationReports));
    }

    @Transactional
    public Map<UUID, UUID> getModificationReports(UUID nodeUuid, UUID rootNetworkUuid) {
        return rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND)).getModificationReports();
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
    public void updateComputationResultUuid(UUID nodeUuid, UUID rootNetworkUuid, UUID computationResultUuid, ComputationType computationType) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        switch (computationType) {
            case LOAD_FLOW -> rootNetworkNodeInfoEntity.setLoadFlowResultUuid(computationResultUuid);
            case SECURITY_ANALYSIS -> rootNetworkNodeInfoEntity.setSecurityAnalysisResultUuid(computationResultUuid);
            case SENSITIVITY_ANALYSIS ->
                rootNetworkNodeInfoEntity.setSensitivityAnalysisResultUuid(computationResultUuid);
            case NON_EVACUATED_ENERGY_ANALYSIS ->
                rootNetworkNodeInfoEntity.setNonEvacuatedEnergyResultUuid(computationResultUuid);
            case SHORT_CIRCUIT -> rootNetworkNodeInfoEntity.setShortCircuitAnalysisResultUuid(computationResultUuid);
            case SHORT_CIRCUIT_ONE_BUS ->
                rootNetworkNodeInfoEntity.setOneBusShortCircuitAnalysisResultUuid(computationResultUuid);
            case VOLTAGE_INITIALIZATION -> rootNetworkNodeInfoEntity.setVoltageInitResultUuid(computationResultUuid);
            case DYNAMIC_SIMULATION -> rootNetworkNodeInfoEntity.setDynamicSimulationResultUuid(computationResultUuid);
            case STATE_ESTIMATION -> rootNetworkNodeInfoEntity.setStateEstimationResultUuid(computationResultUuid);
        }
    }

    public UUID getComputationResultUuid(UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType) {
        Optional<NodeEntity> nodeEntity = nodesRepository.findById(nodeUuid);
        if (nodeEntity.isEmpty() || nodeEntity.get().getType().equals(NodeType.ROOT)) {
            return null;
        }

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        return getComputationResultUuid(rootNetworkNodeInfoEntity, computationType);
    }

    public UUID getComputationResultUuid(RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity, ComputationType computationType) {
        return switch (computationType) {
            case LOAD_FLOW -> rootNetworkNodeInfoEntity.getLoadFlowResultUuid();
            case SECURITY_ANALYSIS -> rootNetworkNodeInfoEntity.getSecurityAnalysisResultUuid();
            case SENSITIVITY_ANALYSIS -> rootNetworkNodeInfoEntity.getSensitivityAnalysisResultUuid();
            case NON_EVACUATED_ENERGY_ANALYSIS -> rootNetworkNodeInfoEntity.getNonEvacuatedEnergyResultUuid();
            case SHORT_CIRCUIT -> rootNetworkNodeInfoEntity.getShortCircuitAnalysisResultUuid();
            case SHORT_CIRCUIT_ONE_BUS -> rootNetworkNodeInfoEntity.getOneBusShortCircuitAnalysisResultUuid();
            case VOLTAGE_INITIALIZATION -> rootNetworkNodeInfoEntity.getVoltageInitResultUuid();
            case DYNAMIC_SIMULATION -> rootNetworkNodeInfoEntity.getDynamicSimulationResultUuid();
            case STATE_ESTIMATION -> rootNetworkNodeInfoEntity.getStateEstimationResultUuid();
        };
    }

    @Transactional(readOnly = true)
    public List<UUID> getComputationResultUuids(UUID studyUuid, ComputationType computationType) {
        List<UUID> uuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            rootNetworkNodeInfoRepository.findAllByNodeInfoId(n.getIdNode()).forEach(tpNodeInfo -> {
                UUID uuid = getComputationResultUuid(tpNodeInfo, computationType);
                if (uuid != null) {
                    uuids.add(uuid);
                }
            });
        });
        return uuids;
    }

    private void getSecurityAnalysisResultUuids(UUID nodeUuid, List<UUID> uuids) {
        rootNetworkNodeInfoRepository.findAllByNodeInfoId(nodeUuid).forEach(tpNodeInfo -> {
            nodesRepository.findById(nodeUuid).flatMap(n -> Optional.ofNullable(getComputationResultUuid(tpNodeInfo, SECURITY_ANALYSIS))).ifPresent(uuids::add);
            nodesRepository.findAllByParentNodeIdNode(nodeUuid)
                .forEach(child -> getSecurityAnalysisResultUuids(child.getIdNode(), uuids));
        });
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
            rootNetworkNodeInfoRepository.findAllByNodeInfoId(n.getIdNode()).forEach(tpNodeInfo -> {
                // we need to check one bus and all bus
                UUID uuidOneBus = getComputationResultUuid(tpNodeInfo, SHORT_CIRCUIT_ONE_BUS);
                UUID uuidAllBus = getComputationResultUuid(tpNodeInfo, SHORT_CIRCUIT);
                if (uuidOneBus != null) {
                    uuids.add(uuidOneBus);
                }
                if (uuidAllBus != null) {
                    uuids.add(uuidAllBus);
                }
            });
        });
        return uuids;
    }

    private UUID getModificationReportUuid(UUID nodeUuid, UUID rootNetworkUuid, UUID nodeToBuildUuid) {
        return self.getModificationReports(nodeToBuildUuid, rootNetworkUuid).getOrDefault(nodeUuid, UUID.randomUUID());
    }

    private void getBuildInfos(NodeEntity nodeEntity, UUID rootNetworkUuid, BuildInfos buildInfos, UUID nodeToBuildUuid) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            if (modificationNode.getModificationsToExclude() != null) {
                buildInfos.addModificationsToExclude(modificationNode.getModificationsToExclude());
            }
            if (!modificationNode.getNodeBuildStatus().isBuilt()) {
                UUID reportUuid = getModificationReportUuid(nodeEntity.getIdNode(), rootNetworkUuid, nodeToBuildUuid);
                buildInfos.insertModificationInfos(modificationNode.getModificationGroupUuid(), new ReportInfos(reportUuid, modificationNode.getId()));
                getBuildInfos(nodeEntity.getParentNode(), rootNetworkUuid, buildInfos, nodeToBuildUuid);
            } else {
                buildInfos.setOriginVariantId(self.getVariantId(nodeEntity.getIdNode(), rootNetworkUuid));
            }
        }
    }

    @Transactional
    public BuildInfos getBuildInfos(UUID nodeUuid, UUID rootNetworkUuid) {
        BuildInfos buildInfos = new BuildInfos();

        nodesRepository.findById(nodeUuid).ifPresentOrElse(entity -> {
            if (entity.getType() != NodeType.NETWORK_MODIFICATION) {  // nodeUuid must be a modification node
                throw new StudyException(BAD_NODE_TYPE, "The node " + entity.getIdNode() + " is not a modification node");
            } else {
                buildInfos.setDestinationVariantId(self.getVariantId(nodeUuid, rootNetworkUuid));
                getBuildInfos(entity, rootNetworkUuid, buildInfos, nodeUuid);
            }
        }, () -> {
            throw new StudyException(ELEMENT_NOT_FOUND);
        });

        return buildInfos;
    }

    private void fillInvalidateNodeInfos(NodeEntity node, UUID rootNetworkUuid, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus,
                                         boolean deleteVoltageInitResults) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkService.getRootNetworkNodeInfo(node.getIdNode(), rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        if (!invalidateOnlyChildrenBuildStatus) {
            // we want to delete associated report and variant in this case
            rootNetworkNodeInfoEntity.getModificationReports().forEach((key, value) -> invalidateNodeInfos.addReportUuid(value));
            invalidateNodeInfos.addVariantId(self.getVariantId(node.getIdNode(), rootNetworkUuid));
        }

        // we want to delete associated computation reports exept for voltage initialization : only if deleteVoltageInitResults is true
        rootNetworkNodeInfoEntity.getComputationReports().forEach((key, value) -> {
            if (deleteVoltageInitResults || !VOLTAGE_INITIALIZATION.name().equals(key)) {
                invalidateNodeInfos.addReportUuid(value);
            }
        });

        UUID loadFlowResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, LOAD_FLOW);
        if (loadFlowResultUuid != null) {
            invalidateNodeInfos.addLoadFlowResultUuid(loadFlowResultUuid);
        }

        UUID securityAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SECURITY_ANALYSIS);
        if (securityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        }

        UUID sensitivityAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SENSITIVITY_ANALYSIS);
        if (sensitivityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
        }

        UUID nonEvacuatedEnergyResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, NON_EVACUATED_ENERGY_ANALYSIS);
        if (nonEvacuatedEnergyResultUuid != null) {
            invalidateNodeInfos.addNonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid);
        }

        UUID shortCircuitAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SHORT_CIRCUIT);
        if (shortCircuitAnalysisResultUuid != null) {
            invalidateNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
        }

        UUID oneBusShortCircuitAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SHORT_CIRCUIT_ONE_BUS);
        if (oneBusShortCircuitAnalysisResultUuid != null) {
            invalidateNodeInfos.addOneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid);
        }

        if (deleteVoltageInitResults) {
            UUID voltageInitResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, VOLTAGE_INITIALIZATION);
            if (voltageInitResultUuid != null) {
                invalidateNodeInfos.addVoltageInitResultUuid(voltageInitResultUuid);
            }
        }

        UUID stateEstimationResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, STATE_ESTIMATION);
        if (stateEstimationResultUuid != null) {
            invalidateNodeInfos.addStateEstimationResultUuid(stateEstimationResultUuid);
        }
    }

    @Transactional
    public void invalidateBuild(UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos, boolean deleteVoltageInitResults) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            rootNetworkRepository.findById(rootNetworkUuid).ifPresent(tp -> {
                invalidateNodeProper(n, tp, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes, deleteVoltageInitResults);
                invalidateChildrenBuildStatus(n, tp, changedNodes, invalidateNodeInfos, deleteVoltageInitResults);
            });
        });

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
    }

    @Transactional
    // method used when moving a node to invalidate it without impacting other nodes
    public void invalidateBuildOfNodeOnly(UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos, boolean deleteVoltageInitResults) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n ->
            rootNetworkRepository.findById(rootNetworkUuid).ifPresent(tp -> {
                invalidateNodeProper(n, tp, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes, deleteVoltageInitResults);
            })
        );

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
    }

    private void invalidateChildrenBuildStatus(NodeEntity nodeEntity, RootNetworkEntity rootNetworkEntity, List<UUID> changedNodes, InvalidateNodeInfos invalidateNodeInfos,
                                               boolean deleteVoltageInitResults) {
        nodesRepository.findAllByParentNodeIdNode(nodeEntity.getIdNode())
            .forEach(child -> {
                invalidateNodeProper(child, rootNetworkEntity, invalidateNodeInfos, false, changedNodes, deleteVoltageInitResults);
                invalidateChildrenBuildStatus(child, rootNetworkEntity, changedNodes, invalidateNodeInfos, deleteVoltageInitResults);
            });
    }

    private void invalidateNodeProper(NodeEntity child, RootNetworkEntity rootNetworkEntity, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus,
                                      List<UUID> changedNodes, boolean deleteVoltageInitResults) {
        UUID childUuid = child.getIdNode();
        // No need to invalidate a node with a status different of "BUILT"
        if (self.getNodeBuildStatus(childUuid, rootNetworkEntity.getId()).isBuilt()) {
            RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(childUuid, rootNetworkEntity.getId()).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
            fillInvalidateNodeInfos(child, rootNetworkEntity.getId(), invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, deleteVoltageInitResults);
            if (!invalidateOnlyChildrenBuildStatus) {
                invalidateNodeBuildStatus(childUuid, rootNetworkNodeInfoEntity, changedNodes);
            }

            rootNetworkNodeInfoEntity.setLoadFlowResultUuid(null);
            rootNetworkNodeInfoEntity.setSecurityAnalysisResultUuid(null);
            rootNetworkNodeInfoEntity.setSensitivityAnalysisResultUuid(null);
            rootNetworkNodeInfoEntity.setNonEvacuatedEnergyResultUuid(null);
            rootNetworkNodeInfoEntity.setShortCircuitAnalysisResultUuid(null);
            rootNetworkNodeInfoEntity.setOneBusShortCircuitAnalysisResultUuid(null);
            if (deleteVoltageInitResults) {
                rootNetworkNodeInfoEntity.setVoltageInitResultUuid(null);
            }
            rootNetworkNodeInfoEntity.setStateEstimationResultUuid(null);

            // we want to keep only voltage initialization report if deleteVoltageInitResults is false
            Map<String, UUID> computationReports = rootNetworkNodeInfoEntity.getComputationReports()
                .entrySet()
                .stream()
                .filter(entry -> VOLTAGE_INITIALIZATION.name().equals(entry.getKey()) && !deleteVoltageInitResults)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // Update the computation reports in the repository
            rootNetworkNodeInfoEntity.setComputationReports(computationReports);
        }
    }

    public void invalidateNodeBuildStatus(UUID nodeUuid, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity, List<UUID> changedNodes) {
        if (!rootNetworkNodeInfoEntity.getNodeBuildStatus().toDto().isBuilt()) {
            return;
        }

        rootNetworkNodeInfoEntity.setNodeBuildStatus(NodeBuildStatusEmbeddable.from(BuildStatus.NOT_BUILT));
        rootNetworkNodeInfoEntity.setVariantId(UUID.randomUUID().toString());
        rootNetworkNodeInfoEntity.setModificationReports(new HashMap<>(Map.of(nodeUuid, UUID.randomUUID())));
        changedNodes.add(nodeUuid);
    }

    @Transactional
    public void updateNodeBuildStatus(UUID nodeUuid, UUID rootNetworkUuid, NodeBuildStatus nodeBuildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        NodeBuildStatusEmbeddable currentNodeStatus = rootNetworkNodeInfoEntity.getNodeBuildStatus();

        BuildStatus newGlobalStatus;
        BuildStatus newLocalStatus;
        if (nodeBuildStatus.isBuilt()) {
            newLocalStatus = nodeBuildStatus.getLocalBuildStatus().max(currentNodeStatus.getLocalBuildStatus());
            NodeEntity previousBuiltNode = doGetLastParentNodeBuilt(nodeEntity, rootNetworkUuid);
            BuildStatus previousGlobalBuildStatus = getNodeBuildStatus(previousBuiltNode.getIdNode(), rootNetworkUuid).getGlobalBuildStatus();
            newGlobalStatus = nodeBuildStatus.getGlobalBuildStatus().max(previousGlobalBuildStatus);
        } else {
            newLocalStatus = nodeBuildStatus.getLocalBuildStatus();
            newGlobalStatus = nodeBuildStatus.getGlobalBuildStatus();
        }
        NodeBuildStatusEmbeddable newNodeStatus = NodeBuildStatusEmbeddable.builder()
            .localBuildStatus(newLocalStatus)
            .globalBuildStatus(newGlobalStatus)
            .build();
        if (newNodeStatus.equals(currentNodeStatus)) {
            return;
        }

        rootNetworkNodeInfoEntity.setNodeBuildStatus(newNodeStatus);
        changedNodes.add(nodeUuid);
        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes);
    }

    @Transactional(readOnly = true)
    public NodeBuildStatus getNodeBuildStatus(RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        return rootNetworkNodeInfoEntity.getNodeBuildStatus().toDto();
    }

    @Transactional(readOnly = true)
    public NodeBuildStatus getNodeBuildStatus(UUID nodeUuid, UUID rootNetworkUuid) {
        NodeEntity nodeEntity = nodesRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (nodeEntity.getType().equals(NodeType.ROOT)) {
            return NodeBuildStatus.from(BuildStatus.NOT_BUILT);
        }
        return getNodeBuildStatus(rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getParentNodeUuid(UUID nodeUuid) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        return (nodeEntity.getType() == NodeType.ROOT) ? Optional.empty() : Optional.of(nodeEntity.getParentNode().getIdNode());
    }

    @Transactional(readOnly = true)
    public UUID doGetLastParentNodeBuiltUuid(UUID nodeUuid, UUID rootNetworkUuid) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        return doGetLastParentNodeBuilt(nodeEntity, rootNetworkUuid).getIdNode();
    }

    private NodeEntity doGetLastParentNodeBuilt(NodeEntity nodeEntity, UUID rootNetworkUuid) {
        if (nodeEntity.getType() == NodeType.ROOT) {
            return nodeEntity;
        } else if (rootNetworkService
            .getRootNetworkNodeInfo(nodeEntity.getIdNode(), rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND))
            .getNodeBuildStatus().toDto().isBuilt()) {
            return nodeEntity;
        } else {
            return doGetLastParentNodeBuilt(nodeEntity.getParentNode(), rootNetworkUuid);
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
    public void handleExcludeModification(UUID nodeUuid, UUID rootNetworkUuid, UUID modificationUuid, boolean active) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        if (rootNetworkNodeInfoEntity.getModificationsToExclude() == null) {
            rootNetworkNodeInfoEntity.setModificationsToExclude(new HashSet<>());
        }
        if (!active) {
            rootNetworkNodeInfoEntity.getModificationsToExclude().add(modificationUuid);
        } else {
            rootNetworkNodeInfoEntity.getModificationsToExclude().remove(modificationUuid);
        }
    }

    @Transactional
    public void removeModificationsToExclude(UUID nodeUuid, UUID rootNetworkUuid, List<UUID> modificationUuids) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        if (rootNetworkNodeInfoEntity.getModificationsToExclude() != null) {
            modificationUuids.forEach(rootNetworkNodeInfoEntity.getModificationsToExclude()::remove);
        }
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

    public long countBuiltNodes(UUID studyUuid, UUID rootNetworkUuid) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyIdAndTypeAndStashed(studyUuid, NodeType.NETWORK_MODIFICATION, false);
        // perform N queries, but it's fast: 25 ms for 400 nodes
        return nodes.stream().filter(n -> getNodeBuildStatus(n.getIdNode(), rootNetworkUuid).isBuilt()).count();
    }

    public NetworkModificationNodeInfoEntity getNetworkModificationNodeInfoEntity(UUID nodeId) {
        return networkModificationNodeInfoRepository.findById(nodeId).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }
}
