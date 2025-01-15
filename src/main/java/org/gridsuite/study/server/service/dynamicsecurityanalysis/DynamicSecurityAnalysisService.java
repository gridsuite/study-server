/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsecurityanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisStatus;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSecurityAnalysisService {

    private final ObjectMapper objectMapper;

    private final DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient;

    public DynamicSecurityAnalysisService(ObjectMapper objectMapper,
                                          DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient) {
        this.objectMapper = objectMapper;
        this.dynamicSecurityAnalysisClient = dynamicSecurityAnalysisClient;
    }

    public String getParameters(UUID parametersUuid) {
        return dynamicSecurityAnalysisClient.getParameters(parametersUuid);
    }

    public UUID createParameters(String parameters) {
        return dynamicSecurityAnalysisClient.createParameters(parameters);
    }

    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        dynamicSecurityAnalysisClient.updateParameters(parametersUuid, parametersInfos);
    }

    public UUID duplicateParameters(UUID sourceParameterId) {
        return dynamicSecurityAnalysisClient.duplicateParameters(sourceParameterId);
    }

    public void deleteParameters(UUID parametersUuid) {
        dynamicSecurityAnalysisClient.deleteParameters(parametersUuid);
    }

    public UUID runDynamicSecurityAnalysis(String provider, UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid, String variantId, UUID reportUuid, UUID dynamicSimulationResultUuid, UUID parametersUuid, String userId) {

        // create receiver for getting back the notification in rabbitmq
        String receiver;

        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return dynamicSecurityAnalysisClient.run(provider, receiver, networkUuid, variantId, new ReportInfos(reportUuid, nodeUuid), dynamicSimulationResultUuid, parametersUuid, userId);
    }

    public DynamicSecurityAnalysisStatus getStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        return dynamicSecurityAnalysisClient.getStatus(resultUuid);
    }

    public void invalidateStatus(List<UUID> resultUuids) {

        if (resultUuids.isEmpty()) {
            return;
        }

        dynamicSecurityAnalysisClient.invalidateStatus(resultUuids);
    }

    public void deleteResult(UUID resultUuid) {
        dynamicSecurityAnalysisClient.deleteResult(resultUuid);
    }

    public void deleteResults() {
        try {
            dynamicSecurityAnalysisClient.deleteResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getResultsCount() {
        return dynamicSecurityAnalysisClient.getResultsCount();
    }

    public void assertDynamicSecurityAnalysisNotRunning(UUID resultUuid) {
        DynamicSecurityAnalysisStatus status = getStatus(resultUuid);
        if (DynamicSecurityAnalysisStatus.RUNNING == status) {
            throw new StudyException(DYNAMIC_SECURITY_ANALYSIS_RUNNING);
        }
    }

    public UUID getDynamicSecurityAnalysisParametersUuidOrElseCreateDefault(StudyEntity studyEntity) {
        if (studyEntity.getDynamicSecurityAnalysisParametersUuid() == null) {
            // not supposed to happen because we create it as the study creation
            studyEntity.setDynamicSecurityAnalysisParametersUuid(dynamicSecurityAnalysisClient.createDefaultParameters());
        }
        return studyEntity.getDynamicSecurityAnalysisParametersUuid();
    }
}
