package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
@MappedSuperclass
public abstract class AbstractTimePointNodeInfoEntity<T extends AbstractNodeInfoEntity<?>> {
    public AbstractTimePointNodeInfoEntity(TimePointEntity timePoint, T nodeInfo) {
        this.timePoint = timePoint;
        this.nodeInfo = nodeInfo;
    }

    @Id
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
    private T nodeInfo;
}
