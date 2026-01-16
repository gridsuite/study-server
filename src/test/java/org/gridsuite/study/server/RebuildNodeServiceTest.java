package org.gridsuite.study.server;

import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@SpringBootTest
@DisableElasticsearch
class RebuildNodeServiceTest {
    @MockitoSpyBean
    private RebuildNodeService rebuildNodeService;

    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockitoBean
    private StudyService studyService;

    UUID studyUuid = UUID.randomUUID();
    UUID node1Uuid = UUID.randomUUID();
    UUID node2Uuid = UUID.randomUUID();
    String userId = "userId";

    UUID rootNetworkUuid = UUID.randomUUID();
    UUID rootNetwork2Uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        doReturn(List.of(node1Uuid, node2Uuid)).when(networkModificationTreeService).getHighestNodeUuids(node1Uuid, node2Uuid);
        doReturn(List.of(node1Uuid)).when(networkModificationTreeService).getHighestNodeUuids(node1Uuid, node1Uuid);
        doReturn(false).when(networkModificationTreeService).isRootOrConstructionNode(any());
    }

    @Test
    void testRebuildSingleNode() {
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetwork(studyUuid, node1Uuid);

        rebuildNodeService.moveNetworkModification(studyUuid, node1Uuid, UUID.randomUUID(), UUID.randomUUID(), userId);

        verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
    }

    @Test
    void testRebuildMultipleNodes() {
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetwork(studyUuid, node1Uuid);
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetwork(studyUuid, node2Uuid);

        rebuildNodeService.moveNetworkModifications(studyUuid, node1Uuid, node2Uuid, List.of(), userId);

        verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        verify(studyService, times(1)).buildNode(studyUuid, node2Uuid, rootNetworkUuid, userId);
    }

    @Test
    void testRebuildMultipleRootNetworks() {
        doReturn(
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.BUILT)
            )
        ).when(studyService).getNodeBuildStatusByRootNetwork(studyUuid, node1Uuid);

        rebuildNodeService.moveNetworkModifications(studyUuid, node1Uuid, node2Uuid, List.of(), userId);

        verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetwork2Uuid, userId);
    }

    @Test
    void testRebuildMultipleRootNetworksAndNodes() {
        Runnable runnable = Mockito.spy(Runnable.class);
        doReturn(
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT)
            )
        ).when(studyService).getNodeBuildStatusByRootNetwork(studyUuid, node1Uuid);

        doReturn(
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.BUILT)
            )
        ).when(studyService).getNodeBuildStatusByRootNetwork(studyUuid, node2Uuid);

        rebuildNodeService.moveNetworkModifications(studyUuid, node1Uuid, node2Uuid, List.of(), userId);

        verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        verify(studyService, times(1)).buildNode(studyUuid, node2Uuid, rootNetwork2Uuid, userId);
    }

    @Test
    void testRebuildConstructionNode() {
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT)),
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetwork(studyUuid, node1Uuid);

        doReturn(true).when(networkModificationTreeService).isRootOrConstructionNode(any());

        rebuildNodeService.moveNetworkModification(studyUuid, node1Uuid, UUID.randomUUID(), UUID.randomUUID(), userId);

        verify(studyService, times(0)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
    }
}
