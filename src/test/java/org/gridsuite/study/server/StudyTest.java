/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.ResourceType;
import com.powsybl.network.store.model.TopLevelDocument;
import com.powsybl.network.store.model.VoltageLevelAttributes;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.utils.MatcherJson;
import org.json.JSONArray;
import org.json.JSONObject;
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
import org.springframework.http.HttpStatus;
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
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.StudyService.*;
import static org.gridsuite.study.server.utils.MatcherBasicStudyInfos.createMatcherStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherStudyInfos.createMatcherStudyInfos;
import static org.junit.Assert.*;
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
    private static final String TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY = "testCase_import_errors_no_message_in_response_body.xiidm";
    private static final String STUDY_NAME = "studyName";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String PARENT_DIRECTORY_UUID = "22400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String DIRECTORY_SERVER_ROOT_UUID = StudyController.TMP_LEGACY_DIRECTORY;
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final String IMPORTED_BLOCKING_CASE_UUID_STRING = "22111111-0000-0000-0000-000000000000";
    private static final String IMPORTED_CASE_WITH_ERRORS_UUID_STRING = "88888888-0000-0000-0000-000000000000";
    private static final String NEW_STUDY_CASE_UUID = "11888888-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String SECURITY_ANALYSIS_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SECURITY_ANALYSIS_UUID = "e3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String HEADER_STUDY_NAME = "studyName";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final String RENAME_FAIL = "renameFail";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_UUID = UUID.fromString(IMPORTED_CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_WITH_ERRORS_UUID = UUID.fromString(IMPORTED_CASE_WITH_ERRORS_UUID_STRING);
    private static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    private static final String CONTIGENCY_LIST_NAME = "ls";
    private static final String SECURITY_ANALYSIS_RESULT_JSON = "{\"version\":\"1.0\",\"preContingencyResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"l3\",\"limitType\":\"CURRENT\",\"acceptableDuration\":1200,\"limit\":10.0,\"limitReduction\":1.0,\"value\":11.0,\"side\":\"ONE\"}],\"actionsTaken\":[]},\"postContingencyResults\":[{\"contingency\":{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}},{\"contingency\":{\"id\":\"l2\",\"elements\":[{\"id\":\"l2\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}}]}";
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String CONTINGENCIES_JSON = "[{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]}]";
    public static final String LOAD_PARAMETERS_JSON = "{\"version\":\"1.5\",\"voltageInitMode\":\"UNIFORM_VALUES\",\"transformerVoltageControlOn\":false,\"phaseShifterRegulationOn\":false,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":false,\"readSlackBus\":false,\"writeSlackBus\":false,\"dc\":false,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_GENERATION_P_MAX\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\"}";
    public static final String LOAD_PARAMETERS_JSON2 = "{\"version\":\"1.5\",\"voltageInitMode\":\"DC_VALUES\",\"transformerVoltageControlOn\":true,\"phaseShifterRegulationOn\":true,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":true,\"readSlackBus\":false,\"writeSlackBus\":true,\"dc\":true,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_CONFORM_LOAD\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\"}";

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private WebTestClient webTestClient;

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
    public void setup() throws IOException {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
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
        studyService.setDirectoryServerBaseUri(baseUrl);
        studyService.setReportServerBaseUri(baseUrl);

        String networkInfosAsString = mapper.writeValueAsString(NETWORK_INFOS);
        String importedCaseUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_UUID);
        String topLevelDocumentAsString = mapper.writeValueAsString(topLevelDocument);
        String importedCaseWithErrorsUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_WITH_ERRORS_UUID);
        String importedBlockingCaseUuidAsString = mapper.writeValueAsString(IMPORTED_BLOCKING_CASE_UUID_STRING);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String path = Objects.requireNonNull(request.getPath());
                Buffer body = request.getBody();

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", SECURITY_ANALYSIS_UUID)
                            .setHeader("receiver", "%7B%22studyUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build());
                    return new MockResponse().setResponseCode(200).setBody("\"" + SECURITY_ANALYSIS_UUID + "\"")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_UUID + "/stop.*")) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", SECURITY_ANALYSIS_UUID)
                            .setHeader("receiver", "%7B%22studyName%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), "sa.stopped");
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/directories/.*/rename/" + RENAME_FAIL) && request.getMethod().equals("PUT")) {
                    return new MockResponse().setResponseCode(500);
                } else if (path.matches("/v1/directories/.*/rename/.*") && request.getMethod().equals("PUT")) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/directories/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200);
                }
                switch (path) {
                    case "/v1/directories/" + DIRECTORY_SERVER_ROOT_UUID:
                        return new MockResponse().setResponseCode(200);
                    case "/v1/directories/" + PARENT_DIRECTORY_UUID:
                        return new MockResponse().setResponseCode(500);
                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d":
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
                    case "/v1/cases/11888888-0000-0000-0000-000000000000/exists":
                        return new MockResponse().setResponseCode(200).setBody("true")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/format":
                        return new MockResponse().setResponseCode(200).setBody("UCTE")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/format":
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/format":
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/format":
                    case "/v1/cases/" + IMPORTED_BLOCKING_CASE_UUID_STRING + "/format":
                        return new MockResponse().setResponseCode(200).setBody("XIIDM")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists":
                        return new MockResponse().setResponseCode(200).setBody("false")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/" + CASE_API_VERSION + "/cases/private": {
                        String bodyStr = body.readUtf8();
                        if (bodyStr.contains("filename=\"" + TEST_FILE_WITH_ERRORS + "\"")) {  // import file with errors
                            return new MockResponse().setResponseCode(200).setBody(importedCaseWithErrorsUuidAsString)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        } else if (bodyStr.contains("filename=\"" + TEST_FILE_IMPORT_ERRORS + "\"")) {  // import file with errors during import in the case server
                            return new MockResponse().setResponseCode(500)
                                    .addHeader("Content-Type", "application/json; charset=utf-8")
                                    .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
                        } else if (bodyStr.contains("filename=\"" + TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY + "\"")) {  // import file with errors during import in the case server without message in response body
                            return new MockResponse().setResponseCode(500)
                                .addHeader("Content-Type", "application/json; charset=utf-8")
                                .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message2\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
                        } else if (bodyStr.contains("filename=\"blockingCaseFile\"")) {
                            return new MockResponse().setResponseCode(200).setBody(importedBlockingCaseUuidAsString)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        } else {
                            return new MockResponse().setResponseCode(200).setBody(importedCaseUuidAsString)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        }
                    }

                    case "/" + CASE_API_VERSION + "/cases/11111111-0000-0000-0000-000000000000":

                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/modifications":
                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/switches/switchId?open=true":
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                                .setBody(new JSONArray(List.of(jsonObject)).toString())
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines/line12/status":
                        if (Objects.nonNull(body) && body.peek().readUtf8().equals("lockout")) {
                            jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2")));
                            return new MockResponse().setResponseCode(200)
                                    .setBody(new JSONArray(List.of(jsonObject)).toString())
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        } else {
                            return new MockResponse().setResponseCode(500);
                        }
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines/line23/status":
                        if (Objects.nonNull(body) && body.peek().readUtf8().equals("trip")) {
                            jsonObject = new JSONObject(Map.of("substationIds", List.of("s2", "s3")));
                            return new MockResponse().setResponseCode(200)
                                    .setBody(new JSONArray(List.of(jsonObject)).toString())
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        } else {
                            return new MockResponse().setResponseCode(500);
                        }

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status": {
                        String bodyStr = Objects.nonNull(body) ? body.peek().readUtf8() : "";
                        if (bodyStr.equals("switchOn") || bodyStr.equals("energiseEndOne")) {
                            jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s3")));
                            return new MockResponse().setResponseCode(200)
                                    .setBody(new JSONArray(List.of(jsonObject)).toString())
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                        } else {
                            return new MockResponse().setResponseCode(500);
                        }
                    }

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status":
                        return new MockResponse().setResponseCode(500).setBody(LINE_MODIFICATION_FAILED.name());

                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/run?reportId=38400000-8cf0-11bd-b23e-10b96e4ef00d&reportName=loadflow&overwrite=true":
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
                    case "/v1/networks?caseUuid=" + NEW_STUDY_CASE_UUID:
                    case "/v1/networks?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING:
                        countDownLatch.await(2, TimeUnit.SECONDS);
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + CASE_UUID_STRING:
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_UUID_STRING:
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
                    case "/v1/report/38400000-8cf0-11bd-b23e-10b96e4ef00d":
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

                    case "/v1/results/" + SECURITY_ANALYSIS_UUID + "?limitType":
                        return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_RESULT_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/contingency-lists/" + CONTIGENCY_LIST_NAME + "/export?networkUuid=" + NETWORK_UUID_STRING:
                        return new MockResponse().setResponseCode(200).setBody(CONTINGENCIES_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/groovy/":
                        jsonObject = new JSONObject(Map.of("substationIds", List.of("s4", "s5", "s6", "s7")));
                        return new MockResponse().setResponseCode(200)
                                .setBody(new JSONArray(List.of(jsonObject)).toString())
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/results/" + SECURITY_ANALYSIS_UUID + "/status":
                        return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/results/" + SECURITY_ANALYSIS_UUID + "/invalidate-status":
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
    }

    private Set<String> getRequestsDone(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                return server.takeRequest(0, TimeUnit.SECONDS).getPath();
            } catch (InterruptedException e) {
                LOGGER.error("Error while attempting to get the request done : ", e);
            }
            return null;
        }).collect(Collectors.toSet());
    }

    private class RequestWithBody {

        public RequestWithBody(String path, String body) {
            this.path = path;
            this.body = body;
        }

        public String getPath() {
            return path;
        }

        public String getBody() {
            return body;
        }

        private String path;
        private String body;
    }

    private Set<RequestWithBody> getRequestsWithBodyDone(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                var request = server.takeRequest();
                return new RequestWithBody(request.getPath(), request.getBody().readUtf8());
            } catch (InterruptedException e) {
                LOGGER.error("Error while attempting to get the request done : ", e);
            }
            return null;
        }).collect(Collectors.toSet());
    }

    @Test
    public void test() {
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
                .uri("/v1/studies/metadata")
                .header("userId", "userId")
                .header("uuids", "")
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
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false);
        UUID studyUuid = studyRepository.findByUserIdAndStudyName("userId", STUDY_NAME).get().getId();

        //insert a study with a non existing case and except exception
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", "randomStudy", "00000000-0000-0000-0000-000000000000", DESCRIPTION, "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isEqualTo(424)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(CASE_NOT_FOUND.name());

        assertTrue(getRequestsDone(1).contains(String.format("/v1/cases/%s/exists", "00000000-0000-0000-0000-000000000000")));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, STUDY_NAME, "userId", "UCTE", "description", false));

        //insert the same study but with another user (should work)
        //even with the same name should work
        createStudy("userId2", STUDY_NAME, CASE_UUID, DESCRIPTION, true);
        studyUuid = studyRepository.findByUserIdAndStudyName("userId2", STUDY_NAME).get().getId();
        UUID oldStudyUuid = studyRepository.findByUserIdAndStudyName("userId", STUDY_NAME).get().getId();

        webTestClient.get()
                .uri("/v1/studies/metadata")
                .header("userId", "userId")
                .header("uuids", oldStudyUuid + "," + studyUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(oldStudyUuid, STUDY_NAME, "userId", "UCTE", "description", false));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, STUDY_NAME, "userId2", "UCTE", DESCRIPTION, true));

        //insert a study with a case (multipartfile)
        createStudy("userId", "s2", TEST_FILE, IMPORTED_CASE_UUID_STRING, "desc", true);
        UUID s2Uuid = studyRepository.findByUserIdAndStudyName("userId", "s2").get().getId();

        // check the study s2
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}", s2Uuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(s2Uuid, "s2", "userId", "XIIDM", "desc", true));

        //try to get the study s2 with another user -> unauthorized because study is private
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}", s2Uuid)
                .header("userId", "userId2")
                .exchange()
                .expectStatus().isForbidden();

        UUID randomUuid = UUID.randomUUID();
        //get a non existing study -> 404 not found
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}", randomUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody();

        // check if a non existing study exists
        webTestClient.get()
                .uri(STUDY_EXIST_URL, "userId", randomUuid)
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

        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        //delete existing study s2
        webTestClient.delete()
                .uri("/v1/studies/" + s2Uuid + "/", "s2")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("userId", headers.get(HEADER_USER_ID));
        assertEquals(s2Uuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(Boolean.FALSE, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s", NETWORK_UUID_STRING)));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/modifications", NETWORK_UUID_STRING)));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/report/%s", NETWORK_UUID_STRING)));

        //expect only 1 study (public one) since the other is private and we use another userId
        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, STUDY_NAME, "userId", "UCTE", "description", false));

        //rename the study
        String newStudyName = "newName";
        RenameStudyAttributes renameStudyAttributes = new RenameStudyAttributes(newStudyName);

        webTestClient.post()
                .uri("/v1/studies/" + studyNameUserIdUuid + "/rename")
                .header("userId", "userId")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(CreatedStudyBasicInfos.class)
                .value(createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "newName", "userId", "UCTE", "description", false));

        // broker message for study rename
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/directories/%s/rename/newName", studyNameUserIdUuid)));

        //directory renaming fails
        webTestClient.post()
                .uri("/v1/studies/" + studyNameUserIdUuid + "/rename")
                .header("userId", "userId")
                .body(BodyInserters.fromValue(new RenameStudyAttributes(RENAME_FAIL)))
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody(String.class)
                .isEqualTo("DIRECTORY_REQUEST_FAILED");
        assertTrue(getRequestsDone(1).contains(String.format("/v1/directories/%s/rename/renameFail", studyNameUserIdUuid)));

        webTestClient.post()
                .uri("/v1/studies/" + randomUuid + "/rename")
                .header("userId", "userId")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isNotFound();

        //get available export format
        webTestClient.get()
                .uri("/v1/export-network-formats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("[\"CGMES\",\"UCTE\",\"XIIDM\"]");

        assertTrue(getRequestsDone(1).contains("/v1/export/formats"));

        //export a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/export-network/{format}", studyNameUserIdUuid, "XIIDM")
                .exchange()
                .expectStatus().isOk();

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/export/XIIDM", NETWORK_UUID_STRING)));

        // make public study private
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/private", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(studyNameUserIdUuid, "newName", "userId", "UCTE", "description", true));

        // make private study private should work
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/private", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(studyNameUserIdUuid, "newName", "userId", "UCTE", "description", true));

        // make private study public
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/public", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(studyNameUserIdUuid, "newName", "userId", "UCTE", "description", false));

        // drop the broker message for study deletion (due to right access change)
        output.receive(1000);
        output.receive(1000);
        output.receive(1000);

        // try to change access rights of a non-existing study
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/public", randomUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound();

        // try to change access rights of a non-existing study
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/private", randomUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound();
        UUID studyNameUserId2Uuid = studyRepository.findAll().get(1).getId();

        // try to change access right for a study of another user -> forbidden
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/private", studyNameUserId2Uuid)
                .header("userId", "notAuth")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    public void testLoadFlow() {
        //insert a study
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false);
        UUID studyNameUserIdUuid = studyRepository.findByUserIdAndStudyName("userId", STUDY_NAME).get().getId();

        //run a loadflow
        webTestClient.put()
                .uri("/v1/studies/" + studyNameUserIdUuid + "/loadflow/run")
                .exchange()
                .expectStatus().isOk();
        // assert that the broker message has been sent
        Message<byte[]> messageLfStatus = output.receive(1000);
        assertEquals("", new String(messageLfStatus.getPayload()));
        MessageHeaders headersLF = messageLfStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));

        Message<byte[]> messageLf = output.receive(1000);
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_LOADFLOW, messageLf.getHeaders().get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/run?reportId=%s&reportName=loadflow&overwrite=true", NETWORK_UUID_STRING, NETWORK_UUID_STRING)));

        // check load flow status
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(studyNameUserIdUuid, STUDY_NAME, "userId", "UCTE", DESCRIPTION, false, LoadFlowStatus.CONVERGED));

        //try to run a another loadflow
        webTestClient.put()
                .uri("/v1/studies/" + studyNameUserIdUuid + "/loadflow/run")
                .exchange()
                .expectStatus().isEqualTo(403)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(LOADFLOW_NOT_RUNNABLE.name());

        // get default LoadFlowParameters
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(LOAD_PARAMETERS_JSON);

        // setting loadFlow Parameters
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
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
                        LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                        true,
                        EnumSet.noneOf(Country.class),
                        LoadFlowParameters.ConnectedComponentMode.MAIN))
                )
                .exchange()
                .expectStatus().isOk();

        // getting setted values
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(LOAD_PARAMETERS_JSON2);

        // run loadflow with new parameters
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/loadflow/run", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent
        messageLf = output.receive(1000);
        assertEquals("", new String(messageLf.getPayload()));
        headersLF = messageLf.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));
        output.receive(1000);
        output.receive(1000);
        output.receive(1000);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/run?reportId=%s&reportName=loadflow&overwrite=true", NETWORK_UUID_STRING, NETWORK_UUID_STRING)));

        // get default load flow provider
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("");

        // set load flow provider
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
                .header("userId", "userId")
                .contentType(MediaType.TEXT_PLAIN)
                .body(BodyInserters.fromValue("Hades2"))
                .exchange()
                .expectStatus().isOk();

        messageLfStatus = output.receive(1000);
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, messageLfStatus.getHeaders().get(HEADER_UPDATE_TYPE));

        // get load flow provider
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Hades2");

        // reset load flow provider to default one
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        messageLfStatus = output.receive(1000);
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, messageLfStatus.getHeaders().get(HEADER_UPDATE_TYPE));

        // get default load flow provider again
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("");
    }

    @Test
    public void testSecurityAnalysis() {
        //insert a study
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false);
        UUID studyNameUserIdUuid = studyRepository.findByUserIdAndStudyName("userId", STUDY_NAME).get().getId();

        // security analysis not found
        webTestClient.get()
                .uri("/v1/security-analysis/results/{resultUuid}", NOT_FOUND_SECURITY_ANALYSIS_UUID)
                .exchange()
                .expectStatus().isNotFound();

        // run security analysis
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/security-analysis/run?contingencyListName={contingencyListName}", studyNameUserIdUuid, CONTIGENCY_LIST_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UUID.class)
                .isEqualTo(UUID.fromString(SECURITY_ANALYSIS_UUID));

        Message<byte[]> securityAnalysisStatusMessage = output.receive(1000);
        assertEquals(studyNameUserIdUuid, securityAnalysisStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, securityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(1000);
        assertEquals(studyNameUserIdUuid, securityAnalysisUpdateMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_RESULT, securityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save?contingencyListName=" + CONTIGENCY_LIST_NAME + "&receiver=%257B%2522studyUuid%2522%253A%2522" + studyNameUserIdUuid + "%2522%257D"));

        // get security analysis result
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/security-analysis/result", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(SECURITY_ANALYSIS_RESULT_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/results/%s?limitType", SECURITY_ANALYSIS_UUID)));

        // get security analysis status
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/security-analysis/status", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(SECURITY_ANALYSIS_STATUS_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/results/%s/status", SECURITY_ANALYSIS_UUID)));

        // stop security analysis
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/security-analysis/stop", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk();

        securityAnalysisStatusMessage = output.receive(1000);
        assertEquals(studyNameUserIdUuid, securityAnalysisStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, securityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains("/v1/results/" + SECURITY_ANALYSIS_UUID + "/stop?receiver=%257B%2522studyUuid%2522%253A%2522" + studyNameUserIdUuid + "%2522%257D"));

        // get contingency count
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/contingency-count?contingencyListName={contingencyListName}", studyNameUserIdUuid, CONTIGENCY_LIST_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Integer.class)
                .isEqualTo(1);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/contingency-lists/%s/export?networkUuid=%s", CONTIGENCY_LIST_NAME, NETWORK_UUID_STRING)));
    }

    @Test
    public void testDiagramsAndGraphics() {
        //insert a study
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false);
        UUID studyNameUserIdUuid = studyRepository.findByUserIdAndStudyName("userId", STUDY_NAME).get().getId();
        UUID randomUuid = UUID.randomUUID();

        //get the voltage level diagram svg
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false", studyNameUserIdUuid, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_XML)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("byte");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/svg/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        //get the voltage level diagram svg from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg", randomUuid, "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();

        //get the voltage level diagram svg and metadata
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", studyNameUserIdUuid, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("svgandmetadata");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/svg-and-metadata/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        //get the voltage level diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata", randomUuid, "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();

        // get the substation diagram svg
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/substations/{substationId}/svg?useName=false", studyNameUserIdUuid, "substationId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_XML)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("substation-byte");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substation-svg/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        // get the substation diagram svg from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/substations/{substationId}/svg", randomUuid, "substationId")
                .exchange()
                .expectStatus().isNotFound();

        // get the substation diagram svg and metadata
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/substations/{substationId}/svg-and-metadata?useName=false", studyNameUserIdUuid, "substationId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("substation-svgandmetadata");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substation-svg-and-metadata/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        // get the substation diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/substations/{substationId}/svg-and-metadata", randomUuid, "substationId")
                .exchange()
                .expectStatus().isNotFound();

        //get voltage levels
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/voltage-levels", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(VoltageLevelInfos.class)
                .value(new MatcherJson<>(List.of(
                        VoltageLevelInfos.builder().id("FFR1AA1").name("FFR1AA1").substationId("FFR1AA").build(),
                        VoltageLevelInfos.builder().id("DDE1AA1").name("DDE1AA1").substationId("DDE1AA").build(),
                        VoltageLevelInfos.builder().id("DDE2AA1").name("DDE2AA1").substationId("DDE2AA").build(),
                        VoltageLevelInfos.builder().id("FFR3AA1").name("FFR3AA1").substationId("FFR3AA").build(),
                        VoltageLevelInfos.builder().id("DDE3AA1").name("DDE3AA1").substationId("DDE3AA").build(),
                        VoltageLevelInfos.builder().id("NNL1AA1").name("NNL1AA1").substationId("NNL1AA").build(),
                        VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").substationId("BBE1AA").build(),
                        VoltageLevelInfos.builder().id("BBE2AA1").name("BBE2AA1").substationId("BBE2AA").build(),
                        VoltageLevelInfos.builder().id("NNL2AA1").name("NNL2AA1").substationId("NNL2AA").build(),
                        VoltageLevelInfos.builder().id("NNL3AA1").name("NNL3AA1").substationId("NNL3AA").build()
                )));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/voltage-levels", NETWORK_UUID_STRING)));

        //get the lines-graphics of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/geo-data/lines/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/lines?networkUuid=%s", NETWORK_UUID_STRING)));

        //get the substation-graphics of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/geo-data/substations/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substations?networkUuid=%s", NETWORK_UUID_STRING)));

        //get the lines map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/lines/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/lines/%s", NETWORK_UUID_STRING)));

        //get the substation map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/substations/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substations/%s", NETWORK_UUID_STRING)));

        //get the 2 windings transformers map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/2-windings-transformers/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/2-windings-transformers/%s", NETWORK_UUID_STRING)));

        //get the 3 windings transformers map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/3-windings-transformers/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/3-windings-transformers/%s", NETWORK_UUID_STRING)));

        //get the generators map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/generators/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/generators/%s", NETWORK_UUID_STRING)));

        //get the batteries map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/batteries/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/batteries/%s", NETWORK_UUID_STRING)));

        //get the dangling lines map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/dangling-lines/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/dangling-lines/%s", NETWORK_UUID_STRING)));

        //get the hvdc lines map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/hvdc-lines/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/hvdc-lines/%s", NETWORK_UUID_STRING)));

        //get the lcc converter stations map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/lcc-converter-stations/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/lcc-converter-stations/%s", NETWORK_UUID_STRING)));

        //get the vsc converter stations map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/vsc-converter-stations/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/vsc-converter-stations/%s", NETWORK_UUID_STRING)));

        //get the loads map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/loads/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/loads/%s", NETWORK_UUID_STRING)));

        //get the shunt compensators map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/shunt-compensators/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/shunt-compensators/%s", NETWORK_UUID_STRING)));

        //get the static var compensators map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/static-var-compensators/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/static-var-compensators/%s", NETWORK_UUID_STRING)));

        //get all map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network-map/all/", studyNameUserIdUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/all/%s", NETWORK_UUID_STRING)));
    }

    @Test
    public void testNetworkModificationSwitch() {
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        //update switch
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/switches/{switchId}?open=true", studyNameUserIdUuid, "switchId")
                .exchange()
                .expectStatus().isOk();

        Set<String> substationsSet = ImmutableSet.of("s1", "s2", "s3");
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDY, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(substationsSet, headers.get(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SWITCH, headers.get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/switches/%s?open=true", NETWORK_UUID_STRING, "switchId")));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, STUDY_NAME, "userId", "UCTE", DESCRIPTION, false));
    }

    @Test
    public void testNetworkModificationEquipment() {
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        //update equipment
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/groovy", studyNameUserIdUuid)
                .body(BodyInserters.fromValue("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
                .exchange()
                .expectStatus().isOk();

        Set<String> substationsSet = ImmutableSet.of("s4", "s5", "s6", "s7");
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals("study", headers.get(HEADER_UPDATE_TYPE));
        assertEquals(substationsSet, headers.get(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(studyNameUserIdUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, headers.get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/groovy/", NETWORK_UUID_STRING)));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, STUDY_NAME, "userId", "UCTE", DESCRIPTION, false));
    }

    @Test
    public void testDeleteNetwokModifications() {
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        // get all modifications for the default group of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/network/modifications", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus()
                .isOk();

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/modifications", NETWORK_UUID_STRING)));

        // delete all modifications for the default group of a network
        webTestClient.delete()
                .uri("/v1/studies/{studyUuid}/network/modifications", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus()
                .isOk();

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/modifications", NETWORK_UUID_STRING)));
    }

    @Test
    public void testCreationWithErrorBadCaseFile() {
        // Create study with a bad case file -> error
        createStudy("userId", "newStudy", TEST_FILE_WITH_ERRORS, IMPORTED_CASE_WITH_ERRORS_UUID_STRING, "desc", false,
                "The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'");
    }

    @Test
    public void testCreationWithErrorBadExistingCase() {
        // Create study with a bad case file -> error when importing in the case server
        createStudy("userId", "newStudy", TEST_FILE_IMPORT_ERRORS, null, "desc", false,
                "Error during import in the case server");
    }

    @Test
    public void testCreationWithErrorInImportingTheElementInDirectoryServer() {
        // Create study with a directory server non reachable -> error
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, false, UUID.fromString(PARENT_DIRECTORY_UUID));
    }

    public void testCreationWithErrorNoMessageBadExistingCase() throws Exception {
        // Create study with a bad case file -> error when importing in the case server without message in response body
        createStudy("userId", "newStudy", TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY, null, "desc", false,
            "{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message2\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
    }

    @SneakyThrows
    private void createStudy(String userId, String studyName, UUID caseUuid, String description, boolean isPrivate, String... errorMessage) {
        createStudy(userId, studyName, caseUuid, description, isPrivate, null, errorMessage);
    }

    @SneakyThrows
    private void createStudy(String userId, String studyName, UUID caseUuid, String description, boolean isPrivate, UUID parentUuid, String... errorMessage) {
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}&parentDirectoryUuid={parentUuid}",
                        studyName, caseUuid, description, isPrivate, parentUuid)
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk();

        UUID studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        if (parentUuid == null) {
            // drop the broker message for directory server insertion
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals(userId, headers.get(HEADER_USER_ID));
            assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
            assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
            assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

            // assert that the broker message has been sent a study creation message for creation
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals(userId, headers.get(HEADER_USER_ID));
            assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
            assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
            assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
            assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(HEADER_ERROR));

            // assert that the broker message has been sent a study creation request message for deletion
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals(userId, headers.get(HEADER_USER_ID));
            assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
            assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
            assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        } else {
            // we should intercept an error
            message = output.receive(1000);
        }

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(parentUuid != null ? 2 : 4);
        if (parentUuid != null) {
            assertTrue(requests.contains(String.format("/v1/directories/%s", PARENT_DIRECTORY_UUID)));
            assertTrue(requests.contains(String.format("/v1/cases/%s/exists", CASE_UUID_STRING)));
        } else {
            assertTrue(requests.contains(String.format("/v1/directories/%s", DIRECTORY_SERVER_ROOT_UUID)));
            assertTrue(requests.contains(String.format("/v1/cases/%s/exists", CASE_UUID_STRING)));
            assertTrue(requests.contains(String.format("/v1/cases/%s/format", CASE_UUID_STRING)));
            assertTrue(requests.contains(String.format("/v1/networks?caseUuid=%s", CASE_UUID_STRING)));
        }
    }

    @SneakyThrows
    private void createStudy(String userId, String studyName, String fileName, String caseUuid, String description, boolean isPrivate, String... errorMessage) {
        final WebTestClient.ResponseSpec exchange;
        final UUID studyUuid;
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + fileName))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", fileName, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename(fileName)
                    .contentType(MediaType.TEXT_XML);

            exchange = webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", studyName, description, isPrivate)
                    .header("userId", userId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange();

            studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

            exchange.expectStatus().isOk()
                    .expectBody(BasicStudyInfos.class)
                    .value(createMatcherStudyBasicInfos(studyUuid, userId, studyName, isPrivate));
        }

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // drop the broker message for directory server insertion
        output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(errorMessage.length != 0 ? studyName : null, headers.get(HEADER_STUDY_NAME));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message for deletion
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(caseUuid == null ? errorMessage.length != 0 ? 3 : 2 : errorMessage.length != 0 ? 5 : 4);
        assertTrue(requests.contains(String.format("/v1/directories/%s", DIRECTORY_SERVER_ROOT_UUID)));
        assertTrue(requests.contains("/v1/cases/private"));
        if (caseUuid != null) {
            assertTrue(requests.contains(String.format("/v1/cases/%s/format", caseUuid)));
            assertTrue(requests.contains(String.format("/v1/networks?caseUuid=%s", caseUuid)));
        }
        if (errorMessage.length != 0) {
            assertTrue(requests.contains(String.format("/v1/directories/%s", DIRECTORY_SERVER_ROOT_UUID)));
        }
    }

    @Test
    public void testGetStudyCreationRequests() throws Exception {
        countDownLatch = new CountDownLatch(1);

        //insert a study with a case (multipartfile)
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("blockingCaseFile/cases/private", "testCase.xiidm", "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("blockingCaseFile")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "s3", DESCRIPTION, "true")
                    .header("userId", "userId")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(BasicStudyInfos.class)
                    .value(createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId", "s3", true));
        }

        UUID studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherStudyBasicInfos(studyUuid, "userId", "s3", true));

        countDownLatch.countDown();

        // Study import is asynchronous, we have to wait because our code doesn't allow block until the study creation processing is done
        Thread.sleep(1000);

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .isEqualTo(List.of());

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "s3", "userId", "XIIDM", DESCRIPTION, true));

        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for directory server insertion
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);
        // drop the broker message for directory server insertion
        output.receive(1000);

        // assert that all http requests have been sent to remote services
        var httpRequests = getRequestsDone(4);
        assertTrue(httpRequests.contains(String.format("/v1/directories/%s", DIRECTORY_SERVER_ROOT_UUID)));
        assertTrue(httpRequests.contains("/v1/cases/private"));
        assertTrue(httpRequests.contains(String.format("/v1/cases/%s/format", IMPORTED_BLOCKING_CASE_UUID_STRING)));
        assertTrue(httpRequests.contains(String.format("/v1/networks?caseUuid=%s", IMPORTED_BLOCKING_CASE_UUID_STRING)));

        countDownLatch = new CountDownLatch(1);

        //insert a study
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", "NewStudy", NEW_STUDY_CASE_UUID, DESCRIPTION, "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BasicStudyInfos.class)
                .value(createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId", "NewStudy", false));

        studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherStudyBasicInfos(studyUuid, "userId", "NewStudy", false));

        countDownLatch.countDown();

        // Study import is asynchronous, we have to wait because our code doesn't allow block until the study creation processing is done
        Thread.sleep(1000);

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .isEqualTo(List.of());

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "NewStudy", "userId", "XIIDM", DESCRIPTION, false));

        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for directory server insertion
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(4);
        assertTrue(requests.contains(String.format("/v1/directories/%s", DIRECTORY_SERVER_ROOT_UUID)));
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.contains(String.format("/v1/cases/%s/format", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.contains(String.format("/v1/networks?caseUuid=%s", NEW_STUDY_CASE_UUID)));
    }

    @Test
    public void testUpdateLines() throws Exception {
        createStudy("userId", STUDY_NAME, CASE_UUID, DESCRIPTION, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        // lockout line
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "line12")
                .bodyValue("lockout")
                .exchange()
                .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, ImmutableSet.of("s1", "s2"));

        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "lineFailedId")
                .bodyValue("lockout")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // trip line
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "line23")
                .bodyValue("trip")
                .exchange()
                .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, ImmutableSet.of("s2", "s3"));

        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "lineFailedId")
                .bodyValue("trip")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // energise line end
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "line13")
                .bodyValue("energiseEndOne")
                .exchange()
                .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, ImmutableSet.of("s1", "s3"));

        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "lineFailedId")
                .bodyValue("energiseEndTwo")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // switch on line
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "line13")
                .bodyValue("switchOn")
                .exchange()
                .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, ImmutableSet.of("s1", "s3"));

        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, "lineFailedId")
                .bodyValue("switchOn")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        var requests = getRequestsWithBodyDone(8);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/line12/status", NETWORK_UUID_STRING)) && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/line23/status", NETWORK_UUID_STRING)) && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/line13/status", NETWORK_UUID_STRING)) && r.getBody().equals("energiseEndOne")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/line13/status", NETWORK_UUID_STRING)) && r.getBody().equals("switchOn")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/lineFailedId/status", NETWORK_UUID_STRING)) && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/lineFailedId/status", NETWORK_UUID_STRING)) && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/lineFailedId/status", NETWORK_UUID_STRING)) && r.getBody().equals("energiseEndTwo")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().equals(String.format("/v1/networks/%s/lines/lineFailedId/status", NETWORK_UUID_STRING)) && r.getBody().equals("switchOn")));
    }

    private void checkLineModificationMessagesReceived(UUID studyNameUserIdUuid, Set<String> modifiedSubstationsSet) {
        // assert that the broker message has been sent
        Message<byte[]> messageStudyUpdate = output.receive(1000);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(StudyService.HEADER_STUDY_UUID));
        assertEquals("study", headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE));
        assertEquals(modifiedSubstationsSet, headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        // assert that the broker message has been sent
        Message<byte[]> messageLFStatus = output.receive(1000);
        assertEquals("", new String(messageLFStatus.getPayload()));
        MessageHeaders headersLFStatus = messageLFStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLFStatus.get(StudyService.HEADER_STUDY_UUID));
        assertEquals("loadflow_status", headersLFStatus.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent
        Message<byte[]> messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        MessageHeaders headersSwitch = messageSwitch.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent
        messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        headersSwitch = messageSwitch.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(StudyService.UPDATE_TYPE_LINE, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));
    }

    @After
    public void tearDown() {
        Set<String> httpRequest = null;
        try {
            httpRequest = getRequestsDone(1);
        } catch (NullPointerException e) {
            // Ignoring
        }

        // Shut down the server. Instances cannot be reused.
        try {
            server.shutdown();
        } catch (Exception e) {
            // Ignoring
        }

        assertNull("Should not be any messages", output.receive(1000));
        assertNull("Should not be any http requests", httpRequest);
    }
}
