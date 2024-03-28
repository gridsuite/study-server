/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

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

    public VoltageInitResultConsumer(StudyService studyService, ConsumerService consumerService) {
        this.studyService = studyService;
        this.consumerService = consumerService;
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitResult() {
        return message -> {
            consumerService.consumeCalculationResult(message, VOLTAGE_INITIALIZATION);
            consumerService.getStudyUuid(message).ifPresent(studyUuid -> {
                StudyVoltageInitParameters parametersUuid = studyService.getVoltageInitParameters(studyUuid);
                if (parametersUuid.isApplyModifications()) {
                    consumerService.getNodeReceiver(message).ifPresent(nodeReceiver -> {
                        String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
                        studyService.copyVoltageInitModifications(studyUuid, nodeReceiver.getNodeUuid(), userId);
                    });
                }
            });
        };
    }
}
