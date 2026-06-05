/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public interface NetworkModificationNodeInfoRepository extends NodeInfoRepository<NetworkModificationNodeInfoEntity> {
    List<AbstractNodeInfoEntity> findAllByNodeStudyIdAndName(UUID studyUuid, String name);

    List<NetworkModificationNodeInfoEntity> findByModificationGroupUuidIn(List<UUID> modificationGroupUuid);

    @Query("select n.columnPosition from NetworkModificationNodeInfoEntity n where n.idNode in :uuids")
    List<Integer> findColumnPositionsByUuidIn(List<UUID> uuids);

    @Query(value = "SELECT n FROM NetworkModificationNodeInfoEntity n WHERE n.idNode IN (?1) ORDER BY n.columnPosition")
    List<NetworkModificationNodeInfoEntity> findAllByIdIn(List<UUID> uuids);
}
