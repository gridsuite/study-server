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
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;

@Service
public class ActuatorHealthService {

    @Builder
    @Getter
    public static class ServiceStatusInfos {
        private String name;
        private String status;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ActuatorHealthService.class);

    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    private static final String ACTUATOR_HEALTH_STATUS_JSON_FIELD = "status";
    private static final String ACTUATOR_HEALTH_STATUS_UP = "UP";
    private static final String ACTUATOR_HEALTH_STATUS_DOWN = "DOWN";
    private static final int ACTUATOR_HEALTH_TIMEOUT_IN_MS = 2000;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    private final StudyServerExecutionService executionService;

    private final RemoteServicesProperties remoteServicesProperties;

    public ActuatorHealthService(ObjectMapper objectMapper, StudyServerExecutionService executionService, RemoteServicesProperties remoteServicesProperties) {
        this.objectMapper = objectMapper;
        this.executionService = executionService;
        this.remoteServicesProperties = remoteServicesProperties;
        this.restTemplate = new RestTemplate(getClientHttpRequestFactory());
    }

    private SimpleClientHttpRequestFactory getClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(ACTUATOR_HEALTH_TIMEOUT_IN_MS);
        clientHttpRequestFactory.setReadTimeout(ACTUATOR_HEALTH_TIMEOUT_IN_MS);
        return clientHttpRequestFactory;
    }

    public List<ServiceStatusInfos> getOptionalServices() {
        // parallel health status check for all services marked as "optional: true" in application.yaml
        return remoteServicesProperties.getServices().stream()
            .filter(RemoteServicesProperties.Service::getOptional)
            .map(this::checkServiceFuture)
            .map(CompletableFuture::join)
            .toList();
    }

    private ServiceStatusInfos toServiceStatusInfos(String serviceName, boolean isUp) {
        return ServiceStatusInfos.builder()
            .name(serviceName)
            .status(isUp ? ACTUATOR_HEALTH_STATUS_UP : ACTUATOR_HEALTH_STATUS_DOWN)
            .build();
    }

    private CompletableFuture<ServiceStatusInfos> checkServiceFuture(RemoteServicesProperties.Service service) {
        return executionService.supplyAsync(() ->
            toServiceStatusInfos(service.getName(), isServerUp(service))
        );
    }

    private boolean isServerUp(RemoteServicesProperties.Service service) {
        String result;
        try {
            result = restTemplate.getForObject(service.getBaseUri() + DELIMITER + ACTUATOR_HEALTH_PATH, String.class);
        } catch (RestClientException e) {
            LOGGER.error(String.format("Network error while testing '%s': %s", service.getName(), e.getMessage()), e);
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(result).path(ACTUATOR_HEALTH_STATUS_JSON_FIELD);
            if (node.isMissingNode()) {
                LOGGER.error("Cannot find {} json node while testing '{}'", ACTUATOR_HEALTH_STATUS_JSON_FIELD, service.getName());
            } else {
                return node.asText().equalsIgnoreCase(ACTUATOR_HEALTH_STATUS_UP);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error(String.format("Json parsing error while testing '%s': %s", service.getName(), e.getMessage()), e);
            return false;
        }
        return false;
    }
}
