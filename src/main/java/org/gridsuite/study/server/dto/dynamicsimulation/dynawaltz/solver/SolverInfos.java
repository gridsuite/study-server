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
    /*
    static List<SolverInfos> parseJson(JsonParser parser) {
        return parseJson(parser, false);
    }

    static List<SolverInfos> parseJson(JsonParser parser, boolean single) {
        Objects.requireNonNull(parser);
        List<SolverInfos> solverList = new ArrayList<>();
        try {
            JsonToken token;
            String id = null;
            String type = null;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME) {
                    String fieldName = parser.getCurrentName();
                    switch (fieldName) {
                        case "id" :
                            id = parser.nextTextValue();
                            break;
                        case "type" :
                            type = parser.nextTextValue();
                            SolverInfos solver = parseOthers(parser, id, type);
                            if (solver != null) {
                                solverList.add(solver);
                            }
                            id = null;
                            break;
                        default:
                            break;
                    }
                } else if (token == JsonToken.END_OBJECT && single) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return solverList;
    }

    static SolverInfos parseOthers(JsonParser parser, String id, String type) {
        SolverTypeInfos solverType = SolverTypeInfos.valueOf(type);
        switch (solverType) {
            case IDA:
                // TODO implement each field
                return new IdaSolverInfos();
            case SIM:
                // TODO implement each field
                return new SimSolverInfos();
            default:
                throw new StudyException(DYNAMIC_SIMULATION_SOLVER_TYPE_UNEXPECTED, String.format("Unexpected solver type: %s", type));
        }
    }

    static void writeJson(JsonGenerator generator, List<? extends SolverInfos> solvers) {
        Objects.requireNonNull(solvers);
        try {
            generator.writeStartArray();
            for (SolverInfos solver : solvers) {
                solver.writeJson(generator);
            }
            generator.writeEndArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void writeJson(JsonGenerator generator);

    String toJson();
 */

    static List<SolverInfos> parseJson(String json) {
        //return JsonUtil.parseJson(json, SolverInfos::parseJson);
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
        // return JsonUtil.toJson(generator -> writeJson(generator, solvers));
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
