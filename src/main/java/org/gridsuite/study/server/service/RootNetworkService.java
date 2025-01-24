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
import org.gridsuite.study.server.dto.BasicRootNetworkInfos;
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

import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Service
public class RootNetworkService {
    private static int MAXIMUM_ROOT_NETWORK_BY_STUDY = 3;

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
        return getRootNetwork(rootNetworkUuid).map(RootNetworkEntity::getCaseName).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
    }

    public Map<String, String> getImportParameters(UUID rootNetworkUuid) {
        return rootNetworkRepository.findWithImportParametersById(rootNetworkUuid).map(RootNetworkEntity::getImportParameters).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
    }

    public List<RootNetworkEntity> getStudyRootNetworks(UUID studyUuid) {
        return rootNetworkRepository.findAllByStudyId(studyUuid);
    }

    public List<RootNetworkInfos> getStudyRootNetworkInfosWithRootNetworkNodeInfos(UUID studyUuid) {
        return rootNetworkRepository.findAllWithInfosByStudyId(studyUuid).stream().map(RootNetworkEntity::toDto).toList();
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
     * delete entity with rootNetworkUuid
     * will also delete all remote resources linked to the entity
     * @param rootNetworkUuid
     */
    public void delete(UUID rootNetworkUuid) {
        delete(rootNetworkRepository.findWithRootNetworkNodeInfosById(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)).toDto());
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

    public List<BasicRootNetworkInfos> getRootNetworks(UUID studyUuid) {
        List<BasicRootNetworkInfos> result = new ArrayList<>();

        // return RootNetworkMinimalInfos with isCreating as false when in rootNetworkRepository
        result.addAll(rootNetworkRepository.findAllByStudyId(studyUuid).stream()
            .map(rootNetworkEntity -> new BasicRootNetworkInfos(rootNetworkEntity.getId(), rootNetworkEntity.getName(), false)).toList());
        // return RootNetworkMinimalInfos with isCreating as true when in rootNetworkCreationRequestRepository
        result.addAll(rootNetworkCreationRequestRepository.findAllByStudyUuid(studyUuid).stream()
            .map(rootNetworkCreationEntity -> new BasicRootNetworkInfos(rootNetworkCreationEntity.getId(), rootNetworkCreationEntity.getName(), true)).toList());

        return result;
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
