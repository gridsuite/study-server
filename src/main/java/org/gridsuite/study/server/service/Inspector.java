/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.NonNull;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ServiceStatusInfos;
import org.gridsuite.study.server.dto.ServiceStatusInfos.ServiceStatus;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class Inspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Inspector.class);

    private static final String ACTUATOR_HEALTH_STATUS_JSON_FIELD = "status";
    static final long REQUEST_TIMEOUT_IN_MS = 2000L;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final RemoteServicesProperties remoteServicesProperties;
    private final InfoEndpoint infoEndpoint;
    private final Inspector asyncSelf; //we need to use spring proxy-based bean

    @Autowired
    public Inspector(ObjectMapper objectMapper,
                     RemoteServicesProperties remoteServicesProperties,
                     @Lazy Inspector asyncInspector,
                     RestTemplateBuilder restTemplateBuilder,
                     InfoEndpoint infoEndpoint) {
        this.objectMapper = objectMapper;
        this.remoteServicesProperties = remoteServicesProperties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_IN_MS))
                .setReadTimeout(Duration.ofMillis(REQUEST_TIMEOUT_IN_MS))
                .build();
        this.infoEndpoint = infoEndpoint;
        this.asyncSelf = asyncInspector;
    }

    public List<ServiceStatusInfos> getOptionalServices() {
        // parallel health status check for all services marked as "optional: true" in application.yaml
        List<CompletableFuture<ServiceStatusInfos>> results = remoteServicesProperties.getServices().stream()
            .filter(RemoteServicesProperties.Service::isOptional)
            .map(service -> asyncSelf.isServerUp(service).thenApply(isUp -> ServiceStatusInfos.builder()
                    .name(service.getName())
                    .status(Boolean.TRUE.equals(isUp) ? ServiceStatus.UP : ServiceStatus.DOWN)
                    .build()))
            .toList();
        CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();
        return results.stream().map(CompletableFuture::join).toList();
    }

    @Async
    public CompletableFuture<Boolean> isServerUp(RemoteServicesProperties.Service service) {
        try {
            final String result = restTemplate.getForObject(service.getBaseUri() + "/actuator/health", String.class);
            final JsonNode node = objectMapper.readTree(result).path(ACTUATOR_HEALTH_STATUS_JSON_FIELD);
            if (node.isMissingNode()) {
                LOGGER.error("Cannot find {} json node while testing '{}'", ACTUATOR_HEALTH_STATUS_JSON_FIELD, service.getName());
            } else {
                return CompletableFuture.completedFuture("UP".equalsIgnoreCase(node.asText()));
            }
        } catch (RestClientException e) {
            LOGGER.error("Network error while testing " + service.getName(), e);
        } catch (JsonProcessingException e) {
            LOGGER.error("Json parsing error while testing " + service.getName(), e);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Get all {@link RemoteServiceName services} actuator information and aggregate them
     * @return a map for all services contacted
     *
     * @apiNote contact {@code /actuator/info} endpoint of the services
     * @implNote retrieve data in parallel to optimize requests time
     */
    @SuppressWarnings("unchecked") //.toArray(...) generics cause "Generic array creation) problem
    public Map<String, JsonNode> getServicesInfo() {
        final CompletableFuture<Entry<String, JsonNode>>[] resultsAsync = Arrays.stream(RemoteServiceName.values())
                .parallel()
                .map(srv -> asyncSelf.getServiceInfo(srv).thenApply(json -> Map.entry(srv.serviceName(), json)))
                .toArray(size -> (CompletableFuture<Entry<String, JsonNode>>[]) new CompletableFuture<?>[size]);
        CompletableFuture.allOf(resultsAsync).join();
        return Map.ofEntries(Arrays.stream(resultsAsync)
                .map(CompletableFuture::join)
                .toArray(size -> (Entry<String, JsonNode>[]) new Entry[size]));
    }

    /**
     * Retrieve {@code <service>/actuator/info} data
     * @param service the service name to request to
     * @return {@code Map<String, Object>} in JSON format
     */
    @Async
    public CompletableFuture<JsonNode> getServiceInfo(@NonNull final RemoteServiceName service) {
        try {
            if (service == RemoteServiceName.STUDY_SERVER) {
                return CompletableFuture.completedFuture(objectMapper.valueToTree(infoEndpoint.info()));
            } else {
                final String rawJson = this.restTemplate.getForObject(
                    URI.create(this.remoteServicesProperties.getServiceUri(service.serviceName()) + "/actuator/info")
                        .normalize(), String.class);
                return CompletableFuture.completedFuture(this.objectMapper.readTree(rawJson));
            }
        } catch (final RuntimeException | JacksonException ex) {
            LOGGER.debug("Error while retrieving informations for " + service, ex);
            return CompletableFuture.completedFuture(NullNode.instance);
        }
    }
}
