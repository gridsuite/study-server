package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.InvalidateNodeInfos;
import org.gridsuite.study.server.elasticsearch.BasicModificationInfos;
import org.gridsuite.study.server.elasticsearch.BasicModificationInfosRepository;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BasicModificationTest {

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

    private static final UUID MODIFICATION_1_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_2_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_3_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_4_UUID = UUID.randomUUID();
    private static final UUID MODIFICATION_5_UUID = UUID.randomUUID();

    @Autowired
    private BasicModificationInfosRepository basicModificationInfosRepository;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private TestUtils testUtils;

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
    }

    @Test
    void testInvalidateBuiltNodeAndItsChildren() {
        networkModificationTreeService.invalidateBuild(node2.getId(), rootNetworkEntity.getId(), false, new InvalidateNodeInfos(), false);

        checkModificationExist(MODIFICATION_1_UUID, true);
        checkModificationExist(MODIFICATION_2_UUID, false);
        checkModificationExist(MODIFICATION_3_UUID, false);
        checkModificationExist(MODIFICATION_4_UUID, true);
        checkModificationExist(MODIFICATION_5_UUID, true);
    }

    @Test
    void testInvalidateNotBuiltNodeAndItsChildren() {
        networkModificationTreeService.invalidateBuild(node4.getId(), rootNetworkEntity.getId(), false, new InvalidateNodeInfos(), false);

        checkModificationExist(MODIFICATION_1_UUID, true);
        checkModificationExist(MODIFICATION_2_UUID, true);
        checkModificationExist(MODIFICATION_3_UUID, true);
        checkModificationExist(MODIFICATION_4_UUID, false);
        checkModificationExist(MODIFICATION_5_UUID, false);
    }

    @Test
    void testInvalidateBuiltNodeChildrenOnly() {
        networkModificationTreeService.invalidateBuild(node4.getId(), rootNetworkEntity.getId(), true, new InvalidateNodeInfos(), false);

        checkModificationExist(MODIFICATION_1_UUID, true);
        checkModificationExist(MODIFICATION_2_UUID, true);
        checkModificationExist(MODIFICATION_3_UUID, true);
        checkModificationExist(MODIFICATION_4_UUID, true);
        checkModificationExist(MODIFICATION_5_UUID, false);
    }

    @Test
    void testInvalidateNotBuiltNodeChildrenOnly() {
        networkModificationTreeService.invalidateBuild(node2.getId(), rootNetworkEntity.getId(), true, new InvalidateNodeInfos(), false);

        checkModificationExist(MODIFICATION_1_UUID, true);
        checkModificationExist(MODIFICATION_2_UUID, false);
        checkModificationExist(MODIFICATION_3_UUID, false);
        checkModificationExist(MODIFICATION_4_UUID, true);
        checkModificationExist(MODIFICATION_5_UUID, true);
    }

    @Test
    void testInvalidateBuiltNodeOnlyWithBuiltChildren() {
        networkModificationTreeService.invalidateBuildOfNodeOnly(node4.getId(), rootNetworkEntity.getId(), false, new InvalidateNodeInfos(), false);

        checkModificationExist(MODIFICATION_1_UUID, true);
        checkModificationExist(MODIFICATION_2_UUID, true);
        checkModificationExist(MODIFICATION_3_UUID, true);
        checkModificationExist(MODIFICATION_4_UUID, true);
        checkModificationExist(MODIFICATION_5_UUID, true);
    }

    @Test
    void testInvalidateBuiltNodeOnlyWithoutBuiltChildren() {
        networkModificationTreeService.invalidateBuildOfNodeOnly(node3.getId(), rootNetworkEntity.getId(), false, new InvalidateNodeInfos(), false);

        checkModificationExist(MODIFICATION_1_UUID, true);
        checkModificationExist(MODIFICATION_2_UUID, false);
        checkModificationExist(MODIFICATION_3_UUID, false);
        checkModificationExist(MODIFICATION_4_UUID, true);
        checkModificationExist(MODIFICATION_5_UUID, true);
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

        createBasicModification(MODIFICATION_1_UUID, node1.getModificationGroupUuid(), rootNetworkEntity.getNetworkUuid());
        createBasicModification(MODIFICATION_2_UUID, node2.getModificationGroupUuid(), rootNetworkEntity.getNetworkUuid());
        createBasicModification(MODIFICATION_3_UUID, node3.getModificationGroupUuid(), rootNetworkEntity.getNetworkUuid());
        createBasicModification(MODIFICATION_4_UUID, node4.getModificationGroupUuid(), rootNetworkEntity.getNetworkUuid());
        createBasicModification(MODIFICATION_5_UUID, node5.getModificationGroupUuid(), rootNetworkEntity.getNetworkUuid());

        networkModificationTreeService.updateNodeBuildStatus(node3.getId(), rootNetworkEntity.getId(), NodeBuildStatus.from(BuildStatus.BUILT));
        networkModificationTreeService.updateNodeBuildStatus(node4.getId(), rootNetworkEntity.getId(), NodeBuildStatus.from(BuildStatus.BUILT));
        networkModificationTreeService.updateNodeBuildStatus(node5.getId(), rootNetworkEntity.getId(), NodeBuildStatus.from(BuildStatus.BUILT));

    }

    private void checkModificationExist(UUID modificationUuid, boolean shouldExist) {
        assertEquals(shouldExist, basicModificationInfosRepository.existsById(modificationUuid + "_" + rootNetworkEntity.getNetworkUuid()));
    }

    private BasicModificationInfos createBasicModification(UUID modificationUuid, UUID groupUuid, UUID networkUuid) {
        return basicModificationInfosRepository.save(BasicModificationInfos.builder()
            .modificationUuid(modificationUuid)
            .groupUuid(groupUuid)
            .networkUuid(networkUuid)
            .build());
    }

    @AfterEach
    void cleanUp() {
        basicModificationInfosRepository.deleteAll();
    }
}
