/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.dynamicsimulation.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventPropertyInfos;
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
@Table(name = "event_property",
    indexes = {@Index(name = "property_event_id_index", columnList = "event_id")},
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "name"}))
public class EventPropertyEntity implements Serializable {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "value_")
    private String value;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private PropertyType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "event_property_fk"))
    private EventEntity event;

    public EventPropertyEntity(EventEntity event, EventPropertyInfos eventProperty) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.name = eventProperty.getName();
        this.value = eventProperty.getValue();
        this.type = eventProperty.getType();
    }
}
