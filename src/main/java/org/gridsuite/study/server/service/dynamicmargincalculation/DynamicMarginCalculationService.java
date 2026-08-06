/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicmargincalculation;

import lombok.NonNull;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_MARGIN_CALCULATION;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class DynamicMarginCalculationService extends AbstractComputationService {
    private final DynamicMarginCalculationRestService dynamicMarginCalculationRestService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final UserAdminService userAdminService;
    private final RootNetworkService rootNetworkService;

    protected DynamicMarginCalculationService(StudyRepository studyRepository,
                                              ComputationParametersService computationParametersService,
                                              NotificationService notificationService,
                                              RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                              DynamicMarginCalculationRestService dynamicMarginCalculationRestService,
                                              NetworkModificationTreeService networkModificationTreeService,
                                              UserAdminService userAdminService,
                                              RootNetworkService rootNetworkService) {
        super(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService);
        this.dynamicMarginCalculationRestService = dynamicMarginCalculationRestService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.userAdminService = userAdminService;
        this.rootNetworkService = rootNetworkService;
    }

    public String getDynamicMarginCalculationProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicMarginCalculationRestService.getProvider(studyEntity.getDynamicMarginCalculationParametersUuid());
    }

    @Transactional
    public String getDynamicMarginCalculationParameters(UUID studyUuid, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicMarginCalculationRestService.getParameters(
                dynamicMarginCalculationRestService.getDynamicMarginCalculationParametersUuidOrElseCreateDefault(studyEntity), userId);
    }

    @Transactional
    public boolean setDynamicMarginCalculationParameters(UUID studyUuid, String dmcParameter, String userId) {
        return setComputationParameters(
                studyUuid,
                dmcParameter,
                userId,
                StudyEntity::getDynamicMarginCalculationParametersUuid,
                StudyEntity::setDynamicMarginCalculationParametersUuid,
                UserProfileInfos::getDynamicMarginCalculationParameterId,
                dynamicMarginCalculationRestService,
                dynamicMarginCalculationRestService::createParameters,
                dynamicMarginCalculationRestService::updateParameters,
                DYNAMIC_MARGIN_CALCULATION,
                List.of(this::invalidateDynamicMarginCalculationStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS
        );
    }

    public void invalidateDynamicMarginCalculationStatusOnAllNodes(UUID studyUuid) {
        dynamicMarginCalculationRestService.invalidateStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, DYNAMIC_MARGIN_CALCULATION));
    }

    @Transactional
    public UUID runDynamicMarginCalculation(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);

        UUID result = handleDynamicMarginCalculationRequest(studyEntity, nodeUuid, rootNetworkUuid, debug, userId);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(DYNAMIC_MARGIN_CALCULATION), result);
        return result;
    }

    private UUID handleDynamicMarginCalculationRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, boolean debug, String userId) {

        // pre-condition check
        if (!rootNetworkNodeInfoService.isLoadflowConverged(nodeUuid, rootNetworkUuid)) {
            throw new StudyException(NOT_ALLOWED, "Load flow must run successfully before running dynamic margin calculation");
        }

        // clean previous result if exist
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_MARGIN_CALCULATION);
        if (prevResultUuid != null) {
            dynamicMarginCalculationRestService.deleteResults(List.of(prevResultUuid));
        }

        // get dynamic simulation parameters uuid
        UUID dynamicSimulationParametersUuid = studyEntity.getDynamicSimulationParametersUuid();

        // get dynamic security analysis parameters uuid
        UUID dynamicSecurityAnalysisParametersUuid = studyEntity.getDynamicSecurityAnalysisParametersUuid();

        // get dynamic margin calculation parameters uuid
        UUID dynamicMarginCalculationParametersUuid = studyEntity.getDynamicMarginCalculationParametersUuid();

        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(DYNAMIC_MARGIN_CALCULATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, DYNAMIC_MARGIN_CALCULATION, reportUuid);

        // launch dynamic margin calculation
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID dynamicMarginCalculationResultUuid = dynamicMarginCalculationRestService.runDynamicMarginCalculation(
                nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid,
                dynamicSimulationParametersUuid, dynamicSecurityAnalysisParametersUuid, dynamicMarginCalculationParametersUuid, userId, debug);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicMarginCalculationResultUuid, DYNAMIC_MARGIN_CALCULATION);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);

        return dynamicMarginCalculationResultUuid;
    }
}
