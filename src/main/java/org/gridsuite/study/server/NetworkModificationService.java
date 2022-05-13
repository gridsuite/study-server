/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.Receiver;
import org.gridsuite.study.server.dto.modification.EquipmentDeletionInfos;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.NETWORK_MODIFICATION_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.StudyService.QUERY_PARAM_RECEIVER;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NetworkModificationService {

    private static final String DELIMITER = "/";
    public static final String GROUP_PATH = "groups" + DELIMITER + "{groupUuid}";
    private static final String GROUP = "group";
    private static final String MODIFICATIONS_PATH = "modifications";

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

    void setNetworkModificationServerBaseUri(String networkModificationServerBaseUri) {
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

    public List<ModificationInfos> getModifications(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .buildAndExpand(groupUuid)
            .toUriString();

        return restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<ModificationInfos>>() { }).getBody();
    }

    public void deleteModifications(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        deleteNetworkModifications(groupUUid);
    }

    private void deleteNetworkModifications(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .buildAndExpand(groupUuid)
            .toUriString();

        try {
            restTemplate.delete(getNetworkModificationServerURI(false) + path);
        } catch (HttpStatusCodeException e) {
            // Ignore because modification group does not exist if no modifications
            if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw e;
            }
        }
    }

    List<EquipmentModificationInfos> changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID groupUuid,
            String variantId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(switchId);
        List<EquipmentModificationInfos> result;

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(buildPathFrom(networkUuid) + "switches" + DELIMITER + "{switchId}")
                .queryParam(GROUP, groupUuid).queryParam("open", open);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand(switchId)
            .toUriString();

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.PUT, null,
                    new ParameterizedTypeReference<List<EquipmentModificationInfos>>() {
                    }).getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(ELEMENT_NOT_FOUND);
            }
            throw e;
        }

        return result;
    }

    public List<ModificationInfos> applyGroovyScript(UUID studyUuid, String groovyScript, UUID groupUuid,
            String variantId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(groovyScript);
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "groovy")
                .queryParam(GROUP, groupUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpEntity<String> httpEntity = new HttpEntity<>(groovyScript);

        return restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.PUT, httpEntity,
                new ParameterizedTypeReference<List<ModificationInfos>>() {
                }).getBody();
    }

    List<ModificationInfos> changeLineStatus(UUID studyUuid, String lineId, String status, UUID groupUuid,
            String variantId) {
        List<ModificationInfos> result;
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(buildPathFrom(networkUuid) + "lines" + DELIMITER + "{lineId}" + DELIMITER + "status")
                .queryParam(GROUP, groupUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand(lineId)
            .toUriString();

        HttpEntity<String> httpEntity = new HttpEntity<>(status);

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.PUT, httpEntity,
                    new ParameterizedTypeReference<List<ModificationInfos>>() {
                    }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e.getResponseBodyAsString(), LINE_MODIFICATION_FAILED);
        }

        return result;
    }

    private StudyException handleChangeError(String responseBody, StudyException.Type type) {
        String message = null;
        try {
            JsonNode node = new ObjectMapper().readTree(responseBody).path("message");
            if (!node.isMissingNode()) {
                message = node.asText();
            }
        } catch (JsonProcessingException e) {
            if (!responseBody.isEmpty()) {
                message = responseBody;
            }
        }
        return new StudyException(type, message);
    }

    public List<EquipmentModificationInfos> createEquipment(UUID studyUuid, String createEquipmentAttributes,
            UUID groupUuid, ModificationType modificationType, String variantId) {
        List<EquipmentModificationInfos> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(createEquipmentAttributes);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(buildPathFrom(networkUuid) + ModificationType.getUriFromType(modificationType))
                .queryParam(GROUP, groupUuid);
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
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.POST, httpEntity,
                    new ParameterizedTypeReference<List<EquipmentModificationInfos>>() {
                    }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e.getResponseBodyAsString(), ModificationType.getExceptionFromType(modificationType));
        }

        return result;
    }

    public List<EquipmentModificationInfos> modifyEquipment(UUID studyUuid, String modifyEquipmentAttributes,
            UUID groupUuid, ModificationType modificationType, String variantId) {
        List<EquipmentModificationInfos> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(modifyEquipmentAttributes);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(buildPathFrom(networkUuid) + ModificationType.getUriFromType(modificationType))
                .queryParam(GROUP, groupUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(modifyEquipmentAttributes, headers);

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.PUT, httpEntity,
                    new ParameterizedTypeReference<List<EquipmentModificationInfos>>() {
                    }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e.getResponseBodyAsString(), ModificationType.getExceptionFromType(modificationType));
        }

        return result;
    }

    public void updateEquipmentCreation(String createEquipmentAttributes, ModificationType modificationType,
            UUID modificationUuid) {
        Objects.requireNonNull(createEquipmentAttributes);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(MODIFICATIONS_PATH + DELIMITER + modificationUuid
                + DELIMITER + ModificationType.getUriFromType(modificationType) + "-creation");
        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(createEquipmentAttributes, headers);

        try {
            restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.PUT, httpEntity,
                    Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e.getResponseBodyAsString(), ModificationType.getExceptionFromType(modificationType));
        }
    }

    public void updateEquipmentModification(String modifyEquipmentAttributes, ModificationType modificationType, UUID modificationUuid) {
        Objects.requireNonNull(modifyEquipmentAttributes);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(MODIFICATIONS_PATH + DELIMITER + modificationUuid + DELIMITER + ModificationType.getUriFromType(modificationType) + "-modification");
        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();

        try {
            restTemplate.put(getNetworkModificationServerURI(false) + path, modifyEquipmentAttributes);
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e.getResponseBodyAsString(), ModificationType.getExceptionFromType(modificationType));
        }
    }

    public List<EquipmentDeletionInfos> deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID groupUuid, String variantId) {
        List<EquipmentDeletionInfos> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(equipmentType);
        Objects.requireNonNull(equipmentId);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "equipments" + DELIMITER
                + "type" + DELIMITER + "{equipmentType}" + DELIMITER + "id" + DELIMITER + "{equipmentId}")
                .queryParam(GROUP, groupUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand(equipmentType, equipmentId)
            .toUriString();

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.DELETE, null,
                    new ParameterizedTypeReference<List<EquipmentDeletionInfos>>() {
                    }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e.getResponseBodyAsString(), DELETE_EQUIPMENT_FAILED);
        }

        return result;
    }

    void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull BuildInfos buildInfos) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)),
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

    public void stopBuild(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)),
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

    public void deleteModifications(UUID groupUuid, List<UUID> modificationsUuids) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + MODIFICATIONS_PATH);
        path.queryParam("modificationsUuids", modificationsUuids);
        try {
            restTemplate.delete(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString());
        } catch (HttpStatusCodeException e) {
            // Ignore 404 because modification group does not exist if no modifications
            if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw e;
            }
        }
    }

    public void reorderModification(UUID groupUuid, UUID modificationUuid, UUID beforeUuid) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(modificationUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH
                + DELIMITER + MODIFICATIONS_PATH + DELIMITER + "move")
                .queryParam("modificationsToMove", modificationUuid);
        if (beforeUuid != null) {
            path.queryParam("before", beforeUuid);
        }

        try {
            restTemplate.put(getNetworkModificationServerURI(false)
                            + path.buildAndExpand(groupUuid, modificationUuid).toUriString(), null);
        } catch (HttpStatusCodeException e) {
            //Ignore because modification group does not exist if no modifications
            if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw e;
            }
        }
    }
}
