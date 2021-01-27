/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repositories;

import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.entities.LoadFlowParametersEntity;
import org.gridsuite.study.server.entities.LoadFlowResultEntity;
import org.gridsuite.study.server.entities.StudyEntity;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@Repository
public interface StudyRepository extends JpaRepository<StudyEntity, Long> {

    List<StudyEntity> findAllByUserId(String userId);

    StudyEntity findByUserIdAndStudyName(String userId, String name);

    @Query("DELETE FROM study WHERE userId = :userId and studyname = :studyName")
    void deleteByStudyNameAndUserId(@Param("userId") String userId, @Param("studyName") String studyName);

    // TO BE  CHECKED
    @Query("SELECT FROM study WHERE userId = :userId")
    Flux<StudyEntity> findByUserId(@Param("userId") String userId);

    @Query("UPDATE study SET loadFlowParameters = :lfParameter  WHERE userId = :userId and studyname = :studyName")
    Mono<Void> updateLoadFlowParameters(String studyName, String userId, LoadFlowParametersEntity lfParameter);

    @Query("UPDATE study SET loadFlowStatus = :status WHERE userId = :userId and studyname = :studyName")
    Mono<Void> updateLoadFlowState(String studyName, String userId, LoadFlowStatus status);

    @Query("UPDATE study SET loadFlowResult = :loadFlowResult WHERE userId = :userId and studyname = :studyName")
    Mono<Void> updateLoadFlowResult(String studyName, String userId, LoadFlowResultEntity loadFlowResult);

    @Query("UPDATE study SET securityAnalysisResultUuid = :securityAnalysisResultUuid WHERE userId = :userId and studyname = :studyName")
    Mono<Void> updateSecurityAnalysisResultUuid(String studyName, String userId, UUID securityAnalysisResultUuid);
}
