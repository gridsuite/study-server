/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.iidm.network.VariantManagerConstants;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.TombstonedEquipmentInfos;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.data.elasticsearch.enabled=true"})
public class StudyServiceSearchTests {

    private static final UUID STUDY_UUID = UUID.fromString("14526897-4b5d-11bd-b23e-17e46e4ef00d");

    private static final UUID NETWORK_UUID = UUID.randomUUID();

    private static final UUID NODE_UUID = UUID.fromString("12345678-8cf0-11bd-b23e-10b96e4ef00d");

    private static final UUID VARIANT_NODE_UUID = UUID.fromString("87654321-8cf0-11bd-b23e-10b96e4ef00d");

    private static final String VARIANT_ID = "58974632-10f0-11bc-b244-11b96e4ef00d";

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private NetworkService networkService;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyService studyService;

    @Before
    public void setup() {
        when(networkService.getNetworkUuid(STUDY_UUID)).thenReturn(Mono.just(NETWORK_UUID));
        when(networkModificationTreeService.getVariantId(NODE_UUID)).thenReturn(Mono.just(VariantManagerConstants.INITIAL_VARIANT_ID));
        when(networkModificationTreeService.getVariantId(VARIANT_NODE_UUID)).thenReturn(Mono.just(VARIANT_ID));
        when(networkModificationTreeService.doGetLastParentModelNodeBuilt(VARIANT_NODE_UUID)).thenReturn(VARIANT_NODE_UUID);
    }

    @After
    public void tearDown() {
        try {
            equipmentInfosService.deleteAll(NETWORK_UUID);
        } catch (NoSuchIndexException ex) {
            // no need to worry that much
        }
    }

    @Test
    public void searchEquipmentInfosMultiVariants() {
        EqualsVerifier.simple().forClass(TombstonedEquipmentInfos.class).verify();

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
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "id_g1", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(1, hits.size());
        assertTrue(hits.contains(generatorInfos));

        hits.clear();
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "id_l", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));

        // Search with node of new variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, VARIANT_NODE_UUID, "id_g1", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(1, hits.size());
        assertTrue(hits.contains(generatorInfos));

        hits.clear();
        studyService.searchEquipments(STUDY_UUID, VARIANT_NODE_UUID, "id_l", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));

        // Add new equipments infos for new variant
        EquipmentInfos newGeneratorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_new_g1").name("name_new_g1").variantId(VARIANT_ID).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos newLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_new_l1").name("name_new_l1").variantId(VARIANT_ID).type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();

        Stream.of(newGeneratorInfos, newLineInfos).forEach(equipmentInfosService::addEquipmentInfos);

        // Search new equipments with node of initial variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "id_new", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(0, hits.size());

        // Search all equipments with node of initial variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(generatorInfos));
        assertTrue(hits.contains(twInfos));

        // Search new equipments with node of new variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, VARIANT_NODE_UUID, "id_new", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(2, hits.size());
        assertTrue(hits.contains(newGeneratorInfos));
        assertTrue(hits.contains(newLineInfos));

        // Search all equipments with node of new variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, VARIANT_NODE_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
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
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "id_tw1", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(1, hits.size());
        assertTrue(hits.contains(twInfos));

        // Search specific load with node of initial variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "loadId1", EquipmentInfosService.FieldSelector.ID, "LOAD", false).subscribe(hits::add);
        assertEquals(1, hits.size());
        assertTrue(hits.contains(load1Infos));

        // Search specific load with the wrong type -> expect no result
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "loadId2", EquipmentInfosService.FieldSelector.ID, "LINE", false).subscribe(hits::add);
        assertEquals(0, hits.size());

        // Search both loads
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, NODE_UUID, "loadId", EquipmentInfosService.FieldSelector.ID, "LOAD", false).subscribe(hits::add);
        assertEquals(2, hits.size());
        assertTrue(hits.contains(load2Infos));
        assertTrue(hits.contains(load1Infos));

        // Search 2wt with node of new variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, VARIANT_NODE_UUID, "id_tw1", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(0, hits.size());

        // Search all equipments with node of new variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, VARIANT_NODE_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, false).subscribe(hits::add);
        assertEquals(5, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(generatorInfos));
        assertTrue(hits.contains(newGeneratorInfos));
        assertTrue(hits.contains(newLineInfos));

        // Search all equipments with node of new variant
        hits.clear();
        studyService.searchEquipments(STUDY_UUID, VARIANT_NODE_UUID, "id_", EquipmentInfosService.FieldSelector.ID, null, true).subscribe(hits::add);
        assertEquals(5, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(generatorInfos));
        assertTrue(hits.contains(newGeneratorInfos));
        assertTrue(hits.contains(newLineInfos));
    }
}
