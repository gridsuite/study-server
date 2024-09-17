package org.gridsuite.study.server.repository.timepoint;

import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;

import java.util.List;

public interface TimePointNetworkModificationNodeInfoRepository extends org.springframework.data.jpa.repository.JpaRepository<TimePointNodeInfoEntity, java.util.UUID> {
    List<TimePointNodeInfoEntity> findAllByLoadFlowResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByDynamicSimulationResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllBySecurityAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllBySensitivityAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByNonEvacuatedEnergyResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByShortCircuitAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByOneBusShortCircuitAnalysisResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByVoltageInitResultUuidNotNull();

    List<TimePointNodeInfoEntity> findAllByStateEstimationResultUuidNotNull();
}
