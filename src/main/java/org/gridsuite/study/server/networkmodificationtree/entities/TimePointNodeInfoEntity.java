package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
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
@Table(name = "TimePointNodeInfo")
public class TimePointNodeInfoEntity {
    public TimePointNodeInfoEntity(TimePointEntity timePoint, NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity) {
        this.timePoint = timePoint;
        this.nodeInfo = networkModificationNodeInfoEntity;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "timePointId",
        referencedColumnName = "id",
        foreignKey = @ForeignKey)
    private TimePointEntity timePoint;

    @Column
    UUID reportUuid;

    @ManyToOne
    @JoinColumn(name = "nodeInfoId",
        referencedColumnName = "idNode",
        foreignKey = @ForeignKey)
    private NetworkModificationNodeInfoEntity nodeInfo;

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

    public TimePointNetworkModificationNode toDto() {
        return TimePointNetworkModificationNode.builder()
            .reportUuid(reportUuid)
            .variantId(variantId)
            .dynamicSimulationResultUuid(dynamicSimulationResultUuid)
            .loadFlowResultUuid(loadFlowResultUuid)
            .modificationsToExclude(modificationsToExclude)
            //TODO fixme
            .nodeBuildStatus(NodeBuildStatus.from(nodeBuildStatus.getLocalBuildStatus()))
            .nonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid)
            .oneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid)
            //TODO to complete
            .build();
    }
}
