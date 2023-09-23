/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.repository.dynamicsimulation.entity.EventEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EventInfos {

    @JsonProperty("uuid")
    private UUID id;

    private UUID nodeId;

    private String equipmentId;

    private String equipmentType;

    private String eventType;

    private List<EventPropertyInfos> properties = new ArrayList<>();

    public EventInfos(EventEntity eventEntity) {
        this.id = eventEntity.getId();
        this.nodeId = eventEntity.getNodeId();
        this.equipmentId = eventEntity.getEquipmentId();
        this.equipmentType = eventEntity.getEquipmentType();
        this.eventType = eventEntity.getEventType();
        this.properties.addAll(eventEntity.getProperties().stream()
                .map(eventPropertyEntity -> new EventPropertyInfos(
                        eventPropertyEntity.getId(),
                        eventPropertyEntity.getName(),
                        eventPropertyEntity.getValue(),
                        eventPropertyEntity.getType()))
                .toList());
    }

}
