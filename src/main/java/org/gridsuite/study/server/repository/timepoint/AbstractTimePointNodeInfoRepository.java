package org.gridsuite.study.server.repository.timepoint;

import org.gridsuite.study.server.networkmodificationtree.entities.AbstractTimePointNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface AbstractTimePointNodeInfoRepository<T extends AbstractTimePointNodeInfoEntity<?>> extends JpaRepository<T, UUID> {
}
