package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@Getter
@Setter
@Entity
@SuperBuilder
@Table(name = "TimePointRootNodeInfo")
public class TimePointRootNodeInfoEntity extends AbstractTimePointNodeInfoEntity<RootNodeInfoEntity> {
}
