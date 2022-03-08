/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.loadflow.LoadFlowResult;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.DeleteNodeInfos;
import org.gridsuite.study.server.dto.LoadFlowInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.networkmodificationtree.RootNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.ModelNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.AbstractNodeRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.networkmodificationtree.NetworkModificationNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.repositories.ModelNodeInfoRepository;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.ModelNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.repositories.NodeRepository;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.repositories.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.StudyService.HEADER_STUDY_UUID;
import static org.gridsuite.study.server.StudyService.HEADER_UPDATE_TYPE;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
@Service
public class NetworkModificationTreeService {

    public static final String HEADER_NODES = "nodes";
    public static final String HEADER_PARENT_NODE = "parentNode";
    public static final String HEADER_NEW_NODE = "newNode";
    public static final String HEADER_REMOVE_CHILDREN = "removeChildren";
    public static final String NODE_UPDATED = "nodeUpdated";
    public static final String NODE_DELETED = "nodeDeleted";
    public static final String NODE_CREATED = "nodeCreated";
    public static final String HEADER_INSERT_MODE = "insertMode";

    private final EnumMap<NodeType, AbstractNodeRepositoryProxy<?, ?, ?>> repositories = new EnumMap<>(NodeType.class);

    final NodeRepository nodesRepository;
    private final RootNodeInfoRepositoryProxy rootNodeInfoRepositoryProxy;

    private static final String CATEGORY_BROKER_OUTPUT = NetworkModificationTreeService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge treeUpdatePublisher;

    @Autowired
    private NetworkModificationTreeService self;

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        treeUpdatePublisher.send("publishStudyUpdate-out-0", message);
    }

    private void emitNodeInserted(UUID studyUuid, UUID parentNode, UUID nodeCreated, InsertMode insertMode) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, NODE_CREATED)
            .setHeader(HEADER_PARENT_NODE, parentNode)
            .setHeader(HEADER_NEW_NODE, nodeCreated)
            .setHeader(HEADER_INSERT_MODE, insertMode.name())
            .build()
        );
    }

    private void emitNodesChanged(UUID studyUuid, Collection<UUID> nodes) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, NODE_UPDATED)
            .setHeader(HEADER_NODES, nodes)
            .build()
        );
    }

    private void emitNodesDeleted(UUID studyUuid, Collection<UUID> nodes, boolean deleteChildren) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, NODE_DELETED)
            .setHeader(HEADER_NODES, nodes)
            .setHeader(HEADER_REMOVE_CHILDREN, deleteChildren)
            .build()
        );
    }

    @Autowired
    public NetworkModificationTreeService(NodeRepository nodesRepository,
                                          RootNodeInfoRepository rootNodeInfoRepository,
                                          ModelNodeInfoRepository modelNodeInfoRepository,
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository
    ) {
        this.nodesRepository = nodesRepository;
        this.rootNodeInfoRepositoryProxy = new RootNodeInfoRepositoryProxy(rootNodeInfoRepository);
        repositories.put(NodeType.ROOT, rootNodeInfoRepositoryProxy);
        repositories.put(NodeType.MODEL, new ModelNodeInfoRepositoryProxy(modelNodeInfoRepository));
        repositories.put(NodeType.NETWORK_MODIFICATION, new NetworkModificationNodeInfoRepositoryProxy(networkModificationNodeInfoRepository));

    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public AbstractNode doCreateNode(UUID studyUuid, UUID nodeId, AbstractNode nodeInfo, InsertMode insertMode) {
        Optional<NodeEntity> referenceNode = nodesRepository.findById(nodeId);
        return referenceNode.map(reference -> {
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
            emitNodeInserted(getStudyUuidForNodeId(nodeId), parent.getIdNode(), node.getIdNode(), insertMode);
            return nodeInfo;
        }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    public Mono<AbstractNode> createNode(UUID studyUuid, UUID nodeId, AbstractNode nodeInfo, InsertMode insertMode) {
        return Mono.fromCallable(() -> self.doCreateNode(studyUuid, nodeId, nodeInfo, insertMode));
    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public void doDeleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren, DeleteNodeInfos deleteNodeInfos) {
        List<UUID> removedNodes = new ArrayList<>();
        UUID studyId = getStudyUuidForNodeId(nodeId);
        deleteNodes(nodeId, deleteChildren, false, removedNodes, deleteNodeInfos);
        emitNodesDeleted(studyId, removedNodes, deleteChildren);
    }

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

            UUID modificationGroupUuid = repositories.get(nodeToDelete.getType()).getModificationGroupUuid(id, false);
            if (modificationGroupUuid != null) {
                deleteNodeInfos.addModificationGroupUuid(modificationGroupUuid);
            }
            String variantId = repositories.get(nodeToDelete.getType()).getVariantId(id, false);
            if (!StringUtils.isBlank(variantId)) {
                deleteNodeInfos.addVariantId(variantId);
            }
            UUID securityAnalysisResultUuid = repositories.get(nodeToDelete.getType()).getSecurityAnalysisResultUuid(id);
            if (securityAnalysisResultUuid != null) {
                deleteNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
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
        NodeEntity node = nodesRepository.save(new NodeEntity(null, null, NodeType.ROOT, study));
        var root = RootNode.builder()
            .studyId(study.getId())
            .id(node.getIdNode())
            .name("Root")
            .readOnly(true)
            .build();
        repositories.get(node.getType()).createNodeInfo(root);
        return node;
    }

    @Transactional
    public RootNode doGetStudyTree(UUID studyId) {
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

    public Mono<RootNode> getStudyTree(UUID studyId) {
        return Mono.fromCallable(() -> self.doGetStudyTree(studyId));
    }

    public Mono<Void> updateNode(UUID studyUuid, AbstractNode node) {
        return Mono.fromRunnable(() -> self.doUpdateNode(studyUuid, node));
    }

    @Transactional
    // TODO test if studyUuid exist and have the node
    public void doUpdateNode(UUID studyUuid, AbstractNode node) {
        repositories.get(node.getType()).updateNode(node);
        emitNodesChanged(getStudyUuidForNodeId(node.getId()), Collections.singletonList(node.getId()));
    }

    // TODO test if studyUuid exist and have a node <nodeId>
    public Mono<AbstractNode> getSimpleNode(UUID studyUuid, UUID nodeId) {
        return Mono.fromCallable(() -> self.doGetSimpleNode(nodeId));
    }

    @Transactional
    public AbstractNode doGetSimpleNode(UUID nodeId) {
        AbstractNode node = nodesRepository.findById(nodeId).map(n -> repositories.get(n.getType()).getNode(nodeId)).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        nodesRepository.findAllByParentNodeIdNode(node.getId()).stream().map(NodeEntity::getIdNode).forEach(node.getChildrenIds()::add);
        return node;
    }

    public UUID getStudyRootNodeUuid(UUID studyId) {
        return nodesRepository.findByStudyIdAndType(studyId, NodeType.ROOT).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND)).getIdNode();
    }

    private UUID getModificationNodeUuidFromNode(UUID nodeUuid) {
        Optional<NodeEntity> nodeEntity = nodesRepository.findById(nodeUuid);
        if (!nodeEntity.isPresent()) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }
        // if nodeUuid is a model node, get parent node of type network modification node
        return nodeEntity.get().getType() != NodeType.MODEL
            ? nodeUuid
            : self.doGetParentNode(nodeUuid, NodeType.NETWORK_MODIFICATION).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public String doGetVariantId(UUID nodeUuid, boolean generateId) {
        UUID modificationNodeUuid = getModificationNodeUuidFromNode(nodeUuid);
        return nodesRepository.findById(modificationNodeUuid).map(n -> repositories.get(n.getType()).getVariantId(modificationNodeUuid, generateId)).orElse(null);
    }

    public Mono<String> getVariantId(UUID nodeUuid) {
        return Mono.fromCallable(() -> self.doGetVariantId(nodeUuid, true))
            .switchIfEmpty(Mono.error(new StudyException(ELEMENT_NOT_FOUND)));
    }

    @Transactional
    public UUID doGetModificationGroupUuid(UUID nodeUuid, boolean generateId) {
        UUID modificationNodeUuid = getModificationNodeUuidFromNode(nodeUuid);
        return nodesRepository.findById(modificationNodeUuid).map(n -> repositories.get(n.getType()).getModificationGroupUuid(modificationNodeUuid, generateId)).orElse(null);
    }

    public Mono<UUID> getModificationGroupUuid(UUID nodeUuid) {
        return Mono.fromCallable(() -> self.doGetModificationGroupUuid(nodeUuid, true))
            .switchIfEmpty(Mono.error(new StudyException(ELEMENT_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public List<UUID> getAllModificationGroupUuids(UUID studyUuid) {
        List<UUID> uuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            UUID modificationUuid = repositories.get(n.getType()).getModificationGroupUuid(n.getIdNode(), false);
            if (modificationUuid != null) {
                uuids.add(modificationUuid);
            }
        });
        return uuids;
    }

    @Transactional(readOnly = true)
    public Mono<LoadFlowStatus> getLoadFlowStatus(UUID nodeUuid) {
        return Mono.justOrEmpty(nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getLoadFlowStatus(nodeUuid)));
    }

    @Transactional
    public void doUpdateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus));
        if (updateChildren) {
            nodesRepository.findAllByParentNodeIdNode(nodeUuid)
                .forEach(child -> doUpdateLoadFlowResultAndStatus(child.getIdNode(), loadFlowResult, loadFlowStatus, updateChildren));
        }
    }

    public Mono<Void> updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        return Mono.fromRunnable(() -> self.doUpdateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus, updateChildren));
    }

    @Transactional
    public void doUpdateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateLoadFlowStatus(nodeUuid, loadFlowStatus));
    }

    public Mono<Void> updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        return Mono.fromRunnable(() -> self.doUpdateLoadFlowStatus(nodeUuid, loadFlowStatus));
    }

    @Transactional
    public void doUpdateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid));
    }

    public Mono<Void> updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        return Mono.fromRunnable(() -> self.doUpdateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid));
    }

    @Transactional
    public void doUpdateStudyLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> doUpdateLoadFlowStatus(n.getIdNode(), loadFlowStatus));
    }

    public Mono<Void> updateStudyLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        return Mono.fromRunnable(() -> self.doUpdateStudyLoadFlowStatus(studyUuid, loadFlowStatus));
    }

    @Transactional(readOnly = true)
    public Mono<UUID> getSecurityAnalysisResultUuid(UUID nodeUuid) {
        return Mono.justOrEmpty(nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getSecurityAnalysisResultUuid(nodeUuid)));
    }

    @Transactional(readOnly = true)
    public Mono<List<UUID>> getStudySecurityAnalysisResultUuids(UUID studyUuid) {
        List<UUID> uuids = new ArrayList<>();
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> {
            UUID uuid = repositories.get(n.getType()).getSecurityAnalysisResultUuid(n.getIdNode());
            if (uuid != null) {
                uuids.add(uuid);
            }
        });
        return Mono.just(uuids);
    }

    private void getSecurityAnalysisResultUuids(UUID nodeUuid, List<UUID> uuids) {
        nodesRepository.findById(nodeUuid).flatMap(n -> Optional.ofNullable(repositories.get(n.getType()).getSecurityAnalysisResultUuid(nodeUuid))).ifPresent(uuids::add);
        nodesRepository.findAllByParentNodeIdNode(nodeUuid)
            .forEach(child -> getSecurityAnalysisResultUuids(child.getIdNode(), uuids));
    }

    @Transactional(readOnly = true)
    public Mono<List<UUID>> getSecurityAnalysisResultUuidsFromNode(UUID nodeUuid) {
        List<UUID> uuids = new ArrayList<>();
        getSecurityAnalysisResultUuids(nodeUuid, uuids);
        return Mono.just(uuids);
    }

    @Transactional(readOnly = true)
    public Mono<LoadFlowInfos> getLoadFlowInfos(UUID nodeUuid) {
        return Mono.justOrEmpty(nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getLoadFlowInfos(nodeUuid)));
    }

    private void getBuildInfos(NodeEntity nodeEntity, BuildInfos buildInfos) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.MODEL) {
            ModelNode modelNode = (ModelNode) node;
            if (modelNode.getBuildStatus() != BuildStatus.BUILT) {
                getBuildInfos(nodeEntity.getParentNode(), buildInfos);
            } else {
                Optional<UUID> modificationNodeUuid = self.doGetParentNode(modelNode.getId(), NodeType.NETWORK_MODIFICATION);
                modificationNodeUuid.ifPresent(uuid -> buildInfos.setOriginVariantId(self.doGetVariantId(uuid, true)));
            }
        } else if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            if (modificationNode.getNetworkModification() != null) {
                buildInfos.insertModificationGroup(modificationNode.getNetworkModification());
            }
            if (modificationNode.getModificationsToExclude() != null) {
                buildInfos.addModificationsToExclude(modificationNode.getModificationsToExclude());
            }
            getBuildInfos(nodeEntity.getParentNode(), buildInfos);
        }
    }

    @Transactional
    public BuildInfos getBuildInfos(UUID nodeUuid) {
        // nodeUuid must be a modelNode
        nodesRepository.findById(nodeUuid).ifPresentOrElse(entity -> {
            if (entity.getType() != NodeType.MODEL) {
                throw new StudyException(BAD_NODE_TYPE, "The node " + entity.getIdNode() + " is not a model node");
            }
        }, () -> new StudyException(ELEMENT_NOT_FOUND));
        BuildInfos buildInfos = new BuildInfos();
        Optional<UUID> modificationNodeUuid = self.doGetParentNode(nodeUuid, NodeType.NETWORK_MODIFICATION);
        modificationNodeUuid.ifPresent(uuid -> {
            NodeEntity modificationNodeEntity = nodesRepository.findById(uuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
            buildInfos.setDestinationVariantId(self.doGetVariantId(uuid, true));
            getBuildInfos(modificationNodeEntity, buildInfos);
        });

        return buildInfos;
    }

    @Transactional
    public void invalidateChildrenBuildStatus(NodeEntity nodeEntity, List<UUID> changedNodes, boolean invalidateOnlyChildrenBuildStatus) {
        nodesRepository.findAllByParentNodeIdNode(nodeEntity.getIdNode())
            .forEach(child -> {
                if (child.getType() == NodeType.MODEL && !invalidateOnlyChildrenBuildStatus) {
                    repositories.get(child.getType()).invalidateBuildStatus(child.getIdNode(), changedNodes);
                }
                invalidateChildrenBuildStatus(child, changedNodes, child.getType() != NodeType.MODEL && invalidateOnlyChildrenBuildStatus);
            });
    }

    @Transactional
    public void doUpdateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            repositories.get(n.getType()).updateBuildStatus(nodeUuid, buildStatus, changedNodes);
            invalidateChildrenBuildStatus(n, changedNodes, false);
        });

        if (!changedNodes.isEmpty()) {
            emitNodesChanged(studyId, changedNodes);
        }
    }

    public Mono<Void> updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        return Mono.fromRunnable(() -> self.doUpdateBuildStatus(nodeUuid, buildStatus));
    }

    @Transactional(readOnly = true)
    public BuildStatus doGetBuildStatus(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getBuildStatus(nodeUuid)).orElse(BuildStatus.NOT_BUILT);
    }

    public BuildStatus getBuildStatus(UUID nodeUuid) {
        return self.doGetBuildStatus(nodeUuid);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> doGetParentNode(UUID nodeUuid, NodeType nodeType) {
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

    public Mono<UUID> getParentNode(UUID nodeUuid, NodeType nodeType) {
        return Mono.fromCallable(() -> self.doGetParentNode(nodeUuid, nodeType).orElse(null))
            .switchIfEmpty(Mono.error(new StudyException(ELEMENT_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public UUID doGetLastParentModelNodeBuilt(UUID nodeUuid) {
        NodeEntity nodeEntity = nodesRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        if (nodeEntity.getType() == NodeType.ROOT) {
            return nodeEntity.getIdNode();
        } else if (nodeEntity.getType() == NodeType.MODEL && doGetBuildStatus(nodeEntity.getIdNode()) == BuildStatus.BUILT) {
            return nodeEntity.getIdNode();
        } else {
            return doGetLastParentModelNodeBuilt(nodeEntity.getParentNode().getIdNode());
        }
    }

    @Transactional(readOnly = true)
    public Mono<Boolean> isReadOnly(UUID nodeUuid) {
        return Mono.justOrEmpty(nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).isReadOnly(nodeUuid)));
    }

    @Transactional
    public void doInvalidateBuildStatus(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            if (n.getType() == NodeType.MODEL && !invalidateOnlyChildrenBuildStatus) {
                repositories.get(n.getType()).invalidateBuildStatus(nodeUuid, changedNodes);
            }
            invalidateChildrenBuildStatus(n, changedNodes, n.getType() != NodeType.MODEL && invalidateOnlyChildrenBuildStatus);
        });

        if (!changedNodes.isEmpty()) {
            emitNodesChanged(studyId, changedNodes);
        }
    }

    public Mono<Void> invalidateBuildStatus(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        return Mono.fromRunnable(() -> self.doInvalidateBuildStatus(nodeUuid, invalidateOnlyChildrenBuildStatus));
    }

    @Transactional
    public void doHandleExcludeModification(UUID nodeUuid, UUID modificationUUid, boolean active) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).handleExcludeModification(nodeUuid, modificationUUid, active));
    }

    public Mono<Void> handleExcludeModification(UUID nodeUuid, UUID modificationUUid, boolean active) {
        return Mono.fromRunnable(() -> self.doHandleExcludeModification(nodeUuid, modificationUUid, active));
    }

    @Transactional
    public void doRemoveModificationToExclude(UUID nodeUuid, UUID modificationUUid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).removeModificationToExclude(nodeUuid, modificationUUid));
    }

    public Mono<Void> removeModificationToExclude(UUID nodeUuid, UUID modificationUuid) {
        return Mono.fromRunnable(() -> self.doRemoveModificationToExclude(nodeUuid, modificationUuid));
    }

    public void notifyModificationNodeChanged(UUID studyUuid, UUID nodeUuid) {
        emitNodesChanged(studyUuid, List.of(getModificationNodeUuidFromNode(nodeUuid)));
    }
}
