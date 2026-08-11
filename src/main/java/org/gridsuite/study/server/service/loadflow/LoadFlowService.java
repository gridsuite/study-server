/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.loadflow;

import com.powsybl.loadflow.LoadFlowParameters;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
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
                           UserAdminService userAdminService) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService,
            rootNetworkService, computationParametersService, userAdminService);
        this.loadflowRestService = loadflowRestService;
    }

    @Transactional
    public void rerunLoadflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, Boolean withRatioTapChangers, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (networkModificationTreeService.isSecurityNode(nodeUuid)) {
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid,
                InvalidateNodeTreeParameters.builder()
                    .invalidationMode(InvalidateNodeTreeParameters.InvalidationMode.ALL)
                    .computationsInvalidationMode(InvalidateNodeTreeParameters.ComputationsInvalidationMode.PRESERVE_LOAD_FLOW_RESULTS)
                    .build(),
                false);

            networkModificationTreeService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId, RerunLoadFlowInfos.builder()
                .loadflowResultUuid(loadflowResultUuid)
                .withRatioTapChangers(withRatioTapChangers)
                .userId(userId)
                .build());
        } else {
            handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
        }
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
    }

    @Transactional
    public void sendLoadflowRequestWorflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
    }

    @Transactional
    public void sendLoadflowRequest(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (networkModificationTreeService.isSecurityNode(nodeUuid)) {
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.builder()
                .invalidationMode(InvalidateNodeTreeParameters.InvalidationMode.ONLY_CHILDREN_BUILD_STATUS)
                .computationsInvalidationMode(InvalidateNodeTreeParameters.ComputationsInvalidationMode.ALL)
                .build(),
                false);
        }

        handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
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
                rootNetworkNodeInfoService::invalidateSecurityAnalysisStatusOnAllNodes,
                rootNetworkNodeInfoService::invalidateSensitivityAnalysisStatusOnAllNodes,
                rootNetworkNodeInfoService::invalidateDynamicSimulationStatusOnAllNodes,
                rootNetworkNodeInfoService::invalidateDynamicSecurityAnalysisStatusOnAllNodes,
                rootNetworkNodeInfoService::invalidateDynamicMarginCalculationStatusOnAllNodes
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
            Set<NodeEntity> nodesToInvalidate = rootNetworkNodeInfoEntities.stream().map(rootNetworkNodeInfoEntity -> rootNetworkNodeInfoEntity.getNodeInfo().getNode()).collect(Collectors.toSet());
            // since invalidateNodeTree is costly, keep only the highest nodes: they invalidate all their children
            getHighestNodes(nodesToInvalidate).forEach(node ->
                networkModificationTreeService.invalidateNodeTree(studyUuid, node.getIdNode(), rootNetworkUuid, InvalidateNodeTreeParameters.ALL, false));
        });
    }

    private static Set<NodeEntity> getHighestNodes(Set<NodeEntity> nodes) {
        Set<NodeEntity> highestNodes = new HashSet<>(nodes);
        nodes.forEach(node -> {
            NodeEntity currentNode = node.getParentNode();
            while (currentNode != null) {
                if (nodes.contains(currentNode)) {
                    highestNodes.remove(node);
                    break;
                }
                currentNode = currentNode.getParentNode();
            }
        });
        return highestNodes;
    }

    @Transactional(readOnly = true)
    public List<UUID> getNodesInvalidatedByLoadFlowParameters(UUID studyUuid) {
        Set<NodeEntity> nodesWithLoadFlowResults = rootNetworkNodeInfoService.getAllByStudyUuidWithLoadFlowResultsNotNull(studyUuid).stream()
            .map(rootNetworkNodeInfoEntity -> rootNetworkNodeInfoEntity.getNodeInfo().getNode())
            .collect(Collectors.toSet());
        // a row on an ancestor already covers its descendants, since the activity invalidates children
        return getHighestNodes(nodesWithLoadFlowResults).stream().map(NodeEntity::getIdNode).toList();
    }

    private void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        loadflowRestService.invalidateLoadFlowStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, LOAD_FLOW));
    }

    private void handleLoadflowRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId) {
        UUID lfParametersUuid = loadflowRestService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        UUID lfReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(LOAD_FLOW.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        boolean isSecurityNode = networkModificationTreeService.isSecurityNode(nodeUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW, lfReportUuid);
        UUID result = loadflowRestService.runLoadFlow(new NodeReceiver(nodeUuid, rootNetworkUuid), loadflowResultUuid, new VariantInfos(networkUuid, variantId),
            new LoadFlowRestService.ParametersInfos(lfParametersUuid, withRatioTapChangers, isSecurityNode), lfReportUuid, userId);
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, result, withRatioTapChangers);

        handleQuotaStart(userId, result, LOAD_FLOW);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
    }
}
