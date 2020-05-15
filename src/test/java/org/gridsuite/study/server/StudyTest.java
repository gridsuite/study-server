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
import com.powsybl.network.store.client.NetworkStoreService;
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
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.BodyInserters;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.mockito.BDDMockito.given;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RunWith(SpringRunner.class)
@WebFluxTest(StudyController.class)
@EnableWebFlux
@ContextHierarchy({
    @ContextConfiguration(classes = {StudyApplication.class, StudyService.class})
    })
public class StudyTest extends AbstractEmbeddedCassandraSetup {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private StudyService studyService;

    @MockBean
    private NetworkStoreService networkStoreClient;

    private static final String STUDIES_URL = "/v1/studies/{studyName}";
    private static final String DESCRIPTION = "description";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String STUDY_NAME = "studyName";
    private static final String NETWORK_UUID = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID = "11111111-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private final UUID networkUuid = UUID.fromString(NETWORK_UUID);
    private final UUID caseUuid = UUID.fromString(CASE_UUID);
    private final UUID importedCaseUuid = UUID.fromString(IMPORTED_CASE_UUID);
    private final NetworkInfos networkInfos = new NetworkInfos(networkUuid, "20140116_0830_2D4_UX1_pst");

    @Before
    public void setup() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = Importers.importData("XIIDM", dataSource, null);
        given(networkStoreClient.getNetwork(networkUuid)).willReturn(network);
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

        ObjectMapper mapper = new ObjectMapper();
        String networkInfosAsString = mapper.writeValueAsString(networkInfos);
        String importedCaseUuidAsString = mapper.writeValueAsString(importedCaseUuid);

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                switch (Objects.requireNonNull(request.getPath())) {
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
                }
                return new MockResponse().setResponseCode(404);
            }
        };
        server.setDispatcher(dispatcher);

        //empty list
        webTestClient.get()
                .uri("/v1/studies")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        //insert a study
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}", STUDY_NAME, caseUuid, DESCRIPTION)
                .exchange()
                .expectStatus().isOk();

        //insert a study with a non existing case and except exception
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}", "randomStudy", "00000000-0000-0000-0000-000000000000", DESCRIPTION)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class)
                .isEqualTo(CASE_DOESNT_EXISTS);

        webTestClient.get()
                .uri("/v1/studies")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"studyName\":\"studyName\",\"description\":\"description\",\"caseFormat\":\"UCTE\"}]");

        //insert the same study => 409 conflict
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}", STUDY_NAME, caseUuid, DESCRIPTION)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(String.class)
                .isEqualTo(STUDY_ALREADY_EXISTS);

        /*
        //insert a study with a case (multipartfile)
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"));) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/plain", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", "test")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("Content-Disposition", "form-data; name=caseFile; filename=caseFile");

            webTestClient.post()
                    .uri(STUDIES_URL + "?description={description}", "s2", "desc")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk();
        }
        */

        // temp insert : to be replaced with second insert method (with multipart file)
        webTestClient.post()
                .uri("/v1/studies/{studyName}/cases/{caseUuid}?description={description}", "s2", IMPORTED_CASE_UUID, "desc")
                .exchange()
                .expectStatus().isOk();

        // check the study s2
        webTestClient.get()
                .uri(STUDIES_URL, "s2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"name\":\"s2\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"description\":\"desc\",\"caseFormat\":\"XIIDM\",\"caseUuid\":\"11111111-0000-0000-0000-000000000000\",\"casePrivate\":false}");

        //get a non existing study -> 404 not found
        webTestClient.get()
                .uri(STUDIES_URL, "s3")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody();

        //get the voltage level diagram svg
        webTestClient.get()
                .uri("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg?useName=false", STUDY_NAME, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_XML)
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("byte");

        //get the voltage level diagram svg from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg", "notExistingStudy", "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();

        //get the voltage level diagram svg and metadata
        webTestClient.get()
                .uri("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", STUDY_NAME, "voltageLevelId")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("svgandmetadata");

        //get the voltage level diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata", "notExistingStudy", "voltageLevelId")
                .exchange()
                .expectStatus().isNotFound();
        //assertEquals(STUDY_DOESNT_EXISTS, result.getResponse().getContentAsString());

        //get the voltage level diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyName}/network/voltage-levels", STUDY_NAME)
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
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
                .uri("/v1/studies/{studyName}/geo-data/lines/", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the substation-graphics of a network
        webTestClient.get()
                .uri("/v1/studies/{studyName}/geo-data/substations/", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the lines map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyName}/network-map/lines/", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //get the substation map data of a network
        webTestClient.get()
                .uri("/v1/studies/{studyName}/network-map/substations/", STUDY_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        //delete existing study s2
        webTestClient.delete()
                .uri(STUDIES_URL, "s2")
                .exchange()
                .expectStatus().isOk();

        //update switch
        webTestClient.put()
                .uri("/v1/studies/{studyName}/network-modification/switches/{switchId}?open=true", STUDY_NAME, "switchId")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/v1/studies")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"studyName\":\"studyName\",\"description\":\"description\",\"caseFormat\":\"UCTE\"}]");

        //rename the study
        String newStudyName = "newName";
        RenameStudyAttributes renameStudyAttributes = new RenameStudyAttributes(newStudyName);

        webTestClient.post()
                .uri("/v1/studies/" + STUDY_NAME + "/rename")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"name\":\"newName\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"description\":\"description\",\"caseFormat\":\"UCTE\",\"caseUuid\":\"00000000-8cf0-11bd-b23e-10b96e4ef00d\",\"casePrivate\":false}");

        webTestClient.post()
                .uri("/v1/studies/" + STUDY_NAME + "/rename")
                .body(BodyInserters.fromValue(renameStudyAttributes))
                .exchange()
                .expectStatus().isNotFound();

        // Shut down the server. Instances cannot be reused.
        server.shutdown();
    }
}
