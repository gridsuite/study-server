/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.externalcontract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.study.server.StudySwaggerConfig;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Extracts, from a full OpenAPI document (as produced by springdoc on {@code /v3/api-docs}), a
 * reduced "contract snapshot" of only the endpoints tagged with {@code @ExternalContract} (i.e.
 * exposed via the {@value StudySwaggerConfig#X_EXTERNAL_CONSUMERS_EXTENSION} OpenAPI extension).
 *
 * <p>The snapshot deliberately only retains a shallow view of each endpoint (path, method,
 * consumers, parameters, and the top-level shape of request/response bodies) so that unrelated
 * documentation-only changes (descriptions, examples, nested schema details, ...) do not trigger
 * false positives, while still catching the breaking changes that matter for consumers: a
 * path/method disappearing, a parameter being removed/renamed, or a top-level field disappearing
 * from a request/response payload.
 *
 * @author Abdellah Ghiles <abdellah.ghiles at rte-france.com>
 */
public final class ExternalContractExtractor {
    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete", "options", "head");

    private ExternalContractExtractor() {
    }

    /**
     * @param openApiDocument the full OpenAPI document returned by {@code /v3/api-docs}
     * @return a map of {@code "METHOD /path"} to its contract snapshot, for every operation tagged
     *         with {@code @ExternalContract}
     */
    public static Map<String, JsonNode> extract(JsonNode openApiDocument) {
        Map<String, JsonNode> snapshots = new TreeMap<>();
        JsonNode paths = openApiDocument.path("paths");
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            for (String method : HTTP_METHODS) {
                JsonNode operation = pathItem.get(method);
                if (operation != null && operation.has(StudySwaggerConfig.X_EXTERNAL_CONSUMERS_EXTENSION)) {
                    snapshots.put(method.toUpperCase() + " " + path, buildOperationSnapshot(operation, openApiDocument));
                }
            }
        }
        return snapshots;
    }

    private static ObjectNode buildOperationSnapshot(JsonNode operation, JsonNode openApiDocument) {
        ObjectNode snapshot = JsonNodeFactory.instance.objectNode();
        snapshot.set("consumers", sortedCopy(operation.path(StudySwaggerConfig.X_EXTERNAL_CONSUMERS_EXTENSION)));
        snapshot.set("parameters", buildParametersSnapshot(operation.path("parameters"), openApiDocument));
        if (operation.has("requestBody")) {
            snapshot.set("requestBody", buildContentSnapshot(operation.path("requestBody").path("content"), openApiDocument));
        }
        snapshot.set("responses", buildResponsesSnapshot(operation.path("responses"), openApiDocument));
        return snapshot;
    }

    private static ArrayNode buildParametersSnapshot(JsonNode parameters, JsonNode openApiDocument) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        if (parameters.isArray()) {
            for (JsonNode parameter : parameters) {
                ObjectNode parameterSnapshot = JsonNodeFactory.instance.objectNode();
                parameterSnapshot.put("name", parameter.path("name").asText());
                parameterSnapshot.put("in", parameter.path("in").asText());
                parameterSnapshot.put("required", parameter.path("required").asBoolean(false));
                parameterSnapshot.set("schema", resolveSchema(parameter.path("schema"), openApiDocument));
                result.add(parameterSnapshot);
            }
        }
        return sortArrayNodeByTextField(result, "name");
    }

    private static ObjectNode buildContentSnapshot(JsonNode content, JsonNode openApiDocument) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        Iterator<Map.Entry<String, JsonNode>> mediaTypes = content.fields();
        while (mediaTypes.hasNext()) {
            Map.Entry<String, JsonNode> mediaType = mediaTypes.next();
            result.set(mediaType.getKey(), resolveSchema(mediaType.getValue().path("schema"), openApiDocument));
        }
        return result;
    }

    private static ObjectNode buildResponsesSnapshot(JsonNode responses, JsonNode openApiDocument) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        Iterator<Map.Entry<String, JsonNode>> statusCodes = responses.fields();
        while (statusCodes.hasNext()) {
            Map.Entry<String, JsonNode> statusCode = statusCodes.next();
            JsonNode content = statusCode.getValue().path("content");
            result.set(statusCode.getKey(), content.isMissingNode() ? JsonNodeFactory.instance.objectNode() : buildContentSnapshot(content, openApiDocument));
        }
        return result;
    }

    /**
     * Resolves a schema (following a single {@code $ref} if present) to a shallow description:
     * its type, the name of the referenced schema if any, and - for objects - the sorted list of
     * its top-level property names, or - for arrays - the resolved schema of its items.
     */
    private static ObjectNode resolveSchema(JsonNode schema, JsonNode openApiDocument) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (schema == null || schema.isMissingNode()) {
            return result;
        }
        JsonNode resolved = schema;
        if (schema.has("$ref")) {
            String ref = schema.path("$ref").asText();
            result.put("refName", ref.substring(ref.lastIndexOf('/') + 1));
            resolved = resolveRef(ref, openApiDocument);
        }
        String type = resolved.path("type").asText(resolved.has("properties") ? "object" : "");
        if (!type.isEmpty()) {
            result.put("type", type);
        }
        if ("array".equals(type)) {
            result.set("items", resolveSchema(resolved.path("items"), openApiDocument));
        } else if (resolved.has("properties")) {
            ArrayNode properties = JsonNodeFactory.instance.arrayNode();
            resolved.path("properties").fieldNames().forEachRemaining(properties::add);
            result.set("properties", sortArrayNode(properties));
        }
        return result;
    }

    private static JsonNode resolveRef(String ref, JsonNode openApiDocument) {
        if (!ref.startsWith("#/")) {
            return JsonNodeFactory.instance.objectNode();
        }
        JsonNode current = openApiDocument;
        for (String segment : ref.substring(2).split("/")) {
            current = current.path(segment);
        }
        return current;
    }

    private static ArrayNode sortedCopy(JsonNode arrayNode) {
        ArrayNode copy = JsonNodeFactory.instance.arrayNode();
        if (arrayNode.isArray()) {
            arrayNode.forEach(copy::add);
        }
        return sortArrayNode(copy);
    }

    private static ArrayNode sortArrayNode(ArrayNode arrayNode) {
        List<JsonNode> elements = new java.util.ArrayList<>();
        arrayNode.forEach(elements::add);
        elements.sort((a, b) -> a.asText().compareTo(b.asText()));
        ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
        elements.forEach(sorted::add);
        return sorted;
    }

    private static ArrayNode sortArrayNodeByTextField(ArrayNode arrayNode, String field) {
        List<JsonNode> elements = new java.util.ArrayList<>();
        arrayNode.forEach(elements::add);
        elements.sort((a, b) -> a.path(field).asText().compareTo(b.path(field).asText()));
        ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
        elements.forEach(sorted::add);
        return sorted;
    }
}
