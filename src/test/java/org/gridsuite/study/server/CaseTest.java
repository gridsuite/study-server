/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.CaseService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.ShortCircuitAnalysisService;
import org.gridsuite.study.server.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.loadflow.LoadFlowParameters;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class CaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseTest.class);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    public static final String POST = "POST";
    private static final String CASE_NAME = "DefaultCaseName";

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

    @Autowired
    private OutputDestination output;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private StudyRepository studyRepository;

    //output destinations
    private String studyUpdateDestination = "study.update";

    @Before
    public void setup() throws IOException {

        server = new MockWebServer();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        caseService.setCaseServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();

                switch (path) {
                    default:
                        LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                        return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }

        };

        server.setDispatcher(dispatcher);
    }

    @Test
    public void getCaseName() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, CASE_NAME);
        UUID study1Uuid = studyEntity.getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/case/name", study1Uuid)).andExpectAll(
                status().isOk(),
                content().string(CASE_NAME));
        mockMvc.perform(get("/v1/studies/{studyUuid}/case/name", UUID.randomUUID()))
                .andExpect(status().isNotFound());

    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, String caseName) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .readSlackBus(true)
                .distributedSlack(true)
                .dcUseTransformerRatio(true)
                .hvdcAcEmulation(true)
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitAnalysisService.toEntity(ShortCircuitAnalysisService.getDefaultShortCircuitParamters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, caseName, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination);

        cleanDB();

        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
