/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.rootnetwork;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Repository
public interface RootNetworkRepository extends JpaRepository<RootNetworkEntity, UUID> {
    List<RootNetworkEntity> findAllByStudyId(UUID studyUuid);

    @EntityGraph(attributePaths = {"rootNetworkNodeInfos"}, type = EntityGraph.EntityGraphType.LOAD)
    List<RootNetworkEntity> findAllWithInfosByStudyId(UUID studyUuid);

    @EntityGraph(attributePaths = {"importParameters"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<RootNetworkEntity> findWithImportParametersById(UUID rootNetworkUuid);

    @EntityGraph(attributePaths = {"rootNetworkNodeInfos"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<RootNetworkEntity> findWithRootNetworkNodeInfosById(UUID rootNetworkUuid);

    boolean existsByIdAndStudyId(UUID rootNetworkUuid, UUID studyUuid);
}
