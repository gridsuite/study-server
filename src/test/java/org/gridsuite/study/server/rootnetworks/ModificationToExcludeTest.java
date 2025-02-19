package org.gridsuite.study.server.rootnetworks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyCreationRequestEntity;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class ModificationToExcludeTest {
    private static final String USER_ID = "userId";

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final String CASE_NAME = "caseName";
    private static final String CASE_FORMAT = "caseFormat";
    private static final UUID REPORT_UUID = UUID.randomUUID();

    private static final UUID NETWORK_UUID2 = UUID.randomUUID();
    private static final UUID CASE_UUID2 = UUID.randomUUID();
    private static final String CASE_NAME2 = "caseName2";
    private static final String CASE_FORMAT2 = "caseFormat2";
    private static final UUID REPORT_UUID2 = UUID.randomUUID();

    private static final String NODE_1_NAME = "node1";
    private static final String NODE_2_NAME = "node2";

    // modification to exclude uuids
    private static final UUID MODIFICATION_TO_EXCLUDE_1 = UUID.randomUUID();
    private static final UUID MODIFICATION_TO_EXCLUDE_2 = UUID.randomUUID();
    private static final UUID MODIFICATION_TO_EXCLUDE_3 = UUID.randomUUID();
    private static final UUID MODIFICATION_TO_EXCLUDE_4 = UUID.randomUUID();

    // this modification should not be included
    private static final UUID MODIFICATION_NEVER_EXCLUDED = UUID.randomUUID();

    private static final Set<UUID> MODIFICATIONS_TO_EXCLUDE_RN_1 = Set.of(MODIFICATION_TO_EXCLUDE_1, MODIFICATION_TO_EXCLUDE_2, MODIFICATION_TO_EXCLUDE_4);
    private static final Set<UUID> MODIFICATIONS_TO_EXCLUDE_RN_2 = Set.of(MODIFICATION_TO_EXCLUDE_1, MODIFICATION_TO_EXCLUDE_3);

    private static final Map<UUID, UUID> ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP = Map.of(
        MODIFICATION_TO_EXCLUDE_1, UUID.randomUUID(),
        MODIFICATION_TO_EXCLUDE_2, UUID.randomUUID(),
        MODIFICATION_TO_EXCLUDE_3, UUID.randomUUID(),
        MODIFICATION_TO_EXCLUDE_4, UUID.randomUUID(),
        MODIFICATION_NEVER_EXCLUDED, UUID.randomUUID()
    );

    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private StudyService studyService;
    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    NetworkModificationService networkModificationService;
    @MockBean
    private CaseService caseService;
    @MockBean
    private NetworkService networkService;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testExcludeModifications() throws Exception {
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity for each root network
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        UUID invalidModificationUuid = UUID.randomUUID();
        Mockito.doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(networkModificationService).verifyModifications(firstNode.getModificationGroupUuid(), Set.of(invalidModificationUuid));

        // try to disable invalid modification - should return 404
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid(), firstNode.getId())
            .param("uuids", invalidModificationUuid.toString())
            .param("activated", Boolean.FALSE.toString())
            .header(USER_ID, USER_ID))
            .andExpect(status().isNotFound());

        UUID validModificationUuid = UUID.randomUUID();
        Mockito.doNothing().when(networkModificationService).verifyModifications(firstNode.getModificationGroupUuid(), Set.of(validModificationUuid));
        // disable modification then check it's actually stored in database
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid(), firstNode.getId())
                .param("uuids", validModificationUuid.toString())
                .param("activated", Boolean.FALSE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk());
        assertEquals(validModificationUuid, rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow().getModificationsToExclude().stream().findFirst().orElseThrow());

        // enable modification then check it's not stored in database anymore
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid(), firstNode.getId())
                .param("uuids", validModificationUuid.toString())
                .param("activated", Boolean.TRUE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk());
        assertEquals(0, rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow().getModificationsToExclude().size());
    }

    @Test
    void testDuplicateNodeWithModificationsToExclude() {
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity for each root network
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // for each RootNetworkNodeInfoEntity, set some modifications to exclude
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity1.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity1);

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity2.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity2);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP).when(networkModificationService).duplicateModificationsGroup(any(), any());

        // run duplication
        UUID duplicateNodeUuid = networkModificationTreeService.duplicateStudyNode(firstNode.getId(), rootNode.getIdNode(), InsertMode.AFTER);

        // get RootNetworkNodeInfoEntity of duplicate node
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicateNodeUuid, rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicateNodeUuid, rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        // assert those values are actually defined from ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity1.getModificationsToExclude());
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_2.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity2.getModificationsToExclude());
    }

    @Test
    void testDuplicateNodeBetweenStudiesWithModificationsToExclude() {
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity for each root network
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // for each RootNetworkNodeInfoEntity, set some modifications to exclude
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity1.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity1);

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity2.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity2);

        // create another study, the target of the node duplication
        StudyEntity studyEntity2 = TestUtils.createDummyStudy(NETWORK_UUID2, CASE_UUID2, CASE_NAME2, CASE_FORMAT2, REPORT_UUID2);
        createDummyRootNetwork(studyEntity2, "secondRootNetwork2");
        studyRepository.save(studyEntity2);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos2 = studyService.getExistingBasicRootNetworkInfos(studyEntity2.getId());

        // create root node for this new study
        NodeEntity rootNode2 = networkModificationTreeService.createRoot(studyEntity2);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP).when(networkModificationService).duplicateModificationsGroup(any(), any());

        // run duplication
        UUID duplicateNodeUuid = networkModificationTreeService.duplicateStudyNode(firstNode.getId(), rootNode2.getIdNode(), InsertMode.AFTER);

        // get RootNetworkNodeInfoEntity of newly duplicated node
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicateNodeUuid, rootNetworkBasicInfos2.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicateNodeUuid, rootNetworkBasicInfos2.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        // assert those values are empty, duplicating node between studies should empty modifications to exclude
        assertTrue(duplicateRootNetworkNodeInfoEntity1.getModificationsToExclude().isEmpty());
        assertTrue(duplicateRootNetworkNodeInfoEntity2.getModificationsToExclude().isEmpty());
    }

    @Test
    void testDuplicateModificationWithModificationsToExclude() {
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity for each root network
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // for each RootNetworkNodeInfoEntity, set some modifications to exclude
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity1.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity1);

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity2.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity2);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        List<UUID> modificationsToDuplicate = List.of(MODIFICATION_NEVER_EXCLUDED, MODIFICATION_TO_EXCLUDE_1, MODIFICATION_TO_EXCLUDE_2);
        Mockito.doReturn(new NetworkModificationsResult(modificationsToDuplicate.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).toList(), List.of())).when(networkModificationService).duplicateOrInsertModifications(any(), any(), any());

        // run duplication
        studyService.duplicateOrInsertNetworkModifications(studyEntity.getId(), firstNode.getId(), modificationsToDuplicate, USER_ID, StudyConstants.ModificationsActionType.COPY);

        // get RootNetworkNodeInfoEntity of duplicate node
        RootNetworkNodeInfoEntity upToDateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        RootNetworkNodeInfoEntity upToDateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        // check duplicated modification uuids have been added to RootNetworkNodeInfoEntities
        // expected result 1 is the union of MODIFICATIONS_TO_EXCLUDE_RN_1...
        Set<UUID> expectedExcludedModification1 = new HashSet<>(MODIFICATIONS_TO_EXCLUDE_RN_1);

        // ... and duplicate modification uuids
        Set<UUID> modificationUuidsToDuplicate1 = getSetIntersection(MODIFICATIONS_TO_EXCLUDE_RN_1, new HashSet<>(modificationsToDuplicate));
        expectedExcludedModification1.addAll(
            modificationUuidsToDuplicate1.stream()
                .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get)
                .collect(Collectors.toSet())
        );
        assertEquals(expectedExcludedModification1, upToDateRootNetworkNodeInfoEntity1.getModificationsToExclude());

        // expected result 2 is the union of MODIFICATIONS_TO_EXCLUDE_RN_2...
        Set<UUID> expectedExcludedModification2 = new HashSet<>(MODIFICATIONS_TO_EXCLUDE_RN_2);

        // ... and duplicate modification uuids
        Set<UUID> modificationUuidsToDuplicate2 = getSetIntersection(MODIFICATIONS_TO_EXCLUDE_RN_2, new HashSet<>(modificationsToDuplicate));
        expectedExcludedModification2.addAll(
            modificationUuidsToDuplicate2.stream()
                .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get)
                .collect(Collectors.toSet())
        );
        assertEquals(expectedExcludedModification2, upToDateRootNetworkNodeInfoEntity2.getModificationsToExclude());
    }

    @Test
    void testDuplicateStudyTreeWithModificationsToExclude() {
        // PREPARE TEST : 2 root network with 3 nodes (rootNode, node1, node2)
        // This test adds some modifications to exclude in several nodes and root network, duplicate node1 subtree, then check exclusions have been accuratly duplicated
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity for each root network
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, firstNode.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);

        // root network 0, first node, set some excluded modifications
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetwork0NodeInfo1Entity.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        // root network 1, second node, set some excluded modifications
        RootNetworkNodeInfoEntity rootNetwork1NodeInfo2Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(secondNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetwork1NodeInfo2Entity.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetwork1NodeInfo2Entity);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP).when(networkModificationService).duplicateModificationsGroup(any(), any());

        // RUN study tree duplication
        studyService.duplicateStudySubtree(studyEntity.getId(), studyEntity.getId(), firstNode.getId(), rootNode.getIdNode(), null);

        // ASSERT duplications are correct
        // get RootNetworkNodeInfoEntity of duplicate nodes
        UUID firstNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(studyEntity.getId(), NODE_1_NAME + " (1)").get(0).getId();
        UUID secondNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(studyEntity.getId(), NODE_2_NAME + " (1)").get(0).getId();

        // get RootNetworkNodeInfoEntity of duplicate node
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeDuplicateUuid, rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNodeDuplicateUuid, rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        // assert those values are actually defined from ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity1.getModificationsToExclude());
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_2.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity2.getModificationsToExclude());
    }

    @Test
    void testDuplicateStudyWithModificationsToExclude() throws Exception {
        // PREPARE TEST : 2 root network with 3 nodes (rootNode, node1, node2)
        // This test adds some modifications to exclude in several nodes and root network, duplicate node1 subtree, then check exclusions have been accuratly duplicated
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity for each root network
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, firstNode.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);

        // root network 0, first node, set some excluded modifications
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetwork0NodeInfo1Entity.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        // root network 1, second node, set some excluded modifications
        RootNetworkNodeInfoEntity rootNetwork1NodeInfo2Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(secondNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetwork1NodeInfo2Entity.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetwork1NodeInfo2Entity);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP).when(networkModificationService).duplicateModificationsGroup(any(), any());
        Mockito.doReturn(UUID.randomUUID()).when(caseService).duplicateCase(any(), any());
        Mockito.doReturn(UUID.randomUUID()).when(networkService).getNetworkUuid(any());

        // RUN study tree duplication
        UUID newStudyUuid = UUID.randomUUID();
        studyCreationRequestRepository.save(new StudyCreationRequestEntity(newStudyUuid));
        studyService.duplicateStudyAsync(BasicStudyInfos.builder().id(newStudyUuid).build(), studyEntity.getId(), USER_ID);

        List<BasicRootNetworkInfos> newStudyRootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(newStudyUuid);

        // ASSERT duplications are correct
        // get RootNetworkNodeInfoEntity of duplicate nodes
        UUID firstNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(newStudyUuid, NODE_1_NAME).get(0).getId();
        UUID secondNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(newStudyUuid, NODE_2_NAME).get(0).getId();

        // get RootNetworkNodeInfoEntity of duplicate node
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeDuplicateUuid, newStudyRootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNodeDuplicateUuid, newStudyRootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        // assert those values are actually defined from ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity1.getModificationsToExclude());
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_2.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity2.getModificationsToExclude());
    }

    @Test
    void testDeleteExcludedModification() {
        // create study with one root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // add some modifications to exclude
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetwork0NodeInfo1Entity.setModificationsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        // if modification deletion fails, they should not be removed from excluded ones
        Mockito.doThrow(new RuntimeException()).when(networkModificationService).deleteModifications(any(), any());
        UUID modificationToRemove = MODIFICATIONS_TO_EXCLUDE_RN_1.stream().findFirst().orElseThrow();
        assertThrows(RuntimeException.class, () -> studyService.deleteNetworkModifications(studyEntity.getId(), firstNode.getId(), List.of(modificationToRemove), USER_ID));

        Set<UUID> excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND)).getModificationsToExclude();
        Set<UUID> expectedExcludedModifications = MODIFICATIONS_TO_EXCLUDE_RN_1;
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);

        // if it returned OK, then they should be removed
        Mockito.doNothing().when(networkModificationService).deleteModifications(any(), any());
        studyService.deleteNetworkModifications(studyEntity.getId(), firstNode.getId(), List.of(modificationToRemove), USER_ID);

        excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND)).getModificationsToExclude();
        expectedExcludedModifications = MODIFICATIONS_TO_EXCLUDE_RN_1.stream().filter(uuid -> uuid != modificationToRemove).collect(Collectors.toSet());
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);
    }

    private Set<UUID> getSetIntersection(Set<UUID> set1, Set<UUID> set2) {
        Set<UUID> intersection = new HashSet<>(set1); // use the copy constructor
        intersection.retainAll(set2);
        return intersection;
    }

    private void createDummyRootNetwork(StudyEntity studyEntity, String name) {
        RootNetworkEntity rootNetworkEntity = RootNetworkInfos.builder()
            .id(UUID.randomUUID())
            .name(name)
            .caseInfos(new CaseInfos(UUID.randomUUID(), "caseName", "caseFormat"))
            .networkInfos(new NetworkInfos(UUID.randomUUID(), UUID.randomUUID().toString()))
            .reportUuid(UUID.randomUUID())
            .build().toEntity();
        studyEntity.addRootNetwork(rootNetworkEntity);
    }
}
