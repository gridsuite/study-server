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
import org.gridsuite.study.server.hypothesisTree.dto.AbstractNode;
import org.gridsuite.study.server.hypothesisTree.dto.HypothesisNode;
import org.gridsuite.study.server.hypothesisTree.dto.ModelNode;
import org.gridsuite.study.server.hypothesisTree.dto.RootNode;
import org.gridsuite.study.server.hypothesisTree.entities.NodeType;
import org.gridsuite.study.server.hypothesisTree.repositories.HypothesisNodeInfoRepository;
import org.gridsuite.study.server.hypothesisTree.repositories.ModelNodeInfoRepository;
import org.gridsuite.study.server.hypothesisTree.repositories.NodeRepository;
import org.gridsuite.study.server.hypothesisTree.repositories.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
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
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class HypothesisTreeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyTest.class);

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private HypothesisNodeInfoRepository hypothesisNodeInfoRepository;

    @Autowired
    private ModelNodeInfoRepository modelNodeInfoRepository;

    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;

    @Autowired
    private HypothesisTreeService hypothesisTreeService;

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
        hypothesisNodeInfoRepository.deleteAll();
        modelNodeInfoRepository.deleteAll();
        rootNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        studyRepository.deleteAll();
    }

    StudyEntity createDummyStudy() {
        return StudyEntity.builder().id(UUID.randomUUID()).caseFormat("").caseUuid(UUID.randomUUID())
            .date(LocalDateTime.now())
            .description("")
            .networkId("netId")
            .networkUuid(UUID.randomUUID())
            .studyName("studyName")
            .userId("userId")
            .loadFlowParameters(new LoadFlowParametersEntity())
            .build();
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
        final HypothesisNode hypo = buildHypothesis("hypo", "potamus", UUID.randomUUID());
        final ModelNode model = buildModel("loadflow", "dance", "loadflow");
        createNode(root, hypo);
        createNode(root, model);
        root = getRootNode(root.getStudyId());

        List<AbstractNode> children = root.getChildren();
        assertEquals(2, children.size());
        /*
        *  expected :
        *       root
        *      /    \
        *  model     hypo
        */

        if (children.get(0).getType() == NodeType.HYPOTHESIS) {
            assertHypoNodeEquals(hypo, children.get(0));
            assertModelNodeEquals(model, children.get(1));
        } else {
            assertHypoNodeEquals(hypo, children.get(1));
            assertModelNodeEquals(model, children.get(0));
        }

        model.setName("niark");
        hypo.setName("condriak");
        createNode(children.get(1), model);
        createNode(children.get(1), hypo);

        /*  expected
        *       root
        *      /    \
        *  node      node
        *           /    \
        *hypo(condriak)   model(niark)
         */

        root = getRootNode(root.getStudyId());
        AbstractNode child;
        if (root.getChildren().get(0).getName().equals(children.get(1).getName())) {
            child = root.getChildren().get(0);
        } else {
            child = root.getChildren().get(1);
        }
        assertEquals(2, child.getChildren().size());
        children = child.getChildren();
        if (children.get(0).getType() == NodeType.HYPOTHESIS) {
            assertHypoNodeEquals(hypo, children.get(0));
            assertModelNodeEquals(model, children.get(1));
        } else {
            assertHypoNodeEquals(hypo, children.get(1));
            assertModelNodeEquals(model, children.get(0));
        }

        webTestClient.delete().uri("/v1/tree/deleteNode/{id}?deleteChildren={delete}", child.getId(), false)
            .exchange()
            .expectStatus().isOk();

        /*  expected
        *     root
        *   /  |   \
        * node node node
        */

        root = getRootNode(root.getStudyId());
        assertEquals(3, root.getChildren().size());
        child = root.getChildren().get(0);
        createNode(child, hypo);

        webTestClient.delete().uri("/v1/tree/deleteNode/{id}?deleteChildren={delete}", child.getId(), true)
            .exchange()
            .expectStatus().isOk();

        /* expected
        *   root
        *   /   \
        * node  node
         */
        root = getRootNode(root.getStudyId());
        assertEquals(2, root.getChildren().size());
        assertEquals(3, nodeRepository.findAll().size());

        hypothesisTreeService.deleteRoot(root.getStudyId());
        assertEquals(0, nodeRepository.findAll().size());

        webTestClient.put().uri("/v1/tree/createNode/{id}", UUID.randomUUID()).bodyValue(hypo)
            .exchange()
            .expectStatus().isNotFound();

    }

    @Test
    public void testNodeUpdate() throws Exception {
        RootNode root = createRoot();
        final HypothesisNode hypo = buildHypothesis("hypo", "potamus", UUID.randomUUID());
        createNode(root, hypo);
        hypo.setName("grunt");
        hypo.setHypothesis(UUID.randomUUID());
        root = getRootNode(root.getStudyId());
        hypo.setId(root.getChildren().get(0).getId());
        webTestClient.put().uri("/v1/tree/updateNode").bodyValue(hypo)
            .exchange()
            .expectStatus().isOk();
        root = getRootNode(root.getStudyId());
        assertEquals(1, root.getChildren().size());
        assertNodeEquals(hypo, root.getChildren().get(0));

        hypo.setId(UUID.randomUUID());
        webTestClient.put().uri("/v1/tree/updateNode").bodyValue(hypo)
            .exchange()
            .expectStatus().isNotFound();

    }

    private void createNode(AbstractNode parentNode, AbstractNode newNode) {
        newNode.setId(null);
        webTestClient.put().uri("/v1/tree/createNode/{id}", parentNode.getId()).bodyValue(newNode)
            .exchange()
            .expectStatus().isOk();
    }

    @NotNull
    private StudyEntity insertDummyStudy() {
        StudyEntity studyEntity = createDummyStudy();
        var study = studyRepository.save(studyEntity);
        hypothesisTreeService.createRoot(study.getId());
        return study;
    }

    private RootNode createRoot() {
        var study = insertDummyStudy();
        return hypothesisTreeService.getStudyTree(study.getId()).block();
    }

    private HypothesisNode buildHypothesis(String name, String description, UUID idHypo) {
        return HypothesisNode.builder().name(name).description(description).hypothesis(idHypo).children(Collections.emptyList()).build();
    }

    private ModelNode buildModel(String name, String description, String model) {
        return ModelNode.builder().name(name).description(description).model(model).children(Collections.emptyList()).build();
    }

    private void assertNodeEquals(AbstractNode expected, AbstractNode current) {
        assertEquals(expected.getName(), current.getName());
        assertEquals(expected.getDescription(), current.getDescription());
        assertEquals(expected.getChildren().size(), current.getChildren().size());
        assertEquals(expected.getType(), current.getType());
    }

    private void assertModelNodeEquals(ModelNode expected, AbstractNode current) {
        assertNodeEquals(expected, current);
        ModelNode node = (ModelNode) current;
        assertEquals(expected.getModel(), node.getModel());
    }

    private void assertHypoNodeEquals(HypothesisNode expected, AbstractNode current) {
        assertNodeEquals(expected, current);
        HypothesisNode node = (HypothesisNode) current;
        assertEquals(expected.getHypothesis(), node.getHypothesis());
    }

}
