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
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.networkmodificationtree.repositories.NodeRepository;
import org.gridsuite.study.server.networkmodificationtree.repositories.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.NetworkModificationTreeService.*;
import static org.gridsuite.study.server.StudyService.HEADER_UPDATE_TYPE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class NetworkModificationTreeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkModificationTreeTest.class);

    private static final long TIMEOUT = 1000;
    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private OutputDestination output;

    ObjectMapper objectMapper = WebFluxConfig.createObjectMapper();

    private LoadFlowResult loadFlowResult;
    private LoadFlowResult loadFlowResult2;

    @Autowired
    private StudyService studyService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private ReportService reportService;

    private MockWebServer server;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private VariantManager variantManager;

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";

    @MockBean
    private Network network;

    @Before
    public void setUp() throws IOException {
        Configuration.defaultConfiguration();
        MockitoAnnotations.initMocks(this);
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);

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
                                                List.of(new LoadFlowResultImpl.ComponentResultImpl(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 10, "bus_1", 5.),
                                                        new LoadFlowResultImpl.ComponentResultImpl(2, 2, LoadFlowResult.ComponentResult.Status.FAILED, 20, "bus_2", 10.)));
        loadFlowResult2 = new LoadFlowResultImpl(false, Map.of("key_3", "metric_3", "key_4", "metric_4"), "logs2",
            List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, 30, "bus_2", 8.),
                new LoadFlowResultImpl.ComponentResultImpl(0, 1, LoadFlowResult.ComponentResult.Status.SOLVER_FAILED, 15, "bus_3", 13.)));

        server = new MockWebServer();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        studyService.setCaseServerBaseUri(baseUrl);
        studyService.setNetworkConversionServerBaseUri(baseUrl);
        studyService.setSingleLineDiagramServerBaseUri(baseUrl);
        studyService.setGeoDataServerBaseUri(baseUrl);
        studyService.setNetworkMapServerBaseUri(baseUrl);
        studyService.setLoadFlowServerBaseUri(baseUrl);
        studyService.setSecurityAnalysisServerBaseUri(baseUrl);
        studyService.setActionsServerBaseUri(baseUrl);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());

                if (path.matches("/v1/results/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/.*") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    LOGGER.error("Path not supported: " + request.getPath());
                    return new MockResponse().setResponseCode(404);
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
        assertNull(output.receive(TIMEOUT));
    }

    StudyEntity createDummyStudy(UUID networkUuid) {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat("").caseUuid(UUID.randomUUID())
            .date(LocalDateTime.now())
            .networkId("netId")
            .networkUuid(networkUuid)
            .userId("userId")
            .loadFlowParameters(new LoadFlowParametersEntity())
            .build();
    }

    @Test
    public void testStudyWithNoNodes() {
        StudyEntity studyEntity = createDummyStudy(NETWORK_UUID);
        var study = studyRepository.save(studyEntity);

        UUID studyUuid = study.getId();
        assertThrows("ELEMENT_NOT_FOUND", StudyException.class, () -> networkModificationTreeService.getStudyRootNodeUuid(studyUuid));
    }

    @SneakyThrows
    @Test
    public void testGetRoot() {
        StudyEntity study = insertDummyStudy();
        RootNode root = getRootNode(study.getId());

        assertEquals("Root", root.getName());
        assertEquals(study.getId(), root.getStudyId());
        assertEquals(0, root.getChildren().size());

        webTestClient.get().uri("/v1/studies/{studyUuid}/tree", UUID.randomUUID())
            .exchange()
            .expectStatus().isNotFound();

        webTestClient.get().uri("/v1/studies/{studyUuid}/tree", study.getId())
            .exchange()
            .expectStatus().isOk();

        webTestClient.delete().uri("/v1/studies/{studyUuid}/tree/nodes/{id}?deleteChildren={delete}", study.getId(), root.getId(), false)
            .exchange()
            .expectStatus().is4xxClientError();
    }

    private AbstractNode getNode(UUID studyUuid, UUID idNode) throws IOException {
        return objectMapper.readValue(webTestClient.get().uri("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, idNode)
            .exchange()
            .expectStatus().isOk()
            .expectBody().returnResult().getResponseBody(), new TypeReference<>() { }
        );
    }

    private RootNode getRootNode(UUID study) throws IOException {
        return objectMapper.readValue(webTestClient.get().uri("/v1/studies/{uuid}/tree", study)
            .exchange()
            .expectStatus().isOk()
            .expectBody().returnResult().getResponseBody(), new TypeReference<>() {
            });
    }

    @Test
    public void testNodeCreation() throws Exception {
        RootNode root = createRoot();
        // Check build status initialized to NOT_BUILT if null
        final NetworkModificationNode node1 = buildNetworkModification("not_built", "not built node", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), null);
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
        final NetworkModificationNode node2 = buildNetworkModification("built", "built node", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.CONVERGED, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
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
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node2 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
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
        node1.setNetworkModification(UUID.randomUUID());
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
        createNode(root.getStudyId(), child, node1);

        deleteNode(root.getStudyId(), child, true, Set.of(node1, child));

        /* expected
            root
            /   \
          node  node
         */
        root = getRootNode(root.getStudyId());
        assertEquals(2, root.getChildren().size());
        assertEquals(3, nodeRepository.findAll().size());

        NetworkModificationNode node3 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, node3);

        networkModificationTreeService.doDeleteTree(root.getStudyId());
        assertEquals(0, nodeRepository.findAll().size());

        webTestClient.post().uri("/v1/studies/{studyUuid}/tree/nodes/{id}", root.getStudyId(), UUID.randomUUID()).bodyValue(node1)
            .exchange()
            .expectStatus().isNotFound();

    }

    private void deleteNode(UUID studyUuid, AbstractNode child, boolean deleteChildren, Set<AbstractNode> expectedDeletion) {
        webTestClient.delete().uri("/v1/studies/{studyUuid}/tree/nodes/{id}?deleteChildren={delete}", studyUuid, child.getId(), deleteChildren)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        if (expectedDeletion != null) {
            Collection<UUID> deletedId = (Collection<UUID>) mess.getHeaders().get(NetworkModificationTreeService.HEADER_NODES);
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
        final NetworkModificationNode networkModification = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        /* trying to insert before root */
        webTestClient.post().uri("/v1/studies/{studyUuid}/tree/nodes/{id}?mode=BEFORE", root.getStudyId(), root.getId()).bodyValue(networkModification)
            .exchange()
            .expectStatus().is4xxClientError();

        createNode(root.getStudyId(), root, networkModification);
        createNode(root.getStudyId(), root, networkModification);
        root = getRootNode(root.getStudyId());
        /* root
            / \
           n1  n2
         */
        AbstractNode unchangedNode = root.getChildren().get(0);
        AbstractNode willBeMoved = root.getChildren().get(1);
        insertNode(root.getStudyId(), willBeMoved, networkModification, InsertMode.BEFORE, root);
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

        webTestClient.post().uri("/v1/studies/{studyUuid}/tree/nodes/{id}", root.getStudyId(), UUID.randomUUID()).bodyValue(networkModification)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    public void testInsertAfter() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        final NetworkModificationNode node2 = buildNetworkModification("loadflow", "dance", null, VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, node2);
        createNode(root.getStudyId(), root, node2);
        root = getRootNode(root.getStudyId());
        var originalChildren = root.getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        insertNode(root.getStudyId(), root, node1, InsertMode.AFTER, root);
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        var grandChildren = getRootNode(root.getStudyId()).getChildren().get(0).getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        assertEquals(originalChildren, grandChildren);

        networkModificationTreeService.getVariantId(node1.getId()).subscribe(variantId -> assertEquals(VARIANT_ID, variantId));

        assertEquals(0, networkModificationTreeService.getAllModificationGroupUuids(root.getStudyId()).size());
        AtomicReference<UUID> modificationGroupUuid = new AtomicReference<>();
        networkModificationTreeService.getModificationGroupUuid(node1.getId()).subscribe(modificationGroupUuid::set);
        assertNotNull(modificationGroupUuid.get());
        assertEquals(1, networkModificationTreeService.getAllModificationGroupUuids(root.getStudyId()).size());

        UUID nodeUuid = node2.getId();
        assertNotNull(networkModificationTreeService.doGetModificationGroupUuid(nodeUuid, true));
        assertFalse(networkModificationTreeService.doGetVariantId(nodeUuid, true).isEmpty());
    }

    @Test
    public void testNodeUpdate() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        createNode(root.getStudyId(), root, node1);
        node1.setName("grunt");
        node1.setNetworkModification(UUID.randomUUID());
        root = getRootNode(root.getStudyId());
        node1.setId(root.getChildren().get(0).getId());
        webTestClient.put().uri("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId()).bodyValue(node1)
            .exchange()
            .expectStatus().isOk();
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        assertNodeEquals(node1, root.getChildren().get(0));

        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        var header = mess.getHeaders();
        assertEquals(root.getStudyId(), header.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(NetworkModificationTreeService.NODE_UPDATED, header.get(HEADER_UPDATE_TYPE));
        Collection<UUID> updated = (Collection<UUID>) header.get(NetworkModificationTreeService.HEADER_NODES);
        assertNotNull(updated);
        assertEquals(1, updated.size());
        updated.forEach(id -> assertEquals(node1.getId(), id));

        var justeANameUpdate = NetworkModificationNode.builder()
            .name("My taylor is rich!").id(node1.getId()).build();

        webTestClient.put().uri("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId()).bodyValue(justeANameUpdate)
            .exchange()
            .expectStatus().isOk();
        output.receive(TIMEOUT).getHeaders();

        var newNode = getNode(root.getStudyId(), node1.getId());
        node1.setName(justeANameUpdate.getName());
        assertNodeEquals(node1, newNode);

        node1.setId(UUID.randomUUID());
        webTestClient.put().uri("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId()).bodyValue(node1)
            .exchange()
            .expectStatus().isNotFound();

    }

    @SneakyThrows
    @Test
    public void testLightNode() {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        createNode(root.getStudyId(), root, hypo);
        createNode(root.getStudyId(), root, hypo);
        createNode(root.getStudyId(), root, hypo);
        AbstractNode node = getNode(root.getStudyId(), root.getId());
        assertEquals(3, node.getChildrenIds().size());
    }

    @Test
    public void testGetParentNode() {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        final NetworkModificationNode node2 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        final NetworkModificationNode node3 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node4 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node5 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node6 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);

        createNode(root.getStudyId(), root, node1);
        createNode(root.getStudyId(), node1, node3);
        createNode(root.getStudyId(), node3, node2);
        createNode(root.getStudyId(), node2, node4);
        createNode(root.getStudyId(), node4, node5);
        createNode(root.getStudyId(), node5, node6);

        networkModificationTreeService.getParentNode(node6.getId(), NodeType.NETWORK_MODIFICATION).subscribe(node -> assertEquals(node5.getId(), node));
        networkModificationTreeService.getParentNode(node2.getId(), NodeType.NETWORK_MODIFICATION).subscribe(node -> assertEquals(node3.getId(), node));
        networkModificationTreeService.getParentNode(node5.getId(), NodeType.ROOT).subscribe(node -> assertEquals(root.getId(), node));
    }

    @Test
    public void testGetLastParentNodeBuilt() {
        RootNode root = createRoot();
        final NetworkModificationNode node1 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        final NetworkModificationNode node2 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        final NetworkModificationNode node3 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID, LoadFlowStatus.RUNNING, loadFlowResult2, UUID.randomUUID(), BuildStatus.BUILT_INVALID);
        final NetworkModificationNode node4 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node5 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node6 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node7 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final NetworkModificationNode node8 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final NetworkModificationNode node9 = buildNetworkModification("loadflow", "dance", UUID.randomUUID(), VARIANT_ID, LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);

        createNode(root.getStudyId(), root, node1);
        createNode(root.getStudyId(), node1, node4);
        createNode(root.getStudyId(), node4, node2);
        createNode(root.getStudyId(), node2, node5);
        createNode(root.getStudyId(), node5, node6);
        createNode(root.getStudyId(), node6, node7);
        createNode(root.getStudyId(), node7, node8);
        createNode(root.getStudyId(), node8, node3);
        createNode(root.getStudyId(), node3, node8);
        createNode(root.getStudyId(), node8, node9);

        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuilt(node7.getId()));
        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuilt(node9.getId()));
        assertEquals(node7.getId(), networkModificationTreeService.doGetLastParentNodeBuilt(node3.getId()));
    }

    private void createNode(UUID studyUuid, AbstractNode parentNode, AbstractNode newNode) {
        newNode.setId(null);
        webTestClient.post().uri("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNode.getId()).bodyValue(newNode)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(HEADER_INSERT_MODE));
    }

    private void insertNode(UUID studyUuid, AbstractNode parentNode, AbstractNode newNode, InsertMode mode, AbstractNode newParentNode) {
        newNode.setId(null);
        webTestClient.post().uri("/v1/studies/{studyUuid}/tree/nodes/{id}?mode={mode}", studyUuid, parentNode.getId(), mode).bodyValue(newNode)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        assertEquals(NODE_CREATED, mess.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(newParentNode.getId(), mess.getHeaders().get(HEADER_PARENT_NODE));
        assertEquals(mode.name(), mess.getHeaders().get(HEADER_INSERT_MODE));
        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(HEADER_NEW_NODE))));
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
        networkModificationTreeService.getStudyTree(study.getId()).subscribe(result::set);
        return result.get();
    }

    private NetworkModificationNode buildNetworkModification(String name, String description, UUID idHypo, String variantId,
                                                             LoadFlowStatus loadFlowStatus, LoadFlowResult loadFlowResult,
                                                             UUID securityAnalysisResultUuid, BuildStatus buildStatus) {
        return NetworkModificationNode.builder()
            .name(name)
            .description(description)
            .networkModification(idHypo)
            .variantId(variantId)
            .loadFlowStatus(loadFlowStatus)
            .loadFlowResult(loadFlowResult)
            .securityAnalysisResultUuid(securityAnalysisResultUuid)
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
        assertEquals(expectedModificationNode.getNetworkModification(), currentModificationNode.getNetworkModification());
        assertEquals(expectedModificationNode.getVariantId(), currentModificationNode.getVariantId());
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
        assertEquals(expectedModificationNode.getBuildStatus(), currentModificationNode.getBuildStatus());
    }
}

