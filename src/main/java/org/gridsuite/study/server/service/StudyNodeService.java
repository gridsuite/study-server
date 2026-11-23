/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.gridsuite.study.server.dto.modification.CompositeInfos;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class StudyNodeService {

    private final NodeService nodeService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final NetworkModificationRestService networkModificationRestService;
    private final DirectoryService directoryService;
    private final NotificationService notificationService;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    public StudyNodeService(NodeService nodeService,
                            NetworkModificationTreeService networkModificationTreeService,
                            NetworkModificationRestService networkModificationRestService,
                            DirectoryService directoryService,
                            NotificationService notificationService,
                            RootNetworkService rootNetworkService,
                            RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        this.nodeService = nodeService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkModificationRestService = networkModificationRestService;
        this.directoryService = directoryService;
        this.notificationService = notificationService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
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
        nodeService.assertIsStudyExist(studyUuid);
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
        nodeService.doUnbuildNodeTree(studyUuid, rootNodeUuid, false, userId);
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
    //TODO : has duplicate in study server see if it is possible to move to a common place
    private void assertCanWriteModificationsInStudy(UUID studyUuid, List<UUID> containerUuids, List<UUID> modificationUuids,
                                            List<UUID> sharedUuids, String userId) {
        networkModificationRestService.assertRootNetworkTagsAllowed(containerUuids, modificationUuids, sharedUuids,
                rootNetworkService.getStudyRootNetworkTags(studyUuid), userId);
    }

}
