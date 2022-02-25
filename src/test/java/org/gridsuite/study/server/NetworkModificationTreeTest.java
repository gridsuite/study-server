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
import org.gridsuite.study.server.networkmodificationtree.dto.ModelNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.networkmodificationtree.repositories.ModelNodeInfoRepository;
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
    private ModelNodeInfoRepository modelNodeInfoRepository;

    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private OutputDestination output;

    ObjectMapper objectMapper = WebFluxConfig.createObjectMapper();

    LoadFlowResult loadFlowResult;

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

    private static final String TEST_FILE = "testCase.xiidm";
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
        modelNodeInfoRepository.deleteAll();
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
    public void testModelNodeCreation() throws Exception {
        RootNode root = createRoot();
        // Check build status initialized to NOT_BUILT if null
        final ModelNode model = buildModel("not_built", "not built node", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), null);
        createNode(root.getStudyId(), root, model);
        root = getRootNode(root.getStudyId());
        List<AbstractNode> children = root.getChildren();
        assertEquals(1, children.size());
        ModelNode modelNode = (ModelNode) children.get(0);
        assertEquals(BuildStatus.NOT_BUILT, modelNode.getBuildStatus());
        assertEquals(LoadFlowStatus.NOT_DONE, modelNode.getLoadFlowStatus());
        assertEquals("not_built", modelNode.getName());
        assertEquals("not built node", modelNode.getDescription());
        deleteNode(root.getStudyId(), children.get(0), false, Set.of(children.get(0)));
        // Check built status correctly initialized
        final ModelNode builtModel = buildModel("built", "built node", "loadflow", LoadFlowStatus.CONVERGED, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, builtModel);
        root = getRootNode(root.getStudyId());
        children = root.getChildren();
        assertEquals(1, children.size());
        modelNode = (ModelNode) children.get(0);
        assertEquals(BuildStatus.BUILT, modelNode.getBuildStatus());
        assertEquals(LoadFlowStatus.CONVERGED, modelNode.getLoadFlowStatus());
        assertEquals("built", modelNode.getName());
        assertEquals("built node", modelNode.getDescription());
        deleteNode(root.getStudyId(), children.get(0), false, Set.of(children.get(0)));
    }

    @Test
    public void testNodeManipulation() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID);
        ModelNode model = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, model);
        createNode(root.getStudyId(), root, hypo);
        root = getRootNode(root.getStudyId());

        List<AbstractNode> children = root.getChildren();
        /*  expected :
                root
               /    \
           model     hypo
        */
        assertChildrenEquals(Set.of(hypo, model), root.getChildren());

        model.setName("niark");
        hypo.setName("condriak");
        hypo.setNetworkModification(UUID.randomUUID());
        createNode(root.getStudyId(), children.get(1), model);
        createNode(root.getStudyId(), children.get(1), hypo);

        /*  expected
                root
               /    \
           node      node
                    /    \
        hypo(condriak)   model(niark)
         */

        root = getRootNode(root.getStudyId());
        AbstractNode child;
        if (root.getChildren().get(0).getName().equals(children.get(1).getName())) {
            child = root.getChildren().get(0);
        } else {
            child = root.getChildren().get(1);
        }
        children = child.getChildren();
        assertChildrenEquals(Set.of(hypo, model), children);

        deleteNode(root.getStudyId(), child, false, Set.of(child));

        /*  expected
              root
            /   |   \
          node node node
        */

        root = getRootNode(root.getStudyId());
        assertEquals(3, root.getChildren().size());
        child = root.getChildren().get(0);
        createNode(root.getStudyId(), child, hypo);

        deleteNode(root.getStudyId(), child, true, Set.of(hypo, child));

        /* expected
            root
            /   \
          node  node
         */
        root = getRootNode(root.getStudyId());
        assertEquals(2, root.getChildren().size());
        assertEquals(3, nodeRepository.findAll().size());

        model = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);
        createNode(root.getStudyId(), root, model);

        networkModificationTreeService.doDeleteTree(root.getStudyId());
        assertEquals(0, nodeRepository.findAll().size());

        webTestClient.post().uri("/v1/studies/{studyUuid}/tree/nodes/{id}", root.getStudyId(), UUID.randomUUID()).bodyValue(hypo)
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
        final NetworkModificationNode networkModification = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID);
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
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", null, VARIANT_ID);
        final ModelNode model = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        createNode(root.getStudyId(), root, model);
        createNode(root.getStudyId(), root, model);
        root = getRootNode(root.getStudyId());
        var originalChildren = root.getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        insertNode(root.getStudyId(), root, hypo, InsertMode.AFTER, root);
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        var grandChildren = getRootNode(root.getStudyId()).getChildren().get(0).getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        assertEquals(originalChildren, grandChildren);

        assertEquals(VARIANT_ID, networkModificationTreeService.getVariantId(hypo.getId()).block());

        assertEquals(0, networkModificationTreeService.getAllModificationGroupUuids(root.getStudyId()).size());
        UUID modificationGroupUuid = networkModificationTreeService.getModificationGroupUuid(hypo.getId()).block();
        assertNotNull(modificationGroupUuid);
        assertEquals(1, networkModificationTreeService.getAllModificationGroupUuids(root.getStudyId()).size());

        UUID modelUuid = model.getId();
        assertNotNull(networkModificationTreeService.doGetModificationGroupUuid(modelUuid, true));
        assertFalse(networkModificationTreeService.doGetVariantId(modelUuid, true).isEmpty());
    }

    @Test
    public void testNodeUpdate() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID);
        createNode(root.getStudyId(), root, hypo);
        hypo.setName("grunt");
        hypo.setNetworkModification(UUID.randomUUID());
        root = getRootNode(root.getStudyId());
        hypo.setId(root.getChildren().get(0).getId());
        webTestClient.put().uri("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId()).bodyValue(hypo)
            .exchange()
            .expectStatus().isOk();
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        assertNodeEquals(hypo, root.getChildren().get(0));

        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        var header = mess.getHeaders();
        assertEquals(root.getStudyId(), header.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(NetworkModificationTreeService.NODE_UPDATED, header.get(HEADER_UPDATE_TYPE));
        Collection<UUID> updated = (Collection<UUID>) header.get(NetworkModificationTreeService.HEADER_NODES);
        assertNotNull(updated);
        assertEquals(1, updated.size());
        updated.forEach(id -> assertEquals(hypo.getId(), id));

        var justeANameUpdate = NetworkModificationNode.builder()
            .name("My taylor is rich!").id(hypo.getId()).build();

        webTestClient.put().uri("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId()).bodyValue(justeANameUpdate)
            .exchange()
            .expectStatus().isOk();
        output.receive(TIMEOUT).getHeaders();

        var newNode = getNode(root.getStudyId(), hypo.getId());
        hypo.setName(justeANameUpdate.getName());
        assertNodeEquals(hypo, newNode);

        hypo.setId(UUID.randomUUID());
        webTestClient.put().uri("/v1/studies/{studyUuid}/tree/nodes", root.getStudyId()).bodyValue(hypo)
            .exchange()
            .expectStatus().isNotFound();

    }

    @SneakyThrows
    @Test
    public void testLightNode() {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID(), VARIANT_ID);
        createNode(root.getStudyId(), root, hypo);
        createNode(root.getStudyId(), root, hypo);
        createNode(root.getStudyId(), root, hypo);
        AbstractNode node = getNode(root.getStudyId(), root.getId());
        assertEquals(3, node.getChildrenIds().size());
    }

    @Test
    public void testGetParentNode() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode hypo1 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID);
        final NetworkModificationNode hypo2 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID);
        final ModelNode model1 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final ModelNode model2 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final ModelNode model3 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final ModelNode model4 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);

        createNode(root.getStudyId(), root, hypo1);
        createNode(root.getStudyId(), hypo1, model1);
        createNode(root.getStudyId(), model1, hypo2);
        createNode(root.getStudyId(), hypo2, model2);
        createNode(root.getStudyId(), model2, model3);
        createNode(root.getStudyId(), model3, model4);

        assertEquals(model3.getId(), networkModificationTreeService.getParentNode(model4.getId(), NodeType.MODEL).block());
        assertEquals(hypo2.getId(), networkModificationTreeService.getParentNode(model4.getId(), NodeType.NETWORK_MODIFICATION).block());
        assertEquals(model1.getId(), networkModificationTreeService.getParentNode(hypo2.getId(), NodeType.MODEL).block());
        assertEquals(root.getId(), networkModificationTreeService.getParentNode(model3.getId(), NodeType.ROOT).block());
    }

    @Test
    public void testGetLastParentModelNodeBuilt() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode hypo1 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID);
        final NetworkModificationNode hypo2 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID);
        final NetworkModificationNode hypo3 = buildNetworkModification("hypo", "potamus", null, VARIANT_ID);
        final ModelNode model1 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final ModelNode model2 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final ModelNode model3 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final ModelNode model4 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.BUILT);
        final ModelNode model5 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);
        final ModelNode model6 = buildModel("loadflow", "dance", "loadflow", LoadFlowStatus.NOT_DONE, loadFlowResult, UUID.randomUUID(), BuildStatus.NOT_BUILT);

        createNode(root.getStudyId(), root, hypo1);
        createNode(root.getStudyId(), hypo1, model1);
        createNode(root.getStudyId(), model1, hypo2);
        createNode(root.getStudyId(), hypo2, model2);
        createNode(root.getStudyId(), model2, model3);
        createNode(root.getStudyId(), model3, model4);
        createNode(root.getStudyId(), model4, model5);
        createNode(root.getStudyId(), model5, hypo3);
        createNode(root.getStudyId(), hypo3, model5);
        createNode(root.getStudyId(), model5, model6);

        assertEquals(model4.getId(), networkModificationTreeService.doGetLastParentModelNodeBuilt(model4.getId()).get());
        assertEquals(model4.getId(), networkModificationTreeService.doGetLastParentModelNodeBuilt(model6.getId()).get());
        assertEquals(model4.getId(), networkModificationTreeService.doGetLastParentModelNodeBuilt(hypo3.getId()).get());
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
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private RootNode createRoot() {
        var study = insertDummyStudy();
        return networkModificationTreeService.getStudyTree(study.getId()).block();
    }

    private NetworkModificationNode buildNetworkModification(String name, String description, UUID idHypo, String variantId) {
        return NetworkModificationNode.builder()
            .name(name)
            .description(description)
            .networkModification(idHypo)
            .variantId(variantId)
            .children(Collections.emptyList()).build();
    }

    private ModelNode buildModel(String name, String description, String model,
                                 LoadFlowStatus loadFlowStatus, LoadFlowResult loadFlowResult,
                                 UUID securityAnalysisResultUuid, BuildStatus buildStatus) {
        return ModelNode.builder()
            .name(name)
            .description(description)
            .model(model)
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
        switch (expected.getType()) {
            case NETWORK_MODIFICATION: assertHypoNodeEquals(expected, current);
            break;
            case MODEL: assertModelNodeEquals(expected, current);
            break;
            default:
        }
    }

    private void assertModelNodeEquals(AbstractNode expected, AbstractNode current) {
        ModelNode currentModel = (ModelNode) current;
        ModelNode expectedModel = (ModelNode) expected;
        assertEquals(expectedModel.getModel(), currentModel.getModel());

        assertEquals(expectedModel.getLoadFlowStatus(), currentModel.getLoadFlowStatus());
        LoadFlowResult expectedLoadFlowResult = expectedModel.getLoadFlowResult();
        LoadFlowResult currentLoadFlowResult = currentModel.getLoadFlowResult();
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
        assertEquals(expectedModel.getSecurityAnalysisResultUuid(), currentModel.getSecurityAnalysisResultUuid());
        assertEquals(expectedModel.getBuildStatus(), currentModel.getBuildStatus());
    }

    private void assertHypoNodeEquals(AbstractNode expected, AbstractNode current) {
        NetworkModificationNode currentModificationNode = (NetworkModificationNode) current;
        NetworkModificationNode expectedModificationNode = (NetworkModificationNode) expected;
        assertEquals(expectedModificationNode.getNetworkModification(), currentModificationNode.getNetworkModification());
        assertEquals(expectedModificationNode.getVariantId(), currentModificationNode.getVariantId());
    }
}

