/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.model.VariantInfos;
import lombok.NonNull;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Service
public class RootNetworkService {
    private static final int MAXIMUM_ROOT_NETWORK_BY_STUDY = 3;

    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final NetworkService networkService;
    private final CaseService caseService;
    private final ReportService reportService;

    private final RootNetworkCreationRequestRepository rootNetworkCreationRequestRepository;
    private final StudyServerExecutionService studyServerExecutionService;
    private final EquipmentInfosService equipmentInfosService;
    private final NetworkStoreService networkStoreService;

    public RootNetworkService(RootNetworkRepository rootNetworkRepository,
                              RootNetworkCreationRequestRepository rootNetworkCreationRequestRepository,
                              RootNetworkNodeInfoService rootNetworkNodeInfoService,
                              NetworkService networkService,
                              CaseService caseService,
                              StudyServerExecutionService studyServerExecutionService,
                              ReportService reportService,
                              EquipmentInfosService equipmentInfosService,
                              NetworkStoreService networkStoreService) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.networkService = networkService;
        this.caseService = caseService;
        this.reportService = reportService;
        this.rootNetworkCreationRequestRepository = rootNetworkCreationRequestRepository;
        this.studyServerExecutionService = studyServerExecutionService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkStoreService = networkStoreService;
    }

    public UUID getNetworkUuid(UUID rootNetworkUuid) {
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
    }

    public UUID getRootReportUuid(UUID rootNetworkUuid) {
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getReportUuid).orElse(null);
    }

    public boolean exists(UUID rootNetworkUuid) {
        return rootNetworkRepository.existsById(rootNetworkUuid);
    }

    public void updateNetwork(@NonNull RootNetworkEntity rootNetworkEntity, @NonNull NetworkInfos networkInfos) {
        updateNetworkInfos(rootNetworkEntity, networkInfos);
    }

    public void updateNetwork(@NonNull RootNetworkInfos rootNetworkInfos) {
        RootNetworkEntity rootNetworkEntity = getRootNetwork(rootNetworkInfos.getId()).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
        UUID oldCaseUuid = rootNetworkEntity.getCaseUuid();
        updateNetwork(rootNetworkEntity, rootNetworkInfos);

        //delete old case
        caseService.deleteCase(oldCaseUuid);
    }

    private void updateNetwork(RootNetworkEntity rootNetworkEntity, RootNetworkInfos rootNetworkInfos) {
        updateCaseInfos(rootNetworkEntity, rootNetworkInfos.getCaseInfos());
        updateNetworkInfos(rootNetworkEntity, rootNetworkInfos.getNetworkInfos());

        rootNetworkEntity.setImportParameters(rootNetworkInfos.getImportParameters());
        rootNetworkEntity.setReportUuid(rootNetworkInfos.getReportUuid());
    }

    private void updateCaseInfos(@NonNull RootNetworkEntity rootNetworkEntity, @NonNull CaseInfos caseInfos) {
        rootNetworkEntity.setCaseUuid(caseInfos.getCaseUuid());
        rootNetworkEntity.setCaseFormat(caseInfos.getCaseFormat());
        rootNetworkEntity.setCaseName(caseInfos.getCaseName());
    }

    private void updateNetworkInfos(@NonNull RootNetworkEntity rootNetworkEntity, @NonNull NetworkInfos networkInfos) {
        rootNetworkEntity.setNetworkId(networkInfos.getNetworkId());
        rootNetworkEntity.setNetworkUuid(networkInfos.getNetworkUuid());
    }

    public RootNetworkEntity createRootNetwork(@NonNull StudyEntity studyEntity, @NonNull RootNetworkInfos rootNetworkInfos) {
        RootNetworkEntity rootNetworkEntity = rootNetworkRepository.save(rootNetworkInfos.toEntity());
        studyEntity.addRootNetwork(rootNetworkEntity);

        rootNetworkNodeInfoService.createRootNetworkLinks(Objects.requireNonNull(studyEntity.getId()), rootNetworkEntity);

        return rootNetworkEntity;
    }

    public List<UUID> getAllNetworkUuids() {
        return rootNetworkRepository.findAll().stream().map(RootNetworkEntity::getNetworkUuid).toList();
    }

    public Optional<RootNetworkEntity> getRootNetwork(UUID rootNetworkUuid) {
        return rootNetworkRepository.findById(rootNetworkUuid);
    }

    public String getCaseName(UUID rootNetworkUuid) {
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getCaseName).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
    }

    public Map<String, String> getImportParameters(UUID rootNetworkUuid) {
        return rootNetworkRepository.findWithImportParametersById(rootNetworkUuid).map(RootNetworkEntity::getImportParameters).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
    }

    public List<RootNetworkInfos> getRootNetworkInfosWithLinksInfos(UUID studyUuid) {
        return rootNetworkRepository.findAllWithInfosByStudyId(studyUuid).stream().map(RootNetworkEntity::toDto).toList();
    }

    @Transactional
    public void duplicateStudyRootNetworks(StudyEntity newStudyEntity, StudyEntity sourceStudyEntity) {
        List<RootNetworkEntity> rootNetworkEntities = sourceStudyEntity.getRootNetworks();
        rootNetworkEntities.forEach(rootNetworkEntityToDuplicate -> {
                List<VariantInfos> networkVariants = networkService.getNetworkVariants(rootNetworkEntityToDuplicate.getNetworkUuid());
                // Clone only the initial variant
                List<String> targetVariantIds = networkVariants.stream().findFirst().map(VariantInfos::getId).stream().toList();
                Network clonedNetwork = networkService.cloneNetwork(rootNetworkEntityToDuplicate.getNetworkUuid(), targetVariantIds);
                UUID clonedNetworkUuid = networkService.getNetworkUuid(clonedNetwork);

                UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkEntityToDuplicate.getCaseUuid(), false);
                Map<String, String> newImportParameters = Map.copyOf(rootNetworkEntityToDuplicate.getImportParameters());

                UUID clonedRootNodeReportUuid = reportService.duplicateReport(rootNetworkEntityToDuplicate.getReportUuid());

                createRootNetwork(newStudyEntity,
                    RootNetworkInfos.builder()
                        .id(UUID.randomUUID())
                        .name(rootNetworkEntityToDuplicate.getName())
                        .importParameters(newImportParameters)
                        .caseInfos(new CaseInfos(clonedCaseUuid, rootNetworkEntityToDuplicate.getCaseName(), rootNetworkEntityToDuplicate.getCaseFormat()))
                        .networkInfos(new NetworkInfos(clonedNetworkUuid, rootNetworkEntityToDuplicate.getNetworkId()))
                        .reportUuid(clonedRootNodeReportUuid)
                        .build()
                );
            }
        );
    }

    public RootNetworkCreationRequestEntity insertCreationRequest(UUID rootNetworkInCreationUuid, StudyEntity studyEntity, String rootNetworkName, String userId) {
        return rootNetworkCreationRequestRepository.save(RootNetworkCreationRequestEntity.builder().id(rootNetworkInCreationUuid).name(rootNetworkName).studyUuid(studyEntity.getId()).userId(userId).build());
    }

    public void assertIsRootNetworkInStudy(UUID studyUuid, UUID rootNetworkUuid) {
        if (!rootNetworkRepository.existsByIdAndStudyId(rootNetworkUuid, studyUuid)) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
    }

    /**
     * Delete entities from uuids
     * will also delete all remote resources linked to the entity
     * @param rootNetworkUuids
     */
    public void deleteRootNetworks(StudyEntity studyEntity, Stream<UUID> rootNetworkUuids) {
        List<RootNetworkInfos> rootNetworksInfos = rootNetworkUuids.map(rootNetworkRepository::findWithRootNetworkNodeInfosById)
            .map(o -> o.orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)))
            .map(RootNetworkEntity::toDto)
            .toList();

        deleteRootNetworks(studyEntity, rootNetworksInfos);
    }

    public void deleteRootNetworks(StudyEntity studyEntity, List<RootNetworkInfos> rootNetworksInfos) {
        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
            getDeleteRootNetworkInfosFutures(rootNetworksInfos).toArray(CompletableFuture[]::new)
        );

        try {
            executeInParallel.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StudyException(DELETE_ROOT_NETWORK_FAILED, e.getMessage());
        } catch (Exception e) {
            throw new StudyException(DELETE_ROOT_NETWORK_FAILED, e.getMessage());
        }

        studyEntity.deleteRootNetworks(rootNetworksInfos.stream().map(RootNetworkInfos::getId).collect(Collectors.toSet()));
    }

    public Stream<CompletableFuture<Void>> getDeleteRootNetworkInfosFutures(List<RootNetworkInfos> rootNetworkInfos) {
        return Stream
            .concat(
                // delete remote data ids set in root network
                Stream.of(
                    studyServerExecutionService.runAsync(() -> reportService.deleteReports(rootNetworkInfos.stream().map(RootNetworkInfos::getReportUuid).toList())),
                    studyServerExecutionService.runAsync(() -> rootNetworkInfos.stream().map(rni -> rni.getNetworkInfos().getNetworkUuid()).filter(Objects::nonNull).forEach(equipmentInfosService::deleteEquipmentIndexes)),
                    studyServerExecutionService.runAsync(() -> rootNetworkInfos.stream().map(rni -> rni.getNetworkInfos().getNetworkUuid()).filter(Objects::nonNull).forEach(networkStoreService::deleteNetwork)),
                    studyServerExecutionService.runAsync(() -> rootNetworkInfos.stream().map(rni -> rni.getCaseInfos().getCaseUuid()).filter(Objects::nonNull).forEach(caseService::deleteCase))
                ),
                // delete remote data ids set in root network node infos
                rootNetworkNodeInfoService.getDeleteRootNetworkNodeInfosFutures(rootNetworkInfos.stream().map(RootNetworkInfos::getRootNetworkNodeInfos).filter(Objects::nonNull).flatMap(Collection::stream).toList())
            );
    }

    public Optional<RootNetworkCreationRequestEntity> getCreationRequest(UUID rootNetworkInCreationUuid) {
        return rootNetworkCreationRequestRepository.findById(rootNetworkInCreationUuid);
    }

    public List<RootNetworkCreationRequestEntity> getCreationRequests(UUID studyUuid) {
        return rootNetworkCreationRequestRepository.findAllByStudyUuid(studyUuid);
    }

    public void deleteCreationRequest(RootNetworkCreationRequestEntity rootNetworkCreationRequestEntity) {
        rootNetworkCreationRequestRepository.delete(rootNetworkCreationRequestEntity);
    }

    public void assertCanCreateRootNetwork(UUID studyUuid, String rootNetworkName) {
        assertMaximumByStudyIsNotReached(studyUuid);
        assertNameNotExistInStudy(studyUuid, rootNetworkName);
    }

    private void assertMaximumByStudyIsNotReached(UUID studyUuid) {
        if (rootNetworkRepository.countAllByStudyId(studyUuid) + rootNetworkCreationRequestRepository.countAllByStudyUuid(studyUuid) >= MAXIMUM_ROOT_NETWORK_BY_STUDY) {
            throw new StudyException(MAXIMUM_ROOT_NETWORK_BY_STUDY_REACHED);
        }
    }

    private void assertNameNotExistInStudy(UUID studyUuid, String name) {
        if (isRootNetworkNameExistsInStudy(studyUuid, name)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public boolean isRootNetworkNameExistsInStudy(UUID studyUuid, String rootNetworkName) {
        return rootNetworkRepository.findByNameAndStudyId(rootNetworkName, studyUuid).isPresent() ||
            rootNetworkCreationRequestRepository.findByNameAndStudyUuid(rootNetworkName, studyUuid).isPresent();
    }
}
