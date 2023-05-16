/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.powsybl.loadflow.LoadFlowResult;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.networkmodificationtree.AbstractNodeRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.NetworkModificationNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.RootNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyException.Type.*;

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

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    public NetworkModificationTreeService(NodeRepository nodesRepository,
                                          RootNodeInfoRepository rootNodeInfoRepository,
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository
    ) {
        this.nodesRepository = nodesRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        repositories.put(NodeType.ROOT, new RootNodeInfoRepositoryProxy(rootNodeInfoRepository));
        repositories.put(NodeType.NETWORK_MODIFICATION, new NetworkModificationNodeInfoRepositoryProxy(networkModificationNodeInfoRepository));

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
            NodeEntity node = nodesRepository.save(new NodeEntity(null, parent, nodeInfo.getType(), reference.getStudy()));
            nodeInfo.setId(node.getIdNode());
            repositories.get(node.getType()).createNodeInfo(nodeInfo);

            if (insertMode.equals(InsertMode.BEFORE)) {
                reference.setParentNode(node);
            } else if (insertMode.equals(InsertMode.AFTER)) {
                nodesRepository.findAllByParentNodeIdNode(nodeId).stream()
                    .filter(n -> !n.getIdNode().equals(node.getIdNode()))
                    .forEach(child -> child.setParentNode(node));
            }
            notificationService.emitNodeInserted(getStudyUuidForNodeId(nodeId), parent.getIdNode(), node.getIdNode(), insertMode);
            // userId is null when creating initial nodes, we don't need to send element update notifications in this case
            if (userId != null) {
                notificationService.emitElementUpdated(studyUuid, userId);
            }
            return nodeInfo;
        }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public UUID duplicateStudyNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        return duplicateNode(nodeToCopyUuid, anchorNodeUuid, insertMode);
    }

    @Transactional
    public UUID duplicateNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        Optional<NodeEntity> anchorNodeOpt = nodesRepository.findById(anchorNodeUuid);
        NodeEntity anchorNodeEntity = anchorNodeOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (insertMode.equals(InsertMode.BEFORE) && anchorNodeEntity.getType().equals(NodeType.ROOT)) {
            throw new StudyException(NOT_ALLOWED);
        }

        Optional<NodeEntity> nodeToCopyOpt = nodesRepository.findById(nodeToCopyUuid);
        NodeEntity nodeToCopyEntity = nodeToCopyOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));

        UUID newGroupUuid = UUID.randomUUID();
        UUID modificationGroupUuid = getModificationGroupUuid(nodeToCopyUuid);
        UUID newReportUuid = UUID.randomUUID();
        //First we create the modification group
        networkModificationService.createModifications(modificationGroupUuid, newGroupUuid);

        NodeEntity parent = insertMode.equals(InsertMode.BEFORE) ?
                anchorNodeEntity.getParentNode() : anchorNodeEntity;
        //Then we create the node
        NodeEntity node = nodesRepository.save(new NodeEntity(null, parent, nodeToCopyEntity.getType(), anchorNodeEntity.getStudy()));

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
                LoadFlowStatus.NOT_DONE,
                null,
                null,
                null,
                null,
                null,
                BuildStatus.NOT_BUILT
        );
        UUID studyUuid = anchorNodeEntity.getStudy().getId();
        newNetworkModificationNodeInfoEntity.setName(getSuffixedNodeName(studyUuid, networkModificationNodeInfoEntity.getName()));
        newNetworkModificationNodeInfoEntity.setDescription(networkModificationNodeInfoEntity.getDescription());
        newNetworkModificationNodeInfoEntity.setIdNode(node.getIdNode());
        newNetworkModificationNodeInfoEntity.setReportUuid(newReportUuid);
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
                duplicateStudySubtree(child.getIdNode(), newParentUuid, newlyCreatedNodes);
            }
        });
        return newParentUuid;
    }

    @Transactional
    public void moveStudyNode(UUID nodeToMoveUuid, UUID anchorNodeUuid, InsertMode insertMode) {        //if we try to move a node around itself, nothing happens
        if (nodeToMoveUuid.equals(anchorNodeUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        UUID studyUuid = moveNode(nodeToMoveUuid, anchorNodeUuid, insertMode);
        notificationService.emitNodeMoved(studyUuid, anchorNodeUuid, nodeToMoveUuid, insertMode);
    }

    @Transactional
    public UUID moveNode(UUID nodeToMoveUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        Optional<NodeEntity> nodeToMoveOpt = nodesRepository.findById(nodeToMoveUuid);
        NodeEntity nodeToMoveEntity = nodeToMoveOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));

        nodesRepository.findAllByParentNodeIdNode(nodeToMoveUuid).stream()
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
        children.forEach(child -> {
            moveStudySubtree(child.getIdNode(), parentNodeToMoveUuid);
        });
    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public void doDeleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren, DeleteNodeInfos deleteNodeInfos) {
        List<UUID> removedNodes = new ArrayList<>();
        UUID studyId = getStudyUuidForNodeId(nodeId);
        deleteNodes(nodeId, deleteChildren, false, removedNodes, deleteNodeInfos);
        notificationService.emitNodesDeleted(studyId, removedNodes, deleteChildren);
    }

    @Transactional(readOnly = true)
    public UUID getStudyUuidForNodeId(UUID id) {
        Optional<NodeEntity> node = nodesRepository.findById(id);
        return node.orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND)).getStudy().getId();
    }

    @Transactional
    public void deleteNodes(UUID id, boolean deleteChildren, boolean allowDeleteRoot, List<UUID> removedNodes, DeleteNodeInfos deleteNodeInfos) {
        Optional<NodeEntity> optNodeToDelete = nodesRepository.findById(id);
        optNodeToDelete.ifPresent(nodeToDelete -> {
            /* root cannot be deleted by accident */
            if (!allowDeleteRoot && nodeToDelete.getType() == NodeType.ROOT) {
                throw new StudyException(CANT_DELETE_ROOT_NODE);
            }

            UUID modificationGroupUuid = repositories.get(nodeToDelete.getType()).getModificationGroupUuid(id);
            deleteNodeInfos.addModificationGroupUuid(modificationGroupUuid);

            UUID reportUuid = repositories.get(nodeToDelete.getType()).getReportUuid(id);
            if (reportUuid != null) {
                deleteNodeInfos.addReportUuid(reportUuid);
            }

            String variantId = repositories.get(nodeToDelete.getType()).getVariantId(id);
            if (!StringUtils.isBlank(variantId)) {
                deleteNodeInfos.addVariantId(variantId);
            }

            UUID securityAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getSecurityAnalysisResultUuid(id);
            if (securityAnalysisResultUuid != null) {
                deleteNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
            }

            UUID sensitivityAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getSensitivityAnalysisResultUuid(id);
            if (sensitivityAnalysisResultUuid != null) {
                deleteNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
            }

            UUID shortCircuitAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getShortCircuitAnalysisResultUuid(id);
            if (shortCircuitAnalysisResultUuid != null) {
                deleteNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
            }

            UUID dynamicSimulationResultUuid = repositories.get(nodeToDelete.getType()).getDynamicSimulationResultUuid(id);
            if (dynamicSimulationResultUuid != null) {
                deleteNodeInfos.addDynamicSimulationResultUuid(dynamicSimulationResultUuid);
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
        NodeEntity node = nodesRepository.save(new NodeEntity(null, null, NodeType.ROOT, study));
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
        var parentNetworkModificationNode = (NetworkModificationNode) fullMap.get(parentNodeUuid);
        return parentNetworkModificationNode;
    }

    @Transactional
    public void cloneStudyTree(AbstractNode nodeToDuplicate, UUID nodeParentId, StudyEntity study) {
        UUID rootId = null;
        if (NodeType.ROOT.equals(nodeToDuplicate.getType())) {
            rootId = getStudyRootNodeUuid(study.getId());
        }
        UUID referenceParentNodeId = rootId != null ? rootId : nodeParentId;

        nodeToDuplicate.getChildren().stream().forEach(sourceNode -> {
            UUID newModificationGroupId = UUID.randomUUID();
            UUID newReportUuid = UUID.randomUUID();
            UUID nextParentId = null;

            if (sourceNode instanceof NetworkModificationNode) {
                NetworkModificationNode model = (NetworkModificationNode) sourceNode;
                UUID modificationGroupToDuplicateId = model.getModificationGroupUuid();
                model.setModificationGroupUuid(newModificationGroupId);
                model.setBuildStatus(BuildStatus.NOT_BUILT);
                model.setReportUuid(newReportUuid);
                model.setLoadFlowStatus(LoadFlowStatus.NOT_DONE);
                model.setLoadFlowResult(null);
                model.setSecurityAnalysisResultUuid(null);
                model.setSensitivityAnalysisResultUuid(null);
                model.setShortCircuitAnalysisResultUuid(null);

                nextParentId = createNode(study.getId(), referenceParentNodeId, model, InsertMode.CHILD, null).getId();
                networkModificationService.createModifications(modificationGroupToDuplicateId, newModificationGroupId);
            }
            if (nextParentId != null) {
                cloneStudyTree(sourceNode, nextParentId, study);
            }
        });
    }

    @Transactional
    public void createBasicTree(StudyEntity studyEntity, UUID importReportUuid) {
        // create 2 nodes : root node, modification node 0
        NodeEntity rootNodeEntity = createRoot(studyEntity, importReportUuid);
        NetworkModificationNode modificationNode = NetworkModificationNode
                .builder()
                .name("N1")
                .variantId(FIRST_VARIANT_ID)
                .loadFlowStatus(LoadFlowStatus.NOT_DONE)
                .buildStatus(BuildStatus.BUILT)
                .build();
        createNode(studyEntity.getId(), rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER, null);
    }

    @Transactional
    public void updateNode(UUID studyUuid, AbstractNode node, String userId) {
        NetworkModificationNodeInfoEntity networkModificationNode = networkModificationNodeInfoRepository.findById(node.getId()).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (!networkModificationNode.getName().equals(node.getName())) {
            assertNodeNameNotExist(studyUuid, node.getName());
        }
        repositories.get(node.getType()).updateNode(node);
        if (isRenameNode(node)) {
            notificationService.emitNodeRenamed(getStudyUuidForNodeId(node.getId()), node.getId());
        } else {
            notificationService.emitNodesChanged(getStudyUuidForNodeId(node.getId()), Collections.singletonList(node.getId()));
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

    @Transactional
    public NodeEntity getNodeEntity(UUID nodeId) {
        return nodesRepository.findById(nodeId).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public AbstractNode getSimpleNode(UUID nodeId) {
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

    @Transactional(readOnly = true)
    public void assertNodeNameNotExist(UUID studyUuid, String nodeName) {
        if (isNodeNameExists(studyUuid, nodeName)) {
            throw new StudyException(NODE_NAME_ALREADY_EXIST);
        }
    }

    @Transactional(readOnly = true)
    public boolean isNodeNameExists(UUID studyUuid, String nodeName) {
        return ROOT_NODE_NAME.equals(nodeName) || !networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(studyUuid, nodeName).isEmpty();
    }

    @Transactional(readOnly = true)
    public String getUniqueNodeName(UUID studyUuid) {
        int counter = 2;
        List<String> studyNodeNames = networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid)
                .stream()
                .map(AbstractNodeInfoEntity::getName)
                .collect(Collectors.toList());

        String namePrefix = "N";
        String uniqueName = StringUtils.EMPTY;
        while (StringUtils.EMPTY.equals(uniqueName) || studyNodeNames.contains(uniqueName)) {
            uniqueName = namePrefix + counter;
            ++counter;
        }

        return uniqueName;
    }

    public String getSuffixedNodeName(UUID studyUuid, String nodeName) {
        List<String> studyNodeNames = networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid)
                .stream()
                .map(AbstractNodeInfoEntity::getName)
                .collect(Collectors.toList());

        String uniqueName = nodeName;
        int i = 1;
        while (studyNodeNames.contains(uniqueName)) {
            uniqueName = nodeName + " (" + i + ")";
            i++;
        }
        return uniqueName;
    }

    @Transactional(readOnly = false)
    public String getVariantId(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getVariantId(nodeUuid)).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UUID getModificationGroupUuid(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getModificationGroupUuid(nodeUuid)).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    // Return json string because modification dtos are not available here
    @Transactional(readOnly = true)
    public String getNetworkModifications(@NonNull UUID nodeUuid) {
        return networkModificationService.getModifications(getModificationGroupUuid(nodeUuid));
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

    public List<NodeEntity> getAllNodes(UUID studyUuid) {
        return nodesRepository.findAllByStudyId(studyUuid);
    }

    @Transactional(readOnly = true)
    public Optional<LoadFlowStatus> getLoadFlowStatus(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getLoadFlowStatus(nodeUuid));

    }

    public void updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus));
        if (updateChildren) {
            nodesRepository.findAllByParentNodeIdNode(nodeUuid)
                .forEach(child -> updateLoadFlowResultAndStatus(child.getIdNode(), loadFlowResult, loadFlowStatus, updateChildren));
        }
    }

    @Transactional
    public void updateShortCircuitAnalysisResultUuid(UUID nodeUuid, UUID shortCircuitAnalysisResultUuid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateShortCircuitAnalysisResultUuid(nodeUuid, shortCircuitAnalysisResultUuid));
    }

    @Transactional
    public void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateLoadFlowStatus(nodeUuid, loadFlowStatus));
    }

    @Transactional
    public void updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid));
    }

    @Transactional
    public void updateSensitivityAnalysisResultUuid(UUID nodeUuid, UUID sensitivityAnalysisResultUuid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateSensitivityAnalysisResultUuid(nodeUuid, sensitivityAnalysisResultUuid));
    }

    @Transactional
    public void updateDynamicSimulationResultUuid(UUID nodeUuid, UUID dynamicSimulationResultUuid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateDynamicSimulationResultUuid(nodeUuid, dynamicSimulationResultUuid));
    }

    @Transactional
    public void updateStudyLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> updateLoadFlowStatus(n.getIdNode(), loadFlowStatus));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getSecurityAnalysisResultUuid(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getSecurityAnalysisResultUuid(nodeUuid));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getSensitivityAnalysisResultUuid(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getSensitivityAnalysisResultUuid(nodeUuid));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getShortCircuitAnalysisResultUuid(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getShortCircuitAnalysisResultUuid(nodeUuid));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getDynamicSimulationResultUuid(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getDynamicSimulationResultUuid(nodeUuid));
    }

    @Transactional(readOnly = true)
    public List<UUID> getStudySecurityAnalysisResultUuids(UUID studyUuid) {
        List<UUID> uuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            UUID uuid = repositories.get(n.getType()).getSecurityAnalysisResultUuid(n.getIdNode());
            if (uuid != null) {
                uuids.add(uuid);
            }
        });
        return uuids;
    }

    private void getSecurityAnalysisResultUuids(UUID nodeUuid, List<UUID> uuids) {
        nodesRepository.findById(nodeUuid).flatMap(n -> Optional.ofNullable(repositories.get(n.getType()).getSecurityAnalysisResultUuid(nodeUuid))).ifPresent(uuids::add);
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
    public List<UUID> getStudySensitivityAnalysisResultUuids(UUID studyUuid) {
        List<UUID> uuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            UUID uuid = repositories.get(n.getType()).getSensitivityAnalysisResultUuid(n.getIdNode());
            if (uuid != null) {
                uuids.add(uuid);
            }
        });
        return uuids;
    }

    @Transactional(readOnly = true)
    public List<UUID> getStudyDynamicSimulationResultUuids(UUID studyUuid) {
        List<UUID> resultUuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            UUID resultUuid = repositories.get(n.getType()).getDynamicSimulationResultUuid(n.getIdNode());
            if (resultUuid != null) {
                resultUuids.add(resultUuid);
            }
        });
        return resultUuids;
    }

    @Transactional(readOnly = true)
    public LoadFlowInfos getLoadFlowInfos(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getLoadFlowInfos(nodeUuid)).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    private void getBuildInfos(NodeEntity nodeEntity, BuildInfos buildInfos) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            if (modificationNode.getModificationsToExclude() != null) {
                buildInfos.addModificationsToExclude(modificationNode.getModificationsToExclude());
            }
            if (!modificationNode.getBuildStatus().isBuilt()) {
                buildInfos.insertModificationInfos(modificationNode.getModificationGroupUuid(), modificationNode.getId().toString());
                getBuildInfos(nodeEntity.getParentNode(), buildInfos);
            } else {
                buildInfos.setOriginVariantId(getVariantId(nodeEntity.getIdNode()));
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
                buildInfos.setDestinationVariantId(getVariantId(nodeUuid));
                buildInfos.setReportUuid(getReportUuid(nodeUuid));
                getBuildInfos(entity, buildInfos);
            }
        }, () -> {
                throw new StudyException(ELEMENT_NOT_FOUND);
            });

        return buildInfos;
    }

    private void fillInvalidateNodeInfos(NodeEntity node, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus) {

        if (!invalidateOnlyChildrenBuildStatus) {
            // we want to delete associated report and variant in this case
            invalidateNodeInfos.addReportUuid(repositories.get(node.getType()).getReportUuid(node.getIdNode()));
            invalidateNodeInfos.addVariantId(repositories.get(node.getType()).getVariantId(node.getIdNode()));
        }

        UUID securityAnalysisResultUuid = repositories.get(node.getType()).getSecurityAnalysisResultUuid(node.getIdNode());
        if (securityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        }

        UUID sensitivityAnalysisResultUuid = repositories.get(node.getType()).getSensitivityAnalysisResultUuid(node.getIdNode());
        if (sensitivityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
        }

        UUID shortCircuitAnalysisResultUuid = repositories.get(node.getType()).getShortCircuitAnalysisResultUuid(node.getIdNode());
        if (shortCircuitAnalysisResultUuid != null) {
            invalidateNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
        }
    }

    @Transactional
    public void invalidateBuild(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            invalidateNodeProper(n, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes);
            invalidateChildrenBuildStatus(n, changedNodes, invalidateNodeInfos);
        });

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
    }

    @Transactional
    // method used when moving a node to invalidate it without impacting other nodes
    public void invalidateBuildOfNodeOnly(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n ->
            invalidateNodeProper(n, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes)
        );

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
    }

    private void invalidateChildrenBuildStatus(NodeEntity nodeEntity, List<UUID> changedNodes, InvalidateNodeInfos invalidateNodeInfos) {
        nodesRepository.findAllByParentNodeIdNode(nodeEntity.getIdNode())
            .forEach(child -> {
                invalidateNodeProper(child, invalidateNodeInfos, false, changedNodes);
                invalidateChildrenBuildStatus(child, changedNodes, invalidateNodeInfos);
            });
    }

    private void invalidateNodeProper(NodeEntity child, InvalidateNodeInfos invalidateNodeInfos,
        boolean invalidateOnlyChildrenBuildStatus, List<UUID> changedNodes) {
        UUID childUuid = child.getIdNode();
        // No need to invalidate a node with a status different of "BUILT"
        AbstractNodeRepositoryProxy<?, ?, ?> nodeRepository = repositories.get(child.getType());
        if (nodeRepository.getBuildStatus(child.getIdNode()).isBuilt()) {
            fillInvalidateNodeInfos(child, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus);
            if (!invalidateOnlyChildrenBuildStatus) {
                nodeRepository.invalidateBuildStatus(childUuid, changedNodes);
            }
            nodeRepository.updateLoadFlowResultAndStatus(childUuid, null, LoadFlowStatus.NOT_DONE);
            nodeRepository.updateSecurityAnalysisResultUuid(childUuid, null);
            nodeRepository.updateSensitivityAnalysisResultUuid(childUuid, null);
            nodeRepository.updateShortCircuitAnalysisResultUuid(childUuid, null);
        }
    }

    @Transactional
    public void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = getStudyUuidForNodeId(nodeUuid);
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);

        BuildStatus newNodeStatus;
        if (buildStatus.isBuilt()) {
            NodeEntity previousBuiltNode = doGetLastParentNodeBuilt(nodeEntity);
            BuildStatus previousBuiltNodeStatus = repositories.get(previousBuiltNode.getType()).getBuildStatus(previousBuiltNode.getIdNode());
            newNodeStatus = buildStatus.max(previousBuiltNodeStatus);
        } else {
            newNodeStatus = buildStatus;
        }

        AbstractNodeRepositoryProxy<?, ?, ?> nodeRepositoryProxy = repositories.get(nodeEntity.getType());
        BuildStatus currentNodeStatus = nodeRepositoryProxy.getBuildStatus(nodeEntity.getIdNode());
        if (newNodeStatus.equals(currentNodeStatus)) {
            return;
        }

        nodeRepositoryProxy.updateBuildStatus(nodeUuid, newNodeStatus, changedNodes);
        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes);
    }

    @Transactional
    public void updateBuildStatus(UUID nodeUuid, NetworkModificationResult.ApplicationStatus applicationStatus) {
        BuildStatus buildStatus = BuildStatus.fromApplicationStatus(applicationStatus);
        updateBuildStatus(nodeUuid, buildStatus);
    }

    @Transactional(readOnly = true)
    public BuildStatus getBuildStatus(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getBuildStatus(nodeUuid)).orElse(BuildStatus.NOT_BUILT);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> doGetParentNode(UUID nodeUuid, NodeType nodeType) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        if (nodeEntity.getType() == NodeType.ROOT && nodeType != NodeType.ROOT) {
            return Optional.empty();
        }
        if (nodeEntity.getType() == NodeType.ROOT || nodeEntity.getParentNode().getType() == nodeType) {
            return Optional.of(nodeEntity.getParentNode() != null ? nodeEntity.getParentNode().getIdNode() : nodeEntity.getIdNode());
        } else {
            return doGetParentNode(nodeEntity.getParentNode().getIdNode(), nodeType);
        }
    }

    // used only in tests
    @Transactional(readOnly = true)
    public UUID getParentNode(UUID nodeUuid, NodeType nodeType) {
        Optional<UUID> parentNodeUuidOpt = doGetParentNode(nodeUuid, nodeType);
        if (parentNodeUuidOpt.isEmpty()) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }

        return parentNodeUuidOpt.get();
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

    @Transactional(readOnly = true)
    public NodeEntity doGetLastParentNodeBuilt(NodeEntity nodeEntity) {
        if (nodeEntity.getType() == NodeType.ROOT) {
            return nodeEntity;
        } else if (getBuildStatus(nodeEntity.getIdNode()).isBuilt()) {
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
            return hasAncestor(nodeEntity.getParentNode().getIdNode(), ancestorNodeUuid);
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
}
