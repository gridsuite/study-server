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
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "event", indexes = {@Index(name = "event_node_id_index", columnList = "node_id")})
@EntityListeners(AuditingEntityListener.class)
public class EventEntity extends AbstractAuditableEntity<UUID> implements Serializable {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "equipment_id")
    private String equipmentId;

    @Column(name = "equipment_type")
    private String equipmentType;

    @Column(name = "event_type")
    private String eventType;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EventPropertyEntity> properties = new HashSet<>();

    @Column(name = "node_id")
    private UUID nodeId; // weak reference to node id of NodeEntity

    public EventEntity(EventInfos event) {
        if (event.getId() == null) {
            this.id = UUID.randomUUID();
        } else {
            this.id =  event.getId();
            this.markNotNew();
        }
        this.nodeId = event.getNodeId();
        this.equipmentId = event.getEquipmentId();
        this.equipmentType = event.getEquipmentType();
        this.eventType = event.getEventType();
        this.properties.addAll(event.getProperties().stream()
                .map(eventProperty -> new EventPropertyEntity(this, eventProperty))
                .toList());
    }
}
