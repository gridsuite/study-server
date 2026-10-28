/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.nodeactivity.BatchActionRepository;
import org.gridsuite.study.server.repository.nodeactivity.NodeActivityRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.nodeactivity.NodeActivityType.UNBUILD;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Slimane amar <slimane.amar at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class BatchActionServiceTest {
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NodeActivityRepository nodeActivityRepository;
    @Autowired
    private BatchActionRepository batchActionRepository;
    @Autowired
    private NodeActivityRunnerService nodeActivityRunnerService;

    @Autowired
    private TestUtils studyTestUtils;

    @MockitoSpyBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockitoBean
    private NotificationService notificationService;

    private UUID studyUuid;
    private UUID nodeUuid;
    private UUID rootNetworkUuid;

    @BeforeEach
    void setUp() {
        cleanDB();
        createStudy();
    }

    @AfterEach
    void tearDown() {
        cleanDB();
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        nodeActivityRepository.deleteAll();
        batchActionRepository.deleteAll();
    }

    @Test
    void testRunBatchActionImmediate() {
        nodeActivityRunnerService.runBatchAction(BatchActionType.INVALIDATE_NODE_TREE, studyUuid, nodeUuid);

        verify(networkModificationTreeService, times(1)).invalidateNodeTree(studyUuid, nodeUuid);
        assertEquals(0, batchActionRepository.count());
    }

    @Test
    void testRunBatchActionDeferred() {
        nodeActivityRunnerService.addNodeActivities(UNBUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid));
        nodeActivityRunnerService.runBatchAction(BatchActionType.INVALIDATE_NODE_TREE, studyUuid, nodeUuid);

        verify(networkModificationTreeService, times(0)).invalidateNodeTree(studyUuid, nodeUuid);
        assertEquals(1, batchActionRepository.count());

        nodeActivityRunnerService.removeActivities(studyUuid, rootNetworkUuid, List.of(nodeUuid));

        verify(networkModificationTreeService, times(1)).invalidateNodeTree(studyUuid, nodeUuid);
        assertEquals(0, batchActionRepository.count());
    }

    private void createStudy() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.randomUUID(), UUID.randomUUID(), "case_name", "case_format", UUID.randomUUID());
        studyUuid = studyRepository.save(studyEntity).getId();
        rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        NodeEntity rootNodeEntity = networkModificationTreeService.createRoot(studyEntity);
        nodeUuid = networkModificationTreeService.createNode(studyEntity, rootNodeEntity.getIdNode(), createModificationNodeInfo("node"), InsertMode.AFTER, null).getId();
    }
}
