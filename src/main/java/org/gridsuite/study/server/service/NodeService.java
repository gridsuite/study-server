/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.modification.CompositeInfos;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.gridsuite.study.server.service.StudyService.STUDY_NOT_FOUND;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class NodeService {
    private final NetworkModificationTreeService networkModificationTreeService;
    private final StudyRepository studyRepository;
    private final NodeRepository nodeRepository;
    private final NetworkModificationRestService networkModificationRestService;
    private final DirectoryService directoryService;
    private final NotificationService notificationService;
    private final RootNetworkService rootNetworkService;
    private final StudyServerExecutionService studyServerExecutionService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final CaseService caseService;
    private final NetworkConversionService networkConversionService;

    public NodeService(NetworkModificationTreeService networkModificationTreeService,
                       StudyRepository studyRepository,
                       NodeRepository nodeRepository,
                       NetworkModificationRestService networkModificationRestService,
                       DirectoryService directoryService,
                       NotificationService notificationService,
                       RootNetworkService rootNetworkService,
                       StudyServerExecutionService studyServerExecutionService,
                       RootNetworkNodeInfoService rootNetworkNodeInfoService,
                       CaseService caseService,
                       NetworkConversionService networkConversionService) {
        this.networkModificationTreeService = networkModificationTreeService;
        this.studyRepository = studyRepository;
        this.nodeRepository = nodeRepository;
        this.networkModificationRestService = networkModificationRestService;
        this.directoryService = directoryService;
        this.notificationService = notificationService;
        this.rootNetworkService = rootNetworkService;
        this.studyServerExecutionService = studyServerExecutionService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.caseService = caseService;
        this.networkConversionService = networkConversionService;
    }

    @Transactional(readOnly = true)
    public void assertIsNodeNotReadOnly(UUID nodeUuid) {
        Boolean isReadOnly = networkModificationTreeService.isReadOnly(nodeUuid);
        if (Boolean.TRUE.equals(isReadOnly)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    private void assertIsStudyExist(UUID studyUuid) {
        if (!studyRepository.existsById(studyUuid)) {
            throw new StudyException(NOT_FOUND, "Study not found");
        }
    }

    public void assertIsStudyAndNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        assertIsNodeExist(studyUuid, nodeUuid);
    }

    public void assertIsNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsNodeExist(studyUuid, nodeUuid, new StudyException(NOT_FOUND, "Node not found"));
    }

    public void assertIsNodeExist(UUID studyUuid, UUID nodeUuid, StudyException exception) {
        if (!nodeRepository.existsByStudyIdAndNodeId(studyUuid, nodeUuid)) {
            throw exception;
        }
    }

    private void assertDuplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode) {
        networkModificationTreeService.assertStudyContainsNode(sourceStudyUuid, nodeToCopyUuid);
        networkModificationTreeService.assertStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
    }

    /**
     * Modifications written into a study must be associated with all its root network tags, including the shared
     * modifications they point to: the ones the user cannot write on, which they cannot associate with a new tag, must
     * already be.
     *
     * @param containerUuids groups or composites whose content is written into the study: only the shared modifications
     *                       their content refers to are checked, not the containers themselves
     * @param modificationUuids modifications written into the study: the shared modifications they and their content
     *                          refer to are checked
     * @param sharedUuids shared modifications the study is given a reference to: they are checked themselves, along
     *                    with the shared modifications their content refers to
     */
    private void assertCanWriteModificationsInStudy(UUID studyUuid, List<UUID> containerUuids, List<UUID> modificationUuids,
                                                    List<UUID> sharedUuids, String userId) {
        networkModificationRestService.assertRootNetworkTagsAllowed(containerUuids, modificationUuids, sharedUuids,
                rootNetworkService.getStudyRootNetworkTags(studyUuid), userId);
    }

    /**
     * A root network tag of a study must be associated with all its shared modifications: the ones the user cannot
     * write on, which they cannot associate with a new tag, must already be.
     */
    private void assertCanAddRootNetworkTag(UUID studyUuid, String newTag, String userId) {
        networkModificationRestService.assertRootNetworkTagsAllowed(getStudyModificationGroupUuids(studyUuid), List.of(), List.of(), List.of(newTag), userId);
    }

    private void assertDuplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid) {
        networkModificationTreeService.assertStudyContainsNode(sourceStudyUuid, parentNodeToCopyUuid);
        networkModificationTreeService.assertStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateSubtree(parentNodeToCopyUuid, referenceNodeUuid);
    }

    /**
     * Only an INSERT of a composite marked as shared gives the study a reference to it: the shared composite is checked
     * itself. Any other composite is copied: only its content is checked, the composite being passed as its container.
     */
    private void assertCanWriteCompositesInStudy(UUID studyUuid, List<CompositeInfos> compositesInfos, boolean isInsert, String userId) {
        Map<Boolean, List<UUID>> uuidsByShared = compositesInfos.stream()
                .collect(Collectors.partitioningBy(composite -> isInsert && composite.isShared(),
                        Collectors.mapping(CompositeInfos::id, Collectors.toList())));
        List<UUID> sharedCompositeUuids = uuidsByShared.get(true);
        List<UUID> copiedCompositeUuids = uuidsByShared.get(false);
        assertCanWriteModificationsInStudy(studyUuid, copiedCompositeUuids, List.of(), sharedCompositeUuids, userId);
    }

    private void createCompositesReferences(List<CompositeInfos> compositesInfos, List<UUID> insertedModificationUuids,
                                            UUID studyUuid, UUID nodeUuid, String userId) {
        for (int i = 0; i < compositesInfos.size(); i++) {
            CompositeInfos composite = compositesInfos.get(i);
            if (!composite.isShared()) {
                continue;
            }
            directoryService.createSharingLink(composite.id(), ReferenceAttributes.createReferenceAttributes(
                    insertedModificationUuids.get(i), studyUuid, nodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE), false, userId);
        }
    }

    StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, STUDY_NOT_FOUND));
    }

    /**
     * Moves a composite modification of a node out of the study : it is stored as an element in the directory server,
     * and replaced in the node by a reference to this now shared composite modification.
     */
    @Transactional(readOnly = true)
    public void shareCompositeNetworkModification(UUID studyUuid, UUID nodeUuid, UUID modificationUuid, String name,
                                                  String description, UUID parentDirectoryUuid, String userId) {
        UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            // the applied modifications are left unchanged : the node does not need to be rebuilt
            ModificationReference newReference = networkModificationRestService.extractCompositeModificationToShare(groupUuid, modificationUuid, name, description);
            // the composite modification keeps its uuid when extracted, so it is shared under that same uuid
            // description is null as it is provided by the referenced modification
            directoryService.createElement(parentDirectoryUuid, null, modificationUuid, name, DirectoryService.MODIFICATION, userId, true);
            // extraction replaced the local composite by a new reference modification, in the node group or in a parent
            // composite: register it on the shared element
            directoryService.createSharingLinks(List.of(newReference), studyUuid, nodeUuid, userId);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public String getExportedNetworkModifications(UUID studyUuid, UUID nodeUuid) {
        if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);

        return networkModificationRestService.getModificationsToExport(groupId);
    }

    @Transactional
    public void deleteNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        assertIsStudyExist(studyUuid);
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            // the references of the deleted modifications are removed from directory-server through the DELETE notification network-modification-server emits
            networkModificationRestService.deleteModificationsByUuids(modificationsUuids, userId);
        } finally {
            notificationService.emitModificationsDeleted(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void unbuildNodeTree(@NonNull UUID studyUuid, UUID rootNodeUuid, @NonNull String userId) {
        doUnbuildNodeTree(studyUuid, rootNodeUuid, false, userId);
    }

    private void doUnbuildNodeTree(UUID studyUuid, UUID rootNodeUuid, boolean skipDeleteVariants, @NonNull String userId) {
        InvalidateNodeTreeParameters invalidateNodeTreeParameters = InvalidateNodeTreeParameters.ALL;
        List<UUID> rootNetworkIds = rootNetworkService.getStudyRootNetworkIds(studyUuid);
        List<CompletableFuture<Void>> futures = rootNetworkIds.stream()
                .map(rnId -> studyServerExecutionService.runAsync(() ->
                        networkModificationTreeService.invalidateNodeTree(studyUuid, rootNodeUuid, rnId, invalidateNodeTreeParameters, skipDeleteVariants)))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        boolean noneMatchUnloading = rootNetworkIds.stream()
                .map(rnId -> rootNetworkService.getRootNetwork(rnId).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")))
                .noneMatch(rootNetwork -> rootNetwork.getLoadStatus() == RootNetworkLoadStatus.UNLOADING);
        if (noneMatchUnloading) {
            notificationService.emitElementUpdated(studyUuid, userId);
        }
    }

    @Transactional
    public void duplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid, String userId) {
        assertDuplicateStudySubtree(sourceStudyUuid, targetStudyUuid, parentNodeToCopyUuid, referenceNodeUuid);
        if (!sourceStudyUuid.equals(targetStudyUuid)) {
            assertCanWriteModificationsInStudy(targetStudyUuid, networkModificationTreeService.getSubtreeModificationGroupUuids(parentNodeToCopyUuid),
                    List.of(), List.of(), userId);
        }
        AbstractNode studySubTree = networkModificationTreeService.getStudySubtree(sourceStudyUuid, parentNodeToCopyUuid, null);
        StudyEntity studyEntity = getStudy(targetStudyUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.cloneStudyTree(studySubTree, referenceNodeUuid, studyEntity, userId);
        notificationService.emitSubtreeInserted(targetStudyUuid, duplicatedNodeUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void duplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        assertDuplicateStudyNode(sourceStudyUuid, targetStudyUuid, nodeToCopyUuid, referenceNodeUuid, insertMode);
        if (!sourceStudyUuid.equals(targetStudyUuid)) {
            assertCanWriteModificationsInStudy(targetStudyUuid, List.of(networkModificationTreeService.getModificationGroupUuid(nodeToCopyUuid)),
                    List.of(), List.of(), userId);
        }

        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode, userId);
        boolean invalidateBuild = networkModificationTreeService.hasModifications(nodeToCopyUuid, false);
        if (invalidateBuild) {
            networkModificationTreeService.invalidateNodeTree(targetStudyUuid, duplicatedNodeUuid, InvalidateNodeTreeParameters.ONLY_CHILDREN_BUILD_STATUS);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void duplicateNetworkModifications(
            UUID targetStudyUuid,
            UUID targetNodeUuid,
            List<UUID> modificationsUuids,
            String userId) {
        assertCanWriteModificationsInStudy(targetStudyUuid, List.of(), modificationsUuids, List.of(), userId);
        duplicateModificationsOrInsertComposites(targetStudyUuid, targetNodeUuid,
                (groupUuid, modificationApplicationContexts) -> {
                    NetworkModificationsResult result = networkModificationRestService.duplicateModifications(groupUuid, Pair.of(modificationsUuids, modificationApplicationContexts), userId);
                    directoryService.createSharingLinks(networkModificationRestService.getChildrenModificationsReferences(result.modificationUuids()), targetStudyUuid, targetNodeUuid, userId);
                    return result;
                },
                userId);
    }

    private void duplicateModificationsOrInsertComposites(
            UUID targetStudyUuid,
            UUID targetNodeUuid,
            BiFunction<UUID, List<ModificationApplicationContext>, NetworkModificationsResult> handleModifications,
            String userId) {
        networkModificationTreeService.invalidateNodeTreeWithLF(targetStudyUuid, targetNodeUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode.ALL);
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        try {
            networkModificationTreeService.assertStudyContainsNode(targetStudyUuid, targetNodeUuid);

            List<RootNetworkEntity> studyRootNetworkEntities = rootNetworkService.getStudyRootNetworks(targetStudyUuid);
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(targetNodeUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                    .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), targetNodeUuid, rootNetworkEntity.getNetworkUuid()))
                    .toList();

            NetworkModificationsResult networkModificationResults = handleModifications.apply(groupUuid, modificationApplicationContexts);

            handleNetworkModificationApplyResult(targetStudyUuid, targetNodeUuid, networkModificationResults, studyRootNetworkEntities);
        } finally {
            notificationService.emitModificationsUpdated(targetStudyUuid, targetNodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void updateRootNetworkRequest(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        rootNetworkService.assertCanModifyRootNetwork(studyUuid, rootNetworkInfos.getId(), rootNetworkInfos.getName(), rootNetworkInfos.getTag());
        String newTag = rootNetworkInfos.getTag();
        if (newTag != null && !newTag.equals(rootNetworkService.getRootNetworkTag(rootNetworkInfos.getId()))) {
            assertCanAddRootNetworkTag(studyUuid, newTag, userId);
        }
        StudyEntity studyEntity = getStudy(studyUuid);

        if (rootNetworkInfos.hasCaseToImport()) {
            RootNetworkRequestEntity requestEntity = rootNetworkService.insertModificationRequest(studyEntity.getId(), rootNetworkInfos, userId);
            updateRootNetworkCaseInfos(studyEntity.getId(), rootNetworkInfos, userId, requestEntity);
        } else {
            updateRootNetworkBasicInfos(studyEntity.getId(), rootNetworkInfos, false, userId);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void modifyRootNetwork(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        invalidateStudyRootNetwork(studyUuid, rootNetworkInfos.getId(), userId, true);
        updateRootNetworkBasicInfos(studyUuid, rootNetworkInfos, true, userId);
    }

    public void invalidateStudyRootNetwork(UUID studyUuid, UUID rootNetworkUuid, String userId, boolean updateCase) {
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        var rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        // First we unbuild all nodes
        doUnbuildNodeTree(studyUuid, rootNodeUuid, true, userId);
        // Then we erase data linked to root node on all root networks
        rootNetworkService.invalidateRootNetworkRemoteInfos(List.of(rootNetworkService.getRootNetworkInfos(rootNetworkUuid)), true, false);
        if (!updateCase) {
            rootNetworkService.updateRootNetworkIndexationStatus(studyUuid, rootNetworkUuid, RootNetworkIndexationStatus.NOT_INDEXED);
        }
        notificationService.emitRootNetworksUpdated(studyUuid);
    }

    private List<UUID> getStudyModificationGroupUuids(UUID studyUuid) {
        return networkModificationTreeService.getAllNetworkModificationNodeInfos(studyUuid).stream()
                .map(NetworkModificationNodeInfoEntity::getModificationGroupUuid)
                .toList();
    }

    private void renameRootNetworkTagInApplicabilities(UUID studyUuid, String previousTag, String newTag, String userId) {
        if (previousTag == null || previousTag.equals(newTag)) {
            return;
        }
        networkModificationRestService.renameRootNetworkTag(getStudyModificationGroupUuids(studyUuid), previousTag, newTag, userId);
    }

    private void updateRootNetworkBasicInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, boolean updateCase, String userId) {
        String previousTag = rootNetworkService.getRootNetworkTag(rootNetworkInfos.getId());
        rootNetworkService.updateRootNetwork(rootNetworkInfos, updateCase);
        // the entity says what the tag really became, unlike the DTO
        String newTag = rootNetworkService.getRootNetworkTag(rootNetworkInfos.getId());
        renameRootNetworkTagInApplicabilities(studyUuid, previousTag, newTag, userId);
        postRootNetworkUpdate(studyUuid, rootNetworkInfos.getId(), updateCase);
    }

    private void postRootNetworkUpdate(UUID studyUuid, UUID rootNetworkUuid, boolean updateCase) {
        if (updateCase) {
            Optional<RootNetworkRequestEntity> rootNetworkModificationRequestEntityOpt = rootNetworkService.getRootNetworkRequest(rootNetworkUuid);
            rootNetworkModificationRequestEntityOpt.ifPresent(rootNetworkService::deleteRootNetworkRequest);
            notificationService.emitRootNetworkUpdated(studyUuid, rootNetworkUuid);
        } else {
            notificationService.emitRootNetworksUpdated(studyUuid);
        }
    }

    private void handleNetworkModificationApplyResult(UUID targetStudyUuid, UUID targetNodeUuid,
                                                      NetworkModificationsResult networkModificationResults,
                                                      List<RootNetworkEntity> studyRootNetworkEntities) {
        if (networkModificationResults != null) {
            int index = 0;
            // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
            for (Optional<NetworkModificationResult> modificationResultOpt : networkModificationResults.modificationResults()) {
                if (modificationResultOpt.isPresent() && studyRootNetworkEntities.get(index) != null) {
                    networkModificationTreeService.handleNetworkModificationApplyResult(targetStudyUuid, targetNodeUuid, studyRootNetworkEntities.get(index).getId(), modificationResultOpt.get());
                }
                index++;
            }
        }
    }

    private void updateRootNetworkCaseInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId, RootNetworkRequestEntity rootNetworkModificationRequestEntity) {
        UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), true);
        rootNetworkInfos.getCaseInfos().setCaseUuid(clonedCaseUuid);
        try {
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParameters(), CaseImportAction.ROOT_NETWORK_MODIFICATION, UUID.randomUUID());
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkModificationRequestEntity);
            throw e;
        }
    }

    @Transactional
    public void insertCompositeNetworkModifications(
            UUID targetStudyUuid,
            UUID targetNodeUuid,
            List<CompositeInfos> compositesInfos,
            String userId,
            StudyConstants.CompositeModificationsActionType action) {
        boolean isInsert = action == StudyConstants.CompositeModificationsActionType.INSERT;
        assertCanWriteCompositesInStudy(targetStudyUuid, compositesInfos, isInsert, userId);
        duplicateModificationsOrInsertComposites(
                targetStudyUuid,
                targetNodeUuid,
                (groupUuid, modificationApplicationContexts) -> {
                    NetworkModificationsResult result = networkModificationRestService.insertCompositeModifications(groupUuid, action, Pair.of(compositesInfos, modificationApplicationContexts));
                    if (isInsert) {
                        createCompositesReferences(compositesInfos, result.modificationUuids(), targetStudyUuid, targetNodeUuid, userId);
                    }
                    return result;
                },
                userId
        );
    }

    @Transactional
    public RootNetworkRequestInfos createRootNetworkRequest(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        rootNetworkService.assertCanCreateRootNetwork(studyUuid, rootNetworkInfos.getName(), rootNetworkInfos.getTag());
        if (rootNetworkInfos.getTag() != null) {
            assertCanAddRootNetworkTag(studyUuid, rootNetworkInfos.getTag(), userId);
        }
        StudyEntity studyEntity = getStudy(studyUuid);

        rootNetworkInfos.setId(UUID.randomUUID());
        RootNetworkRequestEntity rootNetworkCreationRequestEntity = rootNetworkService.insertCreationRequest(studyEntity.getId(), rootNetworkInfos, userId);
        try {
            UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), true);
            rootNetworkInfos.getCaseInfos().setCaseUuid(clonedCaseUuid);
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParameters(), CaseImportAction.ROOT_NETWORK_CREATION, UUID.randomUUID());
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkCreationRequestEntity);
            throw e;
        }

        notificationService.emitRootNetworksUpdated(studyUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
        return rootNetworkCreationRequestEntity.toDto();
    }

    private void persistNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String variantId, String userId,
                                Map<String, Object> importParameters, CaseImportAction caseImportAction, UUID reportId) {
        networkConversionService.persistNetwork(rootNetworkInfos, studyUuid, variantId, userId, reportId, importParameters, caseImportAction);
    }
}
