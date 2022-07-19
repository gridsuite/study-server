package org.gridsuite.study.server.networkmodificationtree.repositories;

import java.util.List;
import java.util.UUID;

import org.gridsuite.study.server.networkmodificationtree.entities.ReportUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportUsageRepository extends JpaRepository<ReportUsageEntity, UUID> {
    @Query(nativeQuery = true, value = "select u2.report_id, u2.build_node_id, u2.definition_node_id"
            + " from report_usage u, report_usage u2"
            + " where u.build_node_id = ?1 and u.report_id = u2.report_id")
    List<ReportUsageEntity> getReportUsageEntities(UUID buildNodeUuid);
}
