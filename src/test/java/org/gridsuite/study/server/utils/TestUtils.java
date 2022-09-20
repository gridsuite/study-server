/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.LoadflowService;
import org.gridsuite.study.server.service.ShortCircuitService;
import org.springframework.cloud.stream.binder.test.OutputDestination;

import com.powsybl.commons.exceptions.UncheckedInterruptedException;

import okhttp3.mockwebserver.MockWebServer;

public final class TestUtils {

    private static final long TIMEOUT = 100;

    private TestUtils() {

    }

    public static Set<RequestWithBody> getRequestsWithBodyDone(int n, MockWebServer server) throws UncheckedInterruptedException {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                var request = server.takeRequest(TIMEOUT, TimeUnit.MILLISECONDS);
                if (request == null) {
                    throw new AssertionError("Expected " + n + " requests, got only " + i);
                }
                return new RequestWithBody(request.getPath(), request.getBody().readUtf8());
            } catch (InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            }
        }).collect(Collectors.toSet());
    }

    public static Set<String> getRequestsDone(int n, MockWebServer server) throws UncheckedInterruptedException {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                return server.takeRequest(TIMEOUT, TimeUnit.MILLISECONDS).getPath();
            } catch (InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            }
        }).collect(Collectors.toSet());
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, UUID caseUuid, String caseFormat, String loadflowProvider, LoadFlowParametersEntity loadFlowParametersEntity) {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat(caseFormat).caseUuid(caseUuid)
            .date(LocalDateTime.now())
            .networkId("netId")
            .networkUuid(networkUuid)
            .userId("userId")
            .loadFlowProvider(loadflowProvider)
            .loadFlowParameters(loadFlowParametersEntity)
            .shortCircuitParameters(ShortCircuitService.toEntity(new ShortCircuitParameters()))
            .build();
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, UUID caseUuid, String caseFormat, ShortCircuitParametersEntity shortCircuitParametersEntity) {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat(caseFormat).caseUuid(caseUuid)
                .date(LocalDateTime.now())
                .networkId("netId")
                .networkUuid(networkUuid)
                .userId("userId")
                .loadFlowParameters(LoadflowService.toEntity(LoadFlowParameters.load()))
                .shortCircuitParameters(shortCircuitParametersEntity)
                .build();
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, UUID caseUuid, String caseName, String caseFormat, String loadflowProvider, LoadFlowParametersEntity loadFlowParametersEntity, ShortCircuitParametersEntity shortCircuitParametersEntity) {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat(caseFormat).caseUuid(caseUuid)
            .caseName(caseName)
            .date(LocalDateTime.now())
            .networkId("netId")
            .networkUuid(networkUuid)
            .userId("userId")
            .loadFlowProvider(loadflowProvider)
            .loadFlowParameters(loadFlowParametersEntity)
            .shortCircuitParameters(shortCircuitParametersEntity)
            .build();
    }

    public static void assertQueuesEmptyThenClear(List<String> destinations, OutputDestination output) {
        try {
            destinations.forEach(destination -> {
                assertNull("Should not be any messages in queue " + destination + " : ", output.receive(TIMEOUT, destination));
            });
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }

    public static void assertServerRequestsEmptyThenShutdown(MockWebServer server) throws UncheckedInterruptedException, IOException {
        Set<String> httpRequest = null;

        try {
            httpRequest = TestUtils.getRequestsDone(1, server);
        } catch (NullPointerException e) {
            // ignoring
        } finally {
            server.shutdown();
        }

        assertNull("Should not be any http requests : ", httpRequest);
    }
}
