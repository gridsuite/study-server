/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.WithAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.Queries;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.stream.Stream;

import static org.gridsuite.study.server.elasticsearch.EquipmentInfosService.EQUIPMENT_TYPE_SCORES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@ExtendWith(SoftAssertionsExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfigurationWithTestChannel
class EquipmentInfosServiceTests implements WithAssertions {

    private static final String TEST_FILE = "testCase.xiidm";
    private static final String EQUIPMENT_TYPE_FIELD = "equipmentType.keyword";
    private static final String EQUIPMENT_ID_FIELD = "equipmentId.fullascii";
    private static final String EQUIPMENT_ID_RAW_FIELD = "equipmentId.raw";
    private static final String EQUIPMENT_NAME_RAW_FIELD = "equipmentName.raw";

    private static final String EQUIPMENT_NAME_FULLASCII_FIELD = "equipmentName.fullascii";
    private static final String EQUIPMENT_NAME_FIELD = "equipmentName";
    private static final String NETWORK_UUID_FIELD = "networkUuid.keyword";

    private static final String VARIANT_ID = "variant_1";

    private static final UUID NETWORK_UUID = UUID.fromString("db240961-a7b6-4b76-bfe8-19749026c1cb");
    private static final UUID NETWORK_UUID_2 = UUID.fromString("8c73b846-5dbe-4ac8-a9c9-8422fda261bb");

    private static final UUID NODE_UUID = UUID.fromString("12345678-8cf0-11bd-b23e-10b96e4ef00d");
    private static final UUID ROOTNETWORK_UUID = UUID.fromString("23456789-8cf0-11bd-b23e-10b96e4ef00d");

    @Autowired
    private EquipmentInfosService equipmentInfosService;

    @MockitoBean
    private NetworkService networkStoreService;

    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockitoBean
    private RootNetworkService rootNetworkService;

    @Autowired
    private StudyService studyService;

    @BeforeEach
    void setup() {
//        when(networkStoreService.getNetworkUuid(NETWORK_UUID)).thenReturn(NETWORK_UUID);
        when(networkModificationTreeService.getVariantId(NODE_UUID, ROOTNETWORK_UUID)).thenReturn(VariantManagerConstants.INITIAL_VARIANT_ID);
        when(rootNetworkService.getNetworkUuid(ROOTNETWORK_UUID)).thenReturn(NETWORK_UUID);
    }

    @AfterEach
    void tearDown() {
        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID);
        equipmentInfosService.deleteAllByNetworkUuid(NETWORK_UUID_2);
    }

    private static BoolQuery buildBoolQueryEquipmentType(String equipmentType) {
        return new BoolQuery.Builder().filter(Queries.termQuery(EQUIPMENT_TYPE_FIELD, equipmentType)._toQuery()).build();
    }

    @Test
    void testAddDeleteEquipmentInfos() {
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
    void testAddDeleteTombstonedEquipmentInfos() {
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
    void testDeleteAllEquipmentInfos() {
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
    void testGetOrphanEquipmentInfosNetworkUuids() {
        // index some equipment infos as orphan
        UUID orphanNetworkUuid = UUID.randomUUID();
        EquipmentInfos orphanLoadInfos = EquipmentInfos.builder().networkUuid(orphanNetworkUuid).id("id").name("name").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();
        UUID orphanNetworkUuid2 = UUID.randomUUID();
        EquipmentInfos orphanVlInfos = EquipmentInfos.builder().networkUuid(orphanNetworkUuid2).id("id").name("name").type("VOLTAGE_LEVEL").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();

        // index an equipment infos for the existing network
        EquipmentInfos loadInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id2").name("name2").type("LOAD").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl").name("vl").build())).build();

        equipmentInfosService.addEquipmentInfos(loadInfos);
        equipmentInfosService.addEquipmentInfos(orphanLoadInfos);
        equipmentInfosService.addEquipmentInfos(orphanVlInfos);

        // get the orphan network uuids
        List<UUID> orphanNetworkUuids = equipmentInfosService.getOrphanEquipmentInfosNetworkUuids(List.of(NETWORK_UUID));

        // check that the orphan network uuids are returned
        assertEquals(2, orphanNetworkUuids.size());
        assertTrue(orphanNetworkUuids.contains(orphanNetworkUuid));
        assertTrue(orphanNetworkUuids.contains(orphanNetworkUuid2));

        // delete the orphan equipment infos
        equipmentInfosService.deleteAllByNetworkUuid(orphanNetworkUuid);
        equipmentInfosService.deleteAllByNetworkUuid(orphanNetworkUuid2);

        // check that the orphan network uuids are not returned anymore
        orphanNetworkUuids = equipmentInfosService.getOrphanEquipmentInfosNetworkUuids(List.of(NETWORK_UUID));
        assertEquals(0, orphanNetworkUuids.size());
    }

    @Test
    void testCloneVariant() {
        equipmentInfosService.addEquipmentInfos(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id1").name("name1").type(IdentifiableType.LOAD.name()).variantId("variant1").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build());
        assertEquals(1, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());

        equipmentInfosService.deleteVariants(NETWORK_UUID, List.of("variant1", "variant2"));
        assertEquals(0, equipmentInfosService.findAllEquipmentInfos(NETWORK_UUID).size());
    }

    @Test
    void testDeleteVariants() {
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
    void searchEquipmentInfos() {
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl4").name("vl4").build())).build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl5").name("vl5").build())).build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl6").name("vl6").build())).build();
        EquipmentInfos configuredBus = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_bus").name("name_bus").type("CONFIGURED_BUS").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl7").name("vl7").build())).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos, configuredBus).forEach(equipmentInfosService::addEquipmentInfos);

        Set<EquipmentInfos> hits = new HashSet<>(equipmentInfosService.searchEquipments(buildBoolQueryEquipmentType("LOAD"), List.of()));
        assertEquals(0, hits.size());

        hits = new HashSet<>(equipmentInfosService.searchEquipments(buildBoolQueryEquipmentType("GENERATOR"), List.of()));
        assertEquals(1, hits.size());
        assertTrue(hits.contains(generatorInfos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(buildBoolQueryEquipmentType("LINE"), List.of()));
        assertEquals(3, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(otherLineInfos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(buildBoolQueryEquipmentType("TWO_WINDINGS_TRANSFORMER"), List.of()));

        assertEquals(2, hits.size());
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        hits = new HashSet<>(equipmentInfosService.searchEquipments(buildBoolQueryEquipmentType("CONFIGURED_BUS"), List.of()));

        assertEquals(1, hits.size());
        assertTrue(hits.contains(configuredBus));
    }

    @Test
    void searchEquipmentInfosWithPattern() {
        BoolQuery query;
        EquipmentInfos generatorInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl1").name("vl1").build())).build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl2").name("vl2").build())).build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl3").name("vl3").build())).build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl4").name("vl4").build())).build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl5").name("vl5").build())).build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl6").name("vl6").build())).build();
        EquipmentInfos configuredBus = EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("id_bus").name("name_bus").type("CONFIGURED_BUS").voltageLevels(Set.of(VoltageLevelInfos.builder().id("vl7").name("vl7").build())).build();

        Stream.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos, configuredBus).forEach(equipmentInfosService::addEquipmentInfos);
        query = new BoolQuery.Builder().must(Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "*")._toQuery()).build();
        assertEquals(7, equipmentInfosService.searchEquipments(query, List.of()).size());

        query = new BoolQuery.Builder().must(Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_l*")._toQuery()).build();
        Set<EquipmentInfos> hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        query = new BoolQuery.Builder().must(Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_tw*")._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        query = new BoolQuery.Builder().should(Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_l*")._toQuery(), Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_tw*")._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));

        query = new BoolQuery.Builder().should(Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_l*")._toQuery(), Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_tw*")._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        assertEquals(4, hits.size());
        assertEquals(4, hits.size());
        assertTrue(hits.contains(line1Infos));
        assertTrue(hits.contains(line2Infos));
        assertTrue(hits.contains(tw1Infos));
        assertTrue(hits.contains(tw2Infos));
        query = new BoolQuery.Builder().must(Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_l*")._toQuery(), Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "id_tw*")._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        assertEquals(0, hits.size());
        hits = new HashSet<>(equipmentInfosService.searchEquipments(new BoolQuery.Builder().must(Queries.wildcardQuery(EQUIPMENT_ID_FIELD, "*other*")._toQuery(), Queries.termQuery(EQUIPMENT_TYPE_FIELD, "LINE")._toQuery()).build(), List.of()));
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

    private static String createEquipmentName(String name) {
        return "*" + EquipmentInfosService.escapeLucene(name) + "*";
    }

    private void testNameFullAscii(String pat) {
        assertEquals(1, studyService.searchEquipments(NODE_UUID, ROOTNETWORK_UUID, pat, EquipmentInfosService.FieldSelector.NAME, null, false).size());
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
    void testSearchSpecialChars(final SoftAssertions softly) {
        BoolQuery query;
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getIdentifiables().forEach(idable -> equipmentInfosService.addEquipmentInfos(toEquipmentInfos(idable)));

        Set<EquipmentInfos> hits;

        Query networkQuery = Queries.termQuery(NETWORK_UUID_FIELD, NETWORK_UUID.toString())._toQuery();

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_NAME_FULLASCII_FIELD, createEquipmentName("___"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(1);

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_NAME_FULLASCII_FIELD, createEquipmentName("e E"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(1);

        query = new BoolQuery.Builder().must(networkQuery, Queries.queryStringQuery(EQUIPMENT_NAME_FIELD, "*e E*", null)._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(4);

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_NAME_FULLASCII_FIELD, createEquipmentName("e e"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(1);

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_NAME_RAW_FIELD, createEquipmentName("e e"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(0);

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_NAME_FULLASCII_FIELD, createEquipmentName(" sp"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(1);

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_NAME_FULLASCII_FIELD, createEquipmentName("PS "))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(1);

        testNameFullAsciis();

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_ID_FIELD, createEquipmentName("FFR1AA1  FFR2AA1  2"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(1);

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_ID_RAW_FIELD, createEquipmentName("fFR1AA1  FFR2AA1  2"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(0);

        query = new BoolQuery.Builder().must(networkQuery, Queries.wildcardQuery(EQUIPMENT_ID_FIELD, createEquipmentName("fFR1àÀ1  FFR2AA1  2"))._toQuery()).build();
        hits = new HashSet<>(equipmentInfosService.searchEquipments(query, List.of()));
        softly.assertThat(hits).as(query.toString()).hasSize(1);
    }

    @Test
    void cleanRemovedEquipmentsInSerachTest() {
        UUID equipmentUuid = UUID.randomUUID();
        EquipmentInfos equipmentInfos = EquipmentInfos.builder().id(equipmentUuid.toString()).name("test").networkUuid(NETWORK_UUID).type("LOAD").variantId(VARIANT_ID).build();
        TombstonedEquipmentInfos tombstonedEquipmentInfos = TombstonedEquipmentInfos.builder().id(equipmentUuid.toString()).networkUuid(NETWORK_UUID).variantId(VARIANT_ID).build();
        // following the creation of a hypothesis for equipment deletion
        equipmentInfosService.addTombstonedEquipmentInfos(tombstonedEquipmentInfos);
        // following the creation of a hypothesis for previously deleted equipment creation
        equipmentInfosService.addEquipmentInfos(equipmentInfos);
        List<EquipmentInfos> result = equipmentInfosService.searchEquipments(NETWORK_UUID, VARIANT_ID, "test", EquipmentInfosService.FieldSelector.NAME, "LOAD");
        assertEquals(equipmentInfos, result.get(0));
    }

    @Test
    void testSearchGlobalOrder() {
        EquipmentInfos generatorInfos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_g1").name("name_g1").type("GENERATOR").build();
        EquipmentInfos line1Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_l1").name("name_l1").type("LINE").build();
        EquipmentInfos line2Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_l2").name("name_l2").type("LINE").build();
        EquipmentInfos otherLineInfos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_other_line").name("name_other_line").type("LINE").build();
        EquipmentInfos tw1Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_tw1").name("name_tw1").type("TWO_WINDINGS_TRANSFORMER").build();
        EquipmentInfos tw2Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_tw2").name("name_tw2").type("TWO_WINDINGS_TRANSFORMER").build();
        EquipmentInfos substation2Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_substation_2").name("name_sub_1").type("SUBSTATION").build();
        EquipmentInfos substation1Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("id_substation_1").name("name_sub_2").type("SUBSTATION").build();
        EquipmentInfos alphabeticallySecondSubstation1Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("def_substation_id").name("name_sub_def").type("SUBSTATION").build();
        EquipmentInfos alphabeticallyFirstSubstation1Infos = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("abc_substation_id").name("name_sub_abc").type("SUBSTATION").build();
        EquipmentInfos filteredOutSubstation = EquipmentInfos.builder().variantId(VARIANT_ID).networkUuid(NETWORK_UUID).id("filtered").name("filtered_name ").type("SUBSTATION").build();

        String userinput = "id";

        List<EquipmentInfos> equipmentInfosList = List.of(generatorInfos, line1Infos, line2Infos, otherLineInfos, tw1Infos, tw2Infos, substation2Infos, substation1Infos, alphabeticallySecondSubstation1Infos, alphabeticallyFirstSubstation1Infos, filteredOutSubstation);
        equipmentInfosList.forEach(equipmentInfosService::addEquipmentInfos);

        List<EquipmentInfos> result = equipmentInfosService.searchEquipments(NETWORK_UUID, VARIANT_ID, userinput, EquipmentInfosService.FieldSelector.ID, null);

        /* assert results are
         * - filtered by userInput
         * - sorted by type, then by id starting by userInput, then by id alphabetically
         */
        assertThat(result).usingRecursiveComparison()
            .isEqualTo(equipmentInfosList.stream()
            .filter(e -> e.getId().contains(userinput))
            .sorted(
                Comparator.comparingInt((EquipmentInfos e) -> EQUIPMENT_TYPE_SCORES.get(e.getType())).reversed()
                    .thenComparing(e -> {
                        String full = e.getId().toLowerCase();
                        return full.startsWith(userinput) ? 0 : 1;
                    })
                    .thenComparing(e -> e.getId().toLowerCase())
            ).toList());
    }
}
