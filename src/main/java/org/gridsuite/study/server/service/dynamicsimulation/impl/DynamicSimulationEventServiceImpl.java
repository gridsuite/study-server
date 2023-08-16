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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        // set event order = maxEventOrder(existing event orders) + 1
        Integer maxEventOrder = eventRepository.maxEventOrder(nodeUuid);
        event.setEventOrder(maxEventOrder == null ? 0 : maxEventOrder + 1);

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
    public void moveEvent(UUID nodeUuid, UUID eventUuid, UUID beforeUuid) {
        List<EventEntity> eventEntities = eventRepository.findAllByNodeId(nodeUuid);
        eventEntities.sort(Comparator.comparingInt(EventEntity::getEventOrder));

        Optional<EventEntity> beforeEntityOpt = beforeUuid != null ?
                eventEntities.stream().filter(elem -> elem.getId().equals(beforeUuid)).findFirst()
                : eventEntities.isEmpty() ? Optional.empty()
                : Optional.of(eventEntities.get(eventEntities.size() - 1)) /* get last elem if beforeUuid not provided */;

        Optional<EventEntity> eventEntityOpt = eventEntities.stream().filter(elem -> elem.getId().equals(eventUuid)).findFirst();

        beforeEntityOpt.ifPresent(beforeEntity ->
            eventEntityOpt.ifPresent(eventEntity -> {
                int insertIndex = eventEntities.indexOf(beforeEntity);
                if (insertIndex != -1) {
                    eventEntities.remove(eventEntity);
                    eventEntities.add(insertIndex, eventEntity);

                    // reset order event
                    IntStream.range(0, eventEntities.size()).forEach(order -> eventEntities.get(order).setEventOrder(order));

                    eventRepository.saveAll(eventEntities);
                }
            })
        );
    }

    @Transactional
    @Override
    public void deleteEvents(UUID nodeUuid, List<UUID> eventUuids) {
        eventRepository.deleteAllById(eventUuids);

        List<EventEntity> remainingEntities = eventRepository.findAllByNodeId(nodeUuid);
        remainingEntities.sort(Comparator.comparingInt(EventEntity::getEventOrder));

        // reset order event
        IntStream.range(0, remainingEntities.size()).forEach(order -> remainingEntities.get(order).setEventOrder(order));

        eventRepository.saveAll(remainingEntities);
    }
}
