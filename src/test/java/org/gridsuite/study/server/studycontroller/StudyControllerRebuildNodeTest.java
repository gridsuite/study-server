/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.controller.NodeController;
import org.gridsuite.study.server.controller.StudyController;
import org.gridsuite.study.server.dto.modification.ModificationMoveInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.nodeactivity.NetworkModificationActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class StudyControllerRebuildNodeTest {

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private NetworkModificationService networkModificationService;

    // this test is only making sure all those endpoint are actually calling rebuildPreviouslyBuiltNodeHandler
    // we mock studyService since we don't cover all the assertions
    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;

    // RebuildNodeService now looks up each modification's real parent composite before delegating
    // to studyService; mock it here too so this delegation-only test doesn't hit the real HTTP client
    @MockitoBean
    private NetworkModificationRestService networkModificationRestService;

    @MockitoSpyBean
    private NodeActivityRunnerService nodeActivityService;

    @MockitoSpyBean
    private NetworkModificationActivityRunnerService modificationActivityRunnerService;

    @Autowired
    private StudyController studyController;

    @Autowired
    private NodeController nodeController;

    private final UUID studyUuid = UUID.randomUUID();
    private final UUID rootNetworkUuid = UUID.randomUUID();
    private final UUID nodeUuid = UUID.randomUUID();
    private final UUID modificationUuid = UUID.randomUUID();

    private final String userId = "userId";

    @BeforeEach
    void setUp() {
        doReturn(Map.of(nodeUuid, Set.of(rootNetworkUuid))).when(networkModificationService).getSecurityNodesToRebuild(any(), any(), any());
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(any(), any());
        doAnswer(_ -> false).when(networkModificationTreeService).isRootOrConstructionNode(any());
        doAnswer(_ -> Map.of()).when(networkModificationRestService).findParentComposites(any());

        TestUtils.bypassNodeActivities(nodeActivityService);
        TestUtils.bypassNodeActivities(modificationActivityRunnerService);
    }

    @Test
    void testCreateNetworkModification() {
        nodeController.createNetworkModification(studyUuid, nodeUuid, "modificationBody", userId);

        verify(networkModificationService, times(1)).createNetworkModification(studyUuid, nodeUuid, "modificationBody", userId);
        verify(networkModificationService, times(1)).buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId);
    }

    @Test
    void testMoveNetworkModifications() {
        UUID originNodeUuid = UUID.randomUUID();
        List<ModificationMoveInfos> modificationMoveInfos = List.of(new ModificationMoveInfos(UUID.randomUUID(), null, null, null));

        doReturn(Map.of(nodeUuid, Set.of(rootNetworkUuid))).when(networkModificationService).getSecurityNodesToRebuild(studyUuid, nodeUuid, originNodeUuid);

        nodeController.moveModifications(studyUuid, nodeUuid, originNodeUuid, modificationMoveInfos, userId);

        verify(networkModificationService, times(1)).moveNetworkModifications(studyUuid, originNodeUuid, nodeUuid, modificationMoveInfos, false, userId);
        verify(networkModificationService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void updateNetworkModification() {
        nodeController.updateNetworkModification(studyUuid, nodeUuid, modificationUuid, "modificationAttributes", userId);

        verify(networkModificationService, times(1)).updateNetworkModification(studyUuid, "modificationAttributes", nodeUuid, modificationUuid, userId);
        verify(networkModificationService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void stashNetworkModification() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        nodeController.stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, true, userId);

        verify(networkModificationService, times(1)).stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, userId);
        verify(networkModificationService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void restoreNetworkModification() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        nodeController.stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, false, userId);

        verify(networkModificationService, times(1)).restoreNetworkModifications(studyUuid, nodeUuid, modificationUuids, userId);
        verify(networkModificationService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    // when a modification is enabled/disabled, this method is called
    @Test
    void updateNetworkModificationActivation() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        nodeController.updateNetworkModificationsActivation(studyUuid, nodeUuid, modificationUuids, true, userId);

        verify(networkModificationService, times(1)).updateNetworkModificationsActivation(studyUuid, nodeUuid, modificationUuids, userId, true);
        verify(networkModificationService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void testUpdateNetworkModificationsNameAndDescription() throws JsonProcessingException {
        Map<String, Map<String, String>> metadataMap = Map.of("message", Map.of("name", "newName", "description", "newDescription"));
        String metadata = mapper.writeValueAsString(metadataMap);
        nodeController.updateModificationNameAndDescription(studyUuid, nodeUuid, modificationUuid, metadata, userId);

        verify(networkModificationService, times(1))
                .updateNetworkModificationNameAndDescription(studyUuid, nodeUuid, modificationUuid, metadata, userId);
    }

    @Test
    void testUpdateNetworkModificationApplicabilityByRootNetwork() {
        Set<UUID> modificationUuids = Set.of(UUID.randomUUID());
        studyController.updateNetworkModificationsApplicability(studyUuid, rootNetworkUuid, nodeUuid, modificationUuids, true, userId);

        verify(networkModificationService, times(1)).updateNetworkModificationsApplicabilityInRootNetwork(studyUuid, nodeUuid, rootNetworkUuid, modificationUuids, userId, true);
        verify(networkModificationService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void testUnshareModificationReference() {
        ResponseEntity<Void> response = nodeController.unshareModificationReference(modificationUuid, studyUuid, nodeUuid, userId);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(networkModificationService, times(1)).unshareModificationReferences(studyUuid, Map.of(nodeUuid, List.of(modificationUuid)), userId);
        verify(studyService, times(1)).assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        verify(studyService, times(1)).assertIsNodeNotReadOnly(nodeUuid);
    }

    @Test
    void testUnshareAllReferencesInNode() {
        ResponseEntity<Void> response = nodeController.unshareAllModificationReferencesInNode(studyUuid, nodeUuid, userId);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(studyService, times(1)).assertIsStudyAndNodeExist(studyUuid, nodeUuid);
        verify(studyService, times(1)).assertIsNodeNotReadOnly(nodeUuid);
        verify(networkModificationService, times(1)).unshareAllReferencesInNode(studyUuid, nodeUuid, userId);
    }

    @Test
    void testUnshareAllReferencesInStudy() {
        ResponseEntity<Void> response = studyController.unshareAllModificationReferences(studyUuid, userId);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(studyService, times(1)).assertIsStudyExist(studyUuid);
        verify(networkModificationService, times(1)).unshareAllReferencesInStudy(studyUuid, userId);
    }
}
