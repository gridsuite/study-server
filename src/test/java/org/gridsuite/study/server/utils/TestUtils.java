/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.loadflow.LoadFlowParameters;
import okhttp3.mockwebserver.MockWebServer;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.ShortCircuitService;
import org.springframework.cloud.stream.binder.test.OutputDestination;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
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
                return Objects.requireNonNull(server.takeRequest(TIMEOUT, TimeUnit.MILLISECONDS)).getPath();
            } catch (InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            }
        }).collect(Collectors.toSet());
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, UUID caseUuid, String caseFormat, String loadflowProvider, LoadFlowParametersEntity loadFlowParametersEntity, ShortCircuitParametersEntity shortCircuitParametersEntity) {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat(caseFormat).caseUuid(caseUuid)
            .networkId("netId")
            .networkUuid(networkUuid)
            .loadFlowProvider(loadflowProvider)
            .loadFlowParameters(loadFlowParametersEntity)
            .shortCircuitParameters(shortCircuitParametersEntity)
            .build();
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, UUID caseUuid, String caseName, String caseFormat, String loadFlowProvider) {
        LoadFlowParametersEntity loadFlowParametersEntity = LoadFlowParametersEntity.builder()
            .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
            .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
            .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
            .readSlackBus(true)
            .distributedSlack(true)
            .dcUseTransformerRatio(true)
            .hvdcAcEmulation(true)
            .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters());
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat(caseFormat).caseUuid(caseUuid)
            .caseName(caseName)
            .networkId("netId")
            .networkUuid(networkUuid)
            .loadFlowProvider(loadFlowProvider)
            .loadFlowParameters(loadFlowParametersEntity)
            .shortCircuitParameters(defaultShortCircuitParametersEntity)
            .build();
    }

    public static NetworkModificationNode createModificationNodeInfo(String name, UUID reportUuid) {
        return NetworkModificationNode.builder()
            .name(name)
            .description("")
            .modificationGroupUuid(UUID.randomUUID())
            .variantId(UUID.randomUUID().toString())
            .reportUuid(reportUuid)
            .loadFlowStatus(LoadFlowStatus.NOT_DONE)
            .loadFlowResult(null)
            .securityAnalysisResultUuid(UUID.randomUUID())
            .sensitivityAnalysisResultUuid(UUID.randomUUID())
            .buildStatus(BuildStatus.NOT_BUILT)
            .children(Collections.emptyList()).build();
    }

    public static void assertQueuesEmptyThenClear(List<String> destinations, OutputDestination output) {
        try {
            destinations.forEach(destination -> assertNull("Should not be any messages in queue " + destination + " : ", output.receive(TIMEOUT, destination)));
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

    public static void assertWiremockServerRequestsEmptyThenShutdown(WireMockServer wireMockServer) throws UncheckedInterruptedException, IOException {
        try {
            wireMockServer.checkForUnmatchedRequests(); // requests no matched ? (it returns an exception if a request was not matched by wireMock, but does not complain if it was not verified by 'verify')
            assertEquals(0, wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl())).size()); // requests no verified ?
        } finally {
            wireMockServer.shutdown();
        }
    }
}
