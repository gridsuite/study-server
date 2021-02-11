/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repositories;

import org.gridsuite.study.server.entities.StudyCreationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@Repository
public interface StudyCreationRequestRepository extends JpaRepository<StudyCreationRequestEntity, Long> {

    List<StudyCreationRequestEntity> findAllByUserId(String userId);

    Optional<StudyCreationRequestEntity> findByUserIdAndStudyName(String userId, String studyName);

    @Transactional
    @Modifying
    void deleteByStudyNameAndUserId(String studyName, String userId);
}
