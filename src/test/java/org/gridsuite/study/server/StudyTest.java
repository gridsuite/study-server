/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.model.TopLevelDocument;
import com.powsybl.network.store.model.VoltageLevelAttributes;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos;
import org.gridsuite.study.server.utils.MatcherStudyInfos;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ResourceUtils;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.BodyInserters;

import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.CASE_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.LOADFLOW_NOT_RUNNABLE;
import static org.gridsuite.study.server.StudyException.Type.STUDY_ALREADY_EXISTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class StudyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyTest.class);

    private static final String STUDIES_URL = "/v1/studies/{studyName}";
    private static final String STUDY_EXIST_URL = "/v1/{userId}/studies/{studyName}/exists";
    private static final String DESCRIPTION = "description";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String TEST_FILE_WITH_ERRORS = "testCase_with_errors.xiidm";
    private static final String TEST_FILE_IMPORT_ERRORS = "testCase_import_errors.xiidm";
    private static final String STUDY_NAME = "studyName";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final String IMPORTED_CASE_WITH_ERRORS_UUID_STRING = "88888888-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String SECURITY_ANALYSIS_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SECURITY_ANALYSIS_UUID = "e3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String HEADER_STUDY_NAME = "studyName";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_UUID = UUID.fromString(IMPORTED_CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_WITH_ERRORS_UUID = UUID.fromString(IMPORTED_CASE_WITH_ERRORS_UUID_STRING);
    private static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    private static final String CONTIGENCY_LIST_NAME = "ls";
    private static final String SECURITY_ANALYSIS_RESULT_JSON = "{\"version\":\"1.0\",\"preContingencyResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"l3\",\"limitType\":\"CURRENT\",\"acceptableDuration\":1200,\"limit\":10.0,\"limitReduction\":1.0,\"value\":11.0,\"side\":\"ONE\"}],\"actionsTaken\":[]},\"postContingencyResults\":[{\"contingency\":{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}},{\"contingency\":{\"id\":\"l2\",\"elements\":[{\"id\":\"l2\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}}]}";
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String CONTINGENCIES_JSON = "[{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]}]";
    public static final String LOAD_PARAMETERS_JSON = "{\"version\":\"1.4\",\"voltageInitMode\":\"UNIFORM_VALUES\",\"transformerVoltageControlOn\":false,\"phaseShifterRegulationOn\":false,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":false,\"readSlackBus\":false,\"writeSlackBus\":false,\"dc\":false,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_GENERATION_P_MAX\"}";
    public static final String LOAD_PARAMETERS_JSON2 = "{\"version\":\"1.4\",\"voltageInitMode\":\"DC_VALUES\",\"transformerVoltageControlOn\":true,\"phaseShifterRegulationOn\":true,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":true,\"readSlackBus\":false,\"writeSlackBus\":true,\"dc\":true,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_CONFORM_LOAD\"}";

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private StudyController controller;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private StudyService studyService;

    @MockBean
    private NetworkStoreService networkStoreClient;

    @Autowired
    private ObjectMapper mapper;

    private TopLevelDocument<VoltageLevelAttributes> topLevelDocument;

    private MockWebServer server;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

    //used by testGetStudyCreationRequests to control asynchronous case import
    CountDownLatch countDownLatch;

    private void cleanDB() {
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();
    }

    @Before
    public void setup() {
        try {
            ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                    new ResourceSet("", TEST_FILE));
            Network network = Importers.importData("XIIDM", dataSource, null);
            given(networkStoreClient.getNetwork(NETWORK_UUID)).willReturn(network);

            List<Resource<VoltageLevelAttributes>> data = new ArrayList<>();

            Iterable<VoltageLevel> vls = network.getVoltageLevels();
            vls.forEach(vl -> data.add(Resource.create(ResourceType.VOLTAGE_LEVEL, vl.getId(), VoltageLevelAttributes.builder().name(vl.getName()).substationId(vl.getSubstation().getId()).build())));

            topLevelDocument = new TopLevelDocument<>(data, null);

            server = new MockWebServer();

            // Start the server.
            server.start();

            // Ask the server for its URL. You'll need this to make HTTP requests.
            HttpUrl baseHttpUrl = server.url("");
            String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
            studyService.setCaseServerBaseUri(baseUrl);
            studyService.setNetworkConversionServerBaseUri(baseUrl);
            studyService.setNetworkModificationServerBaseUri(baseUrl);
            studyService.setSingleLineDiagramServerBaseUri(baseUrl);
            studyService.setGeoDataServerBaseUri(baseUrl);
            studyService.setNetworkMapServerBaseUri(baseUrl);
            studyService.setLoadFlowServerBaseUri(baseUrl);
            studyService.setNetworkStoreServerBaseUri(baseUrl);
            studyService.setSecurityAnalysisServerBaseUri(baseUrl);
            studyService.setActionsServerBaseUri(baseUrl);

            String networkInfosAsString = mapper.writeValueAsString(NETWORK_INFOS);
            String importedCaseUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_UUID);
            String topLevelDocumentAsString = mapper.writeValueAsString(topLevelDocument);
            String importedCaseWithErrorsUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_WITH_ERRORS_UUID);

            final Dispatcher dispatcher = new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    switch (Objects.requireNonNull(request.getPath())) {
                        case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/voltage-levels":
                            return new MockResponse().setResponseCode(200).setBody(topLevelDocumentAsString)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        case "/v1/studies/{studyName}/cases/{caseUuid}":
                            return new MockResponse().setResponseCode(200).setBody("CGMES")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        case "/v1/studies/newStudy/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING:
                            return new MockResponse().setResponseCode(200).setBody("XIIDM")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/exists":
                        case "/v1/cases/11111111-0000-0000-0000-000000000000/exists":
                        case "/v1/cases/88888888-0000-0000-0000-000000000000/exists":
                            return new MockResponse().setResponseCode(200).setBody("true")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/format":
                            return new MockResponse().setResponseCode(200).setBody("UCTE")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/format":
                        case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/format":
                            return new MockResponse().setResponseCode(200).setBody("XIIDM")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists":
                            return new MockResponse().setResponseCode(200).setBody("false")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/" + CASE_API_VERSION + "/cases/private": {
                            String body = request.getBody().readUtf8();
                            if (body.contains("filename=\"caseFile_with_errors\"")) {  // import file with errors
                                return new MockResponse().setResponseCode(200).setBody(importedCaseWithErrorsUuidAsString)
                                        .addHeader("Content-Type", "application/json; charset=utf-8");
                            } else if (body.contains("filename=\"caseFile_import_errors\"")) {  // import file with errors during import in the case server
                                return new MockResponse().setResponseCode(500)
                                        .addHeader("Content-Type", "application/json; charset=utf-8")
                                        .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
                            }  else if (body.contains("filename=\"blocking-import\"")) {
                                countDownLatch.await(2, TimeUnit.SECONDS);
                                return new MockResponse().setResponseCode(200).setBody(importedCaseUuidAsString)
                                        .addHeader("Content-Type", "application/json; charset=utf-8");
                            } else {
                                return new MockResponse().setResponseCode(200).setBody(importedCaseUuidAsString)
                                        .addHeader("Content-Type", "application/json; charset=utf-8");
                            }
                        }

                        case "/" + CASE_API_VERSION + "/cases/11111111-0000-0000-0000-000000000000":

                        case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/switches/switchId?open=true":
                            return new MockResponse().setResponseCode(200)
                                    .setBody("[\"s1\", \"s2\", \"s3\"]")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/run":
                            return new MockResponse().setResponseCode(200)
                                    .setBody("{\n" +
                                            "\"version\":\"1.1\",\n" +
                                            "\"metrics\":{\n" +
                                            "\"network_0_iterations\":\"7\",\n" +
                                            "\"network_0_status\":\"CONVERGED\"\n" +
                                            "},\n" +
                                            "\"isOK\":true,\n" +
                                            "\"componentResults\": [{\"componentNum\":0,\"status\":\"CONVERGED\",\"iterationCount\":7, \"slackBusId\": \"c6ace316-6b39-40ec-b1d6-09ab2fe42992\", \"slackBusActivePowerMismatch\": 3.7}]\n" +
                                            "}")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        case "/v1/networks?caseUuid=" + CASE_UUID_STRING:
                        case "/v1/networks?caseUuid=" + IMPORTED_CASE_UUID_STRING:
                        case "/v1/networks?caseName=" + IMPORTED_CASE_UUID_STRING:
                            return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/networks?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING:
                            return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(500)
                                    .addHeader("Content-Type", "application/json; charset=utf-8")
                                    .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");

                        case "/v1/lines?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/substations?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/substations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/2-windings-transformers/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/3-windings-transformers/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/generators/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/batteries/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/dangling-lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/hvdc-lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/lcc-converter-stations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/vsc-converter-stations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/loads/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/shunt-compensators/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/static-var-compensators/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        case "/v1/all/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                            return new MockResponse().setBody(" ").setResponseCode(200)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/svg/" + NETWORK_UUID_STRING + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                            return new MockResponse().setResponseCode(200).setBody("byte")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                            return new MockResponse().setResponseCode(200).setBody("svgandmetadata")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/substation-svg/" + NETWORK_UUID_STRING + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                            return new MockResponse().setResponseCode(200).setBody("substation-byte")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                            return new MockResponse().setResponseCode(200).setBody("substation-svgandmetadata")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/export/formats":
                            return new MockResponse().setResponseCode(200).setBody("[\"CGMES\",\"UCTE\",\"XIIDM\"]")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/export/XIIDM":
                            return new MockResponse().setResponseCode(200).addHeader("Content-Disposition", "attachment; filename=fileName").setBody("byteData")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save?contingencyListName=ls&receiver=%257B%2522studyName%2522%253A%2522newName%2522%252C%2522userId%2522%253A%2522userId%2522%257D":
                            input.send(MessageBuilder.withPayload("")
                                    .setHeader("resultUuid", SECURITY_ANALYSIS_UUID)
                                    .setHeader("receiver", "%7B%22studyName%22%3A%22newName%22%2C%22userId%22%3A%22userId%22%7D")
                                    .build());
                            return new MockResponse().setResponseCode(200).setBody("\"" + SECURITY_ANALYSIS_UUID + "\"")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/results/" + SECURITY_ANALYSIS_UUID + "?limitType":
                            return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_RESULT_JSON)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/contingency-lists/" + CONTIGENCY_LIST_NAME + "/export?networkUuid=" + NETWORK_UUID_STRING:
                            return new MockResponse().setResponseCode(200).setBody(CONTINGENCIES_JSON)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/groovy/":
                            return new MockResponse().setResponseCode(200)
                                    .setBody("[\"s4\", \"s5\", \"s6\", \"s7\"]")
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/results/" + SECURITY_ANALYSIS_UUID + "/status":
                            return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/results/" + SECURITY_ANALYSIS_UUID + "/invalidate-status":
                            return new MockResponse().setResponseCode(200)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        case "/v1/results/" + SECURITY_ANALYSIS_UUID + "/stop?receiver=%257B%2522studyName%2522%253A%2522newName%2522%252C%2522userId%2522%253A%2522userId%2522%257D":
                            input.send(MessageBuilder.withPayload("")
                                    .setHeader("resultUuid", SECURITY_ANALYSIS_UUID)
                                    .setHeader("receiver", "%7B%22studyName%22%3A%22newName%22%2C%22userId%22%3A%22userId%22%7D")
                                    .build(), "sa.stopped");
                            return new MockResponse().setResponseCode(200)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");

                        default:
                            LOGGER.error("Path not supported: " + request.getPath());
                            return new MockResponse().setResponseCode(404);

                    }
                }
            };
            server.setDispatcher(dispatcher);
            cleanDB();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    @Test
    public void test() throws Exception {

        //empty list
        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        //empty list
        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        //insert a study
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", STUDY_NAME, CASE_UUID, DESCRIPTION, "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        MessageHeaders headersSwitch = messageSwitch.getHeaders();
        assertEquals(STUDY_NAME, headersSwitch.get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_STUDIES, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation message for creation
        messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        headersSwitch = messageSwitch.getHeaders();
        assertEquals(STUDY_NAME, headersSwitch.get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_STUDIES, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation request message for deletion
        messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        headersSwitch = messageSwitch.getHeaders();
        assertEquals(STUDY_NAME, headersSwitch.get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_STUDIES, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        //insert a study with a non existing case and except exception
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", "randomStudy", "00000000-0000-0000-0000-000000000000", DESCRIPTION, "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isEqualTo(424)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(CASE_NOT_FOUND.name());

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos(STUDY_NAME, "userId", "UCTE", false));

        //insert the same study => 409 conflict
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", STUDY_NAME, CASE_UUID, DESCRIPTION, "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(STUDY_ALREADY_EXISTS.name());

        //insert the same study but with another user (should work)
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", STUDY_NAME, CASE_UUID, DESCRIPTION, "true")
                .header("userId", "userId2")
                .exchange()
                .expectStatus().isEqualTo(200);
        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos(STUDY_NAME, "userId2", "UCTE", true));

        //insert a study with a case (multipartfile)
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("caseFile")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "s2", "desc", "true")
                    .header("userId", "userId")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk();
        }
        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);

        //Import the same case -> 409 conflict
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("caseFile")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "s2", "desc", "false")
                    .header("userId", "userId")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$")
                    .isEqualTo(STUDY_ALREADY_EXISTS.name());
        }

        // check the study s2
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}", "s2")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StudyInfos.class)
                .value(MatcherStudyInfos.createMatcherStudyInfos("s2", "userId", "XIIDM", "desc", true, LoadFlowStatus.NOT_DONE));

    //try to get the study s2 with another user -> unauthorized because study is private
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}", "s2")
                .header("userId", "userId2")
                .exchange()
                .expectStatus().isForbidden();

        //get a non existing study -> 404 not found
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}", "s3")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody();

        // check if a non existing study exists
        webTestClient.get()
                .uri(STUDY_EXIST_URL, "userId", "s3")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("false");

        // check study s2 if exists
        webTestClient.get()
                .uri(STUDY_EXIST_URL, "userId", "s2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("true");

        //get the voltage level diagram svg
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg?useName=false", "userId", STUDY_NAME, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_XML)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("byte");

        //get the voltage level diagram svg from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg", "userId", "notExistingStudy", "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();

        //get the voltage level diagram svg and metadata
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", "userId", STUDY_NAME, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("svgandmetadata");

        //get the voltage level diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata", "userId", "notExistingStudy", "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();

        // get the substation diagram svg
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/substations/{substationId}/svg?useName=false", "userId", STUDY_NAME, "substationId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_XML)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("substation-byte");

        // get the substation diagram svg from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/substations/{substationId}/svg", "userId", "notExistingStudy", "substationId")
                .exchange()
                .expectStatus().isNotFound();

        // get the substation diagram svg and metadata
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/substations/{substationId}/svg-and-metadata?useName=false", "userId", STUDY_NAME, "substationId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("substation-svgandmetadata");

        // get the substation diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/substations/{substationId}/svg-and-metadata", "userId", "notExistingStudy", "substationId")
                .exchange()
                .expectStatus().isNotFound();

        //get voltage levels
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("[{\"id\":\"BBE1AA1\",\"name\":\"BBE1AA1\",\"substationId\":\"BBE1AA\"}," +
                        "{\"id\":\"BBE2AA1\",\"name\":\"BBE2AA1\",\"substationId\":\"BBE2AA\"}," +
                        "{\"id\":\"DDE1AA1\",\"name\":\"DDE1AA1\",\"substationId\":\"DDE1AA\"}," +
                        "{\"id\":\"DDE2AA1\",\"name\":\"DDE2AA1\",\"substationId\":\"DDE2AA\"}," +
                        "{\"id\":\"DDE3AA1\",\"name\":\"DDE3AA1\",\"substationId\":\"DDE3AA\"}," +
                        "{\"id\":\"FFR1AA1\",\"name\":\"FFR1AA1\",\"substationId\":\"FFR1AA\"}," +
                        "{\"id\":\"FFR3AA1\",\"name\":\"FFR3AA1\",\"substationId\":\"FFR3AA\"}," +
                        "{\"id\":\"NNL1AA1\",\"name\":\"NNL1AA1\",\"substationId\":\"NNL1AA\"}," +
                        "{\"id\":\"NNL2AA1\",\"name\":\"NNL2AA1\",\"substationId\":\"NNL2AA\"}," +
                        "{\"id\":\"NNL3AA1\",\"name\":\"NNL3AA1\",\"substationId\":\"NNL3AA\"}]");

        //get the lines-graphics of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/geo-data/lines/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the substation-graphics of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/geo-data/substations/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the lines map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/lines/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the substation map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/substations/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the 2 windings transformers map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/2-windings-transformers/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the 3 windings transformers map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/3-windings-transformers/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the generators map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/generators/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the batteries map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/batteries/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the dangling lines map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/dangling-lines/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the hvdc lines map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/hvdc-lines/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the lcc converter stations map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/lcc-converter-stations/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the vsc converter stations map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/vsc-converter-stations/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the loads map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/loads/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the shunt compensators map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/shunt-compensators/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the static var compensators map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/static-var-compensators/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get all map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/all/", "userId", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //delete existing study s2
        webTestClient.delete()
                .uri("/v1/userId/studies/{studyName}/", "s2")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent
        messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        headersSwitch = messageSwitch.getHeaders();
        assertEquals("s2", headersSwitch.get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_STUDIES, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));
        messageSwitch = output.receive(1000);
        assertEquals("s2", headersSwitch.get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_STUDIES, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        //update switch
        webTestClient.put()
                .uri("/v1/{userId}/studies/{studyName}/network-modification/switches/{switchId}?open=true", "userId", STUDY_NAME, "switchId")
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent
        Set<String> substationsSet = ImmutableSet.of("s1", "s2", "s3");
        Message<byte[]> messageStudyUpdate = output.receive(1000);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(STUDY_NAME, headersStudyUpdate.get(StudyService.HEADER_STUDY_NAME));
        assertEquals("study", headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE));
        assertEquals(substationsSet, headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        // assert that the broker message has been sent
        Message<byte[]> messageLFStatus = output.receive(1000);
        assertEquals("", new String(messageLFStatus.getPayload()));
        MessageHeaders headersLFStatus = messageLFStatus.getHeaders();
        assertEquals(STUDY_NAME, headersLFStatus.get(StudyService.HEADER_STUDY_NAME));
        assertEquals("loadflow_status", headersLFStatus.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent
        messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        headersSwitch = messageSwitch.getHeaders();
        assertEquals(STUDY_NAME, headersSwitch.get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent
        messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        headersSwitch = messageSwitch.getHeaders();
        assertEquals(STUDY_NAME, headersSwitch.get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_SWITCH, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        //update equipment
        webTestClient.put()
                .uri("/v1/{userId}/studies/{studyName}/network-modification/groovy", "userId", STUDY_NAME)
                .body(BodyInserters.fromValue("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent
        substationsSet = ImmutableSet.of("s4", "s5", "s6", "s7");
        messageStudyUpdate = output.receive(1000);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(STUDY_NAME, headersStudyUpdate.get(StudyService.HEADER_STUDY_NAME));
        assertEquals("study", headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE));
        assertEquals(substationsSet, headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        // assert that the broker message has been sent
        messageLFStatus = output.receive(1000);
        assertEquals("", new String(messageLFStatus.getPayload()));
        headersLFStatus = messageLFStatus.getHeaders();
        assertEquals(STUDY_NAME, headersLFStatus.get(HEADER_STUDY_NAME));
        assertEquals("loadflow_status", headersLFStatus.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent
        messageLFStatus = output.receive(1000);
        assertEquals("", new String(messageLFStatus.getPayload()));
        headersLFStatus = messageLFStatus.getHeaders();
        assertEquals(STUDY_NAME, headersLFStatus.get(HEADER_STUDY_NAME));
        assertEquals("securityAnalysis_status", headersLFStatus.get(HEADER_UPDATE_TYPE));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos("studyName", "userId", "UCTE", false));

        //expect only 1 study (public one) since the other is private and we use another userId
        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos("studyName", "userId", "UCTE", false));

        //rename the study
        String newStudyName = "newName";
        RenameStudyAttributes renameStudyAttributes = new RenameStudyAttributes(newStudyName);

        webTestClient.post()
                .uri("/v1/userId/studies/" + STUDY_NAME + "/rename")
                .header("userId", "userId")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StudyInfos.class)
                .value(MatcherStudyInfos.createMatcherStudyInfos("newName", "userId", "UCTE", "description", false, LoadFlowStatus.NOT_DONE));

        // broker message for study rename
        messageLFStatus = output.receive(1000);
        assertEquals("", new String(messageLFStatus.getPayload()));
        headersLFStatus = messageLFStatus.getHeaders();
        assertEquals(STUDY_NAME, headersLFStatus.get(HEADER_STUDY_NAME));
        assertEquals("studies", headersLFStatus.get(HEADER_UPDATE_TYPE));

        webTestClient.post()
                .uri("/v1/userId/studies/" + STUDY_NAME + "/rename")
                .header("userId", "userId")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isNotFound();

        //run a loadflow
        webTestClient.put()
                .uri("/v1/userId/studies/" + newStudyName + "/loadflow/run")
                .exchange()
                .expectStatus().isOk();
        // assert that the broker message has been sent
        Message<byte[]> messageLfStatus = output.receive(1000);
        assertEquals("", new String(messageLfStatus.getPayload()));
        MessageHeaders headersLF = messageLfStatus.getHeaders();
        assertEquals("newName", headersLF.get(HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));
        assertEquals(LoadFlowStatus.CONVERGED, Objects.requireNonNull(this.studyService.getStudy(newStudyName, "userId").block()).getLoadFlowStatus());
        Message<byte[]> messageLf = output.receive(1000);
        assertEquals("newName", messageLf.getHeaders().get(HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_LOADFLOW, messageLf.getHeaders().get(HEADER_UPDATE_TYPE));

        //try to run a another loadflow
        webTestClient.put()
                .uri("/v1/userId/studies/" + "newName" + "/loadflow/run")
                .exchange()
                .expectStatus().isEqualTo(403)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(LOADFLOW_NOT_RUNNABLE.name());

        //get available export format
        webTestClient.get()
                .uri("/v1/export-network-formats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("[\"CGMES\",\"UCTE\",\"XIIDM\"]");

        //export a network
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/export-network/{format}", newStudyName, "XIIDM")
                .exchange()
                .expectStatus().isOk();

        // security analysis not found
        webTestClient.get()
                .uri("/v1/security-analysis/results/{resultUuid}", NOT_FOUND_SECURITY_ANALYSIS_UUID)
                .exchange()
                .expectStatus().isNotFound();

        // run security analysis
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/security-analysis/run?contingencyListName={contingencyListName}", newStudyName, CONTIGENCY_LIST_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UUID.class)
                .isEqualTo(UUID.fromString(SECURITY_ANALYSIS_UUID));

        Message<byte[]> securityAnalysisStatusMessage = output.receive(1000);
        assertEquals(newStudyName, securityAnalysisStatusMessage.getHeaders().get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, securityAnalysisStatusMessage.getHeaders().get(StudyService.HEADER_UPDATE_TYPE));

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(1000);
        assertEquals(newStudyName, securityAnalysisUpdateMessage.getHeaders().get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT, securityAnalysisUpdateMessage.getHeaders().get(StudyService.HEADER_UPDATE_TYPE));

        // get security analysis result
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/security-analysis/result", newStudyName)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(SECURITY_ANALYSIS_RESULT_JSON);

        // get security analysis status
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/security-analysis/status", newStudyName)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(SECURITY_ANALYSIS_STATUS_JSON);

        // stop security analysis
        webTestClient.put()
                .uri("/v1/userId/studies/{studyName}/security-analysis/stop", newStudyName)
                .exchange()
                .expectStatus().isOk();

        securityAnalysisStatusMessage = output.receive(1000);
        assertEquals(newStudyName, securityAnalysisStatusMessage.getHeaders().get(StudyService.HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, securityAnalysisStatusMessage.getHeaders().get(StudyService.HEADER_UPDATE_TYPE));

        // get contingency count
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/contingency-count?contingencyListName={contingencyListName}", newStudyName, CONTIGENCY_LIST_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Integer.class)
                .isEqualTo(1);

        // make public study private
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/private", newStudyName)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(MatcherStudyInfos.createMatcherStudyInfos("newName", "userId", "UCTE", "description", true, LoadFlowStatus.CONVERGED));

        // make private study private should work
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/private", newStudyName)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(MatcherStudyInfos.createMatcherStudyInfos("newName", "userId", "UCTE", "description", true, LoadFlowStatus.CONVERGED));

        // make private study public
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/public", newStudyName)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(MatcherStudyInfos.createMatcherStudyInfos("newName", "userId", "UCTE", "description", false, LoadFlowStatus.CONVERGED));

        // drop the broker message for study deletion (due to right access change)
        output.receive(1000);
        output.receive(1000);
        output.receive(1000);

        // try to change access rights of a non-existing study
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/public", "nonExistingStudy")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound();

        // try to change access rights of a non-existing study
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/private", "nonExistingStudy")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound();

        // try to change access right for a study of another user -> forbidden
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/private", newStudyName)
                .header("userId", "notAuth")
                .exchange()
                .expectStatus().isForbidden();

        // get default LoadFlowParameters
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/loadflow/parameters", newStudyName)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(LOAD_PARAMETERS_JSON);

        // setting loadFlow Parameters
        webTestClient.post()
                .uri("/v1/userId/studies/{studyName}/loadflow/parameters", newStudyName)
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new LoadFlowParameters(
                        LoadFlowParameters.VoltageInitMode.DC_VALUES,
                        true,
                        false,
                        true,
                        false,
                        true,
                        false,
                        true,
                        true,
                        true,
                        LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD))
                )
                .exchange()
                .expectStatus().isOk();

        // getting setted values
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/loadflow/parameters", newStudyName)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(LOAD_PARAMETERS_JSON2);

        // run loadflow with new parameters
        webTestClient.put()
                .uri("/v1/userId/studies/{studyName}/loadflow/run", newStudyName)
                .exchange()
                .expectStatus().isOk();
        // assert that the broker message has been sent
        messageLf = output.receive(1000);
        assertEquals("", new String(messageLf.getPayload()));
        headersLF = messageLf.getHeaders();
        assertEquals("newName", headersLF.get(HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));

        output.receive(1000);
        output.receive(1000);
        output.receive(1000);
    }

    @Test
    public void testCreationWithErrorBadCaseFile() throws Exception {
        // Create study with a bad case file -> error
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + TEST_FILE_WITH_ERRORS))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE_WITH_ERRORS, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("caseFile_with_errors")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "newStudy", "desc", "false")
                    .header("userId", "userId")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk();

            // assert that the broker message has been sent a study creation request message
            Message<byte[]> message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            MessageHeaders headers = message.getHeaders();
            assertEquals("newStudy", headers.get(StudyService.HEADER_STUDY_NAME));
            assertEquals(StudyService.UPDATE_TYPE_STUDIES, headers.get(StudyService.HEADER_UPDATE_TYPE));

            // assert that the broker message has been sent a error message for study creation
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals("newStudy", headers.get(StudyService.HEADER_STUDY_NAME));
            assertEquals("The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'", headers.get(StudyService.HEADER_ERROR));

            // assert that the broker message has been sent a study creation request message for deletion
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals("newStudy", headers.get(StudyService.HEADER_STUDY_NAME));
            assertEquals(StudyService.UPDATE_TYPE_STUDIES, headers.get(StudyService.HEADER_UPDATE_TYPE));
        }
    }

    @Test
    public void testCreationWithErrorBadExistingCase() throws Exception {
        // Create study with a bad case file -> error when importing in the case server
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + TEST_FILE_IMPORT_ERRORS))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE_IMPORT_ERRORS, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("caseFile_import_errors")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "newStudy", "desc", "false")
                    .header("userId", "userId")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk();

            // assert that the broker message has been sent a study creation request message
            Message<byte[]> message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            MessageHeaders headers = message.getHeaders();
            assertEquals("newStudy", headers.get(StudyService.HEADER_STUDY_NAME));
            assertEquals(StudyService.UPDATE_TYPE_STUDIES, headers.get(StudyService.HEADER_UPDATE_TYPE));

            // assert that the broker message has been sent a error message for study creation
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals("newStudy", headers.get(StudyService.HEADER_STUDY_NAME));
            assertEquals("Error during import in the case server", headers.get(StudyService.HEADER_ERROR));

            // assert that the broker message has been sent a study creation request message for deletion
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals("newStudy", headers.get(StudyService.HEADER_STUDY_NAME));
            assertEquals(StudyService.UPDATE_TYPE_STUDIES, headers.get(StudyService.HEADER_UPDATE_TYPE));
        }
    }

    @Test
    public void testGetStudyCreationRequests() throws Exception {
        countDownLatch = new CountDownLatch(1);
        //insert a study with a case (multipartfile)
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:blocking-import.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("blocking-import", "blocking-import.xiidm", "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("blocking-import")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "s3", "description", "true")
                    .header("userId", "userId")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .value(b -> {
                        assertTrue(studyCreationRequestRepository.findAll().get(0).getIsPrivate());
                        assertEquals("s3", studyCreationRequestRepository.findAll().get(0).getStudyName());
                        assertEquals("userId", studyCreationRequestRepository.findAll().get(0).getUserId());
                    });
        }

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .value(b -> {
                    assertTrue(b.get(0).isStudyPrivate());
                    assertEquals("s3", b.get(0).getStudyName());
                    assertEquals("userId", b.get(0).getUserId());
                });

        countDownLatch.countDown();

        // Study import is asynchronous, we have to wait because our code doesn't allow block until the study creation processing is done
        Thread.sleep(1000);

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(StudyInfos.class)
                .value(b -> {
                    assertTrue(b.get(0).isStudyPrivate());
                    assertEquals("s3", b.get(0).getStudyName());
                    assertEquals("userId", b.get(0).getUserId());
                    assertEquals("XIIDM", b.get(0).getCaseFormat());
                });

        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);
    }

    @After
    public void tearDown() {
        // Shut down the server. Instances cannot be reused.
        try {
            server.shutdown();
        } catch (Exception e) {
            // Nothing to do
        }
    }
}
