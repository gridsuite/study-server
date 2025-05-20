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
import org.gridsuite.modification.dto.ModificationInfos;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ModificationInfosWithActivationStatus;
import org.gridsuite.study.server.dto.modification.ModificationsSearchResultByGroup;
import org.gridsuite.study.server.dto.modification.ModificationsSearchResultByNode;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
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

    private final NodeRepository nodesRepository;

    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    private final NetworkModificationService networkModificationService;
    private final NotificationService notificationService;

    private final NetworkModificationTreeService self;
    private final RootNodeInfoRepository rootNodeInfoRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final RootNetworkService rootNetworkService;
    private final ReportService reportService;

    public NetworkModificationTreeService(NodeRepository nodesRepository,
                                          RootNodeInfoRepository rootNodeInfoRepository,
                                          NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                                          NotificationService notificationService,
                                          NetworkModificationService networkModificationService,
                                          @Lazy NetworkModificationTreeService networkModificationTreeService,
                                          RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                          RootNetworkService rootNetworkService,
                                          ReportService reportService) {
        this.nodesRepository = nodesRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.networkModificationService = networkModificationService;
        this.notificationService = notificationService;
        this.self = networkModificationTreeService;
        this.rootNodeInfoRepository = rootNodeInfoRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.rootNetworkService = rootNetworkService;
        this.reportService = reportService;
    }

    private NodeEntity createNetworkModificationNode(StudyEntity study, NodeEntity parentNode, NetworkModificationNode networkModificationNode) {
        NodeEntity newNode = nodesRepository.save(new NodeEntity(null, parentNode, NodeType.NETWORK_MODIFICATION, study, false, null));
        if (networkModificationNode.getModificationGroupUuid() == null) {
            networkModificationNode.setModificationGroupUuid(UUID.randomUUID());
        }
        if (networkModificationNode.getNodeType() == null) {
            networkModificationNode.setNodeType(NetworkModificationNodeType.CONSTRUCTION);
        }
        networkModificationNodeInfoRepository.save(
            NetworkModificationNodeInfoEntity.builder()
                .modificationGroupUuid(networkModificationNode.getModificationGroupUuid())
                .idNode(newNode.getIdNode())
                .name(networkModificationNode.getName())
                .description(networkModificationNode.getDescription())
                .nodeType(networkModificationNode.getNodeType())
                .build()
        );
        return newNode;
    }

    private ModificationsSearchResultByNode mapToSearchResultByNode(
            NetworkModificationNodeInfoEntity nodeInfo,
            Map<UUID, ModificationsSearchResultByGroup> modificationsByGroupMap) {

        return Optional.ofNullable(modificationsByGroupMap.get(nodeInfo.getModificationGroupUuid()))
                .map(group -> {
                    BasicNodeInfos basicNodeInfos = BasicNodeInfos.builder()
                            .nodeUuid(nodeInfo.getId())
                            .name(nodeInfo.getName())
                            .build();
                    return new ModificationsSearchResultByNode(basicNodeInfos, group.modifications());
                })
                .orElse(null);
    }

    public List<ModificationsSearchResultByNode> getNetworkModificationsByNodeInfos(List<ModificationsSearchResultByGroup> modificationsByGroup) {

        Map<UUID, ModificationsSearchResultByGroup> modificationsByGroupMap = modificationsByGroup.stream()
                .collect(Collectors.toMap(ModificationsSearchResultByGroup::groupUuid, Function.identity()));

        List<UUID> groupUuids = new ArrayList<>(modificationsByGroupMap.keySet());

        List<NetworkModificationNodeInfoEntity> nodeInfos = networkModificationNodeInfoRepository.findByModificationGroupUuidIn(groupUuids);

        return nodeInfos.stream()
                .map(nodeInfo -> mapToSearchResultByNode(nodeInfo, modificationsByGroupMap))
                .filter(Objects::nonNull)
                .toList();
    }

    // TODO test if studyUuid exist and have a node <nodeId>
    private NetworkModificationNode createAndInsertNode(StudyEntity study, UUID nodeId, NetworkModificationNode nodeInfo, InsertMode insertMode, String userId) {
        NodeEntity reference = getNodeEntity(nodeId);

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
    }

    @Transactional
    public NetworkModificationNode createNode(@NonNull StudyEntity study, @NonNull UUID nodeId, @NonNull NetworkModificationNode nodeInfo, @NonNull InsertMode insertMode, String userId) {
        // create new node
        NetworkModificationNode newNode = createAndInsertNode(study, nodeId, nodeInfo, insertMode, userId);

        NetworkModificationNodeInfoEntity newNodeInfoEntity = networkModificationNodeInfoRepository.getReferenceById(newNode.getId());
        rootNetworkNodeInfoService.createNodeLinks(study, newNodeInfoEntity);

        return newNode;
    }

    private NetworkModificationNode duplicateNode(@NonNull StudyEntity study, @NonNull StudyEntity sourceStudy, @NonNull UUID referenceNodeId, @NonNull NetworkModificationNode newNodeInfo, @NonNull UUID originNodeUuid, @NonNull InsertMode insertMode, Map<UUID, UUID> originToDuplicateModificationUuidMap, boolean isDuplicatingStudy) {
        // create new node
        NetworkModificationNode newNode = createAndInsertNode(study, referenceNodeId, newNodeInfo, insertMode, null);

        NetworkModificationNodeInfoEntity newNodeInfoEntity = networkModificationNodeInfoRepository.getReferenceById(newNode.getId());
        NetworkModificationNodeInfoEntity originNodeInfoEntity = networkModificationNodeInfoRepository.getReferenceById(originNodeUuid);
        if (!isDuplicatingStudy && study.getId() != sourceStudy.getId()) {
            rootNetworkNodeInfoService.createNodeLinks(study, newNodeInfoEntity);
        } else {
            // when duplicating node within the same study, we need to retrieve excluded modifications from source node
            // when duplicating study, we need to retrieve excluded modifications from source node as well, but we also need to have a correspondence between source root networks and duplicated ones
            //     since they are fetched in order, we ensure the duplicate is made accurately
            Map<RootNetworkEntity, RootNetworkEntity> originToDuplicateRootNetworkMap = new HashMap<>();
            for (int i = 0; i < sourceStudy.getRootNetworks().size(); i++) {
                // when study.getId() == sourceStudy.getId(), this mapping makes root networks target themselves, but it makes the code more concise with study duplication
                originToDuplicateRootNetworkMap.put(sourceStudy.getRootNetworks().get(i), study.getRootNetworks().get(i));
            }
            rootNetworkNodeInfoService.duplicateNodeLinks(originNodeInfoEntity.getRootNetworkNodeInfos(), newNodeInfoEntity, originToDuplicateModificationUuidMap, originToDuplicateRootNetworkMap);
        }

        return newNode;
    }

    @Transactional
    public UUID duplicateStudyNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        NodeEntity anchorNode = getNodeEntity(anchorNodeUuid);
        NodeEntity parent = insertMode == InsertMode.BEFORE ? anchorNode.getParentNode() : anchorNode;
        UUID newNodeUUID = duplicateNode(nodeToCopyUuid, anchorNodeUuid, insertMode);
        notificationService.emitNodeInserted(anchorNode.getStudy().getId(), parent.getIdNode(), newNodeUUID, insertMode, anchorNodeUuid);
        return newNodeUUID;
    }

    private UUID duplicateNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        NodeEntity anchorNodeEntity = getNodeEntity(anchorNodeUuid);
        if (insertMode.equals(InsertMode.BEFORE) && anchorNodeEntity.getType().equals(NodeType.ROOT)) {
            throw new StudyException(NOT_ALLOWED);
        }

        UUID newGroupUuid = UUID.randomUUID();
        UUID modificationGroupUuid = self.getModificationGroupUuid(nodeToCopyUuid);
        //First we create the modification group
        Map<UUID, UUID> originToDuplicateModificationUuidMap = networkModificationService.duplicateModificationsGroup(modificationGroupUuid, newGroupUuid);

        //Then we create the node
        NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity = getNetworkModificationNodeInfoEntity(nodeToCopyUuid);
        UUID studyUuid = anchorNodeEntity.getStudy().getId();

        NetworkModificationNode node = duplicateNode(
            anchorNodeEntity.getStudy(),
            networkModificationNodeInfoEntity.getNode().getStudy(),
            anchorNodeUuid,
            NetworkModificationNode.builder()
                .modificationGroupUuid(newGroupUuid)
                .name(getSuffixedNodeName(studyUuid, networkModificationNodeInfoEntity.getName()))
                .description(networkModificationNodeInfoEntity.getDescription())
                .nodeType(networkModificationNodeInfoEntity.getNodeType())
                .build(),
                nodeToCopyUuid,
                insertMode,
            originToDuplicateModificationUuidMap,
            false
        );

        return node.getId();
    }

    @Transactional
    public void moveStudyNode(UUID nodeToMoveUuid, UUID anchorNodeUuid, InsertMode insertMode) {        //if we try to move a node around itself, nothing happens
        if (nodeToMoveUuid.equals(anchorNodeUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        NodeEntity anchorNode = getNodeEntity(anchorNodeUuid);
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
        NodeEntity nodeToMoveEntity = getNodeEntity(nodeToMoveUuid);

        nodesRepository.findAllByParentNodeIdNode(nodeToMoveUuid)
            .forEach(child -> child.setParentNode(nodeToMoveEntity.getParentNode()));

        NodeEntity anchorNodeEntity = getNodeEntity(anchorNodeUuid);

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
        return getNodeEntity(id).getStudy().getId();
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
            if (nodeToDelete.getType() == NodeType.ROOT) {
                rootNodeInfoRepository.deleteById(id);
            } else {
                networkModificationNodeInfoRepository.deleteById(id);
            }
            nodesRepository.delete(nodeToDelete);
        });
    }

    public List<NodeEntity> getChildrenByParentUuid(UUID parentUuid) {
        return nodesRepository.findAllByParentNodeIdNode(parentUuid);
    }

    public List<String> getAllChildrenFromParentUuid(UUID parentUuid) {
        return nodesRepository.findAllDescendants(parentUuid);
    }

    @Transactional
    public void doDeleteTree(UUID studyId) {
        try {
            List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyId);
            Map<NodeType, List<NodeEntity>> nodeUuidsByType = nodes.stream().collect(Collectors.groupingBy(NodeEntity::getType));
            // remove root node infos entities
            List<NodeEntity> rootNodesToDelete = nodeUuidsByType.get(NodeType.ROOT);
            if (rootNodesToDelete != null) {
                rootNodeInfoRepository.deleteByIdNodeIn(rootNodesToDelete.stream().map(NodeEntity::getIdNode).collect(Collectors.toList()));
            }
            // remove network modification node infos entities
            List<NodeEntity> networkModificationNodesToDelete = nodeUuidsByType.get(NodeType.NETWORK_MODIFICATION);
            if (networkModificationNodesToDelete != null) {
                networkModificationNodeInfoRepository.deleteByIdNodeIn(networkModificationNodesToDelete.stream().map(NodeEntity::getIdNode).toList());
            }
            // remove node entities
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
        allNodeInfos.addAll(rootNodeInfoRepository.findAllByNodeStudyId(studyId).stream().map(RootNodeInfoEntity::toDto).toList());
        allNodeInfos.addAll(networkModificationNodeInfoRepository.findAllByNodeStudyId(studyId).stream().map(NetworkModificationNodeInfoEntity::toDto).toList());
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
    public void duplicateStudyNodes(StudyEntity studyEntity, StudyEntity sourceStudyEntity) {
        createRoot(studyEntity);
        AbstractNode rootNode = getStudyTree(sourceStudyEntity.getId(), null);
        cloneStudyTree(rootNode, null, studyEntity, sourceStudyEntity, true);
    }

    @Transactional
    public UUID cloneStudyTree(AbstractNode nodeToDuplicate, UUID nodeParentId, StudyEntity studyEntity, StudyEntity sourceStudyEntity, boolean isDuplicatingStudy) {
        UUID rootId = null;
        if (NodeType.ROOT.equals(nodeToDuplicate.getType())) {
            rootId = getStudyRootNodeUuid(studyEntity.getId());
        }
        UUID nextParentId;
        UUID newModificationGroupId = UUID.randomUUID();

        if (nodeToDuplicate instanceof NetworkModificationNode model) {
            UUID modificationGroupToDuplicateId = model.getModificationGroupUuid();
            model.setModificationGroupUuid(newModificationGroupId);
            model.setName(getSuffixedNodeName(studyEntity.getId(), model.getName()));

            Map<UUID, UUID> originToDuplicateModificationUuidMap = networkModificationService.duplicateModificationsGroup(modificationGroupToDuplicateId, newModificationGroupId);
            nextParentId = duplicateNode(studyEntity, sourceStudyEntity, nodeParentId, model, nodeToDuplicate.getId(), InsertMode.CHILD, originToDuplicateModificationUuidMap, isDuplicatingStudy).getId();
        } else {
            // when cloning studyTree, we don't clone root node
            // if cloning the whole study, the root node is previously created
            nextParentId = rootId;
        }
        nodeToDuplicate.getChildren().forEach(childToDuplicate -> self.cloneStudyTree(childToDuplicate, nextParentId, studyEntity, sourceStudyEntity, isDuplicatingStudy));

        return nextParentId;
    }

    @Transactional
    public void createBasicTree(StudyEntity studyEntity) {
        // create 2 nodes : root node, modification node N1
        NodeEntity rootNodeEntity = self.createRoot(studyEntity);
        UUID firstRootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        NetworkModificationNode modificationNode = NetworkModificationNode
            .builder()
            .nodeType(NetworkModificationNodeType.CONSTRUCTION)
            .name("N1")
            .build();

        NetworkModificationNode networkModificationNode = createNode(studyEntity, rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER, null);
        ReportNode reportNode = ReportNode.newRootReportNode()
                    .withAllResourceBundlesFromClasspath()
                    .withMessageTemplate("study.server.modificationNodeId")
                    .withUntypedValue("modificationNodeId", modificationNode.getId().toString()).build();
        reportService.sendReport(getModificationReportUuid(networkModificationNode.getId(), firstRootNetworkUuid, networkModificationNode.getId()), reportNode);

        BuildInfos buildInfos = getBuildInfos(modificationNode.getId(), firstRootNetworkUuid);
        Map<UUID, UUID> nodeUuidToReportUuid = buildInfos.getReportsInfos().stream().collect(Collectors.toMap(ReportInfos::nodeUuid, ReportInfos::reportUuid));
        rootNetworkNodeInfoService.updateRootNetworkNode(networkModificationNode.getId(), firstRootNetworkUuid,
            RootNetworkNodeInfo.builder().variantId(FIRST_VARIANT_ID).nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT)).modificationReports(nodeUuidToReportUuid).build());
    }

    @Transactional
    public void updateNode(UUID studyUuid, NetworkModificationNode node, String userId) {
        NetworkModificationNodeInfoEntity networkModificationNodeEntity = getNetworkModificationNodeInfoEntity(node.getId());
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
        return nodesRepository.findById(nodeId).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    public RootNodeInfoEntity getRootNodeInfoEntity(UUID nodeId) {
        return rootNodeInfoRepository.findById(nodeId).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    public NetworkModificationNodeInfoEntity getNetworkModificationNodeInfoEntity(UUID nodeId) {
        return networkModificationNodeInfoRepository.findById(nodeId).orElseThrow(() -> new StudyException(NODE_NOT_FOUND));
    }

    private AbstractNodeInfoEntity getNodeInfoEntity(UUID nodeUuid) {
        return getNodeEntity(nodeUuid).getType() == NodeType.ROOT ? getRootNodeInfoEntity(nodeUuid) : getNetworkModificationNodeInfoEntity(nodeUuid);
    }

    private AbstractNode getSimpleNode(UUID nodeId) {
        return getNodeInfoEntity(nodeId).toDto();
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
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        // we will use the network initial variant if node is of type ROOT
        if (nodeEntity.getType().equals(NodeType.ROOT)) {
            return "";
        }

        return rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)).getVariantId();
    }

    @Transactional(readOnly = true)
    public UUID getModificationGroupUuid(UUID nodeUuid) {
        return getNetworkModificationNodeInfoEntity(nodeUuid).getModificationGroupUuid();
    }

    @Transactional(readOnly = true)
    public List<ModificationInfosWithActivationStatus> getNetworkModifications(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, boolean onlyStashed, boolean onlyMetadata) {
        List<ModificationInfos> modificationInfos = networkModificationService.getModifications(self.getModificationGroupUuid(nodeUuid), onlyStashed, onlyMetadata);
        if (!self.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }

        List<RootNetworkNodeInfoEntity> rootNetworkByNodeInfos = rootNetworkNodeInfoService.getAllWithRootNetworkByNodeInfoId(nodeUuid);
        return modificationInfos.stream()
                .map(modification ->
                        (ModificationInfosWithActivationStatus) ModificationInfosWithActivationStatus.builder()
                                .activationStatusByRootNetwork(getActivationStatusByRootNetwork(rootNetworkByNodeInfos, modification.getUuid()))
                                .modificationInfos(modification)
                                .build())
                .toList();
    }

    /**
     * Get modification activation status by root network
     */
    private Map<UUID, Boolean> getActivationStatusByRootNetwork(List<RootNetworkNodeInfoEntity> rootNetworkByNodeInfos, UUID modificationUuid) {
        return rootNetworkByNodeInfos.stream().collect(Collectors.toMap(
                r -> r.getRootNetwork().getId(),
                r -> !r.getModificationsUuidsToExclude().contains(modificationUuid)));
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
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
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
        // get ordered list of stashed NodeEntity
        List<NodeEntity> nodes = nodesRepository.findAllByStudyIdAndStashedAndParentNodeIdNodeOrderByStashDateDesc(studyUuid, true, null);
        // get all their NetworkModificationInfos - order is not guaranteed when using findAllById, we save them in a map to use them in the next operation
        Map<UUID, NetworkModificationNode> networkModificationNodeInfos = networkModificationNodeInfoRepository.findAllById(nodes.stream().map(NodeEntity::getIdNode).toList())
            .stream().map(NetworkModificationNodeInfoEntity::toDto)
            .collect(Collectors.toMap(NetworkModificationNode::getId, Function.identity()));

        List<Pair<AbstractNode, Integer>> result = new ArrayList<>();
        // use ordered list with map to compute result
        nodes.stream().map(node -> networkModificationNodeInfos.get(node.getIdNode()))
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
            NodeEntity nodeToRestore = getNodeEntity(nodeId);
            NodeEntity anchorNode = getNodeEntity(anchorNodeId);
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
        AbstractNode node = getSimpleNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeEntity.getIdNode(), rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
            if (!rootNetworkNodeInfoEntity.getNodeBuildStatus().toDto().isBuilt()) {
                UUID reportUuid = getModificationReportUuid(nodeEntity.getIdNode(), rootNetworkUuid, nodeToBuildUuid);
                buildInfos.insertModificationInfos(modificationNode.getModificationGroupUuid(), rootNetworkNodeInfoEntity.getModificationsUuidsToExclude(), new ReportInfos(reportUuid, modificationNode.getId()));
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
            fillIndexedNodeInfosToInvalidate(invalidateNodeInfos, nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus);
            if (rootNetworkService.exists(rootNetworkUuid)) {
                if (nodeEntity.getType().equals(NodeType.NETWORK_MODIFICATION)) {
                    rootNetworkNodeInfoService.invalidateRootNetworkNodeInfoProper(nodeUuid, rootNetworkUuid, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, changedNodes, deleteVoltageInitResults);
                }
                invalidateChildrenBuildStatus(nodeUuid, rootNetworkUuid, changedNodes, invalidateNodeInfos, deleteVoltageInitResults);
            }
        });

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().toList(), rootNetworkUuid);
    }

    private void fillIndexedNodeInfosToInvalidate(InvalidateNodeInfos invalidateNodeInfos, UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus) {
        // when invalidating node
        // we need to invalidate indexed modifications up to it's last built parent, not included
        boolean isNodeBuilt = self.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt();
        if (!isNodeBuilt && !hasAnyBuiltChildren(getNodeEntity(nodeUuid), rootNetworkUuid)) {
            return;
        }

        if (isNodeBuilt && invalidateOnlyChildrenBuildStatus) {
            fillIndexedNodeInfosToInvalidate(nodeUuid, false, invalidateNodeInfos);
        } else {
            NodeEntity closestNodeWithParentHavingBuiltDescendent = getSubTreeToInvalidateIndexedModifications(nodeUuid, rootNetworkUuid);
            fillIndexedNodeInfosToInvalidate(closestNodeWithParentHavingBuiltDescendent.getIdNode(), true, invalidateNodeInfos);
        }
    }

    @Transactional
    public InvalidateNodeInfos invalidateNode(UUID nodeUuid, UUID rootNetworkUuid) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);

        InvalidateNodeInfos invalidateNodeInfos = rootNetworkNodeInfoService.invalidateRootNetworkNode(nodeUuid, rootNetworkUuid, true);

        fillIndexedNodeInfosToInvalidate(nodeEntity, rootNetworkUuid, invalidateNodeInfos);

        notificationService.emitNodeBuildStatusUpdated(nodeEntity.getStudy().getId(), List.of(nodeUuid), rootNetworkUuid);

        return invalidateNodeInfos;
    }

    @Transactional
    public InvalidateNodeInfos invalidateNodeTree(UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);

        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();

        // First node
        if (nodeEntity.getType().equals(NodeType.NETWORK_MODIFICATION)) {
            invalidateNodeInfos = rootNetworkNodeInfoService.invalidateRootNetworkNode(nodeUuid, rootNetworkUuid, !invalidateOnlyChildrenBuildStatus);
            fillIndexedNodeTreeInfosToInvalidate(nodeEntity, rootNetworkUuid, invalidateNodeInfos);
        }

        invalidateNodeInfos.add(invalidateChildrenNodes(nodeUuid, rootNetworkUuid));

        if (!invalidateNodeInfos.getNodeUuids().isEmpty()) {
            notificationService.emitNodeBuildStatusUpdated(nodeEntity.getStudy().getId(), invalidateNodeInfos.getNodeUuids().stream().toList(), rootNetworkUuid);
        }

        return invalidateNodeInfos;
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

        // when manually invalidating a single node, if this node does not have any built children
        // we need to invalidate indexed modifications up to it's last built parent, not included
        if (!hasAnyBuiltChildren(getNodeEntity(nodeUuid), rootNetworkUuid)) {
            // when invalidating nodes, we need to get last built parent to invalidate all its children modifications in elasticsearch
            NodeEntity closestNodeWithParentHavingBuiltDescendent = getSubTreeToInvalidateIndexedModifications(nodeUuid, rootNetworkUuid);
            fillIndexedNodeInfosToInvalidate(closestNodeWithParentHavingBuiltDescendent.getIdNode(), true, invalidateNodeInfos);
        }

        notificationService.emitNodeBuildStatusUpdated(studyId, changedNodes.stream().distinct().toList(), rootNetworkUuid);
    }

    /**
     * Recursively iterate through *nodeUuid* parents until one of them match one of the following conditions :<br>
     * - it is of type ROOT<br>
     * - it is built<br>
     * - one of its children is built
     * @param nodeUuid reference node from where the recursion will start
     * @param rootNetworkUuid root network necessary to get the build status of each node
     * @return the NodeEntity having its parent matching one of the above criteria
     */
    private NodeEntity getSubTreeToInvalidateIndexedModifications(UUID nodeUuid, UUID rootNetworkUuid) {
        Set<NodeEntity> descendantsChecked = new HashSet<>();

        NodeEntity currentNode = getNodeEntity(nodeUuid);

        while (currentNode.getParentNode() != null) {
            NodeEntity parentNode = currentNode.getParentNode();
            if (parentNode.getType().equals(NodeType.ROOT)
                || self.getNodeBuildStatus(parentNode.getIdNode(), rootNetworkUuid).isBuilt()
                || hasAnyBuiltChildren(parentNode, rootNetworkUuid, descendantsChecked)) {
                return currentNode;
            }

            currentNode = parentNode;
        }

        return currentNode;
    }

    // TODO Need to optimise with a only one recursive query
    private boolean hasAnyBuiltChildren(NodeEntity node, UUID rootNetworkUuid) {
        return hasAnyBuiltChildren(node, rootNetworkUuid, new HashSet<>());
    }

    private boolean hasAnyBuiltChildren(NodeEntity node, UUID rootNetworkUuid, Set<NodeEntity> checkedChildren) {
        if (self.getNodeBuildStatus(node.getIdNode(), rootNetworkUuid).isBuilt()) {
            return true;
        }
        checkedChildren.add(node);

        for (NodeEntity child : getChildrenByParentUuid(node.getIdNode())) {
            if (!checkedChildren.contains(child)
                && hasAnyBuiltChildren(child, rootNetworkUuid, checkedChildren)) {
                return true;
            }
        }

        return false;
    }

    private void fillIndexedNodeInfosToInvalidate(NodeEntity nodeEntity, UUID rootNetworkUuid, InvalidateNodeInfos invalidateNodeInfos) {
        // when manually invalidating a single node, if this node does not have any built children
        // we need to invalidate indexed modifications up to it's last built parent, not included
        if (hasAnyBuiltChildren(nodeEntity, rootNetworkUuid)) {
            return;
        }

        // when invalidating nodes, we need to get last built parent to invalidate all its children modifications in elasticsearch
        NodeEntity closestNodeWithParentHavingBuiltDescendent = getSubTreeToInvalidateIndexedModifications(nodeEntity.getIdNode(), rootNetworkUuid);
        fillIndexedNodeInfosToInvalidate(closestNodeWithParentHavingBuiltDescendent.getIdNode(), true, invalidateNodeInfos);
    }

    // For subTree
    private void fillIndexedNodeTreeInfosToInvalidate(NodeEntity nodeEntity, UUID rootNetworkUuid, InvalidateNodeInfos invalidateNodeInfos) {
        // when invalidating node
        // we need to invalidate indexed modifications up to it's last built parent, not included
        boolean isNodeBuilt = self.getNodeBuildStatus(nodeEntity.getIdNode(), rootNetworkUuid).isBuilt();
        if (!isNodeBuilt && !hasAnyBuiltChildren(getNodeEntity(nodeEntity.getIdNode()), rootNetworkUuid)) {
            return;
        }

        // TODO check invalidateOnlyChildrenBuildStatus
        if (isNodeBuilt) {
            fillIndexedNodeInfosToInvalidate(nodeEntity.getIdNode(), false, invalidateNodeInfos);
        } else {
            NodeEntity closestNodeWithParentHavingBuiltDescendent = getSubTreeToInvalidateIndexedModifications(nodeEntity.getIdNode(), rootNetworkUuid);
            fillIndexedNodeInfosToInvalidate(closestNodeWithParentHavingBuiltDescendent.getIdNode(), true, invalidateNodeInfos);
        }
    }

    private InvalidateNodeInfos invalidateChildrenNodes(UUID nodeUuid, UUID rootNetworkUuid) {
        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        nodesRepository.findAllByParentNodeIdNode(nodeUuid)
            .forEach(child -> {
                invalidateNodeInfos.add(rootNetworkNodeInfoService.invalidateRootNetworkNode(child.getIdNode(), rootNetworkUuid, true));
                invalidateNodeInfos.add(invalidateChildrenNodes(child.getIdNode(), rootNetworkUuid));
            });
        return invalidateNodeInfos;
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
        notificationService.emitNodeBuildStatusUpdated(studyId, List.of(nodeUuid), rootNetworkUuid);
    }

    @Transactional(readOnly = true)
    public NodeBuildStatus getNodeBuildStatus(UUID nodeUuid, UUID rootNetworkUuid) {
        NodeEntity nodeEntity = getNodeEntity(nodeUuid);
        if (nodeEntity.getType().equals(NodeType.ROOT)) {
            return NodeBuildStatus.from(BuildStatus.NOT_BUILT);
        }

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
        return rootNetworkNodeInfoEntity.getNodeBuildStatus().toDto();
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
    public Boolean isReadOnly(UUID nodeUuid) {
        return getNodeInfoEntity(nodeUuid).getReadOnly();
    }

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

    // TODO Need to deal with all root networks : use one DB request count by root network
    public long countBuiltNodes(UUID studyUuid, UUID rootNetworkUuid) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyIdAndTypeAndStashed(studyUuid, NodeType.NETWORK_MODIFICATION, false);
        // perform N queries, but it's fast: 25 ms for 400 nodes
        return nodes.stream().filter(n -> self.getNodeBuildStatus(n.getIdNode(), rootNetworkUuid).isBuilt()).count();
    }

    private void fillIndexedNodeInfosToInvalidate(UUID parentNodeUuid, boolean includeParentNode, InvalidateNodeInfos invalidateNodeInfos) {
        List<UUID> nodesToInvalidate = new ArrayList<>();
        if (includeParentNode) {
            nodesToInvalidate.add(parentNodeUuid);
        }
        nodesToInvalidate.addAll(getChildren(parentNodeUuid));
        invalidateNodeInfos.addGroupUuids(
            networkModificationNodeInfoRepository.findAllById(nodesToInvalidate).stream()
                .map(NetworkModificationNodeInfoEntity::getModificationGroupUuid).toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<UUID, AbstractNode> getAllStudyNodesByUuid(UUID studyId) {
        List<AbstractNode> allNodeInfos = new ArrayList<>();
        allNodeInfos.addAll(rootNodeInfoRepository.findAllByNodeStudyId(studyId).stream().map(RootNodeInfoEntity::toDto).toList());
        allNodeInfos.addAll(networkModificationNodeInfoRepository.findAllByNodeStudyId(studyId).stream().map(NetworkModificationNodeInfoEntity::toDto).toList());
        return allNodeInfos.stream().collect(Collectors.toMap(AbstractNode::getId, node -> node));
    }
}
