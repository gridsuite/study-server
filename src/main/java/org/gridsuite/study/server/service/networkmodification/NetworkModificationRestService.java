/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.networkmodification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyConstants.CompositeModificationsActionType;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkTagsCheckInfos;
import org.gridsuite.study.server.dto.SharedModificationsWriteAccess;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.dto.workflow.AbstractWorkflowInfos;
import org.gridsuite.study.server.service.RootNetworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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
public class NetworkModificationRestService {

    private static final String DELIMITER = "/";
    private static final String COMPOSITE_PATH = "network-composite-modifications" + DELIMITER;
    private static final String GROUPS = "groups";
    private static final String GROUP_PATH = GROUPS + DELIMITER + "{groupUuid}";
    private static final String CONTAINERS = "containers";
    private static final String CONTAINER_PATH = CONTAINERS + DELIMITER + "{containerUuid}";
    private static final String NETWORK_MODIFICATIONS_PATH = "network-modifications";
    private static final String NETWORK_MODIFICATIONS_COUNT_PATH = "network-modifications-count";
    private static final String QUERY_PARAM_ACTION = "action";
    private static final String QUERY_PARAM_NAME = "name";
    private static final String QUERY_PARAM_GROUP_UUID = "groupUuid";
    private static final String QUERY_PARAM_ROOT_NETWORK_TAG = "rootNetworkTag";
    private static final String QUERY_PARAM_GROUP_UUIDS = "groupUuids";
    private static final String QUERY_PARAM_ROOT_NETWORK_TAGS = "rootNetworkTags";
    private static final String QUERY_PARAM_OLD_TAG = "oldTag";
    private static final String QUERY_PARAM_NEW_TAG = "newTag";
    private static final String ROOT_NETWORK_TAG_PATH = "root-network-tag";
    private static final String PARAM_USER_INPUT = "userInput";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RootNetworkService rootNetworkService;
    private String networkModificationServerBaseUri;

    @Autowired
    NetworkModificationRestService(RemoteServicesProperties remoteServicesProperties,
                                   RestClient restClient,
                                   ObjectMapper objectMapper, RootNetworkService rootNetworkService) {
        this.networkModificationServerBaseUri = remoteServicesProperties.getServiceUri("network-modification-server");
        this.restClient = restClient;
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
        return restClient.get().uri(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + "/catalog/line_types")
            .retrieve().body(String.class);
    }

    public String getLineType(UUID lineTypeUuid) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/catalog/line_types/{uuid}").buildAndExpand(lineTypeUuid).toUriString();
        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(String.class);
    }

    public String getLineTypeWithLimits(UUID lineTypeUuid, String area, String temperature, String shapeFactor) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/catalog/line_types/{uuid}/with-limits")
            .queryParam("area", area)
            .queryParamIfPresent("temperature", Optional.ofNullable(temperature))
            .queryParamIfPresent("shapeFactor", Optional.ofNullable(shapeFactor))
            .buildAndExpand(lineTypeUuid).toUriString();
        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(String.class);
    }

    public String getNetworkModification(UUID networkModificationUuid, String userId) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/{uuid}").buildAndExpand(networkModificationUuid).toUriString();
        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .headers(headers -> {
                if (userId != null) {
                    headers.set(HEADER_USER_ID, userId);
                }
            })
            .retrieve().body(String.class);
    }

    /**
     * @return the modification groups containing the given modification, including through references and composites
     */
    public List<UUID> getGroupsContainingModification(UUID networkModificationUuid) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/{uuid}/containing-groups").buildAndExpand(networkModificationUuid).toUriString();
        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(new ParameterizedTypeReference<List<UUID>>() { });
    }

    public String getBusBarSectionsForNewCoupler(String voltageLevelId, Integer busBarCount, Integer sectionCount, List<String> switchKindList) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/busbar-sections-for-new-coupler")
            .queryParam("voltageLevelId", voltageLevelId)
            .queryParam("busBarCount", busBarCount)
            .queryParam("sectionCount", sectionCount)
            .queryParamIfPresent("switchKindList", Optional.ofNullable(switchKindList))
            .build().toUriString();
        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(String.class);
    }

    public void updateNetworkModification(UUID networkModificationUuid, String modificationInfos, String userId) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/{uuid}").buildAndExpand(networkModificationUuid).toUriString();
        restClient.put().uri(getNetworkModificationServerURI(false) + path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .body(modificationInfos)
            .retrieve().toBodilessEntity();
    }

    public void unshareModificationReferences(List<UUID> modificationReferenceUuids, String userId) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + "/unshare").build().toUriString();
        restClient.put().uri(getNetworkModificationServerURI(false) + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(modificationReferenceUuids)
            .retrieve().toBodilessEntity();
    }

    public void updateNetworkModificationsMetadata(List<UUID> networkModificationUuids, String metadata, String userId) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH)
            .queryParam(UUIDS, networkModificationUuids).build().toUriString();
        restClient.put().uri(getNetworkModificationServerURI(false) + path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .body(metadata)
            .retrieve().toBodilessEntity();
    }

    /**
     * @param userId the user the modifications are read for, to supply if permissions are needed
     */
    public String getModifications(UUID groupUUid, boolean stashedModifications, boolean onlyMetadata, String userId) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(CONTAINER_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH)
            .queryParam(QUERY_PARAM_ONLY_STASHED, stashedModifications)
            .queryParam("onlyMetadata", onlyMetadata)
            .buildAndExpand(groupUUid)
            .toUriString();

        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .headers(headers -> {
                if (userId != null) {
                    headers.set(HEADER_USER_ID, userId);
                }
            })
            .retrieve().body(String.class);
    }

    public String getModificationsToExport(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(CONTAINER_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH + DELIMITER + "export")
            .buildAndExpand(groupUUid)
            .toUriString();

        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(String.class);
    }

    public Integer getModificationsCount(UUID groupUUid, boolean stashedModifications) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(CONTAINER_PATH + DELIMITER + NETWORK_MODIFICATIONS_COUNT_PATH)
            .queryParam(QUERY_PARAM_STASHED, stashedModifications)
            .buildAndExpand(groupUUid)
            .toUriString();

        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(Integer.class);
    }

    public void deleteModificationsByGroup(UUID groupUUid, String userId) {
        Objects.requireNonNull(groupUUid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .buildAndExpand(groupUUid)
            .toUriString();

        restClient.delete().uri(getNetworkModificationServerURI(false) + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
    }

    public void deleteModificationsByUuids(List<UUID> modificationsUuids, String userId) {
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(UUIDS, modificationsUuids)
                .buildAndExpand()
                .toUriString();
        restClient.delete().uri(path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
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

        return restClient.post().uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(getModificationContextJsonString(objectMapper, modificationContextInfos))
            .retrieve().body(NetworkModificationsResult.class);
    }

    public void updateModification(String createEquipmentAttributes, UUID modificationUuid, String userId) {
        Objects.requireNonNull(createEquipmentAttributes);

        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + modificationUuid)
                .buildAndExpand()
                .toUriString();

        var requestSpec = restClient.put().uri(path);
        if (userId != null) {
            requestSpec.header(HEADER_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createEquipmentAttributes);
        }
        requestSpec.retrieve().toBodilessEntity();
    }

    public void stashModifications(UUID groupUUid, List<UUID> modificationsUuids, String userId) {
        Objects.requireNonNull(groupUUid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(GROUP_UUID, groupUUid)
                .queryParam(QUERY_PARAM_STASHED, true)
                .buildAndExpand()
                .toUriString();

        restClient.put().uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
    }

    public void updateModificationsMetadata(UUID groupUUid, List<UUID> modificationsUuids, NetworkModificationMetadata metadata, String userId) {
        Objects.requireNonNull(groupUUid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
            .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
            .queryParam(UUIDS, modificationsUuids)
            .queryParam(GROUP_UUID, groupUUid)
            .buildAndExpand()
            .toUriString();

        var requestSpec = restClient.put().uri(path);
        if (userId != null) {
            requestSpec.header(HEADER_USER_ID, userId);
            if (metadata != null) {
                requestSpec.contentType(MediaType.APPLICATION_JSON)
                    .body(metadata);
            }
        }
        requestSpec.retrieve().toBodilessEntity();
    }

    public void updateNetworkModificationNameAndDescription(UUID networkModificationUuid, String metadata, String userId) {
        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + DELIMITER + networkModificationUuid.toString() + DELIMITER + "name-and-description")
                .build().toUriString();
        restClient.put().uri(getNetworkModificationServerURI(false) + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(metadata)
            .retrieve().toBodilessEntity();
    }

    public void restoreModifications(UUID groupUUid, List<UUID> modificationsUuids, String userId) {
        Objects.requireNonNull(groupUUid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH)
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(GROUP_UUID, groupUUid)
                .queryParam(QUERY_PARAM_STASHED, false)
                .buildAndExpand()
                .toUriString();

        restClient.put().uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
    }

    /**
     * @return references data of the modificationsUuids found among modificationsUuids
     */
    public List<ModificationReference> getModificationReferences(List<UUID> modificationsUuids) {
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + "references")
                .queryParam(UUIDS, modificationsUuids)
                .buildAndExpand()
                .toUriString();

        return restClient.get().uri(path)
            .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
            .retrieve().body(new ParameterizedTypeReference<List<ModificationReference>>() { });
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

        return restClient.get().uri(path)
            .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
            .retrieve().body(new ParameterizedTypeReference<Map<UUID, UUID>>() { });
    }

    public List<ModificationReference> getModificationReferences(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(CONTAINER_PATH + DELIMITER + "references");

        return restClient.get().uri(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString())
            .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
            .retrieve().body(new ParameterizedTypeReference<List<ModificationReference>>() { });
    }

    public void updateRootNetworkApplicability(List<UUID> modificationsUuids, String rootNetworkTag, boolean applicable, String userId) {
        Objects.requireNonNull(modificationsUuids);
        Objects.requireNonNull(rootNetworkTag);
        var path = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + "root-network-applicability")
                .queryParam(UUIDS, modificationsUuids)
                .queryParam(QUERY_PARAM_ROOT_NETWORK_TAG, rootNetworkTag)
                .queryParam(QUERY_PARAM_APPLICABLE, applicable)
                .buildAndExpand()
                .toUriString();

        restClient.put().uri(path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    public void addRootNetworkTag(List<UUID> groupUuids, String newTag) {
        Objects.requireNonNull(newTag);
        if (CollectionUtils.isEmpty(groupUuids)) {
            return;
        }
        var uri = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + ROOT_NETWORK_TAG_PATH)
                .queryParam(QUERY_PARAM_GROUP_UUIDS, groupUuids)
                .queryParam(QUERY_PARAM_NEW_TAG, "{newTag}")
                .encode()
                .buildAndExpand(newTag)
                .toUri();

        restClient.post().uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    /**
     * Renames a root network tag in the applicabilities held by the modifications of the given groups.
     */
    public void renameRootNetworkTag(List<UUID> groupUuids, String oldTag, String newTag, String userId) {
        Objects.requireNonNull(oldTag);
        Objects.requireNonNull(newTag);
        if (CollectionUtils.isEmpty(groupUuids)) {
            return;
        }
        var uri = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + DELIMITER + ROOT_NETWORK_TAG_PATH)
                .queryParam(QUERY_PARAM_GROUP_UUIDS, groupUuids)
                .queryParam(QUERY_PARAM_OLD_TAG, "{oldTag}")
                .queryParam(QUERY_PARAM_NEW_TAG, "{newTag}")
                .encode()
                .buildAndExpand(oldTag, newTag)
                .toUri();

        restClient.put().uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
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

        restClient.delete()
            .uri(path)
            .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
            .retrieve().toBodilessEntity();
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

        restClient.post().uri(getNetworkModificationServerURI(true) + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(buildInfos)
            .retrieve().toBodilessEntity();
    }

    public void stopBuild(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        String receiver = buildReceiver(nodeUuid, rootNetworkUuid);
        var path = UriComponentsBuilder.fromPath("build/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .build()
            .toUriString();

        restClient.put().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().toBodilessEntity();
    }

    public NetworkModificationsResult moveModifications(
            UUID originGroupUuid,
            UUID targetGroupUuid,
            List<ModificationMoveInfos> modificationMoveInfos,
            List<ModificationApplicationContext> applicationContexts,
            boolean buildTargetNode,
            String userId) {
        var path = UriComponentsBuilder.fromPath("groups/{groupUuid}/network-modifications/move")
                .queryParam("originGroupUuid", originGroupUuid)
                .queryParam("build", buildTargetNode);

        return restClient.put().uri(getNetworkModificationServerURI(false) + path.buildAndExpand(targetGroupUuid).toUriString())
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .body(Pair.of(modificationMoveInfos, applicationContexts))
            .retrieve().body(NetworkModificationsResult.class);
    }

    public NetworkModificationsResult duplicateModifications(UUID groupUuid,
                                                             Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos,
                                                             String userId) {
        return duplicateModifications(groupUuid, null, modificationContextInfos, userId);
    }

    public NetworkModificationsResult insertCompositeModifications(UUID groupUuid,
                                                                   CompositeModificationsActionType action,
                                                                   Pair<List<CompositeInfos>, List<ModificationApplicationContext>> modificationContextInfos) {
        Objects.requireNonNull(modificationContextInfos);
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + GROUP_PATH)
                .queryParam(QUERY_PARAM_ACTION, action.name());

        return restClient.put().uri(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(modificationContextInfos)
            .retrieve().body(NetworkModificationsResult.class);
    }

    /**
     * Asks the network modification server to take a composite modification out of its container, replacing it there
     * by a reference to it, so that it can be stored as an element in the directory server. The composite modification
     * keeps its own uuid.
     *
     * @return the reference modification left in place of the composite modification, either in the group of the node
     * or in a parent composite
     */
    public ModificationReference extractCompositeModificationToShare(@NonNull UUID groupUuid, @NonNull UUID modificationUuid, @NonNull String name, String description) {

        String path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + "{modificationUuid}" + DELIMITER + "share")
                .queryParam(QUERY_PARAM_NAME, name)
                .queryParam(QUERY_PARAM_GROUP_UUID, groupUuid)
                .buildAndExpand(modificationUuid)
                .toUriString();

        var requestSpec = restClient.post().uri(getNetworkModificationServerURI(false) + path);
        if (description != null) {
            requestSpec.body(description);
        }
        return requestSpec.retrieve().body(ModificationReference.class);
    }

    public UUID assembleModificationsIntoComposite(@NonNull List<UUID> modificationsUuids, String userId) {
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH);

        return restClient.post().uri(getNetworkModificationServerURI(false) + path.toUriString())
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .body(modificationsUuids)
            .retrieve().body(UUID.class);
    }

    private NetworkModificationsResult duplicateModifications(UUID targetGroupUuid, UUID sourceGroupUuid,
                                                              Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos,
                                                              String userId) {
        Objects.requireNonNull(modificationContextInfos);
        var path = UriComponentsBuilder.fromPath("groups/{groupUuid}/network-modifications/copy");
        if (sourceGroupUuid != null) {
            path.queryParam("sourceContainerUuid", sourceGroupUuid);
        }

        return restClient.put().uri(getNetworkModificationServerURI(false) + path.buildAndExpand(targetGroupUuid).toUriString())
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .body(modificationContextInfos)
            .retrieve().body(NetworkModificationsResult.class);
    }

    public void duplicateModificationsGroup(UUID sourceGroupUuid, UUID groupUuid, List<String> rootNetworkTags, String userId) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(sourceGroupUuid);
        var uri = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + "groups/" + sourceGroupUuid + "/duplicate")
                .queryParam(QUERY_PARAM_GROUP_UUID, groupUuid)
                .queryParam(QUERY_PARAM_ROOT_NETWORK_TAGS, Collections.nCopies(rootNetworkTags.size(), "{rootNetworkTag}"))
                .encode()
                .buildAndExpand(rootNetworkTags.toArray())
                .toUri();

        restClient.post().uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
    }

    public NetworkModificationsResult duplicateModificationsFromGroup(UUID groupUuid, UUID originGroupUuid,
                                                                      Pair<List<UUID>, List<ModificationApplicationContext>> modificationContextInfos,
                                                                      String userId) {
        return duplicateModifications(groupUuid, originGroupUuid, modificationContextInfos, userId);
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

    public void deleteStashedModificationsFromGroups(List<UUID> groupUuids, String userId) {
        Objects.requireNonNull(groupUuids);
        if (groupUuids.isEmpty()) {
            return;
        }
        var path = UriComponentsBuilder.fromPath(GROUPS + "/stashed-modifications")
                .toUriString();

        restClient.method(HttpMethod.DELETE)
            .uri(getNetworkModificationServerURI(false) + path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .body(toJson(groupUuids))
            .retrieve().toEntity(new ParameterizedTypeReference<Map<UUID, UUID>>() { });
    }

    public void createSharingLinks(List<UUID> groupUuids, String userId) {
        createSharingLinks(groupUuids, false, userId);
    }

    /**
     * Asks network-modification-server to notify back the recreation of the references of the group, so that they are created in directory-server.
     * Called after the commit only, so that the node holding the group is resolvable when the notification is consumed.
     */
    public void createSharingLinks(List<UUID> groupUuids, boolean recreate, String userId) {
        Objects.requireNonNull(groupUuids);
        var path = UriComponentsBuilder.fromPath(GROUPS + "/sharing-links")
            .queryParam("recreate", recreate)
            .toUriString();

        restClient.post().uri(getNetworkModificationServerURI(false) + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(groupUuids)
            .retrieve().toBodilessEntity();
    }

    public void deleteModificationReferences(UUID groupUuid, String userId) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + "/references")
                .buildAndExpand(groupUuid)
                .toUriString();

        restClient.delete().uri(getNetworkModificationServerURI(false) + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
    }

    public void verifyModifications(UUID groupUuid, Set<UUID> modificationUuids) {
        var path = UriComponentsBuilder.fromPath(CONTAINER_PATH + DELIMITER + NETWORK_MODIFICATIONS_PATH + DELIMITER + "verify")
            .queryParam("uuids", modificationUuids)
            .buildAndExpand(groupUuid)
            .toUriString();

        restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().toBodilessEntity();
    }

    public void deleteIndexedModifications(List<UUID> groupUuids, UUID networkUuid) {
        if (groupUuids.isEmpty()) {
            return;
        }

        String path = UriComponentsBuilder.fromPath(NETWORK_MODIFICATIONS_PATH + DELIMITER + "index")
            .queryParam("networkUuid", networkUuid)
            .queryParam(QUERY_PARAM_GROUP_UUIDS, groupUuids)
            .toUriString();

        restClient.delete()
            .uri(getNetworkModificationServerURI(false) + path)
            .retrieve().toBodilessEntity();
    }

    public Map<UUID, Object> searchModifications(UUID networkUuid, String userInput) {
        URI uri = UriComponentsBuilder
                .fromUriString(getNetworkModificationServerURI(false) + NETWORK_MODIFICATIONS_PATH + "/indexation-infos")
                .queryParam("networkUuid", "{networkUuid}")
                .queryParam(PARAM_USER_INPUT, "{userInput}")
                .build(networkUuid, userInput);

        return restClient.get().uri(uri)
            .retrieve().body(new ParameterizedTypeReference<Map<UUID, Object>>() { });

    }

    /**
     * @return modificationUuids plus the uuids of all the modifications contained into the composite modifications that they reference
     */
    public Set<UUID> expandToLeafUuids(List<UUID> modificationUuids) {
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + "children-uuids")
                .queryParam("uuids", modificationUuids)
                .toUriString();

        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(new ParameterizedTypeReference<Set<UUID>>() { });
    }

    public List<UUID> findAllChildrenUuids(List<UUID> compositeUuids) {
        if (compositeUuids.isEmpty()) {
            return List.of();
        }
        var path = UriComponentsBuilder.fromPath(COMPOSITE_PATH + "children-uuids")
                .queryParam("uuids", compositeUuids)
                .toUriString();

        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(new ParameterizedTypeReference<List<UUID>>() { });
    }

    public boolean hasModificationReferences(List<UUID> containerUuids) {
        String path = UriComponentsBuilder.fromPath(CONTAINERS + DELIMITER + "references/exists")
                .queryParam(UUIDS, containerUuids)
                .build().toUriString();
        return Boolean.TRUE.equals(restClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve().body(Boolean.class));
    }

    public Set<UUID> getContainersWithModificationReferences(List<UUID> containerUuids) {
        if (containerUuids.isEmpty()) {
            return Set.of();
        }
        // the uuids go in the body as a large tree would exceed the url length limit
        String path = UriComponentsBuilder.fromPath(CONTAINERS + DELIMITER + "reference-modifications")
                .build().toUriString();
        List<UUID> containersWithReferenceModifications = restClient.post().uri(getNetworkModificationServerURI(false) + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(containerUuids)
                .retrieve().body(new ParameterizedTypeReference<List<UUID>>() { });
        return containersWithReferenceModifications == null ? Set.of() : new HashSet<>(containersWithReferenceModifications);
    }

    public SharedModificationsWriteAccess getReferencedModificationsWriteAccess(List<UUID> containerUuids, String userId) {
        String path = UriComponentsBuilder.fromPath(CONTAINERS + DELIMITER + "references/write-access")
                .queryParam(UUIDS, containerUuids)
                .build().toUriString();
        return restClient.get().uri(getNetworkModificationServerURI(false) + path)
                .headers(headers -> headers.set(HEADER_USER_ID, userId))
                .retrieve().body(SharedModificationsWriteAccess.class);
    }

    /**
     * Throws forbidden when the unwritable shared modifications involved miss some of the tags.
     */
    public void assertRootNetworkTagsAllowed(List<UUID> containerUuids, List<UUID> modificationUuids, List<UUID> sharedUuids,
                                             List<String> rootNetworkTags, String userId) {
        if (rootNetworkTags.isEmpty() || containerUuids.isEmpty() && modificationUuids.isEmpty() && sharedUuids.isEmpty()) {
            return;
        }
        restClient.post().uri(getNetworkModificationServerURI(false) + CONTAINERS + DELIMITER + "root-network-tags/authorized")
                .headers(headers -> headers.set(HEADER_USER_ID, userId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RootNetworkTagsCheckInfos(containerUuids, modificationUuids, sharedUuids, rootNetworkTags))
                .retrieve().toBodilessEntity();
    }

    /**
     * References among {@code modificationUuids} and among the modifications nested in them: getReferences() does not
     * descend into composites, so a reference sitting inside a copied/inserted composite must be looked up explicitly.
     */
    //TODO fetch references for modifications and its children
    // Transfer this in network modification server
    public List<ModificationReference> getChildrenModificationsReferences(List<UUID> modificationUuids) {
        List<UUID> uuids = new ArrayList<>(modificationUuids);
        uuids.addAll(findAllChildrenUuids(modificationUuids));
        return getModificationReferences(uuids);
    }
}
