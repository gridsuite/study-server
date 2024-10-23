/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Service
public class RootNetworkService {
    private final RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    private final RootNetworkRepository rootNetworkRepository;

    public RootNetworkService(RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository, RootNetworkRepository rootNetworkRepository) {
        this.rootNetworkNodeInfoRepository = rootNetworkNodeInfoRepository;
        this.rootNetworkRepository = rootNetworkRepository;
    }

    public UUID getNetworkUuid(UUID rootNetworkUuid) {
        return rootNetworkRepository.findById(rootNetworkUuid).map(RootNetworkEntity::getNetworkUuid).orElse(null);
    }

    // TODO move to RootNetworkNodeLinkService
    public Optional<RootNetworkNodeInfoEntity> getRootNetworkNodeInfo(UUID nodeUuid, UUID rootNetworkUuid) {
        return rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeUuid, rootNetworkUuid);
    }

    // TODO move to study service
    public List<UUID> getAllReportUuids(UUID studyUuid) {
        List<RootNetworkEntity> rootNetworkEntities = rootNetworkRepository.findAllWithInfosByStudyId(studyUuid);
        List<UUID> rootReportUuids = rootNetworkEntities.stream().map(RootNetworkEntity::getReportUuid).toList();
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkEntities.stream().flatMap(rootNetworkEntity -> rootNetworkEntity.getRootNetworkNodeInfos().stream()).toList();

        //study reports uuids is the concatenation of modification reports, computation reports and root reports uuids
        return rootNetworkNodeInfoEntities.stream().flatMap(rootNetworkNodeInfoEntity ->
                        Stream.of(
                                rootNetworkNodeInfoEntity.getModificationReports().values().stream(),
                                rootNetworkNodeInfoEntity.getComputationReports().values().stream(),
                                rootReportUuids.stream()))
                .reduce(Stream::concat)
                .orElse(Stream.empty()).toList();
    }
}
