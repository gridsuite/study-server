/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.powsybl.commons.report.ReportNode;
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

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
@Service
public class NetworkModificationTreeService {

    public static final String ROOT_NODE_NAME = "Root";

    public static final String FIRST_VARIANT_ID = "first_variant_id";

    private final EnumMap<NodeType, AbstractNodeRepositoryProxy<?, ?, ?>> repositories = new EnumMap<>(NodeType.class);

    private final NodeRepository nodesRepository;

    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    private final NetworkModificationService networkModificationService;
    private final NotificationService notificationService;

    private final NetworkModificationTreeService self;
    private final RootNodeInfoRepository rootNodeInfoRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkRepository rootNetworkRepository;
    private final ReportService reportService;

    public NetworkModificationTreeService(NodeRepository nodesRepository,
                                          RootNodeInfoRepository rootNodeInfoRepository,
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                                          NotificationService notificationService,
                                          NetworkModificationService networkModificationService,
                                          @Lazy NetworkModificationTreeService networkModificationTreeService,
                                          RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                          RootNetworkService rootNetworkService, RootNetworkRepository rootNetworkRepository,
                                          ReportService reportService) {
        this.nodesRepository = nodesRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.networkModificationService = networkModificationService;
        this.notificationService = notificationService;
        repositories.put(NodeType.ROOT, new RootNodeInfoRepositoryProxy(rootNodeInfoRepository));
        repositories.put(NodeType.NETWORK_MODIFICATION, new NetworkModificationNodeInfoRepositoryProxy(networkModificationNodeInfoRepository));
        this.self = networkModificationTreeService;
        this.rootNodeInfoRepository = rootNodeInfoRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkRepository = rootNetworkRepository;
        this.reportService = reportService;
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
            return nodeInfo;
        }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public NetworkModificationNode createNode(@NonNull StudyEntity study, @NonNull UUID nodeId, @NonNull NetworkModificationNode nodeInfo, @NonNull InsertMode insertMode, String userId) {
        // create new node
        NetworkModificationNode newNode = createAndInsertNode(study, nodeId, nodeInfo, insertMode, userId);

        NetworkModificationNodeInfoEntity newNodeInfoEntity = networkModificationNodeInfoRepository.getReferenceById(newNode.getId());
        rootNetworkNodeInfoService.createNodeLinks(study, newNodeInfoEntity);

        return newNode;
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

        UUID newGroupUuid = UUID.randomUUID();
        UUID modificationGroupUuid = self.getModificationGroupUuid(nodeToCopyUuid);
        //First we create the modification group
        networkModificationService.duplicateModificationsGroup(modificationGroupUuid, newGroupUuid);

        //Then we create the node
        NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity = networkModificationNodeInfoRepository.findById(nodeToCopyUuid).orElseThrow(() -> new StudyException(GET_MODIFICATIONS_FAILED));
        UUID studyUuid = anchorNodeEntity.getStudy().getId();

        NetworkModificationNode node = self.createNode(
            anchorNodeEntity.getStudy(),
            anchorNodeUuid,
            NetworkModificationNode.builder()
                .modificationGroupUuid(newGroupUuid)
                .name(getSuffixedNodeName(studyUuid, networkModificationNodeInfoEntity.getName()))
                .description(networkModificationNodeInfoEntity.getDescription())
                .build(),
                insertMode,
                null
        );

        return node.getId();
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
    public List<UUID> doDeleteNode(UUID nodeId, boolean deleteChildren, DeleteNodeInfos deleteNodeInfos) {
        List<UUID> removedNodes = new ArrayList<>();
        UUID studyId = self.getStudyUuidForNodeId(nodeId);
        deleteNodes(nodeId, deleteChildren, false, removedNodes, deleteNodeInfos);
        notificationService.emitNodesDeleted(studyId, removedNodes, deleteChildren);
        return removedNodes;
    }

    @Transactional
    // TODO test if studyUuid exist and have a node <nodeId>
    public void doStashNode(UUID nodeId, boolean stashChildren) {
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

            //complete deleteNodeInfos with computation result and report uuids
            rootNetworkNodeInfoService.fillDeleteNodeInfo(id, deleteNodeInfos);

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
    public RootNode getStudyTree(UUID studyId, UUID rootNetworkUuid) {
        NodeEntity rootNode = nodesRepository.findByStudyIdAndType(studyId, NodeType.ROOT).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        RootNode studyTree = (RootNode) getStudySubtree(studyId, rootNode.getIdNode(), rootNetworkUuid);
        if (studyTree != null) {
            studyTree.setStudyId(studyId);
        }
        return studyTree;
    }

    private void completeNodeInfos(List<AbstractNode> nodes, UUID rootNetworkUuid) {
        RootNetworkEntity rootNetworkEntity = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
        nodes.forEach(nodeInfo -> {
            if (nodeInfo instanceof RootNode rootNode) {
                rootNode.setReportUuid(rootNetworkEntity.getReportUuid());
            } else {
                ((NetworkModificationNode) nodeInfo).completeDtoFromRootNetworkNodeInfo(rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeInfo.getId(), rootNetworkEntity.getId()).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)));
            }
        });
    }

    @Transactional
    public AbstractNode getStudySubtree(UUID studyId, UUID parentNodeUuid, UUID rootNetworkUuid) {
//        TODO: not working because of proxy appearing in tests TOFIX later
//        List<UUID> nodeUuids = nodesRepository.findAllDescendants(parentNodeUuid).stream().map(UUID::fromString).toList();
//        List<NodeEntity> nodes = nodesRepository.findAllById(nodeUuids);
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);

        List<AbstractNode> allNodeInfos = new ArrayList<>();
        repositories.forEach((key, repository) -> allNodeInfos.addAll(repository.getAll(
            nodes.stream().filter(n -> n.getType().equals(key)).map(NodeEntity::getIdNode).collect(Collectors.toSet()))));
        if (rootNetworkUuid != null) {
            completeNodeInfos(allNodeInfos, rootNetworkUuid);
        }
        Map<UUID, AbstractNode> fullMap = allNodeInfos.stream().collect(Collectors.toMap(AbstractNode::getId, Function.identity()));

        nodes.stream()
            .filter(n -> n.getParentNode() != null)
            .forEach(node -> fullMap.get(node.getParentNode().getIdNode()).getChildren().add(fullMap.get(node.getIdNode())));
        return fullMap.get(parentNodeUuid);
    }

    @Transactional
    public void duplicateStudyNodes(StudyEntity studyEntity, UUID sourceStudyUuid, UUID rootNetworkUuid) {
        createRoot(studyEntity);
        AbstractNode rootNode = getStudyTree(sourceStudyUuid, rootNetworkUuid);
        cloneStudyTree(rootNode, null, studyEntity);
    }

    @Transactional
    public UUID cloneStudyTree(AbstractNode nodeToDuplicate, UUID nodeParentId, StudyEntity study) {
        UUID rootId = null;
        if (NodeType.ROOT.equals(nodeToDuplicate.getType())) {
            rootId = getStudyRootNodeUuid(study.getId());
        }
        UUID nextParentId;
        UUID newModificationGroupId = UUID.randomUUID();

        if (nodeToDuplicate instanceof NetworkModificationNode model) {
            UUID modificationGroupToDuplicateId = model.getModificationGroupUuid();
            model.setModificationGroupUuid(newModificationGroupId);
            model.setName(getSuffixedNodeName(study.getId(), model.getName()));

            nextParentId = self.createNode(study, nodeParentId, model, InsertMode.CHILD, null).getId();
            networkModificationService.duplicateModificationsGroup(modificationGroupToDuplicateId, newModificationGroupId);
        } else {
            // when cloning studyTree, we don't clone root node
            // if cloning the whole study, the root node is previously created
            nextParentId = rootId;
        }
        nodeToDuplicate.getChildren().forEach(childToDuplicate -> self.cloneStudyTree(childToDuplicate, nextParentId, study));

        return nextParentId;
    }

    @Transactional
    public void createBasicTree(StudyEntity studyEntity) {
        // create 2 nodes : root node, modification node N1
        NodeEntity rootNodeEntity = self.createRoot(studyEntity);
        NetworkModificationNode modificationNode = NetworkModificationNode
            .builder()
            .name("N1")
            .build();

        NetworkModificationNode networkModificationNode = createNode(studyEntity, rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER, null);
        ReportNode reportNode = ReportNode.newRootReportNode().withMessageTemplate(modificationNode.getId().toString(), modificationNode.getId().toString()).build();
        reportService.sendReport(getModificationReportUuid(networkModificationNode.getId(), studyEntity.getFirstRootNetwork().getId(), networkModificationNode.getId()), reportNode);

        BuildInfos buildInfos = getBuildInfos(modificationNode.getId(), studyEntity.getFirstRootNetwork().getId());
        Map<UUID, UUID> nodeUuidToReportUuid = buildInfos.getReportsInfos().stream().collect(Collectors.toMap(ReportInfos::nodeUuid, ReportInfos::reportUuid));
        rootNetworkNodeInfoService.updateRootNetworkNode(networkModificationNode.getId(), studyEntity.getFirstRootNetwork().getId(),
            RootNetworkNodeInfo.builder().variantId(FIRST_VARIANT_ID).nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT)).modificationReports(nodeUuidToReportUuid).build());
    }

    @Transactional
    public void updateNode(UUID studyUuid, NetworkModificationNode node, String userId) {
        NetworkModificationNodeInfoEntity networkModificationNodeEntity = networkModificationNodeInfoRepository.findById(node.getId()).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
        if (!networkModificationNodeEntity.getName().equals(node.getName())) {
            assertNodeNameNotExist(studyUuid, node.getName());
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

    @Transactional
    public void updateNodesColumnPositions(UUID studyUuid, UUID parentUuid, List<NetworkModificationNode> childrenNodes, String userId) {
        // Convert to a map for quick lookup
        Map<UUID, Integer> nodeIdToColumnPosition = childrenNodes.stream()
                .collect(Collectors.toMap(NetworkModificationNode::getId, NetworkModificationNode::getColumnPosition));

        List<UUID> childrenIds = childrenNodes.stream().map(NetworkModificationNode::getId).toList();
        networkModificationNodeInfoRepository.findAllById(childrenIds).forEach(entity -> {
            Integer newColumnPosition = nodeIdToColumnPosition.get(entity.getId());
            entity.setColumnPosition(Objects.requireNonNull(newColumnPosition));
        });

        List<UUID> orderedUuids = childrenNodes.stream()
            .sorted(Comparator.comparingInt(AbstractNode::getColumnPosition))
            .map(NetworkModificationNode::getId)
            .toList();

        notificationService.emitColumnsChanged(studyUuid, parentUuid, orderedUuids);
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
    public AbstractNode getNode(UUID nodeId, UUID rootNetworkUuid) {
        AbstractNode node = getSimpleNode(nodeId);
        nodesRepository.findAllByParentNodeIdNode(node.getId()).stream().map(NodeEntity::getIdNode).forEach(node.getChildrenIds()::add);
        if (rootNetworkUuid != null) {
            completeNodeInfos(List.of(node), rootNetworkUuid);
        }
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

        return rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)).getVariantId();
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
            return rootNetworkService.getRootReportUuid(rootNetworkUuid);
        } else {
            return rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)).getModificationReports().get(nodeUuid);
        }
    }

    public List<NetworkModificationNodeInfoEntity> getAllStudyNetworkModificationNodeInfo(UUID studyUuid) {
        return networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid);
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
            if (hasChildren(nodeId)) {
                restoreNodeChildren(studyId, nodeId);
                notificationService.emitSubtreeInserted(studyId, nodeId, anchorNodeId);
            } else {
                notificationService.emitNodeInserted(studyId, anchorNodeId, nodeId, InsertMode.CHILD, anchorNodeId);
            }
        }
    }

    private boolean hasChildren(UUID nodeId) {
        return nodesRepository.countByParentNodeIdNode(nodeId) > 0;
    }

    @Transactional
    public void updateComputationReportUuid(UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType, UUID reportUuid) {
        rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).ifPresent(tpNodeInfo -> tpNodeInfo.getComputationReports().put(computationType.name(), reportUuid));
    }

    @Transactional
    public Map<String, UUID> getComputationReports(UUID nodeUuid, UUID rootNetworkUuid) {
        return rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND)).getComputationReports();
    }

    @Transactional
    public void setModificationReports(UUID nodeUuid, UUID rootNetworkUuid, Map<UUID, UUID> modificationReports) {
        rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).ifPresent(tpNodeInfo -> tpNodeInfo.setModificationReports(modificationReports));
    }

    @Transactional
    public Map<UUID, UUID> getModificationReports(UUID nodeUuid, UUID rootNetworkUuid) {
        return rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(NODE_NOT_FOUND)).getModificationReports();
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
            restoreNodeChildren(studyId, nodeEntity.getIdNode());
        });
    }

    public List<NodeEntity> getAllNodes(UUID studyUuid) {
        return nodesRepository.findAllByStudyId(studyUuid);
    }

    private UUID getModificationReportUuid(UUID nodeUuid, UUID rootNetworkUuid, UUID nodeToBuildUuid) {
        return self.getModificationReports(nodeToBuildUuid, rootNetworkUuid).getOrDefault(nodeUuid, UUID.randomUUID());
    }

    private void getBuildInfos(NodeEntity nodeEntity, UUID rootNetworkUuid, BuildInfos buildInfos, UUID nodeToBuildUuid) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeEntity.getIdNode(), rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
            if (!rootNetworkNodeInfoEntity.getNodeBuildStatus().toDto().isBuilt()) {
                UUID reportUuid = getModificationReportUuid(nodeEntity.getIdNode(), rootNetworkUuid, nodeToBuildUuid);
                buildInfos.insertModificationInfos(modificationNode.getModificationGroupUuid(), rootNetworkNodeInfoEntity.getModificationsToExclude(), new ReportInfos(reportUuid, modificationNode.getId()));
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

    @Transactional
    public void invalidateBuild(UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos, boolean deleteVoltageInitResults) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);
        nodesRepository.findById(nodeUuid).ifPresent(nodeEntity -> {
            if (rootNetworkService.exists(rootNetworkUuid)) {
                if (nodeEntity.getType().equals(NodeType.NETWORK_MODIFICATION)) {
                    rootNetworkNodeInfoService.invalidateRootNetworkNodeInfoProper(nodeUuid, rootNetworkUuid, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes, deleteVoltageInitResults);
                }
                invalidateChildrenBuildStatus(nodeUuid, rootNetworkUuid, changedNodes, invalidateNodeInfos, deleteVoltageInitResults);
            }
        });

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()), rootNetworkUuid);
    }

    @Transactional
    // method used when moving a node to invalidate it without impacting other nodes
    public void invalidateBuildOfNodeOnly(UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos, boolean deleteVoltageInitResults) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(nodeEntity -> {
                if (nodeEntity.getType().equals(NodeType.NETWORK_MODIFICATION) && rootNetworkService.exists(rootNetworkUuid)) {
                    rootNetworkNodeInfoService.invalidateRootNetworkNodeInfoProper(nodeUuid, rootNetworkUuid, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes, deleteVoltageInitResults);
                }
            }
        );

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().collect(Collectors.toList()), rootNetworkUuid);
    }

    private void invalidateChildrenBuildStatus(UUID nodeUuid, UUID rootNetworkUuid, List<UUID> changedNodes, InvalidateNodeInfos invalidateNodeInfos,
                                               boolean deleteVoltageInitResults) {
        nodesRepository.findAllByParentNodeIdNode(nodeUuid)
            .forEach(child -> {
                rootNetworkNodeInfoService.invalidateRootNetworkNodeInfoProper(child.getIdNode(), rootNetworkUuid, invalidateNodeInfos, false, changedNodes, deleteVoltageInitResults);
                invalidateChildrenBuildStatus(child.getIdNode(), rootNetworkUuid, changedNodes, invalidateNodeInfos, deleteVoltageInitResults);
            });
    }

    @Transactional
    public void updateNodeBuildStatus(UUID nodeUuid, UUID rootNetworkUuid, NodeBuildStatus nodeBuildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = self.getStudyUuidForNodeId(nodeUuid);
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
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
        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes, rootNetworkUuid);
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
        return self.getNodeBuildStatus(rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)));
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
        } else if (rootNetworkNodeInfoService
            .getRootNetworkNodeInfo(nodeEntity.getIdNode(), rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND))
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

    // TODO Need to deal with all root networks : use one DB request count by root network
    public long countBuiltNodes(UUID studyUuid, UUID rootNetworkUuid) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyIdAndTypeAndStashed(studyUuid, NodeType.NETWORK_MODIFICATION, false);
        // perform N queries, but it's fast: 25 ms for 400 nodes
        return nodes.stream().filter(n -> self.getNodeBuildStatus(n.getIdNode(), rootNetworkUuid).isBuilt()).count();
    }

    public NetworkModificationNodeInfoEntity getNetworkModificationNodeInfoEntity(UUID nodeId) {
        return networkModificationNodeInfoRepository.findById(nodeId).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }
}
