/**
 * Copyright (c) 20226, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.loadflow;

import com.powsybl.loadflow.LoadFlowParameters;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.AbstractComputationService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class LoadFlowService extends AbstractComputationService {
    private final LoadFlowServiceRestRest loadflowServiceRest;
    private final NotificationService notificationService;
    public final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    public LoadFlowService(StudyRepository studyRepository,
                           LoadFlowServiceRestRest loadflowServiceRest,
                           NotificationService notificationService, RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        super(studyRepository);
        this.loadflowServiceRest = loadflowServiceRest;
        this.notificationService = notificationService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    @Transactional
    public UUID getLoadFlowParametersId(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowServiceRest.getLoadFlowParametersOrDefaultsUuid(studyEntity);
    }

    @Transactional(readOnly = true)
    public String getLoadFlowProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowServiceRest.getLoadFlowProvider(studyEntity.getLoadFlowParametersUuid());
    }

    @Transactional
    public LoadFlowParametersInfos getLoadFlowParametersInfos(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return getLoadFlowParametersInfos(studyEntity);
    }

    private LoadFlowParametersInfos getLoadFlowParametersInfos(StudyEntity studyEntity) {
        UUID loadFlowParamsUuid = loadflowServiceRest.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        return loadflowServiceRest.getLoadFlowParameters(loadFlowParamsUuid);
    }

    @Transactional
    public LoadFlowParameters getLoadFlowParameters(StudyEntity studyEntity) {
        LoadFlowParametersInfos lfParameters = getLoadFlowParametersInfos(studyEntity);
        return lfParameters.getCommonParameters();
    }

    @Transactional
    public void deleteLoadflowResult(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid) {
        loadflowServiceRest.deleteLoadFlowResults(List.of(loadflowResultUuid));
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, null, null);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
    }

    @Transactional
    public UUID createLoadflowRunningStatus(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean withRatioTapChangers) {
        // since invalidating and building nodes can be long, we create loadflow result status before execution long operations
        UUID loadflowResultUuid = loadflowServiceRest.createRunningStatus();
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
        return loadflowResultUuid;
    }
}
