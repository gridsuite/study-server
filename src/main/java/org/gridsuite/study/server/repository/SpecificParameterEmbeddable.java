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

import javax.persistence.Column;
import javax.persistence.Embeddable;
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
@Embeddable
public class SpecificParameterEmbeddable {
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

    public static List<ParameterInfos> fromSpecificParameterEmbeddable(List<SpecificParameterEmbeddable> params) {
        return params == null ? null :
            params.stream()
                .map(SpecificParameterEmbeddable::toParameterInfos)
                .collect(Collectors.toList());
    }

    public static List<SpecificParameterEmbeddable> toSpecificParameterEmbeddable(List<ParameterInfos> params) {
        return params == null ? null
            : params.stream()
            .map(p -> {
                String possibleValues = null;
                if (p.getPossibleValues() != null) {
                    possibleValues = String.join(",", p.getPossibleValues());
                }
                return new SpecificParameterEmbeddable(p.getProvider(), p.getName(), p.getValue(),
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
