/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.dynamicsecurityanalysis;

import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisStatus;
import org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_SECURITY_ANALYSIS_RUNNING;
import static org.gridsuite.study.server.utils.TestUtils.assertStudyException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class DynamicSecurityAnalysisServiceTest {
    private static final String VARIANT_1_ID = "variant_1";
    private static final String PARAMETERS_JSON = "parametersJson";

    // converged node
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID ROOTNETWORK_UUID = UUID.randomUUID();
    private static final UUID DYNAMIC_SIMULATION_RESULT_UUID = UUID.randomUUID();
    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID DUPLICATED_PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID REPORT_UUID = UUID.randomUUID();

    // running node
    private static final UUID RESULT_UUID_RUNNING = UUID.randomUUID();

    @MockBean
    DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient;
    @Autowired
    private DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    @Test
    void testGetParameters() {
        given(dynamicSecurityAnalysisClient.getParameters(PARAMETERS_UUID)).willReturn(PARAMETERS_JSON);

        String parametersJson = dynamicSecurityAnalysisService.getParameters(PARAMETERS_UUID);

        assertThat(parametersJson).isEqualTo(PARAMETERS_JSON);
    }

    @Test
    void testCreateParameters() {
        given(dynamicSecurityAnalysisClient.createParameters(PARAMETERS_JSON)).willReturn(PARAMETERS_UUID);

        UUID parametersUuid = dynamicSecurityAnalysisService.createParameters(PARAMETERS_JSON);

        assertThat(parametersUuid).isEqualTo(PARAMETERS_UUID);
    }

    @Test
    void testCreateDefaultParameters() {
        given(dynamicSecurityAnalysisClient.createDefaultParameters()).willReturn(PARAMETERS_UUID);

        UUID parametersUuid = dynamicSecurityAnalysisService.createDefaultParameters();

        assertThat(parametersUuid).isEqualTo(PARAMETERS_UUID);
    }

    @Test
    void testUpdateParameters() {
        doNothing().when(dynamicSecurityAnalysisClient).updateParameters(PARAMETERS_UUID, PARAMETERS_JSON);

        dynamicSecurityAnalysisService.updateParameters(PARAMETERS_UUID, PARAMETERS_JSON);

        verify(dynamicSecurityAnalysisClient, times(1)).updateParameters(PARAMETERS_UUID, PARAMETERS_JSON);
    }

    @Test
    void testDuplicateParameters() {
        when(dynamicSecurityAnalysisClient.duplicateParameters(PARAMETERS_UUID)).thenReturn(DUPLICATED_PARAMETERS_UUID);

        UUID newParametersUuid = dynamicSecurityAnalysisService.duplicateParameters(PARAMETERS_UUID);

        assertThat(newParametersUuid).isEqualTo(DUPLICATED_PARAMETERS_UUID);
    }

    @Test
    void testDeleteParameters() {
        doNothing().when(dynamicSecurityAnalysisClient).deleteParameters(PARAMETERS_UUID);

        dynamicSecurityAnalysisService.deleteParameters(PARAMETERS_UUID);

        verify(dynamicSecurityAnalysisClient, times(1)).deleteParameters(PARAMETERS_UUID);
    }

    @Test
    void testRunDynamicSimulation() {
        // setup DynamicSecurityAnalysisClient mock
        given(dynamicSecurityAnalysisClient.run(eq(""), any(), eq(NETWORK_UUID), eq(VARIANT_1_ID),
                eq(new ReportInfos(REPORT_UUID, NODE_UUID)), eq(DYNAMIC_SIMULATION_RESULT_UUID), eq(PARAMETERS_UUID), any()))
                .willReturn(RESULT_UUID);

        // call method to be tested
        UUID resultUuid = dynamicSecurityAnalysisService.runDynamicSecurityAnalysis("", NODE_UUID, ROOTNETWORK_UUID,
                NETWORK_UUID, VARIANT_1_ID, REPORT_UUID, DYNAMIC_SIMULATION_RESULT_UUID, PARAMETERS_UUID, "testUserId");

        // check result
        assertThat(resultUuid).isEqualTo(RESULT_UUID);
    }

    @Test
    void testGetStatus() {
        // setup DynamicSecurityAnalysisClient mock
        given(dynamicSecurityAnalysisClient.getStatus(RESULT_UUID)).willReturn(DynamicSecurityAnalysisStatus.SUCCEED);

        // call method to be tested
        DynamicSecurityAnalysisStatus status = dynamicSecurityAnalysisService.getStatus(RESULT_UUID);

        // check result
        // status must be "SUCCEED"
        assertThat(status).isEqualTo(DynamicSecurityAnalysisStatus.SUCCEED);
    }

    @Test
    void testInvalidateStatus() {
        List<UUID> uuids = List.of(RESULT_UUID);
        doNothing().when(dynamicSecurityAnalysisClient).invalidateStatus(uuids);

        dynamicSecurityAnalysisService.invalidateStatus(uuids);

        verify(dynamicSecurityAnalysisClient, times(1)).invalidateStatus(uuids);
    }

    @Test
    void testDeleteResult() {
        doNothing().when(dynamicSecurityAnalysisClient).deleteResults(List.of(RESULT_UUID));

        dynamicSecurityAnalysisService.deleteResults(List.of(RESULT_UUID));

        verify(dynamicSecurityAnalysisClient, times(1)).deleteResults(List.of(RESULT_UUID));
    }

    @Test
    void testDeleteResults() {
        doNothing().when(dynamicSecurityAnalysisClient).deleteResults(null);

        dynamicSecurityAnalysisService.deleteAllResults();
        verify(dynamicSecurityAnalysisClient, times(1)).deleteResults(null);
    }

    @Test
    void testResultCount() {
        given(dynamicSecurityAnalysisClient.getResultsCount()).willReturn(10);

        Integer resultsCount = dynamicSecurityAnalysisService.getResultsCount();

        assertThat(resultsCount).isEqualTo(10);
    }

    @Test
    void testAssertDynamicSecurityAnalysisNotRunning() {
        when(dynamicSecurityAnalysisClient.getStatus(RESULT_UUID)).thenReturn(DynamicSecurityAnalysisStatus.SUCCEED);

        // test not running
        assertDoesNotThrow(() -> dynamicSecurityAnalysisService.assertDynamicSecurityAnalysisNotRunning(RESULT_UUID));

        verify(dynamicSecurityAnalysisClient, times(1)).getStatus(RESULT_UUID);
    }

    @Test
    void testAssertDynamicSecurityAnalysisRunning() {
        // setup for running node
        given(dynamicSecurityAnalysisClient.getStatus(RESULT_UUID_RUNNING)).willReturn(DynamicSecurityAnalysisStatus.RUNNING);

        // test running
        assertStudyException(() -> dynamicSecurityAnalysisService.assertDynamicSecurityAnalysisNotRunning(RESULT_UUID_RUNNING),
                DYNAMIC_SECURITY_ANALYSIS_RUNNING, null);
    }

    @Test
    void testUpdateProvider() {
        doNothing().when(dynamicSecurityAnalysisClient).updateProvider(PARAMETERS_UUID, "Dynawo");

        dynamicSecurityAnalysisService.updateProvider(PARAMETERS_UUID, "Dynawo");

        verify(dynamicSecurityAnalysisClient, times(1)).updateProvider(PARAMETERS_UUID, "Dynawo");
    }

    @Test
    void testGetDefaultProvider() {
        given(dynamicSecurityAnalysisClient.getDefaultProvider()).willReturn("Dynawo");

        String provider = dynamicSecurityAnalysisService.getDefaultProvider();

        assertThat(provider).isEqualTo("Dynawo");
    }

    @Test
    void testGetProvider() {
        given(dynamicSecurityAnalysisClient.getProvider(PARAMETERS_UUID)).willReturn("Dynawo");

        String provider = dynamicSecurityAnalysisService.getProvider(PARAMETERS_UUID);

        assertThat(provider).isEqualTo("Dynawo");
    }
}
