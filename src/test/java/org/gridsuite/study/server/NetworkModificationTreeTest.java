/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.gridsuite.study.server.service.NetworkModificationTreeService.ROOT_NODE_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockWebServerExtension.class)
@AutoConfigureWebTestClient
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NetworkModificationTreeTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkModificationTreeTest.class);
    private static final String NODE_UPDATED = "nodeUpdated";
    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private OutputDestination output;

    @Autowired
    private ObjectMapper objectMapper;

    private ObjectWriter objectWriter;

    @Autowired
    private CaseService caseService;

    @Autowired
    private NetworkConversionService networkConversionService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private NonEvacuatedEnergyService nonEvacuatedEnergyService;

    @Autowired
    private NetworkMapService networkMapService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    private StateEstimationService stateEstimationService;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;

    @Autowired
    private LoadFlowService loadflowService;

    @Autowired
    private GeoDataService geoDataService;

    @Autowired
    private ActionsService actionsService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private VariantManager variantManager;

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final UUID MODIFICATION_GROUP_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_GROUP_UUID_2 = UUID.randomUUID();
    private static final UUID MODIFICATION_GROUP_UUID_3 = UUID.randomUUID();
    private static final String MODIFICATION1_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String MODIFICATION2_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef111";
    private static final String MODIFICATION_GROUP_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef222";
    private static final String USER_ID_HEADER = "userId";

    @MockBean
    private Network network;

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";

    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private RootNetworkRepository rootNetworkRepository;
    @Autowired
    private RootNetworkService rootNetworkService;
    @Autowired
    private StudyService studyService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setUp(final MockWebServer server) {
        Configuration.defaultConfiguration();
        MockitoAnnotations.initMocks(this);
        objectMapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        given(networkStoreService.getNetwork(NETWORK_UUID)).willReturn(network);
        given(network.getVariantManager()).willReturn(variantManager);
        given(variantManager.getVariantIds()).willReturn(List.of(VARIANT_ID, VARIANT_ID_2));
        doNothing().when(networkStoreService).flush(isA(Network.class));
        doNothing().when(variantManager).removeVariant(VARIANT_ID);

        Configuration.setDefaults(new Configuration.Defaults() {
            private final JsonProvider jsonProvider = new JacksonJsonProvider(objectMapper);
            private final MappingProvider mappingProvider = new JacksonMappingProvider(objectMapper);

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        caseService.setCaseServerBaseUri(baseUrl);
        networkConversionService.setNetworkConversionServerBaseUri(baseUrl);
        singleLineDiagramService.setSingleLineDiagramServerBaseUri(baseUrl);
        geoDataService.setGeoDataServerBaseUri(baseUrl);
        networkMapService.setNetworkMapServerBaseUri(baseUrl);
        loadflowService.setLoadFlowServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        actionsService.setActionsServerBaseUri(baseUrl);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        nonEvacuatedEnergyService.setSensitivityAnalysisServerBaseUri(baseUrl);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        stateEstimationService.setStateEstimationServerServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());

                if ((path.matches("/v1/results/.*") || path.matches("/v1/non-evacuated-energy/results/.*")) && request.getMethod().equals("DELETE")) {
                    return new MockResponse(HttpStatus.OK.value());
                } else if (path.matches("/v1/network-modifications.*")) {
                    return new MockResponse(HttpStatus.OK.value());
                } else if (path.matches("/v1/groups/" + MODIFICATION_GROUP_UUID + "/network-modifications-count.*") && request.getMethod().equals("GET")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(0));
                } else if (path.matches("/v1/groups/" + MODIFICATION_GROUP_UUID_2 + "/network-modifications-count.*") && request.getMethod().equals("GET")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(2));
                } else if (path.matches("/v1/groups/" + MODIFICATION_GROUP_UUID_3 + "/network-modifications-count.*") && request.getMethod().equals("GET")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(0));
                } else if (path.matches("/v1/groups/" + MODIFICATION_GROUP_UUID + "/.*") && request.getMethod().equals("GET")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(List.of()));
                } else if (path.matches("/v1/groups/" + MODIFICATION_GROUP_UUID_2 + "/.*") && request.getMethod().equals("GET")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(List.of("S1", "S2")));
                } else if (path.matches("/v1/groups/" + MODIFICATION_GROUP_UUID_3 + "/.*") && request.getMethod().equals("GET")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(List.of()));
                } else if (path.matches("/v1/groups/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse(HttpStatus.OK.value());
                } else if (path.matches("/v1/reports") && request.getMethod().equals("DELETE")) {
                    return new MockResponse(HttpStatus.OK.value());
                } else {
                    LOGGER.error("Path not supported: {}", request.getPath());
                    return new MockResponse(HttpStatus.NOT_FOUND.value());
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @AfterEach
    void tearDown() {
        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, ELEMENT_UPDATE_DESTINATION);
        networkModificationNodeInfoRepository.deleteAll();
        rootNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        studyRepository.deleteAll();
        TestUtils.assertQueuesEmptyThenClear(destinations, output);
    }

    private static StudyEntity createDummyStudy(UUID networkUuid) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID())
            .shortCircuitParametersUuid(UUID.randomUUID())
            .build();
        studyEntity.addRootNetwork(RootNetworkEntity.builder()
            .id(UUID.randomUUID())
            .name("rootNetworkName")
            .caseFormat("").caseUuid(UUID.randomUUID())
            .reportUuid(UUID.randomUUID())
            .caseName("caseName1")
            .networkId("netId")
            .networkUuid(networkUuid).build());
        return studyEntity;
    }

    @Test
    void testStudyWithNoNodes() {
        StudyEntity studyEntity = createDummyStudy(NETWORK_UUID);
        var study = studyRepository.save(studyEntity);

        UUID studyUuid = study.getId();
        assertThrows(StudyException.class, () -> networkModificationTreeService.getStudyRootNodeUuid(studyUuid), "ELEMENT_NOT_FOUND");
    }

    @Test
    void testGetRoot() throws Exception {
        StudyEntity study = insertDummyStudy();
        RootNode root = getRootNode(study.getId());

        assertEquals(ROOT_NODE_NAME, root.getName());
        assertEquals(study.getId(), root.getStudyId());
        assertEquals(0, root.getChildren().size());

        mockMvc.perform(get("/v1/studies/{studyUuid}/tree", UUID.randomUUID()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/studies/{studyUuid}/tree", study.getId()))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/studies/{studyUuid}/tree/nodes/{id}?deleteChildren={delete}", study.getId(), root.getId(), false).header(USER_ID_HEADER, "userId"))
            .andExpect(status().is4xxClientError());
    }

    private AbstractNode getNode(UUID studyUuid, UUID idNode) throws Exception {
        return getNode(studyUuid, idNode, null);
    }

    private AbstractNode getNode(UUID studyUuid, UUID idNode, UUID rootNetworkUuid) throws Exception {

        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, idNode)
                .param("rootNetworkUuid", rootNetworkUuid != null ? rootNetworkUuid.toString() : null))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { }
        );
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return getRootNode(study, null);
    }

    private RootNode getRootNode(UUID study, UUID rootNetworkUuid) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study)
                .param("rootNetworkUuid", rootNetworkUuid != null ? rootNetworkUuid.toString() : null))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(), new TypeReference<>() { });
    }

    @Test
    void testNodeCreation() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(root.getStudyId());
        // Check build status initialized to NOT_BUILT if null
        final NetworkModificationNode node1 = buildNetworkModificationNode("not_built", "not built node", MODIFICATION_GROUP_UUID_2, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1, userId);
        root = getRootNode(root.getStudyId(), firstRootNetworkUuid);
        List<AbstractNode> children = root.getChildren();
        assertEquals(1, children.size());
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) children.get(0);
        assertFalse(networkModificationNode.getNodeBuildStatus().isBuilt());
        assertEquals("not_built", networkModificationNode.getName());
        assertEquals("not built node", networkModificationNode.getDescription());
        deleteNode(root.getStudyId(), children, false, Set.of(children.get(0)), true, userId);
        // Check built status correctly initialized
        final NetworkModificationNode node2 = buildNetworkModificationNode("built", "built node", MODIFICATION_GROUP_UUID, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node2, userId);
        root = getRootNode(root.getStudyId(), firstRootNetworkUuid);
        children = root.getChildren();
        assertEquals(1, children.size());
        networkModificationNode = (NetworkModificationNode) children.get(0);
        assertTrue(networkModificationNode.getNodeBuildStatus().isBuilt());
        assertEquals("built", networkModificationNode.getName());
        assertEquals("built node", networkModificationNode.getDescription());
        deleteNode(root.getStudyId(), children, false, Set.of(children.get(0)), userId);
    }

    @Test
    void testNodeStashAndRestore() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        UUID studyId = root.getStudyId();
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyId);
        // Check build status initialized to NOT_BUILT if null
        final NetworkModificationNode node1 = buildNetworkModificationNode("not_built", "not built node", MODIFICATION_GROUP_UUID_2, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);

        createNode(root.getStudyId(), root, node1, userId);
        root = getRootNode(root.getStudyId(), firstRootNetworkUuid);
        List<AbstractNode> children = root.getChildren();
        assertEquals(1, children.size());
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) children.get(0);
        assertFalse(networkModificationNode.getNodeBuildStatus().isBuilt());
        assertEquals("not_built", networkModificationNode.getName());
        assertEquals("not built node", networkModificationNode.getDescription());

        //stash 1 node and do the checks
        stashNode(root.getStudyId(), children.get(0), false, Set.of(children.get(0)), userId);
        var stashedNode = nodeRepository.findById(children.get(0).getId()).orElseThrow();
        assertTrue(stashedNode.isStashed());
        assertNull(stashedNode.getParentNode());
        assertNotNull(stashedNode.getStashDate());
        assertTrue(getNode(studyId, root.getId()).getChildren().isEmpty());

        //create this tree
        //       root
        //       /  \
        //      n1  n4
        //     /  \
        //    n2  n3
        final NetworkModificationNode n1 = buildNetworkModificationNode("n1", "n1", UUID.randomUUID(), "variant1", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(studyId, root, n1, userId);
        final NetworkModificationNode n2 = buildNetworkModificationNode("n2", "n2", UUID.randomUUID(), "variant2", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(studyId, n1, n2, userId);
        final NetworkModificationNode n3 = buildNetworkModificationNode("n3", "n3", UUID.randomUUID(), "variant3", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(studyId, n1, n3, userId);

        final NetworkModificationNode n4 = buildNetworkModificationNode("n4", "n4", UUID.randomUUID(), "variant4", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(studyId, root, n4, userId);

        //Try to stash the node n1 so it should stash n1 and its subtree (n2 and n3)
        stashNode(root.getStudyId(), n1, true, Set.of(n1, n2, n3), userId);
        stashNode(root.getStudyId(), n4, true, Set.of(n4), userId);

        var stashedNode1 = nodeRepository.findById(n1.getId()).orElseThrow();
        assertTrue(stashedNode1.isStashed());
        assertNull(stashedNode.getParentNode());
        assertNotNull(stashedNode.getStashDate());

        var stashedNode2 = nodeRepository.findById(n2.getId()).orElseThrow();
        assertTrue(stashedNode2.isStashed());
        //it should still have its parent since we only unlink the first node and keep the rest chained
        assertEquals(n1.getId(), stashedNode2.getParentNode().getIdNode());
        assertNotNull(stashedNode2.getStashDate());

        var stashedNode3 = nodeRepository.findById(n3.getId()).orElseThrow();
        assertTrue(stashedNode3.isStashed());
        //it should still have its parent since we only unlink the first node and keep the rest chained
        assertEquals(n1.getId(), stashedNode3.getParentNode().getIdNode());
        assertNotNull(stashedNode3.getStashDate());

        var stashedNode4 = nodeRepository.findById(n4.getId()).orElseThrow();
        assertTrue(stashedNode4.isStashed());
        assertNotNull(stashedNode4.getStashDate());

        var result = mockMvc.perform(get("/v1/studies/{studyUuid}/tree/nodes/stash", root.getStudyId())
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk())
                .andReturn();

        //Only the first level nodes should appear
        assertTrue(result.getResponse().getContentAsString().contains(n1.getId().toString()));
        assertFalse(result.getResponse().getContentAsString().contains(n2.getId().toString()));
        assertFalse(result.getResponse().getContentAsString().contains(n3.getId().toString()));
        assertTrue(result.getResponse().getContentAsString().contains(n4.getId().toString()));

        //And now we restore the tree we just stashed
        restoreNode(studyId, List.of(stashedNode1.getIdNode(), stashedNode4.getIdNode()), root.getId(), userId);

        var restoredNode1 = nodeRepository.findById(n1.getId()).orElseThrow();
        assertFalse(restoredNode1.isStashed());
        assertNotNull(restoredNode1.getParentNode());
        assertNull(restoredNode1.getStashDate());

        var restoredNode2 = nodeRepository.findById(n2.getId()).orElseThrow();
        assertFalse(restoredNode2.isStashed());
        assertEquals(n1.getId(), restoredNode2.getParentNode().getIdNode());
        assertNull(restoredNode2.getStashDate());

        var restoredNode3 = nodeRepository.findById(n3.getId()).orElseThrow();
        assertFalse(restoredNode3.isStashed());
        assertEquals(n1.getId(), restoredNode3.getParentNode().getIdNode());
        assertNull(restoredNode3.getStashDate());

        var restoredNode4 = nodeRepository.findById(n4.getId()).orElseThrow();
        assertFalse(restoredNode4.isStashed());
        assertNotNull(restoredNode4.getParentNode());
        assertNull(restoredNode4.getStashDate());

        result = mockMvc.perform(get("/v1/studies/{studyUuid}/tree/nodes/stash", root.getStudyId())
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk())
                .andReturn();
        //Only the first level nodes should appear
        assertFalse(result.getResponse().getContentAsString().contains(n1.getId().toString()));
        assertFalse(result.getResponse().getContentAsString().contains(n2.getId().toString()));
        assertFalse(result.getResponse().getContentAsString().contains(n3.getId().toString()));
        assertFalse(result.getResponse().getContentAsString().contains(n4.getId().toString()));
    }

    private UUID createNodeTree() throws Exception {
        /*  create following small node tree, and return the root studyId
                root
               /    \
              n1    n2
                    /  \
                   n3  n4
         */
        String userId = "userId";
        RootNode root = createRoot();
        UUID rootId = root.getId();
        final NetworkModificationNode node1 = buildNetworkModificationNode("n1", "zzz", MODIFICATION_GROUP_UUID, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModificationNode("n2", "", MODIFICATION_GROUP_UUID_2, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1, userId);
        createNode(root.getStudyId(), root, node2, userId);
        // need to re-access to see the whole tree
        root = getRootNode(root.getStudyId());
        List<AbstractNode> children = root.getChildren();
        assertEquals(2, children.size());
        UUID n1Id = children.get(0).getId();
        UUID n2Id = children.get(1).getId();
        // add 2 children to n2
        node2.setName("n3");
        node1.setName("n4");
        createNode(root.getStudyId(), children.get(1), node2, userId);
        createNode(root.getStudyId(), children.get(1), node1, userId);
        // need to re-access to see the whole tree
        root = getRootNode(root.getStudyId());
        children = root.getChildren();
        assertEquals(2, children.size());
        List<AbstractNode> n2Children = children.get(1).getChildren();
        assertEquals(2, n2Children.size());
        UUID n3Id = n2Children.get(0).getId();
        UUID n4Id = n2Children.get(1).getId();
        return root.getStudyId();
    }

    @Test
    void testNodeModificationInfos() throws Exception {
        UUID rootStudyId = createNodeTree();
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(rootStudyId);
        RootNode root = getRootNode(rootStudyId, firstRootNetworkUuid);
        UUID rootId = root.getId();

        RootNodeInfoEntity rootInfos = rootNodeInfoRepository.findById(rootId).orElseThrow(() -> new StudyException(StudyException.Type.NODE_NOT_FOUND));
        assertEquals(rootId, rootInfos.getId());

        List<AbstractNode> children = root.getChildren();
        assertEquals(2, children.size());
        NetworkModificationNode n1 = (NetworkModificationNode) children.get(0);
        NetworkModificationNodeInfoEntity n1Infos = networkModificationTreeService.getNetworkModificationNodeInfoEntity(n1.getId());
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(n1.getId(), firstRootNetworkUuid).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        assertEquals("n1", n1.getName());
        assertEquals("zzz", n1.getDescription());
        assertFalse(n1.getNodeBuildStatus().isBuilt());
        assertEquals(n1.getId(), n1Infos.getId());
        assertEquals(MODIFICATION_GROUP_UUID, n1Infos.getModificationGroupUuid());
        assertEquals(VARIANT_ID, rootNetworkNodeInfoEntity.getVariantId());
        UUID badUuid = UUID.randomUUID();
        assertThrows(StudyException.class, () -> networkModificationTreeService.getNetworkModificationNodeInfoEntity(badUuid), "ELEMENT_NOT_FOUND");
    }

    @Test
    void testNodeAncestor() throws Exception {
        UUID rootStudyId = createNodeTree();

        // get node ID foreach node
        RootNode root = getRootNode(rootStudyId);
        UUID rootId = root.getId();
        List<AbstractNode> children = root.getChildren();
        assertEquals(2, children.size());
        UUID n1Id = children.get(0).getId();
        UUID n2Id = children.get(1).getId();
        List<AbstractNode> n2Children = children.get(1).getChildren();
        assertEquals(2, n2Children.size());
        UUID n3Id = n2Children.get(0).getId();
        UUID n4Id = n2Children.get(1).getId();
        UUID badUuid = UUID.randomUUID();

        assertTrue(networkModificationTreeService.hasAncestor(rootId, rootId));
        assertFalse(networkModificationTreeService.hasAncestor(rootId, n1Id));
        assertTrue(networkModificationTreeService.hasAncestor(n1Id, rootId));
        assertTrue(networkModificationTreeService.hasAncestor(n3Id, rootId));
        assertTrue(networkModificationTreeService.hasAncestor(n4Id, rootId));
        assertTrue(networkModificationTreeService.hasAncestor(n4Id, n2Id));
        assertFalse(networkModificationTreeService.hasAncestor(n4Id, n1Id));
        assertFalse(networkModificationTreeService.hasAncestor(n3Id, badUuid));
        assertThrows(StudyException.class, () -> networkModificationTreeService.hasAncestor(badUuid, rootId), "ELEMENT_NOT_FOUND");
    }

    @Test
    void testNodeManipulation() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModificationNode("hypo 1", "potamus", MODIFICATION_GROUP_UUID, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModificationNode("loadflow", "dance", MODIFICATION_GROUP_UUID_2, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node4 = buildNetworkModificationNode("hypo 2", "potamus", MODIFICATION_GROUP_UUID_3, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1, userId);
        createNode(root.getStudyId(), root, node2, userId);
        UUID firstRootNetwork = studyTestUtils.getStudyFirstRootNetworkUuid(root.getStudyId());
        root = getRootNode(root.getStudyId(), firstRootNetwork);

        List<AbstractNode> children = root.getChildren();
        /*  expected :
                root
               /    \
           node1     node2
        */
        assertChildrenEquals(Set.of(node1, node2), root.getChildren());

        node2.setName("niark");
        node1.setName("condriak");
        node1.setModificationGroupUuid(UUID.randomUUID());
        createNode(root.getStudyId(), children.get(1), node2, userId);
        createNode(root.getStudyId(), children.get(1), node1, userId);

        /*  expected
                root
               /    \
           node1      node2
                    /    \
        node(condriak)   node(niark)
         */

        root = getRootNode(root.getStudyId(), firstRootNetwork);
        AbstractNode child;
        if (root.getChildren().get(0).getName().equals(children.get(1).getName())) {
            child = root.getChildren().get(0);
        } else {
            child = root.getChildren().get(1);
        }
        children = child.getChildren();
        assertChildrenEquals(Set.of(node1, node2), children);

        deleteNode(root.getStudyId(), List.of(child), false, Set.of(child), true, userId);

        /*  expected
              root
            /   |   \
          node node node
        */

        root = getRootNode(root.getStudyId(), firstRootNetwork);
        assertEquals(3, root.getChildren().size());
        child = root.getChildren().get(0);
        createNode(root.getStudyId(), child, node4, userId);

        deleteNode(root.getStudyId(), List.of(child), true, Set.of(child, node4), userId);

        /* expected
             root
              |
             node
         */
        root = getRootNode(root.getStudyId(), firstRootNetwork);
        assertEquals(2, root.getChildren().size());
        assertEquals(3, nodeRepository.findAll().size());

        NetworkModificationNode node3 = buildNetworkModificationNode("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node3, userId);

        networkModificationTreeService.doDeleteTree(root.getStudyId());
        assertEquals(0, nodeRepository.findAll().size());

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", root.getStudyId(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(node1))
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

    }

    private void deleteNode(UUID studyUuid, List<AbstractNode> child, boolean deleteChildren, Set<AbstractNode> expectedDeletion, String userId) throws Exception {
        deleteNode(studyUuid, child, deleteChildren, expectedDeletion, false, userId);
    }

    private void deleteNode(UUID studyUuid, List<AbstractNode> child, boolean deleteChildren, Set<AbstractNode> expectedDeletion, boolean nodeWithModification, String userId) throws Exception {
        List<UUID> children = child.stream()
                .flatMap(e -> e.getChildren().stream())
                .map(AbstractNode::getId)
                .toList();

        String deleteNodeUuid = child.stream().map(AbstractNode::getId).map(UUID::toString).reduce("", (a1, a2) -> a1 + "," + a2).substring(1);
        mockMvc.perform(delete("/v1/studies/{studyUuid}/tree/nodes?deleteChildren={delete}", studyUuid, deleteChildren).header(USER_ID_HEADER, "userId")
                        .queryParam("ids", deleteNodeUuid))
            .andExpect(status().isOk());

        checkElementUpdatedMessageSent(studyUuid, userId);

        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        if (expectedDeletion != null) {
            Collection<UUID> deletedId = (Collection<UUID>) mess.getHeaders().get(NotificationService.HEADER_NODES);
            assertNotNull(deletedId);
            assertEquals(expectedDeletion.size(), deletedId.size());
            deletedId.forEach(id ->
                assertTrue(expectedDeletion.stream().anyMatch(node -> node.getId().equals(id))));
        }

        if (nodeWithModification) {
            var message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
            while (message != null) {
                Collection<UUID> updatedIds = NODE_BUILD_STATUS_UPDATED.equals(message.getHeaders().get(HEADER_UPDATE_TYPE)) ?
                        (Collection<UUID>) message.getHeaders().get(NotificationService.HEADER_NODES) :
                        List.of((UUID) message.getHeaders().get(NotificationService.HEADER_NODE));
                updatedIds.forEach(id -> assertTrue(children.contains(id)));
                message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
            }
        }
    }

    private void stashNode(UUID studyUuid, AbstractNode child, boolean stashChildren, Set<AbstractNode> expectedStash, String userId) throws Exception {
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}/stash?stashChildren={stash}", studyUuid, child.getId(), stashChildren).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());

        checkElementUpdatedMessageSent(studyUuid, userId);

        //first message is node build status being reset and then is the node deleted
        var message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NODE_BUILD_STATUS_UPDATED, message.getHeaders().get(HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        Collection<UUID> stashedId = (Collection<UUID>) message.getHeaders().get(NotificationService.HEADER_NODES);
        assertNotNull(stashedId);
        assertEquals(expectedStash.size(), stashedId.size());
        stashedId.forEach(id ->
                assertTrue(expectedStash.stream().anyMatch(node -> node.getId().equals(id))));
    }

    private void restoreNode(UUID studyUuid, List<UUID> nodeIds, UUID anchorNodeId, String userId) throws Exception {
        String param = nodeIds.stream().map(UUID::toString).reduce("", (a1, a2) -> a1 + "," + a2).substring(1);
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/restore?anchorNodeId={anchorNodeId}", studyUuid, anchorNodeId).header(USER_ID_HEADER, userId)
                        .queryParam("ids", param))
                .andExpect(status().isOk());

        for (int i = 0; i < nodeIds.size(); i++) {
            var message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
            assertTrue(nodeIds.contains(message.getHeaders().get(HEADER_NEW_NODE)) || anchorNodeId.equals(message.getHeaders().get(HEADER_NEW_NODE)));
        }
    }

    private static void assertChildrenEquals(Set<AbstractNode> original, List<AbstractNode> children) {
        assertEquals(original.size(), children.size());
        children.forEach(node -> {
            Optional<AbstractNode> found = original.stream().filter(s -> node.getId().equals(s.getId())).findFirst();
            assertTrue(found.isPresent());
            assertNodeEquals(found.get(), node);
        });
    }

    @Test
    void testNodeInsertion() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode networkModification1 = buildNetworkModificationNode("hypo 1", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode networkModification2 = buildNetworkModificationNode("hypo 2", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode networkModification3 = buildNetworkModificationNode("hypo 3", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);

        /* trying to insert before root */
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}?mode=BEFORE", root.getStudyId(), root.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(networkModification1))
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().is4xxClientError());

        createNode(root.getStudyId(), root, networkModification1, userId);
        createNode(root.getStudyId(), root, networkModification2, userId);
        root = getRootNode(root.getStudyId());
        /* root
            / \
           n1  n2
         */
        AbstractNode unchangedNode = root.getChildren().get(0);
        AbstractNode willBeMoved = root.getChildren().get(1);
        insertNode(root.getStudyId(), willBeMoved, networkModification3, InsertMode.BEFORE, root, userId);
        /* root
            / \
           n3  n2
           /
          n1
         */
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().stream().filter(child -> child.getId().equals(unchangedNode.getId())).count());
        AbstractNode newNode = root.getChildren().get(0).getId().equals(unchangedNode.getId()) ? root.getChildren().get(1) : root.getChildren().get(1);
        assertEquals(willBeMoved.getId(), newNode.getChildren().get(0).getId());

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", root.getStudyId(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(networkModification1))
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

    }

    @Test
    void testInsertAfter() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModificationNode("hypo", "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModificationNode("loadflow 1", "dance", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node3 = buildNetworkModificationNode("loadflow 2", "dance", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node2, userId);
        createNode(root.getStudyId(), root, node3, userId);
        root = getRootNode(root.getStudyId());
        var originalChildren = root.getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        insertNode(root.getStudyId(), root, node1, InsertMode.AFTER, root, userId);
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        var grandChildren = getRootNode(root.getStudyId()).getChildren().get(0).getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        assertEquals(originalChildren, grandChildren);

        UUID rootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(root.getStudyId());
        assertEquals(VARIANT_ID, networkModificationTreeService.getVariantId(node1.getId(), rootNetworkUuid));

        UUID nodeUuid = node2.getId();
        assertNotNull(networkModificationTreeService.getModificationGroupUuid(nodeUuid));
        assertNotNull(networkModificationTreeService.getReportUuid(nodeUuid, rootNetworkUuid));
        assertFalse(networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid).isEmpty());

        List<RootNetworkInfos> rootNetworkEntities = rootNetworkService.getStudyRootNetworkInfosWithRootNetworkNodeInfos(root.getStudyId());
        assertEquals(1, rootNetworkEntities.size());
        assertEquals(3, rootNetworkEntities.get(0).getRootNetworkNodeInfos().size());
    }

    @Test
    void testNodeUpdate() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(root.getStudyId());
        final NetworkModificationNode node1 = buildNetworkModificationNode("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1, userId);
        node1.setName("grunt");
        node1.setModificationGroupUuid(UUID.randomUUID());
        root = getRootNode(root.getStudyId());
        node1.setId(root.getChildren().get(0).getId());
        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(node1))
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        checkElementUpdatedMessageSent(root.getStudyId(), userId);
        root = getRootNode(root.getStudyId(), firstRootNetworkUuid);
        assertEquals(1, root.getChildren().size());
        assertNodeEquals(node1, root.getChildren().get(0));

        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(mess);
        var header = mess.getHeaders();
        assertEquals(root.getStudyId(), header.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, header.get(HEADER_UPDATE_TYPE));
        Collection<UUID> updated = (Collection<UUID>) header.get(NotificationService.HEADER_NODES);
        assertNotNull(updated);
        assertEquals(1, updated.size());
        updated.forEach(id -> assertEquals(node1.getId(), id));

        var justANameUpdate = NetworkModificationNode.builder()
            .name("My taylor is rich!").id(node1.getId()).build();

        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(justANameUpdate))
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        assertEquals(NODE_RENAMED, output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION).getHeaders().get(HEADER_UPDATE_TYPE));
        checkElementUpdatedMessageSent(root.getStudyId(), userId);

        var newNode = getNode(root.getStudyId(), node1.getId(), firstRootNetworkUuid);
        node1.setName(justANameUpdate.getName());
        assertNodeEquals(node1, newNode);

        node1.setId(UUID.randomUUID());
        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(node1))
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateNodesColumnPositions() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModificationNode("nod", "silently", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModificationNode("nodding", "politely", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1, userId);
        createNode(root.getStudyId(), root, node2, userId);
        assertNull(node1.getColumnPosition());
        node1.setColumnPosition(1);
        node2.setColumnPosition(0);
        List<NetworkModificationNode> nodes = List.of(node1, node2);

        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes/{parentUuid}/children-column-positions", root.getStudyId(), root.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(nodes))
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());

        List<UUID> nodesUuids = List.of(node2.getId(), node1.getId());
        for (NetworkModificationNodeInfoEntity entity : networkModificationNodeInfoRepository.findAllById(nodesUuids)) {
            assertEquals(entity.getId().equals(node2.getId()) ? 0 : 1, entity.getColumnPosition());
        }

        checkColumnsChangedMessageSent(root.getStudyId(), root.getId(), nodesUuids);
        checkElementUpdatedMessageSent(root.getStudyId(), userId);
    }

    // This test is for a part of the code that is not used yet
    // We update a node description (this is not used in the front) and we assume that it will emit a nodeUpdated notif
    // If it's not the case or if this test causes problems feel free to update it / remove it as needed
    @Test
    void testNodeDescriptionUpdate() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModificationNode("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1, userId);

        var nodeDescriptionUpdate = NetworkModificationNode.builder()
                .description("My taylor is rich!").id(node1.getId()).build();

        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(nodeDescriptionUpdate))
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        assertEquals(NODE_UPDATED, output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION).getHeaders().get(HEADER_UPDATE_TYPE));
        checkElementUpdatedMessageSent(root.getStudyId(), userId);
    }

    @Test
    void testLightNode() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModificationNode("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode hypo2 = buildNetworkModificationNode("hypo 2", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode hypo3 = buildNetworkModificationNode("hypo 3", "potamus", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, hypo, userId);
        createNode(root.getStudyId(), root, hypo2, userId);
        createNode(root.getStudyId(), root, hypo3, userId);
        AbstractNode node = getNode(root.getStudyId(), root.getId());
        assertEquals(3, node.getChildrenIds().size());
    }

    @Test
    void testGetParentNode() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        UUID rootId = root.getId();
        final NetworkModificationNode node1 = buildNetworkModificationNode("hypo", "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModificationNode("hypo 1", "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node3 = buildNetworkModificationNode("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node4 = buildNetworkModificationNode("loadflow 1", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node5 = buildNetworkModificationNode("loadflow 2", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node6 = buildNetworkModificationNode("loadflow 3", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);

        createNode(root.getStudyId(), root, node1, userId);
        createNode(root.getStudyId(), node1, node3, userId);
        createNode(root.getStudyId(), node3, node2, userId);
        createNode(root.getStudyId(), node2, node4, userId);
        createNode(root.getStudyId(), node4, node5, userId);
        createNode(root.getStudyId(), node5, node6, userId);

        assertEquals(node5.getId(), networkModificationTreeService.getParentNode(node6.getId(), NodeType.NETWORK_MODIFICATION));
        assertEquals(node3.getId(), networkModificationTreeService.getParentNode(node2.getId(), NodeType.NETWORK_MODIFICATION));
        assertEquals(rootId, networkModificationTreeService.getParentNode(node5.getId(), NodeType.ROOT));
        assertEquals(rootId, networkModificationTreeService.getParentNode(rootId, NodeType.ROOT));

        UUID badUuid = UUID.randomUUID();
        assertThrows(StudyException.class, () -> networkModificationTreeService.getParentNode(badUuid, NodeType.ROOT), "ELEMENT_NOT_FOUND");
        assertThrows(StudyException.class, () -> networkModificationTreeService.getParentNode(rootId, NodeType.NETWORK_MODIFICATION), "ELEMENT_NOT_FOUND");
    }

    @Test
    void testGetLastParentNodeBuilt() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModificationNode("hypo 1", "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModificationNode("hypo 2", "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node3 = buildNetworkModificationNode("hypo 3", "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node4 = buildNetworkModificationNode("loadflow 1", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node5 = buildNetworkModificationNode("loadflow 2", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node6 = buildNetworkModificationNode("loadflow 3", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node7 = buildNetworkModificationNode("loadflow 4", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node8 = buildNetworkModificationNode("loadflow 5", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node9 = buildNetworkModificationNode("loadflow 6", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node10 = buildNetworkModificationNode("loadflow 7", "dance", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);

        createNode(root.getStudyId(), root, node1, userId);
        createNode(root.getStudyId(), node1, node4, userId);
        createNode(root.getStudyId(), node4, node2, userId);
        createNode(root.getStudyId(), node2, node5, userId);
        createNode(root.getStudyId(), node5, node6, userId);
        createNode(root.getStudyId(), node6, node7, userId);
        createNode(root.getStudyId(), node7, node8, userId);
        createNode(root.getStudyId(), node8, node3, userId);
        createNode(root.getStudyId(), node3, node10, userId);
        createNode(root.getStudyId(), node10, node9, userId);

        UUID rootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(root.getStudyId());
        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuiltUuid(node7.getId(), rootNetworkUuid));
        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuiltUuid(node9.getId(), rootNetworkUuid));
        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuiltUuid(node3.getId(), rootNetworkUuid));
    }

    private void createNode(UUID studyUuid, AbstractNode parentNode, NetworkModificationNode newNode, String userId) throws Exception {
        newNode.setId(null);

        // Only for tests. Need to remove when all tests are rewritten without the variantID to identify a test in the MockWebServer
        UUID modificationGroupUuid = newNode.getModificationGroupUuid();
        String newNodeBodyJson = objectWriter.writeValueAsString(newNode);
        JSONObject jsonObject = new JSONObject(newNodeBodyJson);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        newNodeBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNode.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(newNodeBodyJson)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkElementUpdatedMessageSent(studyUuid, userId);
        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(mess);
        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(newNode.getId(), studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder()
                .variantId(newNode.getVariantId())
                .nodeBuildStatus(newNode.getNodeBuildStatus())
                .dynamicSimulationResultUuid(newNode.getDynamicSimulationResultUuid())
                .loadFlowResultUuid(newNode.getLoadFlowResultUuid())
                .securityAnalysisResultUuid(newNode.getSecurityAnalysisResultUuid())
                .sensitivityAnalysisResultUuid(newNode.getSensitivityAnalysisResultUuid())
                .nonEvacuatedEnergyResultUuid(newNode.getNonEvacuatedEnergyResultUuid())
                .shortCircuitAnalysisResultUuid(newNode.getShortCircuitAnalysisResultUuid())
                .oneBusShortCircuitAnalysisResultUuid(newNode.getOneBusShortCircuitAnalysisResultUuid())
                .stateEstimationResultUuid(newNode.getStateEstimationResultUuid())
                .build()
        );
    }

    private void insertNode(UUID studyUuid, AbstractNode parentNode, NetworkModificationNode newNode, InsertMode mode, AbstractNode newParentNode, String userId) throws Exception {
        newNode.setId(null);

        // Only for tests. Need to remove when all tests are rewritten without the variantID to identify a test in the MockWebServer
        String variantId = newNode.getVariantId();
        UUID modificationGroupUuid = newNode.getModificationGroupUuid();
        String newNodeBodyJson = objectWriter.writeValueAsString(newNode);
        JSONObject jsonObject = new JSONObject(newNodeBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        newNodeBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}?mode={mode}", studyUuid, parentNode.getId(), mode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(newNodeBodyJson)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        checkElementUpdatedMessageSent(studyUuid, userId);

        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.NODE_CREATED, mess.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(newParentNode.getId(), mess.getHeaders().get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(mode.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        assertEquals(parentNode.getId(), mess.getHeaders().get(NotificationService.HEADER_REFERENCE_NODE_UUID));

        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));

        rootNetworkNodeInfoService.updateRootNetworkNode(newNode.getId(), studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).nodeBuildStatus(newNode.getNodeBuildStatus()).build());
    }

    @NotNull
    private StudyEntity insertDummyStudy() {
        StudyEntity studyEntity = createDummyStudy(NETWORK_UUID);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private RootNode createRoot() {
        var study = insertDummyStudy();
        AtomicReference<RootNode> result = new AtomicReference<>();
        result.set(networkModificationTreeService.getStudyTree(study.getId(), null));
        return result.get();
    }

    private static NetworkModificationNode buildNetworkModificationNode(String name, String description, UUID modificationGroupUuid, String variantId,
        UUID loadFlowResultUuid, UUID securityAnalysisResultUuid, UUID sensitivityAnalysisResultUuid, UUID nonEvacuatedEnergyResultUuid, UUID shortCircuitAnalysisResultUuid, UUID oneBusShortCircuitAnalysisResultUuid, UUID stateEstimationResultUuid, BuildStatus buildStatus) {
        return NetworkModificationNode.builder()
            .name(name)
            .description(description)
            .modificationGroupUuid(modificationGroupUuid)
            .variantId(variantId)
            .loadFlowResultUuid(loadFlowResultUuid)
            .securityAnalysisResultUuid(securityAnalysisResultUuid)
            .sensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid)
            .nonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid)
            .shortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid)
            .oneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid)
            .stateEstimationResultUuid(stateEstimationResultUuid)
            .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
            .children(Collections.emptyList()).build();
    }

    private static void assertNodeEquals(AbstractNode expected, AbstractNode current) {
        assertEquals(expected.getName(), current.getName());
        assertEquals(expected.getDescription(), current.getDescription());
        assertEquals(expected.getChildren().size(), current.getChildren().size());
        assertEquals(expected.getType(), current.getType());
        if (expected.getType() == NodeType.NETWORK_MODIFICATION) {
            assertModificationNodeEquals(expected, current);
        }
    }

    private static void assertModificationNodeEquals(AbstractNode expected, AbstractNode current) {
        NetworkModificationNode currentModificationNode = (NetworkModificationNode) current;
        NetworkModificationNode expectedModificationNode = (NetworkModificationNode) expected;

        assertEquals(expectedModificationNode.getLoadFlowResultUuid(), currentModificationNode.getLoadFlowResultUuid());
        assertEquals(expectedModificationNode.getSecurityAnalysisResultUuid(), currentModificationNode.getSecurityAnalysisResultUuid());
        assertEquals(expectedModificationNode.getSensitivityAnalysisResultUuid(), currentModificationNode.getSensitivityAnalysisResultUuid());
        assertEquals(expectedModificationNode.getNonEvacuatedEnergyResultUuid(), currentModificationNode.getNonEvacuatedEnergyResultUuid());
        assertEquals(expectedModificationNode.getShortCircuitAnalysisResultUuid(), currentModificationNode.getShortCircuitAnalysisResultUuid());
        assertEquals(expectedModificationNode.getOneBusShortCircuitAnalysisResultUuid(), currentModificationNode.getOneBusShortCircuitAnalysisResultUuid());
        assertEquals(expectedModificationNode.getStateEstimationResultUuid(), currentModificationNode.getStateEstimationResultUuid());
        assertEquals(expectedModificationNode.getNodeBuildStatus(), currentModificationNode.getNodeBuildStatus());
    }

    @Test
    void testNodeName() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        String nodeName = "node 1";
        final NetworkModificationNode node = buildNetworkModificationNode(nodeName, "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        UUID studyUuid = root.getStudyId();
        createNode(studyUuid, root, node, userId);
        createNodeFail(studyUuid, root, root);
        createNodeForbidden(studyUuid, root, node);

        node.setName("node 2");
        createNode(studyUuid, root, node, userId);

        String nodeName1 = getNodeName(studyUuid);
        final NetworkModificationNode node1 = buildNetworkModificationNode(nodeName1, "potamus", null, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(studyUuid, root, node1, userId);
        String nodeName2 = getNodeName(studyUuid);

        assertNotEquals(nodeName, nodeName1);
        assertNotEquals(nodeName, nodeName2);
        assertNotEquals(nodeName1, nodeName2);
    }

    private void createNodeFail(UUID studyUuid, AbstractNode parentNode, AbstractNode newNode) throws Exception {
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectWriter.writeValueAsString(newNode))
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isBadRequest());
    }

    private void createNodeForbidden(UUID studyUuid, AbstractNode parentNode, AbstractNode newNode) throws Exception {
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNode.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(newNode))
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
    }

    private String getNodeName(UUID studyUuid) throws Exception {
        return mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/nextUniqueName", studyUuid))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void testGetNetworkModificationsNode() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        NetworkModificationNode node = buildNetworkModificationNode("modification node 1", "", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node, userId);

        String bodyError = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", root.getStudyId(), node.getId()))
            .andExpect(status().isInternalServerError())
            .andReturn().getResponse().getContentAsString();

        assertEquals(new StudyException(StudyException.Type.GET_MODIFICATIONS_FAILED, HttpStatus.NOT_FOUND.toString()).getMessage(), bodyError);

        // No network modification for a root node
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", root.getStudyId(), root.getId()))
            .andExpect(status().isNotFound());

        node = buildNetworkModificationNode("modification node 2", "", MODIFICATION_GROUP_UUID, VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node, userId);
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", root.getStudyId(), node.getId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    void testGetNetworkModificationsToRestoreByNode() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(root.getStudyId());
        NetworkModificationNode node = buildNetworkModificationNode("modification node 1", "", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node, userId);

        String bodyError = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", root.getStudyId(), node.getId(), true))
            .andExpect(status().isInternalServerError())
            .andReturn().getResponse().getContentAsString();

        assertEquals(new StudyException(StudyException.Type.GET_MODIFICATIONS_FAILED, HttpStatus.NOT_FOUND.toString()).getMessage(), bodyError);

        // No network modification for a root node
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", root.getStudyId(), root.getId(), true))
            .andExpect(status().isNotFound());

        node = buildNetworkModificationNode("modification node 3", "", UUID.fromString(MODIFICATION_GROUP_UUID_STRING), VARIANT_ID, null, null, null, null, null, null, null, BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node, userId);
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications?uuids={modificationID1}&stashed=false&rootNetworkUuid={rootNetworkUuid}", root.getStudyId(), node.getId(), MODIFICATION1_UUID_STRING, firstRootNetworkUuid)
            .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());

        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION));
    }

    @Test
    void testGetNetworkModificationsToStashByNode() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(root.getStudyId());
        NetworkModificationNode node = buildNetworkModificationNode("modification node 1", "", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node, userId);

        String bodyError = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", root.getStudyId(), node.getId(), false))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertEquals(new StudyException(StudyException.Type.GET_MODIFICATIONS_FAILED, HttpStatus.NOT_FOUND.toString()).getMessage(), bodyError);

        // No network modification for a root node
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", root.getStudyId(), root.getId(), false))
                .andExpect(status().isNotFound());

        node = buildNetworkModificationNode("modification node 3", "", UUID.fromString(MODIFICATION_GROUP_UUID_STRING), VARIANT_ID, null, null, null, null, null, null, null, BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node, userId);
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications?uuids={modificationID1}&stashed=true&rootNetworkUuid={rootNetworkUuid}", root.getStudyId(), node.getId(), MODIFICATION1_UUID_STRING, firstRootNetworkUuid)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION));
        assertNotNull(output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION));

    }

    @Test
    void testUpdateBuildStatus() throws Exception {
        Pair<UUID, NetworkModificationNode> result = createTreeForBuildStatus();
        UUID leafNodeId = result.getSecond().getId();
        UUID studyUuid = result.getFirst();
        UUID rootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);

        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT_WITH_WARNING));
        assertEquals(BuildStatus.BUILT_WITH_WARNING, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));

        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT_WITH_ERROR));
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getLocalBuildStatus());
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));

        // keep the previous status (BUILT_WITH_ERROR) because it has higher severity
        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT_WITH_WARNING));
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        // no update because the status didn't change

        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
        assertEquals(BuildStatus.BUILDING, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));

        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));

        // take the closest built parent severity
        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT));
        assertEquals(BuildStatus.BUILT_WITH_WARNING, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getLocalBuildStatus());
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));
    }

    @Test
    void testUpdateApplicationStatus() throws Exception {
        Pair<UUID, NetworkModificationNode> result = createTreeForBuildStatus();
        UUID leafNodeId = result.getSecond().getId();
        UUID studyUuid = result.getFirst();
        UUID rootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);

        // take the closest built parent severity
        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(NetworkModificationResult.ApplicationStatus.ALL_OK, NetworkModificationResult.ApplicationStatus.WITH_WARNINGS));
        assertEquals(BuildStatus.BUILT_WITH_WARNING, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getLocalBuildStatus());
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));

        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(NetworkModificationResult.ApplicationStatus.WITH_WARNINGS, NetworkModificationResult.ApplicationStatus.WITH_WARNINGS));
        assertEquals(BuildStatus.BUILT_WITH_WARNING, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT_WITH_WARNING, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getLocalBuildStatus());
        // local build status has been updated
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));

        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(NetworkModificationResult.ApplicationStatus.WITH_ERRORS, NetworkModificationResult.ApplicationStatus.WITH_ERRORS));
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getLocalBuildStatus());
        checkUpdateNodesMessageReceived(studyUuid, List.of(leafNodeId));

        // keep the previous status (BUILT_WITH_ERROR) because it has higher severity
        networkModificationTreeService.updateNodeBuildStatus(leafNodeId, rootNetworkUuid, NodeBuildStatus.from(NetworkModificationResult.ApplicationStatus.ALL_OK, NetworkModificationResult.ApplicationStatus.WITH_ERRORS));
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(leafNodeId, rootNetworkUuid).getLocalBuildStatus());
    }

    /**
     * Create a network modification tree to test the build status.
     * @return a pair with the ID of the study and the ID of the leaf node of the tree
     */
    private Pair<UUID, NetworkModificationNode> createTreeForBuildStatus() throws Exception {
        String userId = "userId";
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModificationNode("built_with_error", "not built node", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT_WITH_ERROR);
        final NetworkModificationNode node2 = buildNetworkModificationNode("built_with_warning", "not built node", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT_WITH_WARNING);
        final NetworkModificationNode node3 = buildNetworkModificationNode("not_built", "not built node", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node4 = buildNetworkModificationNode("building", "not built node", UUID.randomUUID(), VARIANT_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILDING);
        createNode(root.getStudyId(), root, node1, userId);
        createNode(root.getStudyId(), node1, node2, userId);
        createNode(root.getStudyId(), node2, node3, userId);
        createNode(root.getStudyId(), node3, node4, userId);
        return Pair.of(root.getStudyId(), node4);
    }

    private void checkElementUpdatedMessageSent(UUID elementUuid, String userId) {
        Message<byte[]> message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(elementUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        assertEquals(userId, message.getHeaders().get(NotificationService.HEADER_MODIFIED_BY));
    }

    private void checkColumnsChangedMessageSent(UUID studyUuid, UUID parentNodeUuid, List<UUID> orderedUuids) throws Exception {
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.NODES_COLUMN_POSITIONS_CHANGED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(parentNodeUuid, message.getHeaders().get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(objectMapper.writeValueAsString(orderedUuids), new String(message.getPayload()));
    }

    private void checkUpdateNodesMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }
}
