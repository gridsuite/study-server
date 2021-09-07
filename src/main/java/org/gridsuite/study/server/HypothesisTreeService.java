/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.hypothesisTree.RootNodeInfoRepositoryProxy;
import org.gridsuite.study.server.hypothesisTree.dto.AbstractNode;
import org.gridsuite.study.server.hypothesisTree.dto.RootNode;
import org.gridsuite.study.server.hypothesisTree.AbstractNodeRepositoryProxy;
import org.gridsuite.study.server.hypothesisTree.repositories.HypothesisNodeInfoRepository;
import org.gridsuite.study.server.hypothesisTree.HypothesisNodeInfoRepositoryProxy;
import org.gridsuite.study.server.hypothesisTree.repositories.ModelNodeInfoRepository;
import org.gridsuite.study.server.hypothesisTree.entities.NodeEntity;
import org.gridsuite.study.server.hypothesisTree.ModelNodeInfoRepositoryProxy;
import org.gridsuite.study.server.hypothesisTree.repositories.NodeRepository;
import org.gridsuite.study.server.hypothesisTree.entities.NodeType;
import org.gridsuite.study.server.hypothesisTree.repositories.RootNodeInfoRepository;
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
import java.util.EnumMap;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.CANT_DELETE_ROOT_NODE;
import static org.gridsuite.study.server.StudyException.Type.ELEMENT_NOT_FOUND;
import static org.gridsuite.study.server.StudyService.HEADER_STUDY_UUID;
import static org.gridsuite.study.server.StudyService.HEADER_UPDATE_TYPE;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
@Service
public class HypothesisTreeService {

    private final EnumMap<NodeType, AbstractNodeRepositoryProxy<?, ?, ?>> repositories = new EnumMap<>(NodeType.class);

    final NodeRepository nodesRepository;
    private final RootNodeInfoRepositoryProxy rootNodeInfoRepositoryProxy;

    private static final String CATEGORY_BROKER_OUTPUT = HypothesisTreeService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge treeUpdatePublisher;

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        treeUpdatePublisher.send("publishTreeUpdate-out-0", message);
    }

    private void emitTreeChanged(UUID studyUuid, String updateType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, updateType)
            .build()
        );
    }

    @Autowired
    public HypothesisTreeService(NodeRepository nodesRepository,
                                 RootNodeInfoRepository rootNodeInfoRepository,
                                 ModelNodeInfoRepository modelNodeInfoRepository,
                                 HypothesisNodeInfoRepository hypothesisNodeInfoRepository
    ) {
        this.nodesRepository = nodesRepository;
        this.rootNodeInfoRepositoryProxy = new RootNodeInfoRepositoryProxy(rootNodeInfoRepository);
        repositories.put(NodeType.ROOT, rootNodeInfoRepositoryProxy);
        repositories.put(NodeType.MODEL, new ModelNodeInfoRepositoryProxy(modelNodeInfoRepository));
        repositories.put(NodeType.HYPOTHESIS, new HypothesisNodeInfoRepositoryProxy(hypothesisNodeInfoRepository));

    }

    @Transactional
    public Mono<AbstractNode> createNode(UUID id, AbstractNode nodeInfo) {
        return Mono.fromCallable(() -> {
            Optional<NodeEntity> parentOpt = nodesRepository.findById(id);
            return parentOpt.map(parent -> {
                NodeEntity node = nodesRepository.save(new NodeEntity(null, parent, nodeInfo.getType()));
                nodeInfo.setId(node.getIdNode());
                repositories.get(node.getType()).createNodeInfo(nodeInfo);
                return nodeInfo;
            }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        });
    }

    @Transactional
    public Mono<Void> deleteNode(UUID id, Boolean deleteChildren) {
        return Mono.fromRunnable(() -> {
            deleteNodes(id, deleteChildren, false);
        });
    }

    private void deleteNodes(UUID id, Boolean deleteChildren, boolean allowDeleteRoot) {
        Optional<NodeEntity> optNodeToDelete = nodesRepository.findById(id);
        optNodeToDelete.ifPresent(nodeToDelete -> {
            /* root cannot be deleted */
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
                    .forEach(child -> deleteNodes(child.getIdNode(), true, false));
            }
            repositories.get(nodeToDelete.getType()).deleteByNodeId(id);
            nodesRepository.delete(nodeToDelete);
        });
    }

    public void deleteRoot(UUID studyId) {
        try {
            rootNodeInfoRepositoryProxy.getByStudyId(studyId).ifPresent(root -> deleteNodes(root.getId(), true, true));
        } catch (EntityNotFoundException ignored) {
        }
    }

    @Transactional
    public void createRoot(UUID studyId) {
        NodeEntity node = nodesRepository.save(new NodeEntity(null, null, NodeType.ROOT));
        var root = RootNode.builder().studyId(studyId).id(node.getIdNode()).name("Root").build();
        repositories.get(node.getType()).createNodeInfo(root);
    }

    @Transactional
    public Mono<RootNode> getStudyTree(UUID studyId) {
        return Mono.justOrEmpty(rootNodeInfoRepositoryProxy.getByStudyId(studyId).map(root -> {
            completeChildren(root);
            return root;
        }));
    }

    private void completeChildren(AbstractNode nodeToComplete) {
        nodesRepository.findAllByParentNodeIdNode(nodeToComplete.getId()).forEach(node -> nodeToComplete.getChildren().add(getNode(node)));
    }

    private AbstractNode getNode(NodeEntity nodeEntity) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        completeChildren(node);
        return node;
    }

    private UUID getStudyUuidForNode(NodeEntity node) {
        NodeEntity current = node;
        while (current.getParentNode() != null || current.getType() != NodeType.ROOT) {
            current = current.getParentNode();
        }
        return rootNodeInfoRepositoryProxy.getNode(current.getIdNode()).getStudyId();
    }

    public Mono<Void> updateNode(AbstractNode node) {
        return Mono.fromRunnable(() -> repositories.get(node.getType()).updateNode(node));
    }
}
