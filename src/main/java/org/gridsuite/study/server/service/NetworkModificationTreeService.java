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
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.networkmodificationtree.AbstractNodeRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.NetworkModificationNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.RootNodeInfoRepositoryProxy;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.networkmodificationtree.repositories.NodeRepository;
import org.gridsuite.study.server.networkmodificationtree.repositories.RootNodeInfoRepository;
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
            notificationService.emitNodeInserted(getStudyUuidForNodeId(nodeId), parent.getIdNode(), node.getIdNode(), insertMode);
            return nodeInfo;
        }).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    @Transactional
    public UUID duplicateStudyNode(UUID nodeToCopyUuid, UUID anchorNodeUuid, InsertMode insertMode) {
        Optional<NodeEntity> anchorNodeOpt = nodesRepository.findById(anchorNodeUuid);
        NodeEntity anchorNodeEntity = anchorNodeOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));

        Optional<NodeEntity> nodeToCopyOpt = nodesRepository.findById(nodeToCopyUuid);
        NodeEntity nodeToCopyEntity = nodeToCopyOpt.orElseThrow(() -> new StudyException(NODE_NOT_FOUND));

        UUID newGroupUuid = UUID.randomUUID();
        UUID modificationGroupUuid = getModificationGroupUuid(nodeToCopyUuid);
        UUID newReportUuid = UUID.randomUUID();
        //First we create the modification group
        networkModificationService.createModifications(modificationGroupUuid, newGroupUuid, newReportUuid);

        if (insertMode.equals(InsertMode.BEFORE) && anchorNodeEntity.getType().equals(NodeType.ROOT)) {
            throw new StudyException(NOT_ALLOWED);
        }
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
                BuildStatus.NOT_BUILT
        );
        UUID studyUuid = anchorNodeEntity.getStudy().getId();
        newNetworkModificationNodeInfoEntity.setName(getSuffixedNodeName(studyUuid, networkModificationNodeInfoEntity.getName()));
        newNetworkModificationNodeInfoEntity.setDescription(networkModificationNodeInfoEntity.getDescription());
        newNetworkModificationNodeInfoEntity.setIdNode(node.getIdNode());
        newNetworkModificationNodeInfoEntity.setReportUuid(newReportUuid);
        networkModificationNodeInfoRepository.save(newNetworkModificationNodeInfoEntity);

        notificationService.emitNodeInserted(studyUuid, parent.getIdNode(), node.getIdNode(), insertMode);
        return node.getIdNode();
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
                model.setSensitivityAnalysisResultUuid(null);

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
        notificationService.emitNodesChanged(getStudyUuidForNodeId(node.getId()), Collections.singletonList(node.getId()));
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

    public String getSuffixedNodeName(UUID studyUuid, String nodeName) {
        List<String> studyNodeNames = networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid)
                .stream()
                .map(AbstractNodeInfoEntity::getName)
                .collect(Collectors.toList());

        String uniqueName = StringUtils.EMPTY;
        int i = 1;
        while (StringUtils.EMPTY.equals(uniqueName) || studyNodeNames.contains(uniqueName)) {
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
    public void updateStudyLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> updateLoadFlowStatus(n.getIdNode(), loadFlowStatus));
    }

    @Transactional
    public void updateStudyShortCircuitStatus(UUID studyUuid, ShortCircuitStatus shortCircuitStatus) {
        List<NodeEntity> nodes = nodesRepository.findAllByStudyId(studyUuid);
        nodes.forEach(n -> updateStudyShortCircuitStatus(n.getIdNode(), shortCircuitStatus));
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
    public LoadFlowInfos getLoadFlowInfos(UUID nodeUuid) {
        return nodesRepository.findById(nodeUuid).map(n -> repositories.get(n.getType()).getLoadFlowInfos(nodeUuid)).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    private void getBuildInfos(NodeEntity nodeEntity, BuildInfos buildInfos) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode modificationNode = (NetworkModificationNode) node;
            if (modificationNode.getBuildStatus() != BuildStatus.BUILT) {
                buildInfos.insertModificationGroupAndReport(modificationNode.getModificationGroupUuid(), getReportUuid(nodeEntity.getIdNode()));
            }
            if (modificationNode.getModificationsToExclude() != null) {
                buildInfos.addModificationsToExclude(modificationNode.getModificationsToExclude());
            }
            if (modificationNode.getBuildStatus() != BuildStatus.BUILT) {
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
                getBuildInfos(entity, buildInfos);
            }
        }, () -> {
                throw new StudyException(ELEMENT_NOT_FOUND);
            });

        return buildInfos;
    }

    private void fillInvalidateNodeInfos(NodeEntity node, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus) {

        if (!invalidateOnlyChildrenBuildStatus) {
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
    }

    @Transactional
    public void invalidateBuild(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos) {
        final List<UUID> changedNodes = new ArrayList<>();
        changedNodes.add(nodeUuid);
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> {
            // No need to invalidate a node with a status different of "BUILT"
            if (repositories.get(n.getType()).getBuildStatus(n.getIdNode()) == BuildStatus.BUILT) {
                fillInvalidateNodeInfos(n, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus);
                if (!invalidateOnlyChildrenBuildStatus) {
                    repositories.get(n.getType()).invalidateBuildStatus(nodeUuid, changedNodes);
                }
                repositories.get(n.getType()).updateLoadFlowResultAndStatus(nodeUuid, null, LoadFlowStatus.NOT_DONE);
            }
            invalidateChildrenBuildStatus(n, changedNodes, false, invalidateNodeInfos);
        });

        notificationService.emitNodesChanged(studyId, changedNodes.stream().distinct().collect(Collectors.toList()));
    }

    private void invalidateChildrenBuildStatus(NodeEntity nodeEntity, List<UUID> changedNodes, boolean invalidateOnlyChildrenBuildStatus, InvalidateNodeInfos invalidateNodeInfos) {
        nodesRepository.findAllByParentNodeIdNode(nodeEntity.getIdNode())
            .forEach(child -> {
                // No need to invalidate a node with a status different of "BUILT"
                if (repositories.get(child.getType()).getBuildStatus(child.getIdNode()) == BuildStatus.BUILT) {
                    fillInvalidateNodeInfos(child, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus);
                    if (!invalidateOnlyChildrenBuildStatus) {
                        repositories.get(child.getType()).invalidateBuildStatus(child.getIdNode(), changedNodes);
                    }
                    repositories.get(child.getType()).updateLoadFlowResultAndStatus(child.getIdNode(), null, LoadFlowStatus.NOT_DONE);
                }
                invalidateChildrenBuildStatus(child, changedNodes, false, invalidateNodeInfos);
            });
    }

    @Transactional
    public void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        List<UUID> changedNodes = new ArrayList<>();
        UUID studyId = getStudyUuidForNodeId(nodeUuid);

        nodesRepository.findById(nodeUuid).ifPresent(n -> repositories.get(n.getType()).updateBuildStatus(nodeUuid, buildStatus, changedNodes));

        notificationService.emitNodesChanged(studyId, changedNodes);
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
        notificationService.emitNodesChanged(studyUuid, List.of(nodeUuid));
    }

    private void getParentReportUuidsAndNamesFromNode(NodeEntity nodeEntity, boolean nodeOnlyReport, List<Pair<UUID, String>> res) {
        AbstractNode node = repositories.get(nodeEntity.getType()).getNode(nodeEntity.getIdNode());
        res.add(0, Pair.of(getReportUuid(nodeEntity.getIdNode()), node.getName()));
        if (node.getType() == NodeType.NETWORK_MODIFICATION && !nodeOnlyReport) {
            getParentReportUuidsAndNamesFromNode(nodeEntity.getParentNode(), false, res);
        }
    }

    @Transactional
    public List<Pair<UUID, String>> getParentReportUuidsAndNamesFromNode(UUID nodeUuid, boolean nodeOnlyReport) {
        List<Pair<UUID, String>> uuidsAndNames = new ArrayList<>();
        nodesRepository.findById(nodeUuid).ifPresentOrElse(entity -> getParentReportUuidsAndNamesFromNode(entity, nodeOnlyReport, uuidsAndNames), () -> {
            throw new StudyException(ELEMENT_NOT_FOUND);
        });
        return uuidsAndNames;
    }

    @Transactional
    public List<Pair<UUID, String>> getReportUuidsAndNames(UUID nodeUuid, boolean nodeOnlyReport) {
        List<Pair<UUID, String>> uuidsAndNames = getParentReportUuidsAndNamesFromNode(nodeUuid, nodeOnlyReport);
        if (uuidsAndNames == null) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }
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
