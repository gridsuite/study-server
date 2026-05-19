/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.error.StudyException;
import org.springframework.data.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.UNPROCESSABLE_IMPORT_PARAMETER;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * search and return the node found from the pointers/paths
     * @param node the node to search from
     * @param pointers the paths to search from the {@code node}
     * @return the first found path
     */
    public static JsonNode nodeAt(@NonNull final JsonNode node, final Predicate<JsonNode> validator, @NonNull final JsonPointer... pointers) {
        if (pointers.length < 1) {
            throw new NullPointerException("No JsonPointer argument(s)");
        }
        JsonNode result = MissingNode.getInstance();
        for (final JsonPointer jsonPointer : pointers) {
            if (!validator.test(result)) {
                result = node.at(jsonPointer);
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * search and return the node found from the pointers/paths
     * @param node the node to search from
     * @param pointers the paths to search from the {@code node}
     * @return the first found path
     */
    public static JsonNode nodeAt(@NonNull final JsonNode node, @NonNull final JsonPointer... pointers) {
        return nodeAt(node, jsonNode -> !jsonNode.isMissingNode(), pointers);
    }

    public static String getModificationContextJsonString(Pair<String, List<ModificationApplicationContext>> modificationContextInfos) {
        try {
            ObjectNode modificationJson = (ObjectNode) MAPPER.readTree(modificationContextInfos.getFirst());
            ObjectNode modificationContextJson = MAPPER.valueToTree(modificationContextInfos);
            modificationContextJson.set(Pair.class.getDeclaredField("first").getName(), modificationJson);
            return modificationContextJson.toString();
        } catch (JsonProcessingException | NoSuchFieldException e) {
            throw new IllegalStateException("Impossible to parse modification context", e);
        }
    }

    public static Map<String, Object> deserializeImportParameters(Map<String, String> rawParams) {
        Map<String, Object> result = new HashMap<>();
        if (rawParams == null) {
            return result;
        }

        rawParams.forEach((key, value) -> {
            if (value == null) {
                result.put(key, null);
                return;
            }
            try {
                result.put(key, MAPPER.readValue(value, Object.class));
            } catch (JsonProcessingException e) {
                throw new StudyException(UNPROCESSABLE_IMPORT_PARAMETER, "Import parameter '" + key + " => " + value + "' is not valid JSON: " + e.getMessage());
            }
        });
        return result;
    }

    public static Map<String, String> serializeImportParameters(Map<String, Object> params) {
        Map<String, String> result = new HashMap<>();
        if (params == null) {
            return result;
        }

        params.forEach((key, value) -> {
            try {
                result.put(key, MAPPER.writeValueAsString(value));
            } catch (JsonProcessingException e) {
                throw new StudyException(UNPROCESSABLE_IMPORT_PARAMETER, "Import parameter '" + key + " => " + value + "' is not serializable: " + e.getMessage());
            }
        });
        return result;
    }
}
