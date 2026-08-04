/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.ComputationType;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        doReturn(Map.of(QuotaType.BUILD, 10)).when(userAdminService).getUserMaxQuota(userId);
        doReturn(0L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);
        mockNodeBuild(node3.getIdNode(), rootNetworkUuid);

        studyService.buildNodes(studyUuid,
            studyService.getFirstLevelChildrenToBuild(studyUuid, node1.getIdNode(), rootNetworkUuid, userId),
            rootNetworkUuid, userId);

        verifyNodeBuild(node2.getIdNode(), rootNetworkUuid);
        verifyNodeBuild(node3.getIdNode(), rootNetworkUuid);
        // check n4 has actually not been built
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(3)).getUserMaxQuota(userId);
        verify(networkModificationTreeService, times(3)).countBuiltNodes(studyUuid, rootNetworkUuid);
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
        doReturn(Map.of(QuotaType.BUILD, 10)).when(userAdminService).getUserMaxQuota(userId);
        doReturn(10L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        studyService.buildNodes(studyUuid,
            studyService.getFirstLevelChildrenToBuild(studyUuid, node1.getIdNode(), rootNetworkUuid, userId),
            rootNetworkUuid, userId);

        verify(networkModificationService, times(0)).buildNode(eq(node2.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        verify(userAdminService, times(1)).getUserMaxQuota(userId);
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
        doReturn(Map.of(QuotaType.BUILD, 10)).when(userAdminService).getUserMaxQuota(userId);
        doReturn(9L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);

        studyService.buildNodes(studyUuid,
            studyService.getFirstLevelChildrenToBuild(studyUuid, node1.getIdNode(), rootNetworkUuid, userId),
            rootNetworkUuid, userId);

        verifyNodeBuild(node2.getIdNode(), rootNetworkUuid);
        verify(networkModificationService, times(0)).buildNode(eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(2)).getUserMaxQuota(userId);
        verify(networkModificationTreeService, times(2)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testAssertOnQuotasAvailabilityDoesNothingWhenQuotasCheckDisabled() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", false);

        // quotas check is disabled: even a saturated quota must not throw, and must not even be looked up
        assertDoesNotThrow(() -> studyService.assertOnQuotasAvailability(ComputationType.SHORT_CIRCUIT, userId));

        verify(userAdminService, never()).getUserMaxQuota(userId);
        verify(userAdminService, never()).getUserCurrentQuota(userId);
    }

    @Test
    void testAssertOnQuotasAvailabilityDoesNotThrowWhenUnderQuota() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);

        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 5)).when(userAdminService).getUserMaxQuota(userId);
        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 2)).when(userAdminService).getUserCurrentQuota(userId);

        assertDoesNotThrow(() -> studyService.assertOnQuotasAvailability(ComputationType.SHORT_CIRCUIT, userId));
    }

    @Test
    void testAssertOnQuotasAvailabilityThrowsWhenQuotaReached() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);

        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 5)).when(userAdminService).getUserMaxQuota(userId);
        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 5)).when(userAdminService).getUserCurrentQuota(userId);

        StudyException exception = assertThrows(StudyException.class,
                () -> studyService.assertOnQuotasAvailability(ComputationType.SHORT_CIRCUIT, userId));

        assertEquals(MAX_OPERATION_TYPE_EXCEEDED, exception.getBusinessErrorCode());
        assertEquals(Map.of("maxComputation", 5, "currentComputation", 5), exception.getBusinessErrorValues());
    }

    @Test
    void testAssertOnQuotasAvailabilityThrowsWhenQuotaExceeded() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);

        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 5)).when(userAdminService).getUserMaxQuota(userId);
        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 6)).when(userAdminService).getUserCurrentQuota(userId);

        assertThrows(StudyException.class, () -> studyService.assertOnQuotasAvailability(ComputationType.SHORT_CIRCUIT, userId));
    }

    @Test
    void testAssertOnQuotasAvailabilityDoesNotThrowWhenNoMaxQuotaConfiguredForOperation() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);

        // max quotas map has no entry at all for SHORT_CIRCUIT (only for another operation)
        doReturn(Map.of(QuotaType.LOAD_FLOW, 5)).when(userAdminService).getUserMaxQuota(userId);
        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 100)).when(userAdminService).getUserCurrentQuota(userId);

        assertDoesNotThrow(() -> studyService.assertOnQuotasAvailability(ComputationType.SHORT_CIRCUIT, userId));
    }

    @Test
    void testAssertOnQuotasAvailabilityDoesNotThrowWhenNoCurrentQuotaReported() {
        String userId = "userId";
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);

        doReturn(Map.of(QuotaType.SHORT_CIRCUIT, 5)).when(userAdminService).getUserMaxQuota(userId);
        // current quotas map has no entry at all for SHORT_CIRCUIT (nothing running yet)
        doReturn(Map.of()).when(userAdminService).getUserCurrentQuota(userId);

        assertDoesNotThrow(() -> studyService.assertOnQuotasAvailability(ComputationType.SHORT_CIRCUIT, userId));
    }

    @Test
    void testGetOperationQuotaStatusReflectsConfiguredFlag() {
        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", true);
        assertTrue(studyService.getOperationQuotaStatus());

        ReflectionTestUtils.setField(studyService, "shouldCheckOperationQuotas", false);
        assertFalse(studyService.getOperationQuotaStatus());
    }

    private void mockNodeBuild(UUID nodeUuid, UUID rootNetworkUuid) {
        doReturn(new BuildInfos()).when(networkModificationTreeService).getBuildInfos(nodeUuid, rootNetworkUuid);
        doNothing().when(networkModificationTreeService).setModificationReports(eq(nodeUuid), eq(rootNetworkUuid), any());
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationTreeService).getNodeBuildStatus(nodeUuid, rootNetworkUuid);
    }

    private void verifyNodeBuild(UUID nodeUuid, UUID rootNetworkUuid) {
        verify(networkModificationService, times(1)).buildNode(eq(nodeUuid), eq(rootNetworkUuid), any(), eq(null));
    }
}
