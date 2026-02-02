/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.loadflow;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.controller.StudyController;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.InvalidateNodeInfos;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.utils.TestUtils.ALL_COMPUTATION_STATUS;
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
    private StudyController controller;

    @MockitoSpyBean
    private StudyService studyService;

    UUID studyUuid = UUID.randomUUID();
    UUID nodeUuid = UUID.randomUUID();
    UUID rootNetworkUuid = UUID.randomUUID();
    UUID networkUuid = UUID.randomUUID();
    String userId = "userId";

    UUID loadflowResultUuid = UUID.randomUUID();

    @MockitoBean
    RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private RootNetworkService rootNetworkService;
    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private LoadFlowService loadFlowService;
    @MockitoBean
    private NetworkService networkService;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    StudyRepository studyRepository;

    @MockitoSpyBean
    private StudyServerExecutionService studyServerExecutionService;

    @BeforeEach
    void setup() {
        synchronizeStudyServerExecutionService(studyServerExecutionService);
    }

    @Test
    void testRunLoadFlow() {
        when(rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW)).thenReturn(null);
        doNothing().when(studyService).sendLoadflowRequest(any(), any(), any(), any(), anyBoolean(), anyString());
        doNothing().when(studyService).assertCanRunOnConstructionNode(any(), any(), any(), any());

        controller.runLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, false, userId);

        verify(studyService, times(1)).sendLoadflowRequest(any(), any(), any(), any(), anyBoolean(), anyString());
        verify(studyService, times(1)).assertCanRunOnConstructionNode(any(), any(), any(), any());
    }

    @Test
    void testRunLoadFlowWithExistingResult() {
        UUID previousResultUuid = UUID.randomUUID();
        when(rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW)).thenReturn(previousResultUuid);
        doNothing().when(studyService).assertCanRunOnConstructionNode(eq(studyUuid), eq(nodeUuid), any(), any());

        doNothing().when(studyService).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid);
        doReturn(loadflowResultUuid).when(studyService).createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, false);
        doNothing().when(studyService).rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);

        controller.runLoadFlow(studyUuid, rootNetworkUuid, nodeUuid, false, userId);

        verify(studyService, times(1)).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid);
        verify(studyService, times(1)).createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, false);
        verify(studyService, times(1)).rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);
        verify(studyService, times(1)).assertCanRunOnConstructionNode(eq(studyUuid), eq(nodeUuid), any(), any());
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
        reset(studyService, networkModificationTreeService, networkModificationService, notificationService, loadFlowService, studyRepository);
        when(studyRepository.findById(studyUuid)).thenReturn(Optional.of(studyEntity));
        when(networkModificationTreeService.isSecurityNode(nodeUuid)).thenReturn(isSecurityNode);
        if (isSecurityNode) {
            testRerunLoadFlowSecurityNode(withRatioTapChangers);
        } else {
            testRerunLoadFlowConstructionNode(withRatioTapChangers);
        }
    }

    private void testRerunLoadFlowConstructionNode(boolean withRatioTapChangers) {
        studyService.rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);

        verify(loadFlowService, times(1)).runLoadFlow(any(), any(), any(), any(), any(), anyString());
        verify(notificationService, times(1)).emitStudyChanged(eq(studyUuid), eq(nodeUuid), eq(rootNetworkUuid), anyString());
    }

    private void testRerunLoadFlowSecurityNode(boolean withRatioTapChangers) {
        InvalidateNodeTreeParameters expectedInvalidationParameters = InvalidateNodeTreeParameters.builder()
            .invalidationMode(InvalidateNodeTreeParameters.InvalidationMode.ALL)
            .computationsInvalidationMode(InvalidateNodeTreeParameters.ComputationsInvalidationMode.PRESERVE_LOAD_FLOW_RESULTS)
            .withBlockedNode(true)
            .build();

        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        UUID groupUuidToInvalidate = UUID.randomUUID();
        invalidateNodeInfos.addGroupUuids(List.of(groupUuidToInvalidate));

        BuildInfos buildInfos = new BuildInfos();

        RerunLoadFlowInfos expectedWorkflowInfo = RerunLoadFlowInfos.builder()
            .userId(userId)
            .withRatioTapChangers(withRatioTapChangers)
            .loadflowResultUuid(loadflowResultUuid)
            .build();

        // mock call returning values
        when(networkModificationTreeService.invalidateNodeTree(nodeUuid, rootNetworkUuid, expectedInvalidationParameters)).thenReturn(invalidateNodeInfos);
        when(rootNetworkService.getNetworkUuid(rootNetworkUuid)).thenReturn(networkUuid);
        when(networkModificationTreeService.getBuildInfos(nodeUuid, rootNetworkUuid)).thenReturn(buildInfos);
        when(networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid)).thenReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        doReturn(loadflowResultUuid).when(loadFlowService).createRunningStatus();

        // execute loadflow rerun
        studyService.rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);

        // node invalidation
        verify(networkModificationTreeService, times(1)).invalidateNodeTree(nodeUuid, rootNetworkUuid, expectedInvalidationParameters);
        verify(networkModificationService, times(1)).deleteIndexedModifications(invalidateNodeInfos.getGroupUuids(), networkUuid);
        verify(notificationService, times(ALL_COMPUTATION_STATUS.size() - 1 /* except loadflow which is tested in PRESERVE_LOAD_FLOW_RESULTS mode */))
            .emitStudyChanged(eq(studyUuid), eq(nodeUuid), eq(rootNetworkUuid), anyString());

        // node build
        ArgumentCaptor<RerunLoadFlowInfos> rerunLoadFlowWorkflowInfosArgumentCaptor = ArgumentCaptor.forClass(RerunLoadFlowInfos.class);
        verify(networkModificationService, times(1)).buildNode(eq(nodeUuid), eq(rootNetworkUuid), eq(buildInfos), rerunLoadFlowWorkflowInfosArgumentCaptor.capture());

        // check workflow infos
        assertThat(rerunLoadFlowWorkflowInfosArgumentCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedWorkflowInfo);
    }
}
