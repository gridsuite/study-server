/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Service
public class RootNetworkService {
    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    public RootNetworkService(RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository, RootNetworkRepository rootNetworkRepository,
                              RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    public UUID getNetworkUuid(UUID rootNetworkUuid) {
        return rootNetworkRepository.findById(rootNetworkUuid).map(RootNetworkEntity::getNetworkUuid).orElse(null);
    }

    @Transactional
    public void createRootNetwork(@NonNull StudyEntity studyEntity, @NonNull NetworkInfos networkInfos, @NonNull CaseInfos caseInfos, @NonNull UUID importReportUuid) {
        RootNetworkEntity rootNetworkEntity = rootNetworkRepository.save(RootNetworkEntity.builder()
                .networkUuid(networkInfos.getNetworkUuid())
                .networkId(networkInfos.getNetworkId())
                .caseFormat(caseInfos.getCaseFormat())
                .caseUuid(caseInfos.getCaseUuid())
                .caseName(caseInfos.getCaseName())
                .reportUuid(importReportUuid)
                .build()
        );

        studyEntity.addRootNetwork(rootNetworkEntity);

        rootNetworkNodeInfoService.createRootNetworkLinks(Objects.requireNonNull(studyEntity.getId()), rootNetworkEntity);
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
