/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.networkmodificationtree.RootNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
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
import org.gridsuite.study.server.repository.StudyRepository;
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

    public static final String HEADER_NODES = "NODES";
    public static final String HEADER_PARENT_NODE = "PARENT_NODE";
    public static final String HEADER_NEW_NODE = "NEW_NODE";
    public static final String HEADER_REMOVE_CHILDREN = "REMOVE_CHILDREN";
    public static final String NODES_UPDATED = "NODE_UPDATED";
    public static final String NODES_DELETED = "NODE_DELETED";
    public static final String NODE_CREATED = "NODE_CREATED";

    private final EnumMap<NodeType, AbstractNodeRepositoryProxy<?, ?, ?>> repositories = new EnumMap<>(NodeType.class);

    final NodeRepository nodesRepository;
    private final RootNodeInfoRepositoryProxy rootNodeInfoRepositoryProxy;
    private final StudyRepository studyRepository;

    private static final String CATEGORY_BROKER_OUTPUT = NetworkModificationTreeService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge treeUpdatePublisher;

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        treeUpdatePublisher.send("publishStudyUpdate-out-0", message);
    }

    private void emitNodeInserted(UUID studyUuid, UUID parentNode, UUID nodeCreated) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, NODE_CREATED)
            .setHeader(HEADER_PARENT_NODE, parentNode)
            .setHeader(HEADER_NEW_NODE, nodeCreated)
            .build()
        );
    }

    private void emitNodesChanged(UUID studyUuid, Collection<UUID> nodes) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, NODES_UPDATED)
            .setHeader(HEADER_NODES, nodes)
            .build()
        );
    }

    private void emitNodesDeleted(UUID studyUuid, Collection<UUID> nodes, boolean deleteChildren) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, NODES_DELETED)
            .setHeader(HEADER_NODES, nodes)
            .setHeader(HEADER_REMOVE_CHILDREN, deleteChildren)
            .build()
        );
    }

    @Autowired
    public NetworkModificationTreeService(NodeRepository nodesRepository,
                                          RootNodeInfoRepository rootNodeInfoRepository,
                                          ModelNodeInfoRepository modelNodeInfoRepository,
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                                          StudyRepository studyRepository
    ) {
        this.nodesRepository = nodesRepository;
        this.rootNodeInfoRepositoryProxy = new RootNodeInfoRepositoryProxy(rootNodeInfoRepository);
        this.studyRepository = studyRepository;
        repositories.put(NodeType.ROOT, rootNodeInfoRepositoryProxy);
        repositories.put(NodeType.MODEL, new ModelNodeInfoRepositoryProxy(modelNodeInfoRepository));
        repositories.put(NodeType.NETWORK_MODIFICATION, new NetworkModificationNodeInfoRepositoryProxy(networkModificationNodeInfoRepository));

    }

    @Transactional
    public Mono<AbstractNode> createNode(UUID id, AbstractNode nodeInfo) {
        return Mono.fromCallable(() -> {
            Optional<NodeEntity> parentOpt = nodesRepository.findById(id);
            return parentOpt.map(parent -> {
                NodeEntity node = nodesRepository.save(new NodeEntity(null, parent, nodeInfo.getType(), parent.getStudy()));
                nodeInfo.setId(node.getIdNode());
                repositories.get(node.getType()).createNodeInfo(nodeInfo);
                emitNodeInserted(getStudyUuidForNodeId(id), id, node.getIdNode());
                return nodeInfo;
            }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        });
    }

    @Transactional
    public Mono<AbstractNode> insertNode(UUID id, AbstractNode nodeInfo) {
        return Mono.fromCallable(() -> {
            Optional<NodeEntity> childOpt = nodesRepository.findById(id);
            return childOpt.map(child -> {
                if (child.getType().equals(NodeType.ROOT)) {
                    throw new StudyException(NOT_ALLOWED);
                }
                NodeEntity node = nodesRepository.save(new NodeEntity(null, child.getParentNode(), nodeInfo.getType(), child.getStudy()));
                nodeInfo.setId(node.getIdNode());
                repositories.get(node.getType()).createNodeInfo(nodeInfo);
                child.setParentNode(node);
                nodesRepository.save(child);
                emitNodeInserted(node.getStudy().getId(), node.getParentNode().getIdNode(), node.getIdNode());
                return nodeInfo;
            }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        });
    }

    public Mono<Void> deleteNode(UUID id, boolean deleteChildren) {
        return Mono.fromRunnable(() -> {
            List<UUID> removedNodes = new ArrayList<>();
            UUID studyId = getStudyUuidForNodeId(id);
            deleteNodes(id, deleteChildren, false, removedNodes);
            emitNodesDeleted(studyId, removedNodes, deleteChildren);
        });
    }

    public UUID getStudyUuidForNodeId(UUID id) {
        Optional<NodeEntity> node = nodesRepository.findById(id);
        return node.orElseThrow().getStudy().getId();
    }

    private void deleteNodes(UUID id, boolean deleteChildren, boolean allowDeleteRoot, List<UUID> removedNodes) {
        Optional<NodeEntity> optNodeToDelete = nodesRepository.findById(id);
        optNodeToDelete.ifPresent(nodeToDelete -> {
            /* root cannot be deleted by accident */
            if (!allowDeleteRoot && nodeToDelete.getType() == NodeType.ROOT) {
                throw new StudyException(CANT_DELETE_ROOT_NODE);
            }
            if (!deleteChildren) {
                nodesRepository.findAllByParentNodeIdNode(id).forEach(node -> {
                    node.setParentNode(nodeToDelete.getParentNode());
                    nodesRepository.save(node);
                });
            } else {
                nodesRepository.findAllByParentNodeIdNode(nodeToDelete.getIdNode())
                    .forEach(child -> deleteNodes(child.getIdNode(), true, false, removedNodes));
            }
            removedNodes.add(nodeToDelete.getIdNode());
            repositories.get(nodeToDelete.getType()).deleteByNodeId(id);
            nodesRepository.delete(nodeToDelete);
        });
    }

    @Transactional
    public void deleteRoot(UUID studyId) {
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
    public void createRoot(StudyEntity study) {
        NodeEntity node = nodesRepository.save(new NodeEntity(null, null, NodeType.ROOT, study));
        var root = RootNode.builder().studyId(study.getId()).id(node.getIdNode()).name("Root").build();
        repositories.get(node.getType()).createNodeInfo(root);
    }

    @Transactional
    public Mono<RootNode> getStudyTree(UUID studyId) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
        if (nodes.isEmpty()) {
            return Mono.empty();
        }
        Map<UUID, AbstractNode> fullMap = new HashMap<>();
        repositories.forEach((key, repository) ->
            fullMap.putAll(repository.getAll(nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet()))));

        nodes.stream()
            .filter(n -> n.getParentNode() != null)
            .forEach(node -> fullMap.get(node.getParentNode().getIdNode()).getChildren().add(fullMap.get(node.getIdNode())));
        var root = (RootNode) fullMap.get(nodes.stream().filter(n -> n.getType().equals(NodeType.ROOT)).findFirst().orElseThrow().getIdNode());
        if (root != null) {
            root.setStudyId(studyId);
        }
        return Mono.justOrEmpty(root);
    }

    public Mono<Void> updateNode(AbstractNode node) {
        return Mono.fromRunnable(() -> {
            repositories.get(node.getType()).updateNode(node);
            emitNodesChanged(getStudyUuidForNodeId(node.getId()), Collections.singleton(node.getId()));
        });
    }

    public Mono<AbstractNode> getSimpleNode(UUID id) {
        return Mono.fromCallable(() -> {
            AbstractNode node = nodesRepository.findById(id).map(n -> repositories.get(n.getType()).getNode(id)).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
            nodesRepository.findAllByParentNodeIdNode(node.getId()).stream().map(NodeEntity::getIdNode).forEach(node.getChildrenIds()::add);
            return node;
        });
    }
}
