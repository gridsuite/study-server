/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.model.VariantInfos;
import lombok.NonNull;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Service
public class RootNetworkService {
    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final NetworkService networkService;
    private final CaseService caseService;
    private final ReportService reportService;

    private final RootNetworkService self;
    private final RootNetworkCreationRequestRepository rootNetworkCreationRequestRepository;

    public RootNetworkService(RootNetworkRepository rootNetworkRepository,
                              RootNetworkCreationRequestRepository rootNetworkCreationRequestRepository,
                              RootNetworkNodeInfoService rootNetworkNodeInfoService,
                              NetworkService networkService,
                              CaseService caseService,
                              ReportService reportService,
                              @Lazy RootNetworkService self) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.networkService = networkService;
        this.caseService = caseService;
        this.reportService = reportService;
        this.self = self;
        this.rootNetworkCreationRequestRepository = rootNetworkCreationRequestRepository;
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

    /**
     * Called by consumer - will create root network only if rootNetworkCreatationRequest is still in database
     * @param studyEntity
     * @param rootNetworkInfos
     */
    @Transactional
    public void createRootNetworkFromRequest(StudyEntity studyEntity, @NonNull RootNetworkInfos rootNetworkInfos) {
        if (studyEntity == null) {
            throw new StudyException(StudyException.Type.STUDY_NOT_FOUND);
        }
        Optional<RootNetworkCreationRequestEntity> rootNetworkCreationRequestEntity = rootNetworkCreationRequestRepository.findById(rootNetworkInfos.getId());
        if (rootNetworkCreationRequestEntity.isPresent()) {
            self.createRootNetwork(studyEntity, rootNetworkInfos);
            rootNetworkCreationRequestRepository.delete(rootNetworkCreationRequestEntity.get());
            // TODO: send notification to frontend
        } else {
            // TODO: delete remote resources here
            throw new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND);
        }
    }

    @Transactional
    public RootNetworkEntity createRootNetwork(@NonNull StudyEntity studyEntity, @NonNull RootNetworkInfos rootNetworkInfos) {
        RootNetworkEntity rootNetworkEntity = rootNetworkRepository.save(rootNetworkInfos.toEntity());
        studyEntity.addRootNetwork(rootNetworkEntity);

        rootNetworkNodeInfoService.createRootNetworkLinks(Objects.requireNonNull(studyEntity.getId()), rootNetworkEntity);

        return rootNetworkEntity;
    }

    // TODO move to study service
    @Transactional
    public List<UUID> getStudyReportUuids(UUID studyUuid) {
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

    public Map<String, String> getImportParameters(UUID rootNetworkUuid) {
        return rootNetworkRepository.findWithImportParametersById(rootNetworkUuid).map(RootNetworkEntity::getImportParameters).orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
    }

    public List<UUID> getStudyCaseUuids(UUID studyUuid) {
        return getStudyRootNetworks(studyUuid).stream().map(RootNetworkEntity::getCaseUuid).toList();
    }

    public List<UUID> getStudyNetworkUuids(UUID studyUuid) {
        return getStudyRootNetworks(studyUuid).stream().map(RootNetworkEntity::getNetworkUuid).toList();
    }

    public List<RootNetworkEntity> getStudyRootNetworks(UUID studyUuid) {
        return rootNetworkRepository.findAllByStudyId(studyUuid);
    }

    @Transactional
    public void updateRootNetworkEntityNetwork(RootNetworkEntity rootNetworkEntity, NetworkInfos networkInfos) {
        if (networkInfos != null) {
            rootNetworkEntity.setNetworkId(networkInfos.getNetworkId());
            rootNetworkEntity.setNetworkUuid(networkInfos.getNetworkUuid());

            rootNetworkRepository.save(rootNetworkEntity);
        }
    }

    @Transactional
    public void duplicateStudyRootNetworks(StudyEntity newStudyEntity, UUID sourceStudyUuid) {
        List<RootNetworkEntity> rootNetworkEntities = rootNetworkRepository.findAllWithInfosByStudyId(sourceStudyUuid);
        rootNetworkEntities.forEach(rootNetworkEntityToDuplicate -> {
                List<VariantInfos> networkVariants = networkService.getNetworkVariants(rootNetworkEntityToDuplicate.getNetworkUuid());
                List<String> targetVariantIds = networkVariants.stream().map(VariantInfos::getId).limit(2).collect(Collectors.toList());
                Network clonedNetwork = networkService.cloneNetwork(rootNetworkEntityToDuplicate.getNetworkUuid(), targetVariantIds);
                UUID clonedNetworkUuid = networkService.getNetworkUuid(clonedNetwork);

                UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkEntityToDuplicate.getCaseUuid(), false);
                Map<String, String> newImportParameters = Map.copyOf(rootNetworkEntityToDuplicate.getImportParameters());

                UUID clonedRootNodeReportUuid = reportService.duplicateReport(rootNetworkEntityToDuplicate.getReportUuid());

                self.createRootNetwork(newStudyEntity,
                    RootNetworkInfos.builder()
                        .id(UUID.randomUUID())
                        .importParameters(newImportParameters)
                        .caseInfos(new CaseInfos(clonedCaseUuid, rootNetworkEntityToDuplicate.getCaseName(), rootNetworkEntityToDuplicate.getCaseFormat()))
                        .networkInfos(new NetworkInfos(clonedNetworkUuid, rootNetworkEntityToDuplicate.getNetworkId()))
                        .reportUuid(clonedRootNodeReportUuid)
                        .build()
                );
            }
        );
    }

    public RootNetworkCreationRequestEntity insertCreationRequest(UUID rootNetworkInCreationUuid, StudyEntity studyEntity, String userId) {
        return rootNetworkCreationRequestRepository.save(RootNetworkCreationRequestEntity.builder().id(rootNetworkInCreationUuid).studyUuid(studyEntity.getId()).userId(userId).build());
    }
}
