/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.modification.dto.VoltageInitModificationInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact.SimpleImpactType;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.dto.voltageinit.parameters.*;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.AlertLevel;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.notification.dto.StudyAlert;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.gridsuite.study.server.service.VoltageInitResultConsumer.*;
import static org.gridsuite.study.server.utils.ImpactUtils.createModificationResultWithElementImpact;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class VoltageInitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitTest.class);

    private static final String CASE_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String VOLTAGE_INIT_RESULT_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120002";

    private static final String VOLTAGE_INIT_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final String VOLTAGE_INIT_CANCEL_FAILED_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120003";

    private static final String VOLTAGE_INIT_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";

    private static final VoltageInitParametersInfos VOLTAGE_INIT_PARAMETERS_INFOS = VoltageInitParametersInfos.builder()
        .voltageLimitsDefault(
            List.of(
                VoltageLimitInfos.builder()
                    .priority(0)
                    .lowVoltageLimit(24.)
                    .highVoltageLimit(552.)
                    .filters(List.of(FilterEquipments.builder().filterId(UUID.fromString("6754396b-3791-4b80-9971-defbf5968fb7")).filterName("testfp").build()))
                    .build()))
        .variableQGenerators(
            List.of(
                FilterEquipments.builder().filterId(UUID.fromString("ff915f2f-578c-4d8c-a267-0135a4323462")).filterName("testf1").build())
        )
        .generatorsSelectionType(EquipmentsSelectionType.ALL_EXCEPT)
        .twoWindingsTransformersSelectionType(EquipmentsSelectionType.NONE_EXCEPT)
        .shuntCompensatorsSelectionType(EquipmentsSelectionType.NONE_EXCEPT)
        .build();

    private static final VoltageInitParametersInfos VOLTAGE_INIT_PARAMETERS_INFOS_2 = VoltageInitParametersInfos.builder()
        .voltageLimitsDefault(
            List.of(
                VoltageLimitInfos.builder()
                    .priority(0)
                    .lowVoltageLimit(24.)
                    .highVoltageLimit(552.)
                    .filters(List.of(FilterEquipments.builder().filterId(UUID.fromString("6754396b-3791-4b80-9971-defbf5968fb7")).filterName("testfp").build()))
                    .build()))
        .build();

    private static final VoltageInitModificationInfos VOLTAGE_INIT_MODIFICATION_INFOS = VoltageInitModificationInfos.builder()
            .uuid(UUID.fromString("254a3b85-14ad-4436-bf62-3e60af831dd1"))
            .date(Instant.parse("2025-03-17T12:00:00Z"))
            .stashed(false)
            .messageType("VoltageInitialization")
            .messageValues("Initial voltage setup completed.")
            .generators(List.of())
            .staticVarCompensators(List.of())
            .vscConverterStations(List.of())
            .shuntCompensators(List.of())
            .transformers(List.of())
            .build();

    private static final String VOLTAGE_INIT_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";

    private static final String WRONG_VOLTAGE_INIT_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-1111-83d3-9e530245c7f4";

    private static final String VOLTAGE_INIT_RESULT_JSON = "{\"version\":\"1.0\"}";

    private static final String VOLTAGE_INIT_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";

    private static final UUID VOLTAGE_INIT_PARAMETERS_UUID = UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING);
    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_VOLTAGE_INIT_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a804-e631246d42d6\",\"name\":\"Profile with valid params\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a804-e631246d42d6\",\"name\":\"Profile with broken params\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";
    private static final String DUPLICATED_PARAMS_JSON = "\"" + PROFILE_VOLTAGE_INIT_DUPLICATED_PARAMETERS_UUID_STRING + "\"";
    private String voltageInitDefaultParametersJson;
    private String voltageInitUpdatedParametersJson;
    private String voltageInitModificationInfosList;

    private static final long TIMEOUT = 1000;

    private static final String MODIFICATIONS_GROUP_UUID = "aaaaaaaa-bbbb-cccc-8c82-1c90300da329";

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
    private NetworkModificationService networkModificationService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ReportService reportService;

    @SpyBean
    private LoadFlowService loadFlowService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    private StateEstimationService stateEstimationService;

    @Autowired
    private VoltageInitService voltageInitService;

    @Autowired
    private UserAdminService userAdminService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String voltageInitResultDestination = "voltageinit.result";
    private final String voltageInitStoppedDestination = "voltageinit.stopped";
    private final String voltageInitFailedDestination = "voltageinit.run.dlx";
    private final String voltageInitCancelFailedDestination = "voltageinit.cancelfailed";
    private final String elementUpdateDestination = "element.update";
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setup(final MockWebServer server) throws Exception {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        Network network = Network.create("test", "IIDM");
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeans(network);

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        voltageInitService.setVoltageInitServerBaseUri(baseUrl);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        loadFlowService.setLoadFlowServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        stateEstimationService.setStateEstimationServerServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        String voltageInitResultUuidStr = objectMapper.writeValueAsString(VOLTAGE_INIT_RESULT_UUID);
        String voltageInitResultUuidStr2 = objectMapper.writeValueAsString(VOLTAGE_INIT_CANCEL_FAILED_UUID);

        String voltageInitErrorResultUuidStr = objectMapper.writeValueAsString(VOLTAGE_INIT_ERROR_RESULT_UUID);

        voltageInitDefaultParametersJson = objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS_INFOS);
        voltageInitUpdatedParametersJson = objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS_INFOS_2);
        voltageInitModificationInfosList = objectMapper.writeValueAsString(Arrays.asList(VOLTAGE_INIT_MODIFICATION_INFOS));

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = Objects.requireNonNull(request.getMethod());
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_3)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", VOLTAGE_INIT_CANCEL_FAILED_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .setHeader(HEADER_REACTIVE_SLACKS_OVER_THRESHOLD, Boolean.TRUE)
                            .setHeader(HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE, 10.)
                            .setHeader(HEADER_VOLTAGE_LEVEL_LIMITS_OUT_OF_NOMINAL_VOLTAGE_RANGE, Boolean.TRUE)
                            .build(), voltageInitResultDestination);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), voltageInitResultUuidStr2);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", VOLTAGE_INIT_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .setHeader(HEADER_REACTIVE_SLACKS_OVER_THRESHOLD, Boolean.TRUE)
                            .setHeader(HEADER_REACTIVE_SLACKS_THRESHOLD_VALUE, 10.)
                            .setHeader(HEADER_VOLTAGE_LEVEL_LIMITS_OUT_OF_NOMINAL_VOLTAGE_RANGE, Boolean.TRUE)
                            .build(), voltageInitResultDestination);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), voltageInitResultUuidStr);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .setHeader("resultUuid", VOLTAGE_INIT_ERROR_RESULT_UUID)
                        .build(), voltageInitFailedDestination);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), voltageInitErrorResultUuidStr);
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), VOLTAGE_INIT_RESULT_JSON);
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), VOLTAGE_INIT_STATUS_JSON);
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + MODIFICATIONS_GROUP_UUID + "\"");
                } else if (path.matches("/v1/groups/.*" + "\\?action=COPY&originGroupUuid=.*")) {
                    Optional<NetworkModificationResult> networkModificationResult =
                            createModificationResultWithElementImpact(SimpleImpactType.MODIFICATION,
                                    IdentifiableType.GENERATOR, "genId", Set.of("s1"));
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(new NetworkModificationsResult(List.of(), List.of(networkModificationResult))));
                } else if (path.matches("/v1/groups/" + MODIFICATIONS_GROUP_UUID + "/network-modifications\\?errorOnGroupNotFound=false&onlyStashed=false&onlyMetadata=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), voltageInitModificationInfosList);
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + VOLTAGE_INIT_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? VOLTAGE_INIT_OTHER_NODE_RESULT_UUID : VOLTAGE_INIT_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", resultUuid)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), voltageInitStoppedDestination);
                    return new MockResponse(200);
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_CANCEL_FAILED_UUID + "/stop.*")) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", VOLTAGE_INIT_CANCEL_FAILED_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%7D")
                            .setHeader("userId", "userId")
                            .setHeader("message", "voltage init could not be cancel")
                            .build(), voltageInitCancelFailedDestination);
                    return new MockResponse(200);
                } else if (path.matches("/v1/results/invalidate-status.*")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/parameters/" + WRONG_VOLTAGE_INIT_PARAMETERS_UUID_STRING)) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/results\\?resultsUuids.*")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "1");
                } else if (path.matches("/v1/reports")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/parameters")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS_UUID_STRING));
                } else if (path.matches("/v1/users/" + NO_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_NO_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_VALID_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_INVALID_PARAMS_JSON);
                } else if (path.matches("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID)) {
                    if (method.equals("GET")) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), voltageInitDefaultParametersJson);
                    } else {
                        //Method PUT
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS_UUID));
                    }
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request KO
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters/" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_PARAMS_JSON);
                } else if (path.matches("/v1/parameters/" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    // profile params get request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), voltageInitUpdatedParametersJson);
                } else if (path.matches("/v1/parameters") && method.equals("POST")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS_UUID));
                } else if (path.matches("/v1/parameters/default") && method.equals("POST")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS_UUID));
                } else if (path.matches("/v1/network-modifications/index\\?networkUuid=.*") && method.equals("DELETE")) {
                    return new MockResponse(200);
                } else {
                    LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                    return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }

        };

        server.setDispatcher(dispatcher);
    }

    private void initMockBeans(Network network) {
        when(networkStoreService.getNetwork(UUID.fromString(NETWORK_UUID_STRING))).thenReturn(network);
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, StudyVoltageInitParameters parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parameters != null
                            ? objectMapper.writeValueAsString(parameters)
                            : objectMapper.writeValueAsString(createStudyVoltageInitParameters(false))))
            .andExpect(status().is(status.value()));

        Message<byte[]> voltageInitStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        Message<byte[]> elementUpdateMessage = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(studyNameUserIdUuid, elementUpdateMessage.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    @Test
    void testVoltageInitParameters(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null, false);
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get initial voltage init parameters
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        assertEquals(createStudyVoltageInitParameters(false), objectMapper.readValue(mvcResult.getResponse().getContentAsString(), StudyVoltageInitParameters.class));

        StudyVoltageInitParameters parameters = createStudyVoltageInitParameters(false, VOLTAGE_INIT_PARAMETERS_INFOS);

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, parameters, "userId", HttpStatus.OK);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters")));

        //checking update is registered
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        assertEquals(parameters, objectMapper.readValue(mvcResult.getResponse().getContentAsString(), StudyVoltageInitParameters.class));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID_STRING)));

        //update voltage init parameters
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, parameters, "userId", HttpStatus.OK);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID_STRING)));

        // insert a study with a wrong voltage init parameters uuid
        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(WRONG_VOLTAGE_INIT_PARAMETERS_UUID_STRING), false);

        // get voltage init parameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyEntity2.getId())).andExpect(
            status().isNotFound());

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + WRONG_VOLTAGE_INIT_PARAMETERS_UUID_STRING)));

    }

    @Test
    void testUpdatingParametersWithSameComputationParametersDoesNotInvalidate(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING), false);
        // Just changing applyModifications value but keeping the same computing parameters
        StudyVoltageInitParameters studyVoltageInitParameters = StudyVoltageInitParameters.builder()
            .applyModifications(false)
            .computationParameters(VOLTAGE_INIT_PARAMETERS_INFOS)
            .build();

        mockMvc.perform(
            post("/v1/studies/{studyUuid}/voltage-init/parameters", studyEntity.getId())
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(studyVoltageInitParameters))).andExpect(
            status().isOk());

        TestUtils.assertRequestMatches("GET", "/v1/parameters/.*", server);

        // STUDY_CHANGED event
        output.receive(1000, studyUpdateDestination);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

    }

    @Test
    void testApplyModificationsWhenParameterIsActivated(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(
            UUID.fromString(NETWORK_UUID_STRING),
            CASE_UUID,
            UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING),
            true);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid,
            UUID.randomUUID(), VARIANT_ID_2, "node 1");
        //run a voltage init analysis
        mockMvc.perform(
            put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/run", studyEntity.getId(), firstRootNetworkUuid, modificationNode1.getId()).header("userId", "userId")
            )
            .andExpect(status().isOk());

        // Running the computation
        TestUtils.assertRequestMatches("POST", "/v1/networks/" + NETWORK_UUID_STRING + "/.*", server);
        // Fetch results to get modification group UUID
        TestUtils.assertRequestMatches("GET", "/v1/results/.*", server);
        // Duplicate modification in the group related to the node
        TestUtils.assertRequestMatches("PUT", "/v1/groups/.*", server);
        // Update modification group UUID in the result
        TestUtils.assertRequestMatches("PUT", "/v1/results/.*/modifications-group-uuid", server);

        // Applying modifications also invalidate all results of the node, so it creates a lot of study update notifications
        IntStream.range(0, 22).forEach(i -> output.receive(1000, studyUpdateDestination));
        // It deletes the voltage-init modification and creates a new one on the node
        IntStream.range(0, 2).forEach(i -> output.receive(1000, elementUpdateDestination));
    }

    @Test
    void testVoltageInit(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING), false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run a voltage init on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        //run a voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);

        checkReactiveSlacksAlertMessagesReceived(studyNameUserIdUuid, 10.);
        checkVoltageLevelLimitsOutOfRangeAlertMessagesReceived(studyNameUserIdUuid);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        // get voltage init result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(VOLTAGE_INIT_RESULT_JSON));
        TestUtils.assertRequestMatches("GET", "/v1/results/" + VOLTAGE_INIT_RESULT_UUID, server);

        // get voltage init status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(VOLTAGE_INIT_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status")));

        mockMvc.perform(
                post("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createStudyVoltageInitParameters(false, VOLTAGE_INIT_PARAMETERS_INFOS_2)))).andExpect(
                status().isOk());

        TestUtils.assertRequestMatches("GET", "/v1/parameters/.*", server);
        TestUtils.assertRequestMatches("PUT", "/v1/parameters/.*", server);
        TestUtils.assertRequestMatches("PUT", "/v1/results/invalidate-status.*", server);

        //remove notif about study updating due to parameters changes
        output.receive(1000);
        output.receive(1000);

        // stop voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode2Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)));

        testResultCount(server); //Test result count
        testDeleteResults(server, 1); //Delete Voltage init results
    }

    @Test
    void testVoltageInitCancelFail(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING), false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode3Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 4");
        UUID modificationNode4Uuid = modificationNode4.getId();

        //run a voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode4Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_3)));

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);

        checkReactiveSlacksAlertMessagesReceived(studyNameUserIdUuid, 10.);
        checkVoltageLevelLimitsOutOfRangeAlertMessagesReceived(studyNameUserIdUuid);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        // stop voltage init analysis fail
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode4Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + VOLTAGE_INIT_CANCEL_FAILED_UUID + "/stop\\?receiver=.*nodeUuid.*")));
        checkCancelFailedMessagesReceived(studyNameUserIdUuid, modificationNode4Uuid, "userId");
    }

    @Test
    void testInsertVoltageInitModifications(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING), false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3", BuildStatus.BUILT);
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run a voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        checkReactiveSlacksAlertMessagesReceived(studyNameUserIdUuid, 10.);
        checkVoltageLevelLimitsOutOfRangeAlertMessagesReceived(studyNameUserIdUuid);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        // just retrieve modifications list from modificationNode3Uuid
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications/voltage-init", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .header("userId", "userId")).andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(2, server).stream().allMatch(r ->
                r.matches("/v1/groups/" + MODIFICATIONS_GROUP_UUID + "/network-modifications\\?errorOnGroupNotFound=false&onlyStashed=false&onlyMetadata=false") ||
                r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid")
        ));

        assertTrue(networkModificationTreeService.getNodeBuildStatus(modificationNode3Uuid, firstRootNetworkUuid).isBuilt());

        // clone and copy modifications to modificationNode3Uuid
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications/voltage-init", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .header("userId", "userId")).andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(4, server).stream().allMatch(r ->
            r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid") ||
                r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status") ||
                r.matches("/v1/groups/.*\\?action=COPY&originGroupUuid=.*")
        ));

        checkInsertVoltageInitModifications(studyNameUserIdUuid, modificationNode3Uuid, firstRootNetworkUuid, true);

        // clone and copy modifications to modificationNode3Uuid with LF result -> node is invalidated
        when(loadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications/voltage-init", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .header("userId", "userId")).andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(7, server).stream().allMatch(r ->
            r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid") ||
                r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status") ||
                r.matches("/v1/results\\?resultsUuids=" + VOLTAGE_INIT_RESULT_UUID) ||
                r.matches("/v1/groups/.*\\?action=COPY&originGroupUuid=.*") ||
                r.matches("/v1/network-modifications/index\\?networkUuid=.*&groupUuids=.*") ||
                r.matches("/v1/reports")
        ));

        checkInsertVoltageInitModifications(studyNameUserIdUuid, modificationNode3Uuid, firstRootNetworkUuid, false);
    }

    private void checkInsertVoltageInitModifications(UUID studyUuid, UUID modificationNodeUuid, UUID rootNetworkUuid, boolean isBuildNode) throws Exception {
        NodeBuildStatus nodeBuildStatus = networkModificationTreeService.getNodeBuildStatus(modificationNodeUuid, rootNetworkUuid);
        assertTrue(isBuildNode ? nodeBuildStatus.isBuilt() : nodeBuildStatus.isNotBuilt());
        checkEquipmentUpdatingMessagesReceived(studyUuid, modificationNodeUuid);
        checkEquipmentMessagesReceived(studyUuid, modificationNodeUuid, NetworkImpactsInfos.builder().impactedSubstationsIds(ImmutableSet.of("s1")).build());
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.NODE_BUILD_STATUS_UPDATED);
        checkUpdateModelsStatusMessagesReceived(studyUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyUuid, "userId");
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_LOADFLOW_WITH_RATIO_TAP_CHANGERS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, NetworkImpactsInfos expectedPayload) throws Exception {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        NetworkImpactsInfos actualPayload = objectMapper.readValue(new String(messageStudyUpdate.getPayload()), new TypeReference<>() { });
        assertThat(expectedPayload, new MatcherJson<>(objectMapper, actualPayload));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkElementUpdatedMessageSent(UUID elementUuid, String userId) {
        Message<byte[]> message = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(elementUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        assertEquals(userId, message.getHeaders().get(NotificationService.HEADER_MODIFIED_BY));
    }

    private void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_FINISHED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentUpdatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    void testNotResetedUuidResultWhenVoltageInitFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING), false);
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), rootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), rootNetworkUuid, resultUuid, VOLTAGE_INITIALIZATION);
        assertTrue(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, VOLTAGE_INITIALIZATION) != null);
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, VOLTAGE_INITIALIZATION));

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(
                MessageBuilder.withPayload("")
                    .setHeader(HEADER_RECEIVER, resultUuidJson)
                    .setHeader("resultUuid", VOLTAGE_INIT_ERROR_RESULT_UUID)
                .build(), voltageInitFailedDestination);
            return resultUuid;
        }).when(studyService).runVoltageInit(any(), any(), any(), any());
        studyService.runVoltageInit(studyEntity.getId(), modificationNode.getId(), rootNetworkUuid, "");

        // Test doesn't reset uuid result in the database
        assertEquals(VOLTAGE_INIT_ERROR_RESULT_UUID, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, VOLTAGE_INITIALIZATION).toString());

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED, updateType);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        checkUpdateModelStatusMessagesReceived(studyUuid, updateTypeToCheck, null);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> voltageInitStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) voltageInitStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        if (otherUpdateTypeToCheck == null) {
            assertEquals(updateTypeToCheck, updateType);
        } else {
            assertTrue(updateType.equals(updateTypeToCheck) || updateType.equals(otherUpdateTypeToCheck));
        }
    }

    private void checkCancelFailedMessagesReceived(UUID studyUuid, UUID nodeUuid, String userId) {
        Message<byte[]> voltageInitStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(UPDATE_TYPE_VOLTAGE_INIT_CANCEL_FAILED, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(userId, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_USER_ID));
        assertEquals("voltage init could not be cancel", voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_ERROR));
    }

    private void testResultCount(final MockWebServer server) throws Exception {
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", VOLTAGE_INITIALIZATION.toString())
                .queryParam("dryRun", "true"))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));
    }

    private void testDeleteResults(final MockWebServer server, int expectedInitialResultCount) throws Exception {
        assertEquals(expectedInitialResultCount, rootNetworkNodeInfoRepository.findAllByVoltageInitResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", VOLTAGE_INITIALIZATION.toString())
                .queryParam("dryRun", "false"))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/results\\?resultsUuids")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
        assertEquals(0, rootNetworkNodeInfoRepository.findAllByVoltageInitResultUuidNotNull().size());
    }

    @Test
    void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID_STRING), false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // No voltage init result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // No voltage init status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // stop non-existing voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/voltage-init/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header("userId", "userId")).andExpect(status().isOk());
    }

    @Test
    void testResetVoltageInitParametersUserHasNoProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, VOLTAGE_INIT_PARAMETERS_UUID, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, null, NO_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID_STRING))); // get/update existing with dft
    }

    @Test
    void testResetVoltageInitParametersUserHasNoParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, VOLTAGE_INIT_PARAMETERS_UUID, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, null, NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID_STRING))); // get/update existing with dft
    }

    @Test
    void testResetVoltageInitParametersUserHasInvalidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, VOLTAGE_INIT_PARAMETERS_UUID, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, null, INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);

        var requests = TestUtils.getRequestsDone(4, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID_STRING))); // get/update existing with dft
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING))); // post duplicate ko
    }

    @Test
    void testResetVoltageInitParametersUserHasValidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, VOLTAGE_INIT_PARAMETERS_UUID, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, null, VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID_STRING)));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
    }

    @Test
    void testResetVoltageInitParametersUserHasValidParamsInProfileButNoExistingVoltageInitParams(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, null, VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID voltageInitParametersUuid, boolean applyModifications) {
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
                UUID.randomUUID(), null, voltageInitParametersUuid, null, null,
                defaultNonEvacuatedEnergyParametersEntity, applyModifications);
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
        jsonObject.put("variantId", variantId);
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

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination, voltageInitResultDestination, voltageInitStoppedDestination, voltageInitFailedDestination, voltageInitCancelFailedDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    private static StudyVoltageInitParameters createStudyVoltageInitParameters(boolean applyModifications) {
        return StudyVoltageInitParameters.builder()
            .applyModifications(applyModifications)
            .build();
    }

    private static StudyVoltageInitParameters createStudyVoltageInitParameters(boolean applyModifications, VoltageInitParametersInfos voltageInitParametersInfos) {
        return StudyVoltageInitParameters.builder()
            .applyModifications(applyModifications)
            .computationParameters(voltageInitParametersInfos)
            .build();
    }

    private void checkReactiveSlacksAlertMessagesReceived(UUID studyUuid, Double thresholdValue) throws Exception {
        Message<byte[]> voltageInitMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, voltageInitMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(STUDY_ALERT, voltageInitMessage.getHeaders().get(HEADER_UPDATE_TYPE));
        assertNotNull(voltageInitMessage.getPayload());
        StudyAlert alert = objectMapper.readValue(new String(voltageInitMessage.getPayload()), StudyAlert.class);
        assertEquals(AlertLevel.WARNING, alert.alertLevel());
        assertEquals("REACTIVE_SLACKS_OVER_THRESHOLD", alert.messageId());
        assertEquals(Map.of("threshold", thresholdValue.toString()), alert.attributes());
    }

    private void checkVoltageLevelLimitsOutOfRangeAlertMessagesReceived(UUID studyUuid) throws Exception {
        Message<byte[]> voltageInitMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, voltageInitMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(STUDY_ALERT, voltageInitMessage.getHeaders().get(HEADER_UPDATE_TYPE));
        assertNotNull(voltageInitMessage.getPayload());
        StudyAlert alert = objectMapper.readValue(new String(voltageInitMessage.getPayload()), StudyAlert.class);
        assertEquals(AlertLevel.WARNING, alert.alertLevel());
        assertEquals("VOLTAGE_LEVEL_LIMITS_OUT_OF_NOMINAL_VOLTAGE_RANGE", alert.messageId());
        assertNull(alert.attributes());
    }
}
