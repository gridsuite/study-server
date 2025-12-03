/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class StudyServerExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyServerExecutionService.class);

    private ExecutorService executorService;

    @PostConstruct
    private void postConstruct() {
        ContextSnapshotFactory snapshotFactory = ContextSnapshotFactory.builder().build();
        executorService = ContextExecutorService.wrap(Executors.newCachedThreadPool(),
            snapshotFactory::captureAll);
    }

    @PreDestroy
    private void preDestroy() {
        executorService.shutdown();
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture
            .runAsync(runnable, executorService)
            .whenComplete((r, t) -> {
                if (LOGGER.isErrorEnabled() && t != null) {
                    LOGGER.error(t.toString(), t);
                }
            });
    }
}
