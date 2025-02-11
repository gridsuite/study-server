package org.gridsuite.study.server.rootnetworks;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.BasicRootNetworkInfos;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class ModificationToExcludeTest {
    private static final String USER_ID = "userId";
    // 1st root network
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final String CASE_NAME = "caseName";
    private static final String CASE_FORMAT = "caseFormat";
    private static final UUID REPORT_UUID = UUID.randomUUID();

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
    private TestUtils testUtils;
    @Autowired
    private StudyService studyService;
    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

    @MockBean
    NetworkModificationService networkModificationService;
    @MockBean
    private ReportService reportService;
    @MockBean
    private EquipmentInfosService equipmentInfosService;
    @MockBean
    private NetworkStoreService networkStoreService;
    @MockBean
    private CaseService caseService;
    @MockBean
    private DynamicSimulationService dynamicSimulationService;
    @MockBean
    private SecurityAnalysisService securityAnalysisService;
    @MockBean
    private LoadFlowService loadFlowService;
    @MockBean
    private NonEvacuatedEnergyService nonEvacuatedEnergyService;
    @MockBean
    private ShortCircuitService shortCircuitService;
    @MockBean
    private SensitivityAnalysisService sensitivityAnalysisService;
    @MockBean
    private StateEstimationService stateEstimationService;
    @MockBean
    private VoltageInitService voltageInitService;
    @MockBean
    private NetworkService networkService;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Test
    public void testDuplicateNodeWithModificationsToExclude() throws Exception {
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getBasicRootNetworkInfos(studyEntity.getId());

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
    public void testDuplicateModificationWithModificationsToExclude() throws Exception {
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getBasicRootNetworkInfos(studyEntity.getId());

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
        studyService.duplicateOrInsertNetworkModifications(studyEntity.getId(), firstNode.getId(), modificationsToDuplicate, "userId", StudyConstants.ModificationsActionType.COPY);

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
    public void testDuplicateStudyTreeWithModificationsToExclude() throws Exception {
        // PREPARE TEST : 2 root network with 3 nodes (rootNode, node1, node2)
        // This test adds some modifications to exclude in several nodes and root network, duplicate node1 subtree, then check exclusions have been accuratly duplicated
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getBasicRootNetworkInfos(studyEntity.getId());

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
    public void testDuplicateStudyWithModificationsToExclude() throws Exception {
        // PREPARE TEST : 2 root network with 3 nodes (rootNode, node1, node2)
        // This test adds some modifications to exclude in several nodes and root network, duplicate node1 subtree, then check exclusions have been accuratly duplicated
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getBasicRootNetworkInfos(studyEntity.getId());

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
        UUID newStudyUuid = studyService.duplicateStudy(studyEntity.getId(), "userId");
        while (studyCreationRequestRepository.findById(newStudyUuid).isPresent()) {
            Thread.sleep(1000);
        }
        List<BasicRootNetworkInfos> newStudyRootNetworkBasicInfos = studyService.getBasicRootNetworkInfos(newStudyUuid);

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
