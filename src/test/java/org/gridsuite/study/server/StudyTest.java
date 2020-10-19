/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.*;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
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

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.CASE_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.STUDY_ALREADY_EXISTS;
import static org.junit.Assert.assertEquals;
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
public class StudyTest extends AbstractEmbeddedCassandraSetup {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyTest.class);

    private static final String STUDIES_URL = "/v1/studies/{studyName}";
    private static final String STUDY_EXIST_URL = "/v1/{userId}/studies/{studyName}/exists";
    private static final String DESCRIPTION = "description";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String STUDY_NAME = "studyName";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String SECURITY_ANALYSIS_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SECURITY_ANALYSIS_UUID = "e3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String HEADER_STUDY_NAME = "studyName";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_UUID = UUID.fromString(IMPORTED_CASE_UUID_STRING);
    private static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    private static final String CONTIGENCY_LIST_NAME = "ls";
    private static final String SECURITY_ANALYSIS_RESULT_JSON = "{\"version\":\"1.0\",\"preContingencyResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"l3\",\"limitType\":\"CURRENT\",\"acceptableDuration\":1200,\"limit\":10.0,\"limitReduction\":1.0,\"value\":11.0,\"side\":\"ONE\"}],\"actionsTaken\":[]},\"postContingencyResults\":[{\"contingency\":{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}},{\"contingency\":{\"id\":\"l2\",\"elements\":[{\"id\":\"l2\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}}]}";
    private static final String CONTINGENCIES_JSON = "[{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]}]";
    public static final String LOAD_PARAMETERS_JSON = "{\"version\":\"1.3\",\"voltageInitMode\":\"UNIFORM_VALUES\",\"transformerVoltageControlOn\":false,\"phaseShifterRegulationOn\":false,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":false,\"readSlackBus\":false,\"writeSlackBus\":false}";
    public static final String LOAD_PARAMETERS2_JSON = "{\"version\":\"1.3\",\"voltageInitMode\":\"DC_VALUES\",\"transformerVoltageControlOn\":true,\"phaseShifterRegulationOn\":true,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":true,\"readSlackBus\":false,\"writeSlackBus\":true}";

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

    @Before
    public void setup() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = Importers.importData("XIIDM", dataSource, null);
        given(networkStoreClient.getNetwork(NETWORK_UUID)).willReturn(network);

        List<Resource<VoltageLevelAttributes>> data = new ArrayList<>();

        Iterable<VoltageLevel> vls = network.getVoltageLevels();
        vls.forEach(vl -> data.add(new Resource<>(ResourceType.VOLTAGE_LEVEL, vl.getId(), VoltageLevelAttributes.builder().name(vl.getName()).substationId(vl.getSubstation().getId()).build(), null, null)));

        topLevelDocument = new TopLevelDocument<>(data, null);
    }

    @Test
    public void test() throws Exception {
        MockWebServer server = new MockWebServer();
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

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                switch (Objects.requireNonNull(request.getPath())) {
                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/voltage-levels":
                        return new MockResponse().setResponseCode(200).setBody(topLevelDocumentAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/studies/{studyName}/cases/{caseUuid}":
                        return new MockResponse().setResponseCode(200).setBody("CGMES")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/exists":
                    case "/v1/cases/11111111-0000-0000-0000-000000000000/exists":
                        return new MockResponse().setResponseCode(200).setBody("true")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/format":
                        return new MockResponse().setResponseCode(200).setBody("UCTE")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/format":
                        return new MockResponse().setResponseCode(200).setBody("XIIDM")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists":
                        return new MockResponse().setResponseCode(200).setBody("false")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/" + CASE_API_VERSION + "/cases/private":
                        return new MockResponse().setResponseCode(200).setBody(importedCaseUuidAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/" + CASE_API_VERSION + "/cases/11111111-0000-0000-0000-000000000000":

                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/switches/switchId?open=true":
                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/run":
                        return new MockResponse().setResponseCode(200)
                            .setBody("{\n" +
                                "\"metrics\":{\n" +
                                "\"network_0_iterations\":\"7\",\n" +
                                "\"network_0_status\":\"CONVERGED\"\n" +
                                "},\n" +
                                "\"logs\":\"\",\n" +
                                "\"ok\":true\n" +
                                "}")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + CASE_UUID_STRING:
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_UUID_STRING:
                    case "/v1/networks?caseName=" + IMPORTED_CASE_UUID_STRING:
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/lines?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/substations?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/substations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/2-windings-transformers/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/3-windings-transformers/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/generators/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        return new MockResponse().setBody(" ").setResponseCode(200)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg/" + NETWORK_UUID_STRING + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("byte")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("svgandmetadata")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/export/formats":
                        return new MockResponse().setResponseCode(200).setBody("[\"CGMES\",\"UCTE\",\"XIIDM\"]")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/export/XIIDM":
                        return new MockResponse().setResponseCode(200).addHeader("Content-Disposition", "attachment; filename=fileName").setBody("byteData")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/lines/lineId/switches?lockout=true":
                        return new MockResponse().setResponseCode(200);

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

                    default:
                        LOGGER.error("Path not supported: " + request.getPath());
                        return new MockResponse().setResponseCode(404);
                }
            }
        };
        server.setDispatcher(dispatcher);

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
                .expectBodyList(StudyInfos.class)
                .value(studies -> new MatcherStudyInfos(StudyInfos.builder().studyName("studyName").userId("userId").caseFormat("UCTE")
                                    .description("description").creationDate(ZonedDateTime.now(ZoneId.of("UTC"))).loadFlowResult(new LoadFlowResult())
                                    .build()).matchesSafely(studies.get(0)));

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
                .value(new MatcherStudyInfos(StudyInfos.builder().studyName("s2").userId("userId").description("desc").caseFormat("XIIDM").creationDate(ZonedDateTime.now(ZoneId.of("UTC"))).loadFlowResult(new LoadFlowResult()).build()));

        webTestClient.put()
                .uri("/v1/userId/studies/{studyName}/network-modification/lines/{lineId}/switches?lockout=true", "s2", "lineId")
                .exchange()
                .expectStatus().isOk();

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

        //update switch
        webTestClient.put()
                .uri("/v1/{userId}/studies/{studyName}/network-modification/switches/{switchId}?open=true", "userId", STUDY_NAME, "switchId")
                .exchange()
                .expectStatus().isOk();

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
        assertEquals(StudyService.UPDATE_TYPE_SWITCH, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(StudyInfos.class)
                .value(studies -> new MatcherStudyInfos(StudyInfos.builder().studyName("studyName").userId("userId").caseFormat("UCTE")
                        .description("description").creationDate(ZonedDateTime.now(ZoneId.of("UTC"))).loadFlowResult(new LoadFlowResult())
                        .build()).matchesSafely(studies.get(0)));

        //expect only 1 study (public one) since the other is private and we use another userId
        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(StudyInfos.class)
                .value(studies -> new MatcherStudyInfos(StudyInfos.builder().studyName("studyName").userId("a").caseFormat("UCTE")
                        .description("description").creationDate(ZonedDateTime.now(ZoneId.of("UTC"))).loadFlowResult(new LoadFlowResult())
                        .build()).matchesSafely(studies.get(0)));

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
                .value(new MatcherStudyInfos(StudyInfos.builder().studyName("newName").userId("userId").description("description").caseFormat("UCTE").creationDate(ZonedDateTime.now(ZoneId.of("UTC"))).loadFlowResult(new LoadFlowResult()).build()));

        // drop the broker message for study deletion
        output.receive(1000);
        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);

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
        assertEquals(LoadFlowStatus.CONVERGED, Objects.requireNonNull(this.studyService.getStudy("newName", "userId").block()).getLoadFlowResult().getStatus());
        Message<byte[]> messageLf = output.receive(1000);
        assertEquals("newName", messageLf.getHeaders().get(HEADER_STUDY_NAME));
        assertEquals(StudyService.UPDATE_TYPE_LOADFLOW, messageLf.getHeaders().get(HEADER_UPDATE_TYPE));

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

        // get contingency count
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/contingency-count?contingencyListName={contingencyListName}", newStudyName, CONTIGENCY_LIST_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Integer.class)
                .isEqualTo(1);

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
                        true))
                )
                .exchange()
                .expectStatus().isOk();

        // getting setted values
        webTestClient.get()
                .uri("/v1/userId/studies/{studyName}/loadflow/parameters", newStudyName)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(LOAD_PARAMETERS2_JSON);

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

        // Shut down the server. Instances cannot be reused.
        server.shutdown();
    }

    private static class MatcherBasicStudyInfos<T extends BasicStudyInfos> extends TypeSafeMatcher<T> {
        T source;

        public MatcherBasicStudyInfos(T val) {
            this.source = val;
        }

        @Override
        public boolean matchesSafely(T s) {
            return source.getStudyName().equals(s.getStudyName())
                    && source.getUserId().equals(s.getUserId())
                    && s.getCreationDate().toEpochSecond() - source.getCreationDate().toEpochSecond() < 2;
        }

        @Override
        public void describeTo(Description description) {
            description.toString();
        }
    }

    private static class MatcherStudyInfos extends MatcherBasicStudyInfos<StudyInfos> {

        public MatcherStudyInfos(StudyInfos val) {
            super(val);
        }

        @Override
        public boolean matchesSafely(StudyInfos s) {
            return super.matchesSafely(s)
                    && source.getCaseFormat().equals(s.getCaseFormat())
                    && source.getDescription().equals(s.getDescription())
                    && source.getLoadFlowResult().getStatus() == s.getLoadFlowResult().getStatus();
        }
    }
}
