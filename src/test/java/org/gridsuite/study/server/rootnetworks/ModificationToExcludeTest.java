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
    void testDuplicateNodeWithCommonAndUniqueRootNetworksBetweenStudies() {
        // 1️⃣ Créer étude source S1
        StudyEntity studyS1 = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyS1, "S1RN1", "COM"); // root network commun
        createDummyRootNetwork(studyS1, "S1RN2", "RNS"); // unique source
        studyRepository.save(studyS1);

        List<BasicRootNetworkInfos> s1RootInfos = studyService.getExistingBasicRootNetworkInfos(studyS1.getId());
// Supposons que les modifications du node soient A, B, C
        UUID modA = UUID.randomUUID();
        UUID modB = UUID.randomUUID();
        UUID modC = UUID.randomUUID();

// Pour le root network COM, on veut que B et C soient exclus
        Set<UUID> modificationsToExcludeCom = Set.of(modB, modC);

// Pour le root network RNS, toutes les modifications sont exclues
        Set<UUID> modificationsToExcludeRnq = Set.of(modA, modB, modC);

// Optionnel : mapping mock pour duplication
        Map<UUID, UUID> originToDuplicateModificationUuidMap = Map.of(
                modA, UUID.randomUUID(),
                modB, UUID.randomUUID(),
                modC, UUID.randomUUID()
        );
        // 2️⃣ Créer node source N1 avec modifications A, B, C
        NodeEntity rootNodeS1 = networkModificationTreeService.createRoot(studyS1);
        NetworkModificationNode nodeN1 = networkModificationTreeService.createNode(
                studyS1, rootNodeS1.getIdNode(), createModificationNodeInfo("N1"), InsertMode.AFTER, null);

        // Définir exclusions sur root networks source
        RootNetworkNodeInfoEntity rnComS1 = rootNetworkNodeInfoRepository
                .findByNodeInfoIdAndRootNetworkId(nodeN1.getId(), s1RootInfos.get(1).rootNetworkUuid())
                .orElseThrow();
        rnComS1.setModificationsUuidsToExclude(modificationsToExcludeCom); // ex: B, C
        rootNetworkNodeInfoRepository.save(rnComS1);

        RootNetworkNodeInfoEntity rnRnsS1 = rootNetworkNodeInfoRepository
                .findByNodeInfoIdAndRootNetworkId(nodeN1.getId(), s1RootInfos.get(2).rootNetworkUuid())
                .orElseThrow();
        rnRnsS1.setModificationsUuidsToExclude(modificationsToExcludeRnq); // ex: A, B, C
        rootNetworkNodeInfoRepository.save(rnRnsS1);

        // 3️⃣ Créer étude cible S2
        StudyEntity studyS2 = TestUtils.createDummyStudy(NETWORK_UUID2, CASE_UUID2, CASE_NAME2, CASE_FORMAT2, REPORT_UUID2);
        createDummyRootNetwork(studyS2, "S2RN1", "COM"); // commun
        createDummyRootNetwork(studyS2, "S2RN2", "RNT"); // unique cible
        studyRepository.save(studyS2);

        List<BasicRootNetworkInfos> s2RootInfos = studyService.getExistingBasicRootNetworkInfos(studyS2.getId());
        NodeEntity rootNodeS2 = networkModificationTreeService.createRoot(studyS2);

        // 4️⃣ Mock duplications des UUID
        Mockito.doReturn(originToDuplicateModificationUuidMap)
                .when(networkModificationService)
                .duplicateModificationsGroup(any(), any());

        // 5️⃣ Dupliquer node N1 de S1 vers S2
        UUID duplicateNodeUuid = networkModificationTreeService.duplicateStudyNode(
                nodeN1.getId(), rootNodeS2.getIdNode(), InsertMode.AFTER);

        // 6️⃣ Vérifier root network commun (COM) → exclusions copiées
        RootNetworkNodeInfoEntity rnComS2 = rootNetworkNodeInfoRepository
                .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(
                        duplicateNodeUuid, s2RootInfos.stream().filter(rn -> "COM".equals(rn.tag())).toList().getFirst().rootNetworkUuid())
                .orElseThrow();
        assertEquals(
                modificationsToExcludeCom.stream()
                        .map(originToDuplicateModificationUuidMap::get)
                        .collect(Collectors.toSet()),
                rnComS2.getModificationsUuidsToExclude(),
                "Exclusions doivent être copiées pour le root network commun"
        );

        // 7️⃣ Vérifier root network unique cible (RNT) → toutes les modifs actives
        RootNetworkNodeInfoEntity rnRntS2 = rootNetworkNodeInfoRepository
                .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(
                        duplicateNodeUuid, s2RootInfos.stream().filter(rn -> "RNT".equals(rn.tag())).toList().getFirst().rootNetworkUuid())
                .orElseThrow();
        assertTrue(rnRntS2.getModificationsUuidsToExclude().isEmpty(),
                "Root network unique cible (RNT) ne devrait pas avoir des modifications to exclude");
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
    void testDuplicateNodeBetweenStudies() {
        // 1️⃣ Créer étude source avec deux root networks
        StudyEntity sourceStudy = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(sourceStudy, "RN_COMMON");
        createDummyRootNetwork(sourceStudy, "RN_ONLY_SOURCE");
        studyRepository.save(sourceStudy);

        List<BasicRootNetworkInfos> sourceRootInfos = studyService.getExistingBasicRootNetworkInfos(sourceStudy.getId());

        // 2️⃣ Créer node source
        NodeEntity rootNode = networkModificationTreeService.createRoot(sourceStudy);
        NetworkModificationNode sourceNode = networkModificationTreeService.createNode(
                sourceStudy, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null
        );

        // définir exclusions sur source
        RootNetworkNodeInfoEntity rnCommonSource = rootNetworkNodeInfoRepository
                .findByNodeInfoIdAndRootNetworkId(sourceNode.getId(), sourceRootInfos.getFirst().rootNetworkUuid())
                .orElseThrow();
        rnCommonSource.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rnCommonSource);

        RootNetworkNodeInfoEntity rnOnlySource = rootNetworkNodeInfoRepository
                .findByNodeInfoIdAndRootNetworkId(sourceNode.getId(), sourceRootInfos.get(1).rootNetworkUuid())
                .orElseThrow();
        rnOnlySource.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_2);
        rootNetworkNodeInfoRepository.save(rnOnlySource);

        // 3️⃣ Créer étude cible avec un root network commun + un root network uniquement cible
        StudyEntity targetStudy = TestUtils.createDummyStudy(NETWORK_UUID2, CASE_UUID2, CASE_NAME2, CASE_FORMAT2, REPORT_UUID2);
        createDummyRootNetwork(targetStudy, "RN_COMMON");
        createDummyRootNetwork(targetStudy, "RN_ONLY_TARGET");
        studyRepository.save(targetStudy);

        List<BasicRootNetworkInfos> targetRootInfos = studyService.getExistingBasicRootNetworkInfos(targetStudy.getId());
        NodeEntity targetRootNode = networkModificationTreeService.createRoot(targetStudy);

        // 4️⃣ Mock duplications des UUID
        Mockito.doReturn(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP)
                .when(networkModificationService)
                .duplicateModificationsGroup(any(), any());

        // 5️⃣ Dupliquer le nœud source vers targetStudy
        UUID duplicateNodeUuid = networkModificationTreeService.duplicateStudyNode(
                sourceNode.getId(), targetRootNode.getIdNode(), InsertMode.AFTER
        );

        // 6️⃣ Vérifier root networks communs
        RootNetworkNodeInfoEntity rnCommonTarget = rootNetworkNodeInfoRepository
                .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(
                        duplicateNodeUuid, targetRootInfos.get(0).rootNetworkUuid()
                ).orElseThrow();

        assertEquals(
                MODIFICATIONS_TO_EXCLUDE_RN_1.stream().map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get).collect(Collectors.toSet()),
                rnCommonTarget.getModificationsUuidsToExclude()
        );

        // 7️⃣ Vérifier root networks uniquement dans cible → toutes actives
        RootNetworkNodeInfoEntity rnOnlyTarget = rootNetworkNodeInfoRepository
                .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(
                        duplicateNodeUuid, targetRootInfos.get(1).rootNetworkUuid()
                ).orElseThrow();

        assertTrue(rnOnlyTarget.getModificationsUuidsToExclude().isEmpty());
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
    void testDuplicateModificationBetweenStudiesWithCommonRootNetworkTag() {
        // -------- STUDY 1 --------
        StudyEntity study1 = TestUtils.createDummyStudy(
                NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID
        );

        createDummyRootNetwork(study1, "rnCommon", "COM");
        createDummyRootNetwork(study1, "rnOnly1", "O1");

        studyRepository.save(study1);

        List<BasicRootNetworkInfos> rnStudy1 =
                studyService.getExistingBasicRootNetworkInfos(study1.getId());

        NodeEntity root1 = networkModificationTreeService.createRoot(study1);
        NetworkModificationNode node1 =
                networkModificationTreeService.createNode(
                        study1, root1.getIdNode(),
                        createModificationNodeInfo(NODE_1_NAME),
                        InsertMode.AFTER, null
                );

        // Put exclusions ONLY on COMMON root network
        UUID commonRn1 = rnStudy1.stream()
                .filter(r -> "COM".equals(r.tag()))
                .findFirst().orElseThrow()
                .rootNetworkUuid();

        RootNetworkNodeInfoEntity rnInfo1 =
                rootNetworkNodeInfoRepository
                        .findByNodeInfoIdAndRootNetworkId(node1.getId(), commonRn1)
                        .orElseThrow();

        rnInfo1.setModificationsUuidsToExclude(MODIFICATIONS_TO_EXCLUDE_RN_1);
        rootNetworkNodeInfoRepository.save(rnInfo1);

        // -------- STUDY 2 --------

        StudyEntity study2 = TestUtils.createDummyStudy(
                NETWORK_UUID2, CASE_UUID2, CASE_NAME2, CASE_FORMAT2, REPORT_UUID2
        );

        createDummyRootNetwork(study2, "rnOther", "OTH");
        createDummyRootNetwork(study2, "rnCommon", "COM");

        studyRepository.save(study2);

        List<BasicRootNetworkInfos> rnStudy2 =
                studyService.getExistingBasicRootNetworkInfos(study2.getId());
        NodeEntity root2 = networkModificationTreeService.createRoot(study2);
        NetworkModificationNode node2 =
                networkModificationTreeService.createNode(
                        study2, root2.getIdNode(),
                        createModificationNodeInfo(NODE_2_NAME),
                        InsertMode.AFTER, null
                );

        // -------- MOCK DUPLICATION RESULT --------
        List<ModificationsToCopyInfos> modifications = List.of(
                ModificationsToCopyInfos.builder().uuid(MODIFICATION_TO_EXCLUDE_1).build(),
                ModificationsToCopyInfos.builder().uuid(MODIFICATION_TO_EXCLUDE_2).build()
        );

        Mockito.doReturn(
                        new NetworkModificationsResult(
                                modifications.stream()
                                        .map(m -> ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP.get(m.getUuid()))
                                        .toList(),
                                List.of()
                        )
                ).when(networkModificationService)
                .duplicateOrInsertModifications(any(), any(), any());
        // -------- EXECUTE --------
        studyService.duplicateOrInsertNetworkModifications(
                study2.getId(),
                node2.getId(),
                study1.getId(),
                node1.getId(),
                modifications,
                USER_ID,
                StudyConstants.ModificationsActionType.COPY
        );
        // -------- ASSERT COMMON ROOT NETWORK --------
        UUID commonRn2 = rnStudy2.stream()
                .filter(r -> "COM".equals(r.tag()))
                .findFirst().orElseThrow()
                .rootNetworkUuid();

        RootNetworkNodeInfoEntity resultCommon =
                rootNetworkNodeInfoRepository
                        .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(
                                node2.getId(), commonRn2
                        ).orElseThrow();

        Set<UUID> duplicatedUuids = modifications.stream()
                .map(ModificationsToCopyInfos::getUuid)
                .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get)
                .collect(Collectors.toSet());

        Set<UUID> expected = MODIFICATIONS_TO_EXCLUDE_RN_1.stream()
                .map(ORIGIN_TO_DUPLICATE_MODIFICATION_UUID_MAP::get)
                .filter(duplicatedUuids::contains)
                .collect(Collectors.toSet());

        assertEquals(expected, resultCommon.getModificationsUuidsToExclude());

        // -------- ASSERT NON-COMMON ROOT NETWORK --------

        UUID otherRn2 = rnStudy2.stream()
                .filter(r -> "OTH".equals(r.tag()))
                .findFirst().orElseThrow()
                .rootNetworkUuid();

        RootNetworkNodeInfoEntity resultOther =
                rootNetworkNodeInfoRepository
                        .findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(
                                node2.getId(), otherRn2
                        ).orElseThrow();

        assertTrue(resultOther.getModificationsUuidsToExclude().isEmpty());
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
        List<ModificationsToCopyInfos> modificationsToDuplicate = List.of(
                ModificationsToCopyInfos.builder().uuid(MODIFICATION_NEVER_EXCLUDED).build(),
                ModificationsToCopyInfos.builder().uuid(MODIFICATION_TO_EXCLUDE_1).build(),
                ModificationsToCopyInfos.builder().uuid(MODIFICATION_TO_EXCLUDE_2).build()
        );
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

        // try to execute modification move - if modification move fails, they should not be moved from a node to another in exludedModifications
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
