/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.nodeactivity;

import org.gridsuite.study.server.nodeactivity.NodeActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
public interface NodeActivityRepository extends JpaRepository<NodeActivityEntity, UUID> {

    List<NodeActivityEntity> findAllByStudyId(UUID studyId);

    void deleteByNodeIdInAndRootNetworkId(List<UUID> nodeIds, UUID rootNetworkId);

    void deleteByNodeIdInAndRootNetworkIdIsNull(List<UUID> nodeIds);

}
