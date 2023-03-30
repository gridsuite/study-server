/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import com.powsybl.commons.parameters.ParameterType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.dto.ParameterInfos;

import javax.persistence.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "loadFlowSpecificParameters")
public class LoadFlowSpecificParametersEntity {
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

    @Column(name = "type")
    private ParameterType type;

    @Column(name = "description")
    private String description;

    @Column(name = "possibleValues")
    private String possibleValues;

    public static List<ParameterInfos> fromLoadFlowSpecificParameters(List<LoadFlowSpecificParametersEntity> params) {
        return params == null ? null :
            params.stream()
                .map(LoadFlowSpecificParametersEntity::toParameterInfos)
                .collect(Collectors.toList());
    }

    public static List<LoadFlowSpecificParametersEntity> toLoadFlowSpecificParameters(List<ParameterInfos> params) {
        return params == null ? null
            : params.stream()
            .map(p -> {
                String possibleValues = null;
                if (p.getPossibleValues() != null) {
                    possibleValues = String.join(",", p.getPossibleValues());
                }
                return new LoadFlowSpecificParametersEntity(null, p.getProvider(), p.getName(), p.getValue(),
                        p.getType(), p.getDescription(), possibleValues);
            })
            .collect(Collectors.toList());
    }

    public ParameterInfos toParameterInfos() {
        List<String> values = null;
        if (getPossibleValues() != null) {
            values = Arrays.stream(getPossibleValues().trim().split("\\s*,\\s*")).collect(Collectors.toList());
        }
        return ParameterInfos.builder()
            .provider(getProvider())
            .name(getName())
            .value(getValue())
            .type(getType())
            .description(getDescription())
            .possibleValues(values)
            .build();
    }
}
