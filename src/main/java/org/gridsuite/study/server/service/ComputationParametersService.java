/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.dto.computation.ComputationsParameters;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.common.ComputationParameters;
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
import java.util.function.Supplier;

/**
 * @author Abdelsalem HEDHILI <abdelsalem.hedhili at rte-france.com>
 */

@Service
public class ComputationParametersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputationParametersService.class);

    private final StateEstimationService stateEstimationService;
    private final UserAdminService userAdminService;
    private final List<ComputationParametersDefinition> computationParametersDefinitions;

    // this is useful to avoid repetitive calls when doing operation on all computation types (duplicate, delete)
    private record ComputationParametersDefinition(
            ComputationType type,
            Function<StudyEntity, UUID> studyParameterGetter,
            Function<UserProfileInfos, UUID> profileParameterGetter,
            ComputationParameters service,
            Supplier<UUID> defaultParametersSupplier,
            BiConsumer<ComputationsParameters.ComputationsParametersBuilder, UUID> parametersSetter,
            Function<ComputationsParameters, UUID> parametersGetter,
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

        this.stateEstimationService = stateEstimationService;
        this.userAdminService = userAdminService;
        this.computationParametersDefinitions = List.of(
                new ComputationParametersDefinition(
                        ComputationType.LOAD_FLOW,
                        StudyEntity::getLoadFlowParametersUuid,
                        UserProfileInfos::getLoadFlowParameterId,
                        loadFlowService,
                        loadFlowService::createDefaultLoadFlowParameters,
                        ComputationsParameters.ComputationsParametersBuilder::loadFlowParametersUuid,
                        ComputationsParameters::loadFlowParametersUuid,
                        "LoadFlow analysis"),
                new ComputationParametersDefinition(
                        ComputationType.SHORT_CIRCUIT,
                        StudyEntity::getShortCircuitParametersUuid,
                        UserProfileInfos::getShortcircuitParameterId,
                        shortCircuitService,
                        () -> shortCircuitService.createParameters(null),
                        ComputationsParameters.ComputationsParametersBuilder::shortCircuitParametersUuid,
                        ComputationsParameters::shortCircuitParametersUuid,
                        "ShortCircuit analysis"),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_SIMULATION,
                        StudyEntity::getDynamicSimulationParametersUuid,
                        UserProfileInfos::getDynamicSimulationParameterId,
                        dynamicSimulationService,
                        dynamicSimulationService::createDefaultParameters,
                        ComputationsParameters.ComputationsParametersBuilder::dynamicSimulationParametersUuid,
                        ComputationsParameters::dynamicSimulationParametersUuid,
                        "dynamic simulation"),
                new ComputationParametersDefinition(
                        ComputationType.VOLTAGE_INITIALIZATION,
                        StudyEntity::getVoltageInitParametersUuid,
                        UserProfileInfos::getVoltageInitParameterId,
                        voltageInitService,
                        () -> voltageInitService.createVoltageInitParameters(null),
                        ComputationsParameters.ComputationsParametersBuilder::voltageInitParametersUuid,
                        ComputationsParameters::voltageInitParametersUuid,
                        "voltage init"),
                new ComputationParametersDefinition(
                        ComputationType.SECURITY_ANALYSIS,
                        StudyEntity::getSecurityAnalysisParametersUuid,
                        UserProfileInfos::getSecurityAnalysisParameterId,
                        securityAnalysisService,
                        securityAnalysisService::createDefaultSecurityAnalysisParameters,
                        ComputationsParameters.ComputationsParametersBuilder::securityAnalysisParametersUuid,
                        ComputationsParameters::securityAnalysisParametersUuid,
                        "Security analysis"),
                new ComputationParametersDefinition(
                        ComputationType.SENSITIVITY_ANALYSIS,
                        StudyEntity::getSensitivityAnalysisParametersUuid,
                        UserProfileInfos::getSensitivityAnalysisParameterId,
                        sensitivityAnalysisService,
                        sensitivityAnalysisService::createDefaultSensitivityAnalysisParameters,
                        ComputationsParameters.ComputationsParametersBuilder::sensitivityAnalysisParametersUuid,
                        ComputationsParameters::sensitivityAnalysisParametersUuid,
                        "Sensitivity analysis"),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_SECURITY_ANALYSIS,
                        StudyEntity::getDynamicSecurityAnalysisParametersUuid,
                        UserProfileInfos::getDynamicSecurityAnalysisParameterId,
                        dynamicSecurityAnalysisService,
                        dynamicSecurityAnalysisService::createDefaultParameters,
                        ComputationsParameters.ComputationsParametersBuilder::dynamicSecurityAnalysisParametersUuid,
                        ComputationsParameters::dynamicSecurityAnalysisParametersUuid,
                        "dynamic security analysis"),
                new ComputationParametersDefinition(
                        ComputationType.DYNAMIC_MARGIN_CALCULATION,
                        StudyEntity::getDynamicMarginCalculationParametersUuid,
                        UserProfileInfos::getDynamicMarginCalculationParameterId,
                        dynamicMarginCalculationService,
                        dynamicMarginCalculationService::createDefaultParameters,
                        ComputationsParameters.ComputationsParametersBuilder::dynamicMarginCalculationParametersUuid,
                        ComputationsParameters::dynamicMarginCalculationParametersUuid,
                        "dynamic margin calculation"),
                new ComputationParametersDefinition(
                        ComputationType.STATE_ESTIMATION,
                        StudyEntity::getStateEstimationParametersUuid,
                        userProfileInfos -> null,
                        stateEstimationService,
                        this::createDefaultStateEstimationParameters,
                        ComputationsParameters.ComputationsParametersBuilder::stateEstimationParametersUuid,
                        ComputationsParameters::stateEstimationParametersUuid,
                        "state estimation"),
                new ComputationParametersDefinition(
                        ComputationType.PCC_MIN,
                        StudyEntity::getPccMinParametersUuid,
                        UserProfileInfos::getPccMinParameterId,
                        pccMinService,
                        pccMinService::createDefaultPccMinParameters,
                        ComputationsParameters.ComputationsParametersBuilder::pccMinParametersUuid,
                        ComputationsParameters::pccMinParametersUuid,
                        "pcc min")
        );
    }

    public ComputationsParameters createDefaultComputationParameters(String userId, UserProfileInfos userProfileInfos) {
        ComputationsParameters.ComputationsParametersBuilder parametersBuilder = ComputationsParameters.builder();
        computationParametersDefinitions.forEach(definition ->
                definition.parametersSetter().accept(parametersBuilder, createDefaultParameters(userId, userProfileInfos, definition)));
        return parametersBuilder.build();
    }

    public ComputationsParameters duplicateParameters(StudyEntity sourceStudyEntity) {
        ComputationsParameters.ComputationsParametersBuilder parametersBuilder = ComputationsParameters.builder();
        computationParametersDefinitions.forEach(definition -> {
            UUID sourceParametersUuid = definition.studyParameterGetter().apply(sourceStudyEntity);
            if (sourceParametersUuid != null) {
                definition.parametersSetter().accept(parametersBuilder, definition.service().duplicateParameters(sourceParametersUuid));
            }
        });
        return parametersBuilder.build();
    }

    public void deleteComputationsParameters(ComputationsParameters parameters) {
        computationParametersDefinitions.forEach(definition ->
                deleteComputationParameters(definition.parametersGetter().apply(parameters), definition.service(), definition.type().getLabel()));
    }

    public void deleteComputationParameters(UUID parametersUuid, ComputationParameters computationParameters, String computationType) {
        if (parametersUuid != null) {
            try {
                computationParameters.deleteParameters(parametersUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove {} parameters with uuid: {}", computationType, parametersUuid, e);
            }
        }
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
            String parameterLabel
    ) {
        boolean userProfileIssue = false;
        UUID existingParametersUuid = studyParameterGetter.apply(studyEntity);

        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        UUID profileParameterId = userProfileInfos == null ? null : profileParameterGetter.apply(userProfileInfos);

        if (parameters == null && profileParameterId != null) {
            try {
                UUID parametersFromProfileUuid = computationParameters.duplicateParameters(profileParameterId);
                studyParameterSetter.accept(studyEntity, parametersFromProfileUuid);
                deleteComputationParameters(existingParametersUuid, computationParameters, parameterLabel);
                return false;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(
                        "Could not duplicate {} parameters with id '{}' from user/profile '{}/{}'. Using default parameters",
                        parameterLabel,
                        profileParameterId,
                        userId,
                        userProfileInfos.getName(),
                        e
                );
            }
        }

        if (existingParametersUuid == null) {
            UUID newParametersUuid = createParameters.apply(parameters);
            studyParameterSetter.accept(studyEntity, newParametersUuid);
        } else {
            updateParameters.accept(existingParametersUuid, parameters);
        }
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
            return definition.defaultParametersSupplier().get();
        } catch (Exception e) {
            LOGGER.error("Error while creating default parameters for {}", definition.defaultErrorLabel(), e);
            return null;
        }
    }

    private UUID createDefaultStateEstimationParameters() {
        try {
            return stateEstimationService.createDefaultStateEstimationParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating state estimation default parameters", e);
            return null;
        }
    }
}
