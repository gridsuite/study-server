/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NetworkModificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkModificationService.class);

    private static final String DELIMITER = "/";
    private static final String GROUP_PATH = "groups" + DELIMITER + "{groupUuid}";
    private static final String MODIFICATIONS_PATH = "modifications";
    private static final String NETWORK_MODIFICATIONS_PATH = "network-modifications";
    private static final String QUERY_PARAM_RECEIVER = "receiver";

    private String networkModificationServerBaseUri;

    private final NetworkService networkStoreService;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper;

    @Autowired
    NetworkModificationService(@Value("${backing-services.network-modification.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri,
                               NetworkService networkStoreService,
                               ObjectMapper objectMapper) {
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
        this.networkStoreService = networkStoreService;
        this.objectMapper = objectMapper;
    }

    public void setNetworkModificationServerBaseUri(String networkModificationServerBaseUri) {
        this.networkModificationServerBaseUri = networkModificationServerBaseUri + DELIMITER;
    }

    private String getNetworkModificationServerURI(boolean addNetworksPart) {
        return this.networkModificationServerBaseUri + DELIMITER + NETWORK_MODIFICATION_API_VERSION + DELIMITER + (addNetworksPart ? "networks" + DELIMITER : "");
    }

    private String buildPathFrom(UUID networkUuid) {
        return UriComponentsBuilder.fromPath("{networkUuid}" + DELIMITER)
                .buildAndExpand(networkUuid)
                .toUriString();
    }

    // Return json string because modification dtos are not available here
    public String getModifications(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + MODIFICATIONS_PATH)
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .buildAndExpand(groupUUid)
            .toUriString();

        try {
            return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, String.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, GET_MODIFICATIONS_FAILED);
        }
    }

    public void deleteModifications(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .buildAndExpand(groupUUid)
            .toUriString();

        try {
            restTemplate.delete(getNetworkModificationServerURI(false) + path);
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, DELETE_MODIFICATIONS_FAILED);
        }
    }

    public void deleteModifications(List<UUID> modificationsUuids) {
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(true) + NETWORK_MODIFICATIONS_PATH + DELIMITER + modificationsUuids)
                .buildAndExpand()
                .toUriString();
        try {
            restTemplate.delete(path);
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, DELETE_MODIFICATIONS_FAILED);
        }
    }

    private StudyException handleChangeError(HttpStatusCodeException httpException, StudyException.Type type) {
        String responseBody = httpException.getResponseBodyAsString();
        if (responseBody.isEmpty()) {
            return new StudyException(type, httpException.getStatusCode().toString());
        }

        String message = responseBody;
        try {
            JsonNode node = new ObjectMapper().readTree(responseBody).path("message");
            if (!node.isMissingNode()) {
                message = node.asText();
            }
        } catch (JsonProcessingException e) {
            // responseBody by default
        }

        LOGGER.error(message, httpException);

        return new StudyException(type, message);
    }

    public List<EquipmentModificationInfos> createModification(UUID studyUuid,
                                                               String createEquipmentAttributes,
                                                               UUID groupUuid,
                                                               ModificationType modificationType,
                                                               String variantId, UUID reportUuid,
                                                               String reporterId) {
        List<EquipmentModificationInfos> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(createEquipmentAttributes);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(true) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(NETWORK_UUID, networkUuid)
                .queryParam(GROUP_UUID, groupUuid)
                .queryParam(REPORT_UUID, reportUuid)
                .queryParam(REPORTER_ID, reporterId);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(createEquipmentAttributes, headers);

        try {
            result = restTemplate.exchange(path, HttpMethod.POST, httpEntity,
                    new ParameterizedTypeReference<List<EquipmentModificationInfos>>() {
                    }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
        }

        return result;
    }

    public void updateModification(String createEquipmentAttributes,
                                   ModificationType modificationType,
                                   UUID modificationUuid) {
        Objects.requireNonNull(createEquipmentAttributes);

        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(true) + NETWORK_MODIFICATIONS_PATH + DELIMITER + modificationUuid)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(createEquipmentAttributes, headers);

        try {
            restTemplate.exchange(path, HttpMethod.PUT, httpEntity,
                    Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
        }
    }

    void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull BuildInfos buildInfos) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "build");
        var path = uriComponentsBuilder
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .build()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<BuildInfos> httpEntity = new HttpEntity<>(buildInfos, headers);

        restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.POST, httpEntity, Void.class);
    }

    public void stopBuild(@NonNull UUID nodeUuid) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var path = UriComponentsBuilder.fromPath("build/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .build()
            .toUriString();

        restTemplate.put(getNetworkModificationServerURI(false) + path, null);
    }

    private HttpEntity<String> getModificationsUuidBody(List<UUID> modificationUuidList) {
        HttpEntity<String> httpEntity;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            httpEntity = new HttpEntity<>(objectMapper.writeValueAsString(modificationUuidList), headers);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return httpEntity;
    }

    public String moveModifications(UUID groupUuid, UUID originGroupUuid, List<UUID> modificationUuidList, UUID beforeUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam("action", "MOVE")
            .queryParam("originGroupUuid", originGroupUuid);
        if (beforeUuid != null) {
            path.queryParam("before", beforeUuid);
        }

        HttpEntity<String> httpEntity = getModificationsUuidBody(modificationUuidList);
        return restTemplate.exchange(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString(), HttpMethod.PUT, httpEntity, String.class).getBody();
    }

    public String duplicateModification(UUID groupUuid, List<UUID> modificationUuidList) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam("action", "COPY");

        HttpEntity<String> httpEntity = getModificationsUuidBody(modificationUuidList);
        return restTemplate.exchange(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString(), HttpMethod.PUT, httpEntity, String.class).getBody();
    }

    public void createModifications(UUID sourceGroupUuid, UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(sourceGroupUuid);
        var path = UriComponentsBuilder.fromPath("groups")
                .queryParam("duplicateFrom", sourceGroupUuid)
                .queryParam("groupUuid", groupUuid)
                .buildAndExpand(groupUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, STUDY_CREATION_FAILED);
        }
    }
}
