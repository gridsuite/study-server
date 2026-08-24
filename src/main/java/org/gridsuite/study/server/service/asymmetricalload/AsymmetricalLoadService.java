/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.asymmetricalload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.RunAsymmetricalLoadParametersInfos;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.common.AbstractComputationService;
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

import static org.gridsuite.study.server.dto.ComputationType.ASYMMETRICAL_LOAD;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@Service
public class AsymmetricalLoadService extends AbstractComputationService {

    private final AsymmetricalLoadRestService asymmetricalLoadRestService;
    private final ObjectMapper objectMapper;
    private static final Logger LOGGER = LoggerFactory.getLogger(AsymmetricalLoadService.class);

    protected AsymmetricalLoadService(StudyRepository studyRepository,
                                      ComputationParametersService computationParametersService,
                                      NotificationService notificationService,
                                      RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                      AsymmetricalLoadRestService asymmetricalLoadRestService,
                                      NetworkModificationTreeService networkModificationTreeService,
                                      UserAdminService userAdminService,
                                      ObjectMapper objectMapper,
                                      RootNetworkService rootNetworkService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService, rootNetworkService,
            computationParametersService, userAdminService);
        this.asymmetricalLoadRestService = asymmetricalLoadRestService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID runAsymmetricalLoad(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleAsymmetricalLoadRequest(studyEntity, nodeUuid, rootNetworkUuid, userId);
    }

    private UUID handleAsymmetricalLoadRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(ASYMMETRICAL_LOAD.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, ASYMMETRICAL_LOAD, reportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ASYMMETRICAL_LOAD);
        if (prevResultUuid != null) {
            asymmetricalLoadRestService.deleteAsymmetricalLoadResults(List.of(prevResultUuid));
        }
        var runAsymmetricalLoadParametersInfos = new RunAsymmetricalLoadParametersInfos(studyEntity.getShortCircuitParametersUuid(), studyEntity.getAsymmetricalLoadParametersUuid(), null);

        UUID result = asymmetricalLoadRestService.runAsymmetricalLoad(networkUuid, variantId, runAsymmetricalLoadParametersInfos, new ReportInfos(reportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, ASYMMETRICAL_LOAD);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
        return result;
    }

    @Transactional
    public String getAsymmetricalLoadParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return asymmetricalLoadRestService.getAsymmetricalLoadParameters(asymmetricalLoadRestService.getAsymmetricalLoadParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public boolean setAsymmetricalLoadParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = createOrUpdateAsymmetricalLoadParameters(studyEntity, parameters, userId);

        invalidateAsymmetricalLoadStatusOnAllNodes(studyEntity.getId());
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, ASYMMETRICAL_LOAD);
        return userProfileIssue;
    }

    public boolean createOrUpdateAsymmetricalLoadParameters(StudyEntity studyEntity, String parameters, String userId) {
        UUID existingAsymmetricalLoadParametersUuid = studyEntity.getAsymmetricalLoadParametersUuid();
        boolean userProfileIssue = false;

        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getAsymmetricalLoadParameterId() != null) {
            // reset case, with existing profile, having default asymmetrical load params
            try {
                UUID asymmetricalLoadParametersFromProfileUuid = asymmetricalLoadRestService.duplicateParameters(userProfileInfos.getAsymmetricalLoadParameterId());
                studyEntity.setAsymmetricalLoadParametersUuid(asymmetricalLoadParametersFromProfileUuid);
                asymmetricalLoadRestService.doDeleteComputationParameters(existingAsymmetricalLoadParametersUuid, ASYMMETRICAL_LOAD.getLabel(), LOGGER);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate asymmetrical load parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getAsymmetricalLoadParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }
        if (existingAsymmetricalLoadParametersUuid == null) {
            existingAsymmetricalLoadParametersUuid = asymmetricalLoadRestService.createAsymmetricalLoadParameters(parameters);
            studyEntity.setAsymmetricalLoadParametersUuid(existingAsymmetricalLoadParametersUuid);
        } else {
            asymmetricalLoadRestService.updateAsymmetricalLoadParameters(existingAsymmetricalLoadParametersUuid, parameters);
        }
        return userProfileIssue;
    }

    public void invalidateAsymmetricalLoadStatusOnAllNodes(UUID studyUuid) {
        asymmetricalLoadRestService.invalidateAsymmetricalLoadStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, ASYMMETRICAL_LOAD));
    }
}
