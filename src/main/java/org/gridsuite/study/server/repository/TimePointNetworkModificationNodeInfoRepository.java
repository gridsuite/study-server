package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNetworkModificationNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimePointNetworkModificationNodeInfoRepository extends JpaRepository<TimePointNetworkModificationNodeInfoEntity, UUID> {
    List<TimePointNetworkModificationNodeInfoEntity> findAllByLoadFlowResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllByDynamicSimulationResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllBySecurityAnalysisResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllBySensitivityAnalysisResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllByNonEvacuatedEnergyResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllByShortCircuitAnalysisResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllByOneBusShortCircuitAnalysisResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllByVoltageInitResultUuidNotNull();

    List<TimePointNetworkModificationNodeInfoEntity> findAllByStateEstimationResultUuidNotNull();
}
