/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.QuotaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 *
 * Unit tests for {@link NotificationService}, focusing on the quota change notification
 * introduced alongside {@link QuotaType}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    private static final String USER_ID = "userId";

    @Mock
    private StreamBridge updatePublisher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(updatePublisher, new ObjectMapper());
    }

    @Test
    void testEmitQuotaChange() {
        notificationService.emitQuotaChange(USER_ID, QuotaType.SHORT_CIRCUIT);

        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(updatePublisher).send(eq("publishQuotaUpdate-out-0"), messageCaptor.capture());

        Message<String> message = messageCaptor.getValue();
        assertThat(message.getHeaders().get(NotificationService.HEADER_USER_ID)).isEqualTo(USER_ID);
        assertThat(message.getHeaders().get(NotificationService.HEADER_QUOTA_TYPE)).isEqualTo(QuotaType.SHORT_CIRCUIT);
    }
}
