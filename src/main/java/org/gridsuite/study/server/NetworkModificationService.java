/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.modification.ElementaryModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
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
import static org.gridsuite.study.server.StudyException.Type.ELEMENT_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.LINE_MODIFICATION_FAILED;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
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

    private String getNetworkModificationServerURI() {
        return this.networkModificationServerBaseUri + DELIMITER + NETWORK_MODIFICATION_API_VERSION + DELIMITER + "networks" + DELIMITER;
    }

    private String buildPathFrom(UUID networkUuid) {
        return UriComponentsBuilder.fromPath("{networkUuid}" + DELIMITER)
                .buildAndExpand(networkUuid)
                .toUriString();
    }

    void insertEquipmentsIndexes(UUID networkUuid) {
        Objects.requireNonNull(networkUuid);

    }

    Mono<Void> deleteEquipmentsIndexes(UUID networkUuid) {
        Objects.requireNonNull(networkUuid);
        return Mono.empty();
    }

    public Flux<ModificationInfos> getModifications(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);
        return networkStoreService.getNetworkUuid(studyUuid)
                .flatMapMany(networkUuid -> webClient.get().uri(getNetworkModificationServerURI() + buildPathFrom(networkUuid) + "modifications").retrieve().bodyToFlux(new ParameterizedTypeReference<ModificationInfos>() {
                }));
    }

    public Mono<Void> deleteModifications(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);
        return networkStoreService.getNetworkUuid(studyUuid).flatMap(this::deleteNetworkModifications);
    }

    Mono<Void> deleteNetworkModifications(UUID networkUuid) {
        Objects.requireNonNull(networkUuid);
        return webClient.delete()
                .uri(getNetworkModificationServerURI() + buildPathFrom(networkUuid) + "modifications")
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, r -> Mono.empty()) // Ignore because modification group does not exist if no modifications
                .bodyToMono(Void.class);
    }

    Flux<ElementaryModificationInfos> changeSwitchState(UUID studyUuid, String switchId, boolean open) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(switchId);
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var path = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "switches" + DELIMITER + "{switchId}")
                    .queryParam("open", open)
                    .buildAndExpand(switchId)
                    .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI() + path)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new StudyException(ELEMENT_NOT_FOUND)))
                    .bodyToFlux(new ParameterizedTypeReference<ElementaryModificationInfos>() {
                    });
        });
    }

    public Flux<ElementaryModificationInfos> applyGroovyScript(UUID studyUuid, String groovyScript) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(groovyScript);
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> webClient.put()
                .uri(getNetworkModificationServerURI() + buildPathFrom(networkUuid) + "groovy/")
                .body(BodyInserters.fromValue(groovyScript))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ElementaryModificationInfos>() {
                }));
    }

    Flux<ElementaryModificationInfos> applyLineChanges(UUID studyUuid, String lineId, String status) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(lineId);
        return networkStoreService.getNetworkUuid(studyUuid).flatMapMany(networkUuid -> {
            var path = UriComponentsBuilder.fromPath(buildPathFrom(networkUuid) + "lines" + DELIMITER + "{lineId}" + DELIMITER + "status")
                    .buildAndExpand(lineId)
                    .toUriString();

            return webClient.put()
                    .uri(getNetworkModificationServerURI() + path)
                    .body(BodyInserters.fromValue(status))
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, this::handleChangeLineError)
                    .bodyToFlux(new ParameterizedTypeReference<ElementaryModificationInfos>() {
                    });
        });
    }

    private Mono<? extends Throwable> handleChangeLineError(ClientResponse clientResponse) {
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
            return Mono.error(new StudyException(LINE_MODIFICATION_FAILED, message));
        });
    }
}
