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
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.DeleteNodeInfos;
import org.gridsuite.study.server.dto.InvalidateNodeInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.gridsuite.study.server.StudyException.Type.ROOTNETWORK_NOT_FOUND;
import static org.gridsuite.study.server.dto.ComputationType.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class RootNetworkNodeInfoService {
    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    public RootNetworkNodeInfoService(RootNetworkRepository rootNetworkRepository, RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoRepository = rootNetworkNodeInfoRepository;
    }

    public void createRootNetworkLinks(@NonNull UUID studyUuid, @NonNull RootNetworkEntity rootNetworkEntity) {
        // For each network modification node (nodeInfoEntity) create a link with the root network
        // Create a default NetworkModificationNode (replace by the RootNetworkNodeInfo DTO)
        // addLink(nodeInfoEntity, rootNetworkEntity, newRootNetworkNodeInfoEntity);
    }

    // TODO create a DTO RootNetworkNodeInfo
    public void createNodeLinks(@NonNull UUID studyUuid, @NonNull NetworkModificationNodeInfoEntity modificationNodeInfoEntity, @NonNull NetworkModificationNode rootNetworkNodeInfo) {
        if (Objects.isNull(rootNetworkNodeInfo.getNodeBuildStatus())) {
            rootNetworkNodeInfo.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        }
        if (rootNetworkNodeInfo.getVariantId() == null) {
            rootNetworkNodeInfo.setVariantId(UUID.randomUUID().toString());
        }
        if (rootNetworkNodeInfo.getModificationReports() == null) {
            rootNetworkNodeInfo.setModificationReports(new HashMap<>(Map.of(modificationNodeInfoEntity.getId(), UUID.randomUUID())));
        }

        // For each root network create a link with the node
        rootNetworkRepository.findAllByStudyId(studyUuid).forEach(rootNetworkEntity -> {
            RootNetworkNodeInfoEntity newRootNetworkNodeInfoEntity = RootNetworkNodeInfoEntity.builder()
                    .nodeBuildStatus(rootNetworkNodeInfo.getNodeBuildStatus().toEntity())
                    .variantId(rootNetworkNodeInfo.getVariantId())
                    .dynamicSimulationResultUuid(rootNetworkNodeInfo.getDynamicSimulationResultUuid())
                    .loadFlowResultUuid(rootNetworkNodeInfo.getLoadFlowResultUuid())
                    .nonEvacuatedEnergyResultUuid(rootNetworkNodeInfo.getNonEvacuatedEnergyResultUuid())
                    .securityAnalysisResultUuid(rootNetworkNodeInfo.getSecurityAnalysisResultUuid())
                    .sensitivityAnalysisResultUuid(rootNetworkNodeInfo.getSensitivityAnalysisResultUuid())
                    .oneBusShortCircuitAnalysisResultUuid(rootNetworkNodeInfo.getOneBusShortCircuitAnalysisResultUuid())
                    .shortCircuitAnalysisResultUuid(rootNetworkNodeInfo.getShortCircuitAnalysisResultUuid())
                    .stateEstimationResultUuid(rootNetworkNodeInfo.getStateEstimationResultUuid())
                    .voltageInitResultUuid(rootNetworkNodeInfo.getVoltageInitResultUuid())
                    .computationReports(rootNetworkNodeInfo.getComputationsReports())
                    .modificationReports(rootNetworkNodeInfo.getModificationReports())
                    .build();
            addLink(modificationNodeInfoEntity, rootNetworkEntity, newRootNetworkNodeInfoEntity);
        });
    }

    public void fillDeleteNodeInfo(UUID nodeUuid, DeleteNodeInfos deleteNodeInfos) {
        //get all rootnetworknodeinfo info linked to node
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findAllByNodeInfoId(nodeUuid);
        rootNetworkNodeInfoEntity.forEach(rnNodeinfo -> {
            rnNodeinfo.getModificationReports().forEach((key, value) -> deleteNodeInfos.addReportUuid(value));
            rnNodeinfo.getComputationReports().forEach((key, value) -> deleteNodeInfos.addReportUuid(value));

            String variantId = rnNodeinfo.getVariantId();
            if (!StringUtils.isBlank(variantId)) {
                deleteNodeInfos.addVariantId(variantId);
            }

            UUID loadFlowResultUuid = getComputationResultUuid(rnNodeinfo, LOAD_FLOW);
            if (loadFlowResultUuid != null) {
                deleteNodeInfos.addLoadFlowResultUuid(loadFlowResultUuid);
            }

            UUID securityAnalysisResultUuid = getComputationResultUuid(rnNodeinfo, SECURITY_ANALYSIS);
            if (securityAnalysisResultUuid != null) {
                deleteNodeInfos.addSecurityAnalysisResultUuid(securityAnalysisResultUuid);
            }

            UUID sensitivityAnalysisResultUuid = getComputationResultUuid(rnNodeinfo, SENSITIVITY_ANALYSIS);
            if (sensitivityAnalysisResultUuid != null) {
                deleteNodeInfos.addSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
            }

            UUID nonEvacuatedEnergyResultUuid = getComputationResultUuid(rnNodeinfo, NON_EVACUATED_ENERGY_ANALYSIS);
            if (nonEvacuatedEnergyResultUuid != null) {
                deleteNodeInfos.addNonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid);
            }

            UUID shortCircuitAnalysisResultUuid = getComputationResultUuid(rnNodeinfo, SHORT_CIRCUIT);
            if (shortCircuitAnalysisResultUuid != null) {
                deleteNodeInfos.addShortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid);
            }

            UUID oneBusShortCircuitAnalysisResultUuid = getComputationResultUuid(rnNodeinfo, SHORT_CIRCUIT_ONE_BUS);
            if (oneBusShortCircuitAnalysisResultUuid != null) {
                deleteNodeInfos.addOneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid);
            }

            UUID voltageInitResultUuid = getComputationResultUuid(rnNodeinfo, VOLTAGE_INITIALIZATION);
            if (voltageInitResultUuid != null) {
                deleteNodeInfos.addVoltageInitResultUuid(voltageInitResultUuid);
            }

            UUID dynamicSimulationResultUuid = getComputationResultUuid(rnNodeinfo, DYNAMIC_SIMULATION);
            if (dynamicSimulationResultUuid != null) {
                deleteNodeInfos.addDynamicSimulationResultUuid(dynamicSimulationResultUuid);
            }

            UUID stateEstimationResultUuid = getComputationResultUuid(rnNodeinfo, STATE_ESTIMATION);
            if (stateEstimationResultUuid != null) {
                deleteNodeInfos.addStateEstimationResultUuid(stateEstimationResultUuid);
            }
        });
    }

    public void fillInvalidateNodeInfos(UUID nodeUuid, UUID rootNetworkUuid, InvalidateNodeInfos invalidateNodeInfos, boolean invalidateOnlyChildrenBuildStatus,
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

    public UUID getComputationResultUuid(UUID rootNetworkUuid, UUID nodeUuid, ComputationType computationType) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = getRootNetworkNodeInfo(rootNetworkUuid, nodeUuid).orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
        return getComputationResultUuid(rootNetworkNodeInfoEntity, computationType);
    }

    public UUID getComputationResultUuid(RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity, ComputationType computationType) {
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

    private void addLink(NetworkModificationNodeInfoEntity nodeInfoEntity, RootNetworkEntity rootNetworkEntity, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        nodeInfoEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity);
    }
}
