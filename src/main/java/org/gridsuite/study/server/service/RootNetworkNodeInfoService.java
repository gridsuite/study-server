/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeBuildStatusEmbeddable;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyException.Type.NOT_ALLOWED;
import static org.gridsuite.study.server.StudyException.Type.ROOTNETWORK_NOT_FOUND;
import static org.gridsuite.study.server.dto.ComputationType.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class RootNetworkNodeInfoService {
    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    private final StudyServerExecutionService studyServerExecutionService;
    private final LoadFlowService loadFlowService;
    private final SecurityAnalysisService securityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final NonEvacuatedEnergyService nonEvacuatedEnergyService;
    private final ShortCircuitService shortCircuitService;
    private final VoltageInitService voltageInitService;
    private final DynamicSimulationService dynamicSimulationService;
    private final StateEstimationService stateEstimationService;
    private final ReportService reportService;

    public RootNetworkNodeInfoService(RootNetworkRepository rootNetworkRepository,
                                      RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository,
                                      NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                                      StudyServerExecutionService studyServerExecutionService,
                                      @Lazy LoadFlowService loadFlowService,
                                      @Lazy SecurityAnalysisService securityAnalysisService,
                                      @Lazy SensitivityAnalysisService sensitivityAnalysisService,
                                      @Lazy NonEvacuatedEnergyService nonEvacuatedEnergyService,
                                      @Lazy ShortCircuitService shortCircuitService,
                                      @Lazy VoltageInitService voltageInitService,
                                      @Lazy DynamicSimulationService dynamicSimulationService,
                                      @Lazy StateEstimationService stateEstimationService,
                                      @Lazy ReportService reportService) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoRepository = rootNetworkNodeInfoRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.studyServerExecutionService = studyServerExecutionService;
        this.loadFlowService = loadFlowService;
        this.securityAnalysisService = securityAnalysisService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.nonEvacuatedEnergyService = nonEvacuatedEnergyService;
        this.shortCircuitService = shortCircuitService;
        this.voltageInitService = voltageInitService;
        this.dynamicSimulationService = dynamicSimulationService;
        this.stateEstimationService = stateEstimationService;
        this.reportService = reportService;
    }

    public void createRootNetworkLinks(@NonNull UUID studyUuid, @NonNull RootNetworkEntity rootNetworkEntity) {
        // For each network modification node (nodeInfoEntity) create a link with the root network
        networkModificationNodeInfoRepository.findAllByNodeStudyId(studyUuid).forEach(networkModificationNodeEntity -> {
            RootNetworkNodeInfoEntity newRootNetworkNodeInfoEntity = createDefaultEntity(networkModificationNodeEntity.getId());
            addLink(networkModificationNodeEntity, rootNetworkEntity, newRootNetworkNodeInfoEntity);
        });
    }

    public void createNodeLinks(@NonNull UUID studyUuid, @NonNull NetworkModificationNodeInfoEntity modificationNodeInfoEntity) {
        // For each root network create a link with the node
        rootNetworkRepository.findAllByStudyId(studyUuid).forEach(rootNetworkEntity -> {
            RootNetworkNodeInfoEntity newRootNetworkNodeInfoEntity = createDefaultEntity(modificationNodeInfoEntity.getId());
            addLink(modificationNodeInfoEntity, rootNetworkEntity, newRootNetworkNodeInfoEntity);
        });
    }

    private static RootNetworkNodeInfoEntity createDefaultEntity(UUID nodeUuid) {
        return RootNetworkNodeInfoEntity.builder()
            .nodeBuildStatus(NodeBuildStatusEmbeddable.from(BuildStatus.NOT_BUILT))
            .variantId(UUID.randomUUID().toString())
            .modificationReports(new HashMap<>(Map.of(nodeUuid, UUID.randomUUID())))
            .build();
    }

    @Transactional
    public void updateComputationResultUuid(UUID nodeUuid, UUID rootNetworkUuid, UUID computationResultUuid, ComputationType computationType) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        switch (computationType) {
            case LOAD_FLOW -> rootNetworkNodeInfoEntity.setLoadFlowResultUuid(computationResultUuid);
            case SECURITY_ANALYSIS -> rootNetworkNodeInfoEntity.setSecurityAnalysisResultUuid(computationResultUuid);
            case SENSITIVITY_ANALYSIS ->
                rootNetworkNodeInfoEntity.setSensitivityAnalysisResultUuid(computationResultUuid);
            case NON_EVACUATED_ENERGY_ANALYSIS ->
                rootNetworkNodeInfoEntity.setNonEvacuatedEnergyResultUuid(computationResultUuid);
            case SHORT_CIRCUIT -> rootNetworkNodeInfoEntity.setShortCircuitAnalysisResultUuid(computationResultUuid);
            case SHORT_CIRCUIT_ONE_BUS ->
                rootNetworkNodeInfoEntity.setOneBusShortCircuitAnalysisResultUuid(computationResultUuid);
            case VOLTAGE_INITIALIZATION -> rootNetworkNodeInfoEntity.setVoltageInitResultUuid(computationResultUuid);
            case DYNAMIC_SIMULATION -> rootNetworkNodeInfoEntity.setDynamicSimulationResultUuid(computationResultUuid);
            case STATE_ESTIMATION -> rootNetworkNodeInfoEntity.setStateEstimationResultUuid(computationResultUuid);
        }
    }

    public void fillDeleteNodeInfo(UUID nodeUuid, DeleteNodeInfos deleteNodeInfos) {
        //get all rootnetworknodeinfo info linked to node
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllWithRootNetworkByNodeInfoId(nodeUuid);
        rootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfoEntity -> {
            rootNetworkNodeInfoEntity.getModificationReports().forEach((key, value) -> deleteNodeInfos.addReportUuid(value));
            rootNetworkNodeInfoEntity.getComputationReports().forEach((key, value) -> deleteNodeInfos.addReportUuid(value));

            String variantId = rootNetworkNodeInfoEntity.getVariantId();
            UUID networkUuid = rootNetworkNodeInfoEntity.getRootNetwork().getNetworkUuid();
            if (!StringUtils.isBlank(variantId)) {
                deleteNodeInfos.addVariantId(networkUuid, variantId);
            }

            UUID loadFlowResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, LOAD_FLOW);
            if (loadFlowResultUuid != null) {
                deleteNodeInfos.addLoadFlowResultUuid(loadFlowResultUuid);
            }

            UUID securityAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SECURITY_ANALYSIS);
            if (securityAnalysisResultUuid != null) {
                deleteNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
            }

            UUID sensitivityAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SENSITIVITY_ANALYSIS);
            if (sensitivityAnalysisResultUuid != null) {
                deleteNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
            }

            UUID nonEvacuatedEnergyResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, NON_EVACUATED_ENERGY_ANALYSIS);
            if (nonEvacuatedEnergyResultUuid != null) {
                deleteNodeInfos.addNonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid);
            }

            UUID shortCircuitAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SHORT_CIRCUIT);
            if (shortCircuitAnalysisResultUuid != null) {
                deleteNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
            }

            UUID oneBusShortCircuitAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SHORT_CIRCUIT_ONE_BUS);
            if (oneBusShortCircuitAnalysisResultUuid != null) {
                deleteNodeInfos.addOneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid);
            }

            UUID voltageInitResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, VOLTAGE_INITIALIZATION);
            if (voltageInitResultUuid != null) {
                deleteNodeInfos.addVoltageInitResultUuid(voltageInitResultUuid);
            }

            UUID dynamicSimulationResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, DYNAMIC_SIMULATION);
            if (dynamicSimulationResultUuid != null) {
                deleteNodeInfos.addDynamicSimulationResultUuid(dynamicSimulationResultUuid);
            }

            UUID stateEstimationResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, STATE_ESTIMATION);
            if (stateEstimationResultUuid != null) {
                deleteNodeInfos.addStateEstimationResultUuid(stateEstimationResultUuid);
            }
        });
    }

    public void invalidateRootNetworkNodeInfoProper(UUID nodeUuid, UUID rootNetworUuid, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus,
                                                    List<UUID> changedNodes, boolean deleteVoltageInitResults) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        // No need to invalidate a node with a status different of "BUILT"
        if (rootNetworkNodeInfoEntity.getNodeBuildStatus().toDto().isBuilt()) {
            fillInvalidateNodeInfos(nodeUuid, rootNetworUuid, invalidateNodeInfos, invalidateOnlyChildrenBuildStatus, deleteVoltageInitResults);
            if (!invalidateOnlyChildrenBuildStatus) {
                invalidateRootNetworkNodeInfoBuildStatus(nodeUuid, rootNetworkNodeInfoEntity, changedNodes);
            }

            rootNetworkNodeInfoEntity.setLoadFlowResultUuid(null);
            rootNetworkNodeInfoEntity.setSecurityAnalysisResultUuid(null);
            rootNetworkNodeInfoEntity.setSensitivityAnalysisResultUuid(null);
            rootNetworkNodeInfoEntity.setNonEvacuatedEnergyResultUuid(null);
            rootNetworkNodeInfoEntity.setShortCircuitAnalysisResultUuid(null);
            rootNetworkNodeInfoEntity.setOneBusShortCircuitAnalysisResultUuid(null);
            if (deleteVoltageInitResults) {
                rootNetworkNodeInfoEntity.setVoltageInitResultUuid(null);
            }
            rootNetworkNodeInfoEntity.setStateEstimationResultUuid(null);

            // we want to keep only voltage initialization report if deleteVoltageInitResults is false
            Map<String, UUID> computationReports = rootNetworkNodeInfoEntity.getComputationReports()
                .entrySet()
                .stream()
                .filter(entry -> VOLTAGE_INITIALIZATION.name().equals(entry.getKey()) && !deleteVoltageInitResults)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // Update the computation reports in the repository
            rootNetworkNodeInfoEntity.setComputationReports(computationReports);
        }
    }

    private void fillInvalidateNodeInfos(UUID nodeUuid, UUID rootNetworkUuid, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus,
                                         boolean deleteVoltageInitResults) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        if (!invalidateOnlyChildrenBuildStatus) {
            // we want to delete associated report and variant in this case
            rootNetworkNodeInfoEntity.getModificationReports().forEach((key, value) -> invalidateNodeInfos.addReportUuid(value));
            invalidateNodeInfos.addVariantId(rootNetworkNodeInfoEntity.getVariantId());
        }

        // we want to delete associated computation reports exept for voltage initialization : only if deleteVoltageInitResults is true
        rootNetworkNodeInfoEntity.getComputationReports().forEach((key, value) -> {
            if (deleteVoltageInitResults || !VOLTAGE_INITIALIZATION.name().equals(key)) {
                invalidateNodeInfos.addReportUuid(value);
            }
        });

        UUID loadFlowResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, LOAD_FLOW);
        if (loadFlowResultUuid != null) {
            invalidateNodeInfos.addLoadFlowResultUuid(loadFlowResultUuid);
        }

        UUID securityAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SECURITY_ANALYSIS);
        if (securityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        }

        UUID sensitivityAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SENSITIVITY_ANALYSIS);
        if (sensitivityAnalysisResultUuid != null) {
            invalidateNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
        }

        UUID nonEvacuatedEnergyResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, NON_EVACUATED_ENERGY_ANALYSIS);
        if (nonEvacuatedEnergyResultUuid != null) {
            invalidateNodeInfos.addNonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid);
        }

        UUID shortCircuitAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SHORT_CIRCUIT);
        if (shortCircuitAnalysisResultUuid != null) {
            invalidateNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
        }

        UUID oneBusShortCircuitAnalysisResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, SHORT_CIRCUIT_ONE_BUS);
        if (oneBusShortCircuitAnalysisResultUuid != null) {
            invalidateNodeInfos.addOneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid);
        }

        if (deleteVoltageInitResults) {
            UUID voltageInitResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, VOLTAGE_INITIALIZATION);
            if (voltageInitResultUuid != null) {
                invalidateNodeInfos.addVoltageInitResultUuid(voltageInitResultUuid);
            }
        }

        UUID stateEstimationResultUuid = getComputationResultUuid(rootNetworkNodeInfoEntity, STATE_ESTIMATION);
        if (stateEstimationResultUuid != null) {
            invalidateNodeInfos.addStateEstimationResultUuid(stateEstimationResultUuid);
        }
    }

    public Optional<RootNetworkNodeInfoEntity> getRootNetworkNodeInfo(UUID nodeUuid, UUID rootNetworkUuid) {
        return rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid);
    }

    public UUID getComputationResultUuid(UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
        return getComputationResultUuid(rootNetworkNodeInfoEntity, computationType);
    }

    private static UUID getComputationResultUuid(RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity, ComputationType computationType) {
        return switch (computationType) {
            case LOAD_FLOW -> rootNetworkNodeInfoEntity.getLoadFlowResultUuid();
            case SECURITY_ANALYSIS -> rootNetworkNodeInfoEntity.getSecurityAnalysisResultUuid();
            case SENSITIVITY_ANALYSIS -> rootNetworkNodeInfoEntity.getSensitivityAnalysisResultUuid();
            case NON_EVACUATED_ENERGY_ANALYSIS -> rootNetworkNodeInfoEntity.getNonEvacuatedEnergyResultUuid();
            case SHORT_CIRCUIT -> rootNetworkNodeInfoEntity.getShortCircuitAnalysisResultUuid();
            case SHORT_CIRCUIT_ONE_BUS -> rootNetworkNodeInfoEntity.getOneBusShortCircuitAnalysisResultUuid();
            case VOLTAGE_INITIALIZATION -> rootNetworkNodeInfoEntity.getVoltageInitResultUuid();
            case DYNAMIC_SIMULATION -> rootNetworkNodeInfoEntity.getDynamicSimulationResultUuid();
            case STATE_ESTIMATION -> rootNetworkNodeInfoEntity.getStateEstimationResultUuid();
        };
    }

    public List<UUID> getComputationResultUuids(UUID studyUuid, ComputationType computationType) {
        return rootNetworkNodeInfoRepository.findAllByRootNetworkStudyId(studyUuid).stream()
            .map(rootNetworkNodeInfoEntity -> getComputationResultUuid(rootNetworkNodeInfoEntity, computationType))
            .filter(Objects::nonNull)
            .toList();
    }

    public List<RootNetworkNodeInfoEntity> getStudyRootNetworkNodeInfos(UUID studyUuid) {
        return rootNetworkNodeInfoRepository.findAllByRootNetworkStudyId(studyUuid);
    }

    public void assertNoRootNetworkModificationInfoIsBuilding(UUID studyUuid) {
        if (rootNetworkNodeInfoRepository.existsByStudyUuidAndBuildStatus(studyUuid, BuildStatus.BUILDING)) {
            throw new StudyException(NOT_ALLOWED, "No modification is allowed during a node building.");
        }
    }

    private void addLink(NetworkModificationNodeInfoEntity nodeInfoEntity, RootNetworkEntity rootNetworkEntity, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        nodeInfoEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity);
    }

    private static void invalidateRootNetworkNodeInfoBuildStatus(UUID nodeUuid, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity, List<UUID> changedNodes) {
        if (!rootNetworkNodeInfoEntity.getNodeBuildStatus().toDto().isBuilt()) {
            return;
        }

        rootNetworkNodeInfoEntity.setNodeBuildStatus(NodeBuildStatusEmbeddable.from(BuildStatus.NOT_BUILT));
        rootNetworkNodeInfoEntity.setVariantId(UUID.randomUUID().toString());
        rootNetworkNodeInfoEntity.setModificationReports(new HashMap<>(Map.of(nodeUuid, UUID.randomUUID())));
        changedNodes.add(nodeUuid);
    }

    @Transactional
    public void updateRootNetworkNode(UUID nodeUuid, UUID rootNetworkUuid, RootNetworkNodeInfo rootNetworkNodeInfo) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOTNETWORK_NOT_FOUND));
        if (rootNetworkNodeInfo.getVariantId() != null) {
            rootNetworkNodeInfoEntity.setVariantId(rootNetworkNodeInfo.getVariantId());
        }
        if (rootNetworkNodeInfo.getNodeBuildStatus() != null) {
            rootNetworkNodeInfoEntity.setNodeBuildStatus(rootNetworkNodeInfo.getNodeBuildStatus().toEntity());
        }
        if (rootNetworkNodeInfo.getLoadFlowResultUuid() != null) {
            rootNetworkNodeInfoEntity.setLoadFlowResultUuid(rootNetworkNodeInfo.getLoadFlowResultUuid());
        }
        if (rootNetworkNodeInfo.getSecurityAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setSecurityAnalysisResultUuid(rootNetworkNodeInfo.getSecurityAnalysisResultUuid());
        }
        if (rootNetworkNodeInfo.getSensitivityAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setSensitivityAnalysisResultUuid(rootNetworkNodeInfo.getSensitivityAnalysisResultUuid());
        }
        if (rootNetworkNodeInfo.getNonEvacuatedEnergyResultUuid() != null) {
            rootNetworkNodeInfoEntity.setNonEvacuatedEnergyResultUuid(rootNetworkNodeInfo.getNonEvacuatedEnergyResultUuid());
        }
        if (rootNetworkNodeInfo.getShortCircuitAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setShortCircuitAnalysisResultUuid(rootNetworkNodeInfo.getShortCircuitAnalysisResultUuid());
        }
        if (rootNetworkNodeInfo.getOneBusShortCircuitAnalysisResultUuid() != null) {
            rootNetworkNodeInfoEntity.setOneBusShortCircuitAnalysisResultUuid(rootNetworkNodeInfo.getOneBusShortCircuitAnalysisResultUuid());
        }
        if (rootNetworkNodeInfo.getStateEstimationResultUuid() != null) {
            rootNetworkNodeInfoEntity.setStateEstimationResultUuid(rootNetworkNodeInfo.getStateEstimationResultUuid());
        }
        if (rootNetworkNodeInfo.getDynamicSimulationResultUuid() != null) {
            rootNetworkNodeInfoEntity.setDynamicSimulationResultUuid(rootNetworkNodeInfo.getDynamicSimulationResultUuid());
        }
    }

    public List<CompletableFuture<Void>> getDeleteRootNetworkNodeInfosFutures(List<RootNetworkNodeInfo> rootNetworkNodeInfo) {
        return List.of(
            studyServerExecutionService.runAsync(() -> reportService.deleteReports(rootNetworkNodeInfo.stream().map(this::getReportUuids).flatMap(Collection::stream).toList())),
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getLoadFlowResultUuid).filter(Objects::nonNull).forEach(loadFlowService::deleteLoadFlowResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getSecurityAnalysisResultUuid).filter(Objects::nonNull).forEach(securityAnalysisService::deleteSaResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getSensitivityAnalysisResultUuid).filter(Objects::nonNull).forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getNonEvacuatedEnergyResultUuid).filter(Objects::nonNull).forEach(nonEvacuatedEnergyService::deleteNonEvacuatedEnergyResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getShortCircuitAnalysisResultUuid).filter(Objects::nonNull).forEach(shortCircuitService::deleteShortCircuitAnalysisResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getOneBusShortCircuitAnalysisResultUuid).filter(Objects::nonNull).forEach(shortCircuitService::deleteShortCircuitAnalysisResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getVoltageInitResultUuid).filter(Objects::nonNull).forEach(voltageInitService::deleteVoltageInitResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getDynamicSimulationResultUuid).filter(Objects::nonNull).forEach(dynamicSimulationService::deleteResult)), // TODO delete all with one request only
            studyServerExecutionService.runAsync(() -> rootNetworkNodeInfo.stream()
                .map(RootNetworkNodeInfo::getStateEstimationResultUuid).filter(Objects::nonNull).forEach(stateEstimationService::deleteStateEstimationResult)) // TODO delete all with one request only
        );
    }

    private List<UUID> getReportUuids(RootNetworkNodeInfo rootNetworkNodeInfo) {
        return Stream.of(
            rootNetworkNodeInfo.getModificationReports().values().stream(),
            rootNetworkNodeInfo.getComputationReports().values().stream())
            .flatMap(Function.identity())
            .toList();
    }
}
