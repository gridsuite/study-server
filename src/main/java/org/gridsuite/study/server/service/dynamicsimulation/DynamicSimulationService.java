/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.dynamicsimulation;

import lombok.NonNull;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
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
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SIMULATION;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class DynamicSimulationService extends AbstractComputationService {
    private final DynamicSimulationRestService dynamicSimulationRestService;
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;
    private final DynamicSimulationEventService dynamicSimulationEventService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final UserAdminService userAdminService;
    private final RootNetworkService rootNetworkService;

    protected DynamicSimulationService(StudyRepository studyRepository,
                                       ComputationParametersService computationParametersService,
                                       NotificationService notificationService,
                                       RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                       DynamicSimulationRestService dynamicSimulationRestService,
                                       DynamicSecurityAnalysisService dynamicSecurityAnalysisService,
                                       DynamicSimulationEventService dynamicSimulationEventService,
                                       NetworkModificationTreeService networkModificationTreeService,
                                       UserAdminService userAdminService,
                                       RootNetworkService rootNetworkService) {
        super(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService);
        this.dynamicSimulationRestService = dynamicSimulationRestService;
        this.dynamicSecurityAnalysisService = dynamicSecurityAnalysisService;
        this.dynamicSimulationEventService = dynamicSimulationEventService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.userAdminService = userAdminService;
        this.rootNetworkService = rootNetworkService;
    }

    @Transactional
    public String getDynamicSimulationParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicSimulationRestService.getParameters(
                dynamicSimulationRestService.getDynamicSimulationParametersUuidOrElseCreateDefault(studyEntity));
    }

    @Transactional
    public boolean setDynamicSimulationParameters(UUID studyUuid, String dsParameter, String userId) {
        return setComputationParameters(
                studyUuid,
                dsParameter,
                userId,
                StudyEntity::getDynamicSimulationParametersUuid,
                StudyEntity::setDynamicSimulationParametersUuid,
                UserProfileInfos::getDynamicSimulationParameterId,
                dynamicSimulationRestService,
                dynamicSimulationRestService::createParameters,
                dynamicSimulationRestService::updateParameters,
                DYNAMIC_SIMULATION,
                List.of(this::invalidateDynamicSimulationStatusOnAllNodes,
                        dynamicSecurityAnalysisService::invalidateDynamicSecurityAnalysisStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS,
                NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS
        );
    }

    public void invalidateDynamicSimulationStatusOnAllNodes(UUID studyUuid) {
        dynamicSimulationRestService.invalidateStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, DYNAMIC_SIMULATION));
    }

    public String getDynamicSimulationProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicSimulationRestService.getProvider(studyEntity.getDynamicSimulationParametersUuid());
    }

    @Transactional
    public UUID runDynamicSimulation(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid,
                                     String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        UUID result = handleDynamicSimulationRequest(studyEntity, nodeUuid, rootNetworkUuid, debug, userId);

        QuotaType quotaType = QuotaType.mapFromComputationType(DYNAMIC_SIMULATION);
        userAdminService.startOperationWithQuota(userId, quotaType, result);
        notificationService.emitQuotaChange(userId, quotaType);
        return result;
    }

    private UUID handleDynamicSimulationRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, boolean debug, String userId) {
        // pre-condition check
        if (!rootNetworkNodeInfoService.isLoadflowConverged(nodeUuid, rootNetworkUuid)) {
            throw new StudyException(NOT_ALLOWED, "Load flow must run successfully before running dynamic simulation");
        }

        // clean previous result if exist
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SIMULATION);
        if (prevResultUuid != null) {
            dynamicSimulationRestService.deleteResults(List.of(prevResultUuid));
        }

        // get dynamic simulation result uuid
        UUID dynamicSimulationParametersUuid = studyEntity.getDynamicSimulationParametersUuid();

        // load configured events persisted in the study server DB
        List<EventInfos> events = dynamicSimulationEventService.getEventsByNodeId(nodeUuid);

        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(DYNAMIC_SIMULATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SIMULATION, reportUuid);

        // launch dynamic simulation
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID dynamicSimulationResultUuid = dynamicSimulationRestService.runDynamicSimulation(nodeUuid, rootNetworkUuid,
                networkUuid, variantId, reportUuid, dynamicSimulationParametersUuid, events, userId, debug);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicSimulationResultUuid, DYNAMIC_SIMULATION);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);

        return dynamicSimulationResultUuid;
    }

    @Transactional(readOnly = true)
    public List<EventInfos> getDynamicSimulationEvents(UUID nodeUuid) {
        return dynamicSimulationEventService.getEventsByNodeId(nodeUuid);
    }

    @Transactional(readOnly = true)
    public EventInfos getDynamicSimulationEvent(UUID nodeUuid, String equipmentId) {
        return dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(nodeUuid, equipmentId);
    }

    @Transactional
    public void createDynamicSimulationEvent(UUID studyUuid, UUID nodeUuid, String userId, EventInfos event) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartEventCrudNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.EVENTS_CRUD_CREATING_IN_PROGRESS);
        try {
            dynamicSimulationEventService.saveEvent(nodeUuid, event);
        } finally {
            notificationService.emitEndEventCrudNotification(studyUuid, nodeUuid, childrenUuids);
        }
        postProcessEventCrud(studyUuid, nodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void postProcessEventCrud(UUID studyUuid, UUID nodeUuid) {
        // for delete old result and refresh dynamic simulation run button in UI
        invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    @Transactional
    public void updateDynamicSimulationEvent(UUID studyUuid, UUID nodeUuid, String userId, EventInfos event) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartEventCrudNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.EVENTS_CRUD_UPDATING_IN_PROGRESS);
        try {
            dynamicSimulationEventService.saveEvent(nodeUuid, event);
        } finally {
            notificationService.emitEndEventCrudNotification(studyUuid, nodeUuid, childrenUuids);
        }
        postProcessEventCrud(studyUuid, nodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void deleteDynamicSimulationEvents(UUID studyUuid, UUID nodeUuid, String userId, List<UUID> eventUuids) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartEventCrudNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.EVENTS_CRUD_DELETING_IN_PROGRESS);
        try {
            dynamicSimulationEventService.deleteEvents(eventUuids);
        } finally {
            notificationService.emitEndEventCrudNotification(studyUuid, nodeUuid, childrenUuids);
        }
        postProcessEventCrud(studyUuid, nodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public String getParameters(UUID parametersUuid) {
        return dynamicSimulationRestService.getParameters(parametersUuid);
    }

    public String getProviders() {
        return dynamicSimulationRestService.getProviders();
    }

    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        dynamicSimulationRestService.updateParameters(parametersUuid, parametersInfos);
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        return dynamicSimulationRestService.downloadDebugFile(resultUuid);
    }
}
