/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.util.UUID;

import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.entities.LoadFlowParametersEntity;
import org.gridsuite.study.server.entities.LoadFlowResultEntity;
import org.gridsuite.study.server.entities.PublicStudyEntity;
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
public interface PublicStudyRepository extends ReactiveCassandraRepository<PublicStudyEntity, Integer> {

    Flux<PublicStudyEntity> findAll();

    @Query("DELETE FROM publicStudy WHERE userId = :userId and studyname = :studyName")
    Mono<Void> deleteByStudyNameAndUserId(@Param("studyName") String studyName, @Param("userId") String userId);

    @Query("UPDATE publicStudy SET loadFlowStatus = :status WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Object> updateLoadFlowState(String studyName, String userId, LoadFlowStatus status);

    @Query("UPDATE publicStudy SET loadFlowResult = :result WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Boolean> updateLoadFlowResult(String studyName, String userId, LoadFlowResultEntity result);

    @Query("UPDATE publicStudy SET loadFlowParameters = :lfParameter  WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Boolean> updateLoadFlowParameters(String studyName, String userId, LoadFlowParametersEntity lfParameter);

    @Query("UPDATE publicStudy SET securityAnalysisResultUuid = :securityAnalysisResultUuid WHERE userId = :userId and studyname = :studyName IF EXISTS")
    Mono<Boolean> updateSecurityAnalysisResultUuid(String studyName, String userId, UUID securityAnalysisResultUuid);
}
