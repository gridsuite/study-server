/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.rootnetworks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.SecurityAnalysisParameters;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.dto.ComputationType.SECURITY_ANALYSIS;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Maissa SOUISSI <maissa.souissi at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class SecurityAnalysisTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAnalysisTest.class);

    private static final String SECURITY_ANALYSIS_RESULT_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID = "11111111-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID = "22222222-9594-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_NODE_UUID = "e3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String CONTINGENCY_LIST_NAME = "ls";
    private String limitTypeJson;
    private static final String SECURITY_ANALYSIS_N_RESULT_JSON = "{\"status\":\"CONVERGED\",\"limitViolationsResult\":{\"limitViolations\":[{\"subjectId\":\"l3\",\"limitType\":\"CURRENT\",\"acceptableDuration\":1200,\"limit\":10.0,\"limitReduction\":1.0,\"value\":11.0,\"side\":\"ONE\"}],\"actionsTaken\":[]},\"networkResult\":{\"branchResults\":[],\"busResults\":[],\"threeWindingsTransformerResults\":[]}}";
    private static final String SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON = "[{\"id\":\"l1\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l1\",\"elementType\":\"BRANCH\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l2\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l2\",\"elementType\":\"GENERATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l3\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l3\",\"elementType\":\"BUSBAR_SECTION\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l4\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l4\",\"elementType\":\"LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l6\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l6\",\"elementType\":\"HVDC_LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l7\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l7\",\"elementType\":\"DANGLING_LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l8\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l8\",\"elementType\":\"SHUNT_COMPENSATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l9\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l9\",\"elementType\":\"TWO_WINDINGS_TRANSFORMER\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"la\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l0\",\"elementType\":\"THREE_WINDINGS_TRANSFORMER\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"lb\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"la\",\"elementType\":\"STATIC_VAR_COMPENSATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]}]";
    private static final String SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON = "[{\"constraintId\":\"l3\",\"contingencies\":[]},{\"constraintId\":\"vl1\",\"contingencies\":[{\"contingencyId\":\"l1\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l1\",\"elementType\":\"BRANCH\"}]},{\"contingencyId\":\"l2\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l2\",\"elementType\":\"GENERATOR\"}]},{\"contingencyId\":\"l3\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l3\",\"elementType\":\"BUSBAR_SECTION\"}]},{\"contingencyId\":\"l4\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l4\",\"elementType\":\"LINE\"}]},{\"contingencyId\":\"l6\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l6\",\"elementType\":\"HVDC_LINE\"}]},{\"contingencyId\":\"l7\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l7\",\"elementType\":\"DANGLING_LINE\"}]},{\"contingencyId\":\"l8\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l8\",\"elementType\":\"SHUNT_COMPENSATOR\"}]},{\"contingencyId\":\"l9\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l9\",\"elementType\":\"TWO_WINDINGS_TRANSFORMER\"}]},{\"contingencyId\":\"la\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l0\",\"elementType\":\"THREE_WINDINGS_TRANSFORMER\"}]},{\"contingencyId\":\"lb\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"la\",\"elementType\":\"STATIC_VAR_COMPENSATOR\"}]}]}]";
    private static final byte[] SECURITY_ANALYSIS_N_RESULT_CSV_ZIPPED = {0x00, 0x01};
    private static final byte[] SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_CSV_ZIPPED = {0x02, 0x03};
    private static final byte[] SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_CSV_ZIPPED = {0x04, 0x03};
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "\"CONVERGED\"";
    private static final String CONTINGENCIES_COUNT = "2";

    public static final String SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON = "{\"lowVoltageAbsoluteThreshold\":0.0,\"lowVoltageProportionalThreshold\":0.0,\"highVoltageAbsoluteThreshold\":0.0,\"highVoltageProportionalThreshold\":0.0,\"flowProportionalThreshold\":0.1}";
    private static final String SECURITY_ANALYSIS_PROFILE_PARAMETERS_JSON = "{\"lowVoltageAbsoluteThreshold\":30.0,\"lowVoltageProportionalThreshold\":0.4,\"highVoltageAbsoluteThreshold\":0.0,\"highVoltageProportionalThreshold\":0.0,\"flowProportionalThreshold\":0.1}";

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final UUID CASE_2_UUID = UUID.fromString(CASE_2_UUID_STRING);
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final UUID CASE_3_UUID = UUID.fromString(CASE_3_UUID_STRING);

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";

    private static final String SECURITY_ANALYSIS_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";
    private static final UUID SECURITY_ANALYSIS_PARAMETERS_UUID = UUID.fromString(SECURITY_ANALYSIS_PARAMETERS_UUID_STRING);
    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid params\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with broken params\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";
    private static final String DUPLICATED_PARAMS_JSON = "\"" + PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";

    private static final String CSV_TRANSLATION_DTO_STRING = "{translationsObject}";

    private static final long TIMEOUT = 1000;

    private static WireMockServer wireMockServer;
    private ComputationServerStubs computationServerStubs;
    private ReportServerStubs reportServerStubs;
    private UserAdminServerStubs userAdminServerStubs;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private ObjectMapper objectMapper;

    private ObjectWriter objectWriter;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private ActionsService actionsService;

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ConsumerService consumerService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String saResultDestination = "sa.result";
    private final String saStoppedDestination = "sa.stopped";
    private final String saFailedDestination = "sa.run.dlx";

    private static final SecurityAnalysisParameters SECURITY_ANALYSIS_PARAMETERS = new SecurityAnalysisParameters();

    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeAll
    static void initWireMock(@Autowired InputDestination input) {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockServer.start();
    }

    @AfterAll
    static void shutdownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.shutdown();
        }
    }

    @BeforeEach
    void setup() throws JsonProcessingException {
        computationServerStubs = new ComputationServerStubs(wireMockServer);
        reportServerStubs = new ReportServerStubs(wireMockServer);
        userAdminServerStubs = new UserAdminServerStubs(wireMockServer);

        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        securityAnalysisService.setSecurityAnalysisServerBaseUri(wireMockServer.baseUrl());
        actionsService.setActionsServerBaseUri(wireMockServer.baseUrl());
        reportService.setReportServerBaseUri(wireMockServer.baseUrl());

        loadFlowService.setLoadFlowServerBaseUri(wireMockServer.baseUrl());
        userAdminService.setUserAdminServerBaseUri(wireMockServer.baseUrl());
        limitTypeJson = objectMapper.writeValueAsString(List.of(LimitViolationType.CURRENT.name(), LimitViolationType.HIGH_VOLTAGE.name()));

    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID securityAnalysisParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
            UUID.randomUUID(), null, securityAnalysisParametersUuid, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(), new TypeReference<>() { });
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
            modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
            .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
            .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
            .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).nodeBuildStatus(NodeBuildStatus.from(buildStatus)).build());

        return modificationNode;
    }

    @Test
    void getResultZippedCsv() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID nodeUuid = modificationNode.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        /*
         * RUN SECURITY ANALYSIS START
         */
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, SECURITY_ANALYSIS_RESULT_UUID);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
            studyUuid, firstRootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME).header(HEADER_USER_ID, "testUserId")).andExpect(status().isOk());

        consumeSAResult(studyUuid, firstRootNetworkUuid, nodeUuid, SECURITY_ANALYSIS_RESULT_UUID);

        /*
         * RUN SECURITY ANALYSIS END
         */

        wireMockServer.stubFor(post(urlPathEqualTo(
            "/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-contingencies-result/csv"
        )).withQueryParam("resultType", equalTo(SecurityAnalysisResultType.NMK_CONTINGENCIES.name()))
            .withRequestBody(equalToJson(objectMapper.writeValueAsString(CSV_TRANSLATION_DTO_STRING)))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .withBody(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_CSV_ZIPPED)
            ));

        // get N security analysis result zipped csv
        wireMockServer.stubFor(post(urlPathEqualTo(
            "/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/n-result/csv"
        )).withRequestBody(equalToJson(objectMapper.writeValueAsString(CSV_TRANSLATION_DTO_STRING)))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .withBody(SECURITY_ANALYSIS_N_RESULT_CSV_ZIPPED) // byte[]
            ));

        mockMvc.perform(
            post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv",
                studyUuid, firstRootNetworkUuid, nodeUuid)
                .queryParam("resultType", SecurityAnalysisResultType.N.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CSV_TRANSLATION_DTO_STRING))
        ).andExpect(status().isOk()).andReturn();

        wireMockServer.verify(
            postRequestedFor(urlPathEqualTo("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/n-result/csv"))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(CSV_TRANSLATION_DTO_STRING)))
        );
        // get NMK_CONTINGENCIES security analysis result zipped csv
        wireMockServer.stubFor(
            post(urlPathEqualTo("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-contingencies-result/csv"))
                .withRequestBody(equalTo(CSV_TRANSLATION_DTO_STRING))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .withBody(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_CSV_ZIPPED)
                )
        );

        MvcResult mvcResult = mockMvc.perform(
                post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv?resultType={resultType}",
                    studyUuid, firstRootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_CONTINGENCIES)
                    .content(CSV_TRANSLATION_DTO_STRING)
                    .contentType(MediaType.TEXT_PLAIN) // matches equalTo() body
            )
            .andExpect(status().isOk())
            .andReturn();

        byte[] byteArrayResult = mvcResult.getResponse().getContentAsByteArray();
        assertArrayEquals(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_CSV_ZIPPED, byteArrayResult);
        wireMockServer.verify(
            postRequestedFor(urlPathEqualTo("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-contingencies-result/csv"))
                .withRequestBody(equalTo(CSV_TRANSLATION_DTO_STRING))
        );
        // get NMK_CONSTRAINTS security analysis result zipped csv
        wireMockServer.stubFor(
            post(urlPathEqualTo("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-constraints-result/csv"))
                .withRequestBody(equalTo(CSV_TRANSLATION_DTO_STRING))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .withBody(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_CSV_ZIPPED)
                )
        );
        mvcResult = mockMvc.perform(
                post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv?resultType={resultType}",
                    studyUuid, firstRootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_LIMIT_VIOLATIONS)
                    .content(CSV_TRANSLATION_DTO_STRING)
                    .contentType(MediaType.TEXT_PLAIN) // important for equalTo()
            )
            .andExpect(status().isOk())
            .andReturn();

        byteArrayResult = mvcResult.getResponse().getContentAsByteArray();
        assertArrayEquals(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_CSV_ZIPPED, byteArrayResult);
    }

    @Test
    void getResultZippedCsvNotFound() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID_3, "node 1");
        UUID nodeUuid = modificationNode.getId();

        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        /*
         * RUN SECURITY ANALYSIS START
         */
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, SECURITY_ANALYSIS_RESULT_UUID);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
            studyUuid, firstRootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME).header(HEADER_USER_ID, "testUserId")).andExpect(status().isOk());

        consumeSAResult(studyUuid, firstRootNetworkUuid, nodeUuid, SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID);

        // get NOT_FOUND security analysis result zipped csv
        wireMockServer.stubFor(
            post(urlPathEqualTo("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/n-result/csv"))
                .withRequestBody(equalTo(CSV_TRANSLATION_DTO_STRING))
                .willReturn(aResponse()
                    .withStatus(404)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)));

        mockMvc.perform(
                post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv?resultType={resultType}",
                    studyUuid, firstRootNetworkUuid, nodeUuid, SecurityAnalysisResultType.N)
                    .content(CSV_TRANSLATION_DTO_STRING)
                    .contentType(MediaType.TEXT_PLAIN)
            )
            .andExpect(status().isNotFound());

        wireMockServer.verify(
            1,
            postRequestedFor(
                urlPathEqualTo("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/n-result/csv")
            ).withRequestBody(equalTo(CSV_TRANSLATION_DTO_STRING))
        );

    }

    @Test
    void testResetUuidResultWhenSAFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), rootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), rootNetworkUuid, resultUuid, SECURITY_ANALYSIS);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SECURITY_ANALYSIS));
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SECURITY_ANALYSIS));

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), saFailedDestination);
            return resultUuid;
        }).when(studyService).runSecurityAnalysis(any(), any(), any(), any(), any());
        assertNotNull(studyEntity.getId());
        studyService.runSecurityAnalysis(studyEntity.getId(), List.of(), modificationNode.getId(), rootNetworkUuid, "");

        // Test reset uuid result in the database
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SECURITY_ANALYSIS));

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED, updateType);
    }

    @Test
    void testSecurityAnalysis() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);

        // attempt run security analysis on root node → forbidden (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={name}",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, CONTINGENCY_LIST_NAME)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isForbidden());

        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlMatching("/v1/security-analysis/.*")));

        testSecurityAnalysisWithRootNetworkUuidAndNodeUuid(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SECURITY_ANALYSIS_RESULT_UUID, SECURITY_ANALYSIS_PARAMETERS);
        testSecurityAnalysisWithRootNetworkUuidAndNodeUuid(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID, null);

        // run additional security analysis for deletion test
        MockHttpServletRequestBuilder requestBuilder = post(
            "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, CONTINGENCY_LIST_NAME
        );
        requestBuilder
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectWriter.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS))
            .header(HEADER_USER_ID, "testUserId");

        mockMvc.perform(requestBuilder)
            .andExpect(status().isOk());

        consumeSAResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SECURITY_ANALYSIS_RESULT_UUID);

        //Test delete all results
        computationServerStubs.stubResultsCount(1);
        computationServerStubs.stubDeleteResults("/v1/supervision/results");
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SECURITY_ANALYSIS.toString())
                .queryParam("dryRun", "true"))
            .andExpect(status().isOk());
        computationServerStubs.verifyResultsCountGet();

        //Delete Security analysis results
        assertEquals(1, rootNetworkNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull().size());
        computationServerStubs.stubDeleteResults("/v1/results");
        reportServerStubs.stubDeleteReport();

        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SECURITY_ANALYSIS.toString())
                .queryParam("dryRun", "false"))
            .andExpect(status().isOk());
        WireMockUtilsCriteria.verifyDeleteRequest(wireMockServer, "/v1/results", Map.of("resultsUuids", matching(".*")));
        reportServerStubs.verifyDeleteReport();
        assertEquals(0, rootNetworkNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull().size());
    }

    private void consumeSAResult(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, String resultUuid) throws JsonProcessingException {
        // Verify that WireMock received exactly 1 POST request to run security analysis

        computationServerStubs.verifyComputationRun(NETWORK_UUID_STRING, Map.of("contingencyListName", WireMock.equalTo(CONTINGENCY_LIST_NAME), "receiver", WireMock.matching(".*" + nodeUuid + ".*")));
        // consume SA result
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        MessageHeaders messageHeaders = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, resultUuidJson));
        consumerService.consumeSaResult().accept(MessageBuilder.createMessage("", messageHeaders));

        Message<byte[]> securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);
        securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT, updateType);
    }

    private void testSecurityAnalysisWithRootNetworkUuidAndNodeUuid(UUID studyUuid,
                                                                    UUID rootNetworkUuid,
                                                                    UUID nodeUuid,
                                                                    String resultUuid,
                                                                    SecurityAnalysisParameters securityAnalysisParameters) throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        // --- 1. Security analysis not found ---
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}",
                studyUuid, rootNetworkUuid, NOT_FOUND_NODE_UUID, SecurityAnalysisResultType.N))
            .andExpect(status().isNotFound());

        // --- 2. Run security analysis ---
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, resultUuid);

        MockHttpServletRequestBuilder requestBuilder = post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
            studyUuid, rootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME);
        if (securityAnalysisParameters != null) {
            requestBuilder.contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(securityAnalysisParameters));
        }
        requestBuilder.header(HEADER_USER_ID, "testUserId");

        mockMvc.perform(requestBuilder).andExpect(status().isOk());
        consumeSAResult(studyUuid, rootNetworkUuid, nodeUuid, resultUuid);

        // --- 3. GET limit-types empty list
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                studyUuid, rootNetworkUuid, nodeUuid, LOAD_FLOW, "limit-types"))
            .andExpectAll(status().isOk(),
                content().string("[]"));

        // --- 4. GET limit-types for security analysis ---
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/limit-types"))
            .willReturn(WireMock.okJson(limitTypeJson)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                studyUuid, rootNetworkUuid, nodeUuid, SECURITY_ANALYSIS, "limit-types"))
            .andExpectAll(status().isOk(),
                content().string(limitTypeJson));

        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlMatching("/v1/results/" + resultUuid + "/limit-types"))
        );

        // --- 5. GET N result ---
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/n-result.*"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(SECURITY_ANALYSIS_N_RESULT_JSON)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&globalFilters=random_globalfilters&sort=random_sort", studyUuid, rootNetworkUuid, nodeUuid, SecurityAnalysisResultType.N)).andExpectAll(
            status().isOk(),
            content().string(SECURITY_ANALYSIS_N_RESULT_JSON));

        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlMatching("/v1/results/" + resultUuid + "/n-result.*"))
        );

        // --- 6. GET NMK_CONTINGENCIES result ---
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid + "/nmk-contingencies-result/paged"))
                .withQueryParam("page", WireMock.matching(".*"))
                .withQueryParam("size", WireMock.matching(".*"))
                .withQueryParam("filters", WireMock.matching(".*"))
                .withQueryParam("globalFilters", WireMock.matching(".*"))
                .withQueryParam("sort", WireMock.matching(".*"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON))
        );

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&globalFilters=random_globalfilters&sort=random_sort", studyUuid, rootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_CONTINGENCIES)).andExpectAll(
            status().isOk(),
            content().string(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON));

        wireMockServer.verify(1,
            WireMock.getRequestedFor(
                WireMock.urlMatching("/v1/results/" + resultUuid + "/nmk-contingencies-result/paged.*")
            )
        );

        // --- 7. GET NMK_CONSTRAINTS (NMK_LIMIT_VIOLATIONS) ---
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/nmk-constraints-result/paged"))
            .willReturn(WireMock.okJson(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&globalFilters=random_globalfilters&sort=random_sort",
                studyUuid, rootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_LIMIT_VIOLATIONS))
            .andExpectAll(status().isOk(), content().string(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON));

        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/nmk-constraints-result/paged")));

        // --- 8. GET security analysis status ---
        computationServerStubs.stubGetResultStatus(resultUuid, SECURITY_ANALYSIS_STATUS_JSON);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/status", studyUuid, rootNetworkUuid, nodeUuid)).andExpectAll(
            status().isOk()).andReturn();
        computationServerStubs.verifyGetResultStatus(resultUuid);

        // stop security analysis
        wireMockServer.stubFor(
            put(urlPathMatching("/v1/results/" + resultUuid + "/stop"))
                .willReturn(ok())
        );

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/stop", studyUuid, rootNetworkUuid, nodeUuid).header("userId", "userId")).andExpect(status().isOk());

        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        Message<String> stoppedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, receiverJson)
            .setHeader("resultUuid", resultUuid)
            .build();
        consumerService.consumeSaStopped().accept(stoppedMessage);
        checkMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        computationServerStubs.verifyComputationStop(resultUuid, Map.of("receiver", WireMock.matching(".*")));

        // get contingency count
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/contingency-lists/count"))
            .withQueryParam("ids", equalTo(CONTINGENCY_LIST_NAME))
            .withQueryParam("networkUuid", equalTo(NETWORK_UUID_STRING))
            .willReturn(okJson(CONTINGENCIES_COUNT))
        );

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/contingency-count?contingencyListName={contingencyListName}",
                studyUuid, rootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        Integer integerResponse = Integer.parseInt(resultAsString);
        Integer expectedResponse = Integer.parseInt(CONTINGENCIES_COUNT);
        assertEquals(expectedResponse, integerResponse);

        // get contingency count with no list

        wireMockServer.stubFor(get(urlPathEqualTo("/v1/contingency-lists/count"))
            .withQueryParam("networkUuid", equalTo(NETWORK_UUID_STRING))
            .willReturn(okJson("0"))
        );
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/contingency-count",
                studyUuid, rootNetworkUuid, nodeUuid))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        Integer integerResponse2 = Integer.parseInt(resultAsString);
        assertEquals(0, integerResponse2);

    }

    private void checkMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(updateType, updateTypeToCheck);
    }

    @Test
    void testSecurityAnalysisParameters() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID studyUuid = studyEntity.getId();
        assertNotNull(studyUuid);
        computationServerStubs.stubGetParametersDefault(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);
        wireMockServer.stubFor(
            post(urlEqualTo("/v1/parameters/default"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING + "\"")
                )
        );

        computationServerStubs.stubParametersGet(SECURITY_ANALYSIS_PARAMETERS_UUID_STRING, SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);
        computationServerStubs.stubGetParametersDefault(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);
        mockMvc.perform(get("/v1/studies/{studyUuid}/security-analysis/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/parameters/default")));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING)));

        assertEquals(SECURITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyUuid).orElseThrow().getSecurityAnalysisParametersUuid());

        String mnBodyJson = objectWriter.writeValueAsString(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING, mnBodyJson);
        mockMvc.perform(
            post("/v1/studies/{studyUuid}/security-analysis/parameters", studyUuid)
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mnBodyJson)
        ).andExpect(status().isOk());

        computationServerStubs.verifyParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING);
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        mockMvc.perform(get("/v1/studies/{studyUuid}/security-analysis/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING)));

        mockMvc.perform(
            post("/v1/studies/{studyUuid}/security-analysis/parameters", studyUuid)
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mnBodyJson)
        ).andExpect(status().isOk());

        wireMockServer.verify(putRequestedFor(urlEqualTo("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING)));

        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertEquals(
            SECURITY_ANALYSIS_PARAMETERS_UUID,
            studyRepository.findById(studyUuid).orElseThrow().getSecurityAnalysisParametersUuid()
        );
    }

    @Test
    void testCreateSecurityAnalysisParameters() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID studyUuid = studyEntity.getId();
        assertNotNull(studyUuid);
        assertNull(studyEntity.getSecurityAnalysisParametersUuid());

        String bodyJson = objectWriter.writeValueAsString(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);
        computationServerStubs.stubCreateParameter(SECURITY_ANALYSIS_PARAMETERS_UUID);
        mockMvc.perform(post("/v1/studies/{studyUuid}/security-analysis/parameters", studyUuid)
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyJson))
            .andExpect(status().isOk());

        computationServerStubs.verifyParameterPost(bodyJson);
        assertEquals(SECURITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyUuid).orElseThrow().getSecurityAnalysisParametersUuid());
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasNoProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        userAdminServerStubs.stubGetUserProfile(NO_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING, SECURITY_ANALYSIS_PROFILE_PARAMETERS_JSON);
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);
        computationServerStubs.verifyParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING);
    }

    @Test
    void testSecurityAnalysisFailedForNotification() throws Exception {
        // --- Setup study and node ---
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID, null);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(
            studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1"
        );
        UUID nodeUuid = modificationNode1.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        //run failing security analysis (because in network 2)
        computationServerStubs.stubComputationRun(NETWORK_UUID_2_STRING, null, SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid, firstRootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        // failed security analysis
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, firstRootNetworkUuid));
        Message<String> failedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, resultUuidJson)
            .setHeader("resultUuid", SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID)
            .build();
        computationServerStubs.verifyComputationRun(NETWORK_UUID_2_STRING, Map.of("variantId", matching(".*")));

        consumerService.consumeSaFailed().accept(failedMessage);

        // message sent by run and save controller to notify frontend security analysis is running and should update SA status
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        // message sent by run and save controller to notify frontend security analysis is running and should update SA status
        checkMessagesReceived(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_FAILED);

        /*
         *  what follows is mostly for test coverage -> a failed message without receiver is sent -> will be ignored by consumer
         */
        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID, null);
        UUID studyUuid2 = studyEntity2.getId();
        UUID rootNodeUuid2 = getRootNode(studyUuid2).getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();
        UUID firstRootNetworkUuid2 = studyTestUtils.getOneRootNetworkUuid(studyUuid2);
        computationServerStubs.stubComputationRun(NETWORK_UUID_3_STRING, null, SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid2, firstRootNetworkUuid2, modificationNode1Uuid2, CONTINGENCY_LIST_NAME)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());
        // failed security analysis without receiver -> no failure message sent to frontend
        failedMessage = MessageBuilder.withPayload("")
            .setHeader("resultUuid", SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID)
            .build();

        // --- Consume failure message ---
        consumerService.consumeSaFailed().accept(failedMessage);
        // message sent by run and save controller to notify frontend security analysis is running and should update SA status
        checkMessagesReceived(studyUuid2, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        computationServerStubs.verifyComputationRun(NETWORK_UUID_3_STRING, Map.of("contingencyListName", WireMock.equalTo(CONTINGENCY_LIST_NAME)));
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasNoParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING, SECURITY_ANALYSIS_PROFILE_PARAMETERS_JSON);
        createOrUpdateParametersAndDoChecks(studyUuid, "", NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        userAdminServerStubs.verifyGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasInvalidParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING, SECURITY_ANALYSIS_PROFILE_PARAMETERS_JSON);
        computationServerStubs.stubParametersDuplicateFromNotFound(PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
        createOrUpdateParametersAndDoChecks(studyUuid, "", INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);

        // --- Verify WireMock requests ---
        userAdminServerStubs.verifyGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING);
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasValidParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        wireMockServer.stubFor(post(urlPathMatching("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .willReturn(ok()));
        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SECURITY_ANALYSIS_PARAMETERS_UUID_STRING, objectWriter.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS));
        computationServerStubs.stubParametersDuplicateFrom(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, DUPLICATED_PARAMS_JSON);
        wireMockServer.stubFor(post(urlPathMatching("/v1/results/invalidate-status.*"))
            .withQueryParam("resultUuid", matching(".*"))
            .willReturn(ok()));

        // --- Run security analysis ---
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid, firstRootNetworkUuid, modificationNode1.getId(), CONTINGENCY_LIST_NAME)
                .content(objectWriter.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));

        createOrUpdateParametersAndDoChecks(studyUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        wireMockServer.verify(postRequestedFor(urlPathMatching("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .withQueryParam("contingencyListName", equalTo(CONTINGENCY_LIST_NAME))
            .withQueryParam("receiver", matching(".*")));

        userAdminServerStubs.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);

        // duplicate parameters ---
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", equalTo(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING)));
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING);
        // verify if any result was invalidated
        List<ServeEvent> invalidateCalls = wireMockServer.getAllServeEvents().stream()
            .filter(e -> e.getRequest().getUrl().startsWith("/v1/results/invalidate-status"))
            .toList();
        assertTrue(invalidateCalls.size() <= 1);
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasValidParamsInProfileButNoExistingSecurityAnalysisParams() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", equalTo(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING))
            .willReturn(WireMock.ok()
                .withBody(DUPLICATED_PARAMS_JSON)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        createOrUpdateParametersAndDoChecks(studyUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        // --- Verify requests ---
        userAdminServerStubs.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", equalTo(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING))
        );
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyUuid, String parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/security-analysis/parameters", studyUuid)
                    .header("userId", userId)
                    .contentType(MediaType.ALL)
                    .content(parameters))
            .andExpect(status().is(status.value()));

        // --- Consume and verify Kafka messages ---
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination, saFailedDestination, saResultDestination, saStoppedDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertQueuesEmptyThenClear(
                destinations, output
            );
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
