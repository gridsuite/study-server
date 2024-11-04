/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.rootnetwork;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;

import java.util.ArrayList;
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
@Table(name = "rootNetwork")
public class RootNetworkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "studyUuid")
    private StudyEntity study;

    @OneToMany(orphanRemoval = true, mappedBy = "rootNetwork", cascade = CascadeType.ALL)
    private List<RootNetworkNodeInfoEntity> rootNetworkNodeInfos;

    @Column(name = "networkUuid", nullable = false)
    private UUID networkUuid;

    @Column(name = "networkId", nullable = false)
    private String networkId;

    @Column(name = "caseFormat", nullable = false)
    private String caseFormat;

    @Column(name = "caseUuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "caseName", nullable = false)
    private String caseName;

    // reportUuid of network import, root node one
    @Column(name = "reportUuid")
    private UUID reportUuid;

    public void addRootNetworkNodeInfo(RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        if (rootNetworkNodeInfos == null) {
            rootNetworkNodeInfos = new ArrayList<>();
        }
        rootNetworkNodeInfoEntity.setRootNetwork(this);
        rootNetworkNodeInfos.add(rootNetworkNodeInfoEntity);
    }
}