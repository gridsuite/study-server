/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.LoadFlowSpecificParameterInfos;

import javax.persistence.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table(name = "loadFlowSpecificParameters")
public class LoadFlowSpecificParameterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "provider")
    private String provider;

    @Column(name = "name")
    private String name;

    @Column(name = "value_")
    private String value;

    public static List<LoadFlowSpecificParameterEntity> toLoadFlowSpecificParameters(List<LoadFlowSpecificParameterInfos> params) {
        return params == null ? null
            : params.stream()
            .map(p -> new LoadFlowSpecificParameterEntity(null, p.getProvider(), p.getName(), p.getValue()))
            .collect(Collectors.toList());
    }

    public LoadFlowSpecificParameterInfos toLoadFlowSpecificParameterInfos() {
        return LoadFlowSpecificParameterInfos.builder()
            .provider(getProvider())
            .name(getName())
            .value(getValue())
            .build();
    }
}
