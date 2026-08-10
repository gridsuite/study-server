/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.loadflow;

import com.powsybl.loadflow.LoadFlowParameters;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationRestService;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisRestService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationRestService;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisRestService;
import org.gridsuite.study.server.service.sensitivityanalysis.SensitivityAnalysisRestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class LoadFlowService extends AbstractComputationService {
    private final LoadFlowRestService loadflowRestService;

    public LoadFlowService(StudyRepository studyRepository,
                           LoadFlowRestService loadflowRestService,
                           NotificationService notificationService,
                           ComputationParametersService computationParametersService,
                           RootNetworkNodeInfoService rootNetworkNodeInfoService,
                           NetworkModificationTreeService networkModificationTreeService,
                           RootNetworkService rootNetworkService,
                           SecurityAnalysisRestService securityAnalysisRestService,
                           SensitivityAnalysisRestService sensitivityAnalysisRestService,
                           DynamicSimulationRestService dynamicSimulationRestService,
                           DynamicSecurityAnalysisRestService dynamicSecurityAnalysisRestService,
                           DynamicMarginCalculationRestService dynamicMarginCalculationRestService,
                           UserAdminService userAdminService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService,
            rootNetworkService, computationParametersService, userAdminService);
        this.loadflowRestService = loadflowRestService;
        this.securityAnalysisRestService = securityAnalysisRestService;
        this.sensitivityAnalysisRestService = sensitivityAnalysisRestService;
        this.dynamicSimulationRestService = dynamicSimulationRestService;
        this.dynamicSecurityAnalysisRestService = dynamicSecurityAnalysisRestService;
        this.dynamicMarginCalculationRestService = dynamicMarginCalculationRestService;
    }

    @Transactional
    public UUID getLoadFlowParametersId(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowRestService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
    }

    @Transactional(readOnly = true)
    public String getLoadFlowProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowRestService.getLoadFlowProvider(studyEntity.getLoadFlowParametersUuid());
    }

    @Transactional
    public LoadFlowParametersInfos getLoadFlowParametersInfos(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return getLoadFlowParametersInfos(studyEntity);
    }

    private LoadFlowParametersInfos getLoadFlowParametersInfos(StudyEntity studyEntity) {
        UUID loadFlowParamsUuid = loadflowRestService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        return loadflowRestService.getLoadFlowParameters(loadFlowParamsUuid);
    }

    @Transactional
    public LoadFlowParameters getLoadFlowParameters(StudyEntity studyEntity) {
        LoadFlowParametersInfos lfParameters = getLoadFlowParametersInfos(studyEntity);
        return lfParameters.getCommonParameters();
    }

    @Transactional
    public void deleteLoadflowResult(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid) {
        loadflowRestService.deleteLoadFlowResults(List.of(loadflowResultUuid));
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, null, null);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
    }

    @Transactional
    public UUID createLoadflowRunningStatus(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean withRatioTapChangers) {
        // since invalidating and building nodes can be long, we create loadflow result status before execution long operations
        UUID loadflowResultUuid = loadflowRestService.createRunningStatus();
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
        return loadflowResultUuid;
    }

    public String getProviders() {
        return loadflowRestService.getProviders();
    }

    public String getSpecificParameters() {
        return loadflowRestService.getSpecificParameters();
    }

    public String getDefaultLimitReductions() {
        return loadflowRestService.getDefaultLimitReductions();
    }

    public LoadFlowParametersInfos getLoadFlowParameters(UUID parameterUuid) {
        return loadflowRestService.getParameters(parameterUuid);
    }

    public void updateLoadFlowParameters(UUID parameterUuid, String parameters) {
        loadflowRestService.updateParameters(parameterUuid, parameters);
    }

    @Transactional
    public boolean setLoadFlowParameters(UUID studyUuid, String parameters, String userId) {
        return setComputationParameters(
            studyUuid,
            parameters,
            userId,
            StudyEntity::getLoadFlowParametersUuid,
            StudyEntity::setLoadFlowParametersUuid,
            UserProfileInfos::getLoadFlowParameterId,
            loadflowRestService,
            loadflowRestService::createLoadFlowParameters,
            loadflowRestService::updateLoadFlowParameters,
            LOAD_FLOW,
            List.of(
                this::invalidateAllStudyLoadFlowStatus,
                this::invalidateSecurityAnalysisStatusOnAllNodes,
                this::invalidateSensitivityAnalysisStatusOnAllNodes,
                this::invalidateDynamicSimulationStatusOnAllNodes,
                this::invalidateDynamicSecurityAnalysisStatusOnAllNodes,
                this::invalidateDynamicMarginCalculationStatusOnAllNodes
            ),
            NotificationService.UPDATE_TYPE_LOADFLOW_STATUS,
            NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS,
            NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS,
            NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS,
            NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS,
            NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS
        );
    }

    public void invalidateAllStudyLoadFlowStatus(UUID studyUuid) {
        invalidateSecurityNodeTreeWithLoadFlowResults(studyUuid);
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
    }

    private void invalidateSecurityNodeTreeWithLoadFlowResults(UUID studyUuid) {
        Map<UUID, List<RootNetworkNodeInfoEntity>> rootNetworkNodeInfosWithLFByRootNetwork = rootNetworkNodeInfoService.getAllByStudyUuidWithLoadFlowResultsNotNull(studyUuid).stream()
            .collect(Collectors.groupingBy(rootNetworkNodeInfoEntity -> rootNetworkNodeInfoEntity.getRootNetwork().getId()));

        rootNetworkNodeInfosWithLFByRootNetwork.forEach((rootNetworkUuid, rootNetworkNodeInfoEntities) -> {
            // since invalidateNodeTree is costly, optimise node tree invalidation by keeping only least deep parents from the set to invalidate them and all their children
            Set<NodeEntity> nodesToInvalidate = rootNetworkNodeInfoEntities.stream().map(rootNetworkNodeInfoEntity -> rootNetworkNodeInfoEntity.getNodeInfo().getNode()).collect(Collectors.toSet());
            Set<NodeEntity> nodeTreesToInvalidate = new HashSet<>(nodesToInvalidate);

            nodesToInvalidate.forEach(node -> {
                NodeEntity currentNode = node.getParentNode();
                while (currentNode != null) {
                    if (nodesToInvalidate.contains(currentNode)) {
                        nodeTreesToInvalidate.remove(node);
                        break;
                    }
                    currentNode = currentNode.getParentNode();
                }
            });

            nodeTreesToInvalidate.forEach(node -> networkModificationTreeService.invalidateNodeTree(studyUuid, node.getIdNode(), rootNetworkUuid, InvalidateNodeTreeParameters.ALL, false));
        });
    }

    private void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        loadflowRestService.invalidateLoadFlowStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, LOAD_FLOW));
    }
}
