/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.logicalcontrols;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.LOGICAL_CONTROLS;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class LogicalControlsService extends AbstractComputationService {

    private final LogicalControlsRestService logicalControlsRestService;
    private final ObjectMapper objectMapper;

    protected LogicalControlsService(StudyRepository studyRepository,
                                     ComputationParametersService computationParametersService,
                                     NotificationService notificationService,
                                     RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                     LogicalControlsRestService logicalControlsRestService,
                                     NetworkModificationTreeService networkModificationTreeService,
                                     UserAdminService userAdminService,
                                     ObjectMapper objectMapper,
                                     RootNetworkService rootNetworkService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService, rootNetworkService,
            computationParametersService, userAdminService);
        this.logicalControlsRestService = logicalControlsRestService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID runLogicalControls(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(LOGICAL_CONTROLS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, LOGICAL_CONTROLS, reportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOGICAL_CONTROLS);
        if (prevResultUuid != null) {
            logicalControlsRestService.deleteLogicalControlsResults(List.of(prevResultUuid));
        }

        UUID result = logicalControlsRestService.runLogicalControls(networkUuid, variantId, studyEntity.getStateEstimationParametersUuid(), new ReportInfos(reportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, LOGICAL_CONTROLS);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_LOGICAL_CONTROLS_STATUS);
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
        return result;
    }
}
