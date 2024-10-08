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
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@SuperBuilder
@Table(name = "TimePointNodeInfo")
public class TimePointNodeInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // One TimePoint can have multiple NodeInfo entries
    @ManyToOne
    @JoinColumn(name = "timePointId", nullable = false, foreignKey = @ForeignKey(name = "fk_time_point_node_info"))
    private TimePointEntity timePoint;

    // Many TimePointNodeInfo entries can belong to one NetworkModificationNodeInfo
    @ManyToOne
    @JoinColumn(name = "nodeInfoId",
        referencedColumnName = "idNode",
        foreignKey = @ForeignKey)
    private NetworkModificationNodeInfoEntity nodeInfo;

    //columns moved from to TimePointNodeInfoEntity
    @Column
    private String variantId;

    @Column(name = "modificationsToExclude")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "timePointNodeInfoEntity_modificationsToExclude_fk"), indexes = {@Index(name = "time_point_node_info_entity_modifications_to_exclude_idx", columnList = "time_point_node_info_entity_id")})
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
            indexes = {@Index(name = "timePointNodeInfoEntity_computationReports_idx1", columnList = "time_point_node_info_entity_id")},
            foreignKey = @ForeignKey(name = "timePointNodeInfoEntity_computationReports_fk1"))
    private Map<String, UUID> computationReports;

    @ElementCollection
    @CollectionTable(name = "modificationReports",
            indexes = {@Index(name = "timePointNodeInfoEntity_modificationReports_idx1", columnList = "time_point_node_info_entity_id")},
            foreignKey = @ForeignKey(name = "timePointNodeInfoEntity_modificationReports_fk1"))
    private Map<UUID, UUID> modificationReports;
}
