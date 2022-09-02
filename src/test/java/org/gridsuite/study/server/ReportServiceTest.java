/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

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

import org.gridsuite.study.server.dto.ReportingInfos;
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
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class ReportServiceTest {
    private static final Logger  LOGGER = LoggerFactory.getLogger(ReportServiceTest.class);

    private static final String NODE1_UUID     = "10000000-0000-0000-0000-000000000000";
    private static final String NODE2_UUID     = "20000000-0000-0000-0000-000000000000";
    private static final String MODGROUP1_UUID = "01000000-0000-0000-0000-000000000000";
    private static final String MODGROUP2_UUID = "02000000-0000-0000-0000-000000000000";
    private static final String REPORT1_UUID   = "00100000-0000-0000-0000-000000000000";
    private static final String REPORT2_UUID   = "00200000-0000-0000-0000-000000000000";

    private static final ReporterModel REPORTER1 = makeReporter(MODGROUP1_UUID, REPORT1_UUID);

    private static final ReporterModel REPORTER2 = makeReporter(MODGROUP2_UUID, REPORT2_UUID);

    @NotNull private static ReporterModel makeReporter(String modgroupUuid, String reportUuid) {
        ReporterModel stepReporter = new ReporterModel("do" + modgroupUuid, "done");
        Report report = new Report("key" + modgroupUuid, "default", Map.of());
        stepReporter.report(report);
        ReporterModel networkModification = new ReporterModel(modgroupUuid, "NetworkModification");
        networkModification.addSubReporter(stepReporter);
        ReporterModel outerReporter = new ReporterModel(reportUuid, reportUuid);
        outerReporter.addSubReporter(networkModification);
        return outerReporter;
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
                        .setBody(mapper.writeValueAsString(REPORTER2))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
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
    public void testLogsReport() {

        List<ReportingInfos> reportInfos = new ArrayList<>();
        reportInfos.add(new ReportingInfos(UUID.fromString(NODE1_UUID), UUID.fromString(REPORT1_UUID), UUID.fromString(MODGROUP1_UUID), "buildNodeName"));
        reportInfos.add(new ReportingInfos(UUID.fromString(NODE2_UUID), UUID.fromString(REPORT2_UUID), UUID.fromString(MODGROUP2_UUID), "defNodeName"));

        List<ReporterModel> res = reportService.getReporterModels(reportInfos);

        assertEquals(2, res.size());
        assertEquals("defNodeName", res.get(0).getDefaultName());
        assertEquals("buildNodeName", res.get(1).getDefaultName());
        assertTrue(getRequestsDone(2).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @After
    public void tearDown() {

        Set<String> httpRequest = null;
        try {
            httpRequest = getRequestsDone(1, 100);
        } catch (NullPointerException e) {
            // Ignoring, especially as it is the "normal' way out
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
