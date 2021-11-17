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
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.ModelNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.NetworkModificationTreeService.*;
import static org.gridsuite.study.server.StudyService.HEADER_UPDATE_TYPE;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class NetworkModificationTreeTest {

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

    ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        Configuration.defaultConfiguration();
        MockitoAnnotations.initMocks(this);
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);

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

    StudyEntity createDummyStudy() {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat("").caseUuid(UUID.randomUUID())
            .date(LocalDateTime.now())
            .networkId("netId")
            .networkUuid(UUID.randomUUID())
            .userId("userId")
            .loadFlowParameters(new LoadFlowParametersEntity())
            .build();
    }

    @Test
    public void testStudyWithNoNodes() {
        StudyEntity studyEntity = createDummyStudy();
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

        webTestClient.get().uri("/v1/tree/{id}", UUID.randomUUID())
            .exchange()
            .expectStatus().isNotFound();

        webTestClient.delete().uri("/v1/tree/nodes/{id}?deleteChildren={delete}", root.getId(), false)
            .exchange()
            .expectStatus().is4xxClientError();
    }

    private AbstractNode getNode(UUID idNode) throws IOException {
        return objectMapper.readValue(webTestClient.get().uri("/v1/tree/nodes/{id}", idNode)
            .exchange()
            .expectStatus().isOk()
            .expectBody().returnResult().getResponseBody(), new TypeReference<>() {
            });
    }

    private RootNode getRootNode(UUID study) throws IOException {
        return objectMapper.readValue(webTestClient.get().uri("/v1/tree/{id}", study)
            .exchange()
            .expectStatus().isOk()
            .expectBody().returnResult().getResponseBody(), new TypeReference<>() {
            });
    }

    @Test
    public void testNodeManipulation() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID());
        final ModelNode model = buildModel("loadflow", "dance", "loadflow");
        createNode(root, model);
        createNode(root, hypo);
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
        createNode(children.get(1), model);
        createNode(children.get(1), hypo);

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

        deleteNode(child, false, Set.of(child));

        /*  expected
              root
            /   |   \
          node node node
        */

        root = getRootNode(root.getStudyId());
        assertEquals(3, root.getChildren().size());
        child = root.getChildren().get(0);
        createNode(child, hypo);

        deleteNode(child, true, Set.of(hypo, child));

        /* expected
            root
            /   \
          node  node
         */
        root = getRootNode(root.getStudyId());
        assertEquals(2, root.getChildren().size());
        assertEquals(3, nodeRepository.findAll().size());

        networkModificationTreeService.doDeleteTree(root.getStudyId());
        assertEquals(0, nodeRepository.findAll().size());

        webTestClient.post().uri("/v1/tree/nodes/{id}", UUID.randomUUID()).bodyValue(hypo)
            .exchange()
            .expectStatus().isNotFound();

    }

    private void deleteNode(AbstractNode child, boolean deleteChildren, Set<AbstractNode> expectedDeletion) {
        webTestClient.delete().uri("/v1/tree/nodes/{id}?deleteChildren={delete}", child.getId(), deleteChildren)
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
        final NetworkModificationNode networkModification = buildNetworkModification("hypo", "potamus", UUID.randomUUID());
        /* trying to insert before root */
        webTestClient.post().uri("/v1/tree/nodes/{id}?mode=BEFORE", root.getId()).bodyValue(networkModification)
            .exchange()
            .expectStatus().is4xxClientError();

        createNode(root, networkModification);
        createNode(root, networkModification);
        root = getRootNode(root.getStudyId());
        /* root
            / \
           n1  n2
         */
        AbstractNode unchangedNode = root.getChildren().get(0);
        AbstractNode willBeMoved = root.getChildren().get(1);
        insertNode(willBeMoved, networkModification, InsertMode.BEFORE);
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

        webTestClient.post().uri("/v1/tree/nodes/{id}", UUID.randomUUID()).bodyValue(networkModification)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    public void testInsertAfter() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", null);
        final ModelNode model = buildModel("loadflow", "dance", "loadflow");
        createNode(root, model);
        createNode(root, model);
        root = getRootNode(root.getStudyId());
        var originalChildren = root.getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        insertNode(root, hypo, InsertMode.AFTER);
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        var grandChildren = getRootNode(root.getStudyId()).getChildren().get(0).getChildren().stream().map(AbstractNode::getId).collect(Collectors.toSet());
        assertEquals(originalChildren, grandChildren);

        String variantId = networkModificationTreeService.getVariantId(hypo.getId()).block();
        assertTrue(!StringUtils.isEmpty(variantId));

        assertEquals(0, networkModificationTreeService.getAllModificationGroupUuids(root.getStudyId()).size());
        UUID modificationGroupUuid = networkModificationTreeService.getModificationGroupUuid(hypo.getId()).block();
        assertNotNull(modificationGroupUuid);
        assertEquals(1, networkModificationTreeService.getAllModificationGroupUuids(root.getStudyId()).size());
        modificationGroupUuid = networkModificationTreeService.getModificationGroupUuid(root.getId()).block();
        assertNotNull(modificationGroupUuid);
        assertEquals(2, networkModificationTreeService.getAllModificationGroupUuids(root.getStudyId()).size());

        UUID modelUuid = model.getId();
        assertTrue(networkModificationTreeService.doGetModificationGroupUuid(modelUuid, true).isEmpty());
        assertTrue(networkModificationTreeService.doGetVariantId(modelUuid, true).isEmpty());
    }

    @Test
    public void testNodeUpdate() throws Exception {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID());
        createNode(root, hypo);
        hypo.setName("grunt");
        hypo.setNetworkModification(UUID.randomUUID());
        root = getRootNode(root.getStudyId());
        hypo.setId(root.getChildren().get(0).getId());
        webTestClient.put().uri("/v1/tree/nodes").bodyValue(hypo)
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

        hypo.setId(UUID.randomUUID());
        webTestClient.put().uri("/v1/tree/nodes").bodyValue(hypo)
            .exchange()
            .expectStatus().isNotFound();

    }

    @SneakyThrows
    @Test
    public void testLightNode() {
        RootNode root = createRoot();
        final NetworkModificationNode hypo = buildNetworkModification("hypo", "potamus", UUID.randomUUID());
        createNode(root, hypo);
        createNode(root, hypo);
        createNode(root, hypo);
        AbstractNode node = getNode(root.getId());
        assertEquals(3, node.getChildrenIds().size());
    }

    private void createNode(AbstractNode parentNode, AbstractNode newNode) {
        newNode.setId(null);
        webTestClient.post().uri("/v1/tree/nodes/{id}", parentNode.getId()).bodyValue(newNode)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(HEADER_INSERT_BEFORE));
    }

    private void insertNode(AbstractNode parentNode, AbstractNode newNode, InsertMode mode) {
        newNode.setId(null);
        webTestClient.post().uri("/v1/tree/nodes/{id}?mode={mode}", parentNode.getId(), mode).bodyValue(newNode)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        assertEquals(NODE_CREATED, mess.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(parentNode.getId(), mess.getHeaders().get(HEADER_NODE));
        assertEquals(mode.name(), mess.getHeaders().get(HEADER_INSERT_BEFORE));
        newNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(HEADER_NEW_NODE))));
    }

    @NotNull
    private StudyEntity insertDummyStudy() {
        StudyEntity studyEntity = createDummyStudy();
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private RootNode createRoot() {
        var study = insertDummyStudy();
        return networkModificationTreeService.getStudyTree(study.getId()).block();
    }

    private NetworkModificationNode buildNetworkModification(String name, String description, UUID idHypo) {
        return NetworkModificationNode.builder().name(name).description(description).networkModification(idHypo).children(Collections.emptyList()).build();
    }

    private ModelNode buildModel(String name, String description, String model) {
        return ModelNode.builder().name(name).description(description).model(model).children(Collections.emptyList()).build();
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
    }

    private void assertHypoNodeEquals(AbstractNode expected, AbstractNode current) {
        NetworkModificationNode currentModificationNode = (NetworkModificationNode) current;
        NetworkModificationNode expectedModificationNode = (NetworkModificationNode) expected;
        assertEquals(expectedModificationNode.getNetworkModification(), currentModificationNode.getNetworkModification());
    }

}
