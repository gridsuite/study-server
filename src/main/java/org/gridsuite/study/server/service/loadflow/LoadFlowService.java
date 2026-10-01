/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.loadflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class LoadFlowService extends AbstractComputationService {
    private final LoadFlowRestService loadflowRestService;
    private final ObjectMapper objectMapper;

    public LoadFlowService(StudyRepository studyRepository,
                           LoadFlowRestService loadflowRestService,
                           NotificationService notificationService,
                           ComputationParametersService computationParametersService,
                           RootNetworkNodeInfoService rootNetworkNodeInfoService,
                           NetworkModificationTreeService networkModificationTreeService,
                           RootNetworkService rootNetworkService,
                           UserAdminService userAdminService,
                           ObjectMapper objectMapper) {
        super(studyRepository, notificationService, networkModificationTreeService, rootNetworkNodeInfoService,
            rootNetworkService, computationParametersService, userAdminService);
        this.loadflowRestService = loadflowRestService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void rerunLoadflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, Boolean withRatioTapChangers, String userId, UUID quotaId) {
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
                .quotaId(quotaId)
                .build());
        } else {
            handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId, quotaId);
        }
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
    }

    @Transactional
    public void sendLoadflowRequestWorflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId, UUID quotaId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId, quotaId);
    }

    @Transactional
    public void sendLoadflowRequest(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId, UUID quotaId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (networkModificationTreeService.isSecurityNode(nodeUuid)) {
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.builder()
                .invalidationMode(InvalidateNodeTreeParameters.InvalidationMode.ONLY_CHILDREN_BUILD_STATUS)
                .computationsInvalidationMode(InvalidateNodeTreeParameters.ComputationsInvalidationMode.ALL)
                .build(),
                false);
        }

        handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId, quotaId);
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

    private String computeDifferences(ObjectNode studyParametersNode,
                                      String referenceLoadflowParameters,
                                      String defaultSpecificLoadflowParameters) {
        try {
            JsonNode referenceParametersNode = objectMapper.readTree(referenceLoadflowParameters);
            JsonNode referenceCommonParametersNode = getCommonParametersNode(referenceParametersNode);
            ObjectNode parametersDifferencesNode = objectMapper.createObjectNode();

            computeCommonParametersDifferences(studyParametersNode.path("commonParameters"), referenceCommonParametersNode, parametersDifferencesNode);
            computeSpecificParametersDifferences(studyParametersNode, defaultSpecificLoadflowParameters, parametersDifferencesNode);

            if (!parametersDifferencesNode.isEmpty()) {
                studyParametersNode.set("parametersDifferences", parametersDifferencesNode);
            }

            return objectMapper.writeValueAsString(studyParametersNode);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void computeCommonParametersDifferences(JsonNode studyCommonParametersNode,
                                                    JsonNode referenceCommonParametersNode,
                                                    ObjectNode parametersDifferencesNode) {
        if (!studyCommonParametersNode.isObject() || !referenceCommonParametersNode.isObject()) {
            return;
        }

        studyCommonParametersNode.properties().iterator().forEachRemaining(entry -> {
            String parameterName = entry.getKey();
            JsonNode studyValue = entry.getValue();
            JsonNode referenceValue = referenceCommonParametersNode.get(parameterName);
            if (!Objects.equals(studyValue, referenceValue)) {
                parametersDifferencesNode.set(parameterName, createDifferenceNode(studyValue, referenceValue));
            }
        });
    }

    private void computeSpecificParametersDifferences(ObjectNode studyParametersNode,
                                                      String defaultSpecificLoadflowParameters,
                                                      ObjectNode parametersDifferencesNode) throws JsonProcessingException {
        JsonNode providerNode = studyParametersNode.get("provider");
        JsonNode studySpecificParametersPerProviderNode = studyParametersNode.get("specificParametersPerProvider");

        if (providerNode == null
            || !providerNode.isTextual()
            || studySpecificParametersPerProviderNode == null
            || !studySpecificParametersPerProviderNode.isObject()) {
            return;
        }

        String provider = providerNode.asText();
        JsonNode studyProviderSpecificParametersNode = studySpecificParametersPerProviderNode.get(provider);

        if (studyProviderSpecificParametersNode == null || !studyProviderSpecificParametersNode.isObject()) {
            return;
        }

        JsonNode defaultSpecificParametersByProviderNode = objectMapper.readTree(defaultSpecificLoadflowParameters);
        JsonNode defaultSpecificParameterDefinitionsNode = defaultSpecificParametersByProviderNode.get(provider);
        if (defaultSpecificParameterDefinitionsNode == null || !defaultSpecificParameterDefinitionsNode.isArray()) {
            return;
        }

        Map<String, JsonNode> defaultValuesByParameterName = new HashMap<>();

        for (JsonNode parameterDefinitionNode : defaultSpecificParameterDefinitionsNode) {
            JsonNode namesNode = parameterDefinitionNode.get("names");

            if (namesNode == null
                || !namesNode.isArray()
                || namesNode.isEmpty()
                || !namesNode.get(0).isTextual()) {
                continue;
            }

            String parameterName = namesNode.get(0).asText();
            JsonNode defaultValue = parameterDefinitionNode.get("defaultValue");
            defaultValuesByParameterName.put(parameterName, defaultValue);
        }

        studyProviderSpecificParametersNode.properties().iterator().forEachRemaining(entry -> {
            String parameterName = entry.getKey();
            JsonNode studyValue = entry.getValue();
            JsonNode defaultValue = defaultValuesByParameterName.get(parameterName);
            if (defaultValue == null) {
                return;
            }
            JsonNode normalizedStudyValue = normalizeSpecificParameterValue(studyValue.asText(), defaultValue);
            if (!Objects.equals(normalizedStudyValue, defaultValue)) {
                parametersDifferencesNode.set(parameterName, createDifferenceNode(studyValue, defaultValue));
            }
        });
    }

    private JsonNode normalizeSpecificParameterValue(String value, JsonNode defaultValue) {
        if (defaultValue == null || defaultValue.isNull()) {
            return value == null
                ? objectMapper.nullNode()
                : objectMapper.valueToTree(value);
        }

        if (defaultValue.isBoolean()) {
            return objectMapper.valueToTree(Boolean.parseBoolean(value));
        }

        if (defaultValue.isInt()) {
            try {
                return objectMapper.valueToTree(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return objectMapper.valueToTree(value);
            }
        }

        if (defaultValue.isLong()) {
            try {
                return objectMapper.valueToTree(Long.parseLong(value));
            } catch (NumberFormatException e) {
                return objectMapper.valueToTree(value);
            }
        }

        if (defaultValue.isFloatingPointNumber()) {
            try {
                return objectMapper.readTree(value);
            } catch (JsonProcessingException e) {
                return objectMapper.valueToTree(value);
            }
        }

        if (defaultValue.isContainerNode()) {
            try {
                return objectMapper.readTree(value);
            } catch (JsonProcessingException e) {
                return objectMapper.valueToTree(value);
            }
        }

        return objectMapper.valueToTree(value);
    }

    private ObjectNode createDifferenceNode(JsonNode value, JsonNode defaultValue) {
        ObjectNode differenceNode = objectMapper.createObjectNode();
        differenceNode.set("value", value);
        differenceNode.set("defaultValue", defaultValue);
        return differenceNode;
    }

    @Transactional
    public String getLoadFlowParametersValues(UUID studyUuid, String userId) {
        // get study loadflow parameters
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID studyLoadFlowParamsUuid = loadflowRestService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        String studyLoadflowParameters = loadflowRestService.getParameters(studyLoadFlowParamsUuid);

        String referenceLoadflowParameters;

        // get reference loadflow parameters from user profile, if defined, or else from default parameters
        UserProfileInfos userProfileInfos = userAdminService.getUserProfile(userId);
        if (userProfileInfos != null && userProfileInfos.getLoadFlowParameterId() != null) {
            UUID referenceLoadFlowParamsUuid = userProfileInfos.getLoadFlowParameterId();
            referenceLoadflowParameters = loadflowRestService.getParameters(referenceLoadFlowParamsUuid);
        } else {
            referenceLoadflowParameters = loadflowRestService.getDefaultValues();
        }

        try {
            // get reference specific loadflow parameters for the provider
            ObjectNode studyParametersNode = (ObjectNode) objectMapper.readTree(studyLoadflowParameters);
            String provider = studyParametersNode.path("provider").asText();
            String referenceSpecificLoadflowParameters = loadflowRestService.getSpecificParameters(provider);

            // compute all the differencs between study and reference loadflow parameters
            return computeDifferences(studyParametersNode, referenceLoadflowParameters, referenceSpecificLoadflowParameters);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode getCommonParametersNode(JsonNode referenceParametersNode) {
        JsonNode commonParametersNode = referenceParametersNode.get("commonParameters");
        return commonParametersNode != null ? commonParametersNode : referenceParametersNode;
    }

    @Transactional
    public LoadFlowParameters getCommonParameters(StudyEntity studyEntity) {
        UUID loadFlowParamsUuid = loadflowRestService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        return loadflowRestService.getCommonParameters(loadFlowParamsUuid);
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

    public String getCommonParameters(UUID parameterUuid) {
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

    private void handleLoadflowRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId, UUID quotaId) {
        UUID lfParametersUuid = loadflowRestService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        UUID lfReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(LOAD_FLOW.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        boolean isSecurityNode = networkModificationTreeService.isSecurityNode(nodeUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW, lfReportUuid);
        UUID result = loadflowRestService.runLoadFlow(new NodeReceiver(nodeUuid, rootNetworkUuid), loadflowResultUuid, new VariantInfos(networkUuid, variantId),
            new LoadFlowRestService.ParametersInfos(lfParametersUuid, withRatioTapChangers, isSecurityNode), lfReportUuid, userId);
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, result, withRatioTapChangers);

        handleQuotaStart(result, quotaId);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
        notificationService.emitElementUpdated(studyEntity.getId(), userId);
    }
}
