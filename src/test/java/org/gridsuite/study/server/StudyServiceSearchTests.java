/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.iidm.network.VariantManagerConstants;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.NoSuchIndexException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StudyServiceSearchTests {

    private static final UUID NETWORK_UUID = UUID.randomUUID();

    private static final UUID NODE_UUID = UUID.fromString("12345678-8cf0-11bd-b23e-10b96e4ef00d");

    private static final UUID ROOTNETWORK_UUID = UUID.fromString("2345679-8cf0-11bd-b23e-10b96e4ef00d");

    private static final UUID VARIANT_NODE_UUID = UUID.fromString("87654321-8cf0-11bd-b23e-10b96e4ef00d");

    private static final UUID VARIANT_NODE_UUID_2 = UUID.fromString("cb8271ee-de4c-40f6-8cf1-f19936243612");
    private static final String VARIANT_ID = "58974632-10f0-11bc-b244-11b96e4ef00d";
    private static final String VARIANT_ID_2 = "ce0c6e45-8abf-48c1-934e-757478935910";

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyService studyService;

    @MockBean
    private RootNetworkService rootNetworkService;

    @BeforeEach
    public void setup() {
        when(rootNetworkService.getNetworkUuid(ROOTNETWORK_UUID)).thenReturn(NETWORK_UUID);
        when(networkModificationTreeService.getVariantId(NODE_UUID, ROOTNETWORK_UUID)).thenReturn(VariantManagerConstants.INITIAL_VARIANT_ID);
        when(networkModificationTreeService.getVariantId(VARIANT_NODE_UUID, ROOTNETWORK_UUID)).thenReturn(VARIANT_ID);
        when(networkModificationTreeService.getVariantId(VARIANT_NODE_UUID_2, ROOTNETWORK_UUID)).thenReturn(VARIANT_ID_2);
        when(networkModificationTreeService.doGetLastParentNodeBuiltUuid(VARIANT_NODE_UUID, ROOTNETWORK_UUID)).thenReturn(VARIANT_NODE_UUID);
    }

    @AfterEach
    void tearDown() {
        try {
            equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        } catch (NoSuchIndexException ex) {
            // no need to worry that much
        }
    }

    @Test
    void searchEquipmentInfosMultiVariants() {
        // Initialize equipment infos initial variant
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        EquipmentInfos twInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl5").name("vl5").build())).build();
        EquipmentInfos load1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId1").name("name_load1").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos load2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId2").name("name_load2").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, twInfos, load1Infos, load2Infos).forEach(equipmentInfosService::addEquipmentInfos);

        // Search with node of initial variant
        Set<EquipmentInfos> hits = new HashSet<>();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "id_g1", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(generatorInfos));

        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "id_l", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));

        // Search with node of new variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_g1", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(generatorInfos));

        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_l", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));

        // Add new equipments infos for new variant
        EquipmentInfos newGeneratorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_new_g1").name("name_new_g1").variantId(VARIANT_ID).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos newLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_new_l1").name("name_new_l1").variantId(VARIANT_ID).type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();

        Stream.of(newGeneratorInfos, newLineInfos).forEach(equipmentInfosService::addEquipmentInfos);

        // Search new equipments with node of initial variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "id_new", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(0, hits.size());

        // Search all equipments with node of initial variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(generatorInfos));
        assertTrue(hits.contains(twInfos));

        // Search new equipments with node of new variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_new", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(newGeneratorInfos));
        assertTrue(hits.contains(newLineInfos));

        // Search all equipments with node of new variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(6, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(generatorInfos));
        assertTrue(hits.contains(twInfos));
        assertTrue(hits.contains(newGeneratorInfos));
        assertTrue(hits.contains(newLineInfos));

        // Tombstone the 2wt of the initial variant in the new variant
        TombstonedEquipmentInfos tombstonedGeneratorInfos = TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").variantId(VARIANT_ID).build();
        equipmentInfosService.addTombstonedEquipmentInfos(tombstonedGeneratorInfos);

        // Search 2wt with node of initial variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "id_tw1", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(twInfos));

        // Search specific load with node of initial variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, "LOAD", null, false));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(load1Infos));

        // Search lines with node of initial variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "id", EquipmentInfosService.FieldSelector.ID, "LINE", null, false));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));

        // Search lines with node of new variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id", EquipmentInfosService.FieldSelector.ID, "LINE", null, false));
        assertEquals(3, hits.size());
        assertTrue(hits.contains(newLineInfos));

        // Search specific load with the wrong type -> expect no result
        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "loadId2", EquipmentInfosService.FieldSelector.ID, "LINE", null, false));
        assertEquals(0, hits.size());

        // Search both loads
        hits.clear();
        hits.addAll(studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "loadId", EquipmentInfosService.FieldSelector.ID, "LOAD", null, false));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(load2Infos));
        assertTrue(hits.contains(load1Infos));

        // Search 2wt with node of new variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_tw1", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(0, hits.size());

        // Search all equipments with node of new variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, null, false));
        assertEquals(5, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(generatorInfos));
        assertTrue(hits.contains(newGeneratorInfos));
        assertTrue(hits.contains(newLineInfos));

        // Search all equipments with node of new variant
        hits.clear();
        hits.addAll(studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, null, true));
        assertEquals(5, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(generatorInfos));
        assertTrue(hits.contains(newGeneratorInfos));
        assertTrue(hits.contains(newLineInfos));
    }

    @Test
    void searchModifiedEquipment() {
        // Adding an equipment with type "LOAD" and a specific variant to the EquipmentInfosService.
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId1").name("name_load1").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos);

        // Searching for the equipment by ID and checking if the list size is 1, indicating successful retrieval.
        List<EquipmentInfos> list = studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, null, null, false);
        assertEquals(1, list.size());

        // Adding another version of the same equipment but with a different variant ID.
        EquipmentInfos loadInfos1 = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId1").name("name_load1").variantId(VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos1);

        // Searching for the equipment by ID to check if the correct version is retrieved.
        list = studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, null, null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(loadInfos1));

        // Adding a third version of the same equipment with a different variant ID.
        EquipmentInfos loadInfos2 = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId1").name("name_load1").variantId(VARIANT_ID_2).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos2);

        // Searching for the third version of the equipment and validating its presence in the results.
        list = studyService.searchEquipments(VARIANT_NODE_UUID_2, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, null, null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(loadInfos2));

        // Re-searching for the second version of the equipment to ensure it is still retrievable without third version.
        list = studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, null, null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(loadInfos1));
    }

    @Test
    void testSearchForModifiedEquipmentsFilteredByType() {
        // Adding LOAD and GENERATOR type equipment to the EquipmentInfosService with initial variant.
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId1").name("name_load1").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos);
        equipmentInfosService.addEquipmentInfos(generatorInfos);

        // Searching for LOAD equipment by ID and verifying it's correctly retrieved.
        List<EquipmentInfos> list = studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, "LOAD", null, false);
        assertEquals(1, list.size());

        // Adding new versions of LOAD and GENERATOR equipment with a different variant ID.
        EquipmentInfos loadInfos1 = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId1").name("name_load1").variantId(VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos generatorInfos1 = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").variantId(VARIANT_ID).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos1);
        equipmentInfosService.addEquipmentInfos(generatorInfos1);

        // Searching for the new version of LOAD equipment and verifying the correct variant is retrieved.
        list = studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, "LOAD", null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(loadInfos1));

        // Searching for the new version of GENERATOR equipment and verifying the correct variant is retrieved.
        list = studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_g1", EquipmentInfosService.FieldSelector.ID, "GENERATOR", null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(generatorInfos1));

        // Adding another set of LOAD and GENERATOR equipment with yet another variant ID.
        EquipmentInfos loadInfos2 = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("loadId1").name("name_load1").variantId(VARIANT_ID_2).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        EquipmentInfos generatorInfos2 = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").variantId(VARIANT_ID_2).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos2);
        equipmentInfosService.addEquipmentInfos(generatorInfos2);

        // Searching for the second set of equipment and verifying both LOAD and GENERATOR types are correctly retrieved.
        list = studyService.searchEquipments(VARIANT_NODE_UUID_2, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, "LOAD", null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(loadInfos2));
        list = studyService.searchEquipments(VARIANT_NODE_UUID_2, ROOTNETWORK_UUID, "id_g1", EquipmentInfosService.FieldSelector.ID, "GENERATOR", null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(generatorInfos2));

        // Re-verifying the retrieval of the first set of modified equipment for both LOAD and GENERATOR types without redandante.
        list = studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, "LOAD", null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(loadInfos1));
        list = studyService.searchEquipments(VARIANT_NODE_UUID, ROOTNETWORK_UUID, "id_g1", EquipmentInfosService.FieldSelector.ID, "GENERATOR", null, false);
        assertEquals(1, list.size());
        assertTrue(list.contains(generatorInfos1));
    }
}
