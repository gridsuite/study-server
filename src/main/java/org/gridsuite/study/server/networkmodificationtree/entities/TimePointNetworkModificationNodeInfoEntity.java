package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.networkmodificationtree.dto.TimePointNetworkModificationNode;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

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
@Table(name = "TimePointNetworkModificationNodeInfo")
public class TimePointNetworkModificationNodeInfoEntity extends AbstractTimePointNodeInfoEntity<NetworkModificationNodeInfoEntity> {
    public TimePointNetworkModificationNodeInfoEntity(TimePointEntity timePoint, NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity) {
        super(timePoint, networkModificationNodeInfoEntity);
    }

    @Column
    private String variantId;

    @Column(name = "modificationsToExclude")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "timePointNodeStatusEntity_modificationsToExclude_fk"), indexes = {@Index(name = "timePointNodeStatusEntity_modificationsToExclude_idx", columnList = "time_point_node_status_entity_id")})
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

    public TimePointNetworkModificationNode toDto() {
        return TimePointNetworkModificationNode.builder().build();
    }
}
