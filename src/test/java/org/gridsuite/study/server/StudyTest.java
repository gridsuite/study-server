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
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.RenameStudyAttributes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@ContextHierarchy({
    @ContextConfiguration(classes = {StudyApplication.class, StudyService.class, TestChannelBinderConfiguration.class})
    })
public class StudyTest extends AbstractEmbeddedCassandraSetup {

    @Autowired
    private OutputDestination output;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private StudyService studyService;

    @MockBean
    private NetworkStoreService networkStoreClient;

    private static final String STUDIES_URL = "/v1/studies/{studyName}";
    private static final String STUDY_EXIST_URL = "/v1/{userId}/studies/{studyName}/exists";
    private static final String DESCRIPTION = "description";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String STUDY_NAME = "studyName";
    private static final String NETWORK_UUID = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID = "11111111-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String HEADER_STUDY_NAME = "studyName";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private final UUID networkUuid = UUID.fromString(NETWORK_UUID);
    private final UUID caseUuid = UUID.fromString(CASE_UUID);
    private final UUID importedCaseUuid = UUID.fromString(IMPORTED_CASE_UUID);
    private final NetworkInfos networkInfos = new NetworkInfos(networkUuid, "20140116_0830_2D4_UX1_pst");

    TopLevelDocument<VoltageLevelAttributes> topLevelDocument;

    @Before
    public void setup() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = Importers.importData("XIIDM", dataSource, null);
        given(networkStoreClient.getNetwork(networkUuid)).willReturn(network);

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

        ObjectMapper mapper = new ObjectMapper();
        String networkInfosAsString = mapper.writeValueAsString(networkInfos);
        String importedCaseUuidAsString = mapper.writeValueAsString(importedCaseUuid);
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

                    case "/v1/cases/" + IMPORTED_CASE_UUID + "/format":
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
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks?caseUuid=" + CASE_UUID:
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_UUID:
                    case "/v1/networks?caseName=" + IMPORTED_CASE_UUID:
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/lines?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/substations?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/substations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        return new MockResponse().setBody(" ").setResponseCode(200)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg/" + NETWORK_UUID + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("byte")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg-and-metadata/" + NETWORK_UUID + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("svgandmetadata")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/export/formats":
                        return new MockResponse().setResponseCode(200).setBody("[\"CGMES\",\"UCTE\",\"XIIDM\"]")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/export/XIIDM":
                        return new MockResponse().setResponseCode(200).addHeader("Content-Disposition", "attachment; filename=fileName").setBody("byteData")
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                }
                return new MockResponse().setResponseCode(404);
            }
        };
        server.setDispatcher(dispatcher);

        //empty list
        webTestClient.get()
                .uri("/v1/studies")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        //insert a study
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", STUDY_NAME, caseUuid, DESCRIPTION, "false")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isOk();

        //insert a study with a non existing case and except exception
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", "randomStudy", "00000000-0000-0000-0000-000000000000", DESCRIPTION, "false")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isEqualTo(424)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(CASE_DOESNT_EXISTS);

        webTestClient.get()
                .uri("/v1/studies")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"studyName\":\"studyName\",\"userId\":\"subject\",\"description\":\"description\",\"caseFormat\":\"UCTE\"}]");

        //insert the same study => 409 conflict
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", STUDY_NAME, caseUuid, DESCRIPTION, "false")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(STUDY_ALREADY_EXISTS);

        //insert the same study but with another user (should work)
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}", STUDY_NAME, caseUuid, DESCRIPTION, "true")
                .header("subject", "subject2")
                .exchange()
                .expectStatus().isEqualTo(200);

        //insert a study with a case (multipartfile)
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("caseFile")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "s2", "desc", "true")
                    .header("subject", "subject")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk();
        }

        //Import the same case -> 409 conflict
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                    .filename("caseFile")
                    .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}&isPrivate={isPrivate}", "s2", "desc", "false")
                    .header("subject", "subject")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$")
                    .isEqualTo(STUDY_ALREADY_EXISTS);
        }

        // check the study s2
        webTestClient.get()
                .uri("/v1/subject/studies/{studyName}", "s2")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"name\":\"s2\",\"userId\":\"subject\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"description\":\"desc\",\"caseFormat\":\"XIIDM\",\"caseUuid\":\"11111111-0000-0000-0000-000000000000\",\"casePrivate\":true,\"private\":true}");

        //try to get the study s2 with another user -> unauthorized because study is private
        webTestClient.get()
                .uri("/v1/subject/studies/{studyName}", "s2")
                .header("subject", "subject2")
                .exchange()
                .expectStatus().isForbidden();

        //get a non existing study -> 404 not found
        webTestClient.get()
                .uri("/v1/subject/studies/{studyName}", "s3")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody();

        // check if a non existing study exists
        webTestClient.get()
                .uri(STUDY_EXIST_URL, "subject", "s3")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("false");

        // check study s2 if exists
        webTestClient.get()
                .uri(STUDY_EXIST_URL, "subject", "s2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("true");

        //get the voltage level diagram svg
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg?useName=false", "subject", STUDY_NAME, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_XML)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("byte");

        //get the voltage level diagram svg from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg", "subject", "notExistingStudy", "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();

        //get the voltage level diagram svg and metadata
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", "subject", STUDY_NAME, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("svgandmetadata");

        //get the voltage level diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata", "subject", "notExistingStudy", "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();

        //get voltage levels
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network/voltage-levels", "subject", STUDY_NAME)
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
                .uri("/v1/{userId}/studies/{studyName}/geo-data/lines/", "subject", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the substation-graphics of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/geo-data/substations/", "subject", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the lines map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/lines/", "subject", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the substation map data of a network
        webTestClient.get()
                .uri("/v1/{userId}/studies/{studyName}/network-map/substations/", "subject", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //delete existing study s2
        webTestClient.delete()
                .uri("/v1/subject/studies/{studyName}/", "s2")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isOk();

        //update switch
        webTestClient.put()
                .uri("/v1/{subject}/studies/{studyName}/network-modification/switches/{switchId}?open=true", "subject", STUDY_NAME, "switchId")
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent
        Message<byte[]> messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        MessageHeaders headersSwitch = messageSwitch.getHeaders();
        assertEquals(STUDY_NAME, headersSwitch.get(HEADER_STUDY_NAME));
        assertEquals("switch", headersSwitch.get(HEADER_UPDATE_TYPE));

        webTestClient.get()
                .uri("/v1/studies")
                .header("subject", "subject")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"studyName\":\"studyName\",\"userId\":\"subject\",\"description\":\"description\",\"caseFormat\":\"UCTE\"}]");

        //expect only 1 study (public one) since the other is private and we use another subject
        webTestClient.get()
                .uri("/v1/studies")
                .header("subject", "a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"studyName\":\"studyName\",\"userId\":\"subject\",\"description\":\"description\",\"caseFormat\":\"UCTE\"}]");

        //rename the study
        String newStudyName = "newName";
        RenameStudyAttributes renameStudyAttributes = new RenameStudyAttributes(newStudyName);

        webTestClient.post()
                .uri("/v1/subject/studies/" + STUDY_NAME + "/rename")
                .header("subject", "subject")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"name\":\"newName\",\"userId\":\"subject\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"description\":\"description\",\"caseFormat\":\"UCTE\",\"caseUuid\":\"00000000-8cf0-11bd-b23e-10b96e4ef00d\",\"casePrivate\":false,\"private\":false}");

        webTestClient.post()
                .uri("/v1/subject/studies/" + STUDY_NAME + "/rename")
                .header("subject", "subject")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isNotFound();

        //run a loadflow
        webTestClient.put()
                .uri("/v1/subject/studies/" + "newName" + "/loadflow/run")
                .exchange()
                .expectStatus().isOk();
        // assert that the broker message has been sent
        Message<byte[]> messageLF = output.receive(1000);
        assertEquals("", new String(messageLF.getPayload()));
        MessageHeaders headersLF = messageLF.getHeaders();
        assertEquals("newName", headersLF.get(HEADER_STUDY_NAME));
        assertEquals("loadflow", headersLF.get(HEADER_UPDATE_TYPE));

        //get available export format
        webTestClient.get()
                .uri("/v1/export-network-formats")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("[\"CGMES\",\"UCTE\",\"XIIDM\"]");

        //export a network
        webTestClient.get()
                .uri("/v1/subject/studies/{studyName}/export-network/{format}", newStudyName, "XIIDM")
                .exchange()
                .expectStatus().isOk();

        // Shut down the server. Instances cannot be reused.
        server.shutdown();
    }
}
