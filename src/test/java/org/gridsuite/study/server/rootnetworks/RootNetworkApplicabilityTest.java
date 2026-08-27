/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.rootnetworks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.nodeactivity.NodeActivityRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class RootNetworkApplicabilityTest {
    private static final String USER_ID = "userId";

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final String CASE_NAME = "caseName";
    private static final String CASE_FORMAT = "caseFormat";
    private static final UUID REPORT_UUID = UUID.randomUUID();

    private static final String NODE_1_NAME = "node1";
    private static final String NODE_2_NAME = "node2";

    // tag of the root network created by TestUtils.createDummyStudy
    private static final String ROOT_NETWORK_TAG_1 = "dum";
    private static final String ROOT_NETWORK_TAG_2 = "sec";

    private static final UUID MODIFICATION_1 = UUID.randomUUID();

    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private StudyService studyService;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutputDestination output;
    @Autowired
    private NodeActivityRepository nodeActivityRepository;

    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private DirectoryService directoryService;
    @MockitoBean
    private CaseService caseService;
    @MockitoBean
    private NetworkService networkService;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private EquipmentInfosService equipmentInfosService;
    @MockitoBean
    private NetworkStoreService networkStoreService;
    @MockitoBean
    private UserAdminService userAdminService;

    @Test
    void testUpdateApplicability() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        List<BasicRootNetworkInfos> rootNetworkBasicInfos = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());
        UUID rootNetworkUuid = rootNetworkBasicInfos.getFirst().rootNetworkUuid();

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // an unknown modification returns 404 and does not update anything
        UUID invalidModificationUuid = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(networkModificationService).verifyModifications(firstNode.getModificationGroupUuid(), Set.of(invalidModificationUuid));
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkUuid, firstNode.getId())
                .param("uuids", invalidModificationUuid.toString())
                .param("applicable", Boolean.FALSE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isNotFound());
        verify(networkModificationService, never()).updateRootNetworkApplicability(anyList(), anyString(), anyBoolean());

        doNothing().when(networkModificationService).verifyModifications(firstNode.getModificationGroupUuid(), Set.of(MODIFICATION_1));

        // deactivating the modification on that root network is forwarded with its tag
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkUuid, firstNode.getId())
                .param("uuids", MODIFICATION_1.toString())
                .param("applicable", Boolean.FALSE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk());
        verify(networkModificationService, times(1)).updateRootNetworkApplicability(List.of(MODIFICATION_1), ROOT_NETWORK_TAG_1, false);

        // and so is activating it back
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkUuid, firstNode.getId())
                .param("uuids", MODIFICATION_1.toString())
                .param("applicable", Boolean.TRUE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk());
        verify(networkModificationService, times(1)).updateRootNetworkApplicability(List.of(MODIFICATION_1), ROOT_NETWORK_TAG_1, true);
    }

    @Test
    void testUpdateApplicabilityOfSharedModificationNeedsWritePermission() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        UUID rootNetworkUuid = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId()).getFirst().rootNetworkUuid();

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // the modification is a reference to a shared modification the user is not allowed to write on
        UUID sharedModificationUuid = UUID.randomUUID();
        doReturn(new HashMap<>(Collections.singletonMap(sharedModificationUuid, null))).when(networkModificationService).getReferences(List.of(MODIFICATION_1));
        doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
            .when(directoryService).checkPermission(List.of(sharedModificationUuid), null, USER_ID, PermissionType.WRITE, false);

        UUID studyUuid = studyEntity.getId();
        UUID nodeUuid = firstNode.getId();
        Set<UUID> modificationUuids = Set.of(MODIFICATION_1);
        assertThrows(HttpClientErrorException.class, () -> studyService.updateNetworkModificationsApplicabilityInRootNetwork(
            studyUuid, nodeUuid, rootNetworkUuid, modificationUuids, USER_ID, false));

        // the applicability of the shared modification is left untouched
        verify(networkModificationService, never()).updateRootNetworkApplicability(anyList(), anyString(), anyBoolean());
    }

    @Test
    void testBuildInfosCarryRootNetworkTag() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        UUID rootNetworkUuid = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId()).getFirst().rootNetworkUuid();

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, firstNode.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);

        // the network modification server resolves the applicability of each modification from that tag
        networkModificationTreeService.buildNode(studyEntity.getId(), secondNode.getId(), rootNetworkUuid, "userId", null);

        ArgumentCaptor<BuildInfos> buildInfosCaptor = ArgumentCaptor.captor();
        verify(networkModificationService).buildNode(any(UUID.class), any(UUID.class), buildInfosCaptor.capture(), isNull());
        assertEquals(ROOT_NETWORK_TAG_1, buildInfosCaptor.getValue().getRootNetworkTag());
    }

    @Test
    void testApplicationContextCarriesRootNetworkTag() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        RootNetworkEntity rootNetworkEntity = studyEntity.getRootNetworks().getFirst();

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        assertEquals(ROOT_NETWORK_TAG_1, rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), firstNode.getId(), NETWORK_UUID).rootNetworkTag());
    }

    @Test
    void testRenamingARootNetworkTagCarriesTheApplicabilitiesAlong() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        UUID rootNetworkUuid = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId()).getFirst().rootNetworkUuid();

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, firstNode.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);

        // an applicability is keyed by the tag, so renaming it must reach every modification group of the study
        updateRootNetwork(studyEntity.getId(), rootNetworkUuid, ROOT_NETWORK_TAG_2);

        ArgumentCaptor<List<UUID>> groupUuidsCaptor = ArgumentCaptor.captor();
        verify(networkModificationService, times(1)).renameRootNetworkTag(groupUuidsCaptor.capture(), eq(ROOT_NETWORK_TAG_1), eq(ROOT_NETWORK_TAG_2));
        assertEquals(Set.of(firstNode.getModificationGroupUuid(), secondNode.getModificationGroupUuid()), Set.copyOf(groupUuidsCaptor.getValue()));

        // an update carrying no tag at all leaves it as it was, so it is not a rename
        updateRootNetwork(studyEntity.getId(), rootNetworkUuid, null);
        verify(networkModificationService, times(1)).renameRootNetworkTag(any(), any(), any());

        // and neither is an update carrying the tag the root network already has
        updateRootNetwork(studyEntity.getId(), rootNetworkUuid, ROOT_NETWORK_TAG_2);
        verify(networkModificationService, times(1)).renameRootNetworkTag(any(), any(), any());
    }

    @Test
    void testDeletingARootNetworkDropsTheApplicabilitiesOfItsTag() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        UUID deletedRootNetworkUuid = UUID.randomUUID();
        studyEntity.addRootNetwork(RootNetworkInfos.builder()
            .id(deletedRootNetworkUuid)
            .name("secondRootNetworkName")
            .caseInfos(new CaseInfos(UUID.randomUUID(), UUID.randomUUID(), CASE_NAME, CASE_FORMAT))
            .networkInfos(new NetworkInfos(UUID.randomUUID(), UUID.randomUUID().toString()))
            .reportUuid(UUID.randomUUID())
            .tag(ROOT_NETWORK_TAG_2)
            .build().toEntity(objectMapper));
        studyRepository.save(studyEntity);

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        studyService.deleteRootNetworks(studyEntity.getId(), List.of(deletedRootNetworkUuid), USER_ID);

        verify(networkModificationService, times(1)).deleteRootNetworkTags(List.of(firstNode.getModificationGroupUuid()), List.of(ROOT_NETWORK_TAG_2));
    }

    private void updateRootNetwork(UUID studyUuid, UUID rootNetworkUuid, String tag) throws Exception {
        // an update runs asynchronously: its node activity outlives the request and would have the next one refused
        nodeActivityRepository.deleteAll();
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}", studyUuid, rootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RootNetworkInfos.builder().id(rootNetworkUuid).tag(tag).build()))
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        output.clear();
    }
}
