/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.controller.StudyController;
import org.gridsuite.study.server.dto.modification.NetworkModificationMetadata;
import org.gridsuite.study.server.handler.RebuildPreviouslyBuiltNodeHandler;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@SpringBootTest
@DisableElasticsearch
class StudyControllerRebuildNodeHandlerTest {

    @MockitoBean
    private RebuildPreviouslyBuiltNodeHandler rebuildPreviouslyBuiltNodeHandler;
    // this test is only making sure all those endpoint are actually calling rebuildPreviouslyBuiltNodeHandler
    // we mock studyService since we don't cover all the assertions
    @MockitoBean
    private StudyService studyService;

    @Autowired
    private StudyController studyController;

    private final UUID nodeUuid = UUID.randomUUID();
    private final UUID studyUuid = UUID.randomUUID();
    private final String userId = "userId";

    @Test
    void testCreateNetworkModification() {
        studyController.createNetworkModification(studyUuid, nodeUuid, "modificationBody", userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(userId), any(Runnable.class));
    }

    @Test
    void testMoveNetworkModification() {
        UUID modificationUuid = UUID.randomUUID();
        studyController.moveModification(studyUuid, nodeUuid, modificationUuid, null, userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(userId), any(Runnable.class));
    }

    @Test
    void testMoveNetworkModifications() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        UUID originNodeUuid = UUID.randomUUID();
        studyController.moveOrCopyModifications(studyUuid, nodeUuid, StudyConstants.ModificationsActionType.MOVE, studyUuid, originNodeUuid, modificationUuids, userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(originNodeUuid), eq(userId), any(Runnable.class));
    }

    @Test
    void updateNetworkModification() {
        UUID modificationUuid = UUID.randomUUID();
        studyController.updateNetworkModification(studyUuid, nodeUuid, modificationUuid, "modificationAttributes", userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(userId), any(Runnable.class));
    }

    @Test
    void stashNetworkModification() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        studyController.stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, true, userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(userId), any(Runnable.class));
    }

    @Test
    void restoreNetworkModification() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        studyController.stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, false, userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(userId), any(Runnable.class));
    }

    // when a modification is enabled/disabled, this method is called
    @Test
    void updateNetworkModificationMetadata() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        studyController.updateNetworkModificationsMetadata(studyUuid, nodeUuid, modificationUuids, new NetworkModificationMetadata(true, "description", "type"), userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(userId), any(Runnable.class));
    }

    @Test
    void testUpdateNetworkModificationActivationByRootNetwork() {
        Set<UUID> modificationUuids = Set.of(UUID.randomUUID());
        UUID rootNetworkUuid = UUID.randomUUID();
        studyController.updateNetworkModificationsActivation(studyUuid, rootNetworkUuid, nodeUuid, modificationUuids, true, userId);

        verify(rebuildPreviouslyBuiltNodeHandler, times(1)).execute(eq(studyUuid), eq(nodeUuid), eq(userId), any(Runnable.class));
    }
}
