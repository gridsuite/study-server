/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeAdder;
import com.powsybl.commons.report.TypedValue;

import java.util.Map;

import org.gridsuite.study.server.StudyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
public final class StudyUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyUtils.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private StudyUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static StudyException handleHttpError(HttpStatusCodeException httpException, StudyException.Type type) {
        String responseBody = httpException.getResponseBodyAsString();

        String errorMessage = responseBody.isEmpty() ? httpException.getStatusCode().toString() : parseHttpError(responseBody);

        LOGGER.error(errorMessage, httpException);

        return new StudyException(type, errorMessage);
    }

    private static String parseHttpError(String responseBody) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(responseBody).path("message");
            if (!node.isMissingNode()) {
                return node.asText();
            }
        } catch (JsonProcessingException e) {
            // status code or responseBody by default
        }

        return responseBody;
    }

    public static void addPageableToQueryParams(UriComponentsBuilder builder, Pageable pageable) {
        builder.queryParam("page", pageable.getPageNumber()).queryParam("size", pageable.getPageSize());
        for (Sort.Order order : pageable.getSort()) {
            builder.queryParam("sort", order.getProperty() + "," + order.getDirection());
        }
    }

    public static void insertReportNode(ReportNode parent, ReportNode child) {
        ReportNodeAdder adder = parent.newReportNode().withMessageTemplate(child.getMessageKey(), child.getMessageTemplate());
        for (Map.Entry<String, TypedValue> valueEntry : child.getValues().entrySet()) {
            adder.withUntypedValue(valueEntry.getKey(), valueEntry.getValue().toString());
        }
        ReportNode insertedChild = adder.add();
        if (child.getChildren() != null) {
            child.getChildren().forEach(grandChild -> insertReportNode(insertedChild, grandChild));
        }
    }
}
