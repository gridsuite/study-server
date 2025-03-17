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

import org.gridsuite.study.server.StudyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.DELETE_COMPUTATION_RESULTS_FAILED;

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

    public static void deleteCalculationResults(List<UUID> resultsUuids,
                                                String path,
                                                RestTemplate restTemplate,
                                                String serverBaseUri) {
        if (resultsUuids != null && resultsUuids.isEmpty()) {
            return;
        }
        try {
            UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(path);
            if (!CollectionUtils.isEmpty(resultsUuids)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_RESULTS_UUIDS, resultsUuids);
            }
            restTemplate.delete(serverBaseUri + uriComponentsBuilder.build().toUriString());
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }
}
