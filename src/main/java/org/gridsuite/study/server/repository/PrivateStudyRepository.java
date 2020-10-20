/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.util.UUID;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Repository
public interface PrivateStudyRepository extends ReactiveCassandraRepository<PrivateStudyEntity, Integer> {

    Flux<PrivateStudyEntity> findAllByUserId(String userId);

    @Query("DELETE FROM privateStudy WHERE userId = :userId and studyname = :studyName")
    Mono<Void> deleteByStudyNameAndUserId(@Param("studyName") String studyName, @Param("userId") String userId);

    @Query("UPDATE privateStudy SET loadFlowStatus = :status WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Boolean> updateLoadFlowState(String studyName, String userId, LoadFlowStatusEntity status);

    @Query("UPDATE privateStudy SET loadFlowResult = :result WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Boolean> updateLoadFlowResult(String studyName, String userId, LoadFlowResultEntity result);

    @Query("UPDATE privateStudy SET loadFlowParameters = :lfParameter  WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Boolean> updateLoadFlowParameters(String studyName, String userId, LoadFlowParametersEntity lfParameter);

    @Query("UPDATE privateStudy SET securityAnalysisResultUuid = :securityAnalysisResultUuid WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Boolean> updateSecurityAnalysisResultUuid(String studyName, String userId, UUID securityAnalysisResultUuid);
}
