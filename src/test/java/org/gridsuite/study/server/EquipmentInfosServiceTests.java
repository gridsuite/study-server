/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.TombstonedEquipmentInfos;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.StudyService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashSet;
import java.util.List;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EquipmentInfosServiceTests {

    private static final String TEST_FILE = "testCase.xiidm";

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID_2 = UUID.randomUUID();

    private static final UUID NODE_UUID = UUID.fromString("12345678-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private NetworkService networkStoreService;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyService studyService;

    @Before
    public void setup() {
        when(networkStoreService.getNetworkUuid(NETWORK_UUID)).thenReturn(NETWORK_UUID);
        when(networkModificationTreeService.getVariantId(NODE_UUID)).thenReturn(VariantManagerConstants.INITIAL_VARIANT_ID);
    }

    @After
    public void tearDown() {
        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID_2);
    }

    @Test
    public void testAddDeleteEquipmentInfos() {
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id").name("name").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos);
        assertEquals(1, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
        EquipmentInfos loadInfosDB = equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).get(0);
        assertEquals(loadInfos, loadInfosDB);
        assertEquals(loadInfos.getNetworkUuid() + "_" + loadInfos.getVariantId() + "_" + loadInfos.getId(), loadInfosDB.getUniqueId());
        assertEquals(1, equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID));

        EquipmentInfos loadInfos2 = EquipmentInfos.builder().networkUuid(NETWORK_UUID_2).id("id").name("name").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos2);
        assertEquals(1, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID_2).size());
        EquipmentInfos loadInfosDB2 = equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID_2).get(0);
        assertEquals(loadInfos2, loadInfosDB2);
        assertEquals(loadInfos2.getNetworkUuid() + "_" + loadInfos2.getVariantId() + "_" + loadInfos2.getId(), loadInfosDB2.getUniqueId());
        assertEquals(1, equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID_2));

        assertEquals(2, equipmentInfosService.getEquipmentInfosCount());

        // Change name but uniqueIds are same
        loadInfos2 = EquipmentInfos.builder().networkUuid(NETWORK_UUID_2).id("id").name("newName").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();
        equipmentInfosService.addEquipmentInfos(loadInfos2);
        assertEquals(1, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID_2).size());
        loadInfosDB2 = equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID_2).get(0);
        assertEquals(loadInfos2, loadInfosDB2);
        assertEquals(loadInfos2.getNetworkUuid() + "_" + loadInfos2.getVariantId() + "_" + loadInfos2.getId(), loadInfosDB2.getUniqueId());
        assertEquals(1, equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID_2));

        assertEquals(2, equipmentInfosService.getEquipmentInfosCount());

        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
        assertEquals(0, equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID));

        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build());
        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").name("name2").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build());
        assertEquals(2, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
        assertEquals(2, equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID));

        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
        assertEquals(0, equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID));
    }

    @Test
    public void testAddDeleteTombstonedEquipmentInfos() {
        TombstonedEquipmentInfos loadInfos = TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id").build();
        assertEquals(equipmentInfosService.addTombstonedEquipmentInfos(loadInfos), loadInfos);
        assertEquals(1, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());
        assertEquals(1, equipmentInfosService.getTombstonedEquipmentInfosCount(NETWORK_UUID));

        TombstonedEquipmentInfos loadInfos2 = TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID_2).id("id").build();
        assertEquals(equipmentInfosService.addTombstonedEquipmentInfos(loadInfos2), loadInfos2);
        assertEquals(1, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID_2).size());
        assertEquals(1, equipmentInfosService.getTombstonedEquipmentInfosCount(NETWORK_UUID_2));
        assertEquals(2, equipmentInfosService.getTombstonedEquipmentInfosCount());

        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        assertEquals(0, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());
        assertEquals(0, equipmentInfosService.getTombstonedEquipmentInfosCount(NETWORK_UUID));

        equipmentInfosService.addTombstonedEquipmentInfos(TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").build());
        equipmentInfosService.addTombstonedEquipmentInfos(TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").build());
        assertEquals(2, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());
        assertEquals(2, equipmentInfosService.getTombstonedEquipmentInfosCount(NETWORK_UUID));

        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        assertEquals(0, equipmentInfosService.findAllTombstonedEquipmentInfos(NETWORK_UUID).size());
        assertEquals(0, equipmentInfosService.getTombstonedEquipmentInfosCount(NETWORK_UUID));
    }

    @Test
    public void testDeleteAllEquipmentInfos() {
        TombstonedEquipmentInfos tsLoadInfos = TombstonedEquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id").build();
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id").name("name").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();

        assertEquals(equipmentInfosService.addTombstonedEquipmentInfos(tsLoadInfos), tsLoadInfos);
        assertEquals(equipmentInfosService.addEquipmentInfos(loadInfos), loadInfos);

        assertEquals(1, equipmentInfosService.getTombstonedEquipmentInfosCount());
        assertEquals(1, equipmentInfosService.getEquipmentInfosCount());

        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);

        assertEquals(0, equipmentInfosService.getTombstonedEquipmentInfosCount());
        assertEquals(0, equipmentInfosService.getEquipmentInfosCount());
    }

    @Test
    public void testCloneVariant() {
        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type(IdentifiableType.LOAD.name()).variantId("variant1").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build());
        assertEquals(1, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.deleteVariants(NETWORK_UUID, List.of("variant1", "variant2"));
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
    }

    @Test
    public void testDeleteVariants() {
        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type(IdentifiableType.LOAD.name()).variantId("variant1").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build());
        assertEquals(1, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").name("name2").type(IdentifiableType.GENERATOR.name()).variantId("variant2").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build());
        assertEquals(2, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id3").name("name3").type(IdentifiableType.BATTERY.name()).variantId("variant3").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build());
        assertEquals(3, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.deleteVariants(NETWORK_UUID, List.of("variant1", "variant3"));
        List<EquipmentInfos> equipments = equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID);
        assertEquals(1, equipments.size());
        assertEquals("variant2", equipments.get(0).getVariantId());

        equipmentInfosService.deleteVariants(NETWORK_UUID, List.of("variant2"));
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
    }

    @Test
    public void searchEquipmentInfos() {
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl4").name("vl4").build())).build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl5").name("vl5").build())).build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl6").name("vl6").build())).build();
        EquipmentInfos configuredBus = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_bus").name("name_bus").type("CONFIGURED_BUS").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl7").name("vl7").build())).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos, configuredBus).forEach(equipmentInfosService::addEquipmentInfos);

        Set<EquipmentInfos> hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentType:(LOAD)"));
        assertEquals(0, hits.size());

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentType:(GENERATOR)"));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(generatorInfos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentType:(LINE)"));
        assertEquals(3, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(otherLineInfos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentType:(TWO_WINDINGS_TRANSFORMER)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentType:(CONFIGURED_BUS)"));
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

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos, configuredBus).forEach(equipmentInfosService::addEquipmentInfos);

        assertEquals(7, equipmentInfosService.searchEquipments("*").size());

        Set<EquipmentInfos> hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentId:(id_l*)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentId:(id_tw*)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentId:(id_l*) OR equipmentId:(id_tw*)"));
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentId:(id_l* OR id_tw*)"));
        assertEquals(4, hits.size());
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentId:(id_l* AND id_tw*)"));
        assertEquals(0, hits.size());

        hits = new HashSet<>(equipmentInfosService.searchEquipments("equipmentType:(LINE) AND equipmentId:(*other*)"));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(otherLineInfos));
    }

    private static EquipmentInfos toEquipmentInfos(Identifiable<?> i) {
        return EquipmentInfos.builder()
            .networkUuid(EquipmentInfosServiceTests.NETWORK_UUID)
            .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
            .id(i.getId())
            .name(i.getNameOrId())
            .type(i.getType().name())
            .voltageLevels(Set.of(VoltageLevelInfos.builder().id("vlid").name("vlname").build()))
            .build();
    }

    @Rule
    public ErrorCollector pbsc = new ErrorCollector();

    private void testNameFullAscii(String pat) {
        assertEquals(1, studyService.searchEquipments(NETWORK_UUID, NODE_UUID, pat, EquipmentInfosService.FieldSelector.NAME, null, false).size());
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
        network.getIdentifiables().forEach(idable -> equipmentInfosService.addEquipmentInfos(toEquipmentInfos(idable)));

        Set<EquipmentInfos> hits;

        String prefix = "networkUuid:(" + NETWORK_UUID + ") AND ";

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName.raw:(*___*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName:(*e E*)"));
        pbsc.checkThat(hits.size(), is(4));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName.raw:(*e\\ E*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName.raw:(*e\\ e*)"));
        pbsc.checkThat(hits.size(), is(0));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName.fullascii:(*e\\ E*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName.fullascii:(\\ sp*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName.fullascii:(*PS\\ )"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentName.fullascii:(*e\\ e*)"));
        pbsc.checkThat(hits.size(), is(1));

        testNameFullAsciis();

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentId.raw:(*FFR1AA1  FFR2AA1  2*)"));
        pbsc.checkThat(hits.size(), is(1));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentId.raw:(*fFR1AA1  FFR2AA1  2*)"));
        pbsc.checkThat(hits.size(), is(0));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(prefix + "equipmentId.fullascii:(*fFR1àÀ1  FFR2AA1  2*)"));
        pbsc.checkThat(hits.size(), is(1));
    }
}
