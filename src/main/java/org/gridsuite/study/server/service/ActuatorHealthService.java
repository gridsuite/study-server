/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;

@Service
public class ActuatorHealthService {

    static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    static final String ACTUATOR_HEALTH_STATUS_JSON_FIELD = "status";
    static final String ACTUATOR_HEALTH_STATUS_UP = "UP";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RestTemplate restTemplate;

    private Map<String, String> optionalServices;

    public ActuatorHealthService(@Value("${gridsuite.services.security-analysis-server.base-uri}") String securityAnalysisServerBaseUri,
                                 @Value("${gridsuite.services.sensitivity-analysis-server.base-uri}") String sensitivityAnalysisServerBaseUri,
                                 @Value("${gridsuite.services.shortcircuit-server.base-uri}") String shortcircuitServerBaseUri,
                                 @Value("${gridsuite.services.dynamic-simulation-server.base-uri}") String dynamicSimulationServerBaseUri) {
        optionalServices = Map.of("security-analysis-server", securityAnalysisServerBaseUri,
            "sensitivity-analysis-server", sensitivityAnalysisServerBaseUri,
            "shortcircuit-server", shortcircuitServerBaseUri,
            "dynamic-simulation-server", dynamicSimulationServerBaseUri);
        restTemplate = new RestTemplate(getClientHttpRequestFactory());
    }

    private SimpleClientHttpRequestFactory getClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(2000); // TODO which timeout values ?
        clientHttpRequestFactory.setReadTimeout(2000);
        return clientHttpRequestFactory;
    }

    private boolean isServerUp(String serverName) {
        String result;
        try {
            result = restTemplate.getForObject(optionalServices.get(serverName) + DELIMITER + ACTUATOR_HEALTH_PATH, String.class);
        } catch (RestClientException e) {
            return false;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(result).path(ACTUATOR_HEALTH_STATUS_JSON_FIELD);
            if (!node.isMissingNode() && node.asText().equalsIgnoreCase(ACTUATOR_HEALTH_STATUS_UP)) {
                return true;
            }
        } catch (JsonProcessingException e) {
            return false;
        }
        return false;
    }

    public List<String> getOptionalUpServices() {
        return optionalServices.keySet().stream().filter(this::isServerUp).collect(Collectors.toList());
    }
}
