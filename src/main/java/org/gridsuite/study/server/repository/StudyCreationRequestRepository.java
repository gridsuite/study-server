/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.entities.BasicStudyEntity;
import org.gridsuite.study.server.entities.StudyCreationRequestEntity;
import org.gridsuite.study.server.entities.StudyEntity;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

public interface StudyCreationRequestRepository extends ReactiveCassandraRepository<StudyCreationRequestEntity, UUID> {

    @Query("SELECT * FROM studycreationrequest WHERE userId = :userId")
    Flux<StudyEntity> findStudyCreationRequestsByUserId(@Param("userId") String userId);

    Flux<StudyCreationRequestEntity> findAll();

    Mono<BasicStudyEntity> findByUserIdAndStudyName(String userId, String name);

    Mono<Void> deleteByStudyNameAndUserId(String studyName, String userId);

}
