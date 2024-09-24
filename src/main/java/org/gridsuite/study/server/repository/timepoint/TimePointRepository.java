package org.gridsuite.study.server.repository.timepoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */

@Repository
public interface TimePointRepository extends JpaRepository<TimePointEntity, UUID> {
    List<TimePointEntity> findAllByStudyId(UUID studyUuid);
}
