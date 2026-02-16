/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.rootnetworks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.networkmodificationtree.dto.ExcludedNetworkModifications;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
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
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @MockitoBean
    NetworkModificationService networkModificationService;
    @MockitoBean
    private CaseService caseService;
    @MockitoBean
    private NetworkService networkService;
    @MockitoBean
    private UserAdminService userAdminService;
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
        assertEquals(validModificationUuid, rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow().getModificationsUuidsToExclude().stream().findFirst().orElseThrow());
        // enable modification then check it's not stored in database anymore
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid(), firstNode.getId())
                        .param("uuids", validModificationUuid.toString())
                        .param("activated", Boolean.TRUE.toString())
                        .header(USER_ID, USER_ID))
                .andExpect(status().isOk());
        assertEquals(0, rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow().getModificationsUuidsToExclude().size());
    }

    @Test
    void testDuplicateModificationsBetweenStudiesWithCommonRootNetworkTags() {
        // -------- Study 1  --------
        StudyEntity study1 = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(study1, "rnA", "ok");  // common root network
        createDummyRootNetwork(study1, "rnB", "no1");
        studyRepository.save(study1);

        List<BasicRootNetworkInfos> rootNetworksStudy1 = studyService.getExistingBasicRootNetworkInfos(study1.getId());

        NodeEntity rootNode1 = networkModificationTreeService.createRoot(study1);
        NetworkModificationNode node1 = networkModificationTreeService.createNode(
            study1, rootNode1.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null
        );

        // set modifications to exclude only for common root network
        BasicRootNetworkInfos commonRnStudy1 = rootNetworksStudy1.stream()
            .filter(rn -> "ok".equals(rn.tag()))
            .findFirst()
            .orElseThrow();
        RootNetworkNodeInfoEntity commonRnNodeInfoStudy1 = rootNetworkNodeInfoRepository
            .findByNodeInfoIdAndRootNetworkId(node1.getId(), commonRnStudy1.rootNetworkUuid())
            .orElseThrow();
        commonRnNodeInfoStudy1.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(commonRnNodeInfoStudy1);

        // -------- Study 2  --------
        StudyEntity study2 = TestUtils.createDummyStudy(NETWORK_UUID2, CASE_UUID2, CASE_NAME2, CASE_FORMAT2, REPORT_UUID2);
        createDummyRootNetwork(study2, "rnX", "no2"); // not common
        createDummyRootNetwork(study2, "rnA", "ok");  // common root network
        studyRepository.save(study2);

        List<BasicRootNetworkInfos> rootNetworksStudy2 = studyService.getExistingBasicRootNetworkInfos(study2.getId());
        NodeEntity rootNode2 = networkModificationTreeService.createRoot(study2);

        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP)
            .when(networkModificationService)
            .duplicateModificationsGroup(any(), any());

        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(
            node1.getId(), rootNode2.getIdNode(), InsertMode.AFTER
        );

        // -------- Assertions --------
        // For common root network -> modifications should be copied
        BasicRootNetworkInfos commonRnStudy2 = rootNetworksStudy2.stream()
            .filter(rn -> "ok".equals(rn.tag()))
            .findFirst()
            .orElseThrow();
        RootNetworkNodeInfoEntity commonRnNodeInfoStudy2 = rootNetworkNodeInfoRepository
            .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicatedNodeUuid, commonRnStudy2.rootNetworkUuid())
            .orElseThrow();
        Set<UUID> expectedExcluded = MODIFICATIONS_TO_EXCLUDE_RN_1.stream()
            .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get)
            .collect(Collectors.toSet());
        assertEquals(expectedExcluded, commonRnNodeInfoStudy2.getModificationsUuidsToExclude(),
            "Modifications to exclude should be copied for common root network");

        // unique root network -> modifications should be empty
        BasicRootNetworkInfos uniqueRnStudy2 = rootNetworksStudy2.stream()
            .filter(rn -> "no2".equals(rn.tag()))
            .findFirst()
            .orElseThrow();
        RootNetworkNodeInfoEntity uniqueRnNodeInfoStudy2 = rootNetworkNodeInfoRepository
            .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicatedNodeUuid, uniqueRnStudy2.rootNetworkUuid())
            .orElseThrow();
        assertTrue(uniqueRnNodeInfoStudy2.getModificationsUuidsToExclude().isEmpty(),
            "Modifications to exclude should be empty for non-common root network");
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
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetworkNodeInfoEntity1.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity1);

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetworkNodeInfoEntity2.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity2);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP).when(networkModificationService).duplicateModificationsGroup(any(), any());

        // run duplication
        UUID duplicateNodeUuid = networkModificationTreeService.duplicateStudyNode(firstNode.getId(), rootNode.getIdNode(), InsertMode.AFTER);

        // get RootNetworkNodeInfoEntity of duplicate node
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicateNodeUuid, rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicateNodeUuid, rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));

        // assert those values are actually defined from ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity1.getModificationsUuidsToExclude());
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_2.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity2.getModificationsUuidsToExclude());
    }

    @Test
    void testDuplicateNodeBetweenStudiesWithModificationsToExcludeForCommonTag() {
        // -------- Study 1 --------
        StudyEntity study1 = TestUtils.createDummyStudy(
            NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID
        );
        createDummyRootNetwork(study1, "rootNetwork1", "X"); // RN1 with tag X
        createDummyRootNetwork(study1, "rootNetwork2", "Y"); // RN2 with tag Y
        studyRepository.save(study1);

        List<BasicRootNetworkInfos> rootNetworksStudy1 = studyService.getExistingBasicRootNetworkInfos(study1.getId());

        NodeEntity rootNodeS1 = networkModificationTreeService.createRoot(study1);
        NetworkModificationNode nodeS1 = networkModificationTreeService.createNode(study1, rootNodeS1.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        UUID modificationOrigineUuid = MODIFICATION_TO_EXCLUDE_1;

        // Exclude Modifications for RN2 (tag Y)
        BasicRootNetworkInfos rnYStudy1 = rootNetworksStudy1.stream()
            .filter(rn -> "Y".equals(rn.tag()))
            .findFirst()
            .orElseThrow();

        RootNetworkNodeInfoEntity rnInfoStudy1 = rootNetworkNodeInfoRepository
            .findByNodeInfoIdAndRootNetworkId(nodeS1.getId(), rnYStudy1.rootNetworkUuid())
            .orElseThrow();

        rnInfoStudy1.setModificationsUuidsToExclude(Set.of(modificationOrigineUuid));
        rootNetworkNodeInfoRepository.save(rnInfoStudy1);

        // -------- Study 2 --------
        StudyEntity study2 = TestUtils.createDummyStudy(
            NETWORK_UUID2, CASE_UUID2, CASE_NAME2, CASE_FORMAT2, REPORT_UUID2
        );
        createDummyRootNetwork(study2, "rootNetwork1_2", "X"); // RN1 with tag X
        createDummyRootNetwork(study2, "rootNetwork2_2", "Y"); // RN2 with tag Y
        studyRepository.save(study2);

        List<BasicRootNetworkInfos> rootNetworksStudy2 =
            studyService.getExistingBasicRootNetworkInfos(study2.getId());

        NodeEntity rootNodeS2 = networkModificationTreeService.createRoot(study2);

        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP)
            .when(networkModificationService)
            .duplicateModificationsGroup(any(), any());

        // -------- Duplicate node --------
        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeS1.getId(), rootNodeS2.getIdNode(), InsertMode.AFTER);

        UUID modificationDupliqueeUuid = ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP.get(modificationOrigineUuid);

        // -------- Assertions --------

        // RN2 tag Y (common) -> modification EXCLUDED
        BasicRootNetworkInfos rnYStudy2 = rootNetworksStudy2.stream()
            .filter(rn -> "Y".equals(rn.tag()))
            .findFirst()
            .orElseThrow();

        RootNetworkNodeInfoEntity rnYInfoStudy2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicatedNodeUuid, rnYStudy2.rootNetworkUuid()).orElseThrow();

        assertTrue(rnYInfoStudy2.getModificationsUuidsToExclude().contains(modificationDupliqueeUuid),
            "Duplicated Modification should be excluded on RN2 (tag Y)");

        // RN1 tag X -> modification active (not excluded)
        BasicRootNetworkInfos rnXStudy2 = rootNetworksStudy2.stream()
            .filter(rn -> "X".equals(rn.tag()))
            .findFirst()
            .orElseThrow();

        RootNetworkNodeInfoEntity rnXInfoStudy2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicatedNodeUuid, rnXStudy2.rootNetworkUuid()).orElseThrow();

        assertTrue(rnXInfoStudy2.getModificationsUuidsToExclude().isEmpty(), "Modification is active on RN1 (tag X)");
    }

    @Test
    void testDuplicateNodeFromStudy1ToStudy2WithCommonRootNetwork() {
        // -------- Study 1 setup --------
        StudyEntity study1 = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(study1, "rn1");
        createDummyRootNetwork(study1, "rn2", "rn2"); // has modifications to exclude
        createDummyRootNetwork(study1, "oth");
        studyRepository.save(study1);

        List<BasicRootNetworkInfos> rootNetworksStudy1 = studyService.getExistingBasicRootNetworkInfos(study1.getId());

        NodeEntity rootNode1 = networkModificationTreeService.createRoot(study1);
        NetworkModificationNode node1 = networkModificationTreeService.createNode(
            study1, rootNode1.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null
        );

        // set modifications to exclude only on rn2
        BasicRootNetworkInfos rn2Study1 = rootNetworksStudy1.stream()
            .filter(rn -> "rn2".equals(rn.tag()))
            .findFirst()
            .orElseThrow();

        RootNetworkNodeInfoEntity rn2NodeInfoStudy1 = rootNetworkNodeInfoRepository
            .findByNodeInfoIdAndRootNetworkId(node1.getId(), rn2Study1.rootNetworkUuid())
            .orElseThrow();

        rn2NodeInfoStudy1.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rn2NodeInfoStudy1);

        // -------- Study 2 setup --------
        StudyEntity study2 = TestUtils.createDummyStudy(NETWORK_UUID2, CASE_UUID2, CASE_NAME2, CASE_FORMAT2, REPORT_UUID2);
        createDummyRootNetwork(study2, "rn0", "rn0");
        createDummyRootNetwork(study2, "rn2", "rn2"); // common root network
        createDummyRootNetwork(study2, "oth");
        studyRepository.save(study2);

        List<BasicRootNetworkInfos> rootNetworksStudy2 = studyService.getExistingBasicRootNetworkInfos(study2.getId());

        NodeEntity rootNode2 = networkModificationTreeService.createRoot(study2);

        // mock duplication of modifications
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP)
            .when(networkModificationService)
            .duplicateModificationsGroup(any(), any());

        // -------- Duplicate node --------
        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(
            node1.getId(), rootNode2.getIdNode(), InsertMode.AFTER
        );

        // -------- Assertions --------
        // rn2 (common) -> modifications should be copied
        BasicRootNetworkInfos rn2Study2 = rootNetworksStudy2.stream()
            .filter(rn -> "rn2".equals(rn.tag()))
            .findFirst()
            .orElseThrow();

        RootNetworkNodeInfoEntity rn2NodeInfoStudy2 = rootNetworkNodeInfoRepository
            .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicatedNodeUuid, rn2Study2.rootNetworkUuid())
            .orElseThrow();

        Set<UUID> expectedExcluded = MODIFICATIONS_TO_EXCLUDE_RN_2.stream()
            .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get)
            .collect(Collectors.toSet());

        assertEquals(expectedExcluded, rn2NodeInfoStudy2.getModificationsUuidsToExclude(),
            "Modifications to exclude should be copied for common root network rn2");

        // rn0 (not common) -> modifications should be empty
        BasicRootNetworkInfos rn0Study2 = rootNetworksStudy2.stream()
            .filter(rn -> "rn0".equals(rn.tag()))
            .findFirst()
            .orElseThrow();

        RootNetworkNodeInfoEntity rn0NodeInfoStudy2 = rootNetworkNodeInfoRepository
            .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(duplicatedNodeUuid, rn0Study2.rootNetworkUuid())
            .orElseThrow();

        assertTrue(rn0NodeInfoStudy2.getModificationsUuidsToExclude().isEmpty(),
            "Modifications to exclude should be empty for non-common root network rn0");
    }

    @Test
    void testGetModificationsToExclude() throws Exception {
        // create study with two root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork");
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity for each root network
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // for each RootNetworkNodeInfoEntity, set some modifications to exclude
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetworkNodeInfoEntity1.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity1);

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetworkNodeInfoEntity2.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity2);

        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/excluded-network-modifications",
                         studyEntity.getId(), firstNode.getId())
                        .header(USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andReturn();

        List<ExcludedNetworkModifications> excludedNetworkModifications = objectMapper.readValue(result.getResponse().getContentAsString(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });

        assertEquals(2, excludedNetworkModifications.size());

        assertEquals(excludedNetworkModifications.getFirst().rootNetworkUuid(), rootNetworkBasicInfos.getFirst().rootNetworkUuid());
        assertEquals(excludedNetworkModifications.getFirst().modificationUuidsToExclude(), rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow().getModificationsUuidsToExclude());
        assertEquals(excludedNetworkModifications.get(1).rootNetworkUuid(), rootNetworkBasicInfos.get(1).rootNetworkUuid());
        assertEquals(excludedNetworkModifications.get(1).modificationUuidsToExclude(), rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow().getModificationsUuidsToExclude());
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
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);

        // for each RootNetworkNodeInfoEntity, set some modifications to exclude
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetworkNodeInfoEntity1.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity1);

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetworkNodeInfoEntity2.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity2);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        List<UUID> modificationsToDuplicate = List.of(MODIFICATION_NEVER_EXCLUDED, MODIFICATION_TO_EXCLUDE_1, MODIFICATION_TO_EXCLUDE_2);
        Mockito.doReturn(new NetworkModificationsResult(modificationsToDuplicate.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).toList(), List.of())).when(networkModificationService).duplicateOrInsertModifications(any(), any(), any());

        // duplicate (excluded) modification uuids for RH1
        Set<UUID> modificationUuidsToDuplicate1 = Sets.intersection(MODIFICATIONS_TO_EXCLUDE_RN_1, new HashSet<>(modificationsToDuplicate));
        Set<UUID> expectedExcludedModification1 = modificationUuidsToDuplicate1.stream()
            .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet());

        // duplicate (excluded) modification uuids for RH2
        Set<UUID> modificationUuidsToDuplicate2 = Sets.intersection(MODIFICATIONS_TO_EXCLUDE_RN_2, new HashSet<>(modificationsToDuplicate));
        Set<UUID> expectedExcludedModification2 = modificationUuidsToDuplicate2.stream()
            .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet());

        // test duplication on same node : node1
        studyService.duplicateOrInsertNetworkModifications(studyEntity.getId(), firstNode.getId(), studyEntity.getId(), firstNode.getId(), modificationsToDuplicate, USER_ID, StudyConstants.ModificationsActionType.COPY);
        RootNetworkNodeInfoEntity upToDateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        RootNetworkNodeInfoEntity upToDateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        assertEquals(Sets.union(expectedExcludedModification1, MODIFICATIONS_TO_EXCLUDE_RN_1), upToDateRootNetworkNodeInfoEntity1.getModificationsUuidsToExclude());
        assertEquals(Sets.union(expectedExcludedModification2, MODIFICATIONS_TO_EXCLUDE_RN_2), upToDateRootNetworkNodeInfoEntity2.getModificationsUuidsToExclude());

        // test duplication on different nodes : node1 -> node2
        studyService.duplicateOrInsertNetworkModifications(studyEntity.getId(), secondNode.getId(), studyEntity.getId(), firstNode.getId(), modificationsToDuplicate, USER_ID, StudyConstants.ModificationsActionType.COPY);
        upToDateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNode.getId(), rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        upToDateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        assertEquals(expectedExcludedModification1, upToDateRootNetworkNodeInfoEntity1.getModificationsUuidsToExclude());
        assertEquals(expectedExcludedModification2, upToDateRootNetworkNodeInfoEntity2.getModificationsUuidsToExclude());
    }

    @Test
    void testDuplicateStudyTreeWithModificationsToExclude() {
        // PREPARE TEST : 2 root network with 3 nodes (rootNode, node1, node2)
        // This test adds some modifications to exclude in several al nodes and root network, duplicate node1 subtree, then check exclusions have been accuratly duplicated
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
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork0NodeInfo1Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        // root network 1, second node, set some excluded modifications
        RootNetworkNodeInfoEntity rootNetwork1NodeInfo2Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(secondNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork1NodeInfo2Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetwork1NodeInfo2Entity);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP).when(networkModificationService).duplicateModificationsGroup(any(), any());

        // RUN study tree duplication
        studyService.duplicateStudySubtree(studyEntity.getId(), studyEntity.getId(), firstNode.getId(), rootNode.getIdNode(), null);

        // ASSERT duplications are correct
        // get RootNetworkNodeInfoEntity of duplicate nodes
        UUID firstNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(studyEntity.getId(), NODE_1_NAME + " (1)").getFirst().getId();
        UUID secondNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(studyEntity.getId(), NODE_2_NAME + " (1)").getFirst().getId();

        // get RootNetworkNodeInfoEntity of duplicate node
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeDuplicateUuid, rootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNodeDuplicateUuid, rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));

        // assert those values are actually defined from ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity1.getModificationsUuidsToExclude());
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_2.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity2.getModificationsUuidsToExclude());
    }

    @Test
    void testDuplicateStudyWithModificationsToExclude() {
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
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork0NodeInfo1Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        // root network 1, second node, set some excluded modifications
        RootNetworkNodeInfoEntity rootNetwork1NodeInfo2Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(secondNode.getId(), rootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork1NodeInfo2Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetwork1NodeInfo2Entity);

        // mock duplicateModificationsGroup to return a mapping between origin modification uuid and their duplicate uuid
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP).when(networkModificationService).duplicateModificationsGroup(any(), any());
        Mockito.doReturn(UUID.randomUUID()).when(caseService).duplicateCase(any(), any());
        Mockito.doReturn(UUID.randomUUID()).when(networkService).getNetworkUuid(any());

        // RUN study tree duplication
        UUID newStudyUuid = UUID.randomUUID();
        studyCreationRequestRepository.save(new StudyCreationRequestEntity(newStudyUuid, "firstRootNetworkName"));
        studyService.duplicateStudyAsync(BasicStudyInfos.builder().id(newStudyUuid).build(), studyEntity.getId(), USER_ID);

        List<BasicRootNetworkInfos> newStudyRootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(newStudyUuid);

        // ASSERT duplications are correct
        // get RootNetworkNodeInfoEntity of duplicate nodes
        UUID firstNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(newStudyUuid, NODE_1_NAME).getFirst().getId();
        UUID secondNodeDuplicateUuid = networkModificationNodeInfoRepository.findAllByNodeStudyIdAndName(newStudyUuid, NODE_2_NAME).getFirst().getId();

        // get RootNetworkNodeInfoEntity of duplicate node
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeDuplicateUuid, newStudyRootNetworkBasicInfos.get(0).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        RootNetworkNodeInfoEntity duplicateRootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNodeDuplicateUuid, newStudyRootNetworkBasicInfos.get(1).rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));

        // assert those values are actually defined from ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity1.getModificationsUuidsToExclude());
        assertEquals(MODIFICATIONS_TO_EXCLUDE_RN_2.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()), duplicateRootNetworkNodeInfoEntity2.getModificationsUuidsToExclude());
    }

    @Test
    void testDeleteExcludedModification() {
        // create study with one root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        UUID studyUuid = studyEntity.getId();
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyUuid);

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        UUID firstNodeUuid = firstNode.getId();

        // add some modifications to exclude
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork0NodeInfo1Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        // if modification deletion fails, they should not be removed from excluded ones
        Mockito.doThrow(new RuntimeException()).when(networkModificationService).deleteModifications(any(), any());
        List<UUID> modificationsToRemove = List.of(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().findFirst().orElseThrow());
        assertThrows(RuntimeException.class, () -> studyService.deleteNetworkModifications(studyUuid, firstNodeUuid, modificationsToRemove, USER_ID));

        Set<UUID> excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")).getModificationsUuidsToExclude();
        Set<UUID> expectedExcludedModifications = MODIFICATIONS_TO_EXCLUDE_RN_1;
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);

        // if it returned OK, then they should be removed
        Mockito.doNothing().when(networkModificationService).deleteModifications(any(), any());
        studyService.deleteNetworkModifications(studyUuid, firstNodeUuid, modificationsToRemove, USER_ID);

        excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")).getModificationsUuidsToExclude();
        expectedExcludedModifications = MODIFICATIONS_TO_EXCLUDE_RN_1.stream().filter(uuid -> uuid != modificationsToRemove.getFirst()).collect(Collectors.toSet());
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);
    }

    @Test
    void testMoveExcludedModification() {
        // create study with one root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        UUID studyUuid = studyEntity.getId();
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyUuid);

        // create root node and a network modification node - it will create a RootNetworkNodeInfoEntity
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        UUID firstNodeUuid = firstNode.getId();
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);
        UUID secondNodeUuid = secondNode.getId();

        // add some modifications to exclude to node 1
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork0NodeInfo1Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        // try to execute modification move - if modification move fails, they should not be moved from a node to another in excludedModifications
        Mockito.doThrow(new RuntimeException()).when(networkModificationService).moveModifications(any(), any(), any(), any(), eq(true));
        List<UUID> modificationsToMove = List.of(MODIFICATIONS_TO_EXCLUDE_RN_1.stream().findFirst().orElseThrow());
        assertThrows(RuntimeException.class, () -> studyService.moveNetworkModifications(studyUuid, firstNodeUuid, firstNodeUuid, modificationsToMove, null, false, USER_ID));

        // assert origin node still have all excluded modifications
        Set<UUID> excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")).getModificationsUuidsToExclude();
        Set<UUID> expectedExcludedModifications = MODIFICATIONS_TO_EXCLUDE_RN_1;
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);

        // assert target node still have no excluded modifications
        excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")).getModificationsUuidsToExclude();
        expectedExcludedModifications = Collections.emptySet();
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);

        // if it returned OK, then they should be moved from one node to another's excluded modifications
        Mockito.doReturn(new NetworkModificationsResult(modificationsToMove, List.of(Optional.of(NetworkModificationResult.builder()
            .applicationStatus(NetworkModificationResult.ApplicationStatus.ALL_OK)
            .lastGroupApplicationStatus(NetworkModificationResult.ApplicationStatus.ALL_OK)
            .networkImpacts(List.of())
            .build())))).when(networkModificationService).moveModifications(any(), any(), any(), any(), eq(true));
        studyService.moveNetworkModifications(studyUuid, secondNodeUuid, firstNodeUuid, modificationsToMove, null, true, USER_ID);

        // assert origin node still have all excluded modifications, except the moved one
        excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(firstNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")).getModificationsUuidsToExclude();
        expectedExcludedModifications = MODIFICATIONS_TO_EXCLUDE_RN_1.stream().filter(uuid -> uuid != modificationsToMove.getFirst()).collect(Collectors.toSet());
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);

        // assert target node now have the moved modification as excluded one
        excludedModifications = rootNetworkNodeInfoRepository.findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(secondNodeUuid, rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")).getModificationsUuidsToExclude();
        expectedExcludedModifications = new HashSet<>(modificationsToMove);
        assertThat(expectedExcludedModifications).usingRecursiveComparison().isEqualTo(excludedModifications);
    }

    @Test
    void testBuildNodeWithExcludedModifications() {
        // create study with one root networks
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());

        // create root node and 2 network modification node - it will create 2 RootNetworkNodeInfoEntity
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, firstNode.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);

        // add some modifications to exclude
        RootNetworkNodeInfoEntity rootNetwork0NodeInfo1Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(firstNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork0NodeInfo1Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo1Entity);

        RootNetworkNodeInfoEntity rootNetwork0NodeInfo2Entity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(secondNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid()).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        rootNetwork0NodeInfo2Entity.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rootNetwork0NodeInfo2Entity);

        Mockito.doReturn(Optional.of(3)).when(userAdminService).getUserMaxAllowedBuilds("userId");
        studyService.buildNode(studyEntity.getId(), secondNode.getId(), rootNetworkBasicInfos.getFirst().rootNetworkUuid(), "userId");

        ArgumentCaptor<BuildInfos> buildInfosCaptor = ArgumentCaptor.forClass(BuildInfos.class);
        Mockito.verify(networkModificationService, Mockito.times(1)).buildNode(eq(secondNode.getId()), eq(rootNetworkBasicInfos.getFirst().rootNetworkUuid()), buildInfosCaptor.capture(), eq(null));
        assertThat(buildInfosCaptor.getValue().getModificationUuidsToExclude().get(firstNode.getModificationGroupUuid())).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(MODIFICATIONS_TO_EXCLUDE_RN_1);
        assertThat(buildInfosCaptor.getValue().getModificationUuidsToExclude().get(secondNode.getModificationGroupUuid())).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(MODIFICATIONS_TO_EXCLUDE_RN_2);
    }

    private void createDummyRootNetwork(StudyEntity studyEntity, String name, String tag) {
        RootNetworkEntity rootNetworkEntity = RootNetworkInfos.builder()
            .id(UUID.randomUUID())
            .name(name)
            .tag(tag)
            .caseInfos(new CaseInfos(UUID.randomUUID(), UUID.randomUUID(), "caseName", "caseFormat"))
            .networkInfos(new NetworkInfos(UUID.randomUUID(), UUID.randomUUID().toString()))
            .reportUuid(UUID.randomUUID())
            .build().toEntity();
        studyEntity.addRootNetwork(rootNetworkEntity);
    }

    private void createDummyRootNetwork(StudyEntity studyEntity, String name) {
        createDummyRootNetwork(studyEntity, name, UUID.randomUUID().toString().substring(0, 4));
    }
}
