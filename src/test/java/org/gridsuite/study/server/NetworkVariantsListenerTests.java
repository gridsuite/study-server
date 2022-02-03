/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.iidm.network.VariantManagerConstants;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.data.elasticsearch.enabled=true"})
public class NetworkVariantsListenerTests {

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Before
    public void setup() {
        equipmentInfosService.deleteVariants(NETWORK_UUID, List.of(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID, VARIANT_ID_2));
    }

    @Test
    public void testVariantNotifications() {

        // In initial variant
        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("load0").name("load0").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build());
        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("gen0").name("gen0").variantId(VariantManagerConstants.INITIAL_VARIANT_ID).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build());

        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("load1").name("load1").variantId(VARIANT_ID).type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build());
        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("gen1").name("gen1").variantId(VARIANT_ID).type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build());

        NetworkVariantsListener listener = new NetworkVariantsListener(NETWORK_UUID, equipmentInfosService);

        listener.onVariantCreated(VARIANT_ID, VARIANT_ID_2);
        List<EquipmentInfos> equipmentInfos = equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID);
        assertEquals(6, equipmentInfos.size());
        assertEquals(2, equipmentInfos.stream().filter(eq -> eq.getVariantId().equals(VariantManagerConstants.INITIAL_VARIANT_ID)).count());
        assertEquals(2, equipmentInfos.stream().filter(eq -> eq.getVariantId().equals(VARIANT_ID)).count());
        assertEquals(2, equipmentInfos.stream().filter(eq -> eq.getVariantId().equals(VARIANT_ID_2)).count());

        listener.onVariantRemoved(VARIANT_ID);
        equipmentInfos = equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID);
        assertEquals(4, equipmentInfos.size());
        assertEquals(2, equipmentInfos.stream().filter(eq -> eq.getVariantId().equals(VariantManagerConstants.INITIAL_VARIANT_ID)).count());
        assertEquals(0, equipmentInfos.stream().filter(eq -> eq.getVariantId().equals(VARIANT_ID)).count());
        assertEquals(2, equipmentInfos.stream().filter(eq -> eq.getVariantId().equals(VARIANT_ID_2)).count());
    }
}
