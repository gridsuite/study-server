package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

@NoArgsConstructor
@Getter
@Setter
@Entity
@SuperBuilder
@Table(name = "TimePointRootNodeInfo")
public class TimePointRootNodeInfoEntity extends AbstractTimePointNodeInfoEntity<RootNodeInfoEntity> {
    public TimePointRootNodeInfoEntity(TimePointEntity timePoint, RootNodeInfoEntity rootNodeInfo) {
        super(timePoint, rootNodeInfo);
    }
}
