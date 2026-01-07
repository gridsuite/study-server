/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.networkmodificationtree;

import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;

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

    @NativeQuery("SELECT ne.status FROM node_export ne WHERE ne.export_uuid = :exportUuid")
    Optional<String> findExportStatus(UUID exportUuid);

    @Modifying
    @NativeQuery("UPDATE node_export SET status = :status WHERE export_uuid = :exportUuid")
    void updateExportNetworkStatus(UUID exportUuid, String status);

    @NativeQuery("WITH RECURSIVE NodeHierarchy (id_node) AS ( " +
            "  SELECT n0.id_node" +
            "  FROM NODE n0 " +
            "  WHERE id_node = :nodeUuid " +
            "  UNION ALL " +
            "  SELECT n.id_node" +
            "  FROM NODE n " +
            "  INNER JOIN NodeHierarchy nh ON n.parent_node = nh.id_node " +
            ") " +
        "SELECT cast(nh.id_node AS VARCHAR) " +
        "FROM NodeHierarchy nh where nh.id_node != :nodeUuid ")
    List<UUID> findAllChildrenUuids(UUID nodeUuid);

    @NativeQuery("WITH RECURSIVE NodeHierarchy (id_node) AS ( " +
            "  SELECT n0.id_node" +
            "  FROM NODE n0 " +
            "  WHERE id_node = :nodeUuid " +
            "  UNION ALL " +
            "  SELECT n.id_node" +
            "  FROM NODE n " +
            "  INNER JOIN NodeHierarchy nh ON n.parent_node = nh.id_node " +
            ") " +
            "SELECT * FROM NODE n " +
            "WHERE n.id_node IN (SELECT nh.id_node FROM NodeHierarchy nh) AND n.id_node != :nodeUuid")
    List<NodeEntity> findAllChildren(UUID nodeUuid);

    List<NodeEntity> findAllByStudyIdAndStashedAndParentNodeIdNodeOrderByStashDateDesc(UUID id, boolean stashed, UUID parentNode);

    Optional<NodeEntity> findByStudyIdAndType(UUID id, NodeType type);
}
