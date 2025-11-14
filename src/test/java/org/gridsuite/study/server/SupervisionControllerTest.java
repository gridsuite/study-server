/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import org.elasticsearch.client.RestClient;
import org.gridsuite.study.server.dto.BasicRootNetworkInfos;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.dto.RootNetworkIndexationStatus;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.gridsuite.study.server.dto.supervision.SupervisionStudyInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */

@AutoConfigureMockMvc
@SpringBootTest(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})
class SupervisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String TEST_FILE = "testCase.xiidm";
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID STUDY_UUID = UUID.randomUUID();

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @MockitoBean
    private NetworkConversionService networkConversionService;

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper mapper;

    @MockitoSpyBean
    private RootNetworkService rootNetworkService;

    @MockitoSpyBean
    private StudyService studyService;

    @Autowired
    private RestClient restClient;

    @Autowired
    private TestUtils studyTestUtils;

    @MockitoBean
    private NetworkService networkService;

    @Autowired
    private StudyInfosService studyInfosService;

    private static EquipmentInfos toEquipmentInfos(Identifiable<?> i) {
        return EquipmentInfos.builder()
            .networkUuid(SupervisionControllerTest.NETWORK_UUID)
            .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
            .id(i.getId())
            .name(i.getNameOrId())
            .type(i.getType().name())
            .voltageLevels(Set.of(VoltageLevelInfos.builder().id("vlid").name("vlname").build()))
            .build();
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, String caseName) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, caseName, "", UUID.randomUUID());
        studyEntity.setId(STUDY_UUID);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    @BeforeEach
    void setup() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getIdentifiables().forEach(idable -> equipmentInfosService.addEquipmentInfos(toEquipmentInfos(idable)));

        when(networkConversionService.checkStudyIndexationStatus(NETWORK_UUID)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    private StudyEntity initStudy() throws Exception {
        StudyEntity study = insertDummyStudy(NETWORK_UUID, CASE_UUID, "");
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(STUDY_UUID);

        when(rootNetworkService.getNetworkUuid(rootNetworkUuid)).thenReturn(NETWORK_UUID);
        assertIndexationStatus(STUDY_UUID, RootNetworkIndexationStatus.INDEXED.name());
        return study;
    }

    private void assertIndexationCount(long expectedEquipmentsIndexationCount, long expectedTombstonedEquipmentsIndexationCount) throws Exception {
        MvcResult mvcResult;
        // Test get indexed equipments and tombstoned equipments counts
        mvcResult = mockMvc.perform(get("/v1/supervision/equipments/indexation-count"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(expectedEquipmentsIndexationCount, Long.parseLong(mvcResult.getResponse().getContentAsString()));

        mvcResult = mockMvc.perform(get("/v1/supervision/tombstoned-equipments/indexation-count"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(expectedTombstonedEquipmentsIndexationCount, Long.parseLong(mvcResult.getResponse().getContentAsString()));
    }

    private void assertIndexationStatus(UUID studyUuid, String status) throws Exception {
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", studyUuid, firstRootNetworkUuid))
            .andExpectAll(status().isOk(),
                        content().string(status));
    }

    @Test
    void testESConfig() throws Exception {
        MvcResult mvcResult;

        // Test get elasticsearch host
        mvcResult = mockMvc.perform(get("/v1/supervision/elasticsearch-host"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(restClient.getNodes().get(0).getHost().getHostName() + ":" + restClient.getNodes().get(0).getHost().getPort(), mvcResult.getResponse().getContentAsString());

        // Test get indexed equipments index name
        mvcResult = mockMvc.perform(get("/v1/supervision/studies/index-name"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("studies", mvcResult.getResponse().getContentAsString());

        // Test get indexed equipments index name
        mvcResult = mockMvc.perform(get("/v1/supervision/equipments/index-name"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("equipments", mvcResult.getResponse().getContentAsString());

        // Test get indexed tombstoned equipments index name
        mvcResult = mockMvc.perform(get("/v1/supervision/tombstoned-equipments/index-name"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("tombstoned-equipments", mvcResult.getResponse().getContentAsString());
    }

    @Test
    void testInvalidateAllNodesBuilds() throws Exception {
        initStudy();

        Mockito.doNothing().when(networkService).deleteVariants(eq(NETWORK_UUID), any());

        mockMvc.perform(delete("/v1/supervision/studies/{studyUuid}/nodes/builds", STUDY_UUID))
            .andExpect(status().isOk());

        assertIndexationCount(74, 0);
        assertIndexationStatus(STUDY_UUID, RootNetworkIndexationStatus.INDEXED.name());
        Mockito.verify(networkService, Mockito.times(1)).deleteVariants(eq(NETWORK_UUID), any());
    }

    @Test
    void testOrphan() throws Exception {
        MvcResult mvcResult;

        // Test get orphan indexed equipments
        mvcResult = mockMvc.perform(get("/v1/supervision/orphan_indexed_network_uuids"))
            .andExpect(status().isOk())
            .andReturn();

        List<UUID> orphanIndexedNetworkUuids = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        // We never created a study for those indexed equipments in this network
        assertEquals(List.of(NETWORK_UUID), orphanIndexedNetworkUuids);

        // test delete orphan indexed equipments
        mockMvc.perform(delete("/v1/supervision/studies/{networkUuid}/indexed-equipments-by-network-uuid", NETWORK_UUID))
            .andExpect(status().isOk());
    }

    @Test
    void testRecreateIndices() throws Exception {
        // Fill studies, equipments and tombstoned_equipments indices
        initStudy();
        CreatedStudyBasicInfos studyInfos = CreatedStudyBasicInfos.builder().id(STUDY_UUID).userId("userId1").build();
        studyInfosService.add(studyInfos);
        equipmentInfosService.addTombstonedEquipmentInfos(TombstonedEquipmentInfos.builder().id("id1").build());

        assertIndexationCount(74, 1);
        MvcResult mvcResult = mockMvc.perform(get("/v1/supervision/studies/indexation-count"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(1, Integer.parseInt(mvcResult.getResponse().getContentAsString()));

        mockMvc.perform(post("/v1/supervision/studies/indices"))
                .andExpect(status().isOk())
                .andReturn();

        assertIndexationCount(0, 0);
        mvcResult = mockMvc.perform(get("/v1/supervision/studies/indexation-count"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, Integer.parseInt(mvcResult.getResponse().getContentAsString()));
        assertIndexationStatus(STUDY_UUID, RootNetworkIndexationStatus.NOT_INDEXED.name());
    }

    @Test
    void testReindexStudy() throws Exception {
        UUID studyToReindexUuid = UUID.randomUUID();
        BasicRootNetworkInfos network1 = new BasicRootNetworkInfos(UUID.randomUUID(), UUID.randomUUID(), "name1", "tag1", "description1", false);
        BasicRootNetworkInfos network2 = new BasicRootNetworkInfos(UUID.randomUUID(), UUID.randomUUID(), "name2", "tag2", "description1", false);
        doReturn(List.of(network1, network2))
                .when(studyService).getExistingBasicRootNetworkInfos(studyToReindexUuid);
        doNothing().when(studyService).reindexRootNetwork(studyToReindexUuid, network1.rootNetworkUuid());
        doNothing().when(studyService).reindexRootNetwork(studyToReindexUuid, network2.rootNetworkUuid());

        mockMvc.perform(post("/v1/supervision/studies/{studyUuid}/reindex", studyToReindexUuid))
                .andExpect(status().isOk());

        verify(studyService).getExistingBasicRootNetworkInfos(studyToReindexUuid);
        verify(studyService).reindexRootNetwork(studyToReindexUuid, network1.rootNetworkUuid());
        verify(studyService).reindexRootNetwork(studyToReindexUuid, network2.rootNetworkUuid());
    }

    @Test
    void testSupervisionStudiesBasicData() throws Exception {
        // test empty return
        mockMvc.perform(get("/v1/supervision/studies")).andExpectAll(status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //insert a study
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, "caseName", "caseFormat", UUID.randomUUID());
        studyEntity = studyRepository.save(studyEntity);

        MvcResult mvcResult = mockMvc.perform(get("/v1/supervision/studies", studyEntity.getId()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<SupervisionStudyInfos> infos = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertEquals(1, infos.size());
        // checks that the supervision extra data are here
        assertEquals(1, infos.get(0).getCaseUuids().size());
        assertEquals(1, infos.get(0).getRootNetworkInfos().size());
    }
}
