/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;

import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.Report;
import org.gridsuite.study.server.repository.timepoint.TimePointRepository;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_DEFAULT_NAME;
import static org.gridsuite.study.server.utils.TestUtils.checkReports;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class ReportServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportServiceTest.class);

    private static final UUID ROOT_NODE_REPORT_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_NODE_REPORT_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_CHILD_NODE1_REPORT_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_CHILD_NODE2_REPORT_UUID = UUID.randomUUID();
    @Autowired
    private ObjectMapper mapper;

    private MockWebServer server;

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
    private TimePointRepository timePointRepository;
    @Autowired
    private TimePointService timePointService;

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        reportService.setReportServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/v1/reports/.*\\?defaultName=.*")) {
                    String reportUuid = Objects.requireNonNull(request.getRequestUrl()).pathSegments().get(2);
                    String nodeUuid = request.getRequestUrl().queryParameter(QUERY_PARAM_REPORT_DEFAULT_NAME);
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                            .setBody(mapper.writeValueAsString(getNodeReport(UUID.fromString(reportUuid), nodeUuid)))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                } else if (path.matches("/v1/subreports/.*\\?severityLevels=.*")) {
                    String reportId = Objects.requireNonNull(request.getRequestUrl()).pathSegments().get(2);
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                            .setBody(mapper.writeValueAsString(getNodeReport(UUID.fromString(reportId), reportId)))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(HttpStatus.I_AM_A_TEAPOT.value()).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    @After
    public void tearDown() {
        cleanDB();
        TestUtils.assertQueuesEmptyThenClear(List.of(STUDY_UPDATE_DESTINATION), output);
        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }

    private RootNode createRoot() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.randomUUID(), UUID.randomUUID(), "caseName", "", ROOT_NODE_REPORT_UUID);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return networkModificationTreeService.getStudyTree(studyEntity.getId());
    }

    @SneakyThrows
    @Test
    public void testReport() {
        RootNode rootNode = createRoot();
        StudyEntity studyEntity = studyRepository.findById(rootNode.getStudyId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        List<Report> expectedRootReports = List.of(getNodeReport(ROOT_NODE_REPORT_UUID, rootNode.getId().toString()));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), rootNode.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<Report> reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        assertEquals(1, reports.size());
        checkReports(reports, expectedRootReports);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        NetworkModificationNode node = networkModificationTreeService.createNodeThenLinkItToTimepoints(studyEntity, rootNode.getId(), createModificationNodeInfo("Node1"), InsertMode.AFTER, null);
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);  // message for modification node creation
        List<Report> expectedNodeReports = List.of(getNodeReport(MODIFICATION_NODE_REPORT_UUID, node.getId().toString()));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), node.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReports(reports, expectedNodeReports);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), node.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        final List<Report> expectedReports = Stream.concat(expectedRootReports.stream(), expectedNodeReports.stream()).toList();
        checkReports(reports, expectedReports);
        assertTrue(TestUtils.getRequestsDone(2, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @SneakyThrows
    @Test
    public void testMultipleReport() {
        RootNode rootNode = createRoot();
        StudyEntity studyEntity = studyRepository.findById(rootNode.getStudyId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        NetworkModificationNode node = networkModificationTreeService.createNodeThenLinkItToTimepoints(studyEntity, rootNode.getId(), createModificationNodeInfo("Modification Node"), InsertMode.AFTER, null);
        NetworkModificationNode child1 = networkModificationTreeService.createNodeThenLinkItToTimepoints(studyEntity, node.getId(), createModificationNodeInfo("Child 1"), InsertMode.AFTER, null);
        NetworkModificationNode child2 = networkModificationTreeService.createNodeThenLinkItToTimepoints(studyEntity, node.getId(), createModificationNodeInfo("Child 2"), InsertMode.AFTER, null);

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
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), child1.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<Report> child1Reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReports(child1Reports, List.of(child1ExpectedReport));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        // get Child2 report + parents
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), child2.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<Report> child2AndParentsReports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        // We are expecting 3 nodes reports, from Root to current child2
        List<Report> childrenAndParentsExpectedReports = new ArrayList<>();
        childrenAndParentsExpectedReports.add(rootExpectedReport);
        childrenAndParentsExpectedReports.add(modifNodeExpectedReport);
        childrenAndParentsExpectedReports.add(child2ExpectedReport);
        checkReports(child2AndParentsReports, childrenAndParentsExpectedReports);
        assertTrue(TestUtils.getRequestsDone(childrenAndParentsExpectedReports.size(), server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        // get Child1 report + parents
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), child1.getId()))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        List<Report> child1AndParentsReports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        // We are expecting one more node report, from Root to current node child1
        childrenAndParentsExpectedReports.add(child1ExpectedReport);
        checkReports(child1AndParentsReports, childrenAndParentsExpectedReports);
        assertTrue(TestUtils.getRequestsDone(childrenAndParentsExpectedReports.size(), server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    private Report getNodeReport(UUID reportUuid, String nodeUuid) {
        return Report.builder().id(reportUuid).message(nodeUuid).build();
    }
}
