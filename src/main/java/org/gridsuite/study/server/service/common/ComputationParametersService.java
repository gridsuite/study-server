/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.common;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.dto.computation.ComputationParameterUUIDs;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationService;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author Abdelsalem HEDHILI <abdelsalem.hedhili at rte-france.com>
 */

@Service
public class ComputationParametersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputationParametersService.class);

    private final UserAdminService userAdminService;
    private final List<ComputationParametersDefinition> computationParametersDefinitions;

    // this is useful to avoid repetitive calls when doing operation on all computation types (duplicate, delete)
    private record ComputationParametersDefinition(
            ComputationType type,
            Function<StudyEntity, UUID> studyParameterGetter,
            Function<UserProfileInfos, UUID> profileParameterGetter,
            ComputationParameters service,
            BiConsumer<ComputationParameterUUIDs.ComputationParameterUUIDsBuilder, UUID> parametersSetter,
            Function<ComputationParameterUUIDs, UUID> parametersGetter,
            String defaultErrorLabel
    ) {
    }

    public ComputationParametersService(SecurityAnalysisService securityAnalysisService,
                                        SensitivityAnalysisService sensitivityAnalysisService,
                                        LoadFlowService loadFlowService,
                                        ShortCircuitService shortCircuitService,
                                        VoltageInitService voltageInitService,
                                        DynamicSimulationService dynamicSimulationService,
                                        DynamicSecurityAnalysisService dynamicSecurityAnalysisService,
                                        DynamicMarginCalculationService dynamicMarginCalculationService,
                                        StateEstimationService stateEstimationService,
                                        PccMinService pccMinService,
                                        UserAdminService userAdminService) {

        this.userAdminService = userAdminService;
        this.computationParametersDefinitions = List.of(
                new ComputationParametersDefinition(
                        ComputationType.LOAD_FLOW,
                        StudyEntity::getLoadFlowParametersUuid,
                        UserProfileInfos::getLoadFlowParameterId,
                        loadFlowService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::loadFlowParametersUuid,
                        ComputationParameterUUIDs::loadFlowParametersUuid,
                        "LoadFlow analysis"),
                new ComputationParametersDefinition(
                        ComputationType.SHORT_CIRCUIT,
                        StudyEntity::getShortCircuitParametersUuid,
                        UserProfileInfos::getShortcircuitParameterId,
                        shortCircuitService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::shortCircuitParametersUuid,
                        ComputationParameterUUIDs::shortCircuitParametersUuid,
                        "ShortCircuit analysis"),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_SIMULATION,
                        StudyEntity::getDynamicSimulationParametersUuid,
                        UserProfileInfos::getDynamicSimulationParameterId,
                        dynamicSimulationService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::dynamicSimulationParametersUuid,
                        ComputationParameterUUIDs::dynamicSimulationParametersUuid,
                        "dynamic simulation"),
                new ComputationParametersDefinition(
                        ComputationType.VOLTAGE_INITIALIZATION,
                        StudyEntity::getVoltageInitParametersUuid,
                        UserProfileInfos::getVoltageInitParameterId,
                        voltageInitService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::voltageInitParametersUuid,
                        ComputationParameterUUIDs::voltageInitParametersUuid,
                        "voltage init"),
                new ComputationParametersDefinition(
                        ComputationType.SECURITY_ANALYSIS,
                        StudyEntity::getSecurityAnalysisParametersUuid,
                        UserProfileInfos::getSecurityAnalysisParameterId,
                        securityAnalysisService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::securityAnalysisParametersUuid,
                        ComputationParameterUUIDs::securityAnalysisParametersUuid,
                        "Security analysis"),
                new ComputationParametersDefinition(
                        ComputationType.SENSITIVITY_ANALYSIS,
                        StudyEntity::getSensitivityAnalysisParametersUuid,
                        UserProfileInfos::getSensitivityAnalysisParameterId,
                        sensitivityAnalysisService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::sensitivityAnalysisParametersUuid,
                        ComputationParameterUUIDs::sensitivityAnalysisParametersUuid,
                        "Sensitivity analysis"),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_SECURITY_ANALYSIS,
                        StudyEntity::getDynamicSecurityAnalysisParametersUuid,
                        UserProfileInfos::getDynamicSecurityAnalysisParameterId,
                        dynamicSecurityAnalysisService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::dynamicSecurityAnalysisParametersUuid,
                        ComputationParameterUUIDs::dynamicSecurityAnalysisParametersUuid,
                        "dynamic security analysis"),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_MARGIN_CALCULATION,
                        StudyEntity::getDynamicMarginCalculationParametersUuid,
                        UserProfileInfos::getDynamicMarginCalculationParameterId,
                        dynamicMarginCalculationService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::dynamicMarginCalculationParametersUuid,
                        ComputationParameterUUIDs::dynamicMarginCalculationParametersUuid,
                        "dynamic margin calculation"),
                new ComputationParametersDefinition(
                        ComputationType.STATE_ESTIMATION,
                        StudyEntity::getStateEstimationParametersUuid,
                        userProfileInfos -> null,
                        stateEstimationService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::stateEstimationParametersUuid,
                        ComputationParameterUUIDs::stateEstimationParametersUuid,
                        "state estimation"),
                new ComputationParametersDefinition(
                        ComputationType.PCC_MIN,
                        StudyEntity::getPccMinParametersUuid,
                        UserProfileInfos::getPccMinParameterId,
                        pccMinService,
                        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder::pccMinParametersUuid,
                        ComputationParameterUUIDs::pccMinParametersUuid,
                        "pcc min")
        );
    }

    public ComputationParameterUUIDs createDefaultComputationParameters(String userId, UserProfileInfos userProfileInfos) {
        ComputationParameterUUIDs.ComputationParameterUUIDsBuilder parametersBuilder = ComputationParameterUUIDs.builder();
        computationParametersDefinitions.forEach(definition ->
                definition.parametersSetter().accept(parametersBuilder, createDefaultParameters(userId, userProfileInfos, definition)));
        return parametersBuilder.build();
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
        ComputationParameterUUIDs computationParameterUUIDs = ComputationParameterUUIDs.builder()
                .loadFlowParametersUuid(studyEntity.getLoadFlowParametersUuid())
                .shortCircuitParametersUuid(studyEntity.getShortCircuitParametersUuid())
                .dynamicSimulationParametersUuid(studyEntity.getDynamicSimulationParametersUuid())
                .voltageInitParametersUuid(studyEntity.getVoltageInitParametersUuid())
                .securityAnalysisParametersUuid(studyEntity.getSecurityAnalysisParametersUuid())
                .sensitivityAnalysisParametersUuid(studyEntity.getSensitivityAnalysisParametersUuid())
                .dynamicSecurityAnalysisParametersUuid(studyEntity.getDynamicSecurityAnalysisParametersUuid())
                .dynamicMarginCalculationParametersUuid(studyEntity.getDynamicMarginCalculationParametersUuid())
                .stateEstimationParametersUuid(studyEntity.getStateEstimationParametersUuid())
                .pccMinParametersUuid(studyEntity.getPccMinParametersUuid())
                .build();
        computationParametersDefinitions.forEach(definition ->
            definition.service().doDeleteComputationParameters(definition.parametersGetter.apply(computationParameterUUIDs), definition.type().getLabel(), LOGGER)
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

    private UUID createDefaultParameters(String userId, UserProfileInfos userProfileInfos, ComputationParametersDefinition definition) {
        UUID profileParameterId = userProfileInfos == null ?
                null :
                definition.profileParameterGetter().apply(userProfileInfos);

        if (profileParameterId != null) {
            try {
                return definition.service().duplicateParameters(profileParameterId);
            } catch (Exception e) {
                LOGGER.error("Could not duplicate {} parameters with id '{}' from user/profile '{}/{}'. Using default parameters",
                        definition.type().getLabel(), profileParameterId, userId, userProfileInfos.getName(), e);
            }
        }

        try {
            return definition.service().createDefaultParameters();
        } catch (Exception e) {
            LOGGER.error("Error while creating default parameters for {}", definition.defaultErrorLabel(), e);
            return null;
        }
    }

}
