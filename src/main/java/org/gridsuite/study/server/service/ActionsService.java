/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ContingencyCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class ActionsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionsService.class);

    private final RestTemplate restTemplate;

    private static final String NETWORK_UUID = "networkUuid";
    private static final String CONTINGENCY_LIST_IDS = "ids";

    public static final ContingencyCount EMPTY_CONTINGENCY_COUNT = new ContingencyCount(Map.of());

    @Setter
    private String actionsServerBaseUri;

    public ActionsService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.actionsServerBaseUri = remoteServicesProperties.getServiceUri("actions-server");
        this.restTemplate = restTemplate;
    }

    public ContingencyCount getContingencyCount(UUID networkUuid, String variantId, List<UUID> contingencyListIds) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/count")
                .queryParam(CONTINGENCY_LIST_IDS, contingencyListIds)
                .queryParam(NETWORK_UUID, networkUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        return restTemplate.exchange(
                actionsServerBaseUri + uriComponentsBuilder.toUriString(),
                HttpMethod.GET,
                null,
                ContingencyCount.class
        ).getBody();
    }

    public String getContingencyList(UUID id) {
        try {
            String metadataPath = DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/metadata?ids={id}";
            Map<String, String>[] metadata = restTemplate.getForObject(actionsServerBaseUri + metadataPath, Map[].class, id);
            if (metadata == null || metadata.length == 0) {
                LOGGER.warn("Contingency list {} is referenced but does not exist anymore: it is not exported", id);
                return null;
            }
            String endpoint = "IDENTIFIERS".equals(metadata[0].get("type")) ? "identifier-contingency-lists" : "filters-contingency-lists";
            return restTemplate.getForObject(actionsServerBaseUri + DELIMITER + ACTIONS_API_VERSION + "/" + endpoint + "/{id}", String.class, id);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
