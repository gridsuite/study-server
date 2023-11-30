/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.powsybl.contingency.Contingency;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.service.NetworkMapService.QUERY_PARAM_LINE_ID;

/**
 * @author Ghazwa REHILI <ghazwa.rehili at rte-france.com>
 */
@Service
public class FilterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterService.class);
    private static final String FILTER_SERVER_API_VERSION = "v1";

    private static final String DELIMITER = "/";

    private static String filterServerBaseUri;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public FilterService(RemoteServicesProperties remoteServicesProperties) {
        filterServerBaseUri = remoteServicesProperties.getServiceUri("filter-server");
    }

    public Integer fetchfiltersComplexity(List<UUID> filtersUuids, UUID networkUuid) {

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/complexity")
                .queryParam(QUERY_PARAM_FILTERS_IDS, filtersUuids)
                .queryParam(NETWORK_UUID, networkUuid);

        var path = uriComponentsBuilder
                .build()
                .toUriString();

        return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<Integer>() {
                }).getBody();
    }


}

