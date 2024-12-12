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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyException.Type.DELETE_ROOT_NETWORK_FAILED;

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
    private final StudyServerExecutionService studyServerExecutionService;
    private final EquipmentInfosService equipmentInfosService;
    private final NetworkStoreService networkStoreService;

    public RootNetworkService(RootNetworkRepository rootNetworkRepository,
                              RootNetworkCreationRequestRepository rootNetworkCreationRequestRepository,
                              RootNetworkNodeInfoService rootNetworkNodeInfoService,
                              NetworkService networkService,
                              CaseService caseService,
                              @Lazy RootNetworkService self,
                              StudyServerExecutionService studyServerExecutionService,
                              ReportService reportService,
                              EquipmentInfosService equipmentInfosService,
                              NetworkStoreService networkStoreService) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.networkService = networkService;
        this.caseService = caseService;
        this.reportService = reportService;
        this.self = self;
        this.rootNetworkCreationRequestRepository = rootNetworkCreationRequestRepository;
        this.studyServerExecutionService = studyServerExecutionService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkStoreService = networkStoreService;
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
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getCaseName).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
    }

    public Map<String, String> getImportParameters(UUID rootNetworkUuid) {
        return rootNetworkRepository.findWithImportParametersById(rootNetworkUuid).map(RootNetworkEntity::getImportParameters).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
    }

    public List<RootNetworkEntity> getStudyRootNetworks(UUID studyUuid) {
        return rootNetworkRepository.findAllByStudyId(studyUuid);
    }

    public List<RootNetworkInfos> getStudyRootNetworkInfosWithRootNetworkNodeInfos(UUID studyUuid) {
        return rootNetworkRepository.findAllWithInfosByStudyId(studyUuid).stream().map(RootNetworkEntity::toDto).toList();
    }

    @Transactional
    public void updateNetwork(RootNetworkEntity rootNetworkEntity, NetworkInfos networkInfos) {
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

    public void assertIsRootNetworkInStudy(UUID rootNetworkUuid, UUID studyUuid) {
        if (!rootNetworkRepository.existsByIdAndStudyId(rootNetworkUuid, studyUuid)) {
            throw new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND);
        }
    }

    /**
     * delete entity with rootNetworkUuid
     * will also delete all remote resources linked to the entity
     * @param rootNetworkUuid
     */
    public void delete(UUID rootNetworkUuid) {
        delete(rootNetworkRepository.findWithRootNetworkNodeInfosById(rootNetworkUuid).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND)).toDto());
    }

    public void delete(RootNetworkInfos rootNetworkInfos) {
        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
            getDeleteRootNetworkInfosFutures(List.of(rootNetworkInfos)).toArray(CompletableFuture[]::new)
        );

        try {
            executeInParallel.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StudyException(DELETE_ROOT_NETWORK_FAILED, e.getMessage());
        } catch (Exception e) {
            throw new StudyException(DELETE_ROOT_NETWORK_FAILED, e.getMessage());
        }
        rootNetworkRepository.deleteById(rootNetworkInfos.getId());
    }

    public List<CompletableFuture<Void>> getDeleteRootNetworkInfosFutures(List<RootNetworkInfos> rootNetworkInfos) {
        // delete remote data ids set in root network
        List<CompletableFuture<Void>> result = new ArrayList<>(List.of(
            studyServerExecutionService.runAsync(() -> reportService.deleteReports(rootNetworkInfos.stream().map(RootNetworkInfos::getReportUuid).toList())),
            studyServerExecutionService.runAsync(() -> rootNetworkInfos.stream().map(rni -> rni.getNetworkInfos().getNetworkUuid()).filter(Objects::nonNull).forEach(equipmentInfosService::deleteEquipmentIndexes)),
            studyServerExecutionService.runAsync(() -> rootNetworkInfos.stream().map(rni -> rni.getNetworkInfos().getNetworkUuid()).filter(Objects::nonNull).forEach(networkStoreService::deleteNetwork)),
            studyServerExecutionService.runAsync(() -> rootNetworkInfos.stream().map(rni -> rni.getCaseInfos().getCaseUuid()).filter(Objects::nonNull).forEach(caseService::deleteCase))
        ));

        // delete remote data ids set in root network node infos
        result.addAll(rootNetworkNodeInfoService.getDeleteRootNetworkNodeInfosFutures(rootNetworkInfos.stream().map(RootNetworkInfos::getRootNetworkNodeInfos).filter(Objects::nonNull).flatMap(Collection::stream).toList()));

        return result;
    }

    public Optional<RootNetworkCreationRequestEntity> getCreationRequest(UUID rootNetworkInCreationUuid) {
        return rootNetworkCreationRequestRepository.findById(rootNetworkInCreationUuid);
    }

    public void deleteCreationRequest(RootNetworkCreationRequestEntity rootNetworkCreationRequestEntity) {
        rootNetworkCreationRequestRepository.delete(rootNetworkCreationRequestEntity);
    }
}
