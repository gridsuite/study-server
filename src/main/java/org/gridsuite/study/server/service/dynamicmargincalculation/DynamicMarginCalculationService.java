/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicmargincalculation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicmargincalculation.DynamicMarginCalculationStatus;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.client.dynamicmargincalculation.DynamicMarginCalculationClient;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.COMPUTATION_RUNNING;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicMarginCalculationService {

    private final ObjectMapper objectMapper;

    private final DynamicMarginCalculationClient dynamicMarginCalculationClient;

    public DynamicMarginCalculationService(ObjectMapper objectMapper,
                                           DynamicMarginCalculationClient dynamicMarginCalculationClient) {
        this.objectMapper = objectMapper;
        this.dynamicMarginCalculationClient = dynamicMarginCalculationClient;
    }

    public String getParameters(UUID parametersUuid) {
        return dynamicMarginCalculationClient.getParameters(parametersUuid);
    }

    public UUID createParameters(String parameters) {
        return dynamicMarginCalculationClient.createParameters(parameters);
    }

    public UUID createDefaultParameters() {
        return dynamicMarginCalculationClient.createDefaultParameters();
    }

    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        dynamicMarginCalculationClient.updateParameters(parametersUuid, parametersInfos);
    }

    public UUID duplicateParameters(UUID sourceParameterId) {
        return dynamicMarginCalculationClient.duplicateParameters(sourceParameterId);
    }

    public void deleteParameters(UUID parametersUuid) {
        dynamicMarginCalculationClient.deleteParameters(parametersUuid);
    }

    public UUID runDynamicMarginCalculation(String provider, UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid,
                                            String variantId, UUID reportUuid, UUID dynamicSecurityAnalysisParametersUuid, UUID parametersUuid, String dynamicSimulationParametersJson, String userId, boolean debug) {

        // create receiver for getting back the notification in rabbitmq
        String receiver;

        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return dynamicMarginCalculationClient.run(provider, receiver, networkUuid, variantId, new ReportInfos(reportUuid, nodeUuid), dynamicSecurityAnalysisParametersUuid, parametersUuid, dynamicSimulationParametersJson, userId, debug);
    }

    public DynamicMarginCalculationStatus getStatus(UUID resultUuid) {
        return resultUuid == null ? null : dynamicMarginCalculationClient.getStatus(resultUuid);
    }

    public void invalidateStatus(List<UUID> resultUuids) {
        if (CollectionUtils.isNotEmpty(resultUuids)) {
            dynamicMarginCalculationClient.invalidateStatus(resultUuids);
        }
    }

    public void deleteResults(List<UUID> resultUuids) {
        dynamicMarginCalculationClient.deleteResults(resultUuids);
    }

    public void deleteAllResults() {
        deleteResults(null);
    }

    public Integer getResultsCount() {
        return dynamicMarginCalculationClient.getResultsCount();
    }

    public void assertDynamicMarginCalculationNotRunning(UUID resultUuid) {
        DynamicMarginCalculationStatus status = getStatus(resultUuid);
        if (DynamicMarginCalculationStatus.RUNNING == status) {
            throw new StudyException(COMPUTATION_RUNNING);
        }
    }

    public UUID getDynamicMarginCalculationParametersUuidOrElseCreateDefault(StudyEntity studyEntity) {
        if (studyEntity.getDynamicMarginCalculationParametersUuid() == null) {
            // not supposed to happen because we create it as the study creation
            studyEntity.setDynamicMarginCalculationParametersUuid(dynamicMarginCalculationClient.createDefaultParameters());
        }
        return studyEntity.getDynamicMarginCalculationParametersUuid();
    }

    public void updateProvider(UUID parametersUuid, String provider) {
        dynamicMarginCalculationClient.updateProvider(parametersUuid, provider);
    }

    public String getDefaultProvider() {
        return dynamicMarginCalculationClient.getDefaultProvider();
    }

    public String getProvider(UUID parametersUuid) {
        return dynamicMarginCalculationClient.getProvider(parametersUuid);
    }
}
