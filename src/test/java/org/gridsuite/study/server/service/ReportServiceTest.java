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
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeJsonModule;

import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.utils.MatcherReport;
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
import static org.gridsuite.study.server.utils.TestUtils.addChildReportNode;
import static org.gridsuite.study.server.utils.TestUtils.checkReportNodes;
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

    private static UUID MODIFICATION_NODE_UUID;
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

    private static final String SUB_REPORT_ID = "subReportId";

    private static final long TIMEOUT = 1000;

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

        // FIXME: remove lines when dicos will be used on the front side
        // Override the custom module to restore the standard module in order to have the original serialization used like the report server
        mapper.registerModule(new ReportNodeJsonModule() {
            @Override
            public Object getTypeId() {
                return getClass().getName() + "override";
            }
        });

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/v1/reports/.*\\?defaultName=.*")) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                            .setBody(mapper.writeValueAsString(getNodeReport(Objects.requireNonNull(request.getRequestUrl()).pathSegments().get(2), request.getRequestUrl().queryParameter(QUERY_PARAM_REPORT_DEFAULT_NAME)).getChildren()))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                } else if (path.matches("/v1/subreports/.*\\?severityLevels=.*")) {
                    String reportId = Objects.requireNonNull(request.getRequestUrl()).pathSegments().get(2);
                        return new MockResponse().setResponseCode(HttpStatus.OK.value())
                                .setBody(mapper.writeValueAsString(getRootNodeSimpleReport(reportId).getChildren()))
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
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.randomUUID(), UUID.randomUUID(), "caseName", "");
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, ROOT_NODE_REPORT_UUID);
        return networkModificationTreeService.getStudyTree(studyEntity.getId());
    }

    @SneakyThrows
    @Test
    public void testReport() {
        RootNode rootNode = createRoot();
        ReportNode expectedRootReporter = getNodeReport(ROOT_NODE_REPORT_UUID.toString(), rootNode.getId().toString());

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), rootNode.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<ReportNode> reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReportNodes(reports, expectedRootReporter.getChildren());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), rootNode.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReportNodes(reports, expectedRootReporter.getChildren());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        NetworkModificationNode node = (NetworkModificationNode) networkModificationTreeService.createNode(rootNode.getStudyId(), rootNode.getId(), createModificationNodeInfo("Node1", MODIFICATION_NODE_REPORT_UUID), InsertMode.AFTER, null);
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);  // message for modification node creation
        ReportNode expectedNodeReporter = getNodeReport(MODIFICATION_NODE_REPORT_UUID.toString(), node.getId().toString());

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), node.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReportNodes(reports, expectedNodeReporter.getChildren());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), node.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        final List<ReportNode> expectedReports = Stream.concat(expectedRootReporter.getChildren().stream(), expectedNodeReporter.getChildren().stream()).toList();
        checkReportNodes(reports, expectedReports);
        assertTrue(TestUtils.getRequestsDone(2, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @SneakyThrows
    @Test
    public void testMultipleReport() {
        RootNode rootNode = createRoot();
        NetworkModificationNode node = (NetworkModificationNode) networkModificationTreeService.createNode(rootNode.getStudyId(), rootNode.getId(), createModificationNodeInfo("Modification Node", MODIFICATION_NODE_REPORT_UUID), InsertMode.AFTER, null);
        NetworkModificationNode child1 = (NetworkModificationNode) networkModificationTreeService.createNode(rootNode.getStudyId(), node.getId(), createModificationNodeInfo("Child 1", MODIFICATION_CHILD_NODE1_REPORT_UUID), InsertMode.AFTER, null);
        NetworkModificationNode child2 = (NetworkModificationNode) networkModificationTreeService.createNode(rootNode.getStudyId(), node.getId(), createModificationNodeInfo("Child 2", MODIFICATION_CHILD_NODE2_REPORT_UUID), InsertMode.AFTER, null);

        // message for 3 modification nodes creation
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);

        MODIFICATION_NODE_UUID = node.getId();

        ReportNode expectedRootReporter = getNodeReport(ROOT_NODE_REPORT_UUID.toString(), rootNode.getId().toString());
        ReportNode expectedChild1Reporter = getNodeReport(MODIFICATION_CHILD_NODE1_REPORT_UUID.toString(), child1.getId().toString());
        ReportNode expectedChild2Reporter = getNodeReport(MODIFICATION_CHILD_NODE2_REPORT_UUID.toString(), child2.getId().toString());

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=true&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), child1.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<ReportNode> reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReportNodes(reports, List.of(expectedChild1Reporter.getChildren().get(1)));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), child1.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<ReportNode> reportsNode1 = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReportNodes(reportsNode1, Stream.concat(expectedRootReporter.getChildren().stream(), expectedChild1Reporter.getChildren().stream()).toList());
        assertTrue(TestUtils.getRequestsDone(2, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport=false&reportType=NETWORK_MODIFICATION", rootNode.getStudyId(), child2.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        List<ReportNode> reportsNode2 = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReportNodes(reportsNode2, Stream.concat(expectedRootReporter.getChildren().stream(), expectedChild2Reporter.getChildren().stream()).toList());
        assertTrue(TestUtils.getRequestsDone(2, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        assertNotEquals(mapper.writeValueAsString(reportsNode1), mapper.writeValueAsString(reportsNode2));
    }

    @SneakyThrows
    @Test
    public void testSubReport() {
        RootNode rootNode = createRoot();
        ReportNode expectedRootReporter = getRootNodeSimpleReport(ROOT_NODE_REPORT_UUID.toString());

        MvcResult mvcResult =
                mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/subreport?reportId={id}&severityLevels=INFO&severityLevels=WARN",
                                rootNode.getStudyId(), rootNode.getId(), ROOT_NODE_REPORT_UUID.toString()))
                        .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
        ReportNode report = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        checkReportNodes(List.of(report), List.of(expectedRootReporter));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/subreports/.*")));
    }

    @SneakyThrows
    @Test
    public void testNodeReport() {
        RootNode rootNode = createRoot();
        ReportNode expectedRootReporter = getNodeReport(ROOT_NODE_REPORT_UUID.toString(), rootNode.getId().toString());

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/report?reportId={id}&reportType=NETWORK_MODIFICATION",
                                rootNode.getStudyId(), rootNode.getId(), ROOT_NODE_REPORT_UUID.toString()))
                        .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
        List<ReportNode> reports = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
        });
        reports.forEach(r -> assertThat(r, new MatcherReport(expectedRootReporter.getChildren().get(reports.indexOf(r)))));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    private ReportNode getNodeReport(String reportUuid, String nodeUuid) {
        return Set.of(ROOT_NODE_REPORT_UUID.toString(), MODIFICATION_NODE_REPORT_UUID.toString()).contains(reportUuid) ?
                getNodeSimpleReport(reportUuid, nodeUuid) : getNodeMultipleReport(reportUuid, nodeUuid);
    }

    private ReportNode getNodeSimpleReport(String reportUuid, String nodeUuid) {
        ReportNode reporter = ReportNode.newRootReportNode().withMessageTemplate(reportUuid, reportUuid).build();
        reporter.newReportNode().withMessageTemplate(nodeUuid, nodeUuid)
                .withUntypedValue(SUB_REPORT_ID, reportUuid).add();
        return reporter;
    }

    private ReportNode getRootNodeSimpleReport(String reportUuid) {
        ReportNode reporter = ReportNode.newRootReportNode().withMessageTemplate(reportUuid, reportUuid).build();
        reporter.newReportNode().withMessageTemplate("Root", "Root")
                .withUntypedValue(SUB_REPORT_ID, reportUuid).add();
        return reporter;
    }

    private ReportNode getNodeMultipleReport(String reportUuid, String nodeUuid) {
        ReportNode reporter = ReportNode.newRootReportNode().withMessageTemplate(reportUuid, reportUuid).build();
        ReportNode subReporter = ReportNode.newRootReportNode().withMessageTemplate(MODIFICATION_NODE_UUID.toString(), MODIFICATION_NODE_UUID.toString())
                .withUntypedValue(SUB_REPORT_ID, reportUuid).build();
        subReporter.newReportNode().withMessageTemplate("test" + nodeUuid, "test" + nodeUuid).add();
        addChildReportNode(reporter, subReporter);
        addChildReportNode(reporter, ReportNode.newRootReportNode().withMessageTemplate(nodeUuid, nodeUuid)
                .withUntypedValue(SUB_REPORT_ID, reportUuid).build());
        return reporter;
    }
}
