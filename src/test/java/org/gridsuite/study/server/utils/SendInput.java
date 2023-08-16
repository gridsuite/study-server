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

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RECEIVER;

/**
 * Class that implements an action we want to execute after mocking an API call
 * See 'Post-serve actions' in <a href="https://wiremock.org/docs/extending-wiremock/">...</a>
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
        Object payload = parameters.get("payload");
        String destination = parameters.get("destination").toString();
        CountDownLatch countDownLatch = (CountDownLatch) (parameters.get("latch"));
        List<String> paramsNotToPass = List.of("countDownLatch", "destination", "payload");

        MessageBuilder<?> messageBuilder = MessageBuilder.withPayload(payload);
        QueryParameter receiverParam = serveEvent.getRequest().getQueryParams().get(QUERY_PARAM_RECEIVER);
        if (receiverParam != null) {
            messageBuilder.setHeader(HEADER_RECEIVER, receiverParam.firstValue());
        }

        parameters.forEach((key, value) -> {
            if (!paramsNotToPass.contains(key)) {
                messageBuilder.setHeader(key, value);
            }
        });

        // Wiremock does not accept to send a request http in a post serve action
        // For that it is necessary to use the webhook extension which only sends a http request
        // This is not suitable for our case, i.e. java code that sends a request
        // That's why we do it in another thread
        new Thread(() -> {
            input.send(messageBuilder.build(), destination);
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
        }
        ).start();
    }
}

