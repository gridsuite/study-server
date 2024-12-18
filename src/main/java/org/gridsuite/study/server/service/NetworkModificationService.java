/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NetworkModificationService {

    private static final String DELIMITER = "/";
    private static final String GROUP_PATH = "groups" + DELIMITER + "{groupUuid}";
    private static final String NETWORK_MODIFICATIONS_PATH = "network-modifications";
    private static final String NETWORK_MODIFICATIONS_COUNT_PATH = "network-modifications-count";
    private static final String NETWORK_UUID = "networkUuid";
    private static final String REPORT_UUID = "reportUuid";
    private static final String REPORTER_ID = "reporterId";
    private static final String VARIANT_ID = "variantId";
    private static final String QUERY_PARAM_ACTION = "action";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RootNetworkService rootNetworkService;
    private String networkModificationServerBaseUri;

    @Autowired
    NetworkModificationService(RemoteServicesProperties remoteServicesProperties,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper, RootNetworkService rootNetworkService) {
        this.networkModificationServerBaseUri = remoteServicesProperties.getServiceUri("network-modification-server");
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.rootNetworkService = rootNetworkService;
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
    public String getModifications(UUID groupUUid, boolean stashedModifications, boolean onlyMetadata) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH)
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .queryParam(QUERY_PARAM_ONLY_STASHED, stashedModifications)
            .queryParam("onlyMetadata", onlyMetadata)
            .buildAndExpand(groupUUid)
            .toUriString();

        try {
            return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, String.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, GET_MODIFICATIONS_FAILED);
        }
    }

    public Integer getModificationsCount(UUID groupUUid, boolean stashedModifications) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + NETWORK_MODIFICATIONS_COUNT_PATH)
            .queryParam(QUERY_PARAM_STASHED, stashedModifications)
            .buildAndExpand(groupUUid)
            .toUriString();

        try {
            return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, Integer.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, GET_MODIFICATIONS_COUNT_FAILED);
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
            throw handleHttpError(e, DELETE_NETWORK_MODIFICATION_FAILED);
        }
    }

    public void deleteModifications(UUID groupUuid, List<UUID> modificationsUuids) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(GROUP_UUID, groupUuid)
                .buildAndExpand()
                .toUriString();
        try {
            restTemplate.delete(path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_NETWORK_MODIFICATION_FAILED);
        }
    }

    public Optional<NetworkModificationResult> createModification(UUID studyUuid,
                                                                  String createModificationAttributes,
                                                                  UUID groupUuid,
                                                                  String variantId, UUID reportUuid,
                                                                  UUID nodeUuid,
                                                                  UUID rootNetworkUuid) {
        Optional<NetworkModificationResult> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(createModificationAttributes);

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(NETWORK_UUID, networkUuid)
                .queryParam(GROUP_UUID, groupUuid)
                .queryParam(REPORT_UUID, reportUuid)
                .queryParam(REPORTER_ID, nodeUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(createModificationAttributes, headers);

        try {
            result = restTemplate.exchange(path, HttpMethod.POST, httpEntity,
                    new ParameterizedTypeReference<Optional<NetworkModificationResult> >() {
                    }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_NETWORK_MODIFICATION_FAILED);
        }

        return result;
    }

    public void updateModification(String createEquipmentAttributes, UUID modificationUuid) {
        Objects.requireNonNull(createEquipmentAttributes);

        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + modificationUuid)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(createEquipmentAttributes, headers);

        try {
            restTemplate.exchange(path, HttpMethod.PUT, httpEntity,
                    Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_NETWORK_MODIFICATION_FAILED);
        }
    }

    public void stashModifications(UUID groupUUid, List<UUID> modificationsUuids) {
        Objects.requireNonNull(groupUUid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(GROUP_UUID, groupUUid)
                .queryParam(QUERY_PARAM_STASHED, true)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<BuildInfos> httpEntity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(path, HttpMethod.PUT, httpEntity, Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_NETWORK_MODIFICATION_FAILED);
        }
    }

    public void updateModificationsActivation(UUID groupUUid, List<UUID> modificationsUuids, boolean activated) {
        Objects.requireNonNull(groupUUid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
            .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
            .queryParam(UUIDS, modificationsUuids)
            .queryParam(GROUP_UUID, groupUUid)
            .queryParam(QUERY_PARAM_ACTIVATED, activated)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<BuildInfos> httpEntity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(path, HttpMethod.PUT, httpEntity, Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_NETWORK_MODIFICATION_FAILED);
        }
    }

    public void restoreModifications(UUID groupUUid, List<UUID> modificationsUuids) {
        Objects.requireNonNull(groupUUid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(GROUP_UUID, groupUUid)
                .queryParam(QUERY_PARAM_STASHED, false)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<BuildInfos> httpEntity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(path, HttpMethod.PUT, httpEntity, Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_NETWORK_MODIFICATION_FAILED);
        }
    }

    void buildNode(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull BuildInfos buildInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String receiver = buildReceiver(nodeUuid, rootNetworkUuid);

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

    public void stopBuild(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        String receiver = buildReceiver(nodeUuid, rootNetworkUuid);
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

    public Optional<NetworkModificationResult> moveModifications(UUID originGroupUuid, List<UUID> modificationUuidList, UUID beforeUuid, UUID networkUuid, NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity, boolean buildTargetNode) {
        Objects.requireNonNull(networkUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam(QUERY_PARAM_ACTION, ModificationsActionType.MOVE.name())
            .queryParam(NETWORK_UUID, networkUuid)
            .queryParam(REPORTER_ID, networkModificationNodeInfoEntity.getId())
            .queryParam("originGroupUuid", originGroupUuid)
            .queryParam("build", buildTargetNode);

        if (rootNetworkNodeInfoEntity != null) {
            path.queryParam(VARIANT_ID, rootNetworkNodeInfoEntity.getVariantId())
                .queryParam(REPORT_UUID, rootNetworkNodeInfoEntity.getModificationReports().get(networkModificationNodeInfoEntity.getId()));
        }
        if (beforeUuid != null) {
            path.queryParam("before", beforeUuid);
        }

        HttpEntity<String> httpEntity = getModificationsUuidBody(modificationUuidList);

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path.buildAndExpand(networkModificationNodeInfoEntity.getModificationGroupUuid()).toUriString(),
                HttpMethod.PUT,
                httpEntity,
                new ParameterizedTypeReference<Optional<NetworkModificationResult>>() {
                }).getBody();
    }

    public Optional<NetworkModificationResult> createModifications(List<UUID> modificationUuidList, UUID networkUuid, NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity, ModificationsActionType action) {
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam(QUERY_PARAM_ACTION, action.name())
            .queryParam(NETWORK_UUID, networkUuid)
            .queryParam(REPORT_UUID, rootNetworkNodeInfoEntity.getModificationReports().get(networkModificationNodeInfoEntity.getId()))
            .queryParam(REPORTER_ID, networkModificationNodeInfoEntity.getId())
            .queryParam(VARIANT_ID, rootNetworkNodeInfoEntity.getVariantId());

        HttpEntity<String> httpEntity = getModificationsUuidBody(modificationUuidList);
        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path.buildAndExpand(networkModificationNodeInfoEntity.getModificationGroupUuid()).toUriString(),
                HttpMethod.PUT,
                httpEntity,
                new ParameterizedTypeReference<Optional<NetworkModificationResult>>() {
                }).getBody();
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
            throw handleHttpError(e, STUDY_CREATION_FAILED);
        }
    }

    public Optional<NetworkModificationResult> duplicateModificationsInGroup(UUID originGroupUuid, UUID networkUuid, NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + "duplications")
            .queryParam(NETWORK_UUID, networkUuid)
            .queryParam(REPORT_UUID, rootNetworkNodeInfoEntity.getModificationReports().get(networkModificationNodeInfoEntity.getId()))
            .queryParam(REPORTER_ID, networkModificationNodeInfoEntity.getId())
            .queryParam(VARIANT_ID, rootNetworkNodeInfoEntity.getVariantId())
            .queryParam("duplicateFrom", originGroupUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.exchange(
            getNetworkModificationServerURI(false) + path.buildAndExpand(networkModificationNodeInfoEntity.getModificationGroupUuid()).toUriString(),
            HttpMethod.PUT,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<Optional<NetworkModificationResult>>() {
            }).getBody();
    }

    private String buildReceiver(UUID nodeUuid, UUID rootNetworkUuid) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return receiver;
    }

    public void deleteStashedModifications(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + "/stashed-modifications")
                .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
                .buildAndExpand(groupUUid)
                .toUriString();

        try {
            restTemplate.delete(getNetworkModificationServerURI(false) + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_NETWORK_MODIFICATION_FAILED);
        }
    }
}
