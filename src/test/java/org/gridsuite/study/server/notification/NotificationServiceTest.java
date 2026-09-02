/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * @author Souissi Maissa <souissi.maissa at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String STUDY_UPDATE_DESTINATION = "publishStudyUpdate-out-0";

    @Mock
    private StreamBridge updatePublisher;
    @Captor
    private ArgumentCaptor<Message<String>> messageCaptor;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(updatePublisher, new ObjectMapper());
    }

    @Test
    void emitSharedElementUpdatedSendsMessageWithParentNodeAndModificationUuids() {
        UUID studyUuid = UUID.randomUUID();
        UUID parentNodeUuid = UUID.randomUUID();
        List<UUID> networkModificationUuids = List.of(UUID.randomUUID(), UUID.randomUUID());

        notificationService.emitSharedElementUpdated(studyUuid, parentNodeUuid, networkModificationUuids);

        verify(updatePublisher).send(org.mockito.ArgumentMatchers.eq(STUDY_UPDATE_DESTINATION), messageCaptor.capture());
        Message<String> message = messageCaptor.getValue();
        assertThat(message.getPayload()).isEmpty();
        assertThat(message.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.SHARED_ELEMENT_UPDATED)
                .containsEntry(NotificationService.HEADER_PARENT_NODE, parentNodeUuid)
                .containsEntry(NotificationService.HEADER_NETWORK_MODIFICATION_UUIDS, networkModificationUuids);
    }

    @Test
    void emitSharedElementUpdatedAcceptsEmptyModificationUuids() {
        UUID studyUuid = UUID.randomUUID();
        UUID parentNodeUuid = UUID.randomUUID();

        notificationService.emitSharedElementUpdated(studyUuid, parentNodeUuid, List.of());

        verify(updatePublisher).send(org.mockito.ArgumentMatchers.eq(STUDY_UPDATE_DESTINATION), messageCaptor.capture());
        Message<String> message = messageCaptor.getValue();
        assertThat(message.getHeaders())
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.SHARED_ELEMENT_UPDATED)
                .containsEntry(NotificationService.HEADER_PARENT_NODE, parentNodeUuid)
                .containsEntry(NotificationService.HEADER_NETWORK_MODIFICATION_UUIDS, List.of());
    }
}
