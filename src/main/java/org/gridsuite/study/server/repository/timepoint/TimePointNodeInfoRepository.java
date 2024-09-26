/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.timepoint;

import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
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

    List<TimePointNodeInfoEntity> findAllByNodeInfoIdIn(List<UUID> nodeInfoIds);

    TimePointNodeInfoEntity findByNodeInfoIdAndTimePointId(UUID nodeInfoId, UUID timePointId);
}
