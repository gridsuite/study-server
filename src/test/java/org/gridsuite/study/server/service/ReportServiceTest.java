/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.io.IOException;

import org.gridsuite.study.server.StudyApplication;
import org.gridsuite.study.server.dto.ReportingInfos;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = { StudyApplication.class, TestChannelBinderConfiguration.class})})
//@EnableAutoConfiguration(exclude={ DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class, ElasticsearchDataAutoConfiguration.class})
public class ReportServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportServiceTest.class);

    private static final String ROOT_NODE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String NODE1_UUID     = "10000000-0000-0000-0000-000000000000";
    private static final String NODE2_UUID     = "20000000-0000-0000-0000-000000000000";
    private static final String NODE3_UUID     = "30000000-0000-0000-0000-000000000000";
    private static final String NODEU_UUID     = "c0000000-0000-0000-0000-000000000000";
    private static final String MODGROUP1_UUID = "01000000-0000-0000-0000-000000000000";
    private static final String MODGROUP2_UUID = "02000000-0000-0000-0000-000000000000";
    private static final String MODGROUP3_UUID = "02000000-0000-0000-0000-000000000000";
    private static final String MODGROUPU_UUID = "0c000000-0000-0000-0000-000000000000";
    private static final String SPURIOUS_MODGROUP_UUID = "0d000000-0000-0000-0000-000000000000";
    private static final String REPORT1_UUID   = "00100000-0000-0000-0000-000000000000";
    private static final String REPORT2_UUID   = "00200000-0000-0000-0000-000000000000";
    private static final String REPORT3_UUID   = "00300000-0000-0000-0000-000000000000";
    private static final String REPORTR_UUID   = "00a00000-0000-0000-0000-000000000000";
    private static final String REPORTU_UUID   = "00c00000-0000-0000-0000-000000000000";

    private static final String REPORT_FAILED_UUID = "00f00000-0000-0000-0000-000000000000";
    private static final String SNEAKY_REPORT_UUID = "00d00000-0000-0000-0000-000000000000";

    private static final ReporterModel REPORTER_R = makeRootReporter(REPORTR_UUID);

    private static final ReporterModel REPORTER1 = makeRootReporter(REPORT1_UUID,
        makeNMReporter(MODGROUP1_UUID, null));

    private static final ReporterModel REPORTER23 = makeRootReporter(REPORT3_UUID,
        makeOtherReporter(MODGROUP2_UUID),
        makeNMReporter(MODGROUP2_UUID, null),
        makeOtherReporter(MODGROUP3_UUID),
        makeNMReporter(MODGROUP3_UUID, null));

    @NotNull
    private static ReporterModel makeRootReporter(String reportUuid,
        ReporterModel... networkModifications) {
        ReporterModel outerReporter = new ReporterModel(reportUuid, reportUuid);
        for (ReporterModel networkModification : networkModifications) {
            outerReporter.addSubReporter(networkModification);
        }
        return outerReporter;
    }

    @NotNull
    private static ReporterModel makeNMReporter(String modgroupUuid, String nodeType) {
        ReporterModel stepReporter = new ReporterModel("do" + modgroupUuid, "done");
        Report report = new Report("key" + modgroupUuid, "default", Map.of());
        stepReporter.report(report);
        ReporterModel networkModification = new ReporterModel(modgroupUuid, nodeType != null ? nodeType : "NetworkModification");
        networkModification.addSubReporter(stepReporter);
        return networkModification;
    }

    @NotNull
    private static ReporterModel makeOtherReporter(String modgroupUuid) {
        ReporterModel stepReporter = new ReporterModel("do" + modgroupUuid, "done");
        Report report = new Report("key" + modgroupUuid, "default", Map.of());
        stepReporter.report(report);
        ReporterModel networkModification = new ReporterModel("NotUUID", "Other");
        networkModification.addSubReporter(stepReporter);
        return networkModification;
    }

    @Autowired
    private ObjectMapper mapper;

    private MockWebServer server;

    @Autowired
    private ReportService reportService;

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        reportService.setReportServerBaseUri(baseUrl);

        mapper.registerModule(new ReporterModelJsonModule() {
            @Override
            public Object getTypeId() {
                return getClass().getName() + "override";
            }
        });

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                return indir(request);
            }

            @NotNull private MockResponse indir(RecordedRequest request) throws JsonProcessingException {
                String path = Objects.requireNonNull(request.getPath());

                String prefix = "/v1/reports/";
                if (path.startsWith(prefix + REPORT1_UUID)) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(REPORTER1))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                }
                if (path.startsWith(prefix + REPORT2_UUID)) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(REPORTER23))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                }
                if (path.startsWith(prefix + REPORTR_UUID)) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(REPORTER_R))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                }
                if (path.startsWith(prefix + REPORTU_UUID)) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(makeRootReporter(REPORTU_UUID,
                            makeNMReporter(SPURIOUS_MODGROUP_UUID, "Not network modification"))))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                }
                if (path.startsWith(prefix + REPORT_FAILED_UUID)) {
                    return new MockResponse().setResponseCode(500)
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                }
                if (path.startsWith(prefix + SNEAKY_REPORT_UUID)) {
                    return new MockResponse().setResponseCode(200);
                }

                LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + path);
                return new MockResponse().setResponseCode(418);
            }
        };
        server.setDispatcher(dispatcher);
    }

    private Set<String> getRequestsDone(int n) {
        return getRequestsDone(n, 1000L);
    }

    private Set<String> getRequestsDone(int n, long timeoutMillis) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                RecordedRequest recordedRequest = server.takeRequest(timeoutMillis, TimeUnit.MILLISECONDS);
                return recordedRequest.getPath();
            } catch (InterruptedException e) {
                LOGGER.error("Error while attempting to get the request done : ", e);
            }
            return null;
        }).collect(Collectors.toSet());
    }

    @Test
    public void testLogsReportSimple() {

        List<ReportingInfos> reportInfos = new ArrayList<>();
        reportInfos.add(
            new ReportingInfos(UUID.fromString(NODE1_UUID), UUID.fromString(REPORT1_UUID), UUID.fromString(MODGROUP1_UUID),
                "buildNode1Name"));
        reportInfos.add(
            new ReportingInfos(UUID.fromString(NODEU_UUID), UUID.fromString(REPORTU_UUID), UUID.fromString(MODGROUPU_UUID),
                "unbuilt"));
        reportInfos.add(
            new ReportingInfos(UUID.fromString(NODE2_UUID), UUID.fromString(REPORT2_UUID), UUID.fromString(MODGROUP2_UUID),
                "buildNode2Name"));
        reportInfos.add(
            new ReportingInfos(UUID.fromString(NODE3_UUID), UUID.fromString(REPORT3_UUID), UUID.fromString(MODGROUP3_UUID),
                "defNodeName"));
        reportInfos.add(
            new ReportingInfos(UUID.fromString(ROOT_NODE_UUID), UUID.fromString(REPORTR_UUID), null, "root"));

        List<ReporterModel> res = reportService.getReporterModels(reportInfos);

        assertEquals(5, res.size());
        assertEquals("root", res.get(0).getDefaultName());
        assertEquals("defNodeName", res.get(1).getDefaultName());
        assertEquals("buildNode2Name", res.get(2).getDefaultName());
        assertEquals("unbuilt", res.get(3).getDefaultName());
        assertEquals("buildNode1Name", res.get(4).getDefaultName());
        // asks build node 1, get node 1
        // then asks for unbuilt
        // then asks node 2, gets node 2 and 3
        // then asks for root
        assertTrue(getRequestsDone(4).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @Test
    public void testLogsReportOneFail() {

        List<ReportingInfos> reportInfos = new ArrayList<>();
        reportInfos.add(
            new ReportingInfos(UUID.fromString(NODE1_UUID), UUID.fromString(SNEAKY_REPORT_UUID), UUID.fromString(MODGROUP1_UUID),
                "buildNode1Name"));
        reportInfos.add(
            new ReportingInfos(UUID.fromString(NODE2_UUID), UUID.fromString(REPORT_FAILED_UUID), UUID.fromString(MODGROUP2_UUID),
                "buildNode2Name"));
        reportInfos.add(
            new ReportingInfos(UUID.fromString(NODE3_UUID), UUID.fromString(REPORT2_UUID), UUID.fromString(MODGROUP3_UUID),
                "defNodeName"));

        try {
            reportService.getReporterModels(reportInfos);
            Assert.fail();
        } catch (Exception ex) {
            assertTrue(getRequestsDone(2).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
        }
    }

    @After
    public void tearDown() {

        Set<String> httpRequest = null;
        try {
            httpRequest = getRequestsDone(1, 100);
        } catch (NullPointerException e) {
            // Ignoring, especially as it is the "normal" way out
        }

        // Shut down the server. Instances cannot be reused.
        try {
            server.shutdown();
        } catch (Exception e) {
            // Ignoring
        }

        assertNull("Should not be any http requests : ", httpRequest);
    }
}
