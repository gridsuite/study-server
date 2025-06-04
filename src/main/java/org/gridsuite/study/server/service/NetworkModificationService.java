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
import org.gridsuite.modification.dto.ModificationInfos;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.dto.workflow.AbstractWorkflowInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.JsonUtils.getModificationContextJsonString;
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
    private static final String QUERY_PARAM_ACTION = "action";
    private static final String PARAM_USER_INPUT = "userInput";

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

    public List<ModificationInfos> getModifications(UUID groupUUid, boolean stashedModifications, boolean onlyMetadata) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH)
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .queryParam(QUERY_PARAM_ONLY_STASHED, stashedModifications)
            .queryParam("onlyMetadata", onlyMetadata)
            .buildAndExpand(groupUUid)
            .toUriString();

        try {
            return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<ModificationInfos>>() {
            }).getBody();
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

    public NetworkModificationsResult createModification(UUID groupUuid,
                                                                        Pair<String, List<ModificationApplicationContext>> modificationContextInfos) {
        NetworkModificationsResult result;
        Objects.requireNonNull(modificationContextInfos);

        var uriComponentsBuilder = UriComponentsBuilder
            .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
            .queryParam(GROUP_UUID, groupUuid);

        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            HttpEntity<String> httpEntity = new HttpEntity<>(getModificationContextJsonString(objectMapper, modificationContextInfos), headers);
            result = restTemplate.exchange(path, HttpMethod.POST, httpEntity,
                NetworkModificationsResult.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_NETWORK_MODIFICATION_FAILED);
        } catch (Exception e) {
            throw new StudyException(CREATE_NETWORK_MODIFICATION_FAILED, e.getMessage());
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

    public void buildNode(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull BuildInfos buildInfos, AbstractWorkflowInfos workflowInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String receiver = buildReceiver(nodeUuid, rootNetworkUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "build");

        if (workflowInfos != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_WORKFLOW_TYPE, workflowInfos.getWorkflowType());
            uriComponentsBuilder.queryParam(QUERY_PARAM_WORKFLOW_INFOS, workflowInfos.serialize(objectMapper));
        }

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

    public NetworkModificationsResult moveModifications(UUID originGroupUuid, UUID targetGroupUuid, UUID beforeUuid, Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos, boolean buildTargetNode) {
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam(QUERY_PARAM_ACTION, ModificationsActionType.MOVE.name())
            .queryParam("originGroupUuid", originGroupUuid)
            .queryParam("build", buildTargetNode);
        if (beforeUuid != null) {
            path.queryParam("before", beforeUuid);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Pair<List<UUID>, List<ModificationApplicationContext>>> httpEntity = new HttpEntity<>(modificationContextInfos, headers);

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path.buildAndExpand(targetGroupUuid).toUriString(),
                HttpMethod.PUT,
                httpEntity,
                NetworkModificationsResult.class).getBody();
    }

    public NetworkModificationsResult duplicateOrInsertModifications(UUID groupUuid, ModificationsActionType action,
                                                                     Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos) {
        return handleModifications(groupUuid, null, action, modificationContextInfos);
    }

    private NetworkModificationsResult handleModifications(UUID groupUuid, UUID originGroupUuid, ModificationsActionType action,
                                                           Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos) {
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam(QUERY_PARAM_ACTION, action.name());

        if (originGroupUuid != null) {
            path.queryParam("originGroupUuid", originGroupUuid);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Pair<List<UUID>, List<ModificationApplicationContext>>> httpEntity = new HttpEntity<>(modificationContextInfos, headers);

        return restTemplate.exchange(
            getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString(),
            HttpMethod.PUT,
            httpEntity,
            NetworkModificationsResult.class
        ).getBody();
    }

    public Map<UUID, UUID> duplicateModificationsGroup(UUID sourceGroupUuid, UUID groupUuid) {
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
            return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.POST, new HttpEntity<>(headers), new ParameterizedTypeReference<Map<UUID, UUID>>() { }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, STUDY_CREATION_FAILED);
        }
    }

    public NetworkModificationsResult duplicateModificationsFromGroup(UUID groupUuid, UUID originGroupUuid, Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos) {
        return handleModifications(groupUuid, originGroupUuid, StudyConstants.ModificationsActionType.COPY, modificationContextInfos);
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

    public void verifyModifications(UUID groupUuid, Set<UUID> modificationUuids) {
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH + DELIMITER + "verify")
            .queryParam("uuids", modificationUuids)
            .buildAndExpand(groupUuid)
            .toUriString();

        restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, Void.class);
    }

    public void deleteIndexedModifications(List<UUID> groupUuids, UUID networkUuid) {
        if (groupUuids.isEmpty()) {
            return;
        }

        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + DELIMITER + "index")
            .queryParam("networkUuid", networkUuid)
            .queryParam("groupUuids", groupUuids)
            .toUriString();

        restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.DELETE, null, Void.class);
    }

    public Map<UUID, Object> searchModifications(UUID networkUuid, String userInput) {
        URI uri = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + "/indexation-infos")
                .queryParam("networkUuid", "{networkUuid}")
                .queryParam(PARAM_USER_INPUT, "{userInput}")
                .build(networkUuid, userInput);

        return restTemplate
                .exchange(uri, HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<UUID, Object>>() {
                        }).getBody();

    }
}
