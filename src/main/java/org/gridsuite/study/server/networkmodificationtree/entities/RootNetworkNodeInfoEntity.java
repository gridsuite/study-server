/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;

import java.util.Map;
import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@SuperBuilder
@Table(name = "RootNetworkNodeInfo")
public class RootNetworkNodeInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "rootNetworkId",
        referencedColumnName = "id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_root_network_node_info"))
    private RootNetworkEntity rootNetwork;

    @ManyToOne
    @JoinColumn(name = "nodeInfoId",
        referencedColumnName = "idNode",
        foreignKey = @ForeignKey)
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

    @Column(name = "voltageInitResultUuid")
    private UUID voltageInitResultUuid;

    @Column(name = "securityAnalysisResultUuid")
    private UUID securityAnalysisResultUuid;

    @Column(name = "sensitivityAnalysisResultUuid")
    private UUID sensitivityAnalysisResultUuid;

    @Column(name = "nonEvacuatedEnergyResultUuid")
    private UUID nonEvacuatedEnergyResultUuid;

    @Column(name = "dynamicSimulationResultUuid")
    private UUID dynamicSimulationResultUuid;

    @Column(name = "stateEstimationResultUuid")
    private UUID stateEstimationResultUuid;

    @Embedded
    @AttributeOverrides(value = {
        @AttributeOverride(name = "localBuildStatus", column = @Column(name = "localBuildStatus", nullable = false)),
        @AttributeOverride(name = "globalBuildStatus", column = @Column(name = "globalBuildStatus", nullable = false))
    })
    private NodeBuildStatusEmbeddable nodeBuildStatus;

    public RootNetworkNodeInfo toDto() {
        return RootNetworkNodeInfo.builder()
            .id(id)
            .computationReports(computationReports)
            .modificationReports(modificationReports)
            .dynamicSimulationResultUuid(dynamicSimulationResultUuid)
            .loadFlowResultUuid(loadFlowResultUuid)
            .nodeBuildStatus(nodeBuildStatus.toDto())
            .nonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid)
            .oneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid)
            .securityAnalysisResultUuid(securityAnalysisResultUuid)
            .stateEstimationResultUuid(stateEstimationResultUuid)
            .sensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid)
            .voltageInitResultUuid(voltageInitResultUuid)
            .shortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid)
            .variantId(variantId)
            .build();
    }
}
