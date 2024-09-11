package org.gridsuite.study.server.repository.timepoint;

import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNetworkModificationNodeInfoEntity;

import java.util.List;

public interface TimePointNetworkModificationNodeInfoRepository extends AbstractTimePointNodeInfoRepository<TimePointNetworkModificationNodeInfoEntity> {
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
