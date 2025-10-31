/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.dynamicsimulation;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventPropertyInfos;
import org.gridsuite.study.server.repository.dynamicsimulation.EventRepository;
import org.gridsuite.study.server.repository.dynamicsimulation.entity.EventEntity;
import org.gridsuite.study.server.utils.PropertyType;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class DynamicSimulationEventServiceTest {

    private static final String NODE_UUID_STRING = "00000000-0000-0000-0000-000000000000";
    private static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);
    private static final String EQUIPMENT_ID = "_BUS____1-BUS____5-1_AC";
    private static final EventInfos EVENT = new EventInfos(null, NODE_UUID, EQUIPMENT_ID, "LINE", "Disconnect", List.of(
            new EventPropertyInfos(null, "staticId", EQUIPMENT_ID, PropertyType.STRING),
            new EventPropertyInfos(null, "startTime", "10", PropertyType.FLOAT),
            new EventPropertyInfos(null, "disconnectOnly", "TwoSides.ONE", PropertyType.ENUM)
    ));

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DynamicSimulationEventService dynamicSimulationEventService;

    @AfterEach
    void cleanDB() {
        eventRepository.deleteAll();
    }

    @BeforeEach
    void setup() {
        // init some event by inject directly
        EventEntity event = new EventEntity(EVENT);
        eventRepository.saveAll(List.of(event));
    }

    @Test
    void testGetEventsByNodeId() {
        // call method to be tested
        List<EventInfos> eventResultList = dynamicSimulationEventService.getEventsByNodeId(NODE_UUID);

        // check result
        // only one event
        assertEquals(1, eventResultList.size());

        EventInfos eventInfosResult = eventResultList.get(0);
        // same event type
        assertEquals(EVENT.getEventType(), eventInfosResult.getEventType());
        // same number of properties
        assertEquals(EVENT.getProperties().size(), eventInfosResult.getProperties().size());
    }

    @Test
    void testGetEventByNodeIdAndEquipmentId() {
        // call method to be tested
        EventInfos eventResult = dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(NODE_UUID, EQUIPMENT_ID);

        // check result
        // same event type
        assertEquals(EVENT.getEventType(), eventResult.getEventType());
        // same number of properties
        assertEquals(EVENT.getProperties().size(), eventResult.getProperties().size());
    }

    @Test
    void testCreateEvent() {
        cleanDB();
        // call method to be tested
        dynamicSimulationEventService.saveEvent(NODE_UUID, EVENT);

        // retrieve the saved event
        List<EventInfos> eventResultList = dynamicSimulationEventService.getEventsByNodeId(NODE_UUID);

        // check result
        // only one event
        assertEquals(1, eventResultList.size());

        EventInfos eventInfosResult = eventResultList.get(0);
        // same event type
        assertEquals(EVENT.getEventType(), eventInfosResult.getEventType());
        // same number of properties
        assertEquals(EVENT.getProperties().size(), eventInfosResult.getProperties().size());
    }

    @Test
    void testUpdateEvent() {
        EventInfos eventToUpdate = dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(NODE_UUID, EQUIPMENT_ID);

        // modify the event then save the change
        Optional<EventPropertyInfos> startTimePropertyOpt = eventToUpdate.getProperties().stream().filter(elem -> elem.getName().equals("startTime")).findFirst();
        startTimePropertyOpt.ifPresent(elem -> elem.setValue("20"));

        // call method to be tested
        dynamicSimulationEventService.saveEvent(NODE_UUID, eventToUpdate);

        // retrieve the saved event
        EventInfos eventResult = dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(NODE_UUID, EQUIPMENT_ID);

        // check result
        // same event type
        assertEquals(EVENT.getEventType(), eventResult.getEventType());
        // same number of properties
        assertEquals(EVENT.getProperties().size(), eventResult.getProperties().size());
        // check start time property
        Optional<EventPropertyInfos> startTimePropertyResultOpt = eventResult.getProperties().stream().filter(elem -> elem.getName().equals("startTime")).findFirst();
        assertEquals("20", startTimePropertyResultOpt.get().getValue());
    }

    @Test
    void testDeleteEvents() {
        EventInfos eventResult = dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(NODE_UUID, EQUIPMENT_ID);
        // call method to be tested
        dynamicSimulationEventService.deleteEvents(List.of(eventResult.getId()));

        // check result
        List<EventInfos> eventResultList = dynamicSimulationEventService.getEventsByNodeId(NODE_UUID);
        // no event in the db
        assertEquals(0, eventResultList.size());
    }

    @Test
    void testDeleteEventsByNodeId() {
        // call method to be tested
        dynamicSimulationEventService.deleteEventsByNodeId(NODE_UUID);

        // check result
        List<EventInfos> eventResultList = dynamicSimulationEventService.getEventsByNodeId(NODE_UUID);
        // no event in the db
        assertEquals(0, eventResultList.size());
    }
}
