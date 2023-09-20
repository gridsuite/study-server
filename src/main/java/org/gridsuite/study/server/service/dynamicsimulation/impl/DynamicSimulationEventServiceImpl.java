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
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public List<EventInfos> getEvents(UUID nodeUuid) {
        return eventRepository.findAllByNodeId(nodeUuid).stream().map(EventInfos::new).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public EventInfos getEvent(UUID nodeUuid, String equipmentId) {
        EventEntity eventEntity = eventRepository.findByNodeIdAndEquipmentId(nodeUuid, equipmentId);

        if (eventEntity == null) {
            return null;
        } else {
            return new EventInfos(eventEntity);
        }
    }

    @Transactional
    @Override
    public void createEvent(UUID nodeUuid, EventInfos event) {
        EventEntity eventEntity = new EventEntity(event);
        eventRepository.save(eventEntity);
    }

    @Transactional
    @Override
    public void updateEvent(UUID nodeUuid, EventInfos event) {

        Optional<EventEntity> eventEntityOpt = eventRepository.findById(event.getId());

        eventEntityOpt.ifPresent(eventEntityToUpdate -> {
            // merge entity's values with ones in the dto
            EventEntity eventEntityFromDto = new EventEntity(event);

            // replace completely by all new properties for the simplicity
            if (!CollectionUtils.isEmpty(eventEntityFromDto.getProperties())) {
                if (eventEntityToUpdate.getProperties() != null) {
                    eventEntityToUpdate.getProperties().clear();
                    eventEntityToUpdate.getProperties().addAll(eventEntityFromDto.getProperties());
                } else {
                    eventEntityToUpdate.setProperties(eventEntityFromDto.getProperties());
                }
            }

            // other fields actually immutable => nothing to reassign for the entity

            eventRepository.save(eventEntityToUpdate);
        });
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
