/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ServiceStatusInfos;
import org.gridsuite.study.server.dto.ServiceStatusInfos.ServiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class RemoteServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteServices.class);

    private static final String ACTUATOR_HEALTH_STATUS_JSON_FIELD = "status";
    private static final long ACTUATOR_HEALTH_TIMEOUT_IN_MS = 2000L;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    private final StudyServerExecutionService executionService;

    private final RemoteServicesProperties remoteServicesProperties;

    @Autowired
    public RemoteServices(ObjectMapper objectMapper,
                          StudyServerExecutionService executionService,
                          RemoteServicesProperties remoteServicesProperties,
                          RestTemplateBuilder restTemplateBuilder) {
        this.objectMapper = objectMapper;
        this.executionService = executionService;
        this.remoteServicesProperties = remoteServicesProperties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(ACTUATOR_HEALTH_TIMEOUT_IN_MS))
                .setReadTimeout(Duration.ofMillis(ACTUATOR_HEALTH_TIMEOUT_IN_MS))
                .build();
    }

    public List<ServiceStatusInfos> getOptionalServices() {
        // parallel health status check for all services marked as "optional: true" in application.yaml
        return remoteServicesProperties.getServices().stream()
            .filter(RemoteServicesProperties.Service::isOptional)
            .map(service -> executionService.supplyAsync(() -> ServiceStatusInfos.builder()
                    .name(service.getName())
                    .status(isServerUp(service) ? ServiceStatus.UP : ServiceStatus.DOWN)
                    .build()))
            .map(CompletableFuture::join)
            .toList();
    }

    private boolean isServerUp(RemoteServicesProperties.Service service) {
        try {
            final String result = restTemplate.getForObject(service.getBaseUri() + "/actuator/health", String.class);
            final JsonNode node = objectMapper.readTree(result).path(ACTUATOR_HEALTH_STATUS_JSON_FIELD);
            if (node.isMissingNode()) {
                LOGGER.error("Cannot find {} json node while testing '{}'", ACTUATOR_HEALTH_STATUS_JSON_FIELD, service.getName());
                return false;
            } else {
                return "UP".equalsIgnoreCase(node.asText());
            }
        } catch (RestClientException e) {
            LOGGER.error(String.format("Network error while testing '%s': %s", service.getName(), e.getMessage()), e);
            return false;
        } catch (JsonProcessingException e) {
            LOGGER.error(String.format("Json parsing error while testing '%s': %s", service.getName(), e.getMessage()), e);
            return false;
        }
    }
}
