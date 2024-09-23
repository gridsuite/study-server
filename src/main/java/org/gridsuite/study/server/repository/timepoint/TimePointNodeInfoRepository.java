package org.gridsuite.study.server.repository.timepoint;

import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimePointNodeInfoRepository extends JpaRepository<TimePointNodeInfoEntity, UUID> {
    List<TimePointNodeInfoEntity> findAllByLoadFlowResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByDynamicSimulationResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllBySecurityAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllBySensitivityAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByNonEvacuatedEnergyResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByShortCircuitAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByOneBusShortCircuitAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByVoltageInitResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByStateEstimationResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByNodeInfoId(UUID nodeInfoId);

    TimePointNodeInfoEntity findByNodeInfoIdAndTimePointId(UUID nodeInfoId, UUID timePointId);
}
