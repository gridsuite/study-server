/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.loadflow;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.controller.loadflow.LoadFlowController;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.InvalidateNodeInfos;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeBuildStatusEmbeddable;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_ALL_COMPUTATION_STATUS_WITHOUT_LOADFLOW;
import static org.gridsuite.study.server.utils.TestUtils.synchronizeStudyServerExecutionService;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class LoadFLowUnitTest {

    @Autowired
    private LoadFlowController controller;

    @MockitoSpyBean
    private StudyService studyService;
    @MockitoSpyBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoSpyBean
    private StudyServerExecutionService studyServerExecutionService;
    @MockitoSpyBean
    private LoadFlowService loadFlowService;

    UUID studyUuid = UUID.randomUUID();
    UUID nodeUuid = UUID.randomUUID();
    UUID rootNetworkUuid = UUID.randomUUID();
    UUID networkUuid = UUID.randomUUID();
    String userId = "userId";
    String variantId = "variant_1";

    UUID loadflowResultUuid = UUID.randomUUID();

    @MockitoBean
    private NodeActivityRunnerService nodeActivityService;
    @MockitoBean
    RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @MockitoBean
    private RootNetworkService rootNetworkService;
    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private LoadFlowRestService loadFlowRestService;
    @MockitoBean
    private NetworkService networkService;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    StudyRepository studyRepository;
    @MockitoBean
    NodeRepository nodesRepository;
    @MockitoBean
    private OutputDestination output;

    @BeforeEach
    void setup() {
        synchronizeStudyServerExecutionService(studyServerExecutionService);
        TestUtils.bypassNodeActivities(nodeActivityService);
        doReturn(Boolean.FALSE).when(networkModificationTreeService).isSecurityNode(nodeUuid);
    }

    @Test
    void testRunLoadFlow() {
        doReturn(Boolean.FALSE).when(networkModificationTreeService).isReadOnly(nodeUuid);
        when(rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW)).thenReturn(null);
        doNothing().when(loadFlowService).sendLoadflowRequest(any(), any(), any(), any(), anyBoolean(), anyString());
        doNothing().when(studyService).assertCanRunOnConstructionNode(any(), any(), any(), any());

        controller.runLoadFlow(studyUuid, rootNetworkUuid, nodeUuid, false, userId);

        verify(loadFlowService, times(1)).sendLoadflowRequest(any(), any(), any(), any(), anyBoolean(), anyString());
        verify(studyService, times(1)).assertCanRunOnConstructionNode(any(), any(), any(), any());
    }

    @Test
    void testRunLoadFlowWithExistingResult() {
        UUID previousResultUuid = UUID.randomUUID();
        when(rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW)).thenReturn(previousResultUuid);
        doNothing().when(studyService).assertCanRunOnConstructionNode(eq(studyUuid), eq(nodeUuid), any(), any());

        doNothing().when(loadFlowService).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid);
        doReturn(loadflowResultUuid).when(loadFlowService).createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, false);
        doNothing().when(loadFlowService).rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);

        doReturn(Boolean.FALSE).when(networkModificationTreeService).isReadOnly(nodeUuid);

        controller.runLoadFlow(studyUuid, rootNetworkUuid, nodeUuid, false, userId);

        verify(loadFlowService, times(1)).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid);
        verify(loadFlowService, times(1)).createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, false);
        verify(loadFlowService, times(1)).rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);
        verify(studyService, times(1)).assertCanRunOnConstructionNode(eq(studyUuid), eq(nodeUuid), any(), any());
    }

    @Test
    void testSendLoadflowRequestStartsQuotaAndEmitsQuotaChange() {
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(studyUuid);

        when(studyRepository.findById(studyUuid)).thenReturn(Optional.of(studyEntity));
        doReturn(false).when(networkModificationTreeService).isSecurityNode(nodeUuid);
        doReturn(Map.of()).when(networkModificationTreeService).getComputationReports(nodeUuid, rootNetworkUuid);
        doReturn(variantId).when(networkModificationTreeService).getVariantId(nodeUuid, rootNetworkUuid);
        when(rootNetworkService.getNetworkUuid(rootNetworkUuid)).thenReturn(networkUuid);
        when(loadFlowRestService.runLoadFlow(any(), any(), any(), any(), any(), anyString())).thenReturn(loadflowResultUuid);

        loadFlowService.sendLoadflowRequest(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);

        verify(userAdminService, times(1)).startOperationWithQuota(userId, QuotaType.LOAD_FLOW, loadflowResultUuid);
        verify(notificationService, times(1)).emitQuotaChange(userId, QuotaType.LOAD_FLOW);
        verify(notificationService, times(1)).emitStudyChanged(eq(studyUuid), eq(nodeUuid), eq(rootNetworkUuid), anyString());
        verify(notificationService, times(1)).emitElementUpdated(studyUuid, userId);
    }

    @Test
    void testRerunLoadFlow() {
        testRerunLoadFlow(false, true);
        testRerunLoadFlow(false, false);
        testRerunLoadFlow(true, true);
        testRerunLoadFlow(true, false);
    }

    private void testRerunLoadFlow(boolean withRatioTapChangers, boolean isSecurityNode) {
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(studyUuid);
        NodeEntity nodeEntity = new NodeEntity(nodeUuid, null, NodeType.NETWORK_MODIFICATION, studyEntity, false, null, null);
        reset(studyService, networkModificationTreeService, networkModificationService, notificationService, loadFlowRestService, studyRepository);
        when(studyRepository.findById(studyUuid)).thenReturn(Optional.of(studyEntity));
        when(nodesRepository.findById(nodeUuid)).thenReturn(Optional.of(nodeEntity));
        doReturn(isSecurityNode).when(networkModificationTreeService).isSecurityNode(nodeUuid);
        if (isSecurityNode) {
            testRerunLoadFlowSecurityNode(withRatioTapChangers);
        } else {
            testRerunLoadFlowConstructionNode(withRatioTapChangers);
        }
    }

    private void testRerunLoadFlowConstructionNode(boolean withRatioTapChangers) {
        doNothing().when(networkModificationTreeService).updateComputationReportUuid(any(UUID.class), any(UUID.class), any(ComputationType.class), any(UUID.class));
        doReturn(Map.of(LOAD_FLOW.name(), UUID.randomUUID())).when(networkModificationTreeService).getComputationReports(nodeUuid, rootNetworkUuid);
        doReturn(variantId).when(networkModificationTreeService).getVariantId(nodeUuid, rootNetworkUuid);
        loadFlowService.rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);

        verify(loadFlowRestService, times(1)).runLoadFlow(any(), any(), any(), any(), any(), anyString());
        verify(notificationService, times(1)).emitStudyChanged(eq(studyUuid), eq(nodeUuid), eq(rootNetworkUuid), anyString());
    }

    private void testRerunLoadFlowSecurityNode(boolean withRatioTapChangers) {
        InvalidateNodeTreeParameters expectedInvalidationParameters = InvalidateNodeTreeParameters.builder()
            .invalidationMode(InvalidateNodeTreeParameters.InvalidationMode.ALL)
            .computationsInvalidationMode(InvalidateNodeTreeParameters.ComputationsInvalidationMode.PRESERVE_LOAD_FLOW_RESULTS)
            .build();

        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        UUID groupUuidToInvalidate = UUID.randomUUID();
        invalidateNodeInfos.addGroupUuids(List.of(groupUuidToInvalidate));

        RerunLoadFlowInfos expectedWorkflowInfo = RerunLoadFlowInfos.builder()
            .userId(userId)
            .withRatioTapChangers(withRatioTapChangers)
            .loadflowResultUuid(loadflowResultUuid)
            .build();

        // mock call returning values
        doNothing().when(networkModificationTreeService).buildNode(any(UUID.class), any(UUID.class), any(UUID.class), eq(userId), any(RerunLoadFlowInfos.class));
        when(rootNetworkService.getNetworkUuid(rootNetworkUuid)).thenReturn(networkUuid);
        doReturn(loadflowResultUuid).when(loadFlowRestService).createRunningStatus();

        when(rootNetworkNodeInfoService.getRootNetworkNodeInfo(any(UUID.class), any(UUID.class)))
            .thenReturn(Optional.of(RootNetworkNodeInfoEntity.builder().nodeBuildStatus(new NodeBuildStatusEmbeddable(BuildStatus.NOT_BUILT, BuildStatus.NOT_BUILT)).build()));
        when(rootNetworkNodeInfoService.invalidateRootNetworkNode(any(UUID.class), any(UUID.class), any(InvalidateNodeTreeParameters.class))).thenReturn(new InvalidateNodeInfos());
        when(rootNetworkNodeInfoService.invalidateRootNetworkNodes(any(UUID.class), anyList(), any(InvalidateNodeTreeParameters.class))).thenReturn(invalidateNodeInfos);

        // execute loadflow rerun
        loadFlowService.rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);

        // node invalidation
        verify(networkModificationTreeService, times(1))
            .invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, expectedInvalidationParameters, false);
        verify(networkModificationService, times(1)).deleteIndexedModifications(invalidateNodeInfos.getGroupUuids(), networkUuid);
        verify(notificationService, times(1 /* only all computation without lf */))
            .emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, UPDATE_TYPE_ALL_COMPUTATION_STATUS_WITHOUT_LOADFLOW);

        // node build
        ArgumentCaptor<RerunLoadFlowInfos> rerunLoadFlowWorkflowInfosArgumentCaptor = ArgumentCaptor.forClass(RerunLoadFlowInfos.class);
        verify(networkModificationTreeService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), eq(rootNetworkUuid), eq(userId), rerunLoadFlowWorkflowInfosArgumentCaptor.capture());

        // check workflow infos
        assertThat(rerunLoadFlowWorkflowInfosArgumentCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedWorkflowInfo);
    }
}
