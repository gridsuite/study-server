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
import org.gridsuite.study.server.dto.modification.EquipmentDeletionInfos;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
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
    private static final String GROUP = "group";
    private static final String MODIFICATIONS_PATH = "modifications";
    private static final String QUERY_PARAM_RECEIVER = "receiver";

    private String networkModificationServerBaseUri;

    private final NetworkService networkStoreService;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper;

    @Autowired
    NetworkModificationService(@Value("${backing-services.network-modification.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri,
                               NetworkService networkStoreService,
                               ObjectMapper objectMapper, StreamBridge modificationUpdatePublisher) {
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

    public void deleteModifications(UUID groupUuid, List<UUID> modificationsUuids) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + MODIFICATIONS_PATH);
        path.queryParam("modificationsUuids", modificationsUuids);
        try {
            restTemplate.delete(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString());
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, DELETE_MODIFICATIONS_FAILED);
        }
    }

    List<EquipmentModificationInfos> changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID groupUuid, String variantId, UUID reportUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(switchId);
        List<EquipmentModificationInfos> result;

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(buildPathFrom(networkUuid) + "switches" + DELIMITER + "{switchId}")
            .queryParam(GROUP, groupUuid)
            .queryParam(REPORT_UUID, reportUuid)
            .queryParam("open", open);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + uriComponentsBuilder.build().toUriString(), HttpMethod.PUT, null,
                    new ParameterizedTypeReference<List<EquipmentModificationInfos>>() { }, switchId).getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(ELEMENT_NOT_FOUND);
            }
            throw e;
        }

        return result;
    }

    public List<ModificationInfos> applyGroovyScript(UUID studyUuid, String groovyScript, UUID groupUuid, String variantId, UUID reportUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(groovyScript);
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "groovy")
            .queryParam(GROUP, groupUuid)
            .queryParam(REPORT_UUID, reportUuid);
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

    List<ModificationInfos> changeLineStatus(UUID studyUuid, String lineId, String status, UUID groupUuid, String variantId, UUID reportUuid) {
        List<ModificationInfos> result;
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(buildPathFrom(networkUuid) + "lines" + DELIMITER + "{lineId}" + DELIMITER + "status")
                .queryParam(GROUP, groupUuid)
                .queryParam(REPORT_UUID, reportUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        HttpEntity<String> httpEntity = new HttpEntity<>(status);

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + uriComponentsBuilder.build(), HttpMethod.PUT, httpEntity,
                    new ParameterizedTypeReference<List<ModificationInfos>>() {
                    }, lineId).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, LINE_MODIFICATION_FAILED);
        }

        return result;
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

    public List<EquipmentModificationInfos> createEquipment(UUID studyUuid, String createEquipmentAttributes,
            UUID groupUuid, ModificationType modificationType, String variantId, UUID reportUuid) {
        List<EquipmentModificationInfos> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(createEquipmentAttributes);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(buildPathFrom(networkUuid) + ModificationType.getUriFromType(modificationType))
                .queryParam(GROUP, groupUuid)
                .queryParam(REPORT_UUID, reportUuid);
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
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
        }

        return result;
    }

    public List<EquipmentModificationInfos> modifyEquipment(UUID studyUuid, String modifyEquipmentAttributes,
            UUID groupUuid, ModificationType modificationType, String variantId, UUID reportUuid) {
        List<EquipmentModificationInfos> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(modifyEquipmentAttributes);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(buildPathFrom(networkUuid) + ModificationType.getUriFromType(modificationType))
                .queryParam(GROUP, groupUuid)
                .queryParam(REPORT_UUID, reportUuid);
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
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
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
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
        }
    }

    public void updateEquipmentModification(String modifyEquipmentAttributes, ModificationType modificationType, UUID modificationUuid) {
        Objects.requireNonNull(modifyEquipmentAttributes);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(MODIFICATIONS_PATH + DELIMITER + modificationUuid + DELIMITER + ModificationType.getUriFromType(modificationType));
        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(modifyEquipmentAttributes, headers);
        try {
            restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.PUT, httpEntity,
                    Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
        }
    }

    public List<EquipmentDeletionInfos> deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID groupUuid, String variantId, UUID reportUuid) {
        List<EquipmentDeletionInfos> result;
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(equipmentType);
        Objects.requireNonNull(equipmentId);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "equipments" + DELIMITER
                + "type" + DELIMITER + "{equipmentType}" + DELIMITER + "id" + DELIMITER + "{equipmentId}")
                .queryParam(GROUP, groupUuid)
                .queryParam(REPORT_UUID, reportUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + uriComponentsBuilder.build(), HttpMethod.DELETE, null,
                    new ParameterizedTypeReference<List<EquipmentDeletionInfos>>() {
                    }, equipmentType, equipmentId).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, DELETE_EQUIPMENT_FAILED);
        }

        return result;
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

    public void reorderModification(UUID groupUuid, List<UUID> modificationUuidList, UUID beforeUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam("action", "MOVE");
        if (beforeUuid != null) {
            path.queryParam("before", beforeUuid);
        }

        HttpEntity<String> httpEntity = getModificationsUuidBody(modificationUuidList);
        try {
            restTemplate.put(getNetworkModificationServerURI(false)
                            + path.buildAndExpand(groupUuid).toUriString(), httpEntity);
        } catch (HttpStatusCodeException e) {
            //Ignore because modification group does not exist if no modifications
            if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw e;
            }
        }
    }

    public String duplicateModification(UUID groupUuid, List<UUID> modificationUuidList) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .queryParam("action", "DUPLICATE");

        HttpEntity<String> httpEntity = getModificationsUuidBody(modificationUuidList);
        return restTemplate.exchange(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString(), HttpMethod.PUT, httpEntity, String.class).getBody();
    }

    public void updateLineSplitWithVoltageLevel(String lineSplitWithVoltageLevelAttributes,
        ModificationType modificationType, UUID modificationUuid) {
        UriComponentsBuilder uriComponentsBuilder;
        uriComponentsBuilder = UriComponentsBuilder.fromPath(MODIFICATIONS_PATH + DELIMITER + modificationUuid + DELIMITER + ModificationType.getUriFromType(
            modificationType));
        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(lineSplitWithVoltageLevelAttributes, headers);

        try {
            restTemplate.exchange(getNetworkModificationServerURI(false) + path, HttpMethod.PUT, httpEntity, Void.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
        }
    }

    public List<EquipmentModificationInfos> splitLineWithVoltageLevel(UUID studyUuid, String lineSplitWithVoltageLevelAttributes,
        UUID groupUuid, ModificationType modificationType, String variantId, UUID reportUuid) {
        List<EquipmentModificationInfos> result;
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(
                buildPathFrom(networkUuid) + ModificationType.getUriFromType(modificationType))
            .queryParam(GROUP, groupUuid)
            .queryParam(REPORT_UUID, reportUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(lineSplitWithVoltageLevelAttributes, headers);

        try {
            result = restTemplate.exchange(getNetworkModificationServerURI(true) + path, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<List<EquipmentModificationInfos>>() { }).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleChangeError(e, ModificationType.getExceptionFromType(modificationType));
        }

        return result;
    }

    public void createModifications(UUID sourceGroupUuid, UUID groupUuid, UUID reportUuid) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(sourceGroupUuid);
        var path = UriComponentsBuilder.fromPath("groups")
                .queryParam("duplicateFrom", sourceGroupUuid)
                .queryParam("groupUuid", groupUuid)
                .queryParam("reportUuid", reportUuid)
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
