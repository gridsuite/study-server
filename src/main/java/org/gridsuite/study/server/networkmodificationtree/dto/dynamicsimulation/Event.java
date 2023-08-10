/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.dto.dynamicsimulation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.networkmodificationtree.entities.dynamicsimulation.EventEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Event {

    private String equipmentType;

    private String eventType;

    private int eventOrder;

    private List<EventProperty> properties = new ArrayList<>();

    public Event(EventEntity eventEntity) {
        this.equipmentType = eventEntity.getEquipmentType();
        this.eventType = eventEntity.getEventType();
        this.eventOrder = eventEntity.getEventOrder();
        this.properties = eventEntity.getProperties().stream()
                .map(eventPropertyEntity -> new EventProperty(eventPropertyEntity.getName(), eventPropertyEntity.getValue(), eventPropertyEntity.getType()))
                .collect(Collectors.toList());
    }

}
