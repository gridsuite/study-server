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
import java.util.Map;
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
@Table(name = "rootNetwork",
        indexes = {@Index(name = "rootNetworkEntity_studyId_idx", columnList = "study_uuid")})
public class RootNetworkEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studyUuid", foreignKey = @ForeignKey(name = "rootNetwork_study_id_fk_constraint"))
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

    @ElementCollection
    @CollectionTable(name = "importParameters",
        indexes = {@Index(name = "rootNetworkEntity_importParameters_idx1", columnList = "root_network_entity_id")},
        foreignKey = @ForeignKey(name = "rootNetworkEntity_importParameters_fk1"))
    private Map<String, String> importParameters;

    public void addRootNetworkNodeInfo(RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        if (rootNetworkNodeInfos == null) {
            rootNetworkNodeInfos = new ArrayList<>();
        }
        rootNetworkNodeInfoEntity.setRootNetwork(this);
        rootNetworkNodeInfos.add(rootNetworkNodeInfoEntity);
    }
}
