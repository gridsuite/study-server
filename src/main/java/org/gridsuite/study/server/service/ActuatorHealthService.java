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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;

@Service
public class ActuatorHealthService {

    static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    static final String ACTUATOR_HEALTH_STATUS_JSON_FIELD = "status";
    static final String ACTUATOR_HEALTH_STATUS_UP = "UP";
    static final int ACTUATOR_HEALTH_TIMEOUT_IN_MS = 2000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    private final Map<String, String> optionalServices;

    StudyServerExecutionService studyServerExecutionService;

    private String targetServerUri = null;

    public ActuatorHealthService(@Value("${gridsuite.services.security-analysis-server.base-uri}") String securityAnalysisServerBaseUri,
                                 @Value("${gridsuite.services.sensitivity-analysis-server.base-uri}") String sensitivityAnalysisServerBaseUri,
                                 @Value("${gridsuite.services.shortcircuit-server.base-uri}") String shortcircuitServerBaseUri,
                                 @Value("${gridsuite.services.dynamic-simulation-server.base-uri}") String dynamicSimulationServerBaseUri,
                                 @Value("${gridsuite.services.voltage-init-server.base-uri}") String voltageInitServerBaseUri,
                                 StudyServerExecutionService executionService) {
        studyServerExecutionService = executionService;
        optionalServices = Map.of("security-analysis-server", securityAnalysisServerBaseUri,
                "sensitivity-analysis-server", sensitivityAnalysisServerBaseUri,
                "shortcircuit-server", shortcircuitServerBaseUri,
                "voltage-init-server", voltageInitServerBaseUri,
                "dynamic-simulation-server", dynamicSimulationServerBaseUri);
        restTemplate = new RestTemplate(getClientHttpRequestFactory());
    }

    public void setTargetServerUri(String serverUri) {
        this.targetServerUri = serverUri;
    }

    private SimpleClientHttpRequestFactory getClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(ACTUATOR_HEALTH_TIMEOUT_IN_MS);
        clientHttpRequestFactory.setReadTimeout(ACTUATOR_HEALTH_TIMEOUT_IN_MS);
        return clientHttpRequestFactory;
    }

    public List<String> getOptionalUpServices() {
        try {
            List<CompletableFuture<String>> listOfFutures = optionalServices.keySet().stream().map(this::isUpFuture).toList();
            CompletableFuture<List<String>> futureOfList = CompletableFuture
                    .allOf(listOfFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> listOfFutures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList());
            return futureOfList.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private CompletableFuture<String> isUpFuture(String serverName) {
        return studyServerExecutionService.supplyAsync(() ->
            isServerUp(serverName) ? serverName : null
        );
    }

    private boolean isServerUp(String serverName) {
        String result;
        try {
            result = restTemplate.getForObject((targetServerUri != null ? targetServerUri : optionalServices.get(serverName)) + DELIMITER + ACTUATOR_HEALTH_PATH, String.class);
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
}
