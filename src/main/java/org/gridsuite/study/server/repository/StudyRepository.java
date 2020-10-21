/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.gridsuite.study.server.dto.LoadFlowResult;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@Repository
public class StudyRepository {

    private final PublicAndPrivateStudyRepository publicAndPrivateStudyRepository;

    private final PrivateStudyRepository privateStudyRepository;

    private final PublicStudyRepository publicStudyRepository;

    public StudyRepository(PublicAndPrivateStudyRepository publicAndPrivateStudyRepository, PrivateStudyRepository privateStudyRepository, PublicStudyRepository publicStudyRepository) {
        this.publicAndPrivateStudyRepository = publicAndPrivateStudyRepository;
        this.privateStudyRepository = privateStudyRepository;
        this.publicStudyRepository = publicStudyRepository;
    }

    public Flux<StudyEntity> getStudies(String userId) {
        return Flux.concat(getPublicStudies(), getPrivateStudies(userId));
    }

    public Flux<StudyEntity> getPublicStudies() {
        return publicStudyRepository.findAll().cast(StudyEntity.class);
    }

    public Flux<StudyEntity> getPrivateStudies(String userId) {
        return privateStudyRepository.findAllByUserId(userId).cast(StudyEntity.class);
    }

    public Mono<StudyEntity> insertStudy(String studyName, String userId, boolean isPrivate, UUID networkUuid, String networkId,
                                         String description, String caseFormat, UUID caseUuid, boolean casePrivate,
                                         LoadFlowResult loadFlowResult, LoadFlowParametersEntity loadFlowParameters, UUID securityAnalysisUuid) {
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowResult);
        Objects.requireNonNull(loadFlowParameters);
        PublicAndPrivateStudyEntity publicAndPrivateStudyEntity = new PublicAndPrivateStudyEntity(userId, studyName, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, description, caseFormat, caseUuid,
                                                                                                  casePrivate, isPrivate, new LoadFlowResultEntity(loadFlowResult.getStatus()),
                                                                                                  loadFlowParameters, null);
        PublicStudyEntity publicStudyEntity = new PublicStudyEntity(userId, studyName, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, description, caseFormat, caseUuid,
                                                                    casePrivate, isPrivate, new LoadFlowResultEntity(loadFlowResult.getStatus()),
                                                                    loadFlowParameters, securityAnalysisUuid);
        PrivateStudyEntity privateStudyEntity = new PrivateStudyEntity(userId, studyName, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, description, caseFormat, caseUuid,
                                                                       casePrivate, isPrivate, new LoadFlowResultEntity(loadFlowResult.getStatus()),
                                                                       loadFlowParameters, securityAnalysisUuid);

        if (!isPrivate) {
            return Mono.zip(publicStudyRepository.insert(publicStudyEntity), publicAndPrivateStudyRepository.insert(publicAndPrivateStudyEntity))
                    .map(Tuple2::getT2);
        } else {
            return Mono.zip(privateStudyRepository.insert(privateStudyEntity), publicAndPrivateStudyRepository.insert(publicAndPrivateStudyEntity))
                    .map(Tuple2::getT2);
        }
    }

    public Mono<StudyEntity> findStudy(String userId, String studyName) {
        return publicAndPrivateStudyRepository.findByUserIdAndStudyName(userId, studyName).cast(StudyEntity.class);
    }

    public Mono<Void> deleteStudy(String userId, String studyName) {
        return Mono.zip(privateStudyRepository.deleteByStudyNameAndUserId(studyName, userId),
                        publicStudyRepository.deleteByStudyNameAndUserId(studyName, userId),
                        publicAndPrivateStudyRepository.deleteByStudyNameAndUserId(studyName, userId)).then();
    }

    public Mono<Void> updateLoadFlowState(String studyName, String userId, LoadFlowStatus lfStatus) {
        return Mono.zip(publicAndPrivateStudyRepository.updateLoadFlowState(studyName, userId, lfStatus),
                        privateStudyRepository.updateLoadFlowState(studyName, userId, lfStatus),
                        publicStudyRepository.updateLoadFlowState(studyName, userId, lfStatus))
                .then();
    }

    public Mono<Void> updateLoadFlowResult(String studyName, String userId, LoadFlowResult loadFlowResult) {
        LoadFlowResultEntity loadFlowResultEntity = new LoadFlowResultEntity(loadFlowResult.getStatus());
        return Mono.zip(privateStudyRepository.updateLoadFlowResult(studyName, userId, loadFlowResultEntity),
                        publicStudyRepository.updateLoadFlowResult(studyName, userId, loadFlowResultEntity),
                        publicAndPrivateStudyRepository.updateLoadFlowResult(studyName, userId, loadFlowResultEntity))
                .then();
    }

    public Mono<Void> updateSecurityAnalysisResultUuid(String studyName, String userId, UUID securityAnalysisResultUuid) {
        return Mono.zip(privateStudyRepository.updateSecurityAnalysisResultUuid(studyName, userId, securityAnalysisResultUuid),
                        publicStudyRepository.updateSecurityAnalysisResultUuid(studyName, userId, securityAnalysisResultUuid),
                        publicAndPrivateStudyRepository.updateSecurityAnalysisResultUuid(studyName, userId, securityAnalysisResultUuid))
                .then();
    }

    public Mono<Void> updateLoadFlowParameters(String studyName, String userId, LoadFlowParametersEntity parameters) {
        return Mono.zip(publicAndPrivateStudyRepository.updateLoadFlowParameters(studyName, userId, parameters),
                        publicStudyRepository.updateLoadFlowParameters(studyName, userId, parameters),
                        privateStudyRepository.updateLoadFlowParameters(studyName, userId, parameters)
        ).then();
    }

}
