/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;

import org.gridsuite.study.server.dto.StudyIndexationStatus;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextConfigurationWithTestChannel
public class SupervisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String TEST_FILE = "testCase.xiidm";
    private static final UUID CASE_UUID = UUID.fromString("00000000-8cf0-11bd-b23e-10b96e4ef00d");
    private static final UUID NETWORK_UUID = UUID.fromString("db240961-a7b6-4b76-bfe8-19749026c1cb");
    private static final UUID STUDY_UUID = UUID.fromString("11888888-0000-0000-0000-111111111111");
    private static final UUID NODE_UUID = UUID.fromString("12345678-8cf0-11bd-b23e-10b96e4ef00d");

    @MockBean
    private NetworkService networkStoreService;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockBean
    private NetworkConversionService networkConversionService;

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Autowired
    private StudyRepository studyRepository;

    @Value("${spring.data.elasticsearch.embedded.port:}")
    private String expectedEsPort;

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
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, caseName, "");
        studyEntity.setId(STUDY_UUID);
        studyEntity.setIndexationStatus(StudyIndexationStatus.INDEXED);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    @Before
    public void setup() throws IOException {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getIdentifiables().forEach(idable -> equipmentInfosService.addEquipmentInfos(toEquipmentInfos(idable)));

        when(networkStoreService.getNetworkUuid(STUDY_UUID)).thenReturn(NETWORK_UUID);
        when(networkModificationTreeService.getVariantId(NODE_UUID)).thenReturn(VariantManagerConstants.INITIAL_VARIANT_ID);
        when(networkModificationTreeService.getStudyTree(STUDY_UUID)).thenReturn(RootNode.builder().studyId(STUDY_UUID).id(NODE_UUID).build());
        when(networkConversionService.checkStudyIndexationStatus(NETWORK_UUID)).thenReturn(true);
    }

    @After
    public void tearDown() {
        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        studyRepository.deleteAll();
    }

    private StudyEntity initStudy() throws Exception {
        StudyEntity study = insertDummyStudy(NETWORK_UUID, CASE_UUID, "");

        assertIndexationStatus(STUDY_UUID, "INDEXED");

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
        mockMvc.perform(get("/v1/studies/{studyUuid}/indexation/status", studyUuid))
            .andExpectAll(status().isOk(),
                        content().string(status));
    }

    @Test
    public void testESConfig() throws Exception {
        MvcResult mvcResult;

        // Test get elasticsearch host
        mvcResult = mockMvc.perform(get("/v1/supervision/elasticsearch-host"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("localhost:" + expectedEsPort, mvcResult.getResponse().getContentAsString());

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
    public void testDeleteIndexation() throws Exception {
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
        assertIndexationStatus(STUDY_UUID, "NOT_INDEXED");
    }

    @Test
    public void testInvalidateAllNodesBuilds() throws Exception {
        initStudy();

        mockMvc.perform(delete("/v1/supervision/studies/{studyUuid}/nodes/builds", STUDY_UUID))
            .andExpect(status().isOk());

        assertIndexationCount(74, 0);
        assertIndexationStatus(STUDY_UUID, "INDEXED");

    }
}
