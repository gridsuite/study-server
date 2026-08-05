/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.pccmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.PCC_MIN;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class PccMinService extends AbstractComputationService {
    private final PccMinRestService pccMinRestService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final UserAdminService userAdminService;
    private final ObjectMapper objectMapper;
    private final RootNetworkService rootNetworkService;
    private static final Logger LOGGER = LoggerFactory.getLogger(PccMinService.class);

    protected PccMinService(StudyRepository studyRepository,
                            ComputationParametersService computationParametersService,
                            NotificationService notificationService,
                            RootNetworkNodeInfoService rootNetworkNodeInfoService,
                            PccMinRestService pccMinRestService,
                            NetworkModificationTreeService networkModificationTreeService,
                            UserAdminService userAdminService,
                            ObjectMapper objectMapper,
                            RootNetworkService rootNetworkService) {
        super(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService);
        this.pccMinRestService = pccMinRestService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.userAdminService = userAdminService;
        this.objectMapper = objectMapper;
        this.rootNetworkService = rootNetworkService;
    }

    @Transactional
    public String getPccMinParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return pccMinRestService.getPccMinParameters(pccMinRestService.getPccMinParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public UUID runPccMin(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        UUID result = handlePccMinRequest(studyEntity, nodeUuid, rootNetworkUuid, userId);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(PCC_MIN), result);
        return result;
    }

    private UUID handlePccMinRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(PCC_MIN.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, PCC_MIN, reportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, PCC_MIN);
        if (prevResultUuid != null) {
            pccMinRestService.deletePccMinResults(List.of(prevResultUuid));
        }
        var runPccMinParametersInfos = new RunPccMinParametersInfos(studyEntity.getShortCircuitParametersUuid(), studyEntity.getPccMinParametersUuid(), null);

        UUID result = pccMinRestService.runPccMin(networkUuid, variantId, runPccMinParametersInfos, new ReportInfos(reportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, PCC_MIN);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
        return result;
    }

    @Transactional
    public boolean setPccMinParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = createOrUpdatePccMinParameters(studyEntity, parameters, userId);

        invalidatePccMinStatusOnAllNodes(studyEntity.getId());
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, PCC_MIN);
        return userProfileIssue;
    }

    public boolean createOrUpdatePccMinParameters(StudyEntity studyEntity, String parameters, String userId) {
        UUID existingPccMinParametersUuid = studyEntity.getPccMinParametersUuid();
        boolean userProfileIssue = false;

        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getPccMinParameterId() != null) {
            // reset case, with existing profile, having default pcc min params
            try {
                UUID pccMinParametersFromProfileUuid = pccMinRestService.duplicateParameters(userProfileInfos.getPccMinParameterId());
                studyEntity.setPccMinParametersUuid(pccMinParametersFromProfileUuid);
                pccMinRestService.doDeleteComputationParameters(existingPccMinParametersUuid, PCC_MIN.getLabel(), LOGGER);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate pcc min parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getPccMinParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }
        if (existingPccMinParametersUuid == null) {
            existingPccMinParametersUuid = pccMinRestService.createPccMinParameters(parameters);
            studyEntity.setPccMinParametersUuid(existingPccMinParametersUuid);
        } else {
            pccMinRestService.updatePccMinParameters(existingPccMinParametersUuid, parameters);
        }
        return userProfileIssue;
    }

    public void invalidatePccMinStatusOnAllNodes(UUID studyUuid) {
        pccMinRestService.invalidatePccMinStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, PCC_MIN));
    }

}
