/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;

import java.util.*;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@SuperBuilder
@Table(name = "RootNetworkNodeInfo",
    indexes = {
        @Index(name = "rootNetworkNodeEntity_rootNetworkId_idx", columnList = "root_network_id"),
        @Index(name = "rootNetworkNodeEntity_nodeId_idx", columnList = "node_info_id"),
        @Index(name = "rootNetworkNodeEntity_nodeId_rootNeworkId_idx", columnList = "node_info_id, root_network_id", unique = true),

    })
public class RootNetworkNodeInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rootNetworkId",
        referencedColumnName = "id", nullable = false,
        foreignKey = @ForeignKey(name = "rootNetworkNode_rootNetwork_id_fk_constraint"))
    private RootNetworkEntity rootNetwork;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodeInfoId",
        referencedColumnName = "idNode", nullable = false,
        foreignKey = @ForeignKey(name = "rootNetworkNode_node_id_fk_constraint"))
    private NetworkModificationNodeInfoEntity nodeInfo;

    @Column
    private String variantId;

    @ElementCollection
    @CollectionTable(name = "computationReports",
        indexes = {@Index(name = "root_network_node_info_entity_computationReports_idx1", columnList = "root_network_node_info_entity_id")},
        foreignKey = @ForeignKey(name = "rootNetworkNodeInfoEntity_computationReports_fk1"))
    private Map<String, UUID> computationReports;

    @ElementCollection
    @CollectionTable(name = "modificationReports",
        indexes = {@Index(name = "root_network_node_info_entity_modificationReports_idx1", columnList = "root_network_node_info_entity_id")},
        foreignKey = @ForeignKey(name = "rootNetworkNodeInfoEntity_modificationReports_fk1"))
    private Map<UUID, UUID> modificationReports;

    @Column(name = "shortCircuitAnalysisResultUuid")
    private UUID shortCircuitAnalysisResultUuid;

    @Column(name = "oneBusShortCircuitAnalysisResultUuid")
    private UUID oneBusShortCircuitAnalysisResultUuid;

    @Column(name = "loadflowResultUuid")
    private UUID loadFlowResultUuid;

    @Column(name = "loadflowWithRatioTapChangers")
    private Boolean loadFlowWithRatioTapChangers;

    @Column(name = "voltageInitResultUuid")
    private UUID voltageInitResultUuid;

    @Column(name = "securityAnalysisResultUuid")
    private UUID securityAnalysisResultUuid;

    @Column(name = "sensitivityAnalysisResultUuid")
    private UUID sensitivityAnalysisResultUuid;

    @Column(name = "dynamicSimulationResultUuid")
    private UUID dynamicSimulationResultUuid;

    @Column(name = "dynamicSecurityAnalysisResultUuid")
    private UUID dynamicSecurityAnalysisResultUuid;

    @Column(name = "stateEstimationResultUuid")
    private UUID stateEstimationResultUuid;

    @Column(name = "pccMinResultUuid")
    private UUID pccMinResultUuid;

    @ElementCollection
    @CollectionTable(
            name = "node_export",
            joinColumns = @JoinColumn(name = "root_network_node_info_id"),
            foreignKey = @ForeignKey(name = "rootNetworkNodeInfo_nodeExport_fk")
    )
    private Map<UUID, NodeExportEmbeddable> nodeExportNetwork = new HashMap<>();

    @Column(name = "blockedNode")
    private Boolean blockedNode;

    @Embedded
    @AttributeOverrides(value = {
        @AttributeOverride(name = "localBuildStatus", column = @Column(name = "localBuildStatus", nullable = false)),
        @AttributeOverride(name = "globalBuildStatus", column = @Column(name = "globalBuildStatus", nullable = false))
    })
    private NodeBuildStatusEmbeddable nodeBuildStatus;

    @ElementCollection
    @CollectionTable(name = "RootNetworkNodeInfoModificationsToExclude",
        joinColumns = @JoinColumn(name = "root_network_node_info_id"),
        indexes = {@Index(name = "root_network_node_info_entity_modificationsUuidsToExclude_idx1", columnList = "root_network_node_info_id")},
        foreignKey = @ForeignKey(name = "root_network_node_info_entity_modificationsUuidsToExclude_fk1"))
    private Set<UUID> modificationsUuidsToExclude = new HashSet<>();

    public RootNetworkNodeInfo toDto() {
        return RootNetworkNodeInfo.builder()
            .id(id)
            .computationReports(computationReports)
            .modificationReports(modificationReports)
            .dynamicSimulationResultUuid(dynamicSimulationResultUuid)
            .dynamicSecurityAnalysisResultUuid(dynamicSecurityAnalysisResultUuid)
            .loadFlowResultUuid(loadFlowResultUuid)
            .loadFlowWithRatioTapChangers(loadFlowWithRatioTapChangers)
            .nodeBuildStatus(nodeBuildStatus.toDto())
            .oneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid)
            .securityAnalysisResultUuid(securityAnalysisResultUuid)
            .stateEstimationResultUuid(stateEstimationResultUuid)
            .pccMinResultUuid(pccMinResultUuid)
            .sensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid)
            .voltageInitResultUuid(voltageInitResultUuid)
            .shortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid)
            .variantId(variantId)
            .build();
    }

    public void addModificationsToExclude(Set<UUID> uuids) {
        modificationsUuidsToExclude.addAll(uuids);
    }

    public void removeModificationsFromExclude(Set<UUID> uuids) {
        modificationsUuidsToExclude.removeAll(uuids);
    }
}
