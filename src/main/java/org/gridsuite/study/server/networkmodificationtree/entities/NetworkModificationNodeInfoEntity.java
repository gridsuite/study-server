/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "NetworkModificationNodeInfo  ")
public class NetworkModificationNodeInfoEntity extends AbstractNodeInfoEntity {

    @Column
    private UUID modificationGroupUuid;

    @Column
    private String variantId;

    @Column(name = "modificationsToExclude")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "networkModificationNodeInfoEntity_modificationsToExclude_fk"), indexes = {@Index(name = "networkModificationNodeInfoEntity_modificationsToExclude_idx", columnList = "network_modification_node_info_entity_id_node")})
    private Set<UUID> modificationsToExclude;

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

    @ElementCollection
    @CollectionTable(name = "computationReports",
            indexes = {@Index(name = "networkModificationNodeInfoEntity_computationReports_idx1", columnList = "network_modification_node_info_entity_id_node")},
            foreignKey = @ForeignKey(name = "networkModificationNodeInfoEntity_computationReports_fk1"))
    private Map<String, UUID> computationReports;

    @ElementCollection
    @CollectionTable(name = "modificationReports",
            indexes = {@Index(name = "networkModificationNodeInfoEntity_modificationReports_idx1", columnList = "network_modification_node_info_entity_id_node")},
            foreignKey = @ForeignKey(name = "networkModificationNodeInfoEntity_modificationReports_fk1"))
    private Map<UUID, UUID> modificationReports;
}
