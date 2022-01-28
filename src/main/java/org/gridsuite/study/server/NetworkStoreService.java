/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.NETWORK_STORE_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.STUDY_NOT_FOUND;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class NetworkStoreService {

    private static final String DELIMITER = "/";

    private String networkStoreServerBaseUri;

    private final StudyRepository studyRepository;

    private final WebClient webClient;

    @Autowired
    NetworkStoreService(@Value("${network-store-server.base-uri:http://network-store-server/}") String networkStoreServerBaseUri,
                        StudyRepository studyRepository,
                        WebClient.Builder webClientBuilder) {
        this.networkStoreServerBaseUri = networkStoreServerBaseUri;
        this.studyRepository = studyRepository;
        this.webClient = webClientBuilder.build();
    }

    void setNetworkStoreServerBaseUri(String networkStoreServerBaseUri) {
        this.networkStoreServerBaseUri = networkStoreServerBaseUri + DELIMITER;
    }

    private String getNetworkStoreServerServerURI() {
        return this.networkStoreServerBaseUri + DELIMITER + NETWORK_STORE_API_VERSION + DELIMITER + "networks" + DELIMITER;
    }

    Mono<UUID> getNetworkUuid(UUID studyUuid) {
        return Mono.fromCallable(() -> doGetNetworkUuid(studyUuid).orElse(null))
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)));
    }

    Optional<UUID> doGetNetworkUuid(UUID studyUuid) {
        return studyRepository.findById(studyUuid).map(StudyEntity::getNetworkUuid);
    }

    // This function call directly the network store server without using the dedicated client because it's a blocking client.
    // If we'll have new needs to call the network store server, then we'll migrate the network store client to be nonblocking
    Mono<Void> deleteNetwork(UUID networkUuid) {
        var path = UriComponentsBuilder.fromPath("{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.delete()
                .uri(getNetworkStoreServerServerURI() + path)
                .retrieve()
                .bodyToMono(Void.class);
    }

    Mono<Void> deleteVariant(UUID networkUuid, String variantId) {
        // TODO : get variant infos from network store server, find variant num associated to variant id and delete this variant num ???
        return Mono.empty();
    }
}
