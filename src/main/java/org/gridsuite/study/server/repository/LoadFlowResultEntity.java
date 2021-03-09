/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import lombok.*;

import javax.persistence.*;
import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "loadFlowResult")
public class LoadFlowResultEntity {

    public LoadFlowResultEntity(boolean ok, Map<String, String> metrics, String logs, List<ComponentResultEntity> componentResults) {
        this(null, ok, metrics, logs, componentResults);
    }

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "ok")
    private boolean ok;

    @Column(name = "metrics")
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(foreignKey = @ForeignKey(name = "loadFlowResultEntity_metrics_fk"))
    private Map<String, String> metrics;

    @Column(name = "logs", columnDefinition = "TEXT")
    private String logs;

    @OneToMany(fetch = FetchType.LAZY,
            mappedBy = "loadFlowResult",
            cascade = {CascadeType.ALL})
    @Column(name = "componentResults")
    private List<ComponentResultEntity> componentResults = new ArrayList<>();

    public void addComponentResults(ComponentResultEntity componentResultEntity) {
        componentResultEntity.setLoadFlowResult(this);
        componentResults.add(componentResultEntity);
    }
}
