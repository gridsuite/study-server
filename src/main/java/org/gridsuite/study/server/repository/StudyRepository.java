package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.dto.LoadFlowResult;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.UUID;

@Repository
public class StudyRepository {

    private final AllStudyRepository allStudyRepository;

    private final PrivateStudyRepository privateStudyRepository;

    private final PublicStudyRepository publicStudyRepository;

    public StudyRepository(AllStudyRepository allStudyRepository, PrivateStudyRepository privateStudyRepository, PublicStudyRepository publicStudyRepository) {
        this.allStudyRepository = allStudyRepository;
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
                                         LoadFlowResult loadFlowResult) {
        AllStudyEntity allStudyEntity = new AllStudyEntity(userId, studyName, networkUuid, networkId, description, caseFormat, caseUuid,
                                                           casePrivate, isPrivate, new LoadFlowResultEntity(loadFlowResult.getStatus()));
        PublicStudyEntity publicStudyEntity = new PublicStudyEntity(userId, studyName, networkUuid, networkId, description, caseFormat, caseUuid,
                                                                    casePrivate, isPrivate, new LoadFlowResultEntity(loadFlowResult.getStatus()));
        PrivateStudyEntity privateStudyEntity = new PrivateStudyEntity(userId, studyName, networkUuid, networkId, description, caseFormat, caseUuid,
                                                                       casePrivate, isPrivate, new LoadFlowResultEntity(loadFlowResult.getStatus()));

        if (!isPrivate) {
            return Mono.zip(publicStudyRepository.insert(publicStudyEntity), allStudyRepository.insert(allStudyEntity))
                    .map(Tuple2::getT2);
        } else {
            return Mono.zip(privateStudyRepository.insert(privateStudyEntity), allStudyRepository.insert(allStudyEntity))
                    .map(Tuple2::getT2);
        }
    }

    public Mono<StudyEntity> findStudy(String userId, String studyName) {
        return allStudyRepository.findByUserIdAndStudyName(userId, studyName).cast(StudyEntity.class);
    }

    public Mono<Void> deleteStudy(String userId, String studyName) {
        return Mono.zip(privateStudyRepository.delete(userId, studyName),
                        publicStudyRepository.delete(studyName, userId),
                        allStudyRepository.deleteByStudyNameAndUserId(studyName, userId)).then();
    }

    public Mono<Void> updateLoadFlowState(String studyName, String userId, LoadFlowStatus lfStatus) {
        return Mono.zip(allStudyRepository.updateLoadFlowState(studyName, userId, lfStatus),
                        privateStudyRepository.updateLoadFlowState(studyName, userId, lfStatus),
                        publicStudyRepository.updateLoadFlowState(studyName, userId, lfStatus))
                .then();
    }

    public Mono<Void> updateLoadFlowResult(String studyName, String userId, LoadFlowResult loadFlowResult) {
        LoadFlowResultEntity loadFlowResultEntity = new LoadFlowResultEntity(loadFlowResult.getStatus());
        return Mono.zip(privateStudyRepository.updateLoadFlowResult(studyName, userId, loadFlowResultEntity),
                        publicStudyRepository.updateLoadFlowResult(studyName, userId, loadFlowResultEntity),
                        allStudyRepository.updateLoadFlowResult(studyName, userId, loadFlowResultEntity))
                .then();
    }
}
