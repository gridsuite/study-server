/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.nodeactivity;

import org.gridsuite.study.server.nodeactivity.NodeActivityEntity;
import org.gridsuite.study.server.nodeactivity.NodeActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
public interface NodeActivityRepository extends JpaRepository<NodeActivityEntity, UUID> {

    List<NodeActivityEntity> findAllByStudyId(UUID studyId);

    void deleteByTypeAndNodeIdInAndRootNetworkId(NodeActivityType type, List<UUID> nodeIds, UUID rootNetworkId);

    void deleteByTypeAndNodeIdInAndRootNetworkIdIsNull(NodeActivityType type, List<UUID> nodeIds);

    boolean existsByTypeAndNodeIdAndRootNetworkId(NodeActivityType type, UUID nodeId, UUID rootNetworkId);

    List<NodeActivityEntity> findAllByStartedAtBefore(Instant cutoff);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NodeActivityEntity activity WHERE activity.startedAt < :startedBefore")
    int deleteByStartedAtBefore(@Param("startedBefore") Instant startedBefore);
}
