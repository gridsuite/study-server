/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.dynamicsimulation;

import org.gridsuite.study.server.repository.dynamicsimulation.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    List<EventEntity> findAllByNodeId(UUID nodeId);

    EventEntity findByNodeIdAndEquipmentId(UUID nodeId, String equipmentId);

    Long deleteByNodeId(UUID nodeId);
}
