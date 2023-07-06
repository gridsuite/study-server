/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.repository.LoadFlowResultEntity;

import javax.persistence.*;
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

    @Column(name = "loadFlowStatus")
    @Enumerated(EnumType.STRING)
    private LoadFlowStatus loadFlowStatus;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "loadFlowResultEntity_id",
        referencedColumnName = "id",
        foreignKey = @ForeignKey(
            name = "loadFlowResult_id_fk"
        ))
    private LoadFlowResultEntity loadFlowResult;

    @Column(name = "shortCircuitAnalysisResultUuid")
    private UUID shortCircuitAnalysisResultUuid;

    @Column(name = "voltageInitResultUuid")
    private UUID voltageInitResultUuid;

    @Column(name = "securityAnalysisResultUuid")
    private UUID securityAnalysisResultUuid;

    @Column(name = "sensitivityAnalysisResultUuid")
    private UUID sensitivityAnalysisResultUuid;

    @Column(name = "dynamicSimulationResultUuid")
    private UUID dynamicSimulationResultUuid;

    @Embedded
    @AttributeOverrides(value = {
        @AttributeOverride(name = "localBuildStatus", column = @Column(name = "localBuildStatus", nullable = false)),
        @AttributeOverride(name = "globalBuildStatus", column = @Column(name = "globalBuildStatus", nullable = false))
    })
    private NodeBuildStatusEmbeddable nodeBuildStatus;
}
