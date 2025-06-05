package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.InvalidateNodeInfos;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowWorkflowInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisableElasticsearch
class LoadFLowUnitTest {

    @SpyBean
    private StudyService studyService;

    UUID studyUuid = UUID.randomUUID();
    UUID nodeUuid = UUID.randomUUID();
    UUID rootNetworkUuid = UUID.randomUUID();
    UUID networkUuid = UUID.randomUUID();
    String userId = "userId";

    UUID loadflowResultUuid = UUID.randomUUID();

    @MockBean
    RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockBean
    private RootNetworkService rootNetworkService;
    @MockBean
    private NetworkModificationService networkModificationService;
    @MockBean
    private NetworkService networkService;
    @MockBean
    private NotificationService notificationService;

    @Test
    void testRunLoadFlow() {
        when(rootNetworkNodeInfoService.getLoadflowResultUuid(nodeUuid, rootNetworkUuid)).thenReturn(null);

        doReturn(loadflowResultUuid).when(studyService).sendLoadflowRequest(studyUuid, nodeUuid, rootNetworkUuid, null, false, userId);
        assertEquals(loadflowResultUuid, studyService.runLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, false, userId));

        verify(studyService, times(1)).sendLoadflowRequest(studyUuid, nodeUuid, rootNetworkUuid, null, false, userId);
    }

    @Test
    void testRunLoadFlowWithExistingResult() {
        UUID previousResultUuid = UUID.randomUUID();
        when(rootNetworkNodeInfoService.getLoadflowResultUuid(nodeUuid, rootNetworkUuid)).thenReturn(previousResultUuid);

        doNothing().when(studyService).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid, false);

        doReturn(loadflowResultUuid).when(studyService).createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, false);
        doReturn(loadflowResultUuid).when(studyService).rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);

        assertEquals(loadflowResultUuid, studyService.runLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, false, userId));

        verify(studyService, times(1)).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid, false);
        verify(studyService, times(1)).createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, false);
        verify(studyService, times(1)).rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);
    }

    @Test
    void testRerunLoadFlow() {
        boolean withRatioTapChangers = false;
        InvalidateNodeTreeParameters expectedInvalidationParameters = InvalidateNodeTreeParameters.builder()
            .invalidationMode(InvalidateNodeTreeParameters.InvalidationMode.ALL)
            .computationsInvalidationMode(InvalidateNodeTreeParameters.ComputationsInvalidationMode.PRESERVE_LOAD_FLOW_RESULTS)
            .build();
        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        UUID groupUuidToInvalidate = UUID.randomUUID();
        invalidateNodeInfos.addGroupUuids(List.of(groupUuidToInvalidate));

        BuildInfos buildInfos = new BuildInfos();

        RerunLoadFlowWorkflowInfos expectedWorkflowInfo = RerunLoadFlowWorkflowInfos.builder()
            .userId(userId)
            .withRatioTapChangers(withRatioTapChangers)
            .loadflowResultUuid(loadflowResultUuid)
            .build();

        // mock call returning values
        when(networkModificationTreeService.invalidateNodeTree(nodeUuid, rootNetworkUuid, expectedInvalidationParameters)).thenReturn(invalidateNodeInfos);
        when(rootNetworkService.getNetworkUuid(rootNetworkUuid)).thenReturn(networkUuid);
        when(networkModificationTreeService.getBuildInfos(nodeUuid, rootNetworkUuid)).thenReturn(buildInfos);

        // execute loadflow rerun
        assertEquals(loadflowResultUuid, studyService.rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId));

        // node invalidation
        verify(networkModificationTreeService, times(1)).invalidateNodeTree(nodeUuid, rootNetworkUuid, expectedInvalidationParameters);
        verify(networkModificationService, times(1)).deleteIndexedModifications(invalidateNodeInfos.getGroupUuids(), networkUuid);
        verify(notificationService, times(11)).emitStudyChanged(eq(studyUuid), eq(nodeUuid), eq(rootNetworkUuid), anyString());

        // node build
        ArgumentCaptor<RerunLoadFlowWorkflowInfos> rerunLoadFlowWorkflowInfosArgumentCaptor = ArgumentCaptor.forClass(RerunLoadFlowWorkflowInfos.class);
        verify(networkModificationService, times(1)).buildNode(eq(nodeUuid), eq(rootNetworkUuid), eq(buildInfos), rerunLoadFlowWorkflowInfosArgumentCaptor.capture());

        // check workflow infos
        assertThat(rerunLoadFlowWorkflowInfosArgumentCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedWorkflowInfo);
    }
}
