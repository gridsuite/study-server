/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.util.StringUtils;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.computation.ComputationParameterUUIDs;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.gridsuite.study.server.service.voltageinit.VoltageInitRestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.util.Pair;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@SuppressWarnings("checkstyle:RegexpSingleline")
@Service
public class StudyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyService.class);
    public static final String STUDY_NOT_FOUND = "Study not found";

    NotificationService notificationService;

    NetworkModificationTreeService networkModificationTreeService;

    StudyServerExecutionService studyServerExecutionService;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkModificationRestService networkModificationRestService;
    private final UserAdminService userAdminService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;
    private final NetworkConversionService networkConversionService;
    private final StudyConfigService studyConfigService;
    private final NadConfigService nadConfigService;
    private final FilterService filterService;
    private final CaseService caseService;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

    private final ComputationParametersService computationParametersService;
    private final DynamicMappingService dynamicMappingService;
    private final VoltageInitRestService voltageInitRestService;
    private final WorkspaceService workspaceService;

    private final ObjectMapper objectMapper;

    public enum ReportType {
        NETWORK_MODIFICATION("NetworkModification"),
        LOAD_FLOW("LoadFlow"),
        SECURITY_ANALYSIS("SecurityAnalysis"),
        SHORT_CIRCUIT("AllBusesShortCircuitAnalysis"),
        SHORT_CIRCUIT_ONE_BUS("OneBusShortCircuitAnalysis"),
        SENSITIVITY_ANALYSIS("SensitivityAnalysis"),
        DYNAMIC_SIMULATION("DynamicSimulation"),
        DYNAMIC_SECURITY_ANALYSIS("DynamicSecurityAnalysis"),
        DYNAMIC_MARGIN_CALCULATION("DynamicMarginCalculation"),
        VOLTAGE_INITIALIZATION("VoltageInit"),
        STATE_ESTIMATION("StateEstimation"),
        PCC_MIN("PccMin"),
        ASYMMETRICAL_LOAD("AsymmetricalLoad"),
        LOGICAL_CONTROLS("LogicalControls");

        public final String reportKey;

        ReportType(String reportKey) {
            this.reportKey = reportKey;
        }
    }

    private final StudyService self;

    @Value("${study.enable-operation-quotas}")
    private boolean shouldCheckOperationQuotas;

    @Autowired
    public StudyService(
            StudyRepository studyRepository,
            StudyCreationRequestRepository studyCreationRequestRepository,
            NetworkModificationRestService networkModificationRestService,
            UserAdminService userAdminService,
            StudyInfosService studyInfosService,
            EquipmentInfosService equipmentInfosService,
            NetworkModificationTreeService networkModificationTreeService,
            ObjectMapper objectMapper,
            StudyServerExecutionService studyServerExecutionService,
            NotificationService notificationService,
            NetworkConversionService networkConversionService,
            CaseService caseService,
            DynamicMappingService dynamicMappingService,
            VoltageInitRestService voltageInitService,
            StudyConfigService studyConfigService,
            NadConfigService nadConfigService,
            FilterService filterService,
            @Lazy StudyService studyService,
            RootNetworkService rootNetworkService,
            RootNetworkNodeInfoService rootNetworkNodeInfoService,
            ComputationParametersService computationParametersService,
            WorkspaceService workspaceService) {
        this.studyRepository = studyRepository;
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.networkModificationRestService = networkModificationRestService;
        this.userAdminService = userAdminService;
        this.studyInfosService = studyInfosService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.studyServerExecutionService = studyServerExecutionService;
        this.notificationService = notificationService;
        this.networkConversionService = networkConversionService;
        this.caseService = caseService;
        this.dynamicMappingService = dynamicMappingService;
        this.voltageInitRestService = voltageInitService;
        this.studyConfigService = studyConfigService;
        this.nadConfigService = nadConfigService;
        this.filterService = filterService;
        this.self = studyService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.computationParametersService = computationParametersService;
        this.workspaceService = workspaceService;
    }

    private CreatedStudyBasicInfos toStudyInfos(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return CreatedStudyBasicInfos.builder()
                .id(studyUuid)
                .monoRoot(studyEntity.isMonoRoot())
                .build();
    }

    private static BasicStudyInfos toBasicStudyInfos(StudyCreationRequestEntity entity) {
        return BasicStudyInfos.builder()
                .id(entity.getId())
                .build();
    }

    private CreatedStudyBasicInfos toCreatedStudyBasicInfos(StudyEntity entity) {
        return CreatedStudyBasicInfos.builder()
                .id(entity.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public List<CreatedStudyBasicInfos> getStudies() {
        return studyRepository.findAll().stream()
                .map(this::toCreatedStudyBasicInfos)
                .collect(Collectors.toList());
    }

    public List<UUID> getAllOrphanIndexedEquipmentsNetworkUuids() {
        return equipmentInfosService.getOrphanEquipmentInfosNetworkUuids(rootNetworkService.getAllNetworkUuids());
    }

    @Transactional(readOnly = true)
    public List<CreatedStudyBasicInfos> getStudiesMetadata(List<UUID> uuids) {
        return studyRepository.findAllById(uuids).stream().map(this::toCreatedStudyBasicInfos).toList();

    }

    public List<BasicStudyInfos> getStudiesCreationRequests() {
        return studyCreationRequestRepository.findAll().stream()
                .map(StudyService::toBasicStudyInfos)
                .collect(Collectors.toList());
    }

    public BasicStudyInfos createStudy(UUID caseUuid, String userId, UUID studyUuid, Map<String, Object> importParameters, boolean duplicateCase, String caseFormat, String firstRootNetworkName) {
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid, firstRootNetworkName));
        UUID caseUuidToUse = caseUuid;
        try {
            if (duplicateCase) {
                caseUuidToUse = caseService.duplicateCase(caseUuid, true);
            }
            RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().caseInfos(new CaseInfos(caseUuidToUse,
                    caseUuid, null, caseFormat)).build();

            persistNetwork(rootNetworkInfos, basicStudyInfos.getId(), NetworkModificationTreeService.FIRST_VARIANT_ID, userId, importParameters, CaseImportAction.STUDY_CREATION, UUID.randomUUID());
        } catch (Exception e) {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            throw e;
        }

        return basicStudyInfos;
    }

    @Transactional
    public void deleteRootNetworkRequest(UUID rootNetworkInCreationUuid) {
        Optional<RootNetworkRequestEntity> rootNetworkCreationRequestEntityOpt = rootNetworkService.getRootNetworkRequest(rootNetworkInCreationUuid);
        if (rootNetworkCreationRequestEntityOpt.isPresent()) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkCreationRequestEntityOpt.get());
        }
    }

    @Transactional
    public void createRootNetwork(@NonNull UUID studyUuid, @NonNull RootNetworkInfos rootNetworkInfos) {
        StudyEntity studyEntity = getStudy(studyUuid);
        Optional<RootNetworkRequestEntity> rootNetworkCreationRequestEntityOpt = rootNetworkService.getRootNetworkRequest(rootNetworkInfos.getId());
        if (rootNetworkCreationRequestEntityOpt.isPresent()) {
            rootNetworkInfos.setName(rootNetworkCreationRequestEntityOpt.get().getName());
            rootNetworkInfos.setTag(rootNetworkCreationRequestEntityOpt.get().getTag());
            rootNetworkInfos.setDescription(rootNetworkCreationRequestEntityOpt.get().getDescription());
            rootNetworkService.createRootNetwork(studyEntity, rootNetworkInfos);
            rootNetworkService.deleteRootNetworkRequest(rootNetworkCreationRequestEntityOpt.get());
            //update study entity to multi root
            if (studyEntity.isMonoRoot()) {
                studyEntity.setMonoRoot(false);
                invalidatePreviousRootNetworkNodeTree(studyEntity);
            }
            networkModificationRestService.addRootNetworkTag(getStudyModificationGroupUuids(studyUuid), rootNetworkInfos.getTag());
        } else {
            rootNetworkService.deleteRootNetworks(studyEntity, List.of(rootNetworkInfos));
        }
        notificationService.emitRootNetworksUpdated(studyUuid);
    }

    /**
     * The nodes built while the study was mono root network ignored the applicabilities, so they have to be built
     * again now that these are taken into account.
     */
    private void invalidatePreviousRootNetworkNodeTree(StudyEntity studyEntity) {
        UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyEntity.getId());
        networkModificationTreeService.invalidateNodeTree(studyEntity.getId(), rootNodeUuid, studyEntity.getFirstRootNetwork().getId());
    }

    public SharedModificationsWriteAccess getSharedModificationsWriteAccess(UUID studyUuid, String userId) {
        List<UUID> groupUuids = getStudyModificationGroupUuids(studyUuid);
        return groupUuids.isEmpty()
                ? SharedModificationsWriteAccess.NO_SHARED_MODIFICATION
                : networkModificationRestService.getReferencedModificationsWriteAccess(groupUuids, userId);
    }

    private List<UUID> getStudyModificationGroupUuids(UUID studyUuid) {
        return networkModificationTreeService.getAllNetworkModificationNodeInfos(studyUuid).stream()
                .map(NetworkModificationNodeInfoEntity::getModificationGroupUuid)
                .toList();
    }

    public UUID duplicateStudy(UUID sourceStudyUuid, String userId) {
        Objects.requireNonNull(sourceStudyUuid);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElse(null);
        if (sourceStudy == null) {
            return null;
        }
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, null, null));

        studyServerExecutionService.runAsync(() -> self.duplicateStudyAsync(basicStudyInfos, sourceStudyUuid, userId));

        return basicStudyInfos.getId();
    }

    @Transactional
    public void duplicateStudyAsync(BasicStudyInfos basicStudyInfos, UUID sourceStudyUuid, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        try {
            startTime.set(System.nanoTime());

            StudyEntity duplicatedStudy = duplicateStudy(basicStudyInfos, sourceStudyUuid, userId);

            rootNetworkService.getStudyRootNetworks(duplicatedStudy.getId()).forEach(rootNetworkEntity ->
                    rootNetworkService.reindexRootNetwork(duplicatedStudy, rootNetworkEntity.getId())
            );
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        } finally {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            LOGGER.trace("Create study '{}' from source {} : {} seconds", basicStudyInfos.getId(), sourceStudyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    @Transactional(readOnly = true)
    public CreatedStudyBasicInfos getStudyInfos(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);
        StudyEntity studyEntity = getStudy(studyUuid);
        return toStudyInfos(studyEntity.getId());
    }

    public List<CreatedStudyBasicInfos> searchStudies(@NonNull String query) {
        return studyInfosService.search(query);
    }

    private Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        DeleteStudyInfos deleteStudyInfos = null;
        if (studyCreationRequestEntity.isEmpty() && studyEntity.isPresent()) {
            List<RootNetworkInfos> rootNetworkInfos = getStudyRootNetworksInfos(studyUuid);
            // get all modification groups and nodes related to the study
            List<NetworkModificationNodeInfoEntity> allStudyNetworkModificationNodeInfo = networkModificationTreeService.getAllNetworkModificationNodeInfos(studyUuid);
            List<Pair<UUID, UUID>> modificationGroupUuidsNodeUuids = allStudyNetworkModificationNodeInfo.stream()
                    .map(nodeInfoEntity -> Pair.of(nodeInfoEntity.getModificationGroupUuid(), nodeInfoEntity.getIdNode()))
                    .toList();
            StudyEntity s = studyEntity.get();
            networkModificationTreeService.doDeleteTree(studyUuid);
            studyRepository.deleteById(studyUuid);
            studyInfosService.deleteByUuid(studyUuid);
            computationParametersService.deleteComputationsParameters(s);
            removeNetworkVisualizationParameters(s.getNetworkVisualizationParametersUuid());
            removeSpreadsheetConfigCollection(s.getSpreadsheetConfigCollectionUuid());
            removeWorkspacesConfig(s.getWorkspacesConfigUuid());
            removeNadConfigs(s.getNadConfigsUuids().stream().toList());
            deleteStudyInfos = new DeleteStudyInfos(rootNetworkInfos, modificationGroupUuidsNodeUuids);
        } else {
            studyCreationRequestEntity.ifPresent(creationRequestEntity -> studyCreationRequestRepository.deleteById(creationRequestEntity.getId()));
        }

        if (deleteStudyInfos == null) {
            return Optional.empty();
        } else {
            return Optional.of(deleteStudyInfos);
        }
    }

    private void removeNetworkVisualizationParameters(@Nullable UUID uuid) {
        if (uuid != null) {
            try {
                studyConfigService.deleteNetworkVisualizationParameters(uuid);
            } catch (Exception e) {
                LOGGER.error("Could not delete network visualization parameters with uuid:" + uuid, e);
            }
        }
    }

    @Transactional
    public void deleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        Optional<DeleteStudyInfos> deleteStudyInfosOpt = doDeleteStudyIfNotCreationInProgress(studyUuid);
        if (deleteStudyInfosOpt.isPresent()) {
            DeleteStudyInfos deleteStudyInfos = deleteStudyInfosOpt.get();
            startTime.set(System.nanoTime());

            // delete all distant resources linked to rootNetworks
            rootNetworkService.invalidateRootNetworkRemoteInfos(deleteStudyInfos.getRootNetworkInfosList(), false, true);

            // delete all distant resources linked to nodes
            studyServerExecutionService.runAsync(() -> deleteStudyInfos.getModificationGroupUuidsNodeUuids().stream()
                    .filter(Objects::nonNull)
                    .forEach(groupUuidNodeUuid -> networkModificationRestService.deleteModificationsByGroup(groupUuidNodeUuid.getFirst(), userId)));

            LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));

        }
    }

    @Transactional
    public CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, CaseInfos caseInfos,
                                              ComputationParameterUUIDs computationParameterUUIDs, UUID networkVisualizationParametersUuid,
                                              UUID spreadsheetConfigCollectionUuid, UUID workspacesConfigUuid,
                                              Map<String, Object> importParameters, UUID importReportUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkInfos.getNetworkUuid());
        Objects.requireNonNull(networkInfos.getNetworkId());
        Objects.requireNonNull(caseInfos.getCaseFormat());
        Objects.requireNonNull(caseInfos.getCaseUuid());
        Objects.requireNonNull(importParameters);

        StudyEntity studyEntity = saveStudyThenCreateBasicTree(studyUuid, networkInfos,
                caseInfos, computationParameterUUIDs, networkVisualizationParametersUuid, spreadsheetConfigCollectionUuid, workspacesConfigUuid, importParameters, importReportUuid);

        // Need to deal with the study creation (with a default root network ?)
        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudyCreationFinished(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    @Transactional
    public CreatedStudyBasicInfos updateNetwork(UUID studyUuid, UUID rootNetworkUuid, NetworkInfos networkInfos, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        RootNetworkEntity rootNetworkEntity = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));

        rootNetworkService.updateNetwork(rootNetworkEntity, networkInfos);
        rootNetworkService.updateNetworkLoadStatus(rootNetworkUuid, RootNetworkLoadStatus.LOADED);

        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudyNetworkRecreationDone(studyEntity.getId(), userId);

        return createdStudyBasicInfos;
    }

    public UserProfileInfos getUserProfile(String userId) {
        try {
            return userAdminService.getUserProfile(userId);
        } catch (Exception e) {
            LOGGER.error(String.format("Could not access to profile for user '%s'", userId), e);
        }
        return null;
    }

    private void duplicateStudyNodeAliases(StudyEntity newStudyEntity, StudyEntity sourceStudyEntity) {
        if (!CollectionUtils.isEmpty(sourceStudyEntity.getNodeAliases())) {
            Map<UUID, AbstractNode> newStudyNodes = networkModificationTreeService.getAllStudyNodesByUuid(newStudyEntity.getId());
            Map<UUID, AbstractNode> sourceStudyNodes = networkModificationTreeService.getAllStudyNodesByUuid(sourceStudyEntity.getId());

            List<NodeAliasEmbeddable> newStudyNodeAliases = new ArrayList<>();
            sourceStudyEntity.getNodeAliases().forEach(nodeAliasEmbeddable -> {
                String aliasName = nodeAliasEmbeddable.getName();
                UUID nodeUuid = nodeAliasEmbeddable.getNodeId();
                UUID newNodeId = null;
                if (nodeUuid != null && sourceStudyNodes.containsKey(nodeUuid)) {
                    String nodeName = sourceStudyNodes.get(nodeUuid).getName();
                    newNodeId = newStudyNodes.entrySet().stream().filter(entry -> nodeName.equals(entry.getValue().getName()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                }
                newStudyNodeAliases.add(new NodeAliasEmbeddable(aliasName, newNodeId));
            });
            newStudyEntity.setNodeAliases(newStudyNodeAliases);
        }
    }

    private StudyEntity duplicateStudy(BasicStudyInfos studyInfos, UUID sourceStudyUuid, String userId) {
        Objects.requireNonNull(studyInfos.getId());
        Objects.requireNonNull(userId);

        StudyEntity sourceStudy = getStudy(sourceStudyUuid);

        StudyEntity newStudyEntity = duplicateStudyEntity(sourceStudy, studyInfos.getId());
        rootNetworkService.duplicateStudyRootNetworks(newStudyEntity, sourceStudy);
        networkModificationTreeService.duplicateStudyNodes(newStudyEntity, sourceStudy, userId);
        duplicateStudyNodeAliases(newStudyEntity, sourceStudy);

        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(newStudyEntity);
        studyInfosService.add(createdStudyBasicInfos);
        notificationService.emitStudyCreationFinished(studyInfos.getId(), userId);

        return newStudyEntity;
    }

    private StudyEntity duplicateStudyEntity(StudyEntity sourceStudyEntity, UUID newStudyId) {
        UUID copiedNetworkVisualizationParametersUuid = null;
        if (sourceStudyEntity.getNetworkVisualizationParametersUuid() != null) {
            copiedNetworkVisualizationParametersUuid = studyConfigService.duplicateNetworkVisualizationParameters(sourceStudyEntity.getNetworkVisualizationParametersUuid());
        }

        UUID copiedSpreadsheetConfigCollectionUuid = null;
        if (sourceStudyEntity.getSpreadsheetConfigCollectionUuid() != null) {
            copiedSpreadsheetConfigCollectionUuid = studyConfigService.duplicateSpreadsheetConfigCollection(sourceStudyEntity.getSpreadsheetConfigCollectionUuid());
        }

        UUID copiedWorkspacesConfigUuid = null;
        if (sourceStudyEntity.getWorkspacesConfigUuid() != null) {
            copiedWorkspacesConfigUuid = studyConfigService.duplicateWorkspacesConfig(sourceStudyEntity.getWorkspacesConfigUuid());
        }

        ComputationParameterUUIDs duplicatedComputationParameterUUIDs = computationParametersService.duplicateParameters(sourceStudyEntity);

        return studyRepository.save(StudyEntity.builder()
            .id(newStudyId)
            .loadFlowParametersUuid(duplicatedComputationParameterUUIDs.loadFlowParametersUuid())
            .securityAnalysisParametersUuid(duplicatedComputationParameterUUIDs.securityAnalysisParametersUuid())
            .dynamicSimulationParametersUuid(duplicatedComputationParameterUUIDs.dynamicSimulationParametersUuid())
            .dynamicSecurityAnalysisParametersUuid(duplicatedComputationParameterUUIDs.dynamicSecurityAnalysisParametersUuid())
            .dynamicMarginCalculationParametersUuid(duplicatedComputationParameterUUIDs.dynamicMarginCalculationParametersUuid())
            .shortCircuitParametersUuid(duplicatedComputationParameterUUIDs.shortCircuitParametersUuid())
            .voltageInitParametersUuid(duplicatedComputationParameterUUIDs.voltageInitParametersUuid())
            .sensitivityAnalysisParametersUuid(duplicatedComputationParameterUUIDs.sensitivityAnalysisParametersUuid())
            .stateEstimationParametersUuid(duplicatedComputationParameterUUIDs.stateEstimationParametersUuid())
            .pccMinParametersUuid(duplicatedComputationParameterUUIDs.pccMinParametersUuid())
            .asymmetricalLoadParametersUuid(duplicatedComputationParameterUUIDs.asymmetricalLoadParametersUuid())
            .networkVisualizationParametersUuid(copiedNetworkVisualizationParametersUuid)
            .spreadsheetConfigCollectionUuid(copiedSpreadsheetConfigCollectionUuid)
            .workspacesConfigUuid(copiedWorkspacesConfigUuid)
            .monoRoot(sourceStudyEntity.isMonoRoot())
            .build());
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid, String firstRootNetworkName) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(studyUuid, firstRootNetworkName);
        notificationService.emitStudyCreationStarted(newStudy.getId(), userId);
        return newStudy;
    }

    private void persistNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String variantId, String userId,
                                Map<String, Object> importParameters, CaseImportAction caseImportAction, UUID reportId) {
        networkConversionService.persistNetwork(rootNetworkInfos, studyUuid, variantId, userId, reportId, importParameters, caseImportAction);
    }

    /**
     * Modifications written into a study must be associated with all its root network tags, including the shared
     * modifications they point to: the ones the user cannot write on, which they cannot associate with a new tag, must
     * already be.
     *
     * @param containerUuids groups or composites whose content is written into the study: only the shared modifications
     *                       their content refers to are checked, not the containers themselves
     * @param modificationUuids modifications written into the study: the shared modifications they and their content
     *                          refer to are checked
     * @param sharedUuids shared modifications the study is given a reference to: they are checked themselves, along
     *                    with the shared modifications their content refers to
     */
    private void assertCanWriteModificationsInStudy(UUID studyUuid, List<UUID> containerUuids, List<UUID> modificationUuids,
                                                    List<UUID> sharedUuids, String userId) {
        networkModificationRestService.assertRootNetworkTagsAllowed(containerUuids, modificationUuids, sharedUuids,
                rootNetworkService.getStudyRootNetworkTags(studyUuid), userId);
    }

    public void assertIsStudyExist(UUID studyUuid) {
        if (!studyRepository.existsById(studyUuid)) {
            throw new StudyException(NOT_FOUND, STUDY_NOT_FOUND);
        }
    }

    @Transactional
    public String getNetworkVisualizationParametersValues(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyConfigService.getNetworkVisualizationParameters(studyConfigService.getNetworkVisualizationParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public void setNetworkVisualizationParametersValues(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        createOrUpdateNetworkVisualizationParameters(studyEntity, parameters);
        notificationService.emitNetworkVisualizationParamsChanged(studyUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void createOrUpdateNetworkVisualizationParameters(StudyEntity studyEntity, String parameters) {
        UUID networkVisualizationParametersUuid = studyEntity.getNetworkVisualizationParametersUuid();
        if (networkVisualizationParametersUuid == null) {
            networkVisualizationParametersUuid = studyConfigService.createNetworkVisualizationParameters(parameters);
            studyEntity.setNetworkVisualizationParametersUuid(networkVisualizationParametersUuid);
        } else {
            studyConfigService.updateNetworkVisualizationParameters(networkVisualizationParametersUuid, parameters);
        }
    }

    private void removeNadConfigs(List<UUID> nadConfigUuids) {
        try {
            nadConfigService.deleteNadConfigs(nadConfigUuids);
        } catch (Exception e) {
            LOGGER.error("Could not remove NAD configs with uuids:" + nadConfigUuids, e);
        }
    }

    private StudyEntity buildStudyEntity(UUID studyUuid, ComputationParameterUUIDs computationParameterUUIDs, UUID networkVisualizationParametersUuid,
                                         UUID spreadsheetConfigCollectionUuid, UUID workspacesConfigUuid) {
        return StudyEntity.builder()
                .id(studyUuid)
                .loadFlowParametersUuid(computationParameterUUIDs.loadFlowParametersUuid())
                .shortCircuitParametersUuid(computationParameterUUIDs.shortCircuitParametersUuid())
                .voltageInitParametersUuid(computationParameterUUIDs.voltageInitParametersUuid())
                .securityAnalysisParametersUuid(computationParameterUUIDs.securityAnalysisParametersUuid())
                .sensitivityAnalysisParametersUuid(computationParameterUUIDs.sensitivityAnalysisParametersUuid())
                .voltageInitParameters(new StudyVoltageInitParametersEntity())
                .networkVisualizationParametersUuid(networkVisualizationParametersUuid)
                .dynamicSimulationParametersUuid(computationParameterUUIDs.dynamicSimulationParametersUuid())
                .dynamicSecurityAnalysisParametersUuid(computationParameterUUIDs.dynamicSecurityAnalysisParametersUuid())
                .dynamicMarginCalculationParametersUuid(computationParameterUUIDs.dynamicMarginCalculationParametersUuid())
                .stateEstimationParametersUuid(computationParameterUUIDs.stateEstimationParametersUuid())
                .pccMinParametersUuid(computationParameterUUIDs.pccMinParametersUuid())
                .spreadsheetConfigCollectionUuid(spreadsheetConfigCollectionUuid)
                .workspacesConfigUuid(workspacesConfigUuid)
                .monoRoot(true)
                .build();
    }

    private StudyEntity saveStudyThenCreateBasicTree(UUID studyUuid, NetworkInfos networkInfos,
                                                    CaseInfos caseInfos, ComputationParameterUUIDs computationParameterUUIDs,
                                                    UUID networkVisualizationParametersUuid, UUID spreadsheetConfigCollectionUuid,
                                                    UUID workspacesConfigUuid, Map<String, Object> importParameters, UUID importReportUuid) {

        StudyEntity studyEntity = buildStudyEntity(studyUuid, computationParameterUUIDs, networkVisualizationParametersUuid, spreadsheetConfigCollectionUuid, workspacesConfigUuid);
        var study = studyRepository.save(studyEntity);
        // if the StudyCreationRequestEntity has no firstRootNetworkName then the first root network's name is the case file name with the extension.
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        var firstRootNetworkName = caseInfos.getCaseName();
        if (studyCreationRequestEntity.isPresent() && !StringUtils.isBlank(studyCreationRequestEntity.get().getFirstRootNetworkName())) {
            // in this case, the first root network's name is the name the user entered when selecting the case.
            firstRootNetworkName = studyCreationRequestEntity.get().getFirstRootNetworkName();
        }
        rootNetworkService.createRootNetwork(studyEntity,
                RootNetworkInfos.builder().id(UUID.randomUUID()).name(firstRootNetworkName).networkInfos(networkInfos).caseInfos(caseInfos).reportUuid(importReportUuid).importParameters(
                        importParameters).tag("1").build());
        networkModificationTreeService.createBasicTree(study);

        return study;
    }

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(UUID studyUuid, String firstRootNetworkName) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid, firstRootNetworkName);
        return studyCreationRequestRepository.save(studyCreationRequestEntity);
    }

    @Transactional(readOnly = true)
    public List<UUID> getFirstLevelChildrenToBuild(@NonNull UUID studyUuid, @NonNull UUID parentNodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        return networkModificationTreeService.getChildren(parentNodeUuid).stream()
            .map(NodeEntity::getIdNode)
            .filter(childUuid -> !networkModificationTreeService.getNodeBuildStatus(childUuid, rootNetworkUuid).isBuilt())
            .limit(Math.max(0, getAllowedBuildNodesUpToQuota(studyUuid, rootNetworkUuid, userId)))
            .toList();
    }

    public void handleBuildSuccess(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, NetworkModificationResult networkModificationResult) {
        LOGGER.info("Build completed for node '{}'", nodeUuid);

        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid,
            NodeBuildStatus.from(networkModificationResult.getLastGroupApplicationStatus(), networkModificationResult.getApplicationStatus()));

        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_BUILD_COMPLETED, networkModificationResult.getImpactedSubstationsIds());
    }

    private long getAllowedBuildNodesUpToQuota(@NonNull UUID studyUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        Map<QuotaType, QuotaState> userQuotaState = userAdminService.getUserQuotaState(userId);

        return Optional.ofNullable(userQuotaState.get(QuotaType.BUILD)).map(QuotaState::max).map(maxBuilds -> {
            long nbBuiltNodes = networkModificationTreeService.countBuiltNodes(studyUuid, rootNetworkUuid);
            return (long) maxBuilds - nbBuiltNodes;
        }).orElse(Long.MAX_VALUE);
    }

    private void assertDuplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode) {
        networkModificationTreeService.assertStudyContainsNode(sourceStudyUuid, nodeToCopyUuid);
        networkModificationTreeService.assertStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
    }

    @Transactional
    public void duplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        assertDuplicateStudyNode(sourceStudyUuid, targetStudyUuid, nodeToCopyUuid, referenceNodeUuid, insertMode);
        if (!sourceStudyUuid.equals(targetStudyUuid)) {
            assertCanWriteModificationsInStudy(targetStudyUuid, List.of(networkModificationTreeService.getModificationGroupUuid(nodeToCopyUuid)),
                    List.of(), List.of(), userId);
        }

        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode, userId);
        boolean invalidateBuild = networkModificationTreeService.hasModifications(nodeToCopyUuid, false);
        if (invalidateBuild) {
            networkModificationTreeService.invalidateNodeTree(targetStudyUuid, duplicatedNodeUuid, InvalidateNodeTreeParameters.ONLY_CHILDREN_BUILD_STATUS);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, STUDY_NOT_FOUND));
    }

    public void notify(@NonNull UUID studyUuid) {
        notificationService.emitStudyMetadataChanged(studyUuid);
    }

    @Transactional
    public String getSpreadsheetConfigCollection(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyConfigService.getSpreadsheetConfigCollection(studyConfigService.getSpreadsheetConfigCollectionUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional(readOnly = true)
    public String getSpreadsheetConfigCollectionForExport(UUID studyUuid) {
        UUID spreadsheetConfigCollectionUuid = getStudy(studyUuid).getSpreadsheetConfigCollectionUuid();
        if (spreadsheetConfigCollectionUuid == null) {
            return null;
        }
        return studyConfigService.getSpreadsheetConfigCollection(spreadsheetConfigCollectionUuid);
    }

    @Transactional
    public String getComputationResultGlobalFilters(UUID studyUuid, String computationType) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID computationResultFiltersId = studyEntity.getComputationResultFiltersUuid();
        if (Objects.isNull(computationResultFiltersId)) {
            return null;
        }
        return studyConfigService.getComputationResultGlobalFilters(computationResultFiltersId, computationType);
    }

    @Transactional
    public String getComputationResultColumnFilters(UUID studyUuid, String computationType, String computationSubType) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID computationResultFiltersId = studyEntity.getComputationResultFiltersUuid();
        if (Objects.isNull(computationResultFiltersId)) {
            return null;
        }
        return studyConfigService.getComputationResultColumnFilters(computationResultFiltersId, computationType, computationSubType);
    }

    /**
     * Set spreadsheet config collection on study or reset to default one if empty body.
     * Default is the user profile one, or system default if no profile is available.
     *
     * @param studyUuid the study UUID
     * @param configCollection the spreadsheet config collection (null means reset to default)
     * @param userId the user ID for retrieving profile
     * @return true if reset with user profile cannot be done, false otherwise
     */
    @Transactional
    public boolean setSpreadsheetConfigCollection(UUID studyUuid, String configCollection, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean status = createOrUpdateSpreadsheetConfigCollection(studyEntity, configCollection, userId);
        notificationService.emitSpreadsheetCollectionChanged(studyUuid, studyEntity.getSpreadsheetConfigCollectionUuid());
        return status;
    }

    /**
     * Create or update spreadsheet config collection parameters.
     * If configCollection is null, try to use the one from user profile, or system default if no profile.
     *
     * @param studyEntity the study entity
     * @param configCollection the spreadsheet config collection (null means reset to default)
     * @param userId the user ID for retrieving profile
     * @return true if reset with user profile cannot be done, false otherwise
     */
    private boolean createOrUpdateSpreadsheetConfigCollection(StudyEntity studyEntity, String configCollection, String userId) {
        boolean userProfileIssue = false;
        UUID existingSpreadsheetConfigCollectionUuid = studyEntity.getSpreadsheetConfigCollectionUuid();

        UserProfileInfos userProfileInfos = configCollection == null ? userAdminService.getUserProfile(userId) : null;
        if (configCollection == null && userProfileInfos.getSpreadsheetConfigCollectionId() != null) {
            // reset case, with existing profile, having default spreadsheet config collection
            try {
                UUID spreadsheetConfigCollectionFromProfileUuid = studyConfigService.duplicateSpreadsheetConfigCollection(userProfileInfos.getSpreadsheetConfigCollectionId());
                studyEntity.setSpreadsheetConfigCollectionUuid(spreadsheetConfigCollectionFromProfileUuid);
                removeSpreadsheetConfigCollection(existingSpreadsheetConfigCollectionUuid);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate spreadsheet config collection with id '%s' from user/profile '%s/%s'. Using default collection",
                        userProfileInfos.getSpreadsheetConfigCollectionId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default collection below
            }
        }

        if (configCollection != null) {
            if (existingSpreadsheetConfigCollectionUuid == null) {
                UUID newUuid = studyConfigService.createSpreadsheetConfigCollection(configCollection);
                studyEntity.setSpreadsheetConfigCollectionUuid(newUuid);
            } else {
                studyConfigService.updateSpreadsheetConfigCollection(existingSpreadsheetConfigCollectionUuid, configCollection);
            }
        } else {
            // No config provided, use system default
            UUID defaultCollectionUuid = studyConfigService.createDefaultSpreadsheetConfigCollection();
            studyEntity.setSpreadsheetConfigCollectionUuid(defaultCollectionUuid);
            removeSpreadsheetConfigCollection(existingSpreadsheetConfigCollectionUuid);
        }

        return userProfileIssue;
    }

    private void removeSpreadsheetConfigCollection(@Nullable UUID spreadsheetConfigCollectionUuid) {
        if (spreadsheetConfigCollectionUuid != null) {
            try {
                studyConfigService.deleteSpreadsheetConfigCollection(spreadsheetConfigCollectionUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove spreadsheet config collection with uuid:" + spreadsheetConfigCollectionUuid, e);
            }
        }
    }

    @Transactional
    public String updateSpreadsheetConfigCollection(UUID studyUuid, UUID sourceCollectionUuid, boolean appendMode) {
        StudyEntity studyEntity = getStudy(studyUuid);
        // 2 modes: append the source collection to the existing one, or replace the whole existing collection
        String collectionDto = appendMode ? appendSpreadsheetConfigCollection(studyEntity, sourceCollectionUuid) :
                replaceSpreadsheetConfigCollection(studyEntity, sourceCollectionUuid);
        notificationService.emitSpreadsheetCollectionChanged(studyUuid, studyEntity.getSpreadsheetConfigCollectionUuid());
        return collectionDto;
    }

    private String appendSpreadsheetConfigCollection(StudyEntity studyEntity, UUID sourceCollectionUuid) {
        final UUID existingStudyCollection = studyEntity.getSpreadsheetConfigCollectionUuid();
        if (existingStudyCollection == null) {
            return replaceSpreadsheetConfigCollection(studyEntity, sourceCollectionUuid);
        }
        studyConfigService.appendSpreadsheetConfigCollection(existingStudyCollection, sourceCollectionUuid);
        return studyConfigService.getSpreadsheetConfigCollection(existingStudyCollection);
    }

    private String replaceSpreadsheetConfigCollection(StudyEntity studyEntity, UUID sourceCollectionUuid) {
        // Duplicate the source collection to get a new one
        UUID newCollectionUuid = studyConfigService.duplicateSpreadsheetConfigCollection(sourceCollectionUuid);
        final UUID existingStudyCollection = studyEntity.getSpreadsheetConfigCollectionUuid();
        if (existingStudyCollection != null) {
            // delete the old collection if it exists
            try {
                studyConfigService.deleteSpreadsheetConfigCollection(existingStudyCollection);
            } catch (Exception e) {
                LOGGER.error("Could not remove spreadsheet config collection with uuid:" + existingStudyCollection, e);
                // Continue with the new collection even if deletion failed
            }
        }
        studyEntity.setSpreadsheetConfigCollectionUuid(newCollectionUuid);
        return studyConfigService.getSpreadsheetConfigCollection(newCollectionUuid);
    }

    public boolean shouldApplyModifications(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return Optional.ofNullable(studyEntity.getVoltageInitParameters())
                .map(StudyVoltageInitParametersEntity::shouldApplyModifications)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public UUID getFirstNetworkUuid(UUID studyUuid) {
        return studyRepository.findWithRootNetworksById(studyUuid)
                .map(study -> study.getFirstRootNetwork().getNetworkUuid())
                .orElseThrow(() -> new StudyException(NOT_FOUND, STUDY_NOT_FOUND));
    }

    // --- Dynamic Mapping service methods BEGIN --- //

    public String getNetworkValuesFromStudy(UUID studyUuid) {
        UUID networkUuid = this.self.getFirstNetworkUuid(studyUuid);
        return dynamicMappingService.getNetworkValues(networkUuid);
    }

    public String getNetworkMatchesFromStudy(UUID studyUuid, String ruleToMatch) {
        UUID networkUuid = this.self.getFirstNetworkUuid(studyUuid);
        return dynamicMappingService.getNetworkMatches(networkUuid, ruleToMatch);
    }

    // --- Dynamic Mapping service methods END --- //

    @Transactional(readOnly = true)
    public String exportFilterFromFirstRootNetwork(UUID studyUuid, UUID filterUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return filterService.exportFilter(studyEntity.getFirstRootNetwork().getNetworkUuid(), filterUuid);
    }

    @Transactional(readOnly = true)
    public String evaluateFiltersFromFirstRootNetwork(UUID studyUuid, String filters) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return filterService.evaluateFilters(studyEntity.getFirstRootNetwork().getNetworkUuid(), filters);
    }

    private List<RootNetworkInfos> getStudyRootNetworksInfos(UUID studyUuid) {
        List<RootNetworkEntity> rootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);
        // using the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        rootNetworkService.getRootNetworkInfosWithLinksInfos(studyUuid);
        return rootNetworkEntities.stream().map(rootNetworkEntity -> rootNetworkEntity.toDto(objectMapper)).toList();
    }

    @Transactional(readOnly = true)
    public List<NodeAlias> getNodeAliases(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        List<NodeAlias> nodeAliases = new ArrayList<>();
        Map<UUID, AbstractNode> nodesByUuid = networkModificationTreeService.getAllStudyNodesByUuid(studyUuid);
        studyEntity.getNodeAliases().forEach(nodeAliasEmbeddable -> {
            if (nodeAliasEmbeddable.getNodeId() != null) {
                AbstractNode node = nodesByUuid.get(nodeAliasEmbeddable.getNodeId());
                nodeAliases.add(new NodeAlias(node.getId(), nodeAliasEmbeddable.getName(), node.getName()));
            } else {
                nodeAliases.add(new NodeAlias(null, nodeAliasEmbeddable.getName(), null));
            }
        });
        return nodeAliases;
    }

    @Transactional
    public void updateNodeAliases(UUID studyUuid, List<NodeAlias> nodeAliases, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        //Reset alias values for given study to keep data in sync
        studyEntity.setNodeAliases(null);
        if (!CollectionUtils.isEmpty(nodeAliases)) {
            List<NodeAliasEmbeddable> newNodeAliases = new ArrayList<>();
            nodeAliases.forEach(nodeAlias -> {
                String aliasName = nodeAlias.alias();
                if (!StringUtils.isEmpty(nodeAlias.name())) {
                    newNodeAliases.add(new NodeAliasEmbeddable(aliasName, nodeAlias.id()));
                } else {
                    newNodeAliases.add(new NodeAliasEmbeddable(aliasName, null));
                }
            });
            studyEntity.setNodeAliases(newNodeAliases);
        }
        notificationService.emitSpreadsheetNodeAliasesChanged(studyUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public UUID createColumn(UUID studyUuid, UUID configUuid, String columnInfos) {
        UUID newColId = studyConfigService.createColumn(configUuid, columnInfos);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
        return newColId;
    }

    public void updateColumn(UUID studyUuid, UUID configUuid, UUID columnUuid, String columnInfos) {
        studyConfigService.updateColumn(configUuid, columnUuid, columnInfos);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public void deleteColumn(UUID studyUuid, UUID configUuid, UUID columnUuid) {
        studyConfigService.deleteColumn(configUuid, columnUuid);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public void duplicateColumn(UUID studyUuid, UUID configUuid, UUID columnUuid) {
        studyConfigService.duplicateColumn(configUuid, columnUuid);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public void reorderColumns(UUID studyUuid, UUID configUuid, List<UUID> columnOrder) {
        studyConfigService.reorderColumns(configUuid, columnOrder);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public void updateColumnsStates(UUID studyUuid, UUID configUuid, String columnStateUpdates) {
        studyConfigService.updateColumnsStates(configUuid, columnStateUpdates);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public void setGlobalFilters(UUID studyUuid, UUID configUuid, String globalFilters) {
        studyConfigService.setGlobalFilters(configUuid, globalFilters);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    @Transactional
    public void setGlobalFiltersForComputationResult(UUID studyUuid, String computationType, String globalFilters) {
        UUID computationResultFiltersId = getComputationResultFiltersId(studyUuid);
        studyConfigService.setGlobalFiltersForComputationResult(computationResultFiltersId, computationType, globalFilters);
        notificationService.emitComputationResultGlobalFilterChanged(studyUuid, computationType);
    }

    @Transactional
    public void updateColumns(UUID studyUuid, String computationType, String computationSubType, String columnInfos) {
        UUID computationResultFiltersId = getComputationResultFiltersId(studyUuid);
        studyConfigService.updateColumns(computationResultFiltersId, computationType, computationSubType, columnInfos);
        notificationService.emitComputationResultColumnFilterChanged(studyUuid, computationType, computationSubType);
    }

    @Transactional
    public void resetComputationResultFilters(UUID studyUuid, String computationType, String computationSubType) {
        UUID computationResultFiltersId = getComputationResultFiltersId(studyUuid);
        studyConfigService.resetComputationResultFilters(computationResultFiltersId, computationType, computationSubType);
        notificationService.emitComputationResultGlobalFilterChanged(studyUuid, computationType);
        notificationService.emitComputationResultColumnFilterChanged(studyUuid, computationType, computationSubType);
    }

    public UUID getComputationResultFiltersId(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID computationResultFiltersId = studyEntity.getComputationResultFiltersUuid();
        if (Objects.isNull(computationResultFiltersId)) {
            computationResultFiltersId = studyConfigService.createComputationResultsFiltersId();
            studyEntity.setComputationResultFiltersUuid(computationResultFiltersId);
        }
        return computationResultFiltersId;
    }

    public void renameSpreadsheetConfig(UUID studyUuid, UUID configUuid, String newName) {
        studyConfigService.renameSpreadsheetConfig(configUuid, newName);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public void updateSpreadsheetConfigSort(UUID studyUuid, UUID configUuid, String sortConfig) {
        studyConfigService.updateSpreadsheetConfigSort(configUuid, sortConfig);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public void updateSpreadsheetConfig(UUID studyUuid, UUID configUuid, String spreadsheetConfigInfos) {
        studyConfigService.updateSpreadsheetConfig(configUuid, spreadsheetConfigInfos);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    public UUID addSpreadsheetConfigToCollection(UUID studyUuid, UUID collectionUuid, String configurationDto) {
        UUID newConfigId = studyConfigService.addSpreadsheetConfigToCollection(collectionUuid, configurationDto);
        notificationService.emitSpreadsheetCollectionChanged(studyUuid, collectionUuid);
        return newConfigId;
    }

    public void removeSpreadsheetConfigFromCollection(UUID studyUuid, UUID collectionUuid, UUID configUuid) {
        studyConfigService.removeSpreadsheetConfigFromCollection(collectionUuid, configUuid);
        notificationService.emitSpreadsheetCollectionChanged(studyUuid, collectionUuid);
    }

    public void reorderSpreadsheetConfigs(UUID studyUuid, UUID collectionUuid, List<UUID> newOrder) {
        studyConfigService.reorderSpreadsheetConfigs(collectionUuid, newOrder);
        notificationService.emitSpreadsheetCollectionChanged(studyUuid, collectionUuid);
    }

    public void resetFilters(UUID studyUuid, UUID configUuid) {
        studyConfigService.resetFilters(configUuid);
        notificationService.emitSpreadsheetConfigChanged(studyUuid, configUuid);
    }

    private void removeWorkspacesConfig(@Nullable UUID workspacesConfigUuid) {
        if (workspacesConfigUuid != null) {
            try {
                studyConfigService.deleteWorkspacesConfig(workspacesConfigUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove workspaces config with uuid:" + workspacesConfigUuid, e);
            }
        }
    }

    public Optional<SpreadsheetParameters> getSpreadsheetParameters(@NonNull final UUID studyUuid) {
        return this.studyRepository.findById(studyUuid).map(StudyEntity::getSpreadsheetParameters).map(SpreadsheetParametersEntity::toDto);
    }

    /**
     * @return {@code true} if studyUuid exist, {@code false} otherwise
     */
    @Transactional
    public boolean updateSpreadsheetParameters(UUID studyUuid, SpreadsheetParameters spreadsheetParameters) {
        final Optional<StudyEntity> studyEntity = this.studyRepository.findById(studyUuid);
        studyEntity.map(StudyEntity::getSpreadsheetParameters).ifPresent(entity -> {
            if (entity.update(spreadsheetParameters)) {
                this.studyRepository.save(studyEntity.get());
                this.notificationService.emitSpreadsheetParametersChange(studyUuid);
            }
        });
        return studyEntity.isPresent();
    }

    public UUID consumeQuota(ComputationType computationType, String userId) {
        if (!shouldCheckOperationQuotas) {
            return null;
        }
        QuotaType quotaType = QuotaType.mapFromComputationType(computationType);
        UUID quotaId = userAdminService.consumeQuota(userId, quotaType);
        notificationService.emitQuotaChange(userId, quotaType);
        return quotaId;
    }

    public void releaseQuotaOnFailure(String userId, UUID quotaId) {
        if (quotaId == null) {
            return;
        }
        userAdminService.releaseFailedQuotaId(userId, quotaId);
    }

    public Boolean getOperationQuotaStatus() {
        return shouldCheckOperationQuotas;
    }

    @Transactional(readOnly = true)
    public TreeExportInfos buildTreeExport(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, STUDY_NOT_FOUND));
        List<RootNetworkInfos> rootNetworkInfosList = rootNetworkService.getRootNetworkInfosWithLinksInfos(studyUuid);
        if (rootNetworkInfosList.isEmpty()) {
            throw new StudyException(NOT_FOUND, "No root network found for study " + studyUuid);
        }
        // studyEntity.getRootNetworks() is ordered by the "index" column (@OrderColumn) in the root_network table
        List<UUID> orderedRootNetworkIds = studyEntity.getRootNetworks().stream().map(RootNetworkEntity::getId).toList();
        List<RootNetworkExportInfos> rootNetworks = rootNetworkInfosList.stream()
                .map(rootNetworkInfos -> toRootNetworkExportInfos(rootNetworkInfos, orderedRootNetworkIds.indexOf(rootNetworkInfos.getId())))
                .toList();
        AbstractNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, null);
        NodeTreeExportInfos nodeTree = rootNode != null ? toNodeTreeExportInfos(rootNode) : null;
        return new TreeExportInfos(studyUuid, rootNetworks, nodeTree);
    }

    private RootNetworkExportInfos toRootNetworkExportInfos(RootNetworkInfos rootNetworkInfos, int index) {
        return new RootNetworkExportInfos(
                rootNetworkInfos.getName(),
                rootNetworkInfos.getTag(),
                index,
                new CaseInfos(rootNetworkInfos.getCaseInfos().getCaseUuid(), rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(),
                        rootNetworkInfos.getCaseInfos().getCaseName(), rootNetworkInfos.getCaseInfos().getCaseFormat()),
                rootNetworkInfos.getImportParameters()
        );
    }

    private NodeTreeExportInfos toNodeTreeExportInfos(AbstractNode node) {
        List<NodeTreeExportInfos> children = CollectionUtils.emptyIfNull(node.getChildren()).stream().map(this::toNodeTreeExportInfos).toList();
        UUID modificationGroupUuid = null;
        String nodeType = null;
        if (node instanceof NetworkModificationNode modificationNode) {
            modificationGroupUuid = modificationNode.getModificationGroupUuid();
            nodeType = modificationNode.getNodeType().name();
        }
        return new NodeTreeExportInfos(
                node.getName(),
                node.getType().name(),
                modificationGroupUuid,
                nodeType,
                children
        );
    }

    @Transactional
    public StudyEntity createStudyEntityWithTree(UUID studyUuid, String userId, NodeTreeExportInfos nodeTree) {
        UserProfileInfos userProfileInfos = getUserProfile(userId);
        ComputationParameterUUIDs computationParameterUUIDs = computationParametersService.createDefaultComputationParameters(userId, userProfileInfos);
        UUID networkVisualizationParametersUuid = studyConfigService.createDefaultNetworkVisualizationParameters(userId, userProfileInfos);
        UUID spreadsheetConfigCollectionUuid = studyConfigService.createDefaultSpreadsheetConfigCollection(userId, userProfileInfos);
        UUID workspacesConfigUuid = workspaceService.createWorkspacesConfig(userProfileInfos);
        StudyEntity studyEntity = studyRepository.save(buildStudyEntity(studyUuid, computationParameterUUIDs,
                networkVisualizationParametersUuid, spreadsheetConfigCollectionUuid, workspacesConfigUuid));
        UUID rootNodeUuid = networkModificationTreeService.createRoot(studyEntity).getIdNode();
        if (nodeTree != null) {
            CollectionUtils.emptyIfNull(nodeTree.children()).forEach(child -> createNodeRecursively(studyEntity, rootNodeUuid, child, userId));
        }
        studyInfosService.add(toCreatedStudyBasicInfos(studyEntity));
        return studyEntity;
    }

    private void createNodeRecursively(StudyEntity studyEntity, UUID parentNodeUuid, NodeTreeExportInfos exportNode, String userId) {
        NetworkModificationNodeType nodeType = NetworkModificationNodeType.valueOf(exportNode.nodeType());
        UUID newGroupUuid = exportNode.modificationGroupUuid();
        NetworkModificationNode newNode = networkModificationTreeService.createNode(
                studyEntity,
                parentNodeUuid,
                NetworkModificationNode.builder()
                        .name(exportNode.name())
                        .nodeType(nodeType)
                        // buildStatus intentionally left by default (NOT_BUILT):
                        .modificationGroupUuid(newGroupUuid)
                        .build(),
                InsertMode.CHILD,
                userId
        );
        CollectionUtils.emptyIfNull(exportNode.children()).forEach(child -> createNodeRecursively(studyEntity, newNode.getId(), child, userId));
    }
}
