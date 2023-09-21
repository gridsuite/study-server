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
import org.gridsuite.study.server.repository.dynamicsimulation.entity.EventPropertyEntity;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
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

        return eventEntity != null ? new EventInfos(eventEntity) : null;
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

        // update event => do only merge properties, other fields are immutables
        if (CollectionUtils.isEmpty(event.getProperties())) {
            return;
        }

        Optional<EventEntity> eventEntityOpt = eventRepository.findById(event.getId());

        eventEntityOpt.ifPresent(eventEntityToUpdate -> {
            // merge entity's values with ones in the dto
            EventEntity eventEntityFromDto = new EventEntity(event);

            // 1 - remove properties which do not exist from the dto
            List<String> propertyNamesFromDto = eventEntityFromDto.getProperties().stream().map(EventPropertyEntity::getName).toList();
            eventEntityToUpdate.getProperties().removeIf(property -> !propertyNamesFromDto.contains(property.getName()));

            // 2 - set value from dto for remaining properties
            eventEntityToUpdate.getProperties().forEach(property -> eventEntityFromDto.getProperties().stream()
                .filter(elem -> Objects.equals(elem.getName(), property.getName()))
                .findFirst().ifPresent(elem -> property.setValue(elem.getValue())));

            // 3 - add new properties from dto
            List<String> propertyNamesFromUpdate = eventEntityToUpdate.getProperties().stream().map(EventPropertyEntity::getName).toList();
            eventEntityFromDto.getProperties().removeIf(property -> propertyNamesFromUpdate.contains(property.getName()));
            eventEntityToUpdate.getProperties().addAll(eventEntityFromDto.getProperties());

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
