/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.error.StudyBusinessErrorCode;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Etienne HOMER <etiennehomer@gmail.com>
 */
class JsonUtilsTest {

    @Test
    void testSerializeImportParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("string", "value");
        params.put("int", 123);
        params.put("bool", true);
        params.put("list", List.of("a", "b"));
        params.put("map", Map.of("k", "v"));
        params.put("null", null);

        Map<String, String> result = JsonUtils.serializeImportParameters(params);

        assertEquals("\"value\"", result.get("string"));
        assertEquals("123", result.get("int"));
        assertEquals("true", result.get("bool"));
        assertEquals("[\"a\",\"b\"]", result.get("list"));
        assertEquals("{\"k\":\"v\"}", result.get("map"));
        assertEquals("null", result.get("null"));
    }

    @Test
    void testSerializeNull() {
        Map<String, String> result = JsonUtils.serializeImportParameters(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testNonSerializableParameter() {
        class NonSerializable {
            private final String hidden = "hidden";
        }

        StudyException exception = assertThrows(StudyException.class, () -> JsonUtils.serializeImportParameters(Map.of("nonSerializable", new NonSerializable())));
        assertEquals(StudyBusinessErrorCode.UNPROCESSABLE_IMPORT_PARAMETER, exception.getBusinessErrorCode());
        assertTrue(exception.getMessage().contains("Import parameter 'nonSerializable =>"));
    }

    @Test
    void testDeserializeImportParameters() {
        Map<String, String> rawParams = new HashMap<>();
        rawParams.put("string", "\"value\"");
        rawParams.put("int", "123");
        rawParams.put("bool", "true");
        rawParams.put("list", "[\"a\",\"b\"]");
        rawParams.put("map", "{\"k\":\"v\"}");
        rawParams.put("null", "null");
        rawParams.put("realNull", null);

        Map<String, Object> result = JsonUtils.deserializeImportParameters(rawParams);

        assertEquals("value", result.get("string"));
        assertEquals(123, result.get("int"));
        assertEquals(true, result.get("bool"));
        assertEquals(List.of("a", "b"), result.get("list"));
        assertEquals(Map.of("k", "v"), result.get("map"));
        assertNull(result.get("null"));
        assertNull(result.get("realNull"));
    }

    @Test
    void testDeserializeNull() {
        Map<String, Object> result = JsonUtils.deserializeImportParameters(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testDeserializeInvalidJson() {
        Map<String, String> rawParams = Map.of("invalid", "{notJson}");
        StudyException exception = assertThrows(StudyException.class, () -> JsonUtils.deserializeImportParameters(rawParams));
        assertEquals(StudyBusinessErrorCode.UNPROCESSABLE_IMPORT_PARAMETER, exception.getBusinessErrorCode());
        assertTrue(exception.getMessage().contains("Import parameter 'invalid => {notJson}' is not valid JSON"));
    }
}
