/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.TombstonedEquipmentInfos;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.data.elasticsearch.enabled=false"})
public class EquipmentInfosServiceMockTests {

    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    private static final String VARIANT_ID = "variant1";

    private static final String NEW_VARIANT_ID = "variant2";

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Test
    public void testAddDeleteEquipmentInfos() {
        List<EquipmentInfos> equipmentsInfos = List.of(
                EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build(),
                EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build()
        );

        equipmentsInfos.forEach(equipmentInfosService::addEquipmentInfos);
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());

        List<TombstonedEquipmentInfos> tombstonedEquipmentsInfos = List.of(
                TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").variantId(VARIANT_ID).build(),
                TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").variantId(NEW_VARIANT_ID).build()
        );

        tombstonedEquipmentsInfos.forEach(equipmentInfosService::addTombstonedEquipmentInfos);
        assertEquals(0, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
        assertEquals(0, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());

        List<EquipmentInfos> equipments = equipmentInfosService.searchEquipments("equipmentId.fullascii: id1");
        assertEquals(Collections.emptyList(), equipments);

        List<TombstonedEquipmentInfos> tombstonedEquipments = equipmentInfosService.searchTombstonedEquipments("equipmentId.fullascii: id1");
        assertEquals(Collections.emptyList(), tombstonedEquipments);

        equipmentInfosService.addAllEquipmentInfos(equipmentsInfos);
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.addAllTombstonedEquipmentInfos(tombstonedEquipmentsInfos);
        assertEquals(0, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.cloneVariantModifications(NETWORK_UUID, VARIANT_ID, NEW_VARIANT_ID);
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
        assertEquals(0, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.deleteVariants(NETWORK_UUID, List.of(VARIANT_ID, NEW_VARIANT_ID));
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
        assertEquals(0, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());
    }
}
