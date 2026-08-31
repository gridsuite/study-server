/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.shortcircuit;

import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.asymmetricalload.AsymmetricalLoadService;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.study.server.dto.ComputationType.SHORT_CIRCUIT;
import static org.gridsuite.study.server.dto.ComputationType.SHORT_CIRCUIT_ONE_BUS;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class ShortCircuitService extends AbstractComputationService {
    private final ShortCircuitRestService shortCircuitRestService;
    private final AsymmetricalLoadService asymmetricalLoadService;

    protected ShortCircuitService(StudyRepository studyRepository,
                                  ComputationParametersService computationParametersService,
                                  NotificationService notificationService,
                                  RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                  ShortCircuitRestService shortCircuitServicerRest,
                                  NetworkModificationTreeService networkModificationTreeService,
                                  UserAdminService userAdminService,
                                  RootNetworkService rootNetworkService,
                                  AsymmetricalLoadService asymmetricalLoadService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService,
            rootNetworkService, computationParametersService, userAdminService);
        this.shortCircuitRestService = shortCircuitServicerRest;
        this.asymmetricalLoadService = asymmetricalLoadService;
    }

    @Transactional
    public String getShortCircuitParametersInfo(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (studyEntity.getShortCircuitParametersUuid() == null) {
            studyEntity.setShortCircuitParametersUuid(shortCircuitRestService.createParameters(null));
            studyRepository.save(studyEntity);
        }
        return shortCircuitRestService.getParameters(studyEntity.getShortCircuitParametersUuid());
    }

    @Transactional
    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Optional<String> busId, boolean debug, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);

        UUID result = handleShortCircuitRequest(studyEntity, nodeUuid, rootNetworkUuid, busId, debug, userId);

        userAdminService.startOperationWithQuota(userId, QuotaType.mapFromComputationType(SHORT_CIRCUIT), result);
        return result;
    }

    private UUID handleShortCircuitRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, Optional<String> busId, boolean debug, String userId) {
        ComputationType computationType = busId.isEmpty() ? SHORT_CIRCUIT : SHORT_CIRCUIT_ONE_BUS;
        UUID shortCircuitResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, computationType);
        if (shortCircuitResultUuid != null) {
            shortCircuitRestService.deleteShortCircuitAnalysisResults(List.of(shortCircuitResultUuid));
        }
        UUID scReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(computationType.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, computationType, scReportUuid);
        final UUID result = shortCircuitRestService.runShortCircuit(rootNetworkUuid, new VariantInfos(networkUuid, variantId), busId.orElse(null), studyEntity.getShortCircuitParametersUuid(),
                new ReportInfos(scReportUuid, nodeUuid), userId, debug);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, computationType);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid,
                busId.isEmpty() ? NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS : NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
        return result;
    }

    @Transactional
    public boolean setShortCircuitParameters(UUID studyUuid, @Nullable String shortCircuitParametersInfos, String userId) {
        return setComputationParameters(
                studyUuid,
                shortCircuitParametersInfos,
                userId,
                StudyEntity::getShortCircuitParametersUuid,
                StudyEntity::setShortCircuitParametersUuid,
                UserProfileInfos::getShortcircuitParameterId,
                shortCircuitRestService,
                shortCircuitRestService::createParameters,
                shortCircuitRestService::updateParameters,
                SHORT_CIRCUIT,
                List.of(this::invalidateShortCircuitStatusOnAllNodes,
                        rootNetworkNodeInfoService::invalidatePccMinStatusOnAllNodes,
                        asymmetricalLoadService::invalidateAsymmetricalLoadStatusOnAllNodes),
                NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS,
                NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS,
                NotificationService.UPDATE_TYPE_PCC_MIN_STATUS
        );
    }

    public void invalidateShortCircuitStatusOnAllNodes(UUID studyUuid) {
        shortCircuitRestService.invalidateShortCircuitStatus(Stream.concat(
                rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SHORT_CIRCUIT).stream(),
                rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SHORT_CIRCUIT_ONE_BUS).stream()
        ).toList());
    }

    public String getParameters(UUID parameterUuid) {
        return shortCircuitRestService.getParameters(parameterUuid);
    }

    public void updateParameters(UUID parameterUuid, String parameters) {
        shortCircuitRestService.updateParameters(parameterUuid, parameters);
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        return shortCircuitRestService.downloadDebugFile(resultUuid);
    }

    public String getSpecificParameters() {
        return shortCircuitRestService.getSpecificParameters();
    }

}
