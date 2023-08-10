/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.entities.dynamicsimulation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.networkmodificationtree.dto.dynamicsimulation.EventProperty;
import org.gridsuite.study.server.utils.PropertyType;

import javax.persistence.*;
import java.io.Serializable;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "event_property", indexes = {@Index(name = "property_event_id_index", columnList = "event_id")})
@IdClass(EventPropertyId.class)
public class EventPropertyEntity implements Serializable {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "value_")
    private String value;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private PropertyType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "event_property_fk"))
    @MapsId("eventId")
    private EventEntity event;

    public EventPropertyEntity(UUID eventId, EventProperty eventProperty) {
        this.eventId = eventId;
        this.name = eventProperty.getName();
        this.value = eventProperty.getValue();
        this.type = eventProperty.getType();
    }
}
