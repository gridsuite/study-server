/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.AboutInfo;
import org.gridsuite.study.server.dto.AboutInfo.ModuleType;
import org.gridsuite.study.server.dto.ServiceStatusInfos;
import org.gridsuite.study.server.dto.ServiceStatusInfos.ServiceStatus;
import org.gridsuite.study.server.exception.PartialResultException;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.gridsuite.study.server.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class RemoteServicesInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteServicesInspector.class);

    private static final String ACTUATOR_HEALTH_STATUS_JSON_FIELD = "status";
    static final long REQUEST_TIMEOUT_IN_MS = 2000L;

    private static final JsonPointer ACTUATOR_INFO_BUILD_NAME = JsonPointer.compile("/build/name");
    private static final JsonPointer ACTUATOR_INFO_BUILD_ARTIFACT = JsonPointer.compile("/build/artifact");
    private static final JsonPointer ACTUATOR_INFO_BUILD_VERSION = JsonPointer.compile("/build/version");
    private static final JsonPointer ACTUATOR_INFO_GIT_TAGS = JsonPointer.compile("/git/tags");
    private static final JsonPointer ACTUATOR_INFO_GIT_COMMIT_DESCRIBE = JsonPointer.compile("/git/commit/id/describe-short");

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final RemoteServicesProperties remoteServicesProperties;
    private final InfoEndpoint infoEndpoint;
    private final RemoteServicesInspector asyncSelf; //we need to use spring proxy-based bean

    @Autowired
    public RemoteServicesInspector(ObjectMapper objectMapper,
                                   RemoteServicesProperties remoteServicesProperties,
                                   @Lazy RemoteServicesInspector asyncRemoteServicesInspector,
                                   RestTemplateBuilder restTemplateBuilder,
                                   InfoEndpoint infoEndpoint) {
        this.objectMapper = objectMapper;
        this.remoteServicesProperties = remoteServicesProperties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_IN_MS))
                .readTimeout(Duration.ofMillis(REQUEST_TIMEOUT_IN_MS))
                .build();
        this.infoEndpoint = infoEndpoint;
        this.asyncSelf = asyncRemoteServicesInspector;
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
     * @param view the view to use to filter the services returned
     * @return a map for all services contacted
     *
     * @apiNote contact {@code /actuator/info} endpoint of the services
     * @implNote retrieve data in parallel to optimize requests time
     */
    @SuppressWarnings("unchecked") //.toArray(...) generics cause "Generic array creation) problem
    public Map<String, JsonNode> getServicesInfo(@Nullable FrontService view) throws PartialResultException {
        final CompletableFuture<Entry<String, JsonNode>>[] resultsAsync = Optional.ofNullable(view)
                .map(viewFilter -> remoteServicesProperties.getRemoteServiceViewFilter().get(viewFilter))
                .orElse(remoteServicesProperties.getRemoteServiceViewDefault())
                .parallelStream()
                .map(srv -> asyncSelf.getServiceInfo(srv).thenApply(json -> Map.entry(srv.serviceName(), json)))
                .toArray(size -> (CompletableFuture<Entry<String, JsonNode>>[]) new CompletableFuture<?>[size]);
        CompletableFuture.allOf(resultsAsync).join();
        final AtomicBoolean isPartial = new AtomicBoolean(false); //need effectively final for lambda
        final Map<String, JsonNode> result = Map.ofEntries(Arrays.stream(resultsAsync)
                .map(CompletableFuture::join)
                .map(e -> {
                    if (NullNode.instance.equals(e.getValue())) {
                        isPartial.lazySet(true);
                    }
                    return e;
                })
                .toArray(size -> (Entry<String, JsonNode>[]) new Entry[size]));
        if (isPartial.get()) {
            throw new PartialResultException(new HashMap<>(result), "Didn't get response from some servers");
        } else {
            return result;
        }
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

    /**
     * Aggregate result of {@link #getServicesInfo(FrontService)} into map of {@link AboutInfo}s for fronts
     * @return a map for all services contacted
     */
    public AboutInfo[] convertServicesInfoToAboutInfo(@NonNull final Map<String, JsonNode> infos) {
        return infos.entrySet().stream()
            .map(e -> {
                final JsonNode root = Objects.requireNonNullElse(e.getValue(), MissingNode.getInstance());
                final String tags = JsonUtils.nodeAt(root, jn -> StringUtils.isNotBlank(jn.asText(null)),
                        ACTUATOR_INFO_GIT_TAGS, ACTUATOR_INFO_GIT_COMMIT_DESCRIBE).asText(null);
                final String name = JsonUtils.nodeAt(root,
                        jn -> StringUtils.isNotBlank(jn.asText(null)),
                        ACTUATOR_INFO_BUILD_NAME,
                        ACTUATOR_INFO_BUILD_ARTIFACT
                    ).asText(e.getKey());
                final String version = root.at(ACTUATOR_INFO_BUILD_VERSION).asText(null);
                return new AboutInfo(ModuleType.SERVER, name, version,
                        tags != null && tags.indexOf(',') >= 0 ? StringUtils.substringAfterLast(tags, ",") : tags);
            })
            .toArray(AboutInfo[]::new);
    }
}
