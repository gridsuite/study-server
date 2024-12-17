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
import org.gridsuite.study.server.dto.StudyIndexationStatus;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.StudyTestUtils;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */

@AutoConfigureMockMvc
@SpringBootTest(classes = {StudyApplication.class})
class SupervisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String TEST_FILE = "testCase.xiidm";
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID STUDY_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockBean
    private NetworkConversionService networkConversionService;

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private RootNetworkService rootNetworkService;

    @Autowired
    private StudyService studyService;

    @Autowired
    private RestClient restClient;
    @Autowired
    private StudyTestUtils studyTestUtils;

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
        studyEntity.setIndexationStatus(StudyIndexationStatus.INDEXED);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    @BeforeEach
    void setup() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getIdentifiables().forEach(idable -> equipmentInfosService.addEquipmentInfos(toEquipmentInfos(idable)));

        //TODO: removing it still works, check if it's normal
//        when(networkModificationTreeService.getVariantId(NODE_UUID, any())).thenReturn(VariantManagerConstants.INITIAL_VARIANT_ID);
        when(networkModificationTreeService.getStudyTree(STUDY_UUID)).thenReturn(RootNode.builder().studyId(STUDY_UUID).id(NODE_UUID).build());
        when(networkConversionService.checkStudyIndexationStatus(NETWORK_UUID)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        studyRepository.deleteAll();
    }

    private StudyEntity initStudy() throws Exception {
        StudyEntity study = insertDummyStudy(NETWORK_UUID, CASE_UUID, "");
        UUID rootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(STUDY_UUID);

        when(rootNetworkService.getNetworkUuid(rootNetworkUuid)).thenReturn(NETWORK_UUID);
        assertIndexationStatus(STUDY_UUID, StudyIndexationStatus.INDEXED.name());
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
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);
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
    void testDeleteIndexation() throws Exception {
        initStudy();

        assertIndexationCount(74, 0);
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(NODE_UUID.toString()).id("id").name("name").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos);

        TombstonedEquipmentInfos tombLoadInfos = TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).variantId(NODE_UUID.toString()).id("idTomb").build();
        equipmentInfosService.addTombstonedEquipmentInfos(tombLoadInfos);

        assertIndexationCount(75, 1);

        MvcResult mvcResult;

        // Test indexed equipments deletion
        mvcResult = mockMvc.perform(delete("/v1/supervision/studies/{studyUuid}/equipments/indexation", STUDY_UUID))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(76, Long.parseLong(mvcResult.getResponse().getContentAsString()));
        assertIndexationCount(0, 0);
        assertIndexationStatus(STUDY_UUID, StudyIndexationStatus.NOT_INDEXED.name());
    }

    @Test
    void testInvalidateAllNodesBuilds() throws Exception {
        initStudy();

        mockMvc.perform(delete("/v1/supervision/studies/{studyUuid}/nodes/builds", STUDY_UUID))
            .andExpect(status().isOk());

        assertIndexationCount(74, 0);
        assertIndexationStatus(STUDY_UUID, StudyIndexationStatus.INDEXED.name());

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
}
