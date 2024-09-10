package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.networkmodificationtree.entities.TimePointRootNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TimePointRootNodeInfosRepository extends JpaRepository<TimePointRootNodeInfoEntity, UUID> {
}
