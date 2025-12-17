/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.rootnetwork;

import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
public interface RootNetworkNodeInfoRepository extends JpaRepository<RootNetworkNodeInfoEntity, UUID> {
    List<RootNetworkNodeInfoEntity> findAllByLoadFlowResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByDynamicSimulationResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByDynamicSecurityAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllBySecurityAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllBySensitivityAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByShortCircuitAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByOneBusShortCircuitAnalysisResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByVoltageInitResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByStateEstimationResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByPccMinResultUuidNotNull();

    List<RootNetworkNodeInfoEntity> findAllByNodeExportNetworkExportUuid(UUID exportUuid);

    List<RootNetworkNodeInfoEntity> findAllByNodeInfoId(UUID nodeInfoId);

    @EntityGraph(attributePaths = {"rootNetwork"}, type = EntityGraph.EntityGraphType.LOAD)
    List<RootNetworkNodeInfoEntity> findAllWithRootNetworkByNodeInfoId(UUID nodeInfoId);

    Optional<RootNetworkNodeInfoEntity> findByNodeInfoIdAndRootNetworkId(UUID nodeInfoId, UUID rootNetworkUuid);

    @EntityGraph(attributePaths = {"modificationsUuidsToExclude"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<RootNetworkNodeInfoEntity> findWithModificationsToExcludeByNodeInfoIdAndRootNetworkId(UUID nodeInfoId, UUID rootNetworkUuid);

    List<RootNetworkNodeInfoEntity> findAllByRootNetworkStudyId(UUID studyUuid);

    List<RootNetworkNodeInfoEntity> findAllByRootNetworkStudyIdAndLoadFlowResultUuidNotNull(UUID studyUuid);

    List<RootNetworkNodeInfoEntity> findAllByRootNetworkStudyIdAndNodeInfoNodeTypeAndLoadFlowResultUuidNotNull(UUID studyUuid, NetworkModificationNodeType nodeType);

    @Query("SELECT count(rnni) > 0 from RootNetworkNodeInfoEntity rnni LEFT JOIN rnni.rootNetwork rn LEFT JOIN rn.study s " +
        "where s.id = :studyUuid and (rnni.nodeBuildStatus.globalBuildStatus = :buildStatus or rnni.nodeBuildStatus.localBuildStatus = :buildStatus)")
    boolean existsByStudyUuidAndBuildStatus(UUID studyUuid, BuildStatus buildStatus);

    List<RootNetworkNodeInfoEntity> getAllByRootNetworkIdAndNodeInfoIdIn(UUID rootNetworkUuid, List<UUID> nodesUuids);

    @Query(value = "SELECT count(rnni) > 0 FROM RootNetworkNodeInfoEntity rnni WHERE rnni.rootNetwork.id = :rootNetworkUuid AND rnni.nodeInfo.idNode IN :nodesUuids AND rnni.blockedNode = true ")
    boolean existsByNodeUuidsAndBlockedNode(UUID rootNetworkUuid, List<UUID> nodesUuids);

    /**
     * Finds report UUIDs that are still referenced by other RootNetworkNodeInfo entities.
     * <p>
     * This is a critical safety query that prevents cascade deletion of shared reports.
     * It performs a bulk check in a single database round trip for efficiency.
     * <p>
     * <strong>Use case:</strong> When invalidating a node, we need to determine which
     * reports can be safely deleted. This query identifies reports that are still needed
     * by other nodes.
     *
     * @param reportUuids Set of report UUIDs to check for references
     * @param excludedEntityId The RootNetworkNodeInfo ID to exclude from the check
     *                         (typically the node being deleted/invalidated)
     * @return Set of report UUIDs that are still referenced by other entities
     */
    @Query("""
        SELECT DISTINCT VALUE(rnni.modificationReports)
        FROM RootNetworkNodeInfoEntity rnni
        JOIN rnni.modificationReports mr
        WHERE VALUE(rnni.modificationReports) IN :reportUuids
        AND rnni.id != :excludedEntityId
        """)
    Set<UUID> findReferencedReportUuidsExcludingEntity(Set<UUID> reportUuids, UUID excludedEntityId);

    /**
     * Finds report UUIDs for a specific node by searching across all RootNetworkNodeInfo
     * entities in the given root network.
     * <p>
     * This method searches the modificationReports maps of all nodes in the root network
     * to find if any node has already generated a report for the target node.
     * <p>
     * <strong>Important:</strong> Based on our code logic, there should be at most one report UUID
     * assigned to any given node across the entire root network. The returned Set should contain
     * either 0 or 1 elements. If the Set contains multiple values, this indicates a data
     * inconsistency that should be investigated.
     *
     * @param targetNodeUuid the node UUID to find a report for (the map key)
     * @param rootNetworkUuid the root network to search within
     * @return a Set containing the report UUID(s) found in the modificationReports maps,
     *         expected to be empty or contain exactly one element under normal conditions
     */
    @Query("""
        SELECT DISTINCT VALUE(rnni.modificationReports)
        FROM RootNetworkNodeInfoEntity rnni
        WHERE rnni.rootNetwork.id = :rootNetworkUuid
        AND KEY(rnni.modificationReports) = :targetNodeUuid
        """)
    Set<UUID> findReportUuidsForNodeInRootNetwork(UUID targetNodeUuid, UUID rootNetworkUuid);
}
