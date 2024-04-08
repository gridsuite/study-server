/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.notification.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Consumer;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_REACTIVE_SLACKS_OVER_THRESHOLD_LABEL;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Service
public class VoltageInitResultConsumer {

    private final StudyService studyService;

    private final ConsumerService consumerService;

    private final NotificationService notificationService;

    public VoltageInitResultConsumer(StudyService studyService,
                                     ConsumerService consumerService,
                                     NotificationService notificationService) {
        this.studyService = studyService;
        this.consumerService = consumerService;
        this.notificationService = notificationService;
    }

    private void checkReactiveSlacksOverThreshold(Message<String> msg, UUID studyUuid) {
        consumerService.getNodeReceiver(msg).ifPresent(nodeReceiver -> {
            String alertLabel = msg.getHeaders().get(HEADER_REACTIVE_SLACKS_OVER_THRESHOLD_LABEL, String.class);
            if (!StringUtils.isBlank(alertLabel)) {
                String userId = msg.getHeaders().get(HEADER_USER_ID, String.class);
                Double alertThreshold = msg.getHeaders().get(HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE, Double.class);
                notificationService.emitVoltageInitReactiveSlacksAlert(studyUuid, nodeReceiver.getNodeUuid(), userId, alertLabel, alertThreshold);
            }
        });
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitResult() {
        return message -> {
            consumerService.consumeCalculationResult(message, VOLTAGE_INITIALIZATION);
            consumerService.getStudyUuid(message).ifPresent(studyUuid -> {
                checkReactiveSlacksOverThreshold(message, studyUuid);
                if (studyService.shouldApplyModifications(studyUuid)) {
                    consumerService.getNodeReceiver(message).ifPresent(nodeReceiver -> {
                        String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
                        studyService.copyVoltageInitModifications(studyUuid, nodeReceiver.getNodeUuid(), userId);
                    });
                }
            });
        };
    }
}
