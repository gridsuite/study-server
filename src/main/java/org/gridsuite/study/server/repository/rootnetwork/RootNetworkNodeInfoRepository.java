/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.rootnetwork;

import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    @Query("select count(rnni) > 0 from RootNetworkNodeInfoEntity rnni LEFT JOIN rnni.rootNetwork rn LEFT JOIN rn.study s " +
        "where s.id = :studyUuid and (rnni.nodeBuildStatus.globalBuildStatus = :buildStatus or rnni.nodeBuildStatus.localBuildStatus = :buildStatus)")
    boolean existsByStudyUuidAndBuildStatus(UUID studyUuid, BuildStatus buildStatus);
}