/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.securityanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.SECURITY_ANALYSIS;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class SecurityAnalysisService extends AbstractComputationService {

    private final SecurityAnalysisRestService securityAnalysisRestService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final ObjectMapper objectMapper;
    private final RootNetworkService rootNetworkService;
    private final UserAdminService userAdminService;

    public SecurityAnalysisService(StudyRepository studyRepository,
                                   ComputationParametersService computationParametersService,
                                   NotificationService notificationService,
                                   SecurityAnalysisRestService securityAnalysisRestService,
                                   NetworkModificationTreeService networkModificationTreeService,
                                   ObjectMapper objectMapper,
                                   RootNetworkService rootNetworkService,
                                   RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                   UserAdminService userAdminService) {
        super(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService);
        this.securityAnalysisRestService = securityAnalysisRestService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.rootNetworkService = rootNetworkService;
        this.userAdminService = userAdminService;
    }

    @Transactional
    public String getSecurityAnalysisParametersValues(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return securityAnalysisRestService.getSecurityAnalysisParameters(securityAnalysisRestService.getSecurityAnalysisParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public UUID runSecurityAnalysis(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        UUID result = handleSecurityAnalysisRequest(study, nodeUuid, rootNetworkUuid, userId);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(SECURITY_ANALYSIS), result);
        return result;
    }

    private UUID handleSecurityAnalysisRequest(StudyEntity study, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID saReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(SECURITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, SECURITY_ANALYSIS, saReportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, SECURITY_ANALYSIS);
        if (prevResultUuid != null) {
            securityAnalysisRestService.deleteSecurityAnalysisResults(List.of(prevResultUuid));
        }

        var runSecurityAnalysisParametersInfos = new RunSecurityAnalysisParametersInfos(study.getSecurityAnalysisParametersUuid(), study.getLoadFlowParametersUuid());
        UUID result = securityAnalysisRestService.runSecurityAnalysis(networkUuid, variantId, runSecurityAnalysisParametersInfos,
                new ReportInfos(saReportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, SECURITY_ANALYSIS);
        notificationService.emitStudyChanged(study.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(study.getId(), userId);
        return result;
    }

    @Transactional
    public boolean setSecurityAnalysisParametersValues(UUID studyUuid, String parameters, String userId) {
        return setComputationParameters(
                studyUuid,
                parameters,
                userId,
                StudyEntity::getSecurityAnalysisParametersUuid,
                StudyEntity::setSecurityAnalysisParametersUuid,
                UserProfileInfos::getSecurityAnalysisParameterId,
                securityAnalysisRestService,
                securityAnalysisRestService::createSecurityAnalysisParameters,
                securityAnalysisRestService::updateSecurityAnalysisParameters,
                SECURITY_ANALYSIS,
                List.of(this::invalidateSecurityAnalysisStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS
        );
    }

    public void invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        securityAnalysisRestService.invalidateSaStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SECURITY_ANALYSIS));
    }

}
