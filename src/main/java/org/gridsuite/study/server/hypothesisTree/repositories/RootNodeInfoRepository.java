/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.hypothesisTree.repositories;

import org.gridsuite.study.server.hypothesisTree.entities.RootNodeInfoEntity;

import java.util.Optional;
import java.util.UUID;

public interface RootNodeInfoRepository extends NodeInfoRepository<RootNodeInfoEntity> {
    Optional<RootNodeInfoEntity> findFirstByStudyId(UUID studyId);
}
