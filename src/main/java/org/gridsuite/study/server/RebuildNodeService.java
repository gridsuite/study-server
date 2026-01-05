package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.modification.NetworkModificationMetadata;
import org.gridsuite.study.server.handler.RebuildPreviouslyBuiltNodeHandler;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RebuildNodeService {
    private final RebuildPreviouslyBuiltNodeHandler rebuildPreviouslyBuiltNodeHandler;
    private final StudyService studyService;

    public RebuildNodeService(RebuildPreviouslyBuiltNodeHandler rebuildPreviouslyBuiltNodeHandler, StudyService studyService) {
        this.rebuildPreviouslyBuiltNodeHandler = rebuildPreviouslyBuiltNodeHandler;
        this.studyService = studyService;
    }

    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, nodeUuid, userId,
            () -> studyService.updateNetworkModification(studyUuid, updateModificationAttributes, nodeUuid, modificationUuid, userId));
    }

    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, nodeUuid, userId,
            () -> studyService.stashNetworkModifications(studyUuid, nodeUuid, modificationsUuids, userId));
    }

    public void updateNetworkModificationsMetadata(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId, NetworkModificationMetadata metadata) {
        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, nodeUuid, userId,
            () -> studyService.updateNetworkModificationsMetadata(studyUuid, nodeUuid, modificationsUuids, userId, metadata));
    }

    public void updateNetworkModificationsActivationInRootNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Set<UUID> modificationsUuids, String userId, boolean activated) {
        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, nodeUuid, userId,
            () -> studyService.updateNetworkModificationsActivationInRootNetwork(studyUuid, nodeUuid, rootNetworkUuid, modificationsUuids, userId, activated));
    }

    public void restoreNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, nodeUuid, userId,
            () -> studyService.restoreNetworkModifications(studyUuid, nodeUuid, modificationsUuids, userId));
    }
}
