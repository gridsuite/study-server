/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public interface NetworkModificationNodeInfoRepository extends NodeInfoRepository<NetworkModificationNodeInfoEntity> {
    List<AbstractNodeInfoEntity> findAllByNodeStudyIdAndName(UUID studyUuid, String name);

    AbstractNodeInfoEntity findByModificationGroupUuid(UUID modificationGroupUuid);
}
