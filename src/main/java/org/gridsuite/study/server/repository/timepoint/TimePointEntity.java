/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.timepoint;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;

import java.util.List;
import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "timePoint")
public class TimePointEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // many to one : one study can be related to many timepoints MAISSA
    //@ManyToOne(fetch = FetchType.LAZY) to add ? MAISSA
    //    @JoinColumn(name = "studyUuid", foreignKey = @ForeignKey(name = "time_point_entity_fk"))
    @ManyToOne
    @JoinColumn(name = "studyUuid")
    private StudyEntity study;

    // One TimePoint can have multiple TimePointNodeInfo entries
    @OneToMany(mappedBy = "timePoint", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimePointNodeInfoEntity> timePointNodeInfos;

    //added column  ==> moved from StudyEntity  to TimePointEntity  MAISSA
    @Column(name = "networkUuid", nullable = false)
    private UUID networkUuid;

    @Column(name = "networkId", nullable = false) //this  MAISSA
    private String networkId;

    @Column(name = "caseFormat", nullable = false) //this  MAISSA
    private String caseFormat;

    @Column(name = "caseUuid", nullable = false) //this  MAISSA
    private UUID caseUuid;

    @Column(name = "caseName", nullable = false) //this  MAISSA
    private String caseName;

    // reportUuid of network import, root node one
    @Column(name = "reportUuid")
    private UUID reportUuid;
    //@Enumerated(EnumType.STRING)
    //@Builder.Default
    //private StudyIndexationStatus indexationStatus = StudyIndexationStatus.NOT_INDEXED; //this  MAISSA SHOULD NOT MOVE IT
}
