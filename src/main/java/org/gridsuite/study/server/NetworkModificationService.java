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
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
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

import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.NETWORK_MODIFICATION_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.DELETE_EQUIPMENT_FAILED;
import static org.gridsuite.study.server.StudyException.Type.ELEMENT_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.LINE_MODIFICATION_FAILED;
import static org.gridsuite.study.server.StudyException.Type.LOAD_CREATION_FAILED;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NetworkModificationService {

    private static final String DELIMITER = "/";

    private String networkModificationServerBaseUri;

    private final NetworkStoreService networkStoreService;

    private final WebClient webClient;

    @Autowired
    NetworkModificationService(@Value("${backing-services.network-modification.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri,
                               NetworkStoreService networkStoreService,
                               WebClient.Builder webClientBuilder) {
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
        this.networkStoreService = networkStoreService;
        this.webClient = webClientBuilder.build();
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
        var path = UriComponentsBuilder.fromPath("groups" + DELIMITER + "{groupUuid}")
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

    Mono<Void> deleteNetworkModifications(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        var path = UriComponentsBuilder.fromPath("groups" + DELIMITER + "{groupUuid}")
                .buildAndExpand(groupUuid)
                .toUriString();
        return webClient.delete()
                .uri(getNetworkModificationServerURI(false) + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, r -> Mono.empty()) // Ignore because modification group does not exist if no modifications
                .bodyToMono(Void.class);
    }

    Flux<EquipmentModificationInfos> changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID groupUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(switchId);
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var path = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "switches" + DELIMITER + "{switchId}")
                .queryParam("group", groupUuid)
                .queryParam("open", open)
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

    public Flux<EquipmentModificationInfos> applyGroovyScript(UUID studyUuid, String groovyScript, UUID groupUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(groovyScript);
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var path = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "groovy")
                .queryParam("group", groupUuid)
                .buildAndExpand()
                    .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI(true) + path)
                    .body(BodyInserters.fromValue(groovyScript))
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<EquipmentModificationInfos>() {
                    });
        });
    }

    Flux<EquipmentModificationInfos> applyLineChanges(UUID studyUuid, String lineId, String status, UUID groupUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(lineId);
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var path = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "lines" + DELIMITER + "{lineId}" + DELIMITER + "status")
                .queryParam("group", groupUuid)
                .buildAndExpand(lineId)
                    .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI(true) + path)
                    .body(BodyInserters.fromValue(status))
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                        handleChangeError(response, LINE_MODIFICATION_FAILED)
                    )
                    .bodyToFlux(new ParameterizedTypeReference<EquipmentModificationInfos>() {
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

    public Flux<EquipmentModificationInfos> createLoad(UUID studyUuid, String createLoadAttributes, UUID groupUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(createLoadAttributes);
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var path = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "createLoad")
                .queryParam("group", groupUuid)
                .buildAndExpand()
                .toUriString();

            return webClient.put()
                .uri(getNetworkModificationServerURI(true) + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(createLoadAttributes))
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                        handleChangeError(response, LOAD_CREATION_FAILED))
                .bodyToFlux(new ParameterizedTypeReference<EquipmentModificationInfos>() {
                });
        });
    }

    public Flux<EquipmentModificationInfos> deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID groupUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(equipmentType);
        Objects.requireNonNull(equipmentId);

        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var path = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "equipments" + DELIMITER + "type" + DELIMITER + "{equipmentType}" + DELIMITER + "id" + DELIMITER + "{equipmentId}")
                .queryParam("group", groupUuid)
                .buildAndExpand(equipmentType, equipmentId)
                .toUriString();

            return webClient.delete()
                .uri(getNetworkModificationServerURI(true) + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, response ->
                    handleChangeError(response, DELETE_EQUIPMENT_FAILED))
                .bodyToFlux(new ParameterizedTypeReference<EquipmentModificationInfos>() {
                });
        });
    }
}
