/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.logging.Level;
import org.gridsuite.study.server.StudyService;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Repository
public class StudyCreationRequestRepository {

    private final PrivateStudyCreationRequestRepository privateRepository;
    private final PublicStudyCreationRequestRepository publicRepository;

    public StudyCreationRequestRepository(PublicStudyCreationRequestRepository publicRepository, PrivateStudyCreationRequestRepository privateRepository) {
        this.privateRepository = privateRepository;
        this.publicRepository = publicRepository;
    }

    public Flux<BasicStudyEntity> getStudyCreationRequests(String userId) {
        return Flux.concat(getPublicStudyCreationRequests(), getPrivateStudyCreationRequests(userId));
    }

    public Flux<BasicStudyEntity> getPublicStudyCreationRequests() {
        return publicRepository.findAll().cast(BasicStudyEntity.class);
    }

    public Flux<BasicStudyEntity> getPrivateStudyCreationRequests(String userId) {
        return privateRepository.findAllByUserId(userId).cast(BasicStudyEntity.class);
    }

    public Mono<Void> insertStudyCreationRequest(String studyName, String userId, boolean isPrivate) {
        if (isPrivate) {
            return privateRepository.insert(new PrivateStudyCreationRequestEntity(userId, studyName, LocalDateTime.now(ZoneOffset.UTC))).then()
                    .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        } else {
            return publicRepository.insert(new PublicStudyCreationRequestEntity(userId, studyName, LocalDateTime.now(ZoneOffset.UTC))).then()
                    .log(StudyService.ROOT_CATEGORY_REACTOR, Level.FINE);
        }
    }

    public Mono<Void> deleteStudyCreationRequest(String studyName, String userId) {
        return Mono.when(privateRepository.deleteByStudyNameAndUserId(studyName, userId), publicRepository.deleteByStudyNameAndUserId(studyName, userId));
    }

    public Mono<BasicStudyEntity> findStudy(String userId, String studyName) {
        return publicRepository.findByUserIdAndStudyName(userId, studyName).cast(BasicStudyEntity.class)
                .switchIfEmpty(privateRepository.findByUserIdAndStudyName(userId, studyName));
    }

}
