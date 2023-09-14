/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;

import java.util.List;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface DynamicSimulationEventService {

    List<EventInfos> getEvents(UUID nodeUuid);

    EventInfos getEvent(UUID nodeUuid, String equipmentId);

    void createEvent(UUID nodeUuid, EventInfos event);

    void updateEvent(UUID nodeUuid, EventInfos event);

    void deleteEvents(UUID nodeUuid, List<UUID> eventUuids);
}
