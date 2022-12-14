/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.messaging.support.MessageBuilder;

import static org.gridsuite.study.server.service.ConsumerService.HEADER_RECEIVER;
import static org.gridsuite.study.server.service.NetworkModificationService.QUERY_PARAM_RECEIVER;

/**
 * Class that implements an action we want to execute after mocking an API call
 * See 'Post-serve actions' in https://wiremock.org/docs/extending-wiremock/
 */
public class SendInput extends PostServeAction {

    public static final String POST_ACTION_SEND_INPUT = "send-input";

    private final InputDestination input;

    public SendInput(InputDestination input) {
        this.input = input;
    }

    @Override
    public String getName() {
        return POST_ACTION_SEND_INPUT;
    }

    @Override
    public void doAction(ServeEvent serveEvent, Admin admin, Parameters parameters) {
        String payload = parameters.get("payload").toString();
        String destination = parameters.get("destination").toString();

        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(payload);
        QueryParameter receiverParam = serveEvent.getRequest().getQueryParams().get(QUERY_PARAM_RECEIVER);
        if (receiverParam != null) {
            messageBuilder.setHeader(HEADER_RECEIVER, receiverParam.firstValue());
        }

        input.send(messageBuilder.build(), destination);
    }
}

