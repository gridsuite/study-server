/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public interface NodeRepository extends JpaRepository<NodeEntity, UUID> {
    List<NodeEntity> findAllByParentNodeIdNode(UUID id);

    List<NodeEntity> findAllByStudyId(UUID id);

    List<NodeEntity> findAllByStudyIdAndStashedAndParentNodeIdNodeOrderByStashDateDesc(UUID id, boolean stashed, UUID parentNode);

    Optional<NodeEntity> findByStudyIdAndType(UUID id, NodeType type);
}
