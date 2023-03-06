/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.DynaWaltzParametersInfos;

import java.io.UncheckedIOException;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "name",
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        visible = true
)@JsonSubTypes({
    @JsonSubTypes.Type(value = DynaWaltzParametersInfos.class, name = "DynaWaltzParameters")})
public interface DynamicSimulationExtension {
    String getName();

    static List<DynamicSimulationExtension> parseJson(String json) {

        ObjectMapper objectMapper = new ObjectMapper();
        List<DynamicSimulationExtension> extensions;
        try {
            extensions = objectMapper.readValue(json, new TypeReference<List<DynamicSimulationExtension>>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return extensions;
    }

    static String toJson(List<DynamicSimulationExtension> extensions) {

        ObjectMapper objectMapper = new ObjectMapper();
        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(extensions);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return json;
    }
}
