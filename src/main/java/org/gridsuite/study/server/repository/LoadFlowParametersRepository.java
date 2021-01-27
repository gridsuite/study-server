/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.entities.LoadFlowParametersEntity;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;

import java.util.UUID;

/**
 */

public interface LoadFlowParametersRepository extends ReactiveCassandraRepository<LoadFlowParametersEntity, UUID> {
}
