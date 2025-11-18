/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
class SingleLineDiagramWiremockTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SingleLineDiagramService singleLineDiagramService;
    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    public ObjectMapper mapper;

    @MockitoBean
    private NetworkService networkService;
    @MockitoBean
    private LoadFlowService loadFlowService;
    @MockitoBean
    private RootNetworkService rootNetworkService;
    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;

    private WireMockServer wireMockServer;
    private WireMockUtils wireMockUtils;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);

        // Start the server.
        wireMockServer.start();

        singleLineDiagramService.setSingleLineDiagramServerBaseUri(wireMockServer.baseUrl());
        shortCircuitService.setShortCircuitServerBaseUri(wireMockServer.baseUrl());
    }

    @Test
    void generateSingleLineDiagramWithICCAndLimitViolations() throws Exception {
        testGenerateSingleLineDiagram(UUID.randomUUID(), UUID.randomUUID(), List.of(new CurrentLimitViolationInfos("equipmentId", null)), Map.of("testBusId", 10.0, "testBusId2", 15.0));
    }

    @Test
    void generateSingleLineDiagramWithICC() throws Exception {
        testGenerateSingleLineDiagram(null, UUID.randomUUID(), List.of(), Map.of("testBusId", 10.0, "testBusId2", 15.0));
    }

    @Test
    void generateSingleLineDiagramWithLimitViolations() throws Exception {
        testGenerateSingleLineDiagram(UUID.randomUUID(), null, List.of(new CurrentLimitViolationInfos("equipmentId", null)), Map.of());
    }

    @Test
    void generateSingleLineDiagramWithoutICC() throws Exception {
        testGenerateSingleLineDiagram(null, null, List.of(), Map.of());
    }

    private void testGenerateSingleLineDiagram(UUID loadflowResultUuid, UUID shortcircuitResultUuid, List<CurrentLimitViolationInfos> violations, Map<String, Double> busIdToIcc) throws Exception {
        UUID networkUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        UUID nodeUuid = UUID.randomUUID();
        String variantId = "variant1";
        String voltageLevelId = "voltageLevelId";

        SvgGenerationMetadata svgGenerationMetadata = new SvgGenerationMetadata(violations, busIdToIcc);
        mockServicesAroundSvgGeneration(nodeUuid, rootNetworkUuid, networkUuid, loadflowResultUuid, shortcircuitResultUuid, variantId, svgGenerationMetadata);

        String svgGenerationMetadataJson = mapper.writeValueAsString(svgGenerationMetadata);
        UUID generateSvgStubUuid = wireMockUtils.stubGenerateSvg(networkUuid, variantId, voltageLevelId, svgGenerationMetadataJson);
        UUID getIccValuesStubUuid = null;
        if (shortcircuitResultUuid != null) {
            getIccValuesStubUuid = wireMockUtils.stubGetVoltageLevelIccValues(shortcircuitResultUuid, voltageLevelId, mapper.writeValueAsString(busIdToIcc));
        }
        //get the voltage level diagram svg
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false&language=en",
            UUID.randomUUID(), rootNetworkUuid, nodeUuid, voltageLevelId)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_XML),
            content().string("generatedSvg"));

        wireMockUtils.verifyGenerateSvg(generateSvgStubUuid, networkUuid, variantId, voltageLevelId, svgGenerationMetadataJson);
        if (shortcircuitResultUuid != null) {
            wireMockUtils.verifyGetVoltageLevelIccValues(getIccValuesStubUuid, shortcircuitResultUuid, voltageLevelId);
        }
    }

    private void mockServicesAroundSvgGeneration(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid, UUID loadflowResultUuid, UUID shortcircuitResultUuid, String variantId, SvgGenerationMetadata svgGenerationMetadata) {
        doReturn(networkUuid).when(rootNetworkService).getNetworkUuid(rootNetworkUuid);
        doReturn(variantId).when(networkModificationTreeService).getVariantId(nodeUuid, rootNetworkUuid);
        doReturn(true).when(networkService).existVariant(networkUuid, variantId);
        doReturn(loadflowResultUuid).when(rootNetworkNodeInfoService).getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.LOAD_FLOW);
        doReturn(shortcircuitResultUuid).when(rootNetworkNodeInfoService).getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.SHORT_CIRCUIT);
        List<LimitViolationInfos> violations = svgGenerationMetadata.getCurrentLimitViolationInfos().stream().map(clv -> LimitViolationInfos.builder().subjectId(clv.equipmentId()).build()).toList();
        doReturn(violations).when(loadFlowService).getCurrentLimitViolations(loadflowResultUuid);
    }
}
