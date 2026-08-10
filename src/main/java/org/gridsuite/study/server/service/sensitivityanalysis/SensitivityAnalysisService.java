/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.sensitivityanalysis;

import lombok.NonNull;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.SENSITIVITY_ANALYSIS;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class SensitivityAnalysisService extends AbstractComputationService {

    private final DirectoryService directoryService;

    protected SensitivityAnalysisService(StudyRepository studyRepository,
                                         ComputationParametersService computationParametersService,
                                         NotificationService notificationService,
                                         RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                         SensitivityAnalysisRestService sensitivityAnalysisRestService,
                                         NetworkModificationTreeService networkModificationTreeService,
                                         RootNetworkService rootNetworkService,
                                         UserAdminService userAdminService,
                                         DirectoryService directoryService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService,
            rootNetworkService, computationParametersService, userAdminService);
        this.sensitivityAnalysisRestService = sensitivityAnalysisRestService;
        this.directoryService = directoryService;
    }

    @Transactional
    public String getSensitivityAnalysisParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return sensitivityAnalysisRestService.getSensitivityAnalysisParameters(
                sensitivityAnalysisRestService.getSensitivityAnalysisParametersUuidOrElseCreateDefault(studyEntity));
    }

    @Transactional
    public boolean setSensitivityAnalysisParameters(UUID studyUuid, String parameters, String userId) {
        return setComputationParameters(
                studyUuid,
                parameters,
                userId,
                StudyEntity::getSensitivityAnalysisParametersUuid,
                StudyEntity::setSensitivityAnalysisParametersUuid,
                UserProfileInfos::getSensitivityAnalysisParameterId,
                sensitivityAnalysisRestService,
                sensitivityAnalysisRestService::createSensitivityAnalysisParameters,
                sensitivityAnalysisRestService::updateSensitivityAnalysisParameters,
                SENSITIVITY_ANALYSIS,
                List.of(this::invalidateSensitivityAnalysisStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS
        );
    }

    @Transactional
    public UUID runSensitivityAnalysis(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        UUID result = handleSensitivityAnalysisRequest(study, nodeUuid, rootNetworkUuid, userId);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(SENSITIVITY_ANALYSIS), result);
        return result;
    }

    private UUID handleSensitivityAnalysisRequest(StudyEntity study, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS);
        if (prevResultUuid != null) {
            sensitivityAnalysisRestService.deleteSensitivityAnalysisResults(List.of(prevResultUuid));
        }
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID sensiReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(SENSITIVITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS, sensiReportUuid);

        UUID sensiParamsUuid = study.getSensitivityAnalysisParametersUuid();

        // fetch the filters and contingencyLists contained in sensi parameters
        // and retrieve their names, as they are needed in the results
        List<UUID> elementIds = sensitivityAnalysisRestService.getElementIds(sensiParamsUuid);
        Map<UUID, String> elementsIdNameMap = directoryService.getElementNames(new HashSet<>(elementIds));
        UUID result = sensitivityAnalysisRestService.runSensitivityAnalysis(nodeUuid, rootNetworkUuid, networkUuid, variantId,
                sensiReportUuid, userId, sensiParamsUuid, study.getLoadFlowParametersUuid(), elementsIdNameMap);

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, SENSITIVITY_ANALYSIS);
        notificationService.emitStudyChanged(study.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(study.getId(), userId);
        return result;
    }

    public String getProviders() {
        return sensitivityAnalysisRestService.getProviders();
    }

    public String getSensitivityAnalysisParametersByUuid(UUID parameterUuid) {
        return sensitivityAnalysisRestService.getParameters(parameterUuid);
    }

    public void updateSensitivityAnalysisParameters(UUID parameterUuid, String parameters) {
        sensitivityAnalysisRestService.updateParameters(parameterUuid, parameters);
    }

}
