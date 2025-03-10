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
import org.gridsuite.study.server.dto.RootNetworkAction;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestRepository;
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
    private static final int MAXIMUM_TAG_LENGTH = 4;

    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final NetworkService networkService;
    private final CaseService caseService;
    private final ReportService reportService;

    private final RootNetworkRequestRepository rootNetworkRequestRepository;
    private final StudyServerExecutionService studyServerExecutionService;
    private final EquipmentInfosService equipmentInfosService;
    private final NetworkStoreService networkStoreService;

    public RootNetworkService(RootNetworkRepository rootNetworkRepository,
                              RootNetworkRequestRepository rootNetworkRequestRepository,
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
        this.rootNetworkRequestRepository = rootNetworkRequestRepository;
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

    public void updateRootNetwork(@NonNull RootNetworkInfos rootNetworkInfos, boolean updateCase) {
        RootNetworkEntity rootNetworkEntity = getRootNetwork(rootNetworkInfos.getId())
                .orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));

        UUID oldCaseUuid = rootNetworkEntity.getCaseUuid();
        updateRootNetworkInfos(rootNetworkEntity, rootNetworkInfos, updateCase);

        if (updateCase && oldCaseUuid != null) {
            //delete old case
            caseService.deleteCase(oldCaseUuid);
        }
    }

    private void updateRootNetworkInfos(RootNetworkEntity rootNetworkEntity, RootNetworkInfos rootNetworkInfos, boolean updateCase) {
        if (updateCase) {
            getRootNetworkRequest(rootNetworkInfos.getId()).ifPresent(request -> {
                rootNetworkInfos.setName(request.getName());
                rootNetworkInfos.setTag(request.getTag());
            });

            updateCaseInfos(rootNetworkEntity, rootNetworkInfos.getCaseInfos());
            updateNetworkInfos(rootNetworkEntity, rootNetworkInfos.getNetworkInfos());
            rootNetworkEntity.setImportParameters(rootNetworkInfos.getImportParameters());
            rootNetworkEntity.setReportUuid(rootNetworkInfos.getReportUuid());
        }

        if (rootNetworkInfos.getName() != null) {
            rootNetworkEntity.setName(rootNetworkInfos.getName());
        }
        if (rootNetworkInfos.getTag() != null) {
            rootNetworkEntity.setTag(rootNetworkInfos.getTag());
        }
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
                        .tag(rootNetworkEntityToDuplicate.getTag())
                        .build()
                );
            }
        );
    }

    private RootNetworkRequestEntity insertRootNetworkRequest(UUID rootNetworkUuid, StudyEntity studyEntity, String rootNetworkName, String rootNetworkTag, String userId, RootNetworkAction action) {
        return rootNetworkRequestRepository.save(RootNetworkRequestEntity.builder().id(rootNetworkUuid).name(rootNetworkName).tag(rootNetworkTag).studyUuid(studyEntity.getId()).userId(userId).actionRequest(action).build());
    }

    public RootNetworkRequestEntity insertCreationRequest(UUID rootNetworkUuid, StudyEntity studyEntity, String rootNetworkName, String rootNetworkTag, String userId) {
        return insertRootNetworkRequest(rootNetworkUuid, studyEntity, rootNetworkName, rootNetworkTag, userId, RootNetworkAction.ROOT_NETWORK_CREATION);
    }

    public RootNetworkRequestEntity insertModificationRequest(UUID rootNetworkUuid, StudyEntity studyEntity, String rootNetworkName, String rootNetworkTag, String userId) {
        return insertRootNetworkRequest(rootNetworkUuid, studyEntity, rootNetworkName, rootNetworkTag, userId, RootNetworkAction.ROOT_NETWORK_MODIFICATION);
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

    public Optional<RootNetworkRequestEntity> getRootNetworkRequest(UUID rootNetworkUuid) {
        return rootNetworkRequestRepository.findById(rootNetworkUuid);
    }

    public List<RootNetworkRequestEntity> getCreationRequests(UUID studyUuid) {
        return rootNetworkRequestRepository.findAllByStudyUuid(studyUuid);
    }

    public void deleteRootNetworkRequest(RootNetworkRequestEntity rootNetworkRequestEntity) {
        rootNetworkRequestRepository.delete(rootNetworkRequestEntity);
    }

    public void assertCanCreateRootNetwork(UUID studyUuid, String rootNetworkName, String rootNetworkTag) {
        assertMaximumByStudyIsNotReached(studyUuid);
        assertNameNotExistInStudy(studyUuid, rootNetworkName);
        assertTagSize(rootNetworkTag);
        assertTagNotExistInStudy(studyUuid, rootNetworkTag);
    }

    public void assertCanModifyRootNetwork(UUID studyUuid, UUID rootNetworkUuid, String rootNetworkName, String rootNetworkTag) {
        assertNameNotExistInStudyExceptCurrent(studyUuid, rootNetworkUuid, rootNetworkName);
        assertTagSize(rootNetworkTag);
        assertTagNotExistInStudyExceptCurrent(studyUuid, rootNetworkUuid, rootNetworkTag);
    }

    private void assertTagNotExistInStudyExceptCurrent(UUID studyUuid, UUID rootNetworkUuid, String tag) {
        if (isRootNetworkTagExistsInStudy(studyUuid, tag) && !isTagForCurrentRootNetwork(rootNetworkUuid, tag)) {
            throw new StudyException(NOT_ALLOWED, "Tag already exists in study and is not for current root network");
        }
    }

    private void assertNameNotExistInStudyExceptCurrent(UUID studyUuid, UUID rootNetworkUuid, String rootNetworkName) {
        if (isRootNetworkNameExistsInStudy(studyUuid, rootNetworkName) && !isNameForCurrentRootNetwork(rootNetworkUuid, rootNetworkName)) {
            throw new StudyException(NOT_ALLOWED, "Name already exists in study and is not for current root network");
        }
    }

    private boolean isTagForCurrentRootNetwork(UUID rootNetworkUuid, String tag) {
        return rootNetworkRepository.findByIdAndTag(rootNetworkUuid, tag).isPresent();
    }

    private boolean isNameForCurrentRootNetwork(UUID rootNetworkUuid, String name) {
        return rootNetworkRepository.findByIdAndName(rootNetworkUuid, name).isPresent();
    }

    private void assertMaximumByStudyIsNotReached(UUID studyUuid) {
        if (rootNetworkRepository.countAllByStudyId(studyUuid) + rootNetworkRequestRepository.countAllByStudyUuid(studyUuid) >= MAXIMUM_ROOT_NETWORK_BY_STUDY) {
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
                rootNetworkRequestRepository.findByNameAndStudyUuid(rootNetworkName, studyUuid).isPresent();
    }

    private void assertTagSize(String tag) {
        if (tag.length() > MAXIMUM_TAG_LENGTH) {
            throw new StudyException(MAXIMUM_TAG_LENGTH_EXCEEDED, "Tag maximum length exceeded. Should be <= " + MAXIMUM_TAG_LENGTH);
        }
    }

    private void assertTagNotExistInStudy(UUID studyUuid, String tag) {
        if (isRootNetworkTagExistsInStudy(studyUuid, tag)) {
            throw new StudyException(NOT_ALLOWED, "Tag already exists in study");
        }
    }

    public boolean isRootNetworkTagExistsInStudy(UUID studyUuid, String rootNetworkTag) {
        return rootNetworkRepository.findByTagAndStudyId(rootNetworkTag, studyUuid).isPresent() ||
                rootNetworkRequestRepository.findByTagAndStudyUuid(rootNetworkTag, studyUuid).isPresent();
    }
}
