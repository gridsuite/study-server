/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.rootnetwork;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RootNetworkRequestRepository extends JpaRepository<RootNetworkRequestEntity, UUID> {
    List<RootNetworkRequestEntity> findAllByStudyUuid(UUID studyUuid);

    int countAllByStudyUuid(UUID studyUuid);

    Optional<RootNetworkRequestRepository> findByNameAndStudyUuid(String name, UUID studyUuid);

    Optional<RootNetworkRequestRepository> findByTagAndStudyUuid(String tag, UUID studyUuid);
}
