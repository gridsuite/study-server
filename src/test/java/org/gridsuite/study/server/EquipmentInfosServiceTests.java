/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.google.common.collect.Iterables;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.iidm.impl.NetworkImpl;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.EquipmentType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.data.elasticsearch.enabled=true"})
public class EquipmentInfosServiceTests {

    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @Before
    public void setup() {
        equipmentInfosService.deleteAll(NETWORK_UUID);
    }

    @Test
    public void testAddDeleteEquipmentInfos() {
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id").name("name").type("LOAD").voltageLevelsIds(Set.of("vl")).build();
        assertEquals(equipmentInfosService.add(loadInfos), loadInfos);
        assertEquals(1, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.add(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type("LOAD").voltageLevelsIds(Set.of("vl1")).build());
        equipmentInfosService.add(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").name("name2").type("GENERATOR").voltageLevelsIds(Set.of("vl2")).build());
        assertEquals(2, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));
    }

    @Test
    public void searchEquipmentInfos() {
        EqualsVerifier.simple().forClass(EquipmentInfos.class).verify();

        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").voltageLevelsIds(Set.of("vl1")).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").voltageLevelsIds(Set.of("vl2")).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").voltageLevelsIds(Set.of("vl3")).build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").voltageLevelsIds(Set.of("vl4")).build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").voltageLevelsIds(Set.of("vl5")).build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").voltageLevelsIds(Set.of("vl6")).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos).forEach(equipmentInfosService::add);

        Set<EquipmentInfos> hits = new HashSet<>(equipmentInfosService.search("equipmentType:(LOAD)"));
        assertEquals(0, hits.size());

        hits = new HashSet<>(equipmentInfosService.search("equipmentType:(GENERATOR)"));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(generatorInfos));

        hits = new HashSet<>(equipmentInfosService.search("equipmentType:(LINE)"));
        assertEquals(3, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(otherLineInfos));

        hits = new HashSet<>(equipmentInfosService.search("equipmentType:(TWO_WINDINGS_TRANSFORMER)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));
    }

    @Test
    public void searchEquipmentInfosWithPattern() {
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").voltageLevelsIds(Set.of("vl1")).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").voltageLevelsIds(Set.of("vl2")).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").voltageLevelsIds(Set.of("vl3")).build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").voltageLevelsIds(Set.of("vl4")).build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").voltageLevelsIds(Set.of("vl5")).build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").voltageLevelsIds(Set.of("vl6")).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos).forEach(equipmentInfosService::add);

        assertEquals(6, equipmentInfosService.search("*").size());

        Set<EquipmentInfos> hits = new HashSet<>(equipmentInfosService.search("equipmentId:(id_l*)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));

        hits = new HashSet<>(equipmentInfosService.search("equipmentId:(id_tw*)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.search("equipmentId:(id_l*) OR equipmentId:(id_tw*)"));
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.search("equipmentId:(id_l* OR id_tw*)"));
        assertEquals(4, hits.size());
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.search("equipmentId:(id_l* AND id_tw*)"));
        assertEquals(0, hits.size());

        hits = new HashSet<>(equipmentInfosService.search("equipmentType:(LINE) AND equipmentId:(*other*)"));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(otherLineInfos));
    }

    @Test
    public void testBadEquipmentType() {
        Identifiable<Network> network = new NetworkFactoryImpl().createNetwork("test", "test");
        String errorMessage = assertThrows(StudyException.class, () -> EquipmentType.getType(network)).getMessage();
        assertTrue(errorMessage.contains(String.format("The equipment type : %s is unknown", NetworkImpl.class.getSimpleName())));
    }
}
