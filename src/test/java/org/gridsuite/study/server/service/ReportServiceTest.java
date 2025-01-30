/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.Report;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_DEFAULT_NAME;
import static org.gridsuite.study.server.utils.TestUtils.checkReports;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class ReportServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportServiceTest.class);

    private static final UUID ROOT_NODE_REPORT_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_NODE_REPORT_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_CHILD_NODE1_REPORT_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_CHILD_NODE2_REPORT_UUID = UUID.randomUUID();
    private static final UUID NOT_FOUND_REPORT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private OutputDestination output;

    private static final String STUDY_UPDATE_DESTINATION = "study.update";

    private static final long TIMEOUT = 1000;
    @Autowired
    private RootNetworkRepository rootNetworkRepository;
    @Autowired
    private RootNetworkService rootNetworkService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setup(final MockWebServer server) {
        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        reportService.setReportServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows(JsonProcessingException.class)
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/v1/reports/.*\\?defaultName=.*")) {
                    String reportUuid = Objects.requireNonNull(request.getRequestUrl()).pathSegments().get(2);
                    String nodeUuid = request.getRequestUrl().queryParameter(QUERY_PARAM_REPORT_DEFAULT_NAME);
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(getNodeReport(UUID.fromString(reportUuid), nodeUuid)));
                } else if (path.matches("/v1/subreports/.*\\?severityLevels=.*")) {
                    String reportId = Objects.requireNonNull(request.getRequestUrl()).pathSegments().get(2);
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(getNodeReport(UUID.fromString(reportId), reportId)));
                } else if (path.matches("/v1/reports/" + NOT_FOUND_REPORT_UUID + "/duplicate")) {
                    return new MockResponse(HttpStatus.NOT_FOUND.value());
                } else if (path.matches("/v1/reports/" + MODIFICATION_CHILD_NODE1_REPORT_UUID + "/aggregated-severities")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(Set.of("INFO")));
                } else if (path.matches("/v1/reports/" + ROOT_NODE_REPORT_UUID + "/aggregated-severities")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(Set.of("WARN")));
                } else if (path.matches("/v1/reports/.*/aggregated-severities")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(Set.of("UNKNOWN")));
                } else {
                    LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                    return new MockResponse.Builder().code(HttpStatus.I_AM_A_TEAPOT.value()).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        TestUtils.assertQueuesEmptyThenClear(List.of(STUDY_UPDATE_DESTINATION), output);
        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    private RootNode createRoot() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.randomUUID(), UUID.randomUUID(), "caseName", "", ROOT_NODE_REPORT_UUID);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
    }

    @Test
    void testReport(final MockWebServer server) throws Exception {
        RootNode rootNode = createRoot();
        StudyEntity studyEntity = studyRepository.findById(rootNode.getStudyId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyEntity.getId());
        List<Report> expectedRootReports = List.of(getNodeReport(ROOT_NODE_REPORT_UUID, rootNode.getId().toString()));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), firstRootNetworkUuid, rootNode.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<Report> reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(1, reports.size());
        checkReports(reports, expectedRootReports);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        NetworkModificationNode node = networkModificationTreeService.createNode(studyEntity, rootNode.getId(), createModificationNodeInfo("Node1"), InsertMode.AFTER, null);
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);  // message for modification node creation
        List<Report> expectedNodeReports = List.of(getNodeReport(MODIFICATION_NODE_REPORT_UUID, node.getId().toString()));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), firstRootNetworkUuid, node.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        checkReports(reports, expectedNodeReports);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), firstRootNetworkUuid, node.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        final List<Report> expectedReports = Stream.concat(expectedRootReports.stream(), expectedNodeReports.stream()).toList();
        checkReports(reports, expectedReports);
        assertTrue(TestUtils.getRequestsDone(2, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @Test
    void testMultipleReport(final MockWebServer server) throws Exception {
        RootNode rootNode = createRoot();
        StudyEntity studyEntity = studyRepository.findById(rootNode.getStudyId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyEntity.getId());
        NetworkModificationNode node = networkModificationTreeService.createNode(studyEntity, rootNode.getId(), createModificationNodeInfo("Modification Node"), InsertMode.AFTER, null);
        NetworkModificationNode child1 = networkModificationTreeService.createNode(studyEntity, node.getId(), createModificationNodeInfo("Child 1"), InsertMode.AFTER, null);
        NetworkModificationNode child2 = networkModificationTreeService.createNode(studyEntity, node.getId(), createModificationNodeInfo("Child 2"), InsertMode.AFTER, null);

        // message for 3 modification nodes creation
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);

        /*           Root
        *             |
        *      "Modification Node"
        *             |
        *         "child2"
        *             |
        *        "child 1"
        */
        Report child1ExpectedReport = getNodeReport(MODIFICATION_CHILD_NODE1_REPORT_UUID, child1.getId().toString());
        Report child2ExpectedReport = Report.builder()
                .id(MODIFICATION_CHILD_NODE2_REPORT_UUID)
                .message(child2.getId().toString())
                .subReports(List.of(child1ExpectedReport))
                .build();
        Report modifNodeExpectedReport = Report.builder()
                .id(MODIFICATION_NODE_REPORT_UUID)
                .message(node.getId().toString())
                .subReports(List.of(child2ExpectedReport))
                .build();
        Report rootExpectedReport = Report.builder()
                .id(ROOT_NODE_REPORT_UUID)
                .message(rootNode.getId().toString())
                .subReports(List.of(modifNodeExpectedReport))
                .build();

        // get only Child1 report
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), firstRootNetworkUuid, child1.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<Report> child1Reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        checkReports(child1Reports, List.of(child1ExpectedReport));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        // get only Child1 aggregated severities
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/{reportId}/aggregated-severities", rootNode.getStudyId(), firstRootNetworkUuid, child1.getId(), MODIFICATION_CHILD_NODE1_REPORT_UUID))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        Set<String> child1AggregatedSeverities = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(Set.of("INFO"), child1AggregatedSeverities);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*/aggregated-severities")));

        // get Child2 report + parents
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), firstRootNetworkUuid, child2.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<Report> child2AndParentsReports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        // We are expecting 3 nodes reports, from Root to current child2
        List<Report> childrenAndParentsExpectedReports = new ArrayList<>();
        childrenAndParentsExpectedReports.add(rootExpectedReport);
        childrenAndParentsExpectedReports.add(modifNodeExpectedReport);
        childrenAndParentsExpectedReports.add(child2ExpectedReport);
        checkReports(child2AndParentsReports, childrenAndParentsExpectedReports);
        assertTrue(TestUtils.getRequestsDone(childrenAndParentsExpectedReports.size(), server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/aggregated-severities", rootNode.getStudyId(), firstRootNetworkUuid, node.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        Set<String> child2AggregatedSeverities = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(Set.of("WARN", "UNKNOWN"), child2AggregatedSeverities);
        assertTrue(TestUtils.getRequestsDone(2, server).stream().anyMatch(r -> r.matches("/v1/reports/.*/aggregated-severities")));

        // get Child1 report + parents
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), firstRootNetworkUuid, child1.getId()))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        List<Report> child1AndParentsReports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        // We are expecting one more node report, from Root to current node child1
        childrenAndParentsExpectedReports.add(child1ExpectedReport);
        checkReports(child1AndParentsReports, childrenAndParentsExpectedReports);
        assertTrue(TestUtils.getRequestsDone(childrenAndParentsExpectedReports.size(), server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @Test
    void testDuplicateRootNodeReport(final MockWebServer server) {
        // with null reportUuid
        assertThrows(NullPointerException.class, () -> reportService.duplicateReport(null));

        // error while duplicating report
        assertNotNull(reportService.duplicateReport(NOT_FOUND_REPORT_UUID));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/" + NOT_FOUND_REPORT_UUID + "/duplicate")));
    }

    private static Report getNodeReport(UUID reportUuid, String nodeUuid) {
        return Report.builder().id(reportUuid).message(nodeUuid).build();
    }
}
