/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.network.store.client.NetworkStoreService;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyServerExecutionService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.gridsuite.study.server.utils.TestUtils.synchronizeStudyServerExecutionService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@DisableElasticsearch
@SpringBootTest
@ContextConfigurationWithTestChannel
class ModificationIndexationTest {

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final String CASE_NAME = "caseName";
    private static final String CASE_FORMAT = "caseFormat";
    private static final UUID REPORT_UUID = UUID.randomUUID();

    private static final String NODE_1_NAME = "node1";
    private static final String NODE_2_NAME = "node2";
    private static final String NODE_3_NAME = "node3";
    private static final String NODE_4_NAME = "node4";
    private static final String NODE_5_NAME = "node5";

    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private TestUtils testUtils;

    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private NetworkStoreService networkStoreService;
    @MockitoBean
    private OutputDestination output;

    @MockitoSpyBean
    private StudyServerExecutionService studyServerExecutionService;

    StudyEntity studyEntity;
    RootNetworkEntity rootNetworkEntity;

    NetworkModificationNode node1;
    NetworkModificationNode node2;
    NetworkModificationNode node3;
    NetworkModificationNode node4;
    NetworkModificationNode node5;

    @BeforeEach
    void setup() {
        /* Setup study with following structure
         *       R
         *       |
         *      N1
         *    ------
         *    |    |
         *   N2   (N4)
         *    |    |
         *  (N3)  (N5)
         *
         * () means the node is built
         */
        createStudyAndNodesWithIndexedModification();
        synchronizeStudyServerExecutionService(studyServerExecutionService);
        SQLStatementCountValidator.reset();
    }

    @Test
    void testInvalidateBuiltNodeAndItsChildren() {
        networkModificationTreeService.invalidateNodeTree(studyEntity.getId(), node2.getId(), rootNetworkEntity.getId(), InvalidateNodeTreeParameters.ALL, false);

        verifyInvalidateResults(List.of(node2.getModificationGroupUuid(), node3.getModificationGroupUuid()), 20);
    }

    private void verifyInvalidateResults(List<UUID> getModificationGroupUuids, int expectedSelectCount) {
        ArgumentCaptor<List<UUID>> uuidsCaptor = ArgumentCaptor.forClass(List.class);
        verify(networkModificationService, times(1))
            .deleteIndexedModifications(uuidsCaptor.capture(), any(UUID.class));

        assertEquals(getModificationGroupUuids.stream().collect(Collectors.toSet()), uuidsCaptor.getValue().stream().collect(Collectors.toSet()));

        SQLStatementCountValidator.assertSelectCount(expectedSelectCount);
    }

    @Test
    void testInvalidateNotBuiltNodeAndItsChildren() {
        networkModificationTreeService.invalidateNodeTree(studyEntity.getId(), node4.getId(), rootNetworkEntity.getId(), InvalidateNodeTreeParameters.ALL, false);

        verifyInvalidateResults(List.of(node4.getModificationGroupUuid(), node5.getModificationGroupUuid()), 20);
    }

    @Test
    void testInvalidateBuiltNodeChildrenOnly() {
        networkModificationTreeService.invalidateNodeTree(studyEntity.getId(), node4.getId(), rootNetworkEntity.getId(), InvalidateNodeTreeParameters.ONLY_CHILDREN_BUILD_STATUS, false);

        verifyInvalidateResults(List.of(node5.getModificationGroupUuid()), 11);
    }

    @Test
    void testInvalidateNotBuiltNodeChildrenOnly() {
        networkModificationTreeService.invalidateNodeTree(studyEntity.getId(), node2.getId(), rootNetworkEntity.getId(), InvalidateNodeTreeParameters.ONLY_CHILDREN_BUILD_STATUS, false);

        verifyInvalidateResults(List.of(node2.getModificationGroupUuid(), node3.getModificationGroupUuid()), 20);
    }

    @Test
    void testInvalidateBuiltNodeOnlyWithBuiltChildren() {
        networkModificationTreeService.invalidateNode(studyEntity.getId(), node4.getId(), rootNetworkEntity.getId());

        verifyInvalidateResults(List.of(), 9);
    }

    @Test
    void testInvalidateBuiltNodeOnlyWithoutBuiltChildren() {
        networkModificationTreeService.invalidateNodeTree(studyEntity.getId(), node3.getId(), rootNetworkEntity.getId(), InvalidateNodeTreeParameters.ALL, false);

        verifyInvalidateResults(List.of(node2.getModificationGroupUuid(), node3.getModificationGroupUuid()), 21);
    }

    private void createStudyAndNodesWithIndexedModification() {
        studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        rootNetworkEntity = testUtils.getOneRootNetwork(studyEntity.getId());
        NodeEntity rootNodeEntity = networkModificationTreeService.createRoot(studyEntity);

        /*
         *       R
         *       |
         *      N1
         *    ------
         *    |    |
         *   N2   N4
         *    |    |
         *   N3   N5
         */

        node1 = networkModificationTreeService.createNode(studyEntity, rootNodeEntity.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        node2 = networkModificationTreeService.createNode(studyEntity, node1.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);
        node3 = networkModificationTreeService.createNode(studyEntity, node2.getId(), createModificationNodeInfo(NODE_3_NAME), InsertMode.AFTER, null);
        node4 = networkModificationTreeService.createNode(studyEntity, node1.getId(), createModificationNodeInfo(NODE_4_NAME), InsertMode.CHILD, null);
        node5 = networkModificationTreeService.createNode(studyEntity, node4.getId(), createModificationNodeInfo(NODE_5_NAME), InsertMode.AFTER, null);

        networkModificationTreeService.updateNodeBuildStatus(node3.getId(), rootNetworkEntity.getId(), NodeBuildStatus.from(BuildStatus.BUILT));
        networkModificationTreeService.updateNodeBuildStatus(node4.getId(), rootNetworkEntity.getId(), NodeBuildStatus.from(BuildStatus.BUILT));
        networkModificationTreeService.updateNodeBuildStatus(node5.getId(), rootNetworkEntity.getId(), NodeBuildStatus.from(BuildStatus.BUILT));

    }
}
