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
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public interface NodeRepository extends JpaRepository<NodeEntity, UUID> {
    long countByParentNodeIdNode(UUID id);

    List<NodeEntity> findAllByParentNodeIdNode(UUID id);

    List<NodeEntity> findAllByStudyId(UUID id);

    List<NodeEntity> findAllByStudyIdAndTypeAndStashed(UUID id, NodeType type, boolean stashed);

    @Query(nativeQuery = true, value =
        "WITH RECURSIVE NodeHierarchy (id_node, depth) AS ( " +
            "  SELECT n0.id_node, 0 AS depth" +
            "  FROM NODE n0 " +
            "  WHERE id_node = :nodeUuid " +
            "  UNION ALL " +
            "  SELECT n.id_node, nh.depth + 1 as depth" +
            "  FROM NODE n " +
            "  INNER JOIN NodeHierarchy nh ON n.parent_node = nh.id_node " +
            ") " +
            "SELECT nh.id_node::text " +
            "FROM NodeHierarchy nh " +
            "ORDER BY nh.depth DESC")
    List<String> findAllDescendants(UUID nodeUuid);

    List<NodeEntity> findAllByIdNodeIn(List<UUID> uuids);

    List<NodeEntity> findAllByStudyIdAndStashedAndParentNodeIdNodeOrderByStashDateDesc(UUID id, boolean stashed, UUID parentNode);

    Optional<NodeEntity> findByStudyIdAndType(UUID id, NodeType type);

    List<NodeEntity> findAllByStudyIdAndAliasNotNull(UUID id);
}
