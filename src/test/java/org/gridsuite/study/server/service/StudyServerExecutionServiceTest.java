/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed Benrejeb <mohamed.ben-rejeb at rte-france.com>
 */
class StudyServerExecutionServiceTest {

    private static final String THREAD_LOCAL_KEY = "study-server-thread-local";
    private final ThreadLocal<String> threadLocal = new ThreadLocal<>();

    @AfterEach
    void tearDown() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(THREAD_LOCAL_KEY);
        threadLocal.remove();
    }

    @Test
    void runAsyncPropagatesContext() throws Exception {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<String>() {
            @Override
            public String key() {
                return THREAD_LOCAL_KEY;
            }

            @Override
            public String getValue() {
                return threadLocal.get();
            }

            @Override
            public void setValue(String value) {
                threadLocal.set(value);
            }

            @Override
            public void setValue() {
                threadLocal.remove();
            }
        });

        StudyServerExecutionService service = new StudyServerExecutionService();
        Method postConstruct = StudyServerExecutionService.class.getDeclaredMethod("postConstruct");
        postConstruct.setAccessible(true);
        postConstruct.invoke(service);

        Field executorField = StudyServerExecutionService.class.getDeclaredField("executorService");
        executorField.setAccessible(true);

        ExecutorService executorService = (ExecutorService) executorField.get(service);
        assertInstanceOf(ContextExecutorService.class, executorService, "executor should be wrapped in ContextExecutorService");

        threadLocal.set("expected-context");
        assertEquals("expected-context", executorService.submit(threadLocal::get).get());
    }
}
