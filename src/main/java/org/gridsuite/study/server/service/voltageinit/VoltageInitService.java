/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.voltageinit;

import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.dto.VariantInfos;
import org.gridsuite.study.server.dto.voltageinit.ContextInfos;
import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class VoltageInitService extends AbstractComputationService {
    private final VoltageInitRestService voltageInitRestService;

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitService.class);

    protected VoltageInitService(StudyRepository studyRepository,
                                 ComputationParametersService computationParametersService,
                                 NotificationService notificationService,
                                 RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                 VoltageInitRestService voltageInitRestService,
                                 NetworkModificationTreeService networkModificationTreeService,
                                 UserAdminService userAdminService,
                                 RootNetworkService rootNetworkService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService,
            rootNetworkService, computationParametersService, userAdminService);
        this.voltageInitRestService = voltageInitRestService;
    }

    public StudyVoltageInitParameters getVoltageInitParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return new StudyVoltageInitParameters(
                Optional.ofNullable(studyEntity.getVoltageInitParametersUuid()).map(voltageInitRestService::getVoltageInitParameters).orElse(null),
                Optional.ofNullable(studyEntity.getVoltageInitParameters()).map(StudyVoltageInitParametersEntity::shouldApplyModifications).orElse(true)
        );
    }

    @Transactional
    public UUID runVoltageInit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        UUID result = handleVoltageInitRequest(studyEntity, nodeUuid, rootNetworkUuid, debug, userId);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(VOLTAGE_INITIALIZATION), result);
        return result;
    }

    private UUID handleVoltageInitRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, boolean debug, String userId) {
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION);
        if (prevResultUuid != null) {
            voltageInitRestService.deleteVoltageInitResults(List.of(prevResultUuid));
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(VOLTAGE_INITIALIZATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION, reportUuid);

        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        NetworkModificationNodeInfoEntity nodeEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(nodeUuid);

        UUID result = voltageInitRestService.runVoltageInit(new VariantInfos(networkUuid, variantId), studyEntity.getVoltageInitParametersUuid(),
                new ReportInfos(reportUuid, nodeUuid), rootNetworkUuid, userId, debug,
                new ContextInfos(rootNetwork.getName(), nodeEntity.getName()));

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, VOLTAGE_INITIALIZATION);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
        return result;
    }

    @Transactional(readOnly = true)
    public String getVoltageInitResult(UUID nodeUuid, UUID rootNetworkUuid, String globalFilters) {
        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION);
        return voltageInitRestService.getVoltageInitResult(resultUuid, networkuuid, variantId, globalFilters);
    }

    @Transactional
    public boolean setVoltageInitParameters(UUID studyUuid, StudyVoltageInitParameters parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        var voltageInitParameters = studyEntity.getVoltageInitParameters();
        if (voltageInitParameters == null) {
            var newVoltageInitParameters = new StudyVoltageInitParametersEntity(parameters.isApplyModifications());
            studyEntity.setVoltageInitParameters(newVoltageInitParameters);
        } else {
            voltageInitParameters.setApplyModifications(parameters.isApplyModifications());
        }
        boolean userProfileIssue = createOrUpdateVoltageInitParameters(studyEntity, parameters.getComputationParameters(), userId);
        emitComputationParametersChanged(
                studyUuid,
                userId,
                VOLTAGE_INITIALIZATION,
                List.of(this::invalidateVoltageInitStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS
        );
        return userProfileIssue;
    }

    public void invalidateVoltageInitStatusOnAllNodes(UUID studyUuid) {
        voltageInitRestService.invalidateVoltageInitStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, VOLTAGE_INITIALIZATION));
    }

    public boolean createOrUpdateVoltageInitParameters(StudyEntity studyEntity, VoltageInitParametersInfos parameters, String userId) {
        boolean userProfileIssue = false;
        UUID existingVoltageInitParametersUuid = studyEntity.getVoltageInitParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getVoltageInitParameterId() != null) {
            // reset case, with existing profile, having default voltage init params
            try {
                UUID voltageInitParametersFromProfileUuid = voltageInitRestService.duplicateParameters(userProfileInfos.getVoltageInitParameterId());
                studyEntity.setVoltageInitParametersUuid(voltageInitParametersFromProfileUuid);
                voltageInitRestService.doDeleteComputationParameters(existingVoltageInitParametersUuid, VOLTAGE_INITIALIZATION.getLabel(), LOGGER);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate voltage init parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getVoltageInitParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }

        if (existingVoltageInitParametersUuid == null) {
            existingVoltageInitParametersUuid = voltageInitRestService.createVoltageInitParameters(parameters);
            studyEntity.setVoltageInitParametersUuid(existingVoltageInitParametersUuid);
        } else {
            VoltageInitParametersInfos oldParameters = voltageInitRestService.getVoltageInitParameters(existingVoltageInitParametersUuid);
            if (Objects.isNull(parameters) || !parameters.equals(oldParameters)) {
                voltageInitRestService.updateVoltageInitParameters(existingVoltageInitParametersUuid, parameters);
            }
        }

        return userProfileIssue;
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        return voltageInitRestService.downloadDebugFile(resultUuid);
    }

    public VoltageInitParametersInfos getVoltageInitParametersByUuid(UUID parameterUuid) {
        return voltageInitRestService.getParameters(parameterUuid);
    }

}
