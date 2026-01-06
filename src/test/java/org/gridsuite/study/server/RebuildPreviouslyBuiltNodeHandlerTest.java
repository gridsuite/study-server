package org.gridsuite.study.server;

import org.gridsuite.study.server.handler.RebuildPreviouslyBuiltNodeHandler;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisableElasticsearch
class RebuildPreviouslyBuiltNodeHandlerTest {
    @Autowired
    private RebuildPreviouslyBuiltNodeHandler rebuildPreviouslyBuiltNodeHandler;

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
    }

    @Test
    void testRebuildSingleNode() {
        Runnable runnable = Mockito.spy(Runnable.class);
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT)),
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetworkUuid(studyUuid, node1Uuid);

        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, node1Uuid, userId, runnable);

        InOrder inOrder = Mockito.inOrder(runnable, studyService);
        inOrder.verify(runnable, times(1)).run();
        inOrder.verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
    }

    @Test
    void testRebuildMultipleNodes() {
        Runnable runnable = Mockito.spy(Runnable.class);
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT)),
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetworkUuid(studyUuid, node1Uuid);
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT)),
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetworkUuid(studyUuid, node2Uuid);

        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, node1Uuid, node2Uuid, userId, runnable);

        InOrder inOrder = Mockito.inOrder(runnable, studyService);
        inOrder.verify(runnable, times(1)).run();
        inOrder.verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        inOrder.verify(studyService, times(1)).buildNode(studyUuid, node2Uuid, rootNetworkUuid, userId);
    }

    @Test
    void testRebuildMultipleRootNetworks() {
        Runnable runnable = Mockito.spy(Runnable.class);
        doReturn(
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.BUILT)
            ),
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT)
            )
        ).when(studyService).getNodeBuildStatusByRootNetworkUuid(studyUuid, node1Uuid);

        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, node1Uuid, userId, runnable);

        InOrder inOrder = Mockito.inOrder(runnable, studyService);
        inOrder.verify(runnable, times(1)).run();
        inOrder.verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        // this does not need to be checked in order, what matters is that "runnable" is called BEFORE nodes are rebuilt
        Mockito.verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetwork2Uuid, userId);
    }

    @Test
    void testRebuildMultipleRootNetworksAndNodes() {
        Runnable runnable = Mockito.spy(Runnable.class);
        doReturn(
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT)
            ),
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT)
            )
        ).when(studyService).getNodeBuildStatusByRootNetworkUuid(studyUuid, node1Uuid);

        doReturn(
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.BUILT)
            ),
            Map.of(
                rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT),
                rootNetwork2Uuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT)
            )
        ).when(studyService).getNodeBuildStatusByRootNetworkUuid(studyUuid, node2Uuid);

        rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, node1Uuid, node2Uuid, userId, runnable);

        InOrder inOrder = Mockito.inOrder(runnable, studyService);
        inOrder.verify(runnable, times(1)).run();
        inOrder.verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        // this does not need to be checked in order, what matters is that "runnable" is called BEFORE nodes are rebuilt
        Mockito.verify(studyService, times(1)).buildNode(studyUuid, node2Uuid, rootNetwork2Uuid, userId);
    }

    @Test
    void testRebuildNodeException() {
        // operation should not be canceled and no exception should be thrown if node rebuild fails
        Runnable runnable = Mockito.spy(Runnable.class);
        doReturn(
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT)),
            Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT))
        ).when(studyService).getNodeBuildStatusByRootNetworkUuid(studyUuid, node1Uuid);

        doThrow(new RuntimeException("Something wrong happened during node building"))
            .when(studyService).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);

        assertDoesNotThrow(() -> rebuildPreviouslyBuiltNodeHandler.execute(studyUuid, node1Uuid, userId, runnable));

        InOrder inOrder = Mockito.inOrder(runnable, studyService);
        inOrder.verify(runnable, times(1)).run();
        inOrder.verify(studyService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
    }
}
