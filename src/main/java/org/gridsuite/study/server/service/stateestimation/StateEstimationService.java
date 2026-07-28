package org.gridsuite.study.server.service.stateestimation;

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

import static org.gridsuite.study.server.dto.ComputationType.STATE_ESTIMATION;

@Service
public class StateEstimationService extends AbstractComputationService {
    private final NetworkModificationTreeService networkModificationTreeService;
    private final RootNetworkService rootNetworkService;
    private final StateEstimationRestService stateEstimationRestService;
    private final UserAdminService userAdminService;
    private final ObjectMapper objectMapper;

    protected StateEstimationService(StudyRepository studyRepository,
                                     ComputationParametersService computationParametersService,
                                     NotificationService notificationService,
                                     RootNetworkNodeInfoService rootNetworkNodeInfoService, NetworkModificationTreeService networkModificationTreeService,
                                     RootNetworkService rootNetworkService,
                                     StateEstimationRestService stateEstimationRestService,
                                     UserAdminService userAdminService,
                                     ObjectMapper objectMapper) {
        super(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService);
        this.networkModificationTreeService = networkModificationTreeService;
        this.rootNetworkService = rootNetworkService;
        this.stateEstimationRestService = stateEstimationRestService;
        this.userAdminService = userAdminService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID runStateEstimation(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        UUID result = handleStateEstimationRequest(studyEntity, nodeUuid, rootNetworkUuid, userId, debug);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(STATE_ESTIMATION), result);
        return result;
    }

    private UUID handleStateEstimationRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, String userId, boolean debug) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(STATE_ESTIMATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, STATE_ESTIMATION, reportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = getRootNetworkNodeInfoService().getComputationResultUuid(nodeUuid, rootNetworkUuid, STATE_ESTIMATION);
        if (prevResultUuid != null) {
            stateEstimationRestService.deleteStateEstimationResults(List.of(prevResultUuid));
        }

        UUID result = stateEstimationRestService.runStateEstimation(networkUuid, variantId, studyEntity.getStateEstimationParametersUuid(),
                new ReportInfos(reportUuid, nodeUuid), receiver, userId, debug);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, STATE_ESTIMATION);
        getNotificationService().emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        getNotificationService().emitElementUpdated(studyEntity.getId(), userId);

        return result;
    }

    @Transactional
    public String getStateEstimationParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return stateEstimationRestService.getStateEstimationParameters(stateEstimationRestService.getStateEstimationParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public void setStateEstimationParametersValues(UUID studyUuid, String parameters, String userId) {
        setComputationParameters(
                studyUuid,
                parameters,
                userId,
                StudyEntity::getStateEstimationParametersUuid,
                StudyEntity::setStateEstimationParametersUuid,
                stateEstimationRestService::createStateEstimationParameters,
                stateEstimationRestService::updateStateEstimationParameters,
                STATE_ESTIMATION,
                List.of(this::invalidateStateEstimationStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS
        );
    }

    private void invalidateStateEstimationStatusOnAllNodes(UUID studyUuid) {
        stateEstimationRestService.invalidateStateEstimationStatus(getRootNetworkNodeInfoService().getComputationResultUuids(studyUuid, STATE_ESTIMATION));
    }
}
