/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
class StudyControllerSearchTest {
    private static final String USER_ID_HEADER = "userId";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String VARIANT_1 = "variant_1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestUtils studyTestUtils;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private StudyRepository studyRepository;

    @MockitoBean
    private StudyInfosService studyInfosService;
    @MockitoSpyBean
    private EquipmentInfosService equipmentInfosService;

    @Test
    void testSearchStudyCreation() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        List<CreatedStudyBasicInfos> studiesInfos = List.of(
            CreatedStudyBasicInfos.builder().id(UUID.randomUUID()).userId("userId1").build(),
            CreatedStudyBasicInfos.builder().id(UUID.randomUUID()).userId("userId1").build()
        );

        when(studyInfosService.search("userId:%s".formatted("userId")))
            .then((Answer<List<CreatedStudyBasicInfos>>) invocation -> studiesInfos);

        mvcResult = mockMvc
            .perform(get("/v1/search?q={request}", "userId:%s".formatted("userId")).header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(createdStudyBasicInfosList, new MatcherJson<>(mapper, studiesInfos));
    }

    @Test
    void testSearchLines() throws Exception {
        UUID caseUuid = UUID.randomUUID();
        UUID networkUuid = UUID.randomUUID();
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "caseName", "caseFormat", UUID.randomUUID());
        studyEntity = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        UUID rootNodeId = getRootNodeUuid(studyUuid);

        Network network = initNetwork();
        List<EquipmentInfos> linesInfos = network.getLineStream().map(l -> toEquipmentInfos(l, networkUuid)).toList();
        MvcResult mvcResult;
        String resultAsString;

        doReturn(linesInfos).when(equipmentInfosService).searchEquipments(any(BoolQuery.class), any());

        mvcResult = mockMvc
            .perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=NAME",
                studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<EquipmentInfos> equipmentInfos = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mvcResult = mockMvc
            .perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=NAME",
                studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        equipmentInfos = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mvcResult = mockMvc
            .perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=ID",
                studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        equipmentInfos = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        var result = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=bogus",
                studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
            .andExpect(status().isInternalServerError())
            .andReturn();
        var problemDetail = mapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertNotNull(problemDetail.getDetail());
        assertTrue(problemDetail.getDetail().contains("Enum unknown entry 'bogus' should be among NAME, ID"));
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    private Network initNetwork() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        return network;
    }

    private static EquipmentInfos toEquipmentInfos(Line line, UUID networkUuid) {
        return EquipmentInfos.builder()
            .networkUuid(networkUuid)
            .id(line.getId())
            .name(line.getNameOrId())
            .type("LINE")
            .variantId("InitialState")
            .voltageLevels(Set.of(VoltageLevelInfos.builder().id(line.getTerminal1().getVoltageLevel().getId()).name(line.getTerminal1().getVoltageLevel().getNameOrId()).build()))
            .build();
    }
}
