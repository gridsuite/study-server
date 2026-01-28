/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.io.ByteStreams;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okio.Buffer;
import org.assertj.core.api.ThrowableAssert;
import org.assertj.core.api.ThrowableAssertAlternative;
import org.gridsuite.study.server.dto.Report;
import org.gridsuite.study.server.error.StudyBusinessErrorCode;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.StudyServerExecutionService;
import org.gridsuite.study.server.utils.assertions.Assertions;
import org.junit.platform.commons.util.StringUtils;
import org.mockito.stubbing.Answer;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.gridsuite.study.server.notification.NotificationService.ALL_COMPUTATION_STATUS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public final class TestUtils {

    //output destinations
    public static final String STUDY_UPDATE_DESTINATION = "study.update";
    public static final String ELEMENT_UPDATE_DESTINATION = "element.update";

    private static final long TIMEOUT = 100;
    public static final String USER_DEFAULT_PROFILE_JSON = """
        {
            "id":null,
            "name":"default profile",
            "loadFlowParameterId":null,
            "securityAnalysisParameterId":null,
            "sensitivityAnalysisParameterId":null,
            "shortcircuitParameterId":null,
            "voltageInitParameterId":null,
            "allLinksValid":null,
            "maxAllowedCases":20,
            "maxAllowedBuilds":20,
            "spreadsheetConfigCollectionId":null,
            "networkVisualizationParameterId":null,
            "diagramConfigId":null
        }""";

    private final RootNetworkRepository rootNetworkRepository;

    public TestUtils(RootNetworkRepository rootNetworkRepository) {
        this.rootNetworkRepository = rootNetworkRepository;
    }

    public UUID getOneRootNetworkUuid(UUID studyUuid) {
        return getOneRootNetwork(studyUuid).getId();
    }

    public RootNetworkEntity getOneRootNetwork(UUID studyUuid) {
        return rootNetworkRepository.findAllWithInfosByStudyId(studyUuid).get(0);
    }

    public UUID getNetworkUuid(UUID studyUuid) {
        return getOneRootNetwork(studyUuid).getNetworkUuid();
    }

    public static Set<RequestWithBody> getRequestsWithBodyDone(int n, MockWebServer server) {
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

    public static void assertRequestMatches(String method, String path, MockWebServer server) {
        RecordedRequest recordedRequest;
        try {
            recordedRequest = Objects.requireNonNull(server.takeRequest(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
        assertEquals(method, recordedRequest.getMethod());
        assertNotNull(recordedRequest.getPath());
        assertTrue(recordedRequest.getPath().matches(path));
    }

    public static Set<String> getRequestsDone(int n, MockWebServer server) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                return Objects.requireNonNull(server.takeRequest(TIMEOUT, TimeUnit.MILLISECONDS)).getPath();
            } catch (InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            }
        }).collect(Collectors.toSet());
    }

    //TODO: WHY is casename now mandatory ? there was @NotNull before as well
    public static StudyEntity createDummyStudy(UUID networkUuid, String networkId, UUID caseUuid, String caseFormat, String caseName, UUID importReportUuid,
                                               UUID loadFlowParametersUuid,
                                               UUID shortCircuitParametersUuid,
                                               UUID voltageInitParametersUuid,
                                               UUID securityAnalysisParametersUuid,
                                               UUID sensitivityParametersUuid,
                                               boolean applyModifications) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID())
            .loadFlowParametersUuid(loadFlowParametersUuid)
            .shortCircuitParametersUuid(shortCircuitParametersUuid)
            .voltageInitParametersUuid(voltageInitParametersUuid)
            .securityAnalysisParametersUuid(securityAnalysisParametersUuid)
            .sensitivityAnalysisParametersUuid(sensitivityParametersUuid)
            .voltageInitParameters(new StudyVoltageInitParametersEntity(applyModifications))
            .build();
        RootNetworkEntity rootNetworkEntity = RootNetworkEntity.builder().id(UUID.randomUUID()).name("rootNetworkName").tag("dum").caseFormat(caseFormat).caseUuid(caseUuid).caseName(caseName).networkId(networkId).networkUuid(networkUuid).reportUuid(importReportUuid).build();
        studyEntity.addRootNetwork(rootNetworkEntity);

        return studyEntity;
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, String networkId, UUID caseUuid, String caseFormat, String caseName, UUID reportUuid, UUID networkVisuParametersUuid) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID())
                .networkVisualizationParametersUuid(networkVisuParametersUuid)
                .build();
        RootNetworkEntity rootNetworkEntity = RootNetworkEntity.builder().id(UUID.randomUUID()).name("rootNetworkName").tag("dum").caseFormat(caseFormat).caseUuid(caseUuid).caseName(caseName).networkId(networkId).networkUuid(networkUuid).reportUuid(reportUuid).build();
        studyEntity.addRootNetwork(rootNetworkEntity);
        return studyEntity;
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, String networkId, UUID caseUuid, String caseFormat, String caseName, UUID spreadsheetCollectionUuid) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID())
                .spreadsheetConfigCollectionUuid(spreadsheetCollectionUuid)
                .build();
        RootNetworkEntity rootNetworkEntity = RootNetworkEntity.builder().id(UUID.randomUUID()).name("rootNetworkName").tag("dum").caseFormat(caseFormat).caseUuid(caseUuid).caseName(caseName).networkId(networkId).networkUuid(networkUuid).build();
        studyEntity.addRootNetwork(rootNetworkEntity);
        return studyEntity;
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, String networkId, UUID caseUuid, String caseFormat, String caseName, UUID reportUuid,
                                               UUID loadFlowParametersUuid,
                                               UUID shortCircuitParametersUuid,
                                               UUID securityAnalysisParametersUuid,
                                               UUID sensitivityParametersUuid,
                                               UUID stateEstimationParametersUuid,
                                               UUID pccMinParametersUuid) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID())
                .loadFlowParametersUuid(loadFlowParametersUuid)
                .shortCircuitParametersUuid(shortCircuitParametersUuid)
                .securityAnalysisParametersUuid(securityAnalysisParametersUuid)
                .sensitivityAnalysisParametersUuid(sensitivityParametersUuid)
                .stateEstimationParametersUuid(stateEstimationParametersUuid)
                .pccMinParametersUuid(pccMinParametersUuid)
                .build();
        RootNetworkEntity rootNetworkEntity = RootNetworkEntity.builder().id(UUID.randomUUID()).name("rootNetworkName").tag("dum").caseFormat(caseFormat).caseUuid(caseUuid).caseName(caseName).networkId(networkId).networkUuid(networkUuid).reportUuid(reportUuid).build();
        studyEntity.addRootNetwork(rootNetworkEntity);
        return studyEntity;
    }

    public static StudyEntity createDummyStudy(UUID networkUuid, UUID caseUuid, String caseName, String caseFormat, UUID reportUuid) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID())
//            .shortCircuitParametersUuid(UUID.randomUUID())
            .build();
        studyEntity.addRootNetwork(RootNetworkEntity.builder().id(UUID.randomUUID()).name("rootNetworkName").tag("dum").caseFormat(caseFormat).caseUuid(caseUuid).caseName(caseName).networkId("netId").networkUuid(networkUuid).reportUuid(reportUuid).build());

        return studyEntity;
    }

    public static NetworkModificationNode createModificationNodeInfo(String name) {
        return NetworkModificationNode.builder()
            .name(name)
            .description("")
            .modificationGroupUuid(UUID.randomUUID())
            .children(Collections.emptyList()).build();
    }

    public static void assertQueuesEmptyThenClear(List<String> destinations, OutputDestination output) {
        try {
            destinations.forEach(destination -> assertNull(output.receive(TIMEOUT, destination), "Should not be any messages in queue " + destination + " : "));
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }

    public static void assertServerRequestsEmptyThenShutdown(MockWebServer server) throws UncheckedInterruptedException {
        try {
            assertNull(getRequestsDone(1, server), "Should not be any http requests : ");
        } catch (NullPointerException e) {
            // ignoring
        }
    }

    public static void assertWiremockServerRequestsEmptyThenShutdown(WireMockServer wireMockServer) throws UncheckedInterruptedException {
        try {
            wireMockServer.checkForUnmatchedRequests(); // requests no matched ? (it returns an exception if a request was not matched by wireMock, but does not complain if it was not verified by 'verify')
            var requests = wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()));
            assertEquals(0, requests.size(), "Uncatch WireMock requests found:\n" + requests.stream()
                .map(r -> "URL: " + r.getUrl() + ", Method: " + r.getMethod() + ", Body: " + r.getBodyAsString() + ", Params: " + r.getQueryParams())
                .collect(Collectors.joining("\n")));
        } finally {
            wireMockServer.stop();
        }
    }

    public static void assertWiremockServerRequestsEmptyThenClear(WireMockServer wireMockServer) throws UncheckedInterruptedException {
        try {
            wireMockServer.checkForUnmatchedRequests();
            assertEquals(0, wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl())).size());
        } finally {
            wireMockServer.resetAll();
        }
    }

    public static String resourceToString(String resource) throws IOException {
        String content = new String(ByteStreams.toByteArray(TestUtils.class.getResourceAsStream(resource)), StandardCharsets.UTF_8);
        return StringUtils.replaceWhitespaceCharacters(content, "");
    }

    public static Buffer getBinaryAsBuffer(byte[] binary) {
        Buffer buf = new Buffer();
        buf.write(binary);
        return buf;
    }

    public static void checkReports(List<Report> reports, List<Report> expectedReports) {
        reports.forEach(r -> assertThat(r, new MatcherReport(expectedReports.get(reports.indexOf(r)))));
    }

    public static void assertStudyException(ThrowableAssert.ThrowingCallable throwingCallable, StudyBusinessErrorCode type, String message) {
        ThrowableAssertAlternative<StudyException> throwableAssert = Assertions.assertThatExceptionOfType(StudyException.class)
                .isThrownBy(throwingCallable);
        throwableAssert.extracting("errorCode").isEqualTo(type);
        Optional.ofNullable(message).ifPresent(throwableAssert::withMessage);
    }

    public static void synchronizeStudyServerExecutionService(StudyServerExecutionService studyServerExecutionService) {
        doAnswer((Answer<CompletableFuture>) invocation -> {
            CompletableFuture completableFuture = (CompletableFuture) invocation.callRealMethod();
            try {
                completableFuture.get();
            } catch (ExecutionException e) { // in complete async mode we ignore exception (log only)
            }
            return completableFuture;
        }).when(studyServerExecutionService).runAsync(any(Runnable.class));
    }

    public static void checkUpdateTypeMessageReceived(UUID studyUuid, UUID nodeUuid, String updateType, OutputDestination output, String destination) {
        // assert that the broker message has been sent for updating updateType
        Message<byte[]> messageStatus = output.receive(TIMEOUT, destination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    public static void checkUpdateStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, OutputDestination output) {
        ALL_COMPUTATION_STATUS.forEach(computationStatus -> checkUpdateTypeMessageReceived(studyUuid, nodeUuid, computationStatus, output, STUDY_UPDATE_DESTINATION));
    }
}
