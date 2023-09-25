/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventPropertyInfos;
import org.gridsuite.study.server.repository.dynamicsimulation.EventRepository;
import org.gridsuite.study.server.repository.dynamicsimulation.entity.EventEntity;
import org.gridsuite.study.server.utils.PropertyType;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DisableElasticsearch
public class DynamicSimulationEventServiceTest {

    private static final String NODE_UUID_STRING = "00000000-0000-0000-0000-000000000000";
    public static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);
    private static final String EVENT_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final UUID EVENT_UUID = UUID.fromString(EVENT_UUID_STRING);
    public static final String EQUIPMENT_ID = "_BUS____1-BUS____5-1_AC";
    public static final EventInfos EVENT = new EventInfos(null, NODE_UUID, EQUIPMENT_ID, "LINE", "Disconnect", List.of(
            new EventPropertyInfos(null, "staticId", EQUIPMENT_ID, PropertyType.STRING),
            new EventPropertyInfos(null, "startTime", "10", PropertyType.FLOAT),
            new EventPropertyInfos(null, "disconnectOnly", "Branch.Side.ONE", PropertyType.ENUM)

    ));
    private static final String EVENT_2_UUID_STRING = "22222222-0000-0000-0000-000000000000";
    private static final UUID EVENT_2_UUID = UUID.fromString(EVENT_2_UUID_STRING);

    @Autowired
    EventRepository eventRepository;

    @Autowired
    DynamicSimulationEventService dynamicSimulationEventService;

    public void cleanDB() {
        eventRepository.deleteAll();
    }

    @Before
    public void setup() {
        cleanDB();
        // init some event by inject directly
        EventEntity event = new EventEntity(EVENT);
        eventRepository.saveAll(List.of(event));
    }

    @Test
    public void testGetEventsByNodeId() {
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
    public void testGetEventByNodeIdAndEquipmentId() {
        // UUID nodeUuid, String equipmentId
        // call method to be tested
        EventInfos eventResult = dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(NODE_UUID, EQUIPMENT_ID);

        // check result
        // same event type
        assertEquals(EVENT.getEventType(), eventResult.getEventType());
        // same number of properties
        assertEquals(EVENT.getProperties().size(), eventResult.getProperties().size());
    }

    @Test
    public void testSaveEvent() {
        cleanDB();
        // UUID nodeUuid, EventInfos event
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
    public void testDeleteEvents() {
        // List<UUID> eventUuids
        EventInfos eventResult = dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(NODE_UUID, EQUIPMENT_ID);
        // call method to be tested
        dynamicSimulationEventService.deleteEvents(List.of(eventResult.getId()));

        // check result
        List<EventInfos> eventResultList = dynamicSimulationEventService.getEventsByNodeId(NODE_UUID);
        // no event in the db
        assertEquals(0, eventResultList.size());
    }

    @Test
    public void testDeleteEventsByNodeId(){
        // UUID nodeUuid
        // call method to be tested
        dynamicSimulationEventService.deleteEventsByNodeId(NODE_UUID);

        // check result
        List<EventInfos> eventResultList = dynamicSimulationEventService.getEventsByNodeId(NODE_UUID);
        // no event in the db
        assertEquals(0, eventResultList.size());
    }

}
