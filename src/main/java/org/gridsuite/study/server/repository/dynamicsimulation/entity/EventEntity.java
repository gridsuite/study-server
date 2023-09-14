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
import org.gridsuite.study.server.repository.AbstractManuallyAssignedIdentifierEntity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

import static javax.persistence.TemporalType.TIMESTAMP;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "event", indexes = {@Index(name = "event_node_id_index", columnList = "node_id")})
public class EventEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {
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

    @CreatedDate
    @Temporal(TIMESTAMP)
    @Column(name = "created_date", updatable = false)
    private Date createdDate;

    @LastModifiedDate
    @Temporal(TIMESTAMP)
    @Column(name = "updated_date")
    private Date updatedDate;

    public EventEntity(EventInfos event) {
        this.id = event.getId() == null ? UUID.randomUUID() : event.getId();
        this.nodeId = event.getNodeId();
        this.equipmentId = event.getEquipmentId();
        this.equipmentType = event.getEquipmentType();
        this.eventType = event.getEventType();
        this.properties = event.getProperties() != null ? event.getProperties().stream()
                .map(eventProperty -> new EventPropertyEntity(this, eventProperty))
                .collect(Collectors.toSet()) : null;
    }
}
