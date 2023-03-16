/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;

import java.util.List;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        visible = true
)@JsonSubTypes({
    @JsonSubTypes.Type(value = IdaSolverInfos.class, name = "IDA"),
    @JsonSubTypes.Type(value = SimSolverInfos.class, name = "SIM")})
public interface SolverInfos {
    String getId();

    SolverTypeInfos getType();

    static List<SolverInfos> parseJson(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<SolverInfos> solvers;
        try {
            solvers = objectMapper.readValue(json, new TypeReference<List<SolverInfos>>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return solvers;
    }

    static String toJson(List<SolverInfos> solvers) {
        ObjectMapper objectMapper = new ObjectMapper();
        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(solvers);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return json;
    }
}
