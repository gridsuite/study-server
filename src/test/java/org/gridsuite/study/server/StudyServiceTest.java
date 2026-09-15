/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.QuotaState;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.MAX_OPERATION_TYPE_EXCEEDED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyServiceTest {
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private StudyService studyService;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoSpyBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private NetworkModificationService networkModificationService;

    @AfterEach
    void resetOperationQuotasFlag() {
        // restore the test-profile default (study.enable-operation-quotas=false) so no state leaks between tests
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", false);
    }

    @Test
    void testBuildFirstLevelChildren() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota not reached, all first level children of N1 will be built
        doNothing().when(networkModificationTreeService).buildNode(any(UUID.class), any(UUID.class), any(UUID.class), eq(userId), isNull());
        doReturn(Map.of(QuotaType.BUILD, new QuotaState(0, 10))).when(userAdminService).getUserQuotaState(userId);
        doReturn(0L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);
        mockNodeBuild(node3.getIdNode(), rootNetworkUuid);

        studyService.buildNodes(studyUuid,
            studyService.getFirstLevelChildrenToBuild(studyUuid, node1.getIdNode(), rootNetworkUuid, userId),
            rootNetworkUuid, userId);

        verify(networkModificationTreeService, times(1)).buildNode(eq(studyUuid), eq(node2.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationTreeService, times(1)).buildNode(eq(studyUuid), eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        // check n4 has actually not been built
        verify(networkModificationTreeService, times(0)).buildNode(eq(studyUuid), eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(1)).getUserQuotaState(userId);
        verify(networkModificationTreeService, times(1)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testBuildFirstLevelChildrenWithQuotaAlreadyReached() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota already reached, nothing will be built
        doReturn(Map.of(QuotaType.BUILD, new QuotaState(0, 10))).when(userAdminService).getUserQuotaState(userId);
        doReturn(10L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        studyService.buildNodes(studyUuid,
            studyService.getFirstLevelChildrenToBuild(studyUuid, node1.getIdNode(), rootNetworkUuid, userId),
            rootNetworkUuid, userId);

        verify(networkModificationService, times(0)).buildNode(eq(node2.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        verify(userAdminService, times(1)).getUserQuotaState(userId);
        verify(networkModificationTreeService, times(1)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testBuildFirstLevelChildrenWithQuotaReached() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota will be reached, only one child will be built
        doNothing().when(networkModificationTreeService).buildNode(any(UUID.class), any(UUID.class), any(UUID.class), eq(userId), isNull());
        doReturn(Map.of(QuotaType.BUILD, new QuotaState(0, 10))).when(userAdminService).getUserQuotaState(userId);
        doReturn(9L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);

        studyService.buildNodes(studyUuid,
            studyService.getFirstLevelChildrenToBuild(studyUuid, node1.getIdNode(), rootNetworkUuid, userId),
            rootNetworkUuid, userId);

        verify(networkModificationTreeService, times(1)).buildNode(eq(studyUuid), eq(node2.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationTreeService, times(0)).buildNode(eq(studyUuid), eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationTreeService, times(0)).buildNode(eq(studyUuid), eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(1)).getUserQuotaState(userId);
        verify(networkModificationTreeService, times(1)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testConsumeQuotaReturnsNullAndSkipsRemoteCallWhenQuotasCheckDisabled() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", false);

        assertNull(studyService.consumeQuota(ComputationType.SHORT_CIRCUIT, userId));

        verify(userAdminService, never()).consumeQuota(eq(userId), any());
    }

    @Test
    void testConsumeQuotaReturnsQuotaIdWhenUnderQuota() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);

        UUID quotaId = UUID.randomUUID();
        doReturn(quotaId).when(userAdminService).consumeQuota(userId, QuotaType.SHORT_CIRCUIT);

        assertEquals(quotaId, studyService.consumeQuota(ComputationType.SHORT_CIRCUIT, userId));
    }

    @Test
    void testConsumeQuotaPropagatesStudyExceptionWhenQuotaExhausted() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);

        doThrow(new StudyException(MAX_OPERATION_TYPE_EXCEEDED, "Max number of SHORT_CIRCUIT already reached"))
                .when(userAdminService).consumeQuota(userId, QuotaType.SHORT_CIRCUIT);

        StudyException exception = assertThrows(StudyException.class,
                () -> studyService.consumeQuota(ComputationType.SHORT_CIRCUIT, userId));

        assertEquals(MAX_OPERATION_TYPE_EXCEEDED, exception.getBusinessErrorCode());
    }

    @Test
    void testReleaseQuotaOnFailureIsNoOpWhenQuotaIdIsNull() {
        studyService.releaseQuotaOnFailure("userId", null);

        verify(userAdminService, never()).releaseQuotaId(anyString(), any());
    }

    @Test
    void testReleaseQuotaOnFailureCallsRemoteRelease() {
        UUID quotaId = UUID.randomUUID();
        studyService.releaseQuotaOnFailure("userId", quotaId);

        verify(userAdminService, times(1)).releaseQuotaId("userId", quotaId);
    }

    @Test
    void testGetOperationQuotaStatusReflectsConfiguredFlag() {
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);
        assertTrue(studyService.getOperationQuotaStatus());

        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", false);
        assertFalse(studyService.getOperationQuotaStatus());
    }

    private void mockNodeBuild(UUID nodeUuid, UUID rootNetworkUuid) {
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationTreeService).getNodeBuildStatus(nodeUuid, rootNetworkUuid);
    }

}
