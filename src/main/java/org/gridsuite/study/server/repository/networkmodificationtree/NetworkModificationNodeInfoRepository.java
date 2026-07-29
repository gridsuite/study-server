/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.dto.LocalActivityStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.SharedActivityStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public interface NetworkModificationNodeInfoRepository extends NodeInfoRepository<NetworkModificationNodeInfoEntity> {
    List<AbstractNodeInfoEntity> findAllByNodeStudyIdAndName(UUID studyUuid, String name);

    List<NetworkModificationNodeInfoEntity> findByModificationGroupUuidIn(List<UUID> modificationGroupUuid);

    @Query("select max(n.columnPosition) from NetworkModificationNodeInfoEntity n join n.node nd where nd.parentNode.idNode = :parentNodeId")
    Optional<Integer> findMaxColumnPositionByParentNodeId(UUID parentNodeId);

    @Query(value = "SELECT n FROM NetworkModificationNodeInfoEntity n WHERE n.idNode IN (?1) ORDER BY n.columnPosition")
    List<NetworkModificationNodeInfoEntity> findAllByIdIn(List<UUID> uuids);

    @Modifying
    @Query(value = """
        UPDATE NetworkModificationNodeInfoEntity n SET n.sharedActivityStatus = :reason
        WHERE n.idNode IN :nodeUuids AND n.sharedActivityStatus = :sharedIdle
          AND NOT EXISTS (
              SELECT 1 FROM RootNetworkNodeInfoEntity rnni
              WHERE rnni.nodeInfo.idNode IN :localActivityCheckUuids AND rnni.activityStatus <> :idle
          )
          AND NOT EXISTS (
              SELECT 1 FROM NetworkModificationNodeInfoEntity n3
              WHERE n3.idNode IN :sharedActivityCheckUuids AND n3.sharedActivityStatus <> :sharedIdle
          )
        """)
    int acquireSharedActivity(List<UUID> nodeUuids, List<UUID> localActivityCheckUuids, List<UUID> sharedActivityCheckUuids, SharedActivityStatus reason,
                               LocalActivityStatus idle, SharedActivityStatus sharedIdle);

    default int acquireSharedActivity(List<UUID> nodeUuids, List<UUID> localActivityCheckUuids, List<UUID> sharedActivityCheckUuids, SharedActivityStatus reason) {
        return acquireSharedActivity(nodeUuids, localActivityCheckUuids, sharedActivityCheckUuids, reason, LocalActivityStatus.IDLE, SharedActivityStatus.IDLE);
    }

    @Modifying
    @Query("UPDATE NetworkModificationNodeInfoEntity n SET n.sharedActivityStatus = :idle WHERE n.idNode IN :nodeUuids")
    void releaseSharedActivity(List<UUID> nodeUuids, SharedActivityStatus idle);

    default void releaseSharedActivity(List<UUID> nodeUuids) {
        releaseSharedActivity(nodeUuids, SharedActivityStatus.IDLE);
    }
}
