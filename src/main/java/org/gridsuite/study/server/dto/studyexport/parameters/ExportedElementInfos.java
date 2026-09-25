/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.parameters;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
public record ExportedElementInfos(UUID uuid, String name, JsonNode content) {

    public static ExportedElementInfos of(JsonNode content, Map<UUID, String> names) {
        UUID uuid = UUID.fromString(content.get("id").asText());
        return new ExportedElementInfos(uuid, names.get(uuid), content);
    }
}
