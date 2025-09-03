/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

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
        executorService = Executors.newCachedThreadPool();
    }

    @PreDestroy
    private void preDestroy() {
        executorService.shutdown();
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executorService);
    }

    public CompletableFuture<Void> runAsyncAndComplete(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executorService).whenCompleteAsync((r, t) -> logAsyncError(t));
    }

    private void logAsyncError(Throwable e) {
        if (e != null) {
            LOGGER.error(e.toString(), e);
        }
    }
}
