/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.google.common.collect.Iterables;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.utils.MatcherEquipmentInfos;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id").equipmentName("name").equipmentType("LOAD").build();
        assertThat(equipmentInfosService.add(loadInfos), new MatcherEquipmentInfos<>(loadInfos));
        assertEquals(1, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.add(EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id1").equipmentName("name1").equipmentType("LOAD").build());
        equipmentInfosService.add(EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id2").equipmentName("name2").equipmentType("GENERATOR").build());
        assertEquals(2, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));
    }

    @Test
    public void searchEquipmentInfos() {
        EqualsVerifier.simple().forClass(EquipmentInfos.class).verify();

        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_g1").equipmentName("name_g1").equipmentType("GENERATOR").build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_l1").equipmentName("name_l1").equipmentType("LINE").build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_l2").equipmentName("name_l2").equipmentType("LINE").build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_other_line").equipmentName("name_other_line").equipmentType("LINE").build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_tw1").equipmentName("name_tw1").equipmentType("TWO_WINDINGS_TRANSFORMER").build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_tw2").equipmentName("name_tw2").equipmentType("TWO_WINDINGS_TRANSFORMER").build();

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
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_g1").equipmentName("name_g1").equipmentType("GENERATOR").build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_l1").equipmentName("name_l1").equipmentType("LINE").build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_l2").equipmentName("name_l2").equipmentType("LINE").build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_other_line").equipmentName("name_other_line").equipmentType("LINE").build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_tw1").equipmentName("name_tw1").equipmentType("TWO_WINDINGS_TRANSFORMER").build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).equipmentId("id_tw2").equipmentName("name_tw2").equipmentType("TWO_WINDINGS_TRANSFORMER").build();

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
}
