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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

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
    private final RestClient restClient;
    private final RemoteServicesProperties remoteServicesProperties;
    private final InfoEndpoint infoEndpoint;
    private final StudyServerExecutionService studyServerExecutionService;

    @Autowired
    public RemoteServicesInspector(ObjectMapper objectMapper,
                                   RemoteServicesProperties remoteServicesProperties,
                                   StudyServerExecutionService studyServerExecutionService,
                                   RestClient.Builder restClientBuilder,
                                   InfoEndpoint infoEndpoint) {
        this.objectMapper = objectMapper;
        this.remoteServicesProperties = remoteServicesProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_IN_MS));
        requestFactory.setReadTimeout(Duration.ofMillis(REQUEST_TIMEOUT_IN_MS));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.infoEndpoint = infoEndpoint;
        this.studyServerExecutionService = studyServerExecutionService;
    }

    public List<ServiceStatusInfos> getOptionalServices() {
        // parallel health status check for all services marked as "optional: true" in application.yaml
        List<CompletableFuture<ServiceStatusInfos>> futures = remoteServicesProperties.getServices().stream()
            .filter(RemoteServicesProperties.Service::isOptional)
            .map(service -> studyServerExecutionService.supplyAsync(() -> ServiceStatusInfos.builder()
                .name(service.getName())
                .status(isServiceUp(service) ? ServiceStatus.UP : ServiceStatus.DOWN)
                .build()))
            .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    /**
     * Check if an optional service is up and running.
     * This method is specifically designed for optional services and uses debug-level logging
     * to avoid polluting stdout with expected failures.
     *
     * @param service the optional service to check
     */
    public boolean isServiceUp(@NonNull RemoteServicesProperties.Service service) {
        try {
            final String result = restClient
                .get()
                .uri(service.getBaseUri() + "/actuator/health")
                .retrieve()
                .body(String.class);
            final JsonNode node = objectMapper.readTree(result).path(ACTUATOR_HEALTH_STATUS_JSON_FIELD);
            if (node.isMissingNode()) {
                LOGGER.debug("Cannot find {} json node while testing '{}'", ACTUATOR_HEALTH_STATUS_JSON_FIELD, service.getName());
            } else {
                return "UP".equalsIgnoreCase(node.asText());
            }
        } catch (RestClientException e) {
            LOGGER.debug("Network error while testing {}", service.getName(), e);
        } catch (JsonProcessingException e) {
            LOGGER.debug("Json parsing error while testing {}", service.getName(), e);
        }
        return false;
    }

    public RemoteServicesProperties.Service getService(RemoteServiceName name) {
        final List<RemoteServicesProperties.Service> services = remoteServicesProperties.getServices();
        if (services == null) {
            return null;
        }
        return services.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name.serviceName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all {@link RemoteServiceName services} actuator information and aggregate them
     * @param view the view to use to filter the services returned
     * @return a map for all services contacted
     *
     * @apiNote contact {@code /actuator/info} endpoint of the services
     * @implNote retrieve data in parallel to optimize requests time
     */
    public Map<String, JsonNode> getServicesInfo(@Nullable FrontService view) throws PartialResultException {
        final List<CompletableFuture<Entry<String, JsonNode>>> futures = Optional.ofNullable(view)
                .map(viewFilter -> remoteServicesProperties.getRemoteServiceViewFilter().get(viewFilter))
                .orElse(remoteServicesProperties.getRemoteServiceViewDefault())
                .stream()
                .map(serviceName -> studyServerExecutionService.supplyAsync(() -> Map.entry(serviceName.serviceName(), getServiceInfo(serviceName))))
                .toList();
        List<Entry<String, JsonNode>> infos = futures.stream().map(CompletableFuture::join).toList();
        final HashMap<String, JsonNode> result = new HashMap<>();
        boolean isPartial = false;
        for (Entry<String, JsonNode> info : infos) {
            result.put(info.getKey(), info.getValue());
            if (NullNode.instance.equals(info.getValue())) {
                isPartial = true;
            }
        }
        if (isPartial) {
            throw new PartialResultException(result, "Didn't get response from some servers");
        }
        return result;
    }

    /**
     * Retrieve {@code <service>/actuator/info} data
     * @param serviceName the service name to request to
     * @return {@code Map<String, Object>} in JSON format
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private JsonNode getServiceInfo(@NonNull final RemoteServiceName serviceName) {
        try {
            if (serviceName == RemoteServiceName.STUDY_SERVER) {
                return objectMapper.valueToTree(infoEndpoint.info());
            } else {
                final String rawJson = this.restClient
                    .get()
                    .uri(
                        URI.create(this.remoteServicesProperties.getServiceUri(serviceName.serviceName()) + "/actuator/info")
                        .normalize()
                    )
                    .retrieve()
                    .body(String.class);
                return this.objectMapper.readTree(rawJson);
            }
        } catch (final RuntimeException | JacksonException ex) {
            LOGGER.debug("Error while retrieving informations for {}", serviceName, ex);
            return NullNode.instance;
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
