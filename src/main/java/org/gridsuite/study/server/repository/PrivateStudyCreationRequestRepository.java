/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

public interface PrivateStudyCreationRequestRepository extends ReactiveCassandraRepository<PrivateStudyCreationRequestEntity, String> {

    Flux<PrivateStudyCreationRequestEntity> findAllByUserId(String userId);

    Mono<PrivateStudyCreationRequestEntity> findByUserIdAndStudyName(String userId, String name);

    Mono<Void> deleteByStudyNameAndUserId(String studyName, String userId);

}
