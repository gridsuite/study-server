/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.loadflow.LoadFlowParameters;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.ShortCircuitAnalysisService;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class})})
public class ShortCircuitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    private ObjectWriter objectWriter;

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    private static final String CASE_SHORT_CIRCUIT_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    public static final String SHORT_CIRCUIT_PARAMETERS_JSON = "{\"version\":\"1.0\",\"withLimitViolations\":true,\"withVoltageMap\":true,\"withFeederResult\":true,\"studyType\":\"TRANSIENT\",\"minVoltageDropProportionalThreshold\":20.0}";
    public static final String SHORT_CIRCUIT_PARAMETERS_JSON2 = "{\"version\":\"1.0\",\"studyType\":\"SUB_TRANSIENT\",\"minVoltageDropProportionalThreshold\":1.0}";

    @Before
    public void setup() throws IOException {
        objectWriter = mapper.writer().withDefaultPrettyPrinter();
    }

    @Test
    public void testShortCircuitAnalysisParameters() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), UUID.fromString(CASE_SHORT_CIRCUIT_UUID_STRING));
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get default ShortCircuitParameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_PARAMETERS_JSON));

        //setting short-circuit analysis Parameters
        //passing self made json because shortCircuitParameter serializer removes the parameters with default value
        String shortCircuitParameterBodyJson = "{\n" +
                "  \"version\" : \"1.0\",\n" +
                "  \"studyType\" : \"SUB_TRANSIENT\",\n" +
                "  \"minVoltageDropProportionalThreshold\" : 1.0,\n" +
                "  \"withVoltageMap\" : false,\n" +
                "  \"withFeederResult\" : false,\n" +
                "  \"withLimitViolations\" : false\n" +
                "}";
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortCircuitParameterBodyJson)).andExpect(
                status().isOk());

        //getting set values
        mockMvc.perform(get("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_PARAMETERS_JSON2));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .readSlackBus(true)
                .distributedSlack(true)
                .dcUseTransformerRatio(true)
                .hvdcAcEmulation(true)
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitAnalysisService.toEntity(ShortCircuitAnalysisService.getDefaultShortCircuitParameters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }
}
