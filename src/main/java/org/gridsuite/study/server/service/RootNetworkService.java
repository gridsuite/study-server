/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Service
public class RootNetworkService {
    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    public RootNetworkService(RootNetworkRepository rootNetworkRepository,
                              RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    public UUID getNetworkUuid(UUID rootNetworkUuid) {
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getNetworkUuid).orElse(null);
    }

    public UUID getRootReportUuid(UUID rootNetworkUuid) {
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getReportUuid).orElse(null);
    }

    public boolean exists(UUID rootNetworkUuid) {
        return rootNetworkRepository.existsById(rootNetworkUuid);
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
    @Transactional
    public List<UUID> getAllReportUuids(UUID studyUuid) {
        List<RootNetworkEntity> rootNetworkEntities = rootNetworkRepository.findAllWithInfosByStudyId(studyUuid);
        List<UUID> rootNodeReportUuids = rootNetworkEntities.stream().map(RootNetworkEntity::getReportUuid).toList();
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkEntities.stream().flatMap(rootNetworkEntity -> rootNetworkEntity.getRootNetworkNodeInfos().stream()).toList();

        //study reports uuids is the concatenation of modification reports, computation reports and root reports uuids

        List<UUID> networkModificationNodeReportUuids = rootNetworkNodeInfoEntities.stream().flatMap(rootNetworkNodeInfoEntity ->
                Stream.of(
                    rootNetworkNodeInfoEntity.getModificationReports().values().stream(),
                    rootNetworkNodeInfoEntity.getComputationReports().values().stream()))
            .reduce(Stream::concat)
            .orElse(Stream.empty()).toList();

        return Stream.concat(rootNodeReportUuids.stream(), networkModificationNodeReportUuids.stream()).toList();
    }

    public List<UUID> getAllNetworkUuids() {
        return rootNetworkRepository.findAll().stream().map(RootNetworkEntity::getNetworkUuid).toList();
    }

    public Optional<RootNetworkEntity> getRootNetwork(UUID rootNetworkUuid) {
        return rootNetworkRepository.findById(rootNetworkUuid);
    }

    public String getCaseName(UUID rootNetworkUuid) {
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getCaseName).orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
    }

    public List<UUID> getStudyCaseUuids(UUID studyUuid) {
        return rootNetworkRepository.findAllByStudyId(studyUuid).stream().map(RootNetworkEntity::getCaseUuid).toList();
    }

    public List<UUID> getStudyNetworkUuids(UUID studyUuid) {
        return rootNetworkRepository.findAllByStudyId(studyUuid).stream().map(RootNetworkEntity::getNetworkUuid).toList();
    }
}
