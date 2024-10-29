/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.rootnetwork;

import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
public interface RootNetworkNodeInfoRepository extends JpaRepository<RootNetworkNodeInfoEntity, UUID> {
    List<RootNetworkNodeInfoEntity> findAllByLoadFlowResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByDynamicSimulationResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllBySecurityAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllBySensitivityAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByNonEvacuatedEnergyResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByShortCircuitAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByOneBusShortCircuitAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByVoltageInitResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByStateEstimationResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByNodeInfoId(UUID nodeInfoId);

    Optional<RootNetworkNodeInfoEntity> findByNodeInfoIdAndRootNetworkId(UUID nodeInfoId, UUID rootNetworkUuid);

    List<RootNetworkNodeInfoEntity> findAllByRootNetworkStudyId(UUID studyUuid);
}
