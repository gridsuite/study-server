/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import java.util.concurrent.Semaphore;

/**
 * Class that implements an action we want to execute after mocking an API call
 * See 'Post-serve actions' in https://wiremock.org/docs/extending-wiremock/
 */
public class SemaphoreReleaseAction extends PostServeAction {

    public static final String POST_ACTION_SEMAPHORE_RELEASE = "semaphore_release";

    private final Semaphore semaphore;

    public SemaphoreReleaseAction(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    @Override
    public String getName() {
        return POST_ACTION_SEMAPHORE_RELEASE;
    }

    @Override
    public void doAction(ServeEvent serveEvent, Admin admin, Parameters parameters) {
        semaphore.release();
    }
}

