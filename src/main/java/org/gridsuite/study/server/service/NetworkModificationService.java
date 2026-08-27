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
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReferenceData;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.dto.workflow.AbstractWorkflowInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.utils.JsonUtils.getModificationContextJsonString;

/**
 * @author Slimane amar <slimane.amar at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NetworkModificationService {

    private static final String DELIMITER = "/";
    private static final String COMPOSITE_PATH = "network-composite-modifications" + DELIMITER;
    private static final String GROUP_PATH = "groups" + DELIMITER + "{groupUuid}";
    private static final String CONTAINER_PATH = "containers" + DELIMITER + "{containerId}";
    private static final String NETWORK_MODIFICATIONS_PATH = "network-modifications";
    private static final String NETWORK_MODIFICATIONS_COUNT_PATH = "network-modifications-count";
    private static final String QUERY_PARAM_ACTION = "action";
    private static final String QUERY_PARAM_ROOT_NETWORK_TAG = "rootNetworkTag";
    private static final String QUERY_PARAM_GROUP_UUIDS = "groupUuids";
    private static final String QUERY_PARAM_ROOT_NETWORK_TAGS = "rootNetworkTags";
    private static final String ROOT_NETWORK_TAG_PATH = "root-network-tag";
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
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
    }

    private String getNetworkModificationServerURI(boolean addNetworksPart) {
        return this.networkModificationServerBaseUri + DELIMITER + NETWORK_MODIFICATION_API_VERSION + DELIMITER + (addNetworksPart ? "networks" + DELIMITER : "");
    }

    private String buildPathFrom(UUID networkUuid) {
        return UriComponentsBuilder.fromPath("{networkUuid}" + DELIMITER)
                .buildAndExpand(networkUuid)
                .toUriString();
    }

    public String getLineTypesCatalog() {
        return restTemplate.getForObject(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + "/catalog/line_types", String.class);
    }

    public String getLineType(UUID lineTypeUuid) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/catalog/line_types/{uuid}").buildAndExpand(lineTypeUuid).toUriString();
        return restTemplate.getForObject(getNetworkModificationServerURI(false) + path, String.class);
    }

    public String getLineTypeWithLimits(UUID lineTypeUuid, String area, String temperature, String shapeFactor) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/catalog/line_types/{uuid}/with-limits")
            .queryParam("area", area)
            .queryParamIfPresent("temperature", Optional.ofNullable(temperature))
            .queryParamIfPresent("shapeFactor", Optional.ofNullable(shapeFactor))
            .buildAndExpand(lineTypeUuid).toUriString();
        return restTemplate.getForObject(getNetworkModificationServerURI(false) + path, String.class);
    }

    public String getNetworkModificationsFromComposite(List<UUID> compositeModificationUuids, boolean onlyMetadata) {
        String path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + NETWORK_MODIFICATIONS_PATH)
            .queryParam(UUIDS, compositeModificationUuids)
            .queryParam("onlyMetadata", onlyMetadata)
            .build().toUriString();
        return restTemplate.getForObject(getNetworkModificationServerURI(false) + path, String.class);
    }

    public String getNetworkModification(UUID networkModificationUuid) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/{uuid}").buildAndExpand(networkModificationUuid).toUriString();
        return restTemplate.getForObject(getNetworkModificationServerURI(false) + path, String.class);
    }

    public String getBusBarSectionsForNewCoupler(String voltageLevelId, Integer busBarCount, Integer sectionCount, List<String> switchKindList) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/busbar-sections-for-new-coupler")
            .queryParam("voltageLevelId", voltageLevelId)
            .queryParam("busBarCount", busBarCount)
            .queryParam("sectionCount", sectionCount)
            .queryParamIfPresent("switchKindList", Optional.ofNullable(switchKindList))
            .build().toUriString();
        return restTemplate.getForObject(getNetworkModificationServerURI(false) + path, String.class);
    }

    public void updateNetworkModification(UUID networkModificationUuid, String modificationInfos) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/{uuid}").buildAndExpand(networkModificationUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.PUT, new HttpEntity<>(modificationInfos, headers), Void.class);
    }

    public void updateNetworkModificationsMetadata(List<UUID> networkModificationUuids, String metadata) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH)
            .queryParam(UUIDS, networkModificationUuids).build().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.PUT, new HttpEntity<>(metadata, headers), Void.class);
    }

    public String getModifications(UUID groupUUid, boolean stashedModifications, boolean onlyMetadata) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH)
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .queryParam(QUERY_PARAM_ONLY_STASHED, stashedModifications)
            .queryParam("onlyMetadata", onlyMetadata)
            .buildAndExpand(groupUUid)
            .toUriString();

        return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, String.class).getBody();
    }

    public String getModificationsToExport(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH + DELIMITER + "export")
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .buildAndExpand(groupUUid)
            .toUriString();

        return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, String.class).getBody();
    }

    public Integer getModificationsCount(UUID groupUUid, boolean stashedModifications) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + NETWORK_MODIFICATIONS_COUNT_PATH)
            .queryParam(QUERY_PARAM_STASHED, stashedModifications)
            .buildAndExpand(groupUUid)
            .toUriString();

        return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, Integer.class).getBody();
    }

    public void deleteModifications(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
            .buildAndExpand(groupUUid)
            .toUriString();

        restTemplate.delete(getNetworkModificationServerURI(false) + path);
    }

    public void deleteModifications(UUID groupUuid, List<UUID> modificationsUuids) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(GROUP_UUID, groupUuid)
                .buildAndExpand()
                .toUriString();
        restTemplate.delete(path);
    }

    public NetworkModificationsResult createModification(UUID groupUuid,
                                                         Pair<String, List<ModificationApplicationContext>> modificationContextInfos) {
        Objects.requireNonNull(modificationContextInfos);

        var uriComponentsBuilder = UriComponentsBuilder
            .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
            .queryParam(GROUP_UUID, groupUuid);

        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(getModificationContextJsonString(objectMapper, modificationContextInfos), headers);
        return restTemplate.exchange(path, HttpMethod.POST, httpEntity, NetworkModificationsResult.class).getBody();
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

        restTemplate.exchange(path, HttpMethod.PUT, httpEntity, Void.class);
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
        restTemplate.exchange(path, HttpMethod.PUT, httpEntity, Void.class);
    }

    public void updateModificationsMetadata(UUID groupUUid, List<UUID> modificationsUuids, NetworkModificationMetadata metadata) {
        Objects.requireNonNull(groupUUid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
            .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
            .queryParam(UUIDS, modificationsUuids)
            .queryParam(GROUP_UUID, groupUUid)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<NetworkModificationMetadata> httpEntity = new HttpEntity<>(metadata, headers);
        restTemplate.exchange(path, HttpMethod.PUT, httpEntity, Void.class);
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

        restTemplate.exchange(path, HttpMethod.PUT, httpEntity, Void.class);
    }

    /**
     * @return references data of the modificationsUuids found among modificationsUuids
     */
    public List<ReferenceData> getReferences(List<UUID> modificationsUuids) {
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + "references")
                .queryParam(UUIDS, modificationsUuids)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                path,
                HttpMethod.GET,
                httpEntity,
                new ParameterizedTypeReference<List<ReferenceData>>() { }
        ).getBody();
    }

    /**
     * @return modification uuid -> uuid of the composite currently containing it; modifications sitting directly
     * under a group (or not found) have no entry
     */
    public Map<UUID, UUID> findParentComposites(List<UUID> modificationsUuids) {
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + COMPOSITE_PATH + "parent-composites")
                .queryParam(UUIDS, modificationsUuids)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                path,
                HttpMethod.GET,
                httpEntity,
                new ParameterizedTypeReference<Map<UUID, UUID>>() { }
        ).getBody();
    }

    /**
     * @return references data of the modifications in the group :
     * - element uuid in directory server
     * - uuid of its mother composite (null if the modification is at the root level)
     */
    public List<ReferenceData> getReferencesFromGroup(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + "references");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<ReferenceData>> httpEntity = new HttpEntity<>(headers);

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString(),
                HttpMethod.GET,
                httpEntity,
                new ParameterizedTypeReference<List<ReferenceData>>() { }
        ).getBody();
    }

    public void updateRootNetworkApplicability(List<UUID> modificationsUuids, String rootNetworkTag, boolean applicable) {
        Objects.requireNonNull(modificationsUuids);
        Objects.requireNonNull(rootNetworkTag);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + "root-network-applicability")
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(QUERY_PARAM_ROOT_NETWORK_TAG, rootNetworkTag)
                .queryParam(QUERY_PARAM_APPLICABLE, applicable)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    /**
     * Renames a root network tag in the applicabilities held by the modifications of the given groups.
     */
    public void renameRootNetworkTag(List<UUID> groupUuids, String oldTag, String newTag) {
        Objects.requireNonNull(oldTag);
        Objects.requireNonNull(newTag);
        if (CollectionUtils.isEmpty(groupUuids)) {
            return;
        }
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + ROOT_NETWORK_TAG_PATH)
                .queryParam(QUERY_PARAM_GROUP_UUIDS, groupUuids)
                .queryParam("oldTag", oldTag)
                .queryParam("newTag", newTag)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    /**
     * Drops root network tags from the applicabilities held by the modifications of the given groups.
     */
    public void deleteRootNetworkTags(List<UUID> groupUuids, List<String> rootNetworkTags) {
        if (CollectionUtils.isEmpty(groupUuids) || CollectionUtils.isEmpty(rootNetworkTags)) {
            return;
        }
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + ROOT_NETWORK_TAG_PATH)
                .queryParam(QUERY_PARAM_GROUP_UUIDS, groupUuids)
                .queryParam(QUERY_PARAM_ROOT_NETWORK_TAGS, rootNetworkTags)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }

    public void buildNode(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull BuildInfos buildInfos, AbstractWorkflowInfos workflowInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String receiver = buildReceiver(nodeUuid, rootNetworkUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "build");

        if (workflowInfos != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_WORKFLOW_TYPE, workflowInfos.getType());
            uriComponentsBuilder.queryParam(QUERY_PARAM_WORKFLOW_INFOS, URLEncoder.encode(toJson(workflowInfos), StandardCharsets.UTF_8));
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

    public NetworkModificationsResult moveModifications(
            MoveModificationInfos moveModificationInfos,
            Pair<List<UUID>, List<ModificationApplicationContext>> body,
            boolean buildTargetNode) {
        UUID sourceContainerId = moveModificationInfos.source().id();
        UUID targetContainerId = moveModificationInfos.target().id();
        UUID beforeUuid = moveModificationInfos.beforeUuid();

        var path = UriComponentsBuilder.fromPath(CONTAINER_PATH)
                .queryParam(QUERY_PARAM_ACTION, ModificationsActionType.MOVE.name())
                .queryParam("sourceContainerId", sourceContainerId)
                .queryParam("sourceContainerType", moveModificationInfos.source().type())
                .queryParam("targetContainerType", moveModificationInfos.target().type())
                .queryParam("build", buildTargetNode);
        if (beforeUuid != null) {
            path.queryParam("before", beforeUuid);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Pair<List<UUID>, List<ModificationApplicationContext>>> httpEntity = new HttpEntity<>(body, headers);

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path.buildAndExpand(targetContainerId).toUriString(),
                HttpMethod.PUT, httpEntity, NetworkModificationsResult.class).getBody();
    }

    public NetworkModificationsResult duplicateModifications(UUID groupUuid,
                                                             Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos) {
        return handleModifications(groupUuid, null, ModificationsActionType.COPY, modificationContextInfos);
    }

    public NetworkModificationsResult insertCompositeModifications(UUID groupUuid,
                                                                   CompositeModificationsActionType action,
                                                                   Pair<List<CompositeInfos>, List<ModificationApplicationContext>> modificationContextInfos) {
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + GROUP_PATH)
                .queryParam(QUERY_PARAM_ACTION, action.name());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Pair<List<CompositeInfos>, List<ModificationApplicationContext>>> httpEntity = new HttpEntity<>(modificationContextInfos, headers);

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString(),
                HttpMethod.PUT,
                httpEntity,
                NetworkModificationsResult.class
        ).getBody();
    }

    public UUID assembleModificationsIntoComposite(@NonNull List<UUID> modificationsUuids) {
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<UUID>> httpEntity = new HttpEntity<>(
                modificationsUuids,
                headers
        );

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path.toUriString(),
                HttpMethod.POST,
                httpEntity,
                UUID.class
        ).getBody();
    }

    private NetworkModificationsResult handleModifications(UUID groupUuid, UUID originGroupUuid, ModificationsActionType action,
                                                           Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos) {
        var path = UriComponentsBuilder.fromPath(CONTAINER_PATH)
            .queryParam(QUERY_PARAM_ACTION, action.name());

        if (originGroupUuid != null) {
            path.queryParam("sourceContainerId", originGroupUuid);
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

    public void duplicateModificationsGroup(UUID sourceGroupUuid, UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(sourceGroupUuid);
        var path = UriComponentsBuilder.fromPath("groups/{uuid}/duplicate")
                .queryParam("groupUuid", groupUuid)
                .buildAndExpand(sourceGroupUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(
            getNetworkModificationServerURI(false) + path,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Void.class
        );
    }

    public NetworkModificationsResult duplicateModificationsFromGroup(UUID groupUuid, UUID originGroupUuid, Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos) {
        return handleModifications(groupUuid, originGroupUuid, StudyConstants.ModificationsActionType.COPY, modificationContextInfos);
    }

    private String buildReceiver(UUID nodeUuid, UUID rootNetworkUuid) {
        return URLEncoder.encode(toJson(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
    }

    private String toJson(Object object) {
        String json;
        try {
            json = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        return json;
    }

    public void deleteStashedModifications(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + "/stashed-modifications")
                .queryParam(QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND, false)
                .buildAndExpand(groupUUid)
                .toUriString();

        restTemplate.delete(getNetworkModificationServerURI(false) + path);
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

    /**
     * @return modificationUuids plus the uuids of all the modifications contained into the composite modifications that they reference
     */
    public Set<UUID> expandToLeafUuids(List<UUID> modificationUuids) {
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + "children-uuids")
                .queryParam("uuids", modificationUuids)
                .toUriString();

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<UUID>>() { }
        ).getBody();
    }

    public List<UUID> findAllChildrenUuids(List<UUID> compositeUuids) {
        if (compositeUuids.isEmpty()) {
            return List.of();
        }
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + "children-uuids")
                .queryParam("uuids", compositeUuids)
                .toUriString();

        return restTemplate.exchange(
                getNetworkModificationServerURI(false) + path,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<UUID>>() { }
        ).getBody();
    }
}
