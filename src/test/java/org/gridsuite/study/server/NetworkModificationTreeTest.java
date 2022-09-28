/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

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
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.client.NetworkStoreService;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.NodeModificationInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.networkmodificationtree.repositories.NodeRepository;
import org.gridsuite.study.server.networkmodificationtree.repositories.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.ActionsService;
import org.gridsuite.study.server.service.CaseService;
import org.gridsuite.study.server.service.GeoDataService;
import org.gridsuite.study.server.service.LoadflowService;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.NetworkMapService;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NotificationService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.service.SecurityAnalysisService;
import org.gridsuite.study.server.service.SensitivityAnalysisService;
import org.gridsuite.study.server.service.SingleLineDiagramService;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.service.NetworkModificationTreeService.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class NetworkModificationTreeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkModificationTreeTest.class);

    @Autowired
    private MockMvc mockMvc;

    private static final long TIMEOUT = 1000;
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

    private LoadFlowResult loadFlowResult;
    private LoadFlowResult loadFlowResult2;

    @Autowired
    private CaseService caseService;

    @Autowired
    private NetworkConversionService networkConversionService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private NetworkMapService networkMapService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;

    @Autowired
    private LoadflowService loadflowService;

    @Autowired
    private GeoDataService geoDataService;

    @Autowired
    private ActionsService actionsService;

    @MockBean
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private StudyInfosService studyInfosService;

    private MockWebServer server;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private VariantManager variantManager;

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final UUID MODIFICATION_GROUP_UUID = UUID.randomUUID();

    @MockBean
    private Network network;

    private String studyUpdateDestination = "study.update";

    @Before
    public void setUp() throws IOException {
        Configuration.defaultConfiguration();
        MockitoAnnotations.initMocks(this);
        objectMapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);

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

        loadFlowResult = new LoadFlowResultImpl(true, Map.of("key_1", "metric_1", "key_2", "metric_2"), "logs",
                                                List.of(new LoadFlowResultImpl.ComponentResultImpl(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 10, "bus_1", 5., 4.3),
                                                        new LoadFlowResultImpl.ComponentResultImpl(2, 2, LoadFlowResult.ComponentResult.Status.FAILED, 20, "bus_2", 10., 2.5)));
        loadFlowResult2 = new LoadFlowResultImpl(false, Map.of("key_3", "metric_3", "key_4", "metric_4"), "logs2",
            List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, 30, "bus_2", 8., 2.6),
                new LoadFlowResultImpl.ComponentResultImpl(0, 1, LoadFlowResult.ComponentResult.Status.SOLVER_FAILED, 15, "bus_3", 13., 1.67)));

        server = new MockWebServer();

        // Start the server.
        server.start();

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

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());

                if (path.matches("/v1/results/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/" + MODIFICATION_GROUP_UUID + "/.*") && request.getMethod().equals("GET")) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody(objectMapper.writeValueAsString(List.of()));
                } else if (path.matches("/v1/groups/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/reports/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    LOGGER.error("Path not supported: " + request.getPath());
                    return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @After
    public void cleanDB() {
        networkModificationNodeInfoRepository.deleteAll();
        rootNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        studyRepository.deleteAll();
        assertNull(output.receive(TIMEOUT, studyUpdateDestination));
    }

    StudyEntity createDummyStudy(UUID networkUuid) {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat("").caseUuid(UUID.randomUUID())
            .date(LocalDateTime.now())
            .networkId("netId")
            .networkUuid(networkUuid)
            .userId("userId")
            .loadFlowParameters(new LoadFlowParametersEntity())
            .shortCircuitParameters(new ShortCircuitParametersEntity())
            .build();
    }

    @Test
    public void testStudyWithNoNodes() {
        StudyEntity studyEntity = createDummyStudy(NETWORK_UUID);
        var study = studyRepository.save(studyEntity);

        UUID studyUuid = study.getId();
        assertThrows("ELEMENT_NOT_FOUND", StudyException.class, () -> networkModificationTreeService.getStudyRootNodeUuid(studyUuid));
    }

    @Test
    public void testGetRoot() throws Exception {
        StudyEntity study = insertDummyStudy();
        RootNode root = getRootNode(study.getId());

        assertEquals(ROOT_NODE_NAME, root.getName());
        assertEquals(study.getId(), root.getStudyId());
        assertEquals(0, root.getChildren().size());

        mockMvc.perform(get("/v1/studies/{studyUuid}/tree", UUID.randomUUID()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/studies/{studyUuid}/tree", study.getId()))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/studies/{studyUuid}/tree/nodes/{id}?deleteChildren={delete}", study.getId(), root.getId(), false))
            .andExpect(status().is4xxClientError());
    }

    private AbstractNode getNode(UUID studyUuid, UUID idNode) throws Exception {

        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, idNode))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { }
        );
    }

    private RootNode getRootNode(UUID study) throws Exception {

        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(), new TypeReference<>() { });
    }

    @Test
    public void testNodeCreation() throws Exception {
        RootNode root = createRoot();
        // Check build status initialized to NOT_BUILT if null
        final NetworkModificationNode node1 = buildNetworkModification("not_built", "not built node", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), null);
        createNode(root.getStudyId(), root, node1);
        root = getRootNode(root.getStudyId());
        List<AbstractNode> children = root.getChildren();
        assertEquals(1, children.size());
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) children.get(0);
        assertEquals(BuildStatus.NOT_BUILT, networkModificationNode.getBuildStatus());
        assertEquals(LoadFlowStatus.NOT_DONE, networkModificationNode.getLoadFlowStatus());
        assertEquals("not_built", networkModificationNode.getName());
        assertEquals("not built node", networkModificationNode.getDescription());
        deleteNode(root.getStudyId(), children.get(0), false, Set.of(children.get(0)));
        // Check built status correctly initialized
        final NetworkModificationNode node2 = buildNetworkModification("built", "built node", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.CONVERGED, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node2);
        root = getRootNode(root.getStudyId());
        children = root.getChildren();
        assertEquals(1, children.size());
        networkModificationNode = (NetworkModificationNode) children.get(0);
        assertEquals(BuildStatus.BUILT, networkModificationNode.getBuildStatus());
        assertEquals(LoadFlowStatus.CONVERGED, networkModificationNode.getLoadFlowStatus());
        assertEquals("built", networkModificationNode.getName());
        assertEquals("built node", networkModificationNode.getDescription());
        deleteNode(root.getStudyId(), children.get(0), false, Set.of(children.get(0)));
    }

    @Test
    public void testNodeManipulation() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo 1", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node4 = buildNetworkModification("hypo 2", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1);
        createNode(root.getStudyId(), root, node2);
        root = getRootNode(root.getStudyId());

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
        createNode(root.getStudyId(), children.get(1), node2);
        createNode(root.getStudyId(), children.get(1), node1);

        /*  expected
                root
               /    \
           node1      node2
                    /    \
        node(condriak)   node(niark)
         */

        root = getRootNode(root.getStudyId());
        AbstractNode child;
        if (root.getChildren().get(0).getName().equals(children.get(1).getName())) {
            child = root.getChildren().get(0);
        } else {
            child = root.getChildren().get(1);
        }
        children = child.getChildren();
        assertChildrenEquals(Set.of(node1, node2), children);

        deleteNode(root.getStudyId(), child, false, Set.of(child));

        /*  expected
              root
            /   |   \
          node node node
        */

        root = getRootNode(root.getStudyId());
        assertEquals(3, root.getChildren().size());
        child = root.getChildren().get(0);
        createNode(root.getStudyId(), child, node4);

        deleteNode(root.getStudyId(), child, true, Set.of(node4, child));

        /* expected
            root
            /   \
          node  node
         */
        root = getRootNode(root.getStudyId());
        assertEquals(2, root.getChildren().size());
        assertEquals(3, nodeRepository.findAll().size());

        NetworkModificationNode node3 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node3);

        networkModificationTreeService.doDeleteTree(root.getStudyId());
        assertEquals(0, nodeRepository.findAll().size());

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", root.getStudyId(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(node1)))
            .andExpect(status().isNotFound());

    }

    private void deleteNode(UUID studyUuid, AbstractNode child, boolean deleteChildren, Set<AbstractNode> expectedDeletion) throws Exception {
        mockMvc.perform(delete("/v1/studies/{studyUuid}/tree/nodes/{id}?deleteChildren={delete}", studyUuid, child.getId(), deleteChildren))
            .andExpect(status().isOk());

        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        if (expectedDeletion != null) {
            Collection<UUID> deletedId = (Collection<UUID>) mess.getHeaders().get(NotificationService.HEADER_NODES);
            assertNotNull(deletedId);
            assertEquals(expectedDeletion.size(), deletedId.size());
            deletedId.forEach(id ->
                assertTrue(expectedDeletion.stream().anyMatch(node -> node.getId().equals(id))));
        }
    }

    private void assertChildrenEquals(Set<AbstractNode> original, List<AbstractNode> children) {
        assertEquals(original.size(), children.size());
        children.forEach(node -> {
            Optional<AbstractNode> found = original.stream().filter(s -> node.getId().equals(s.getId())).findFirst();
            assertTrue(found.isPresent());
            assertNodeEquals(found.get(), node);
            }
        );
    }

    @Test
    public void testNodeInsertion() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode networkModification1 = buildNetworkModification("hypo 1", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode networkModification2 = buildNetworkModification("hypo 2", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode networkModification3 = buildNetworkModification("hypo 3", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        /* trying to insert before root */
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}?mode=BEFORE", root.getStudyId(), root.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(networkModification1)))
            .andExpect(status().is4xxClientError());

        createNode(root.getStudyId(), root, networkModification1);
        createNode(root.getStudyId(), root, networkModification2);
        root = getRootNode(root.getStudyId());
        /* root
            / \
           n1  n2
         */
        AbstractNode unchangedNode = root.getChildren().get(0);
        AbstractNode willBeMoved = root.getChildren().get(1);
        insertNode(root.getStudyId(), willBeMoved, networkModification3, InsertMode.BEFORE, root);
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
                .content(objectWriter.writeValueAsString(networkModification1)))
            .andExpect(status().isNotFound());

    }

    @Test
    public void testInsertAfter() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModification("loadflow 1", "dance", null, VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node3 = buildNetworkModification("loadflow 2", "dance", null, VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node2);
        createNode(root.getStudyId(), root, node3);
        root = getRootNode(root.getStudyId());
        var originalChildren = root.getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        insertNode(root.getStudyId(), root, node1, InsertMode.AFTER, root);
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        var grandChildren = getRootNode(root.getStudyId()).getChildren().get(0).getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        assertEquals(originalChildren, grandChildren);

        assertEquals(VARIANT_ID, networkModificationTreeService.getVariantId(node1.getId()));

        UUID nodeUuid = node2.getId();
        assertNotNull(networkModificationTreeService.getModificationGroupUuid(nodeUuid));
        assertNotNull(networkModificationTreeService.getReportUuid(nodeUuid));
        assertFalse(networkModificationTreeService.getVariantId(nodeUuid).isEmpty());

        assertEquals(4, networkModificationTreeService.getAllNodesModificationInfos(root.getStudyId()).stream().map(NodeModificationInfos::getReportUuid).collect(Collectors.toList()).size());
    }

    @Test
    public void testNodeUpdate() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node1);
        node1.setName("grunt");
        node1.setModificationGroupUuid(UUID.randomUUID());
        root = getRootNode(root.getStudyId());
        node1.setId(root.getChildren().get(0).getId());
        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(node1)))
            .andExpect(status().isOk());
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        assertNodeEquals(node1, root.getChildren().get(0));

        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        var header = mess.getHeaders();
        assertEquals(root.getStudyId(), header.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, header.get(NotificationService.HEADER_UPDATE_TYPE));
        Collection<UUID> updated = (Collection<UUID>) header.get(NotificationService.HEADER_NODES);
        assertNotNull(updated);
        assertEquals(1, updated.size());
        updated.forEach(id -> assertEquals(node1.getId(), id));

        var justeANameUpdate = NetworkModificationNode.builder()
            .name("My taylor is rich!").id(node1.getId()).build();

        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(justeANameUpdate)))
            .andExpect(status().isOk());
        output.receive(TIMEOUT, studyUpdateDestination).getHeaders();

        var newNode = getNode(root.getStudyId(), node1.getId());
        node1.setName(justeANameUpdate.getName());
        assertNodeEquals(node1, newNode);

        node1.setId(UUID.randomUUID());
        mockMvc.perform(put("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectWriter.writeValueAsString(node1)))
            .andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    public void testLightNode() {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode hypo2 = buildNetworkModification("hypo 2", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode hypo3 = buildNetworkModification("hypo 3", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, hypo);
        createNode(root.getStudyId(), root, hypo2);
        createNode(root.getStudyId(), root, hypo3);
        AbstractNode node = getNode(root.getStudyId(), root.getId());
        assertEquals(3, node.getChildrenIds().size());
    }

    @Test
    public void testGetParentNode() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModification("hypo 1", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node3 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node4 = buildNetworkModification("loadflow 1", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node5 = buildNetworkModification("loadflow 2", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node6 = buildNetworkModification("loadflow 3", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);

        createNode(root.getStudyId(), root, node1);
        createNode(root.getStudyId(), node1, node3);
        createNode(root.getStudyId(), node3, node2);
        createNode(root.getStudyId(), node2, node4);
        createNode(root.getStudyId(), node4, node5);
        createNode(root.getStudyId(), node5, node6);

        assertEquals(node5.getId(), networkModificationTreeService.getParentNode(node6.getId(), NodeType.NETWORK_MODIFICATION));
        assertEquals(node3.getId(), networkModificationTreeService.getParentNode(node2.getId(), NodeType.NETWORK_MODIFICATION));
        assertEquals(root.getId(), networkModificationTreeService.getParentNode(node5.getId(), NodeType.ROOT));
    }

    @Test
    public void testGetLastParentNodeBuilt() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo 1", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModification("hypo 2", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node3 = buildNetworkModification("hypo 3", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node4 = buildNetworkModification("loadflow 1", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node5 = buildNetworkModification("loadflow 2", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node6 = buildNetworkModification("loadflow 3", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node7 = buildNetworkModification("loadflow 4", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node8 = buildNetworkModification("loadflow 5", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node9 = buildNetworkModification("loadflow 6", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node10 = buildNetworkModification("loadflow 7", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.NOT_BUILT);

        createNode(root.getStudyId(), root, node1);
        createNode(root.getStudyId(), node1, node4);
        createNode(root.getStudyId(), node4, node2);
        createNode(root.getStudyId(), node2, node5);
        createNode(root.getStudyId(), node5, node6);
        createNode(root.getStudyId(), node6, node7);
        createNode(root.getStudyId(), node7, node8);
        createNode(root.getStudyId(), node8, node3);
        createNode(root.getStudyId(), node3, node10);
        createNode(root.getStudyId(), node10, node9);

        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuilt(node7.getId()));
        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuilt(node9.getId()));
        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuilt(node3.getId()));
    }

    private void createNode(UUID studyUuid, AbstractNode parentNode, NetworkModificationNode newNode) throws Exception {
        newNode.setId(null);

        // Only for tests. Need to remove when all tests are rewritten without the variantID to identify a test in the MockWebServer
        String variantId = newNode.getVariantId();
        UUID modificationGroupUuid = newNode.getModificationGroupUuid();
        String newNodeBodyJson = objectWriter.writeValueAsString(newNode);
        JSONObject jsonObject = new JSONObject(newNodeBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        newNodeBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNode.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(newNodeBodyJson))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
    }

    private void insertNode(UUID studyUuid, AbstractNode parentNode, NetworkModificationNode newNode, InsertMode mode, AbstractNode newParentNode) throws Exception {
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
                .content(newNodeBodyJson))
            .andExpect(status().isOk());

        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.NODE_CREATED, mess.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(newParentNode.getId(), mess.getHeaders().get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(mode.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
    }

    @NotNull
    private StudyEntity insertDummyStudy() {
        StudyEntity studyEntity = createDummyStudy(NETWORK_UUID);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private RootNode createRoot() {
        var study = insertDummyStudy();
        AtomicReference<RootNode> result = new AtomicReference<>();
        result.set(networkModificationTreeService.getStudyTree(study.getId()));
        return result.get();
    }

    private NetworkModificationNode buildNetworkModification(String name, String description, UUID idHypo, String variantId,

        LoadFlowStatus loadFlowStatus, LoadFlowResult loadFlowResult,
        UUID securityAnalysisResultUuid, UUID sensitivityAnalysisResultUuid, BuildStatus buildStatus) {
        return NetworkModificationNode.builder()
            .name(name)
            .description(description)
            .modificationGroupUuid(idHypo)
            .variantId(variantId)
            .loadFlowStatus(loadFlowStatus)
            .loadFlowResult(loadFlowResult)
            .securityAnalysisResultUuid(securityAnalysisResultUuid)
            .sensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid)
            .buildStatus(buildStatus)
            .children(Collections.emptyList()).build();
    }

    private void assertNodeEquals(AbstractNode expected, AbstractNode current) {
        assertEquals(expected.getName(), current.getName());
        assertEquals(expected.getDescription(), current.getDescription());
        assertEquals(expected.getChildren().size(), current.getChildren().size());
        assertEquals(expected.getType(), current.getType());
        if (expected.getType() == NodeType.NETWORK_MODIFICATION) {
            assertModificationNodeEquals(expected, current);
        }
    }

    private void assertModificationNodeEquals(AbstractNode expected, AbstractNode current) {
        NetworkModificationNode currentModificationNode = (NetworkModificationNode) current;
        NetworkModificationNode expectedModificationNode = (NetworkModificationNode) expected;
        assertEquals(expectedModificationNode.getLoadFlowStatus(), currentModificationNode.getLoadFlowStatus());
        LoadFlowResult expectedLoadFlowResult = expectedModificationNode.getLoadFlowResult();
        LoadFlowResult currentLoadFlowResult = currentModificationNode.getLoadFlowResult();
        assertFalse((expectedLoadFlowResult != null && currentLoadFlowResult == null) || (expectedLoadFlowResult == null && currentLoadFlowResult != null));
        if (expectedLoadFlowResult != null && currentLoadFlowResult != null) {
            assertEquals(expectedLoadFlowResult.isOk(), currentLoadFlowResult.isOk());
            assertEquals(expectedLoadFlowResult.getMetrics(), currentLoadFlowResult.getMetrics());
            List<LoadFlowResult.ComponentResult> expectedComponentResults = expectedLoadFlowResult.getComponentResults();
            List<LoadFlowResult.ComponentResult> currentComponentResults = currentLoadFlowResult.getComponentResults();
            assertFalse((expectedComponentResults != null && currentComponentResults == null) || (expectedComponentResults == null && currentComponentResults != null));
            if (expectedComponentResults != null && currentComponentResults != null) {
                for (int i = 0; i < expectedComponentResults.size(); ++i) {
                    LoadFlowResult.ComponentResult expectedComponentResult = expectedComponentResults.get(i);
                    LoadFlowResult.ComponentResult currentComponentResult = currentComponentResults.get(i);
                    assertEquals(expectedComponentResult.getConnectedComponentNum(), currentComponentResult.getConnectedComponentNum());
                    assertEquals(expectedComponentResult.getIterationCount(), currentComponentResult.getIterationCount());
                    assertEquals(expectedComponentResult.getSynchronousComponentNum(), currentComponentResult.getSynchronousComponentNum());
                    assertEquals(expectedComponentResult.getStatus(), currentComponentResult.getStatus());
                    assertEquals(expectedComponentResult.getSlackBusId(), currentComponentResult.getSlackBusId());
                    assertEquals(expectedComponentResult.getSlackBusActivePowerMismatch(), currentComponentResult.getSlackBusActivePowerMismatch(), 0.1);
                }
            }
        }
        assertEquals(expectedModificationNode.getSecurityAnalysisResultUuid(), currentModificationNode.getSecurityAnalysisResultUuid());
        assertEquals(expectedModificationNode.getSensitivityAnalysisResultUuid(), currentModificationNode.getSensitivityAnalysisResultUuid());
        assertEquals(expectedModificationNode.getBuildStatus(), currentModificationNode.getBuildStatus());
    }

    @Test
    public void testNodeName() throws Exception {
        RootNode root = createRoot();
        String nodeName = "node 1";
        final NetworkModificationNode node = buildNetworkModification(nodeName, "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        UUID studyUuid = root.getStudyId();
        createNode(studyUuid, root, node);
        createNodeFail(studyUuid, root, root);
        createNodeFail(studyUuid, root, node);

        node.setName("node 2");
        createNode(studyUuid, root, node);

        String nodeName1 = getNodeName(studyUuid);
        final NetworkModificationNode node1 = buildNetworkModification(nodeName1, "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(studyUuid, root, node1);
        String nodeName2 = getNodeName(studyUuid);

        assertNotEquals(nodeName, nodeName1);
        assertNotEquals(nodeName, nodeName2);
        assertNotEquals(nodeName1, nodeName2);
    }

    private void createNodeFail(UUID studyUuid, AbstractNode parentNode, AbstractNode newNode) throws Exception {
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectWriter.writeValueAsString(newNode)))
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
    public void testGetNetworkModificationsNode() throws Exception {
        RootNode root = createRoot();
        NetworkModificationNode node = buildNetworkModification("modification node 1", "", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, null, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node);

        String bodyError = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications", root.getStudyId(), node.getId()))
            .andExpect(status().isInternalServerError())
            .andReturn().getResponse().getContentAsString();

        assertEquals(new StudyException(StudyException.Type.GET_MODIFICATIONS_FAILED, HttpStatus.NOT_FOUND.toString()).getMessage(), bodyError);

        // No network modification for a root node
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications", root.getStudyId(), root.getId()))
            .andExpect(status().isNotFound());

        node = buildNetworkModification("modification node 2", "", MODIFICATION_GROUP_UUID, VARIANT_ID, LoadFlowStatus.NOT_DONE, null, UUID.randomUUID(), UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node);
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications", root.getStudyId(), node.getId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }
}
