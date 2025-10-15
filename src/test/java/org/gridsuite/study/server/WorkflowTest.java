/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
import org.gridsuite.study.server.dto.workflow.WorkflowType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.ConsumerService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class WorkflowTest {

    UUID studyUuid = UUID.randomUUID();
    UUID nodeUuid = UUID.randomUUID();
    UUID rootNetworkUuid = UUID.randomUUID();
    UUID loadflowResultUuid = UUID.randomUUID();
    String userId = "userId";

    @Autowired
    ConsumerService consumerService;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private StudyService studyService;
    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testConsumeBuildResultInRerunLoadFlowWorkflow() throws JsonProcessingException {
        boolean withRatioTapChangers = true;
        NetworkModificationResult networkModificationResult = new NetworkModificationResult();
        NodeReceiver nodeReceiver = new NodeReceiver(nodeUuid, rootNetworkUuid);
        RerunLoadFlowInfos rerunLoadFlowInfos = RerunLoadFlowInfos.builder()
            .loadflowResultUuid(loadflowResultUuid)
            .withRatioTapChangers(withRatioTapChangers)
            .userId(userId)
            .build();

        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_RECEIVER, objectMapper.writeValueAsString(nodeReceiver));
        headers.put(HEADER_WORKFLOW_TYPE, WorkflowType.RERUN_LOAD_FLOW.name());
        headers.put(HEADER_WORKFLOW_INFOS, objectMapper.writeValueAsString(rerunLoadFlowInfos));
        MessageHeaders messageHeaders = new MessageHeaders(headers);

        when(networkModificationTreeService.getStudyUuidForNodeId(nodeUuid)).thenReturn(studyUuid);

        // execute consume
        consumerService.consumeBuildResult().accept(MessageBuilder.createMessage(networkModificationResult, messageHeaders));

        // check loadflow is actually ran after build is completed
        verify(studyService, times(1)).handleBuildSuccess(studyUuid, nodeUuid, rootNetworkUuid, networkModificationResult);
        verify(studyService, times(1)).sendLoadflowRequest(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, false, userId);
    }

    @Test
    void testConsumeBuildFailedInRerunLoadFlowWorkflow() throws JsonProcessingException {
        boolean withRatioTapChangers = true;
        NodeReceiver nodeReceiver = new NodeReceiver(nodeUuid, rootNetworkUuid);
        RerunLoadFlowInfos rerunLoadFlowInfos = RerunLoadFlowInfos.builder()
            .loadflowResultUuid(loadflowResultUuid)
            .withRatioTapChangers(withRatioTapChangers)
            .userId(userId)
            .build();

        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_RECEIVER, objectMapper.writeValueAsString(nodeReceiver));
        String errorMessage = "Build failure";
        headers.put(HEADER_ERROR_MESSAGE, errorMessage);
        headers.put(HEADER_WORKFLOW_TYPE, WorkflowType.RERUN_LOAD_FLOW.name());
        headers.put(HEADER_WORKFLOW_INFOS, objectMapper.writeValueAsString(rerunLoadFlowInfos));
        MessageHeaders messageHeaders = new MessageHeaders(headers);

        when(networkModificationTreeService.getStudyUuidForNodeId(nodeUuid)).thenReturn(studyUuid);

        // execute consume
        consumerService.consumeBuildFailed().accept(MessageBuilder.createMessage("", messageHeaders));

        // check loadflow is actually ran after build is completed
        verify(notificationService, times(1)).emitNodeBuildFailed(studyUuid, nodeUuid, rootNetworkUuid, errorMessage);
        verify(studyService, times(1)).deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, loadflowResultUuid);
    }
}
