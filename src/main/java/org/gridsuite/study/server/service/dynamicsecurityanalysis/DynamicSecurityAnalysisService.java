/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsecurityanalysis;

import lombok.NonNull;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SECURITY_ANALYSIS;
import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SIMULATION;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class DynamicSecurityAnalysisService extends AbstractComputationService {

    protected DynamicSecurityAnalysisService(StudyRepository studyRepository,
                                             ComputationParametersService computationParametersService,
                                             NotificationService notificationService,
                                             RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                             DynamicSecurityAnalysisRestService dynamicSecurityAnalysisRestService,
                                             NetworkModificationTreeService networkModificationTreeService,
                                             UserAdminService userAdminService, RootNetworkService rootNetworkService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService, rootNetworkService,
            computationParametersService, userAdminService);
        this.dynamicSecurityAnalysisRestService = dynamicSecurityAnalysisRestService;
    }

    public String getDynamicSecurityAnalysisProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicSecurityAnalysisRestService.getProvider(studyEntity.getDynamicSecurityAnalysisParametersUuid());
    }

    @Transactional
    public String getDynamicSecurityAnalysisParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicSecurityAnalysisRestService.getParameters(
                dynamicSecurityAnalysisRestService.getDynamicSecurityAnalysisParametersUuidOrElseCreateDefault(studyEntity));
    }

    @Transactional
    public boolean setDynamicSecurityAnalysisParameters(UUID studyUuid, String dsaParameter, String userId) {
        return setComputationParameters(
                studyUuid,
                dsaParameter,
                userId,
                StudyEntity::getDynamicSecurityAnalysisParametersUuid,
                StudyEntity::setDynamicSecurityAnalysisParametersUuid,
                UserProfileInfos::getDynamicSecurityAnalysisParameterId,
                dynamicSecurityAnalysisRestService,
                dynamicSecurityAnalysisRestService::createParameters,
                dynamicSecurityAnalysisRestService::updateParameters,
                DYNAMIC_SECURITY_ANALYSIS,
                List.of(this::invalidateDynamicSecurityAnalysisStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS
        );
    }

    @Transactional
    public UUID runDynamicSecurityAnalysis(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        UUID result = handleDynamicSecurityAnalysisRequest(studyEntity, nodeUuid, rootNetworkUuid, debug, userId);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(DYNAMIC_SECURITY_ANALYSIS), result);
        return result;
    }

    private UUID handleDynamicSecurityAnalysisRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, boolean debug, String userId) {

        // pre-condition check
        if (!rootNetworkNodeInfoService.isLoadflowConverged(nodeUuid, rootNetworkUuid)) {
            throw new StudyException(NOT_ALLOWED, "Load flow must run successfully before running dynamic security analysis");
        }

        String dsStatus = rootNetworkNodeInfoService.getDynamicSimulationStatus(nodeUuid, rootNetworkUuid);
        if (!DynamicSimulationStatus.CONVERGED.name().equals(dsStatus)) {
            throw new StudyException(NOT_ALLOWED, "Dynamic simulation must run successfully before running dynamic security analysis");
        }

        // clean previous result if exist
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SECURITY_ANALYSIS);
        if (prevResultUuid != null) {
            dynamicSecurityAnalysisRestService.deleteResults(List.of(prevResultUuid));
        }

        // get dynamic simulation result uuid
        UUID dynamicSimulationResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SIMULATION);

        // get dynamic security analysis parameters uuid
        UUID dynamicSecurityAnalysisParametersUuid = studyEntity.getDynamicSecurityAnalysisParametersUuid();

        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(DYNAMIC_SECURITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SECURITY_ANALYSIS, reportUuid);

        // launch dynamic security analysis
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID dynamicSecurityAnalysisResultUuid = dynamicSecurityAnalysisRestService.runDynamicSecurityAnalysis(
                nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid,
                dynamicSimulationResultUuid, dynamicSecurityAnalysisParametersUuid, userId, debug);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicSecurityAnalysisResultUuid, DYNAMIC_SECURITY_ANALYSIS);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);

        return dynamicSecurityAnalysisResultUuid;
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        return dynamicSecurityAnalysisRestService.downloadDebugFile(resultUuid);
    }

    public String getProviders() {
        return dynamicSecurityAnalysisRestService.getProviders();
    }

    public String getParameters(UUID parametersUuid) {
        return dynamicSecurityAnalysisRestService.getParameters(parametersUuid);
    }

    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        dynamicSecurityAnalysisRestService.updateParameters(parametersUuid, parametersInfos);
    }

}
