package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.study.server.dto.BasicStudyInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.CaseService;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class StudyServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyServiceTest.class);

    WireMockServer wireMockServer;

    WireMockUtils wireMockUtils;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private CaseService caseService;

    @Autowired
    private NetworkConversionService networkConversionService;

    @Autowired
    ObjectMapper mapper;
    ObjectWriter objectWriter;

    private static final long TIMEOUT = 1000;
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String REIMPORT_NETWORK_IF_NOT_FOUND_HEADER = "reimportNetworkIfNotFound";
    private static final String USER_ID_HEADER = "userId";
    private static final String HEADER_UPDATE_TYPE = "updateType";

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Before
    public void setup() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockUtils = new WireMockUtils(wireMockServer);

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        wireMockServer.start();

        caseService.setCaseServerBaseUri(wireMockServer.baseUrl());
        networkConversionService.setNetworkConversionServerBaseUri(wireMockServer.baseUrl());
    }

    private final String studyUpdateDestination = "study.update";

    @Test
    public void testReimportStudyOnNetworkNotFoundWithExistingCase() throws Exception {
        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("param1", "changedValue1, changedValue2");
        importParameters.put("param2", "changedValue");
        String userId = "userId";

        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);

        when(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.NONE)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + NETWORK_UUID + "' not found"));

        UUID caseExistsStubId = wireMockUtils.stubCaseExists(CASE_UUID.toString(), true);
        UUID postNetworkStubId = wireMockUtils.stubImportNetwork(CASE_UUID.toString(), importParameters, NETWORK_UUID.toString(), "20140116_0830_2D4_UX1_pst", "UCTE");
        UUID disableCaseExpirationStubId = wireMockUtils.stubDisableCaseExpiration(CASE_UUID.toString());

        mockMvc.perform(get("/v1/studies/{studyUuid}/network", studyUuid)
                .param(REIMPORT_NETWORK_IF_NOT_FOUND_HEADER, "true")
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isNotFound());

        // studies updated
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        MessageHeaders headers = message.getHeaders();
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));

        // study reimport done notification
        message = output.receive(TIMEOUT, studyUpdateDestination);
        headers = message.getHeaders();
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_REIMPORT_DONE, headers.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));

        wireMockUtils.verifyCaseExists(caseExistsStubId, CASE_UUID.toString());
        wireMockUtils.verifyImportNetwork(postNetworkStubId, CASE_UUID.toString());
        Thread.sleep(4000);
        wireMockUtils.verifyDisableCaseExpiration(disableCaseExpirationStubId, CASE_UUID.toString());
    }

    @Test
    public void testReimportStudyOnNetworkNotFoundWithMissingCase() throws Exception {
        Map<String, Object> importParameters = new HashMap<>();
        String userId = "userId";

        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);

        when(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.NONE)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + NETWORK_UUID + "' not found"));

        UUID caseExistsStubId = wireMockUtils.stubCaseExists(CASE_UUID.toString(), false);

        mockMvc.perform(get("/v1/studies/{studyUuid}/network", studyUuid)
                .param(REIMPORT_NETWORK_IF_NOT_FOUND_HEADER, "true")
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isInternalServerError(), content().string(StudyException.Type.BROKEN_STUDY.name()));

        wireMockUtils.verifyCaseExists(caseExistsStubId, CASE_UUID.toString());
    }

    private UUID createStudy(String userId, UUID caseUuid, Map<String, Object> importParameters) throws Exception {
        // mock API calls

        UUID caseExistsStubId = wireMockUtils.stubCaseExists(caseUuid.toString(), true);

        UUID postNetworkStubId = wireMockUtils.stubImportNetwork(caseUuid.toString(), importParameters, NETWORK_UUID.toString(), "20140116_0830_2D4_UX1_pst", "UCTE");

        UUID disableCaseExpirationStubId = wireMockUtils.stubDisableCaseExpiration(caseUuid.toString());

        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid).header("userId", userId))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        assertStudyCreation(studyUuid, userId);

        // assert API calls have been made
        wireMockUtils.verifyCaseExists(caseExistsStubId, caseUuid.toString());
        wireMockUtils.verifyImportNetwork(postNetworkStubId, caseUuid.toString());
        wireMockUtils.verifyDisableCaseExpiration(disableCaseExpirationStubId, caseUuid.toString());

        return studyUuid;
    }

    private void assertStudyCreation(UUID studyUuid, String userId, String... errorMessage) {
        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT, studyUpdateDestination);  // message for first modification node creation

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(NotificationService.HEADER_ERROR));

        assertTrue(studyRepository.findById(studyUuid).isPresent());
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
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
