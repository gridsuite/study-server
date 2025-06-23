/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
public abstract class AbstractWorkflowInfos {
    public String serialize(ObjectMapper objectMapper) {
        String result;
        try {
            result = URLEncoder.encode(objectMapper.writeValueAsString(this), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    public abstract WorkflowType getType();
}
