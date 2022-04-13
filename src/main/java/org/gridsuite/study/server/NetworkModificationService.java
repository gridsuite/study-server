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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private static final String EQUIPMENT_ID = "equipmentId";

    private String networkModificationServerBaseUri;

    private final NetworkService networkStoreService;

    private final WebClient webClient;

    private final ObjectMapper objectMapper;

    @Autowired
    NetworkModificationService(@Value("${backing-services.network-modification.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri,
                               NetworkService networkStoreService,
                               WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper) {
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
        this.networkStoreService = networkStoreService;
        this.webClient = webClientBuilder.build();
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

    public Flux<ModificationInfos> getModifications(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .buildAndExpand(groupUuid)
            .toUriString();
        return webClient.get().uri(getNetworkModificationServerURI(false) + path)
            .retrieve()
            .bodyToFlux(new ParameterizedTypeReference<ModificationInfos>() { });
    }

    public Mono<Void> deleteModifications(UUID groupUUid) {
        Objects.requireNonNull(groupUUid);
        return deleteNetworkModifications(groupUUid);
    }

    private Mono<Void> deleteNetworkModifications(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH)
            .buildAndExpand(groupUuid)
            .toUriString();
        return webClient.delete()
            .uri(getNetworkModificationServerURI(false) + path)
            .retrieve()
            .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, r -> Mono.empty()) // Ignore because modification group does not exist if no modifications
            .bodyToMono(Void.class);
    }

    Flux<EquipmentModificationInfos> changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID groupUuid, String variantId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(switchId);

        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "switches" + DELIMITER + "{switchId}")
                .queryParam(GROUP, groupUuid)
                .queryParam("open", open);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                .buildAndExpand(switchId)
                .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI(true) + path)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new StudyException(ELEMENT_NOT_FOUND)))
                    .bodyToFlux(new ParameterizedTypeReference<EquipmentModificationInfos>() {
                    });
        });
    }

    public Flux<ModificationInfos> applyGroovyScript(UUID studyUuid, String groovyScript, UUID groupUuid, String variantId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(groovyScript);

        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "groovy")
                .queryParam(GROUP, groupUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI(true) + path)
                    .body(BodyInserters.fromValue(groovyScript))
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ModificationInfos>() {
                    });
        });
    }

    Flux<ModificationInfos> changeLineStatus(UUID studyUuid, String lineId, String status, UUID groupUuid, String variantId) {
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "lines" + DELIMITER + "{lineId}" + DELIMITER + "status")
                .queryParam(GROUP, groupUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                .buildAndExpand(lineId)
                .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI(true) + path)
                    .body(BodyInserters.fromValue(status))
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                        handleChangeError(response, LINE_MODIFICATION_FAILED)
                    )
                    .bodyToFlux(new ParameterizedTypeReference<ModificationInfos>() {
                    });
        });
    }

    private Mono<? extends Throwable> handleChangeError(ClientResponse clientResponse, StudyException.Type type) {
        return clientResponse.bodyToMono(String.class).flatMap(body -> {
            String message = null;
            try {
                JsonNode node = new ObjectMapper().readTree(body).path("message");
                if (!node.isMissingNode()) {
                    message = node.asText();
                }
            } catch (JsonProcessingException e) {
                if (!body.isEmpty()) {
                    message = body;
                }
            }
            return Mono.error(new StudyException(type, message));
        });
    }

    public Flux<EquipmentModificationInfos> createEquipment(UUID studyUuid, String createEquipmentAttributes, UUID groupUuid,
                                                            ModificationType modificationType, String variantId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(createEquipmentAttributes);

        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + ModificationType.getUriFromType(modificationType))
                    .queryParam(GROUP, groupUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();
            return webClient.post()
                .uri(getNetworkModificationServerURI(true) + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(createEquipmentAttributes))
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                        handleChangeError(response, ModificationType.getExceptionFromType(modificationType)))
                .bodyToFlux(new ParameterizedTypeReference<EquipmentModificationInfos>() {
                });
        });
    }

    public Flux<EquipmentModificationInfos> modifyEquipment(UUID studyUuid, String modifyEquipmentAttributes,
                                                            UUID groupUuid, ModificationType modificationType, String variantId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(modifyEquipmentAttributes);

        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + ModificationType.getUriFromType(modificationType))
                    .queryParam(GROUP, groupUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                    .buildAndExpand()
                    .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI(true) + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(modifyEquipmentAttributes))
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                            handleChangeError(response, ModificationType.getExceptionFromType(modificationType)))
                    .bodyToFlux(new ParameterizedTypeReference<EquipmentModificationInfos>() {
                    });
        });
    }

    public Mono<Void> updateEquipmentCreation(String createEquipmentAttributes, ModificationType modificationType, UUID modificationUuid) {
        Objects.requireNonNull(createEquipmentAttributes);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath("modifications" + DELIMITER + modificationUuid + DELIMITER + ModificationType.getUriFromType(modificationType) + "-creation");
        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();

        return webClient.put()
                .uri(getNetworkModificationServerURI(false) + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(createEquipmentAttributes))
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                        handleChangeError(response, ModificationType.getExceptionFromType(modificationType)))
                .bodyToMono(Void.class);
    }

    public Flux<EquipmentDeletionInfos> deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID groupUuid, String variantId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(equipmentType);
        Objects.requireNonNull(equipmentId);

        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "equipments" + DELIMITER + "type" + DELIMITER + "{equipmentType}" + DELIMITER + "id" + DELIMITER + "{equipmentId}")
                .queryParam(GROUP, groupUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                .buildAndExpand(equipmentType, equipmentId)
                .toUriString();

            return webClient.delete()
                .uri(getNetworkModificationServerURI(true) + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                    handleChangeError(response, DELETE_EQUIPMENT_FAILED))
                .bodyToFlux(new ParameterizedTypeReference<EquipmentDeletionInfos>() {
                });
        });
    }

    Mono<Void> buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull BuildInfos buildInfos) {
        return networkStoreService.getNetworkUuid(studyUuid).flatMap(networkUuid -> {
            String receiver;
            try {
                receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)), StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                return Mono.error(new UncheckedIOException(e));
            }

            var uriComponentsBuilder = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "build");
            var path = uriComponentsBuilder
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .build()
                .toUriString();

            return webClient.post()
                .uri(getNetworkModificationServerURI(true) + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(buildInfos))
                .retrieve()
                .bodyToMono(Void.class);
        });
    }

    public Mono<Void> stopBuild(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var path = UriComponentsBuilder.fromPath("build/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .build()
            .toUriString();

        return webClient.put()
            .uri(getNetworkModificationServerURI(false) + path)
            .retrieve()
            .bodyToMono(Void.class);
    }

    public Mono<Void> deleteModifications(UUID groupUuid, List<UUID> modificationsUuids) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(modificationsUuids);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH + DELIMITER + "modifications");
        path.queryParam("modificationsUuids", modificationsUuids);
        return webClient.delete()
            .uri(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid).toUriString())
            .retrieve()
            .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, r -> Mono.empty()) // Ignore because modification group does not exist if no modifications
            .bodyToMono(Void.class);
    }

    public Mono<Void> reorderModification(UUID groupUuid, UUID modificationUuid, UUID beforeUuid) {
        Objects.requireNonNull(groupUuid);
        Objects.requireNonNull(modificationUuid);
        var path = UriComponentsBuilder.fromPath(GROUP_PATH
                + DELIMITER + "modifications" + DELIMITER + "move")
            .queryParam("modificationsToMove", modificationUuid);
        if (beforeUuid != null) {
            path.queryParam("before", beforeUuid);
        }

        return webClient.put()
            .uri(getNetworkModificationServerURI(false) + path.buildAndExpand(groupUuid, modificationUuid).toUriString())
            .retrieve()
            .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, r -> Mono.empty()) // Ignore because modification group does not exist if no modifications
            .bodyToMono(Void.class);

    }
}
