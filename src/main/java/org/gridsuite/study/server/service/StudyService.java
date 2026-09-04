/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.loadflow.LoadFlowParameters;
import io.micrometer.common.util.StringUtils;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.filter.globalfilter.GlobalFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters.ComputationsInvalidationMode;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters.InvalidationMode;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.computation.ComputationParameterUUIDs;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;
import org.gridsuite.study.server.dto.networkexport.NodeExportInfos;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.sequence.NodeSequenceType;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationEventService;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisRestService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitRestService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.gridsuite.study.server.service.voltageinit.VoltageInitRestService;
import org.gridsuite.study.server.utils.ElementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.BUS_ID_TO_ICC_VALUES;
import static org.gridsuite.study.server.StudyConstants.CURRENT_LIMIT_VIOLATIONS_INFOS;
import static org.gridsuite.study.server.dto.ComputationType.*;
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
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
    private final UserAdminService userAdminService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;
    private final SingleLineDiagramService singleLineDiagramService;
    private final NetworkConversionService networkConversionService;
    private final GeoDataService geoDataService;
    private final NetworkMapService networkMapService;
    private final StudyConfigService studyConfigService;
    private final NadConfigService nadConfigService;
    private final FilterService filterService;
    private final ActionsService actionsService;
    private final CaseService caseService;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final DirectoryService directoryService;

    private final ComputationParametersService computationParametersService;
    private final LoadFlowRestService loadflowRestService;
    private final LoadFlowService loadFlowService;
    private final SecurityAnalysisRestService securityAnalysisRestService;
    private final DynamicMappingService dynamicMappingService;
    private final DynamicSimulationEventService dynamicSimulationEventService;
    private final ShortCircuitRestService shortCircuitRestService;
    private final VoltageInitRestService voltageInitRestService;

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
        ASYMMETRICAL_LOAD("AsymmetricalLoad");

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
        NetworkService networkStoreService,
        NetworkModificationService networkModificationService,
        ReportService reportService,
        UserAdminService userAdminService,
        StudyInfosService studyInfosService,
        EquipmentInfosService equipmentInfosService,
        NetworkModificationTreeService networkModificationTreeService,
        ObjectMapper objectMapper,
        StudyServerExecutionService studyServerExecutionService,
        NotificationService notificationService,
        LoadFlowRestService loadflowRestService,
        LoadFlowService loadFlowService,
        ShortCircuitRestService shortCircuitService,
        SingleLineDiagramService singleLineDiagramService,
        NetworkConversionService networkConversionService,
        GeoDataService geoDataService,
        NetworkMapService networkMapService,
        SecurityAnalysisRestService securityAnalysisRestService,
        ActionsService actionsService,
        CaseService caseService,
        DynamicMappingService dynamicMappingService,
        VoltageInitRestService voltageInitService,
        DynamicSimulationEventService dynamicSimulationEventService,
        StudyConfigService studyConfigService,
        NadConfigService nadConfigService,
        FilterService filterService,
        @Lazy StudyService studyService,
        RootNetworkService rootNetworkService,
        RootNetworkNodeInfoService rootNetworkNodeInfoService,
        DirectoryService directoryService,
        ComputationParametersService computationParametersService) {
        this.studyRepository = studyRepository;
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.networkStoreService = networkStoreService;
        this.networkModificationService = networkModificationService;
        this.reportService = reportService;
        this.userAdminService = userAdminService;
        this.studyInfosService = studyInfosService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.studyServerExecutionService = studyServerExecutionService;
        this.notificationService = notificationService;
        this.loadFlowService = loadFlowService;
        this.loadflowRestService = loadflowRestService;
        this.shortCircuitRestService = shortCircuitService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.geoDataService = geoDataService;
        this.networkMapService = networkMapService;
        this.securityAnalysisRestService = securityAnalysisRestService;
        this.actionsService = actionsService;
        this.caseService = caseService;
        this.dynamicMappingService = dynamicMappingService;
        this.voltageInitRestService = voltageInitService;
        this.dynamicSimulationEventService = dynamicSimulationEventService;
        this.studyConfigService = studyConfigService;
        this.nadConfigService = nadConfigService;
        this.filterService = filterService;
        this.self = studyService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.directoryService = directoryService;
        this.computationParametersService = computationParametersService;
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

    @Transactional(readOnly = true)
    public void assertIsRootNetworkAndNodeInStudy(@NonNull final UUID studyUuid, @NonNull final UUID rootNetworkId, @NonNull final UUID nodeUuid) {
        this.rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkId);
        if (!studyUuid.equals(this.networkModificationTreeService.getStudyUuidForNodeId(nodeUuid))) {
            throw new StudyException(NOT_FOUND, "Node not found");
        }
    }

    @Transactional
    public void deleteRootNetworks(UUID studyUuid, List<UUID> rootNetworksUuids, String userId) {
        assertIsStudyExist(studyUuid);
        StudyEntity studyEntity = getStudy(studyUuid);
        List<RootNetworkEntity> allRootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);
        if (rootNetworksUuids.size() >= allRootNetworkEntities.size()) {
            throw new StudyException(ROOT_NETWORK_DELETE_FORBIDDEN);
        }
        if (!allRootNetworkEntities.stream().map(RootNetworkEntity::getId).collect(Collectors.toSet()).containsAll(rootNetworksUuids)) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        notificationService.emitRootNetworksDeletionStarted(studyUuid, rootNetworksUuids);

        rootNetworkService.deleteRootNetworks(studyEntity, rootNetworksUuids.stream());

        notificationService.emitRootNetworksUpdated(studyUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public RootNetworkRequestInfos createRootNetworkRequest(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        rootNetworkService.assertCanCreateRootNetwork(studyUuid, rootNetworkInfos.getName(), rootNetworkInfos.getTag());
        StudyEntity studyEntity = getStudy(studyUuid);

        rootNetworkInfos.setId(UUID.randomUUID());
        RootNetworkRequestEntity rootNetworkCreationRequestEntity = rootNetworkService.insertCreationRequest(studyEntity.getId(), rootNetworkInfos, userId);
        try {
            UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), true);
            rootNetworkInfos.getCaseInfos().setCaseUuid(clonedCaseUuid);
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParameters(), CaseImportAction.ROOT_NETWORK_CREATION, UUID.randomUUID());
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkCreationRequestEntity);
            throw e;
        }

        notificationService.emitRootNetworksUpdated(studyUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
        return rootNetworkCreationRequestEntity.toDto();
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
            if (studyEntity.getRootNetworks().size() > 1) {
                studyEntity.setMonoRoot(false);
            }
        } else {
            rootNetworkService.deleteRootNetworks(studyEntity, List.of(rootNetworkInfos));
        }
        notificationService.emitRootNetworksUpdated(studyUuid);
    }

    private void updateRootNetworkBasicInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, boolean updateCase) {
        rootNetworkService.updateRootNetwork(rootNetworkInfos, updateCase);
        postRootNetworkUpdate(studyUuid, rootNetworkInfos.getId(), updateCase);
    }

    @Transactional
    public void updateRootNetworkRequest(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        rootNetworkService.assertCanModifyRootNetwork(studyUuid, rootNetworkInfos.getId(), rootNetworkInfos.getName(), rootNetworkInfos.getTag());
        StudyEntity studyEntity = getStudy(studyUuid);

        if (rootNetworkInfos.hasCaseToImport()) {
            RootNetworkRequestEntity requestEntity = rootNetworkService.insertModificationRequest(studyEntity.getId(), rootNetworkInfos, userId);
            updateRootNetworkCaseInfos(studyEntity.getId(), rootNetworkInfos, userId, requestEntity);
        } else {
            updateRootNetworkBasicInfos(studyEntity.getId(), rootNetworkInfos, false);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void updateRootNetworkCaseInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId, RootNetworkRequestEntity rootNetworkModificationRequestEntity) {
        UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), true);
        rootNetworkInfos.getCaseInfos().setCaseUuid(clonedCaseUuid);
        try {
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParameters(), CaseImportAction.ROOT_NETWORK_MODIFICATION, UUID.randomUUID());
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkModificationRequestEntity);
            throw e;
        }
    }

    @Transactional
    public void modifyRootNetwork(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        invalidateStudyRootNetwork(studyUuid, rootNetworkInfos.getId(), userId, true);
        updateRootNetworkBasicInfos(studyUuid, rootNetworkInfos, true);
    }

    private void postRootNetworkUpdate(UUID studyUuid, UUID rootNetworkUuid, boolean updateCase) {
        if (updateCase) {
            Optional<RootNetworkRequestEntity> rootNetworkModificationRequestEntityOpt = rootNetworkService.getRootNetworkRequest(rootNetworkUuid);
            rootNetworkModificationRequestEntityOpt.ifPresent(rootNetworkService::deleteRootNetworkRequest);
            notificationService.emitRootNetworkUpdated(studyUuid, rootNetworkUuid);
        } else {
            notificationService.emitRootNetworksUpdated(studyUuid);
        }
    }

    /**
     * Recreates study network from <caseUuid> and <importParameters>
     * @param caseUuid
     * @param userId
     * @param studyUuid
     * @param importParameters
     */
    public void recreateNetwork(UUID caseUuid, String userId, UUID studyUuid, UUID rootNetworkUuid, String caseFormat, Map<String, Object> importParameters) {
        RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().caseInfos(new CaseInfos(caseUuid,
                caseUuid, null, caseFormat)).id(rootNetworkUuid).build();
        recreateNetwork(rootNetworkInfos, studyUuid, userId, importParameters, false, UUID.randomUUID());
    }

    /**
     * Recreates study network from existing case and import parameters
     * @param userId
     * @param studyUuid
     */
    public void recreateNetwork(String userId, UUID studyUuid, UUID rootNetworkUuid, String caseFormat) {
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        if (rootNetwork.getLoadStatus() == RootNetworkLoadStatus.LOADING) {
            LOGGER.warn("Root network '{}' is already loading, skipping recreateNetwork", rootNetworkUuid);
            return;
        }
        rootNetworkService.updateNetworkLoadStatus(rootNetworkUuid, RootNetworkLoadStatus.LOADING);
        UUID caseUuid = rootNetwork.getCaseUuid();
        UUID originalCaseUuid = rootNetwork.getOriginalCaseUuid();
        RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().id(rootNetworkUuid).caseInfos(new CaseInfos(caseUuid, originalCaseUuid, null, caseFormat)).build();

        recreateNetwork(rootNetworkInfos, studyUuid, userId, null, true, rootNetwork.getReportUuid());
    }

    private void recreateNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String userId, Map<String, Object> importParameters, boolean shouldLoadPreviousImportParameters, UUID reportId) {
        caseService.assertCaseExists(rootNetworkInfos.getCaseInfos().getCaseUuid());
        Map<String, Object> importParametersToUse = shouldLoadPreviousImportParameters
            ? new HashMap<>(rootNetworkService.getImportParameters(rootNetworkInfos.getId()))
            : importParameters;

        persistNetwork(rootNetworkInfos, studyUuid, null, userId, importParametersToUse, CaseImportAction.NETWORK_RECREATION, reportId);
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
                    reindexRootNetwork(duplicatedStudy, rootNetworkEntity.getId())
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

    private UUID getNodeUuidToSearchIn(UUID initialNodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = initialNodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuiltUuid(initialNodeUuid, rootNetworkUuid);
        }
        return nodeUuidToSearchIn;
    }

    public List<EquipmentInfos> searchEquipments(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userInput,
                                                 @NonNull EquipmentInfosService.FieldSelector fieldSelector, String equipmentType,
                                                 boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return equipmentInfosService.searchEquipments(networkUuid, variantId, userInput, fieldSelector, equipmentType);
    }

    public List<ModificationsSearchResultByNode> searchModifications(@NonNull UUID rootNetworkUuid, @NonNull String userInput) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        Map<UUID, Object> modificationsByGroup = networkModificationService.searchModifications(networkUuid, userInput);
        return networkModificationTreeService.getNetworkModificationsByNodeInfos(modificationsByGroup);
    }

    @Transactional
    public String getExportedNetworkModifications(UUID studyUuid, UUID nodeUuid) {
        if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);

        return networkModificationService.getModificationsToExport(groupId);
    }

    private Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        DeleteStudyInfos deleteStudyInfos = null;
        if (studyCreationRequestEntity.isEmpty() && studyEntity.isPresent()) {
            List<RootNetworkInfos> rootNetworkInfos = getStudyRootNetworksInfos(studyUuid);
            // get all modification groups and nodes related to the study
            List<NetworkModificationNodeInfoEntity> allStudyNetworkModificationNodeInfo = networkModificationTreeService.getAllStudyNetworkModificationNodeInfo(studyUuid);
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
                    .forEach(groupUuidNodeUuid -> deleteModificationsFromGroup(groupUuidNodeUuid, userId)));

            LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));

        }
    }

    private void deleteModificationsFromGroup(Pair<UUID, UUID> groupUuidNodeUuid, String userId) {
        // fetch the references data in order to remove those references from directory-server
        List<ReferenceData> referencesToBeDeleted = networkModificationService.getReferencesFromGroup(groupUuidNodeUuid.getFirst());
        removeReferences(referencesToBeDeleted, userId, groupUuidNodeUuid.getSecond());

        networkModificationService.deleteModifications(groupUuidNodeUuid.getFirst());
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
        networkModificationTreeService.duplicateStudyNodes(newStudyEntity, sourceStudy);
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
            .build());
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid, String firstRootNetworkName) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(studyUuid, firstRootNetworkName);
        notificationService.emitStudyCreationStarted(newStudy.getId(), userId);
        return newStudy;
    }

    public byte[] generateVoltageLevelSvg(String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.generateVoltageLevelSvg(networkUuid, variantId, voltageLevelId, populateSldRequestInfos(sldRequestInfos, voltageLevelId, nodeUuid, rootNetworkUuid));
        } else {
            return null;
        }
    }

    public String generateVoltageLevelSvgAndMetadata(String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.generateVoltageLevelSvgAndMetadata(networkUuid, variantId, voltageLevelId, populateSldRequestInfos(sldRequestInfos, voltageLevelId, nodeUuid,
                    rootNetworkUuid));
        } else {
            return null;
        }
    }

    private Map<String, Object> populateSldRequestInfos(Map<String, Object> sldRequestInfos, String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid) {
        List<CurrentLimitViolationInfos> violations = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
        Map<String, Double> busIdToIccValues = getBusIdToIccValuesMap(voltageLevelId, nodeUuid, rootNetworkUuid);
        sldRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, violations);
        sldRequestInfos.put(BUS_ID_TO_ICC_VALUES, busIdToIccValues);
        return sldRequestInfos;
    }

    private Map<String, Double> getBusIdToIccValuesMap(String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid) {
        UUID shortCircuitResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, SHORT_CIRCUIT);
        return shortCircuitResultUuid != null ?
            shortCircuitRestService.getVoltageLevelIccValues(shortCircuitResultUuid, voltageLevelId) : Map.of();
    }

    private void persistNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String variantId, String userId,
                                Map<String, Object> importParameters, CaseImportAction caseImportAction, UUID reportId) {
        networkConversionService.persistNetwork(rootNetworkInfos, studyUuid, variantId, userId, reportId, importParameters, caseImportAction);
    }

    public String getLinesGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> linesIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getLinesGraphics(networkUuid, variantId, linesIds);
    }

    public String getSubstationsGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getSubstationsGraphics(networkUuid, variantId, substationsIds);
    }

    @Transactional
    public String getNetworkElementsInfos(UUID studyUuid,
                                          UUID nodeUuid,
                                          UUID rootNetworkUuid,
                                          List<String> substationsIds,
                                          String infoType,
                                          String elementType,
                                          boolean inUpstreamBuiltParentNode,
                                          List<Double> nominalVoltages) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = loadFlowService.getLoadFlowParameters(studyEntity);
        return networkMapService.getElementsInfos(
            rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
            substationsIds,
            elementType,
            nominalVoltages,
            infoType,
            getOptionalParameters(elementType, studyEntity, loadFlowParameters));
    }

    @Transactional
    public String getNetworkElementInfos(UUID studyUuid,
                                         UUID nodeUuid,
                                         UUID rootNetworkUuid,
                                         String elementType,
                                         InfoTypeParameters infoTypeParameters,
                                         String elementId,
                                         boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = loadFlowService.getLoadFlowParameters(studyEntity);
        return networkMapService.getElementInfos(
            rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
            elementType,
            infoTypeParameters.getInfoType(),
            getSingleElementOptionalParameters(elementId, elementType, studyEntity, nodeUuid, rootNetworkUuid, loadFlowParameters),
            elementId);
    }

    private Map<String, String> getSingleElementOptionalParameters(String elementId, String elementType, StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid,
            LoadFlowParameters loadFlowParameters) {
        Map<String, String> additionalParameters = getOptionalParameters(elementType, studyEntity, loadFlowParameters);

        if ("voltage_level".equalsIgnoreCase(elementType)) {
            try {
                additionalParameters.put(
                    InfoTypeParameters.QUERY_PARAM_BUS_ID_TO_ICC_VALUES,
                    UriUtils.encode(objectMapper.writeValueAsString(getBusIdToIccValuesMap(elementId, nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8)
                );
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        return additionalParameters;
    }

    private static Map<String, String> getOptionalParameters(String elementType, StudyEntity studyEntity, LoadFlowParameters loadFlowParameters) {
        Map<String, String> additionalParameters = new HashMap<>();
        additionalParameters.put(InfoTypeParameters.QUERY_PARAM_DC_POWERFACTOR, String.valueOf(loadFlowParameters.getDcPowerFactor()));
        switch (elementType.toLowerCase()) {
            case "branch" -> additionalParameters.put(
                InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBranchOperationalLimitGroup()));
            case "line" -> additionalParameters.put(
                InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadLineOperationalLimitGroup()));
            case "two_windings_transformer" -> additionalParameters.put(
                InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadTwtOperationalLimitGroup()));
            case "generator" -> additionalParameters.put(
                InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadGeneratorRegulatingTerminal()));
            case "battery" -> additionalParameters.put(
                    InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                    String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBatteryRegulatingTerminal()));
            case "bus" -> additionalParameters.put(
                InfoTypeParameters.QUERY_PARAM_LOAD_NETWORK_COMPONENTS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBusNetworkComponents()));
        }
        return additionalParameters;
    }

    public String getNetworkCountries(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getCountries(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid));
    }

    public String getNetworkNominalVoltages(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getNominalVoltages(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid));
    }

    public String getVoltageLevelEquipments(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String voltageLevelId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        String equipmentPath = "voltage-levels" + StudyConstants.DELIMITER + voltageLevelId + StudyConstants.DELIMITER + "equipments";
        return networkMapService.getEquipmentsMapData(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                null, equipmentPath);
    }

    public String getHvdcLineShuntCompensators(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String hvdcId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return networkMapService.getHvdcLineShuntCompensators(networkUuid, variantId, hvdcId);
    }

    public String getBranchOr3WTVoltageLevelId(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String equipmentId, ThreeSides side) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return networkMapService.getBranchOr3WTVoltageLevelId(networkUuid, variantId, equipmentId, side);
    }

    @Transactional
    public String getAllMapData(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = loadFlowService.getLoadFlowParameters(studyEntity);
        Map<String, Map<String, String>> optionalParameters = new HashMap<>();
        Stream.of(
            String.valueOf(ElementType.BRANCH),
            String.valueOf(ElementType.LINE),
            String.valueOf(ElementType.TIE_LINE),
            String.valueOf(ElementType.TWO_WINDINGS_TRANSFORMER),
            String.valueOf(ElementType.BATTERY)
            ).forEach(type -> optionalParameters.put(
                type,
                new HashMap<>(Map.of(InfoTypeParameters.QUERY_PARAM_DC_POWERFACTOR, String.valueOf(loadFlowParameters.getDcPowerFactor())))
            ));
        optionalParameters.get(String.valueOf(ElementType.BRANCH)).put(
            InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
            String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBranchOperationalLimitGroup()));
        optionalParameters.get(String.valueOf(ElementType.LINE)).put(
            InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
            String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadLineOperationalLimitGroup()));
        optionalParameters.get(String.valueOf(ElementType.TWO_WINDINGS_TRANSFORMER)).put(
            InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
            String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadTwtOperationalLimitGroup()));
        optionalParameters.put(String.valueOf(ElementType.GENERATOR),
            Map.of(
                InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadGeneratorRegulatingTerminal())));
        optionalParameters.put(String.valueOf(ElementType.BATTERY),
                Map.of(
                        InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBatteryRegulatingTerminal())));
        optionalParameters.put(String.valueOf(ElementType.BUS),
            Map.of(
                InfoTypeParameters.QUERY_PARAM_LOAD_NETWORK_COMPONENTS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBusNetworkComponents())));
        return networkMapService.getAllElementsInfos(
            rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid),
            substationsIds,
            optionalParameters);
    }

    public UUID exportNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, NodeExportInfos exportInfos, String format, CompressionType compression, String userId, String parametersJson) {
        // Checks if we can write on target directory in gridexplore
        if (exportInfos.exportToGridExplore()) {
            directoryService.checkPermission(List.of(), exportInfos.directoryUuid(), userId, PermissionType.WRITE, false);
            if (directoryService.elementExists(exportInfos.directoryUuid(), exportInfos.fileName(), DirectoryService.CASE)) {
                throw new StudyException(ELEMENT_ALREADY_EXISTS, "export file name " + exportInfos.fileName() + " already exists in directory", Map.of("fileName", exportInfos.fileName()));
            }
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID exportUuid = networkConversionService.exportNetwork(networkUuid, studyUuid, variantId,
            new NodeExportInfos(exportInfos.exportToGridExplore(), exportInfos.directoryUuid(), exportInfos.fileName(), exportInfos.description()), format, compression, userId, parametersJson);

        networkModificationTreeService.updateExportNetworkStatus(nodeUuid, exportUuid, ExportNetworkStatus.RUNNING);
        return exportUuid;
    }

    @Transactional(readOnly = true)
    public void assertIsNodeNotReadOnly(UUID nodeUuid) {
        Boolean isReadOnly = networkModificationTreeService.isReadOnly(nodeUuid);
        if (Boolean.TRUE.equals(isReadOnly)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanRunOnConstructionNode(UUID studyUuid, UUID nodeUuid, List<String> forbiddenProvidersOnConstructionNode, Function<UUID, String> providerGetter) {
        if (networkModificationTreeService.isConstructionNode(nodeUuid)) {
            String provider = providerGetter.apply(studyUuid);
            if (forbiddenProvidersOnConstructionNode.contains(provider)) {
                throw new StudyException(NOT_ALLOWED, provider + " must run only from a security type node !");
            }
        }
    }

    public void assertIsNodeExist(UUID studyUuid, UUID nodeUuid) {
        boolean exists = networkModificationTreeService.getAllNodes(studyUuid).stream()
                .anyMatch(nodeEntity -> nodeUuid.equals(nodeEntity.getIdNode()));

        if (!exists) {
            throw new StudyException(NOT_FOUND, "Node not found");
        }
    }

    public void assertIsStudyExist(UUID studyUuid) {
        boolean exists = studyRepository.existsById(studyUuid);
        if (!exists) {
            throw new StudyException(NOT_FOUND, "Node not found");
        }
    }

    public void assertIsStudyAndNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        assertIsNodeExist(studyUuid, nodeUuid);
    }

    public void assertRootNodeOrBuiltNode(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        if (!(networkModificationTreeService.getStudyRootNodeUuid(studyUuid).equals(nodeUuid)
                || networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt())) {
            throw new StudyException(NODE_NOT_BUILT);
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

    public ContingencyCount getContingencyCount(UUID studyUuid, List<UUID> contingencyListIds, UUID nodeUuid, UUID rootNetworkUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListIds);
        Objects.requireNonNull(nodeUuid);

        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return actionsService.getContingencyCount(networkuuid, variantId, contingencyListIds);
    }

    public List<LimitViolationInfos> getLimitViolations(@NonNull UUID nodeUuid, UUID rootNetworkUuid, String filters, String globalFilters, Sort sort) {
        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        return loadflowRestService.getLimitViolations(resultUuid, filters, globalFilters, sort, networkuuid, variantId);
    }

    public byte[] generateSubstationSvg(String substationId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            List<CurrentLimitViolationInfos> violations = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
            sldRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, violations);
            return singleLineDiagramService.generateSubstationSvg(networkUuid, variantId, substationId, sldRequestInfos);
        } else {
            return null;
        }
    }

    public String generateSubstationSvgAndMetadata(String substationId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            List<CurrentLimitViolationInfos> violations = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
            sldRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, violations);
            return singleLineDiagramService.generateSubstationSvgAndMetadata(networkUuid, variantId, substationId, sldRequestInfos);
        } else {
            return null;
        }
    }

    public String generateNetworkAreaDiagram(UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> nadRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            List<CurrentLimitViolationInfos> currentLimitViolationInfos = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
            nadRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, currentLimitViolationInfos);
            return singleLineDiagramService.generateNetworkAreaDiagram(networkUuid, variantId, nadRequestInfos);
        } else {
            return null;
        }
    }

    private void removeNadConfigs(List<UUID> nadConfigUuids) {
        try {
            nadConfigService.deleteNadConfigs(nadConfigUuids);
        } catch (Exception e) {
            LOGGER.error("Could not remove NAD configs with uuids:" + nadConfigUuids, e);
        }
    }

    private StudyEntity updateRootNetworkIndexationStatus(StudyEntity studyEntity, RootNetworkEntity rootNetworkEntity, RootNetworkIndexationStatus indexationStatus) {
        rootNetworkEntity.setIndexationStatus(indexationStatus);
        notificationService.emitRootNetworkIndexationStatusChanged(studyEntity.getId(), rootNetworkEntity.getId(), indexationStatus);
        return studyEntity;
    }

    public StudyEntity updateRootNetworkIndexationStatus(UUID studyUuid, UUID rootNetworkUuid, RootNetworkIndexationStatus indexationStatus) {
        return updateRootNetworkIndexationStatus(getStudy(studyUuid), rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")),
                indexationStatus);
    }

    private StudyEntity saveStudyThenCreateBasicTree(UUID studyUuid, NetworkInfos networkInfos,
                                                    CaseInfos caseInfos, ComputationParameterUUIDs computationParameterUUIDs,
                                                    UUID networkVisualizationParametersUuid, UUID spreadsheetConfigCollectionUuid,
                                                    UUID workspacesConfigUuid, Map<String, Object> importParameters, UUID importReportUuid) {

        StudyEntity studyEntity = StudyEntity.builder()
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

    public List<String> getResultEnumValues(UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType, String enumName) {
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(enumName);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, computationType);
        if (resultUuid != null) {
            return switch (computationType) {
                case LOAD_FLOW -> loadflowRestService.getEnumValues(enumName, resultUuid);
                case SECURITY_ANALYSIS -> securityAnalysisRestService.getEnumValues(enumName, resultUuid);
                case SHORT_CIRCUIT, SHORT_CIRCUIT_ONE_BUS -> shortCircuitRestService.getEnumValues(enumName, resultUuid);
                default -> throw new StudyException(NOT_ALLOWED);
            };
        } else {
            return new ArrayList<>();
        }
    }

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(UUID studyUuid, String firstRootNetworkName) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid, firstRootNetworkName);
        return studyCreationRequestRepository.save(studyCreationRequestEntity);
    }

    @Transactional
    public void createNetworkModification(UUID studyUuid, UUID nodeUuid, String createModificationAttributes, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            List<RootNetworkEntity> studyRootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), nodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();

            NetworkModificationsResult networkModificationResults = networkModificationService.createModification(groupUuid, Pair.of(createModificationAttributes, modificationApplicationContexts));

            if (networkModificationResults != null && networkModificationResults.modificationResults() != null) {
                int index = 0;
                // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
                for (Optional<NetworkModificationResult> modificationResultOpt : networkModificationResults.modificationResults()) {
                    if (modificationResultOpt.isPresent() && studyRootNetworkEntities.get(index) != null) {
                        emitNetworkModificationImpacts(studyUuid, nodeUuid, studyRootNetworkEntities.get(index).getId(), modificationResultOpt.get());
                    }
                    index++;
                }
            }
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            networkModificationService.updateModification(updateModificationAttributes, modificationUuid);
            invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public String getVoltageLevelSubstationId(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        return networkMapService.getVoltageLevelSubstationId(networkUuid, variantId, voltageLevelId);
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId,
                                                                        String busPath) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return networkMapService.getVoltageLevelBusesOrBusbarSections(networkUuid, variantId, voltageLevelId, busPath);
    }

    public String getVoltageLevelTopologyInfos(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId,
                                               String path) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return networkMapService.getVoltageLevelTopologyInfos(networkUuid, variantId, voltageLevelId, path);
    }

    public String getVoltageLevelSubstationId(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelSubstationId(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId);
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelBusesOrBusbarSections(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId, "buses-or-busbar-sections");
    }

    public String getVoltageLevelTopologyInfos(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode, String path) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelTopologyInfos(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId, path);
    }

    @Transactional
    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        networkModificationTreeService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId, null);
    }

    @Transactional(readOnly = true)
    public List<UUID> getFirstLevelChildrenToBuild(@NonNull UUID studyUuid, @NonNull UUID parentNodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        return networkModificationTreeService.getChildren(parentNodeUuid).stream()
            .map(NodeEntity::getIdNode)
            .filter(childUuid -> !networkModificationTreeService.getNodeBuildStatus(childUuid, rootNetworkUuid).isBuilt())
            .limit(Math.max(0, getAllowedBuildNodesUpToQuota(studyUuid, rootNetworkUuid, userId)))
            .toList();
    }

    @Transactional
    public void buildNodes(@NonNull UUID studyUuid, @NonNull List<UUID> nodeUuids, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        nodeUuids.forEach(nodeUuid -> networkModificationTreeService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId, null));
    }

    @Transactional(readOnly = true)
    public boolean isNodeBuilt(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        return networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt();
    }

    @Transactional(readOnly = true)
    public boolean isSecurityNodeWithLoadflowDone(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        return networkModificationTreeService.isSecurityNode(nodeUuid) && rootNetworkNodeInfoService.isLoadflowDone(nodeUuid, rootNetworkUuid);
    }

    public void handleBuildSuccess(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, NetworkModificationResult networkModificationResult) {
        LOGGER.info("Build completed for node '{}'", nodeUuid);

        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid,
            NodeBuildStatus.from(networkModificationResult.getLastGroupApplicationStatus(), networkModificationResult.getApplicationStatus()));

        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_BUILD_COMPLETED, networkModificationResult.getImpactedSubstationsIds());
    }

    private long getAllowedBuildNodesUpToQuota(@NonNull UUID studyUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        Map<QuotaType, Integer> userMaxQuotas = userAdminService.getUserMaxQuota(userId);

        return Optional.ofNullable(userMaxQuotas.get(QuotaType.BUILD)).map(maxBuilds -> {
            long nbBuiltNodes = networkModificationTreeService.countBuiltNodes(studyUuid, rootNetworkUuid);
            return maxBuilds - nbBuiltNodes;
        }).orElse(Long.MAX_VALUE);
    }

    @Transactional
    public void unbuildStudyNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        if (networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isNotBuilt()) {
            return;
        }

        // if loadflow was run on a security node, all children node might have been impacted with loadflow modifications
        // we need to invalidate them all
        if (self.isSecurityNodeWithLoadflowDone(nodeUuid, rootNetworkUuid)) {
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid);
        } else {
            networkModificationTreeService.invalidateNode(studyUuid, nodeUuid, rootNetworkUuid);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void unbuildNodeTree(@NonNull UUID studyUuid, UUID rootNodeUuid, @NonNull String userId) {
        doUnbuildNodeTree(studyUuid, rootNodeUuid, false, userId);
    }

    private void doUnbuildNodeTree(UUID studyUuid, UUID rootNodeUuid, boolean skipDeleteVariants, @NonNull String userId) {
        InvalidateNodeTreeParameters invalidateNodeTreeParameters = InvalidateNodeTreeParameters.ALL;
        List<UUID> rootNetworkIds = rootNetworkService.getStudyRootNetworkIds(studyUuid);
        List<CompletableFuture<Void>> futures = rootNetworkIds.stream()
                .map(rnId -> studyServerExecutionService.runAsync(() ->
                    networkModificationTreeService.invalidateNodeTree(studyUuid, rootNodeUuid, rnId, invalidateNodeTreeParameters, skipDeleteVariants)))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        boolean noneMatchUnloading = rootNetworkIds.stream()
                .map(rnId -> rootNetworkService.getRootNetwork(rnId).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")))
                .noneMatch(rootNetwork -> rootNetwork.getLoadStatus() == RootNetworkLoadStatus.UNLOADING);
        if (noneMatchUnloading) {
            notificationService.emitElementUpdated(studyUuid, userId);
        }
    }

    public void stopBuild(@NonNull UUID nodeUuid, UUID rootNetworkUuid) {
        networkModificationService.stopBuild(nodeUuid, rootNetworkUuid);
    }

    private void assertDuplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode) {
        checkStudyContainsNode(sourceStudyUuid, nodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
    }

    private void assertMoveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID referenceNodeUuid, InsertMode insertMode) {
        checkStudyContainsNode(studyUuid, nodeToMoveUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateNode(nodeToMoveUuid, referenceNodeUuid, insertMode);
    }

    @Transactional
    public void duplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        assertDuplicateStudyNode(sourceStudyUuid, targetStudyUuid, nodeToCopyUuid, referenceNodeUuid, insertMode);

        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
        boolean invalidateBuild = networkModificationTreeService.hasModifications(nodeToCopyUuid, false);
        if (invalidateBuild) {
            invalidateNodeTree(targetStudyUuid, duplicatedNodeUuid, InvalidateNodeTreeParameters.ONLY_CHILDREN_BUILD_STATUS);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void moveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        assertMoveStudyNode(studyUuid, nodeToMoveUuid, referenceNodeUuid, insertMode);

        List<NodeEntity> oldChildren = null;
        boolean shouldUnbuildChildren = networkModificationTreeService.hasModifications(nodeToMoveUuid, false);

        //Unbuild previous children if necessary
        if (shouldUnbuildChildren) {
            oldChildren = networkModificationTreeService.getChildren(nodeToMoveUuid);
        }

        networkModificationTreeService.moveStudyNode(nodeToMoveUuid, referenceNodeUuid, insertMode);

        //Unbuilding moved node or new children if necessary
        if (shouldUnbuildChildren) {
            invalidateNodeTree(studyUuid, nodeToMoveUuid);
            oldChildren.forEach(child -> invalidateNodeTree(studyUuid, child.getIdNode()));
        } else {
            invalidateNode(studyUuid, nodeToMoveUuid);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void assertDuplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid) {
        checkStudyContainsNode(sourceStudyUuid, parentNodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateSubtree(parentNodeToCopyUuid, referenceNodeUuid);
    }

    @Transactional
    public void duplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid, String userId) {
        assertDuplicateStudySubtree(sourceStudyUuid, targetStudyUuid, parentNodeToCopyUuid, referenceNodeUuid);
        AbstractNode studySubTree = networkModificationTreeService.getStudySubtree(sourceStudyUuid, parentNodeToCopyUuid, null);
        StudyEntity studyEntity = getStudy(targetStudyUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.cloneStudyTree(studySubTree, referenceNodeUuid, studyEntity);
        notificationService.emitSubtreeInserted(targetStudyUuid, duplicatedNodeUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    private void assertMoveStudySubtree(UUID studyUuid, UUID parentNodeToMoveUuid, UUID referenceNodeUuid) {
        checkStudyContainsNode(studyUuid, parentNodeToMoveUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateSubtree(parentNodeToMoveUuid, referenceNodeUuid);
    }

    @Transactional
    public void moveStudySubtree(UUID studyUuid, UUID parentNodeToMoveUuid, UUID referenceNodeUuid, String userId) {
        assertMoveStudySubtree(studyUuid, parentNodeToMoveUuid, referenceNodeUuid);

        List<UUID> allChildren = networkModificationTreeService.getChildrenUuids(parentNodeToMoveUuid);
        if (allChildren.contains(referenceNodeUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }

        networkModificationTreeService.moveStudySubtree(parentNodeToMoveUuid, referenceNodeUuid);

        rootNetworkService.getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity -> {
            UUID rootNetworkUuid = rootNetworkEntity.getId();
            if (networkModificationTreeService.getNodeBuildStatus(parentNodeToMoveUuid, rootNetworkUuid).isBuilt()) {
                invalidateNodeTree(studyUuid, parentNodeToMoveUuid);
            }
            allChildren.stream()
                .filter(childUuid -> networkModificationTreeService.getNodeBuildStatus(childUuid, rootNetworkUuid).isBuilt())
                .forEach(childUuid -> invalidateNodeTree(studyUuid, childUuid));
        });

        notificationService.emitSubtreeMoved(studyUuid, parentNodeToMoveUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void invalidateNode(UUID studyUuid, UUID nodeUuid) {
        rootNetworkService.getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            networkModificationTreeService.invalidateNode(studyUuid, nodeUuid, rootNetworkEntity.getId()));
    }

    private void invalidateNodeTree(UUID studyUuid, UUID nodeUuid) {
        invalidateNodeTree(studyUuid, nodeUuid, InvalidateNodeTreeParameters.ALL);
    }

    private void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, InvalidateNodeTreeParameters invalidateTreeParameters) {
        rootNetworkService.getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkEntity.getId(), invalidateTreeParameters));
    }

    @Transactional
    public void invalidateNodeTreeWhenMoveModification(UUID studyUuid, UUID nodeUuid) {
        invalidateNodeTree(studyUuid, nodeUuid, InvalidateNodeTreeParameters.ALL);
    }

    @Transactional
    public boolean invalidateNodeTreeWhenMoveModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid) {
        boolean isTargetInDifferentNodeTree = !targetNodeUuid.equals(originNodeUuid)
            && !networkModificationTreeService.isAChild(originNodeUuid, targetNodeUuid);

        invalidateNodeTree(studyUuid, originNodeUuid, InvalidateNodeTreeParameters.ALL);

        if (isTargetInDifferentNodeTree) {
            invalidateNodeTreeWithLF(studyUuid, targetNodeUuid, ComputationsInvalidationMode.ALL);
        }

        return isTargetInDifferentNodeTree;
    }

    @Transactional
    public void invalidateNodeTreeWithLF(UUID studyUuid, UUID nodeUuid) {
        invalidateNodeTreeWithLF(studyUuid, nodeUuid, ComputationsInvalidationMode.ALL);
    }

    private void invalidateNodeTreeWithLF(UUID studyUuid, UUID nodeUuid, ComputationsInvalidationMode computationsInvalidationMode) {
        rootNetworkService.getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            invalidateNodeTreeWithLF(studyUuid, nodeUuid, rootNetworkEntity.getId(), computationsInvalidationMode)
        );
    }

    private void invalidateNodeTreeWithLF(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, ComputationsInvalidationMode computationsInvalidationMode) {
        boolean invalidateAll = self.isSecurityNodeWithLoadflowDone(nodeUuid, rootNetworkUuid);
        InvalidateNodeTreeParameters invalidateNodeTreeParameters = InvalidateNodeTreeParameters.builder()
            .invalidationMode(invalidateAll ? InvalidationMode.ALL : InvalidationMode.ONLY_CHILDREN_BUILD_STATUS)
            .computationsInvalidationMode(invalidateAll ? ComputationsInvalidationMode.ALL : computationsInvalidationMode)
            .build();
        invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, invalidateNodeTreeParameters);
    }

    public void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.ALL);
    }

    public void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, InvalidateNodeTreeParameters invalidateTreeParameters) {
        networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, invalidateTreeParameters, false);
    }

    @Transactional
    public void deleteNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        StudyEntity studyEntity = getStudy(studyUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);

            List<ReferenceData> referencesToBeDeleted = networkModificationService.getReferences(modificationsUuids);
            networkModificationService.deleteModifications(groupId, modificationsUuids);
            // if there are unstashed references modifications in the deleted netmods, those references have to be removed from directory server
            removeReferences(referencesToBeDeleted, userId, nodeUuid);

            // for each root network, remove modifications from excluded ones
            studyEntity.getRootNetworks().forEach(rootNetworkEntity -> rootNetworkNodeInfoService.updateModificationsToExclude(nodeUuid, rootNetworkEntity.getId(), new HashSet<>(modificationsUuids),
                    true));
        } finally {
            notificationService.emitModificationsDeleted(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void removeReferences(List<ReferenceData> references, String userId, UUID nodeUuid) {
        references.forEach(reference ->
                directoryService.removeReference(reference.containerId() != null ? reference.containerId() : nodeUuid, userId, reference.referenceId())
        );
    }

    @Transactional
    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.stashModifications(groupId, modificationsUuids);
            invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModificationsMetadata(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId, NetworkModificationMetadata metadata) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.updateModificationsMetadata(groupId, modificationsUuids, metadata);
            if (metadata.getActivated() != null || metadata.getName() != null) {
                invalidateNodeTree(studyUuid, nodeUuid);
            }
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModificationsActivationInRootNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Set<UUID> modificationsUuids, String userId, boolean activated) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        networkModificationService.verifyModifications(networkModificationTreeService.getModificationGroupUuid(nodeUuid), modificationsUuids);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            Set<UUID> modificationsToExclude = new HashSet<>(modificationsUuids);
            modificationsToExclude.addAll(networkModificationService.expandToLeafUuids(new ArrayList<>(modificationsUuids)));
            rootNetworkNodeInfoService.updateModificationsToExclude(nodeUuid, rootNetworkUuid, modificationsToExclude, activated);
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, Optional.of(rootNetworkUuid), childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void restoreNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.restoreModifications(groupId, modificationsUuids);
            invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void removeNodesFromAliases(UUID studyUuid, List<UUID> nodeIds, boolean removeChildren) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (!CollectionUtils.isEmpty(studyEntity.getNodeAliases())) {
            Set<UUID> allNodeIds = new HashSet<>(nodeIds);
            if (removeChildren) {
                nodeIds.forEach(n -> allNodeIds.addAll(networkModificationTreeService.getAllChildrenUuids(n)));
            }
            studyEntity.getNodeAliases().forEach(nodeAliasEmbeddable -> {
                if (nodeAliasEmbeddable.getNodeId() != null && allNodeIds.contains(nodeAliasEmbeddable.getNodeId())) {
                    nodeAliasEmbeddable.setNodeId(null);
                }
            });
        }
    }

    @Transactional
    public void deleteNodes(UUID studyUuid, List<UUID> nodeIds, boolean deleteChildren, String userId) {
        removeNodesFromAliases(studyUuid, nodeIds, deleteChildren);

        DeleteNodeInfos deleteNodeInfos = new DeleteNodeInfos();
        for (UUID nodeId : nodeIds) {
            AtomicReference<Long> startTime = new AtomicReference<>(null);
            startTime.set(System.nanoTime());

            boolean invalidateChildrenBuild = !deleteChildren && networkModificationTreeService.hasModifications(nodeId, false);
            List<NodeEntity> childrenNodes = networkModificationTreeService.getChildren(nodeId);
            networkModificationTreeService.doDeleteNode(nodeId, deleteChildren, deleteNodeInfos);

            if (invalidateChildrenBuild) {
                childrenNodes.forEach(nodeEntity -> invalidateNodeTree(studyUuid, nodeEntity.getIdNode()));
            }

            if (startTime.get() != null && LOGGER.isTraceEnabled()) {
                LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId.toString().replaceAll("[\n\r]", "_"), studyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
            }
        }

        deleteNodesInfos(deleteNodeInfos, userId);

        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void deleteNodesInfos(DeleteNodeInfos deleteNodeInfos, String userId) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        futures.add(studyServerExecutionService.runAsync(() -> deleteNodeInfos.getVariantIds().forEach(networkStoreService::deleteVariants)));
        List<UUID> modificationGroupUuids = deleteNodeInfos.getModificationGroupUuids();
        List<UUID> removedNodeUuids = deleteNodeInfos.getRemovedNodeUuids();
        List<Pair<UUID, UUID>> modificationGroupUuidsNodeUuids = IntStream.range(0, modificationGroupUuids.size())
                .mapToObj(index -> Pair.of(modificationGroupUuids.get(index), removedNodeUuids.get(index)))
                .toList();
        futures.add(studyServerExecutionService.runAsync(() -> modificationGroupUuidsNodeUuids.forEach(
                groupUuidNodeUuid -> deleteModificationsFromGroup(groupUuidNodeUuid, userId))
        ));
        futures.add(studyServerExecutionService.runAsync(() -> deleteNodeInfos.getRemovedNodeUuids().forEach(dynamicSimulationEventService::deleteEventsByNodeId)));
        futures.addAll(rootNetworkNodeInfoService.getRemoteDeletions(deleteNodeInfos));
        // Do not wait completion and do not throw exception
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Transactional
    public void stashNode(UUID studyUuid, UUID nodeId, boolean stashChildren, String userId) {
        removeNodesFromAliases(studyUuid, List.of(nodeId), stashChildren);

        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());

        boolean unbuildChildren = stashChildren || networkModificationTreeService.hasModifications(nodeId, false);
        List<UUID> rootNetworkUuids = rootNetworkService.getStudyRootNetworks(studyUuid).stream()
                .map(RootNetworkEntity::getId)
                .toList();

        if (unbuildChildren) {
            rootNetworkUuids.forEach(rootNetworkId ->
                invalidateNodeTree(studyUuid, nodeId, rootNetworkId));
        } else {
            rootNetworkUuids.forEach(rootNetworkId ->
                networkModificationTreeService.invalidateNode(studyUuid, nodeId, rootNetworkId)
            );
        }

        networkModificationTreeService.doStashNode(nodeId, stashChildren);

        if (startTime.get() != null) {
            LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId, studyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }

        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public List<Pair<AbstractNode, Integer>> getStashedNodes(UUID studyId) {
        return networkModificationTreeService.getStashedNodes(studyId);
    }

    public void restoreNodes(UUID studyId, List<UUID> nodeIds, UUID anchorNodeId, String userId) {
        networkModificationTreeService.assertIsRootOrConstructionNode(anchorNodeId);
        networkModificationTreeService.restoreNode(studyId, nodeIds, anchorNodeId);
        notificationService.emitElementUpdated(studyId, userId);
    }

    private void reindexRootNetwork(StudyEntity study, UUID rootNetworkUuid) {
        CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study);
        // reindex root network for study in elasticsearch
        studyInfosService.recreateStudyInfos(studyInfos);
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        if (rootNetwork.getLoadStatus() != RootNetworkLoadStatus.LOADED) {
            LOGGER.info("Root network '{}' is not loaded, skipping reindexation", rootNetworkUuid);
            return;
        }
        // Reset indexation status
        updateRootNetworkIndexationStatus(study, rootNetwork, RootNetworkIndexationStatus.INDEXING_ONGOING);
        try {
            networkConversionService.reindexStudyNetworkEquipments(rootNetworkService.getNetworkUuid(rootNetworkUuid));
            updateRootNetworkIndexationStatus(study, rootNetwork, RootNetworkIndexationStatus.INDEXED);
        } catch (Exception e) {
            // Allow to retry indexation
            updateRootNetworkIndexationStatus(study, rootNetwork, RootNetworkIndexationStatus.NOT_INDEXED);
            throw e;
        }
        LOGGER.info("Study with id = '{}' has been reindexed", study.getId());
    }

    @Transactional
    public void reindexRootNetwork(UUID studyUuid, UUID rootNetworkUuid) {
        reindexRootNetwork(getStudy(studyUuid), rootNetworkUuid);
    }

    private StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, STUDY_NOT_FOUND));
    }

    @Transactional
    public Map<UUID, NodeBuildStatus> getNodeBuildStatusByRootNetwork(UUID studyUuid, UUID nodeUuid) {
        return rootNetworkService.getStudyRootNetworks(studyUuid).stream().collect(Collectors.toMap(
            RootNetworkEntity::getId,
            rn -> rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rn.getId()).map(rni -> rni.getNodeBuildStatus().toDto()).orElseThrow(() -> new StudyException(NOT_FOUND,
                    "Root network not found"))
        ));
    }

    @Transactional
    public RootNetworkIndexationStatus getRootNetworkIndexationStatus(UUID studyUuid, UUID rootNetworkUuid) {
        StudyEntity study = getStudy(studyUuid);
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        if (rootNetwork.getIndexationStatus() == RootNetworkIndexationStatus.INDEXED
                && !networkConversionService.checkStudyIndexationStatus(rootNetworkService.getNetworkUuid(rootNetworkUuid))) {
            updateRootNetworkIndexationStatus(study, rootNetwork, RootNetworkIndexationStatus.NOT_INDEXED);
        }
        return rootNetwork.getIndexationStatus();
    }

    @Transactional
    public void moveNetworkModifications(
            @NonNull UUID studyUuid,
            @NonNull UUID targetNodeUuid,
            @NonNull List<ModificationMoveOrCopyInfos> modificationInfos,
            UUID sourceNodeUuid,
            ModificationContainerInfos targetModificationContainer,
            UUID beforeUuid,
            boolean isTargetInDifferentNodeTree,
            String userId) {
        ModificationContainerInfos resolvedTarget = resolveContainer(targetModificationContainer, targetNodeUuid);
        Map<ModificationContainerInfos, List<UUID>> modificationUuidsBySource = resolveAndGroupBySource(modificationInfos, sourceNodeUuid);

        Map<ModificationContainerInfos, UUID> originNodeBySource = new LinkedHashMap<>();
        modificationUuidsBySource.keySet().forEach(source ->
                originNodeBySource.put(source, networkModificationTreeService.getNodeUuidByModificationGroup(source.id())));
        Set<UUID> originNodesTouched = originNodeBySource.values().stream()
                .filter(node -> node != null && !node.equals(targetNodeUuid))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<UUID> targetChildrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        Map<UUID, List<UUID>> originChildrenUuidsByNode = originNodesTouched.stream()
                .collect(Collectors.toMap(Function.identity(), networkModificationTreeService::getChildrenUuids, (a, b) -> a, LinkedHashMap::new));

        try {
            StudyEntity studyEntity = getStudy(studyUuid);
            checkStudyContainsNode(studyUuid, targetNodeUuid);
            List<ModificationApplicationContext> applicationContexts = studyEntity.getRootNetworks().stream()
                    .map(rn -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rn.getId(), targetNodeUuid, rn.getNetworkUuid()))
                    .toList();
            List<UUID> allModificationUuids = modificationInfos.stream().map(ModificationMoveOrCopyInfos::modificationUuid).toList();
            List<ReferenceData> allReferencesToMove = networkModificationService.getReferences(allModificationUuids);

            for (Map.Entry<ModificationContainerInfos, List<UUID>> entry : modificationUuidsBySource.entrySet()) {
                ModificationContainerInfos source = entry.getKey();
                List<UUID> modificationUuidsToMove = entry.getValue();
                UUID originNodeUuid = originNodeBySource.get(source);
                boolean isTargetDifferentNode = !targetNodeUuid.equals(originNodeUuid);
                Set<UUID> modificationUuidsToMoveSet = new HashSet<>(modificationUuidsToMove);
                List<ReferenceData> referencesToMove = allReferencesToMove.stream()
                        .filter(reference -> modificationUuidsToMoveSet.contains(reference.modificationUuid()))
                        .toList();

                NetworkModificationsResult result = networkModificationService.moveModifications(
                        new MoveModificationInfos(source, resolvedTarget, beforeUuid),
                        Pair.of(modificationUuidsToMove, applicationContexts),
                        isTargetInDifferentNodeTree);

                if (result != null && isTargetInDifferentNodeTree) {
                    emitNetworkModificationImpactsForAllRootNetworks(result.modificationResults(), studyEntity, targetNodeUuid);
                }

                if (result != null && originNodeUuid != null) {
                    Set<UUID> allMovedUuids = networkModificationService.expandToLeafUuids(result.modificationUuids());
                    rootNetworkNodeInfoService.moveModificationsToExclude(originNodeUuid, targetNodeUuid, new ArrayList<>(allMovedUuids));
                }

                updateSharedCompositeReferencesForMove(result, source, resolvedTarget, referencesToMove, userId, originNodeUuid, targetNodeUuid, isTargetDifferentNode);
            }
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, targetNodeUuid, targetChildrenUuids);
            originChildrenUuidsByNode.forEach((originNodeUuid, children) ->
                    notificationService.emitModificationsUpdated(studyUuid, originNodeUuid, children));
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    /**
     * Updates node-references to shared composites impacted by a move, based on source/target container types:
     * - group -> composite: the reference now targets the composite it was moved into
     * - composite -> group: the reference now targets the node it was moved into
     * - composite -> different composite: the reference now targets the new composite (independent of node,
     *   since a composite's own identity - not the node it happens to be attached to - is what the reference tracks)
     * - moved to a different node (from a node-level group): the existing reference is repointed, not duplicated
     */
    private void updateSharedCompositeReferencesForMove(NetworkModificationsResult result, ModificationContainerInfos source, ModificationContainerInfos target,
                                                        List<ReferenceData> referencesToMove, String userId,
                                                        UUID originNodeUuid, UUID targetNodeUuid, boolean isTargetDifferentNode) {
        if (referencesToMove.isEmpty()) {
            return;
        }
        if (source.type() == ModificationContainerType.GROUP && target.type() == ModificationContainerType.COMPOSITE) {
            updateReferenceWhenMoveModification(referencesToMove, userId, originNodeUuid, target.id(), ReferenceAttributes.ReferenceType.NETWORK_MODIFICATION);
        }
        if (target.type() == ModificationContainerType.GROUP && source.type() == ModificationContainerType.COMPOSITE) {
            updateReferenceWhenMoveModification(referencesToMove, userId, source.id(), targetNodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE);
        }
        if (source.type() == ModificationContainerType.COMPOSITE && target.type() == ModificationContainerType.COMPOSITE
                && !source.id().equals(target.id())) {
            updateReferenceWhenMoveModification(referencesToMove, userId, source.id(), target.id(), ReferenceAttributes.ReferenceType.NETWORK_MODIFICATION);
        }
        // shared composites: the moved occurrence's node reference is updated, not duplicated
        if (result != null && isTargetDifferentNode && originNodeUuid != null) {
            updateReferenceWhenMoveModification(referencesToMove, userId, originNodeUuid, targetNodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE);
        }
    }

    /**
     * updates, for each moved shared composite, the existing node-reference so that it points
     * to the target
     */
    private void updateReferenceWhenMoveModification(List<ReferenceData> referenceTargets, String userId, UUID originReferenceUuid,
                                                     UUID targetReferenceUuid, ReferenceAttributes.ReferenceType targetReferenceType) {
        List<UUID> referenceModificationsUuids = referenceTargets.stream()
                .map(ReferenceData::referenceId)
                .collect(Collectors.toList());
        directoryService.updateReferencesToSharedComposites(
                referenceModificationsUuids,
                userId,
                originReferenceUuid,
                targetReferenceUuid,
                targetReferenceType
        );
    }

    private Map<ModificationContainerInfos, List<UUID>> resolveAndGroupBySource(List<ModificationMoveOrCopyInfos> modificationInfos, UUID fallbackSourceNodeUuid) {
        List<UUID> modificationUuidsNeedingSourceLookup = modificationInfos.stream()
                .filter(info -> !hasModificationSource(info.source()))
                .map(ModificationMoveOrCopyInfos::modificationUuid)
                .toList();
        Map<UUID, UUID> parentCompositeByModificationUuid = modificationUuidsNeedingSourceLookup.isEmpty()
                ? Map.of()
                : networkModificationService.findParentComposites(modificationUuidsNeedingSourceLookup);

        return modificationInfos.stream()
                .collect(Collectors.groupingBy(
                        info -> resolveSourceContainer(info.source(), info.modificationUuid(), fallbackSourceNodeUuid, parentCompositeByModificationUuid),
                        LinkedHashMap::new,
                        Collectors.mapping(ModificationMoveOrCopyInfos::modificationUuid, Collectors.toList())));
    }

    private boolean hasModificationSource(ModificationContainerInfos source) {
        return source != null && source.id() != null;
    }

    private ModificationContainerInfos resolveSourceContainer(ModificationContainerInfos source, UUID modificationUuid, UUID originNodeUuid,
                                                                Map<UUID, UUID> parentCompositeByModificationUuid) {
        if (hasModificationSource(source)) {
            return source;
        }
        UUID parentCompositeUuid = parentCompositeByModificationUuid.get(modificationUuid);
        if (parentCompositeUuid != null) {
            return new ModificationContainerInfos(parentCompositeUuid, ModificationContainerType.COMPOSITE);
        }
        return new ModificationContainerInfos(networkModificationTreeService.getModificationGroupUuid(originNodeUuid), ModificationContainerType.GROUP);
    }

    private ModificationContainerInfos resolveContainer(ModificationContainerInfos container, UUID nodeUuid) {
        if (container != null && container.id() != null) {
            return container;
        }
        return new ModificationContainerInfos(networkModificationTreeService.getModificationGroupUuid(nodeUuid), ModificationContainerType.GROUP);
    }

    private void emitNetworkModificationImpactsForAllRootNetworks(List<Optional<NetworkModificationResult>> modificationResults, StudyEntity studyEntity, UUID impactedNode) {
        int index = 0;
        List<RootNetworkEntity> rootNetworkEntities = studyEntity.getRootNetworks();
        // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
        for (Optional<NetworkModificationResult> modificationResultOpt : modificationResults) {
            if (modificationResultOpt.isPresent() && rootNetworkEntities.get(index) != null) {
                emitNetworkModificationImpacts(studyEntity.getId(), impactedNode, rootNetworkEntities.get(index).getId(), modificationResultOpt.get());
            }
            index++;
        }
    }

    @Transactional
    public void duplicateNetworkModifications(
            UUID targetStudyUuid,
            UUID targetNodeUuid,
            UUID originNodeUuid,
            List<UUID> modificationsUuids,
            String userId) {
        duplicateModificationsOrInsertComposites(targetStudyUuid, targetNodeUuid,
                (groupUuid, modificationApplicationContexts) -> {
                    // fetched BEFORE the duplication. getReferences() only tells us whether a given modification IS
                    // itself a modification-reference - it does not descend into composites - so a reference nested
                    // inside a copied composite (e.g. X's child R, pointing at shared element S) is invisible unless
                    // its own uuid is included in the lookup; hence querying modificationsUuids' children too.
                    List<UUID> originalChildrenUuids = networkModificationService.findAllChildrenUuids(modificationsUuids);
                    List<UUID> allOriginalUuids = new ArrayList<>(modificationsUuids);
                    allOriginalUuids.addAll(originalChildrenUuids);
                    List<ReferenceData> referenceTargets = networkModificationService.getReferences(allOriginalUuids);

                    NetworkModificationsResult networkModificationResults = networkModificationService.duplicateModifications(groupUuid, Pair.of(modificationsUuids, modificationApplicationContexts));
                    Map<UUID, UUID> mappingModificationsUuids = buildModificationsUuidMapping(modificationsUuids, originalChildrenUuids, networkModificationResults);
                    copyModificationsToExclude(originNodeUuid, targetNodeUuid, mappingModificationsUuids);
                    createReferencesToSharedComposites(referenceTargets, modificationsUuids, mappingModificationsUuids, userId, targetNodeUuid);
                    return networkModificationResults;
                },
                userId);
    }

    /**
     * @return old modification uuid -> new (copied) modification uuid, for every modification duplicated by
     * {@code networkModificationResults} - root-level ones as well as those nested in a duplicated composite
     */
    private Map<UUID, UUID> buildModificationsUuidMapping(List<UUID> copiedUuids, List<UUID> copiedChildrenUuids, NetworkModificationsResult networkModificationResults) {
        Map<UUID, UUID> mappingModificationsUuids = new HashMap<>();
        List<UUID> copyUuids = networkModificationResults.modificationUuids();

        // Map root-level modifications
        for (int i = 0; i < copiedUuids.size(); i++) {
            mappingModificationsUuids.put(copiedUuids.get(i), copyUuids.get(i));
        }

        List<UUID> copyChildren = networkModificationService.findAllChildrenUuids(copyUuids);
        for (int i = 0; i < copiedChildrenUuids.size(); i++) {
            mappingModificationsUuids.put(copiedChildrenUuids.get(i), copyChildren.get(i));
        }

        return mappingModificationsUuids;
    }

    private void copyModificationsToExclude(UUID originNodeUuid, UUID targetNodeUuid, Map<UUID, UUID> mappingModificationsUuids) {
        rootNetworkNodeInfoService.copyModificationsToExcludeFromTags(originNodeUuid, targetNodeUuid, mappingModificationsUuids);
    }

    /**
     * one new reference is created per pasted modification-reference occurrence: even if several of them target the
     * same shared composite, each occurrence must get its own reference row in directory-server (symmetric with
     * {@link #removeReferences}, which likewise issues one removal per occurrence on delete) - so this must NOT be
     * deduplicated by referenceId.
     */
    private void createReferencesToSharedComposites(List<ReferenceData> references, List<UUID> modificationsUuids,
                                                    Map<UUID, UUID> mappingModificationsUuids, String userId, UUID targetNodeUuid) {
        Set<UUID> requestedUuids = new HashSet<>(modificationsUuids);

        List<UUID> directlyRequestedReferenceIds = references.stream()
                .filter(reference -> requestedUuids.contains(reference.modificationUuid()))
                .map(ReferenceData::referenceId)
                .toList();
        if (!directlyRequestedReferenceIds.isEmpty()) {
            directoryService.createsReferencesToSharedComposites(directlyRequestedReferenceIds, userId, targetNodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE);
        }

        Map<UUID, List<UUID>> nestedReferenceIdsByNewComposite = references.stream()
                .filter(reference -> !requestedUuids.contains(reference.modificationUuid()))
                .collect(Collectors.groupingBy(
                        reference -> mappingModificationsUuids.get(reference.containerId()),
                        Collectors.mapping(ReferenceData::referenceId, Collectors.toList())));
        nestedReferenceIdsByNewComposite.forEach((newCompositeUuid, referenceIds) ->
                directoryService.createsReferencesToSharedComposites(referenceIds, userId, newCompositeUuid, ReferenceAttributes.ReferenceType.NETWORK_MODIFICATION));
    }

    @Transactional
    public UUID assembleModificationsIntoComposite(
            UUID targetStudyUuid,
            UUID targetNodeUuid,
            List<UUID> modificationsUuids,
            String userId) {
        UUID newCompositeUuid;
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        try {
            checkStudyContainsNode(targetStudyUuid, targetNodeUuid);
            newCompositeUuid = networkModificationService.assembleModificationsIntoComposite(modificationsUuids);
        } finally {
            notificationService.emitModificationsUpdated(targetStudyUuid, targetNodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
        return newCompositeUuid;
    }

    /**
     * Moves a composite modification of a node out of the study : it is stored as an element in the directory server,
     * and replaced in the node by a reference to this now shared composite modification.
     */
    @Transactional(readOnly = true)
    public void shareCompositeNetworkModification(
        UUID studyUuid,
        UUID nodeUuid,
        UUID modificationUuid,
        String name,
        String description,
        UUID parentDirectoryUuid,
        String userId) {
        // checks we can write in the target directory before touching anything
        directoryService.checkPermission(List.of(), parentDirectoryUuid, userId, PermissionType.WRITE, false);
        if (directoryService.elementExists(parentDirectoryUuid, name, DirectoryService.MODIFICATION)) {
            throw new StudyException(ELEMENT_ALREADY_EXISTS, "composite modification name " + name + " already exists in directory", Map.of("fileName", name));
        }

        UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            // the applied modifications are left unchanged : the node does not need to be rebuilt
            networkModificationService.extractCompositeModificationToShare(groupUuid, modificationUuid, name);
            // the composite modification keeps its uuid when extracted, so it is shared under that same uuid
            directoryService.createElement(parentDirectoryUuid, description, modificationUuid, name, DirectoryService.MODIFICATION, userId);
            directoryService.createsReferencesToSharedComposites(List.of(modificationUuid), userId, nodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void insertCompositeNetworkModifications(
        UUID targetStudyUuid,
        UUID targetNodeUuid,
        List<CompositeInfos> compositesInfos,
        String userId,
        StudyConstants.CompositeModificationsActionType action) {
        // is some of the inserted modifications are shared, references have to be created in directory server
        List<UUID> sharedCompositeUuids = compositesInfos.stream()
                .filter(CompositeInfos::isShared)
                .map(CompositeInfos::id)
                .toList();
        if (action == StudyConstants.CompositeModificationsActionType.INSERT && !sharedCompositeUuids.isEmpty()) {
            directoryService.createsReferencesToSharedComposites(sharedCompositeUuids, userId, targetNodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE);
        }

        duplicateModificationsOrInsertComposites(
                targetStudyUuid,
                targetNodeUuid,
                (groupUuid, modificationApplicationContexts) ->
                        networkModificationService.insertCompositeModifications(groupUuid, action, Pair.of(compositesInfos, modificationApplicationContexts)),
                userId
        );
    }

    private void duplicateModificationsOrInsertComposites(
        UUID targetStudyUuid,
        UUID targetNodeUuid,
        BiFunction<UUID, List<ModificationApplicationContext>, NetworkModificationsResult> handleModifications,
        String userId) {
        invalidateNodeTreeWithLF(targetStudyUuid, targetNodeUuid, ComputationsInvalidationMode.ALL);
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        try {
            checkStudyContainsNode(targetStudyUuid, targetNodeUuid);

            List<RootNetworkEntity> studyRootNetworkEntities = rootNetworkService.getStudyRootNetworks(targetStudyUuid);
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(targetNodeUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), targetNodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();

            NetworkModificationsResult networkModificationResults = handleModifications.apply(groupUuid, modificationApplicationContexts);

            sendImpactNotifications(targetStudyUuid, targetNodeUuid, networkModificationResults, studyRootNetworkEntities);
        } finally {
            notificationService.emitModificationsUpdated(targetStudyUuid, targetNodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    private void sendImpactNotifications(UUID targetStudyUuid, UUID targetNodeUuid, NetworkModificationsResult networkModificationResults, List<RootNetworkEntity> studyRootNetworkEntities) {
        if (networkModificationResults != null) {
            int index = 0;
            // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
            for (Optional<NetworkModificationResult> modificationResultOpt : networkModificationResults.modificationResults()) {
                if (modificationResultOpt.isPresent() && studyRootNetworkEntities.get(index) != null) {
                    emitNetworkModificationImpacts(targetStudyUuid, targetNodeUuid, studyRootNetworkEntities.get(index).getId(), modificationResultOpt.get());
                }
                index++;
            }
        }
    }

    private void checkStudyContainsNode(UUID studyUuid, UUID nodeUuid) {
        if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    private ReportPage getParentNodesReportLogs(UUID nodeUuid, UUID rootNetworkUuid, String messageFilter, Set<String> severityLevels, boolean paged, Pageable pageable) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        List<UUID> reportUuids = nodeIds.stream()
            .map(nodeId -> Optional.ofNullable(modificationReportsMap.get(nodeId))
                    .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        return reportService.getPagedMultipleReportLogs(reportUuids, messageFilter, severityLevels, paged, pageable);
    }

    @Transactional(readOnly = true)
    public ReportPage getReportLogs(UUID nodeUuid, UUID rootNetworkUuid, UUID reportId, String messageFilter, Set<String> severityLevels, boolean paged, Pageable pageable) {
        if (reportId != null) {
            return reportService.getPagedReportLogs(reportId, messageFilter, severityLevels, paged, pageable);
        }
        return getParentNodesReportLogs(nodeUuid, rootNetworkUuid, messageFilter, severityLevels, paged, pageable);
    }

    private String getSearchTermMatchesInParentNodesFilteredLogs(UUID nodeUuid, UUID rootNetworkUuid, Set<String> severityLevels, String messageFilter, String searchTerm, int pageSize) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        List<UUID> reportUuids = nodeIds.stream()
            .map(nodeId -> Optional.ofNullable(modificationReportsMap.get(nodeId))
                    .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        return reportService.getSearchTermMatchesInMultipleFilteredLogs(reportUuids, severityLevels, messageFilter, searchTerm, pageSize);
    }

    @Transactional(readOnly = true)
    public String getSearchTermMatchesInFilteredLogs(UUID nodeUuid, UUID rootNetworkUuid, UUID reportId, Set<String> severityLevels, String messageFilter, String searchTerm, int pageSize) {
        if (reportId != null) {
            return reportService.getSearchTermMatchesInFilteredLogs(reportId, severityLevels, messageFilter, searchTerm, pageSize);
        }
        return getSearchTermMatchesInParentNodesFilteredLogs(nodeUuid, rootNetworkUuid, severityLevels, messageFilter, searchTerm, pageSize);
    }

    private Set<String> getParentNodesAggregatedReportSeverities(UUID nodeUuid, UUID rootNetworkUuid) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        Set<String> severities = new HashSet<>();
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        for (UUID nodeId : nodeIds) {
            Optional<UUID> reportId = Optional.ofNullable(modificationReportsMap.get(nodeId))
                    .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid));

            reportId.ifPresent(uuid ->
                    severities.addAll(reportService.getReportAggregatedSeverities(uuid))
            );
        }
        return severities;
    }

    @Transactional(readOnly = true)
    public Set<String> getAggregatedReportSeverities(UUID nodeUuid, UUID rootNetworkUuid, UUID reportId) {
        if (reportId != null) {
            return reportService.getReportAggregatedSeverities(reportId);
        }
        return getParentNodesAggregatedReportSeverities(nodeUuid, rootNetworkUuid);
    }

    @Transactional(readOnly = true)
    public List<Report> getParentNodesReport(UUID nodeUuid, UUID rootNetworkUuid, boolean nodeOnlyReport, ReportType reportType, Set<String> severityLevels) {
        AbstractNode nodeInfos = networkModificationTreeService.getNode(nodeUuid, rootNetworkUuid);

        if (isNonRootNodeWithComputationReportType(nodeInfos, reportType)) {
            UUID reportUuid = getReportUuidForNode(nodeUuid, rootNetworkUuid, reportType);
            return reportUuid != null ? List.of(reportService.getReport(reportUuid, nodeUuid.toString(), severityLevels)) : Collections.emptyList();
        } else if (nodeOnlyReport) {
            return getNodeOnlyReport(nodeUuid, rootNetworkUuid, severityLevels);
        } else {
            return getAllModificationReports(nodeUuid, rootNetworkUuid, severityLevels);
        }
    }

    private boolean isNonRootNodeWithComputationReportType(AbstractNode nodeInfos, ReportType reportType) {
        return nodeInfos.getType() != NodeType.ROOT && reportType != ReportType.NETWORK_MODIFICATION;
    }

    private UUID getReportUuidForNode(UUID nodeUuid, UUID rootNetworkUuid, ReportType reportType) {
        return networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).get(reportType.name());
    }

    private List<Report> getNodeOnlyReport(UUID nodeUuid, UUID rootNetworkUuid, Set<String> severityLevels) {
        return networkModificationTreeService.getReportUuid(nodeUuid, rootNetworkUuid)
                .map(uuid -> List.of(reportService.getReport(uuid, nodeUuid.toString(), severityLevels)))
                .orElse(Collections.emptyList());
    }

    private List<Report> getAllModificationReports(UUID nodeUuid, UUID rootNetworkUuid, Set<String> severityLevels) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        List<Report> modificationReports = new ArrayList<>();
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        for (UUID nodeId : nodeIds) {
            Optional<UUID> reportId = Optional.ofNullable(modificationReportsMap.get(nodeId))
                    .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid));

            reportId.ifPresent(uuid ->
                    modificationReports.add(reportService.getReport(uuid, nodeId.toString(), severityLevels))
            );
        }

        return modificationReports;
    }

    private List<UUID> nodesTree(UUID nodeUuid) {
        List<UUID> nodeIds = new ArrayList<>();
        nodeIds.add(nodeUuid);
        Optional<UUID> parentUuid = networkModificationTreeService.getParentNodeUuid(nodeUuid);

        while (parentUuid.isPresent()) {
            nodeIds.add(parentUuid.get());
            parentUuid = networkModificationTreeService.getParentNodeUuid(parentUuid.get());
        }

        Collections.reverse(nodeIds);
        return nodeIds;
    }

    private void emitNetworkModificationImpacts(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, NetworkModificationResult networkModificationResult) {
        //TODO move this / rename parent method when refactoring notifications
        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid,
                NodeBuildStatus.from(networkModificationResult.getLastGroupApplicationStatus(), networkModificationResult.getApplicationStatus()));

        Set<org.gridsuite.study.server.notification.dto.EquipmentDeletionInfos> deletionsInfos =
                networkModificationResult.getNetworkImpacts().stream()
                        .filter(impact -> impact.isSimple() && ((SimpleElementImpact) impact).isDeletion())
                        .map(impact -> new org.gridsuite.study.server.notification.dto.EquipmentDeletionInfos(((SimpleElementImpact) impact).getElementId(), impact.getElementType().name()))
                        .collect(Collectors.toSet());

        Set<String> impactedElementTypes = networkModificationResult.getNetworkImpacts().stream()
                .filter(impact -> impact.isCollection())
                .map(impact -> impact.getElementType().name())
                .collect(Collectors.toSet());

        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_STUDY,
                NetworkImpactsInfos.builder()
                        .deletedEquipments(deletionsInfos)
                        .impactedSubstationsIds(networkModificationResult.getImpactedSubstationsIds())
                        .impactedElementTypes(impactedElementTypes)
                        .build()
        );
    }

    public void notify(@NonNull UUID studyUuid) {
        notificationService.emitStudyMetadataChanged(studyUuid);
    }

    @Transactional
    public String getSpreadsheetConfigCollection(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyConfigService.getSpreadsheetConfigCollection(studyConfigService.getSpreadsheetConfigCollectionUuidOrElseCreateDefaults(studyEntity));
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

    public String getNetworkElementsIds(UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String equipmentType, List<Double> nominalVoltages) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getElementsIds(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                substationsIds, equipmentType, nominalVoltages);
    }

    @Transactional(readOnly = true)
    public String getVoltageInitModifications(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, String.format("Missing results for rootNetwork %s on node %s", rootNetworkUuid, nodeUuid));
        }
        UUID voltageInitModificationsGroupUuid = voltageInitRestService.getModificationsGroupUuid(nodeUuid, resultUuid);
        return networkModificationService.getModifications(voltageInitModificationsGroupUuid, false, false);
    }

    @Transactional
    public void insertVoltageInitModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, String.format("Missing results for rootNetwork %s on node %s", rootNetworkUuid, nodeUuid));
        }
        UUID voltageInitModificationsGroupUuid = voltageInitRestService.getModificationsGroupUuid(nodeUuid, resultUuid);

        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);

            invalidateNodeTreeWithLF(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode.PRESERVE_VOLTAGE_INIT_RESULTS);

            // voltageInit modification should apply only on the root network where the computation has been made:
            // - application context will point to the computation root network only
            // - after creation, we deactivate the new modification for all other root networks
            List<RootNetworkEntity> studyRootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);
            List<ModificationApplicationContext> modificationApplicationContexts = new ArrayList<>();
            List<UUID> rootNetworkToDeactivateUuids = new ArrayList<>();
            studyRootNetworkEntities.forEach(rootNetworkEntity -> {
                if (rootNetworkUuid.equals(rootNetworkEntity.getId())) {
                    modificationApplicationContexts.add(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), nodeUuid, rootNetworkEntity.getNetworkUuid()));
                } else {
                    rootNetworkToDeactivateUuids.add(rootNetworkEntity.getId());
                }
            });
            // duplicate the modification created by voltageInit server into the current node
            NetworkModificationsResult networkModificationResults = networkModificationService.duplicateModificationsFromGroup(networkModificationTreeService.getModificationGroupUuid(nodeUuid),
                    voltageInitModificationsGroupUuid, Pair.of(List.of(), modificationApplicationContexts));

            // We expect a single voltageInit modification in the result list
            if (networkModificationResults != null && networkModificationResults.modificationUuids().size() == 1) {
                for (UUID otherRootNetwork : rootNetworkToDeactivateUuids) {
                    rootNetworkNodeInfoService.updateModificationsToExclude(nodeUuid, otherRootNetwork, Set.of(networkModificationResults.modificationUuids().getFirst()), false);
                }
                // The modification was applied only on rootNetworkUuid, so the single result must be attributed to it
                networkModificationResults.modificationResults().getFirst()
                    .ifPresent(result -> emitNetworkModificationImpacts(studyUuid, nodeUuid, rootNetworkUuid, result));
            }

            voltageInitRestService.resetModificationsGroupUuid(resultUuid);

            // send notification voltage init result has changed
            notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional(readOnly = true)
    public String evaluateFilter(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String filter) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return filterService.evaluateFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid), filter);
    }

    @Transactional(readOnly = true)
    public List<String> evaluateGlobalFilter(@NonNull final UUID nodeUuid, @NonNull final UUID rootNetworkUuid,
                                             @NonNull final List<EquipmentType> equipmentTypes, @NonNull final GlobalFilter filter) {
        return filterService.evaluateGlobalFilter(
            rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, true), rootNetworkUuid),
            equipmentTypes,
            filter
        );
    }

    @Transactional
    public String getNetworkElementsInfosByGlobalFilter(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, EquipmentType equipmentType, String infoType, GlobalFilter filter) {
        // Get the list of equipment ids that match the filter
        List<String> equipmentIds = self.evaluateGlobalFilter(nodeUuid, rootNetworkUuid, List.of(equipmentType), filter);

        // Get the requested info for the filtered equipment ids
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, true);
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = loadFlowService.getLoadFlowParameters(studyEntity);

        return networkMapService.getElementsInfosByIds(
            rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
            String.valueOf(equipmentType),
            infoType,
            getOptionalParameters(String.valueOf(equipmentType), studyEntity, loadFlowParameters),
            equipmentIds
        );
    }

    @Transactional(readOnly = true)
    public String exportFilter(UUID rootNetworkUuid, UUID filterUuid) {
        return filterService.exportFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), filterUuid);
    }

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

    public String exportFilters(UUID rootNetworkUuid, List<UUID> filtersUuid, UUID nodeUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return filterService.exportFilters(rootNetworkService.getNetworkUuid(rootNetworkUuid), filtersUuid, variantId);
    }

    @Transactional
    public NetworkModificationNode createNode(UUID studyUuid, UUID nodeId, NetworkModificationNode nodeInfo, InsertMode insertMode, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.assertCreateNode(nodeId, nodeInfo.getNodeType(), insertMode);
        NetworkModificationNode newNode = networkModificationTreeService.createNode(study, nodeId, nodeInfo, insertMode, userId);

        UUID parentUuid = networkModificationTreeService.getParentNodeUuid(newNode.getId()).orElse(null);
        notificationService.emitNodeInserted(study.getId(), parentUuid, newNode.getId(), insertMode, nodeId);
        // userId is null when creating initial nodes, we don't need to send element update notifications in this case
        if (userId != null) {
            notificationService.emitElementUpdated(study.getId(), userId);
        }
        return newNode;
    }

    @Transactional(readOnly = true)
    public List<UUID> getRootNetworksToBuildNewNode(UUID studyUuid, UUID parentNodeUuid, NetworkModificationNode newNode) {
        if (!newNode.isSecurityNode() || !networkModificationTreeService.isRootOrConstructionNode(parentNodeUuid)) {
            return List.of();
        }
        return rootNetworkService.getStudyRootNetworks(studyUuid).stream().map(RootNetworkEntity::getId).toList();
    }

    @Transactional
    public NetworkModificationNode createSequence(UUID studyUuid, UUID parentNodeUuid, NodeSequenceType nodeSequenceType, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.assertIsRootOrConstructionNode(parentNodeUuid);

        NetworkModificationNode newParentNode = networkModificationTreeService.createTreeNodeFromNodeSequence(study, parentNodeUuid, nodeSequenceType);

        notificationService.emitSubtreeInserted(study.getId(), newParentNode.getId(), parentNodeUuid);
        // userId is null when creating initial nodes, we don't need to send element update notifications in this case
        if (userId != null) {
            notificationService.emitElementUpdated(study.getId(), userId);
        }
        return newParentNode;
    }

    private List<RootNetworkInfos> getStudyRootNetworksInfos(UUID studyUuid) {
        List<RootNetworkEntity> rootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);
        // using the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        rootNetworkService.getRootNetworkInfosWithLinksInfos(studyUuid);
        return rootNetworkEntities.stream().map(rootNetworkEntity -> rootNetworkEntity.toDto(objectMapper)).toList();
    }

    @Transactional(readOnly = true)
    public List<BasicRootNetworkInfos> getAllBasicRootNetworkInfos(UUID studyUuid) {
        return Stream
            .concat(
                getExistingRootNetworkInfos(studyUuid).stream(),
                rootNetworkService.geRootNetworkRequests(studyUuid).stream().filter(s -> s.getActionRequest() == RootNetworkAction.ROOT_NETWORK_CREATION).map(RootNetworkRequestEntity::toBasicDto))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BasicRootNetworkInfos> getExistingBasicRootNetworkInfos(UUID studyUuid) {
        return getExistingRootNetworkInfos(studyUuid);
    }

    private List<BasicRootNetworkInfos> getExistingRootNetworkInfos(UUID studyUuid) {
        return rootNetworkService.getStudyRootNetworks(studyUuid).stream().map(RootNetworkEntity::toBasicDto).toList();
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

    private List<CurrentLimitViolationInfos> getCurrentLimitViolations(UUID nodeUuid, UUID rootNetworkUuid) {
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        if (resultUuid == null) {
            return List.of();
        }
        return loadflowRestService.getCurrentLimitViolations(resultUuid)
            .stream()
            .map(l -> new CurrentLimitViolationInfos(l.getSubjectId(), null))
            .toList();
    }

    public Map<ComputationType, String> getAllComputationsStatus(@NonNull UUID studyUuid, @NonNull UUID rootNetworkUuid, @NonNull UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        Map<ComputationType, String> allComputationStatus = new EnumMap<>(ComputationType.class);
        allComputationStatus.put(LOAD_FLOW, rootNetworkNodeInfoService.getLoadFlowStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(SECURITY_ANALYSIS, rootNetworkNodeInfoService.getSecurityAnalysisStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(PCC_MIN, rootNetworkNodeInfoService.getPccMinStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(ASYMMETRICAL_LOAD, rootNetworkNodeInfoService.getAsymmetricalLoadStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(DYNAMIC_MARGIN_CALCULATION, rootNetworkNodeInfoService.getDynamicMarginCalculationStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(DYNAMIC_SECURITY_ANALYSIS, rootNetworkNodeInfoService.getDynamicSecurityAnalysisStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(DYNAMIC_SIMULATION, rootNetworkNodeInfoService.getDynamicSimulationStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(STATE_ESTIMATION, rootNetworkNodeInfoService.getStateEstimationStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(SENSITIVITY_ANALYSIS, rootNetworkNodeInfoService.getSensitivityAnalysisStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(SHORT_CIRCUIT_ONE_BUS, rootNetworkNodeInfoService.getShortCircuitAnalysisStatus(nodeUuid, rootNetworkUuid, ShortcircuitAnalysisType.ONE_BUS));
        allComputationStatus.put(SHORT_CIRCUIT, rootNetworkNodeInfoService.getShortCircuitAnalysisStatus(nodeUuid, rootNetworkUuid, ShortcircuitAnalysisType.ALL_BUSES));
        allComputationStatus.put(VOLTAGE_INITIALIZATION, rootNetworkNodeInfoService.getVoltageInitStatus(nodeUuid, rootNetworkUuid));
        return allComputationStatus;
    }

    public void invalidateStudyRootNetwork(UUID studyUuid, UUID rootNetworkUuid, String userId, boolean updateCase) {
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        var rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        // First we unbuild all nodes
        doUnbuildNodeTree(studyUuid, rootNodeUuid, true, userId);
        // Then we erase data linked to root node on all root networks
        rootNetworkService.invalidateRootNetworkRemoteInfos(List.of(rootNetworkService.getRootNetworkInfos(rootNetworkUuid)), true, false);
        if (!updateCase) {
            rootNetworkService.updateRootNetworkIndexationStatus(studyUuid, rootNetworkUuid, RootNetworkIndexationStatus.NOT_INDEXED);
        }
        notificationService.emitRootNetworksUpdated(studyUuid);
    }

    public void assertOnQuotasAvailability(ComputationType computationType, String userId) {
        if (!shouldCheckOperationQuotas) {
            return;
        }

        Map<QuotaType, Integer> userMaxQuotas = userAdminService.getUserMaxQuota(userId);
        Map<QuotaType, Integer> userCurrentQuotas = userAdminService.getUserCurrentQuota(userId);
        QuotaType quotaType = QuotaType.mapFromComputationType(computationType);

        Integer maxComputation = userMaxQuotas.get(quotaType);
        Integer currentComputation = userCurrentQuotas.get(quotaType);

        if (maxComputation != null && currentComputation != null && currentComputation >= maxComputation) {
            throw new StudyException(MAX_OPERATION_TYPE_EXCEEDED, "Max number of " + computationType.name() + " already reached",
                                     Map.of("maxComputation", maxComputation, "currentComputation", currentComputation));
        }
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

    public Map<String, String> exportComputationParameters(UUID studyUuid, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return computationParametersService.exportParameters(studyEntity, userId);
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
}
