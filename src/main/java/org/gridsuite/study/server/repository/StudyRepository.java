/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@Repository
public interface StudyRepository extends JpaRepository<StudyEntity, UUID> {

    @Query(value = "SELECT * FROM study u WHERE id in ?1 and (isPrivate='false' or userid=?2)", nativeQuery = true)
    List<StudyEntity> findAllByUuids(List<UUID> uuids, String userId);

    List<StudyEntity> findAllByUserId(String userId);

    List<StudyEntity> findByUserIdOrIsPrivate(@Param("userId") String userId, @Param("isPrivate") boolean isPrivate);
}
