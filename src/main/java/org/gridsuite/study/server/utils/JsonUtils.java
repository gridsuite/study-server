package org.gridsuite.study.server.utils;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.NonNull;

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
}
