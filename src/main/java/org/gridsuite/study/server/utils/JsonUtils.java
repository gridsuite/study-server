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
import org.springframework.data.util.Pair;

import java.util.List;
import java.util.function.Predicate;

public final class JsonUtils {
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

    public static String getModificationContextJsonString(ObjectMapper objectMapper, Pair<String, List<ModificationApplicationContext>> modificationContextInfos) throws JsonProcessingException, NoSuchFieldException {
        ObjectNode modificationJson = (ObjectNode) objectMapper.readTree(modificationContextInfos.getFirst());
        ObjectNode modificationContextJson = objectMapper.valueToTree(modificationContextInfos);
        modificationContextJson.put(Pair.class.getDeclaredField("first").getName(), modificationJson);
        return modificationContextJson.toString();
    }
}
