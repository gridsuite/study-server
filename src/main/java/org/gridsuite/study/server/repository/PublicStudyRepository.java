/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.dto.LoadFlowResult;
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
public interface PublicStudyRepository extends ReactiveCassandraRepository<PublicStudy, Integer> {

    Flux<PublicStudy> findAll();

    @Query("DELETE FROM publicStudy WHERE userId = :userId and studyname = :studyName")
    Mono<Void> delete(@Param("studyName") String studyName, @Param("userId") String userId);

    @Query("UPDATE publicStudy SET loadFlowResult.status = :status WHERE userId = :userId and studyname = :studyName IF isPrivate != null")
    Mono<Void> updateLoadFlowState(String studyName, String userId, LoadFlowResult.LoadFlowStatus status);

    @Query("UPDATE publicStudy SET loadFlowResult = :result WHERE userId = :userId and studyname = :studyName IF isPrivate != null")
    Mono<Void> updateLoadFlowResult(String studyName, String userId, LoadFlowResult result);

}
