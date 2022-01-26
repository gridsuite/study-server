/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.google.common.collect.Iterables;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.data.elasticsearch.enabled=true"})
public class EquipmentInfosServiceTests {

    private static final String TEST_FILE = "testCase.xiidm";

    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private StudyService studyService;

    @Before
    public void setup() {
        when(networkStoreService.getNetworkUuid(NETWORK_UUID)).thenReturn(Mono.just(NETWORK_UUID));

        try {
            equipmentInfosService.deleteAll(NETWORK_UUID);
        } catch (NoSuchIndexException ex) {
            // no need to worry that much
        }
    }

    @Test
    public void testAddDeleteEquipmentInfos() {
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id").name("name").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();
        assertEquals(equipmentInfosService.add(loadInfos), loadInfos);
        assertEquals(1, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.add(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build());
        equipmentInfosService.add(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").name("name2").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build());
        assertEquals(2, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));

        equipmentInfosService.deleteAll(NETWORK_UUID);
        assertEquals(0, Iterables.size(equipmentInfosService.findAll(NETWORK_UUID)));
    }

    @Test
    public void searchEquipmentInfos() {
        EqualsVerifier.simple().forClass(EquipmentInfos.class).verify();
        EqualsVerifier.simple().forClass(VoltageLevelInfos.class).verify();

        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl4").name("vl4").build())).build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl5").name("vl5").build())).build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl6").name("vl6").build())).build();
        EquipmentInfos configuredBus = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_bus").name("name_bus").type("CONFIGURED_BUS").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl7").name("vl7").build())).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos, configuredBus).forEach(equipmentInfosService::add);

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

        hits = new HashSet<>(equipmentInfosService.search("equipmentType:(CONFIGURED_BUS)"));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(configuredBus));
    }

    @Test
    public void searchEquipmentInfosWithPattern() {
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl4").name("vl4").build())).build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl5").name("vl5").build())).build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl6").name("vl6").build())).build();
        EquipmentInfos configuredBus = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_bus").name("name_bus").type("CONFIGURED_BUS").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl7").name("vl7").build())).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos, configuredBus).forEach(equipmentInfosService::add);

        assertEquals(7, equipmentInfosService.search("*").size());

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

    private static EquipmentInfos toEquipmentInfos(Identifiable<?> i) {
        return EquipmentInfos.builder()
            .networkUuid(EquipmentInfosServiceTests.NETWORK_UUID)
            .id(i.getId())
            .name(i.getNameOrId())
            .type(i.getType().name())
            .voltageLevels(Set.of(VoltageLevelInfos.builder().id("vlid").name("vlname").build()))
            .build();
    }

    @Rule
    public ErrorCollector pbsc = new ErrorCollector();

    private void testNameFullAscii(String pat) {
        Set<EquipmentInfos> hits = new HashSet<>();

        studyService.searchEquipments(NETWORK_UUID, pat, EquipmentInfosService.FieldSelector.NAME).subscribe(hits::add);
        pbsc.checkThat(hits.size(), is(1));
    }

    private void testNameFullAsciis() {
        testNameFullAscii("s+S");
        testNameFullAscii("s+s");
        testNameFullAscii("h-h");
        testNameFullAscii("t.t");
        testNameFullAscii("h/h");
        testNameFullAscii("l\\l");
        testNameFullAscii("p&p");
        testNameFullAscii("n(n");
        testNameFullAscii("n)n");
        testNameFullAscii("k[k");
        testNameFullAscii("k]k");
        testNameFullAscii("e{e");
        testNameFullAscii("e}e");
        testNameFullAscii("t<t");
        testNameFullAscii("t>t");
        testNameFullAscii("s's");
        testNameFullAscii("e|e");
    }

    @Test
    public void testSearchSpecialChars() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getIdentifiables().forEach(idable -> equipmentInfosService.add(toEquipmentInfos(idable)));

        Set<EquipmentInfos> hits;

        String prefix = "networkUuid:(" + NETWORK_UUID + ") AND ";

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName.raw:(*___*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName:(*e E*)"));
        pbsc.checkThat(hits.size(), is(4));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName.raw:(*e\\ E*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName.raw:(*e\\ e*)"));
        pbsc.checkThat(hits.size(), is(0));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName.fullascii:(*e\\ E*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName.fullascii:(\\ sp*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName.fullascii:(*PS\\ )"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentName.fullascii:(*e\\ e*)"));
        pbsc.checkThat(hits.size(), is(1));

        testNameFullAsciis();

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentId.raw:(*FFR1AA1  FFR2AA1  2*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentId.raw:(*fFR1AA1  FFR2AA1  2*)"));
        pbsc.checkThat(hits.size(), is(0));

        hits = new HashSet<>(equipmentInfosService.search(prefix + "equipmentId.fullascii:(*fFR1àÀ1  FFR2AA1  2*)"));
        pbsc.checkThat(hits.size(), is(1));
    }
}
