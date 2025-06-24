/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.StudyController;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.InvalidateNodeInfos;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class LoadFLowUnitTest {

    @Autowired
    private StudyController controller;

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
    private UserAdminService userAdminService;
    @MockBean
    private NotificationService notificationService;

    @Test
    void testRunLoadFlow() {
        when(rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW)).thenReturn(null);
        doReturn(loadflowResultUuid).when(studyService).sendLoadflowRequest(any(), any(), any(), any(), anyBoolean(), anyString());

        controller.runLoadFlow(studyUuid, nodeUuid, rootNetworkUuid, false, userId);

        verify(studyService, times(1)).sendLoadflowRequest(any(), any(), any(), any(), anyBoolean(), anyString());
    }

    @Test
    void testRunLoadFlowWithExistingResult() {
        UUID previousResultUuid = UUID.randomUUID();
        when(rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW)).thenReturn(previousResultUuid);

        doNothing().when(studyService).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid);

        doReturn(loadflowResultUuid).when(studyService).createLoadflowRunningStatus(studyUuid, nodeUuid, rootNetworkUuid, false);
        doReturn(loadflowResultUuid).when(studyService).rerunLoadflow(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, false, userId);

        controller.runLoadFlow(studyUuid, rootNetworkUuid, nodeUuid, false, userId);

        verify(studyService, times(1)).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, previousResultUuid);
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

        RerunLoadFlowInfos expectedWorkflowInfo = RerunLoadFlowInfos.builder()
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
        verify(notificationService, times(9)).emitStudyChanged(eq(studyUuid), eq(nodeUuid), eq(rootNetworkUuid), anyString());

        // node build
        ArgumentCaptor<RerunLoadFlowInfos> rerunLoadFlowWorkflowInfosArgumentCaptor = ArgumentCaptor.forClass(RerunLoadFlowInfos.class);
        verify(networkModificationService, times(1)).buildNode(eq(nodeUuid), eq(rootNetworkUuid), eq(buildInfos), rerunLoadFlowWorkflowInfosArgumentCaptor.capture());

        // check workflow infos
        assertThat(rerunLoadFlowWorkflowInfosArgumentCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedWorkflowInfo);
    }
}
