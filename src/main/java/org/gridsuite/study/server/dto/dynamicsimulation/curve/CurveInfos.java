/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.curve;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurveInfos {
    private String equipmentId;
    private String variableId;

    public static List<CurveInfos> parseJson(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<CurveInfos> curves;
        try {
            curves = objectMapper.readValue(json, new TypeReference<List<CurveInfos>>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return curves;
    }

    public static String toJson(List<CurveInfos> curves) {
        ObjectMapper objectMapper = new ObjectMapper();
        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(curves);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return json;
    }
}
