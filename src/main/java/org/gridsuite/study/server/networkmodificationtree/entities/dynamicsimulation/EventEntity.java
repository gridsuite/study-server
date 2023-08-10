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
import org.gridsuite.study.server.networkmodificationtree.dto.dynamicsimulation.Event;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
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
@Table(name = "event")
public class EventEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "equipmentType")
    private String equipmentType;

    @Column(name = "eventType")
    private String eventType;

    @Column(name = "eventOrder")
    private int eventOrder;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EventPropertyEntity> properties = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idNode", foreignKey = @ForeignKey(name = "node_info_event_fk"))
    private NetworkModificationNodeInfoEntity nodeInfo;

    @CreatedDate
    @Temporal(TIMESTAMP)
    @Column(name = "created_date", updatable = false)
    private Date createdDate;

    @LastModifiedDate
    @Temporal(TIMESTAMP)
    @Column(name = "updated_date")
    private Date updatedDate;

    public EventEntity(NetworkModificationNodeInfoEntity nodeInfo, Event event) {

        UUID newID = UUID.randomUUID();
        this.id = newID;
        this.nodeInfo = nodeInfo;
        this.equipmentType = event.getEquipmentType();
        this.eventType = event.getEventType();
        this.eventOrder = event.getEventOrder();
        this.properties = event.getProperties() != null ? event.getProperties().stream()
                .map(eventProperty -> new EventPropertyEntity(newID, eventProperty))
                .collect(Collectors.toSet()) : null;

    }
}
