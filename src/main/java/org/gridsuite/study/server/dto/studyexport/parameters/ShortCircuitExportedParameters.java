/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.parameters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.studyexport.ExportedParameters;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.study.server.dto.studyexport.ExportedParameters.nullSafe;
import static org.gridsuite.study.server.dto.studyexport.ExportedParameters.toUuidSet;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShortCircuitExportedParameters(Map<String, Map<String, String>> specificParametersPerProvider) implements ExportedParameters {

    public static final String POWER_ELECTRONICS_CLUSTERS = "powerElectronicsClusters";
    public static final String NODE_CLUSTER_FILTER_IDS = "nodeClusterFilterIds";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilterElements(UUID filterId) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PowerElectronicsCluster(List<FilterElements> filters) { }

    @Override
    public Set<UUID> getFilterUuids() {
        Stream<Map<String, String>> specificParameters = specificParametersPerProvider == null ? Stream.empty()
                : specificParametersPerProvider.values().stream().filter(Objects::nonNull);
        return toUuidSet(specificParameters.flatMap(parameters -> Stream.concat(
                        parseList(parameters.get(POWER_ELECTRONICS_CLUSTERS), new TypeReference<List<PowerElectronicsCluster>>() { })
                                .flatMap(cluster -> nullSafe(cluster.filters())),
                        parseList(parameters.get(NODE_CLUSTER_FILTER_IDS), new TypeReference<List<FilterElements>>() { })))
                .map(FilterElements::filterId));
    }

    private static <T> Stream<T> parseList(String json, TypeReference<List<T>> type) {
        if (StringUtils.isBlank(json)) {
            return Stream.empty();
        }
        try {
            return nullSafe(OBJECT_MAPPER.readValue(json, type)).filter(Objects::nonNull);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
