/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.rootnetworks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private static final String FIRST_ROOT_NETWORK_TAG = "dum";
    private static final String SECOND_ROOT_NETWORK_TAG = "PH1";

    private static final UUID MODIFICATION_1 = UUID.randomUUID();
    private static final UUID MODIFICATION_2 = UUID.randomUUID();
    private static final UUID MODIFICATION_3 = UUID.randomUUID();

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

    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private DirectoryService directoryService;
    @MockitoBean
    private CaseService caseService;
    @MockitoBean
    private NetworkService networkService;

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
        Mockito.doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(networkModificationService).verifyModifications(firstNode.getModificationGroupUuid(), Set.of(invalidModificationUuid));
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkUuid, firstNode.getId())
                .param("uuids", invalidModificationUuid.toString())
                .param("activated", Boolean.FALSE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isNotFound());
        Mockito.verify(networkModificationService, Mockito.never()).updateRootNetworkApplicability(Mockito.anyList(), Mockito.anyString(), Mockito.anyBoolean());

        Mockito.doNothing().when(networkModificationService).verifyModifications(firstNode.getModificationGroupUuid(), Set.of(MODIFICATION_1));

        // deactivating the modification on that root network is forwarded with its tag
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkUuid, firstNode.getId())
                .param("uuids", MODIFICATION_1.toString())
                .param("activated", Boolean.FALSE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk());
        Mockito.verify(networkModificationService, Mockito.times(1)).updateRootNetworkApplicability(List.of(MODIFICATION_1), FIRST_ROOT_NETWORK_TAG, false);

        // and so is activating it back
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-modifications", studyEntity.getId(), rootNetworkUuid, firstNode.getId())
                .param("uuids", MODIFICATION_1.toString())
                .param("activated", Boolean.TRUE.toString())
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk());
        Mockito.verify(networkModificationService, Mockito.times(1)).updateRootNetworkApplicability(List.of(MODIFICATION_1), FIRST_ROOT_NETWORK_TAG, true);
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
        Mockito.doReturn(new HashMap<>(Collections.singletonMap(sharedModificationUuid, null))).when(networkModificationService).getReferences(List.of(MODIFICATION_1));
        Mockito.doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
            .when(directoryService).checkPermission(List.of(sharedModificationUuid), null, USER_ID, PermissionType.WRITE, false);

        assertThrows(HttpClientErrorException.class, () -> studyService.updateNetworkModificationsActivationInRootNetwork(
            studyEntity.getId(), firstNode.getId(), rootNetworkUuid, Set.of(MODIFICATION_1), USER_ID, false));

        // the applicability of the shared modification is left untouched
        Mockito.verify(networkModificationService, Mockito.never()).updateRootNetworkApplicability(Mockito.anyList(), Mockito.anyString(), Mockito.anyBoolean());
    }

    @Test
    void testApplicabilitiesAreRelayedToTheFrontEnd() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "secondRootNetwork", SECOND_ROOT_NETWORK_TAG);
        studyRepository.save(studyEntity);

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // MODIFICATION_1 is not applicable on any root network of the study, MODIFICATION_2 only on the second one,
        // MODIFICATION_3 has an applicability for a tag which is not in the study: it is relayed as is, the front-end
        // only displays the tags of the root networks it knows
        Map<UUID, Map<String, Boolean>> applicabilities = Map.of(
                MODIFICATION_1, Map.of(FIRST_ROOT_NETWORK_TAG, false, SECOND_ROOT_NETWORK_TAG, false),
                MODIFICATION_2, Map.of(FIRST_ROOT_NETWORK_TAG, true, SECOND_ROOT_NETWORK_TAG, false),
                MODIFICATION_3, Map.of("ABS", false));
        Mockito.doReturn(applicabilities).when(networkModificationService).getRootNetworkApplicabilities(firstNode.getModificationGroupUuid());

        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications/applicability", studyEntity.getId(), firstNode.getId())
                .header(USER_ID, USER_ID))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(applicabilities, objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<Map<UUID, Map<String, Boolean>>>() { }));
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
        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(secondNode.getId(), rootNetworkUuid);
        assertEquals(FIRST_ROOT_NETWORK_TAG, buildInfos.getRootNetworkTag());
    }

    @Test
    void testApplicationContextCarriesRootNetworkTag() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        RootNetworkEntity rootNetworkEntity = studyEntity.getRootNetworks().getFirst();

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        assertEquals(FIRST_ROOT_NETWORK_TAG, rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), firstNode.getId(), NETWORK_UUID).rootNetworkTag());
    }

    @AfterEach
    void tearDown() {
        // these tests emit notifications on purpose: do not leave them behind for the next test class
        output.clear();
    }

    private void createDummyRootNetwork(StudyEntity studyEntity, String name, String tag) {
        RootNetworkEntity rootNetworkEntity = RootNetworkInfos.builder()
            .id(UUID.randomUUID())
            .name(name)
            .tag(tag)
            .caseInfos(new CaseInfos(UUID.randomUUID(), UUID.randomUUID(), CASE_NAME, CASE_FORMAT))
            .networkInfos(new NetworkInfos(UUID.randomUUID(), UUID.randomUUID().toString()))
            .reportUuid(UUID.randomUUID())
            .build().toEntity(objectMapper);
        studyEntity.addRootNetwork(rootNetworkEntity);
    }
}
