/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.loadflow.LoadFlowResult;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.networkmodificationtree.RootNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.AbstractNodeRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.ReportUsageEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.networkmodificationtree.NetworkModificationNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.NodeRepository;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.repositories.ReportUsageRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.StudyService.*;

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
    public static final String ROOT_NODE_NAME = "Root";

    private final EnumMap<NodeType, AbstractNodeRepositoryProxy<?, ?, ?>> repositories = new EnumMap<>(NodeType.class);

    private final NodeRepository nodesRepository;

    private final ReportUsageRepository reportsUsagesRepository;

    private static final String CATEGORY_BROKER_OUTPUT = NetworkModificationTreeService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Autowired
    private StreamBridge treeUpdatePublisher;

    @Autowired
    private NetworkModificationService networkModificationService;

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
        if (nodes.isEmpty()) {
            return;
        }
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
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                                          ReportUsageRepository reportsUsagesRepository
    ) {
        this.nodesRepository = nodesRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        repositories.put(NodeType.ROOT, new RootNodeInfoRepositoryProxy(rootNodeInfoRepository));
        repositories.put(NodeType.NETWORK_MODIFICATION, new NetworkModificationNodeInfoRepositoryProxy(networkModificationNodeInfoRepository));
        this.reportsUsagesRepository = reportsUsagesRepository;
    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public AbstractNode createNode(UUID studyUuid, UUID nodeId, AbstractNode nodeInfo, InsertMode insertMode) {
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
            emitNodeInserted(getStudyUuidForNodeId(nodeId), parent.getIdNode(), node.getIdNode(), insertMode);
            return nodeInfo;
        }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
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

            UUID modificationGroupUuid = repositories.get(nodeToDelete.getType()).getModificationGroupUuid(id);
            deleteNodeInfos.addModificationGroupUuid(modificationGroupUuid);

            UUID reportUuid = repositories.get(nodeToDelete.getType()).getReportUuid(id);
            if (reportUuid != null) {
                deleteNodeInfos.addReportUuid(reportUuid);
            }
            List<ReportUsageEntity> reportUsageEntities = reportsUsagesRepository.getReportUsageEntities(nodeToDelete.getIdNode());
            reportUsageEntities.stream().map(ReportUsageEntity::getReportId).forEach(deleteNodeInfos::addReportUuid);

            String variantId = repositories.get(nodeToDelete.getType()).getVariantId(id);
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
    public void doDeleteTree(UUID studyId, List<UUID> buildReportsUuids) {
        try {
            Set<UUID> allReportUuids = new HashSet<>();
            List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
            nodes.forEach(n -> {
                AbstractNode node = repositories.get(n.getType()).getNode(n.getIdNode());
                allReportUuids.add(node.getReportUuid());
            });
            repositories.forEach((key, repository) -> {
                    repository.deleteAll(
                        nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet()));
                }
            );

            Set<UUID> allReportUsageUuids = new HashSet<>();

            // first calls of getReportUsageEntities may bring several times same ancestor report usages,
            // though we could use a more refined query for this case.
            nodes.forEach(n -> {
                List<ReportUsageEntity> reportUsageEntities = reportsUsagesRepository.getReportUsageEntities(n.getIdNode());
                allReportUuids.addAll(reportUsageEntities.stream().map(ReportUsageEntity::getReportId).collect(Collectors.toList()));
                allReportUsageUuids.addAll(reportUsageEntities.stream().map(ReportUsageEntity::getId).collect(Collectors.toList()));
            });

            if (buildReportsUuids != null) {
                buildReportsUuids.addAll(allReportUuids);
            }
            nodesRepository.deleteAll(nodes);
            reportsUsagesRepository.deleteAllById(allReportUsageUuids);
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

                nextParentId = createNode(study.getId(), referenceParentNodeId, model, InsertMode.CHILD).getId();
                networkModificationService.createModifications(modificationGroupToDuplicateId, newModificationGroupId, newReportUuid);
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
                .name("modification node 0")
                .variantId(FIRST_VARIANT_ID)
                .loadFlowStatus(LoadFlowStatus.NOT_DONE)
                .buildStatus(BuildStatus.BUILT)
                .build();
        createNode(studyEntity.getId(), rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER);
    }

    @Transactional
    public void updateNode(UUID studyUuid, AbstractNode node) {
        NetworkModificationNodeInfoEntity networkModificationNode = networkModificationNodeInfoRepository.findById(node.getId()).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (!networkModificationNode.getName().equals(node.getName())) {
            assertNodeNameNotExist(studyUuid, node.getName());
        }
        repositories.get(node.getType()).updateNode(node);
        emitNodesChanged(getStudyUuidForNodeId(node.getId()), Collections.singletonList(node.getId()));
    }

    // TODO test if studyUuid exist and have a node <nodeId>
    @Transactional
    public AbstractNode getSimpleNode(UUID nodeId) {
        AbstractNode node = nodesRepository.findById(nodeId).map(n -> repositories.get(n.getType()).getNode(nodeId)).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
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
        int counter = 1;
        List<String> studyNodeNames = networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid)
                .stream()
                .map(AbstractNodeInfoEntity::getName)
                .collect(Collectors.toList());

        String namePrefix = "New node ";
        String uniqueName = StringUtils.EMPTY;
        while (StringUtils.EMPTY.equals(uniqueName) || studyNodeNames.contains(uniqueName)) {
            uniqueName = namePrefix + counter;
            ++counter;
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
    public String getNetworkModifications(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
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
    public void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateLoadFlowStatus(nodeUuid, loadFlowStatus));
    }

    @Transactional
    public void updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid));
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
    public LoadFlowInfos getLoadFlowInfos(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getLoadFlowInfos(nodeUuid)).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    private void fillBuildInfos(NodeEntity nodeEntity, BuildInfos buildInfos, NodeEntity toBuildNode) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            if (modificationNode.getBuildStatus() != BuildStatus.BUILT) {
                UUID reportUuid;
                if (nodeEntity.getIdNode().equals(toBuildNode.getIdNode())) {
                    reportUuid = modificationNode.getReportUuid();
                } else {
                    reportUuid = UUID.randomUUID();
                    reportsUsagesRepository.save(new ReportUsageEntity(null, reportUuid, toBuildNode, nodeEntity));
                }
                buildInfos.insertModificationGroupAndReport(modificationNode.getModificationGroupUuid(), reportUuid);
            }
            if (modificationNode.getModificationsToExclude() != null) {
                buildInfos.addModificationsToExclude(modificationNode.getModificationsToExclude());
            }
            if (modificationNode.getBuildStatus() == BuildStatus.BUILT) {
                List<ReportUsageEntity> usages = reportsUsagesRepository.getReportUsageEntities(nodeEntity.getIdNode());
                usages.forEach(usage -> {
                    // avoid duplicates from children
                    if (usage.getBuildNode().getIdNode().equals(nodeEntity.getIdNode())) {
                        reportsUsagesRepository.save(new ReportUsageEntity(null, usage.getReportId(), toBuildNode,
                            usage.getDefinitionNode()));
                    }
                });
                buildInfos.setOriginVariantId(getVariantId(nodeEntity.getIdNode()));
            } else {
                fillBuildInfos(nodeEntity.getParentNode(), buildInfos, toBuildNode);
            }
        }
    }

    @Transactional
    public BuildInfos fillBuildInfos(UUID nodeUuid) {
        BuildInfos buildInfos = new BuildInfos();

        nodesRepository.findById(nodeUuid).ifPresentOrElse(entity -> {
            if (entity.getType() != NodeType.NETWORK_MODIFICATION) {  // nodeUuid must be a modification node
                throw new StudyException(BAD_NODE_TYPE, "The node " + entity.getIdNode() + " is not a modification node");
            } else {
                buildInfos.setDestinationVariantId(getVariantId(nodeUuid));
                fillBuildInfos(entity, buildInfos, entity);
            }
        }, () -> {
                throw new StudyException(ELEMENT_NOT_FOUND);
            });

        return buildInfos;
    }

    @Transactional
    public void invalidateBuild(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            // No need to invalidate a node with a status different of "BUILT"
            BuildStatus wasBuildStatus = repositories.get(n.getType()).getBuildStatus(n.getIdNode());
            if (wasBuildStatus == BuildStatus.BUILT) {
                fillInvalidateNodeInfos(n, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus);
                if (!invalidateOnlyChildrenBuildStatus) {
                    repositories.get(n.getType()).invalidateBuildStatus(nodeUuid, changedNodes);
                }
                repositories.get(n.getType()).updateLoadFlowResultAndStatus(nodeUuid, null, LoadFlowStatus.NOT_DONE);
            }
            invalidateChildrenBuildStatus(n, changedNodes, invalidateNodeInfos);
        });

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emitNodesChanged(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
            }
        });
    }

    private void fillInvalidateNodeInfos(NodeEntity node, InvalidateNodeInfos invalidateNodeInfos,
        boolean invalidateOnlyChildrenBuildStatus) {

        var repositoryProxy = repositories.get(node.getType());
        UUID nodeUuid = node.getIdNode();
        NetworkModificationNode modificationNode = (NetworkModificationNode) repositoryProxy.getNode(nodeUuid);

        if (!invalidateOnlyChildrenBuildStatus) {
            List<ReportUsageEntity> usages = reportsUsagesRepository.getReportUsageEntities(node.getIdNode());
            Set<UUID> ownUsedReportIds = new HashSet<>();
            Set<UUID> otherUsedReportIds = new HashSet<>();
            usages.forEach(u -> {
                if (u.getBuildNode().getIdNode().equals(node.getIdNode())) {
                    ownUsedReportIds.add(u.getReportId());
                } else {
                    otherUsedReportIds.add(u.getReportId());
                }
            });
            List<UUID> ownUsagesUuids = usages.stream()
                .filter(u -> u.getBuildNode().getIdNode().equals(node.getIdNode()))
                .map(ReportUsageEntity::getId)
                .collect(Collectors.toList());
            reportsUsagesRepository.deleteAllByIdInBatch(ownUsagesUuids);

            UUID reportUuid = modificationNode.getReportUuid();
            String variantId = modificationNode.getVariantId();
            ownUsedReportIds.add(reportUuid);

            invalidateNodeInfos.addVariantId(variantId);
            ownUsedReportIds.removeAll(otherUsedReportIds);
            ownUsedReportIds.forEach(invalidateNodeInfos::addReportUuid);
        }

        UUID securityAnalysisResultUuid = repositoryProxy.getSecurityAnalysisResultUuid(nodeUuid);
        if (securityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        }
    }

    private void invalidateChildrenBuildStatus(NodeEntity nodeEntity, List<UUID> changedNodes,
        InvalidateNodeInfos invalidateNodeInfos) {
        nodesRepository.findAllByParentNodeIdNode(nodeEntity.getIdNode())
            .forEach(child -> {
                // No need to invalidate a node with a status different of "BUILT"
                BuildStatus wasBuildStatus = repositories.get(child.getType()).getBuildStatus(child.getIdNode());
                if (wasBuildStatus == BuildStatus.BUILT) {
                    fillInvalidateNodeInfos(child, invalidateNodeInfos, false);
                    repositories.get(child.getType()).invalidateBuildStatus(child.getIdNode(), changedNodes);
                    repositories.get(child.getType()).updateLoadFlowResultAndStatus(child.getIdNode(), null, LoadFlowStatus.NOT_DONE);
                }
                invalidateChildrenBuildStatus(child, changedNodes, invalidateNodeInfos);
            });
    }

    @Transactional
    public void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateBuildStatus(nodeUuid, buildStatus, changedNodes));

        emitNodesChanged(studyId, changedNodes);
    }

    @Transactional(readOnly = true)
    public BuildStatus getBuildStatus(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getBuildStatus(nodeUuid)).orElse(BuildStatus.NOT_BUILT);
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
    public UUID doGetLastParentNodeBuilt(UUID nodeUuid) {
        NodeEntity nodeEntity = nodesRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
        if (nodeEntity.getType() == NodeType.ROOT) {
            return nodeEntity.getIdNode();
        } else if (getBuildStatus(nodeEntity.getIdNode()) == BuildStatus.BUILT) {
            return nodeEntity.getIdNode();
        } else {
            return doGetLastParentNodeBuilt(nodeEntity.getParentNode().getIdNode());
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

    public void notifyModificationNodeChanged(UUID studyUuid, UUID nodeUuid) {
        emitNodesChanged(studyUuid, List.of(nodeUuid));
    }

    private void fillNodesInBuildOrder(NodeEntity nodeEntity, boolean nodeOnlyReport,
        Map<UUID, Pair<UUID, String>> defNodeIdToReport,
        List<Pair<UUID, String>> uuidsAndNames) {

        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (nodeEntity.getType() != NodeType.NETWORK_MODIFICATION) {
            uuidsAndNames.add(0, Pair.of(node.getReportUuid(), ROOT_NODE_NAME));
        } else {
            Pair<UUID, String> p = defNodeIdToReport.get(nodeEntity.getIdNode());
            // found usage : use it ! Otherwise, was an already built node by time of build
            // if it as changed current node has been invalidated
            uuidsAndNames.add(0, Objects.requireNonNullElseGet(p, () -> Pair.of(node.getReportUuid(), node.getName())));

            if (!nodeOnlyReport) {
                fillNodesInBuildOrder(nodeEntity.getParentNode(), false, defNodeIdToReport, uuidsAndNames);
            }
        }
    }

    private List<Pair<UUID, String>> getParentReportUuidsAndNamesFromNode(UUID nodeUuid, boolean nodeOnlyReport) {
        List<Pair<UUID, String>> uuidsAndNames = new ArrayList<>();
        Map<UUID, Pair<UUID, String>> defNodeIdToReport = new HashMap<>();
        nodesRepository.findById(nodeUuid).ifPresentOrElse(buildNodeEntity -> {
            List<ReportUsageEntity> usages = reportsUsagesRepository.getReportUsageEntities(buildNodeEntity.getIdNode());
            usages.forEach(us -> {
                if (us.getBuildNode().getIdNode().equals(buildNodeEntity.getIdNode())
                    && (!nodeOnlyReport || us.getDefinitionNode().getIdNode().equals(nodeUuid)))  {
                    NodeEntity definitionNodeEntity = us.getDefinitionNode();
                    AbstractNode definitionNode = repositories.get(definitionNodeEntity.getType()).getNode(definitionNodeEntity.getIdNode());
                    defNodeIdToReport.put(definitionNodeEntity.getIdNode(),
                        Pair.of(us.getReportId(), definitionNode.getName()));
                }
            });

            fillNodesInBuildOrder(buildNodeEntity, nodeOnlyReport, defNodeIdToReport, uuidsAndNames);
        }, () -> {
                throw new StudyException(ELEMENT_NOT_FOUND);
            });
        return uuidsAndNames;
    }

    @Transactional
    public List<Pair<UUID, String>> getReportUuidsAndNames(UUID nodeUuid, boolean nodeOnlyReport) {
        List<Pair<UUID, String>> uuidsAndNames = getParentReportUuidsAndNamesFromNode(nodeUuid, nodeOnlyReport);
        return uuidsAndNames;
    }

    @Transactional
    public NodeModificationInfos getNodeModificationInfos(UUID nodeUuid) {
        NodeModificationInfos nodeModificationInfos = nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getNodeModificationInfos(nodeUuid)).orElse(null);
        if (nodeModificationInfos == null) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }
        return nodeModificationInfos;
    }
}
