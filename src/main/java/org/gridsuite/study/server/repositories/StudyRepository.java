/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repositories;

import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.entities.StudyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@Repository
public interface StudyRepository extends JpaRepository<StudyEntity, UUID> {

    List<StudyEntity> findAllByUserId(String userId);

    Optional<StudyEntity> findByUserIdAndStudyName(String userId, String name);

    List<StudyEntity> findAllByUserIdAndIsPrivate(String userId, boolean isPrivate);

    List<StudyEntity> findAllByIsPrivate(boolean isPrivate);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE study SET loadFlowStatus = :#{#loadFlowStatus.name()} WHERE userId = :userId and studyName = :studyName", nativeQuery = true)
    int updateLoadFlowStatus(@Param("studyName") String studyName, @Param("userId") String userId, @Param("loadFlowStatus") LoadFlowStatus loadFlowStatus);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE study SET securityAnalysisResultUuid = :securityAnalysisResultUuid WHERE userId = :userId and studyName = :studyName", nativeQuery = true)
    void updateSecurityAnalysisResultUuid(@Param("studyName") String studyName, @Param("userId") String userId, @Param("securityAnalysisResultUuid") UUID securityAnalysisResultUuid);
}
