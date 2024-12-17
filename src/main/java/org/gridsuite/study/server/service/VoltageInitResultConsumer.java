/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.AlertLevel;
import org.gridsuite.study.server.notification.dto.StudyAlert;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Service
public class VoltageInitResultConsumer {

    private final StudyService studyService;

    private final ConsumerService consumerService;

    private final NotificationService notificationService;

    public static final String HEADER_REACTIVE_SLACKS_OVER_THRESHOLD = "REACTIVE_SLACKS_OVER_THRESHOLD";
    public static final String HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE = "reactiveSlacksThreshold";

    public VoltageInitResultConsumer(StudyService studyService,
                                     ConsumerService consumerService,
                                     NotificationService notificationService) {
        this.studyService = studyService;
        this.consumerService = consumerService;
        this.notificationService = notificationService;
    }

    private void checkReactiveSlacksOverThreshold(Message<String> msg, UUID studyUuid) {
        consumerService.getNodeReceiver(msg).ifPresent(nodeReceiver -> {
            Boolean alert = msg.getHeaders().get(HEADER_REACTIVE_SLACKS_OVER_THRESHOLD, Boolean.class);
            if (Boolean.TRUE.equals(alert)) {
                String userId = msg.getHeaders().get(HEADER_USER_ID, String.class);
                Double alertThreshold = msg.getHeaders().get(HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE, Double.class);
                notificationService.emitStudyAlert(studyUuid, nodeReceiver.getNodeUuid(), nodeReceiver.getRootNetworkUuid(), userId, new StudyAlert(AlertLevel.WARNING, HEADER_REACTIVE_SLACKS_OVER_THRESHOLD, Map.of("threshold", alertThreshold.toString())));
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
                        studyService.copyVoltageInitModifications(studyUuid, nodeReceiver.getNodeUuid(), nodeReceiver.getRootNetworkUuid(), userId);
                    });
                }
            });
        };
    }
}
