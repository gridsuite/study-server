/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.dto.computation.ComputationParameterUUIDs;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationRestService;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisRestService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationRestService;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.gridsuite.study.server.service.pccmin.PccMinRestService;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisRestService;
import org.gridsuite.study.server.service.sensitivityanalysis.SensitivityAnalysisRestService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitRestService;
import org.gridsuite.study.server.service.stateestimation.StateEstimationRestService;
import org.gridsuite.study.server.service.voltageinit.VoltageInitRestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.EXPORT_STUDY_ERROR;

/**
 * @author Abdelsalem HEDHILI <abdelsalem.hedhili at rte-france.com>
 */

@Service
public class ComputationParametersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputationParametersService.class);

    private final UserAdminService userAdminService;
    private final ObjectMapper objectMapper;
    private final List<ComputationParametersDefinition> computationParametersDefinitions;

    // this is useful to avoid repetitive calls when doing operation on all computation types (duplicate, delete, export, import)
    private record ComputationParametersDefinition(
            ComputationType type,
            Function<StudyEntity, UUID> studyParameterGetter,
            Function<UserProfileInfos, UUID> profileParameterGetter,
            ComputationParameters service,
            BiConsumer<ComputationParameterUUIDs.ComputationParameterUUIDsBuilder, UUID> parametersSetter,
            BiFunction<UUID, String, ?> parametersFetcher,
            Function<String, UUID> parametersCreator
    ) {
    }

    public ComputationParametersService(SecurityAnalysisRestService securityAnalysisService,
                                        SensitivityAnalysisRestService sensitivityAnalysisService,
                                        LoadFlowRestService loadFlowRestService,
                                        ShortCircuitRestService shortCircuitService,
                                        VoltageInitRestService voltageInitService,
                                        DynamicSimulationRestService dynamicSimulationRestService,
                                        DynamicSecurityAnalysisRestService dynamicSecurityAnalysisRestService,
                                        DynamicMarginCalculationRestService dynamicMarginCalculationRestService,
                                        StateEstimationRestService stateEstimationService,
                                        PccMinRestService pccMinService,
                                        UserAdminService userAdminService,
                                        ObjectMapper objectMapper) {

        this.userAdminService = userAdminService;
        this.objectMapper = objectMapper;
        this.computationParametersDefinitions = List.of(
                new ComputationParametersDefinition(
                        ComputationType.LOAD_FLOW,
                        StudyEntity::getLoadFlowParametersUuid,
                        UserProfileInfos::getLoadFlowParameterId,
                    loadFlowRestService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::loadFlowParametersUuid,
                        (uuid, userId) -> loadFlowRestService.getParameters(uuid),
                        loadFlowRestService::createLoadFlowParameters),
                new ComputationParametersDefinition(
                        ComputationType.SHORT_CIRCUIT,
                        StudyEntity::getShortCircuitParametersUuid,
                        UserProfileInfos::getShortcircuitParameterId,
                        shortCircuitService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::shortCircuitParametersUuid,
                        (uuid, userId) -> shortCircuitService.getParameters(uuid),
                        shortCircuitService::createParameters),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_SIMULATION,
                        StudyEntity::getDynamicSimulationParametersUuid,
                        UserProfileInfos::getDynamicSimulationParameterId,
                        dynamicSimulationRestService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::dynamicSimulationParametersUuid,
                        (uuid, userId) -> dynamicSimulationRestService.getParameters(uuid),
                        dynamicSimulationRestService::createParameters),
                new ComputationParametersDefinition(
                        ComputationType.VOLTAGE_INITIALIZATION,
                        StudyEntity::getVoltageInitParametersUuid,
                        UserProfileInfos::getVoltageInitParameterId,
                        voltageInitService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::voltageInitParametersUuid,
                        (uuid, userId) -> voltageInitService.getParameters(uuid),
                        json -> voltageInitService.createVoltageInitParameters(parseVoltageInitParameters(json))),
                new ComputationParametersDefinition(
                        ComputationType.SECURITY_ANALYSIS,
                        StudyEntity::getSecurityAnalysisParametersUuid,
                        UserProfileInfos::getSecurityAnalysisParameterId,
                        securityAnalysisService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::securityAnalysisParametersUuid,
                        (uuid, userId) -> securityAnalysisService.getParameters(uuid),
                        securityAnalysisService::createSecurityAnalysisParameters),
                new ComputationParametersDefinition(
                        ComputationType.SENSITIVITY_ANALYSIS,
                        StudyEntity::getSensitivityAnalysisParametersUuid,
                        UserProfileInfos::getSensitivityAnalysisParameterId,
                        sensitivityAnalysisService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::sensitivityAnalysisParametersUuid,
                        (uuid, userId) -> sensitivityAnalysisService.getParameters(uuid),
                        sensitivityAnalysisService::createSensitivityAnalysisParameters),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_SECURITY_ANALYSIS,
                        StudyEntity::getDynamicSecurityAnalysisParametersUuid,
                        UserProfileInfos::getDynamicSecurityAnalysisParameterId,
                        dynamicSecurityAnalysisRestService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::dynamicSecurityAnalysisParametersUuid,
                        (uuid, userId) -> dynamicSecurityAnalysisRestService.getParameters(uuid),
                        dynamicSecurityAnalysisRestService::createParameters),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_MARGIN_CALCULATION,
                        StudyEntity::getDynamicMarginCalculationParametersUuid,
                        UserProfileInfos::getDynamicMarginCalculationParameterId,
                        dynamicMarginCalculationRestService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::dynamicMarginCalculationParametersUuid,
                        dynamicMarginCalculationRestService::getParameters,
                        dynamicMarginCalculationRestService::createParameters),
                new ComputationParametersDefinition(
                        ComputationType.STATE_ESTIMATION,
                        StudyEntity::getStateEstimationParametersUuid,
                        userProfileInfos -> null,
                        stateEstimationService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::stateEstimationParametersUuid,
                        (uuid, userId) -> stateEstimationService.getStateEstimationParameters(uuid),
                        stateEstimationService::createStateEstimationParameters),
                new ComputationParametersDefinition(
                        ComputationType.PCC_MIN,
                        StudyEntity::getPccMinParametersUuid,
                        UserProfileInfos::getPccMinParameterId,
                        pccMinService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::pccMinParametersUuid,
                        (uuid, userId) -> pccMinService.getParameters(uuid),
                        pccMinService::createPccMinParameters)
        );
    }

    private VoltageInitParametersInfos parseVoltageInitParameters(String json) {
        try {
            return objectMapper.readValue(json, VoltageInitParametersInfos.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ComputationParameterUUIDs createDefaultComputationParameters(String userId, UserProfileInfos userProfileInfos) {
        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder parametersBuilder = ComputationParameterUUIDs.builder();
        computationParametersDefinitions.forEach(definition ->
                definition.parametersSetter().accept(parametersBuilder,
                        definition.service.doCreateDefaultParameters(
                                userId,
                                userProfileInfos,
                                definition.profileParameterGetter,
                                definition.type().getLabel(),
                                LOGGER
                        )
                )
        );
        return parametersBuilder.build();
    }

    public ComputationParameterUUIDs importComputationParameters(Map<String, String> exportedParametersByType, String userId, UserProfileInfos userProfileInfos) {
        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder parametersBuilder = ComputationParameterUUIDs.builder();
        computationParametersDefinitions.forEach(definition -> {
            String exportedJson = exportedParametersByType == null ? null : exportedParametersByType.get(definition.type().name());
            UUID newParametersUuid = exportedJson == null ? null : createFromExportedJson(definition, exportedJson);
            if (newParametersUuid == null) {
                newParametersUuid = definition.service().doCreateDefaultParameters(userId, userProfileInfos, definition.profileParameterGetter(), definition.type().getLabel(), LOGGER);
            }
            definition.parametersSetter().accept(parametersBuilder, newParametersUuid);
        });
        return parametersBuilder.build();
    }

    private UUID createFromExportedJson(ComputationParametersDefinition definition, String json) {
        try {
            return definition.parametersCreator().apply(json);
        } catch (Exception e) {
            LOGGER.warn("Failed to recreate imported {} parameters, falling back to default parameters", definition.type().getLabel(), e);
            return null;
        }
    }

    public ComputationParameterUUIDs duplicateParameters(StudyEntity sourceStudyEntity) {
        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder parametersBuilder = ComputationParameterUUIDs.builder();
        computationParametersDefinitions.forEach(definition -> {
            UUID sourceParametersUuid = definition.studyParameterGetter().apply(sourceStudyEntity);
            if (sourceParametersUuid != null) {
                definition.parametersSetter().accept(parametersBuilder, definition.service().duplicateParameters(sourceParametersUuid));
            }
        });
        return parametersBuilder.build();
    }

    public void deleteComputationsParameters(StudyEntity studyEntity) {
        computationParametersDefinitions.forEach(definition ->
            definition.service().doDeleteComputationParameters(definition.studyParameterGetter().apply(studyEntity), definition.type().getLabel(), LOGGER)
        );
    }

    public <T> boolean createOrUpdateParameters(
            StudyEntity studyEntity,
            T parameters,
            String userId,
            Function<StudyEntity, UUID> studyParameterGetter,
            BiConsumer<StudyEntity, UUID> studyParameterSetter,
            Function<UserProfileInfos, UUID> profileParameterGetter,
            ComputationParameters computationParameters,
            Function<T, UUID> createParameters,
            BiConsumer<UUID, T> updateParameters,
            String computationType
    ) {
        boolean userProfileIssue = false;
        UUID existingParametersUuid = studyParameterGetter.apply(studyEntity);

        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        UUID profileParameterId = userProfileInfos == null ? null : profileParameterGetter.apply(userProfileInfos);

        if (parameters == null && profileParameterId != null) {
            try {
                UUID parametersFromProfileUuid = computationParameters.duplicateParameters(profileParameterId);
                studyParameterSetter.accept(studyEntity, parametersFromProfileUuid);
                computationParameters.doDeleteComputationParameters(existingParametersUuid, computationType, LOGGER);
                return false;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(
                        "Could not duplicate {} parameters with id '{}' from user/profile '{}/{}'. Using default parameters",
                        computationType,
                        profileParameterId,
                        userId,
                        userProfileInfos.getName(),
                        e
                );
            }
        }

        createOrUpdateParameters(studyEntity, parameters, studyParameterGetter, studyParameterSetter, createParameters, updateParameters);
        return userProfileIssue;
    }

    public <T> void createOrUpdateParameters(
            StudyEntity studyEntity,
            T parameters,
            Function<StudyEntity, UUID> studyParameterGetter,
            BiConsumer<StudyEntity, UUID> studyParameterSetter,
            Function<T, UUID> createParameters,
            BiConsumer<UUID, T> updateParameters
    ) {
        UUID existingParametersUuid = studyParameterGetter.apply(studyEntity);
        if (existingParametersUuid == null) {
            UUID newParametersUuid = createParameters.apply(parameters);
            studyParameterSetter.accept(studyEntity, newParametersUuid);
        } else {
            updateParameters.accept(existingParametersUuid, parameters);
        }
    }

    public Map<String, String> exportParameters(StudyEntity studyEntity, String userId) {
        Map<String, String> parametersByType = new LinkedHashMap<>();
        computationParametersDefinitions.forEach(definition -> {
            UUID parametersUuid = definition.studyParameterGetter().apply(studyEntity);
            if (parametersUuid == null) {
                return;
            }
            try {
                Object parameters = definition.parametersFetcher().apply(parametersUuid, userId);
                parametersByType.put(definition.type().name(), parameters instanceof String json ? json : writeAsJson(definition.type(), parameters));
            } catch (Exception e) {
                LOGGER.warn("Failed to fetch {} parameters (uuid={}) for study export", definition.type().getLabel(), parametersUuid, e);
            }
        });
        return parametersByType;
    }

    private String writeAsJson(ComputationType type, Object parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to serialize " + type.getLabel() + " parameters");
        }
    }

}
