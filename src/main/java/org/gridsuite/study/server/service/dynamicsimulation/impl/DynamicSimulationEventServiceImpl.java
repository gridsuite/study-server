/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation.impl;

import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.repository.dynamicsimulation.EventRepository;
import org.gridsuite.study.server.repository.dynamicsimulation.entity.EventEntity;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationEventServiceImpl implements DynamicSimulationEventService {

    private final EventRepository eventRepository;

    public DynamicSimulationEventServiceImpl(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<EventInfos> getEventsByNodeId(UUID nodeUuid) {
        return eventRepository.findAllByNodeId(nodeUuid).stream().map(EventInfos::new).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public EventInfos getEventByNodeIdAndEquipmentId(UUID nodeUuid, String equipmentId) {
        EventEntity eventEntity = eventRepository.findByNodeIdAndEquipmentId(nodeUuid, equipmentId);
        return Optional.ofNullable(eventEntity).map(EventInfos::new).orElse(null);
    }

    @Transactional
    @Override
    public void saveEvent(UUID nodeUuid, EventInfos event, String userId) {
        event.setNodeId(nodeUuid);
        EventEntity eventEntity = new EventEntity(event, userId);
        eventRepository.save(eventEntity);
    }

    @Transactional
    @Override
    public void deleteEvents(List<UUID> eventUuids) {
        eventRepository.deleteAllById(eventUuids);
    }

    @Transactional
    @Override
    public void deleteEventsByNodeId(UUID nodeUuid) {
        eventRepository.deleteByNodeId(nodeUuid);
    }
}
