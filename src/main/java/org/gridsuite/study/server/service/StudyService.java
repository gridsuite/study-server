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
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.nad.NadConfigInfos;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.ModificationsSearchResultByNode;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;
import org.gridsuite.study.server.dto.sequence.NodeSequenceType;
import org.gridsuite.study.server.dto.voltageinit.ContextInfos;
import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.dto.workflow.AbstractWorkflowInfos;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationEventService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.ElementType;
import org.gridsuite.study.server.utils.PropertyUtils;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.BUS_ID_TO_ICC_VALUES;
import static org.gridsuite.study.server.StudyConstants.CURRENT_LIMIT_VIOLATIONS_INFOS;
import static org.gridsuite.study.server.dto.ComputationType.*;
import static org.gridsuite.study.server.dto.InvalidateNodeTreeParameters.ALL_WITH_BLOCK_NODES;
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

    NotificationService notificationService;

    NetworkModificationTreeService networkModificationTreeService;

    StudyServerExecutionService studyServerExecutionService;

    private final String defaultDynamicSimulationProvider;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
    private final UserAdminService userAdminService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;
    private final LoadFlowService loadflowService;
    private final ShortCircuitService shortCircuitService;
    private final VoltageInitService voltageInitService;
    private final SingleLineDiagramService singleLineDiagramService;
    private final NetworkConversionService networkConversionService;
    private final GeoDataService geoDataService;
    private final NetworkMapService networkMapService;
    private final SecurityAnalysisService securityAnalysisService;
    private final DynamicSimulationService dynamicSimulationService;
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final DynamicSimulationEventService dynamicSimulationEventService;
    private final StudyConfigService studyConfigService;
    private final DiagramGridLayoutService diagramGridLayoutService;
    private final NadConfigService nadConfigService;
    private final FilterService filterService;
    private final ActionsService actionsService;
    private final CaseService caseService;
    private final StateEstimationService stateEstimationService;
    private final PccMinService pccMinService;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final DirectoryService directoryService;

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
        VOLTAGE_INITIALIZATION("VoltageInit"),
        STATE_ESTIMATION("StateEstimation"),
        PCC_MIN("PccMin");

        public final String reportKey;

        ReportType(String reportKey) {
            this.reportKey = reportKey;
        }
    }

    private final StudyService self;

    @Autowired
    public StudyService(
        @Value("${dynamic-simulation.default-provider}") String defaultDynamicSimulationProvider,
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
        LoadFlowService loadflowService,
        ShortCircuitService shortCircuitService,
        SingleLineDiagramService singleLineDiagramService,
        NetworkConversionService networkConversionService,
        GeoDataService geoDataService,
        NetworkMapService networkMapService,
        SecurityAnalysisService securityAnalysisService,
        ActionsService actionsService,
        CaseService caseService,
        SensitivityAnalysisService sensitivityAnalysisService,
        DynamicSimulationService dynamicSimulationService,
        DynamicSecurityAnalysisService dynamicSecurityAnalysisService,
        VoltageInitService voltageInitService,
        DynamicSimulationEventService dynamicSimulationEventService,
        StudyConfigService studyConfigService,
        DiagramGridLayoutService diagramGridLayoutService,
        NadConfigService nadConfigService,
        FilterService filterService,
        StateEstimationService stateEstimationService,
        PccMinService pccMinService,
        @Lazy StudyService studyService,
        RootNetworkService rootNetworkService,
        RootNetworkNodeInfoService rootNetworkNodeInfoService,
        DirectoryService directoryService) {
        this.defaultDynamicSimulationProvider = defaultDynamicSimulationProvider;
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
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.loadflowService = loadflowService;
        this.shortCircuitService = shortCircuitService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.geoDataService = geoDataService;
        this.networkMapService = networkMapService;
        this.securityAnalysisService = securityAnalysisService;
        this.actionsService = actionsService;
        this.caseService = caseService;
        this.dynamicSimulationService = dynamicSimulationService;
        this.dynamicSecurityAnalysisService = dynamicSecurityAnalysisService;
        this.voltageInitService = voltageInitService;
        this.dynamicSimulationEventService = dynamicSimulationEventService;
        this.studyConfigService = studyConfigService;
        this.diagramGridLayoutService = diagramGridLayoutService;
        this.nadConfigService = nadConfigService;
        this.filterService = filterService;
        this.stateEstimationService = stateEstimationService;
        this.pccMinService = pccMinService;
        this.self = studyService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.directoryService = directoryService;
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

            persistNetwork(rootNetworkInfos, basicStudyInfos.getId(), NetworkModificationTreeService.FIRST_VARIANT_ID, userId, importParameters, CaseImportAction.STUDY_CREATION);
        } catch (Exception e) {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId());
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
    public void deleteRootNetworks(UUID studyUuid, List<UUID> rootNetworksUuids) {
        assertIsStudyExist(studyUuid);
        StudyEntity studyEntity = getStudy(studyUuid);
        List<RootNetworkEntity> allRootNetworkEntities = getStudyRootNetworks(studyUuid);
        if (rootNetworksUuids.size() >= allRootNetworkEntities.size()) {
            throw new StudyException(ROOT_NETWORK_DELETE_FORBIDDEN);
        }
        if (!allRootNetworkEntities.stream().map(RootNetworkEntity::getId).collect(Collectors.toSet()).containsAll(rootNetworksUuids)) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        notificationService.emitRootNetworksDeletionStarted(studyUuid, rootNetworksUuids);

        rootNetworkService.deleteRootNetworks(studyEntity, rootNetworksUuids.stream());

        notificationService.emitRootNetworksUpdated(studyUuid);
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
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParametersRaw(), CaseImportAction.ROOT_NETWORK_CREATION);
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkCreationRequestEntity);
            throw e;
        }

        notificationService.emitRootNetworksUpdated(studyUuid);
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

        if (rootNetworkInfos.getCaseInfos() != null && rootNetworkInfos.getCaseInfos().getOriginalCaseUuid() != null) {
            invalidateNodeTree(studyUuid, networkModificationTreeService.getStudyRootNodeUuid(studyUuid), rootNetworkInfos.getId(), ALL_WITH_BLOCK_NODES);
            RootNetworkRequestEntity requestEntity = rootNetworkService.insertModificationRequest(studyEntity.getId(), rootNetworkInfos, userId);
            updateRootNetworkCaseInfos(studyEntity.getId(), rootNetworkInfos, userId, requestEntity);
        } else {
            updateRootNetworkBasicInfos(studyEntity.getId(), rootNetworkInfos, false);
        }
    }

    private void updateRootNetworkCaseInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId, RootNetworkRequestEntity rootNetworkModificationRequestEntity) {
        UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), true);
        rootNetworkInfos.getCaseInfos().setCaseUuid(clonedCaseUuid);
        try {
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParametersRaw(), CaseImportAction.ROOT_NETWORK_MODIFICATION);
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkModificationRequestEntity);
            throw e;
        }
    }

    @Transactional
    public void modifyRootNetwork(UUID studyUuid, RootNetworkInfos rootNetworkInfos) {
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
        recreateNetwork(rootNetworkInfos, studyUuid, userId, importParameters, false);
    }

    /**
     * Recreates study network from existing case and import parameters
     * @param userId
     * @param studyUuid
     */
    public void recreateNetwork(String userId, UUID studyUuid, UUID rootNetworkUuid, String caseFormat) {
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        UUID caseUuid = rootNetwork.getCaseUuid();
        UUID originalCaseUuid = rootNetwork.getOriginalCaseUuid();
        RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().id(rootNetworkUuid).caseInfos(new CaseInfos(caseUuid, originalCaseUuid, null, caseFormat)).build();

        recreateNetwork(rootNetworkInfos, studyUuid, userId, null, true);
    }

    private void recreateNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String userId, Map<String, Object> importParameters, boolean shouldLoadPreviousImportParameters) {
        caseService.assertCaseExists(rootNetworkInfos.getCaseInfos().getCaseUuid());
        Map<String, Object> importParametersToUse = shouldLoadPreviousImportParameters
            ? new HashMap<>(rootNetworkService.getImportParameters(rootNetworkInfos.getId()))
            : importParameters;

        persistNetwork(rootNetworkInfos, studyUuid, null, userId, importParametersToUse, CaseImportAction.NETWORK_RECREATION);
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

            getStudyRootNetworks(duplicatedStudy.getId()).forEach(rootNetworkEntity ->
                    reindexRootNetwork(duplicatedStudy, rootNetworkEntity.getId())
            );
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        } finally {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId());
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

    private Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        DeleteStudyInfos deleteStudyInfos = null;
        if (studyCreationRequestEntity.isEmpty()) {
            List<RootNetworkInfos> rootNetworkInfos = getStudyRootNetworksInfos(studyUuid);
            // get all modification groups related to the study
            List<UUID> modificationGroupUuids = networkModificationTreeService.getAllStudyNetworkModificationNodeInfo(studyUuid).stream().map(NetworkModificationNodeInfoEntity::getModificationGroupUuid).toList();
            studyEntity.ifPresent(s -> {
                networkModificationTreeService.doDeleteTree(studyUuid);
                studyRepository.deleteById(studyUuid);
                studyInfosService.deleteByUuid(studyUuid);
                removeLoadFlowParameters(s.getLoadFlowParametersUuid());
                removeSecurityAnalysisParameters(s.getSecurityAnalysisParametersUuid());
                removeVoltageInitParameters(s.getVoltageInitParametersUuid());
                removeSensitivityAnalysisParameters(s.getSensitivityAnalysisParametersUuid());
                removeDynamicSecurityAnalysisParameters(s.getDynamicSecurityAnalysisParametersUuid());
                removeNetworkVisualizationParameters(s.getNetworkVisualizationParametersUuid());
                removeStateEstimationParameters(s.getStateEstimationParametersUuid());
                removePccMinParameters(s.getPccMinParametersUuid());
                removeSpreadsheetConfigCollection(s.getSpreadsheetConfigCollectionUuid());
                removeDiagramGridLayout(s.getDiagramGridLayoutUuid());
                removeNadConfigs(s.getNadConfigsUuids().stream().toList());
            });
            deleteStudyInfos = new DeleteStudyInfos(rootNetworkInfos, modificationGroupUuids);
        } else {
            studyCreationRequestRepository.deleteById(studyCreationRequestEntity.get().getId());
        }

        if (deleteStudyInfos == null) {
            return Optional.empty();
        } else {
            return Optional.of(deleteStudyInfos);
        }
    }

    private void removeStateEstimationParameters(@Nullable UUID uuid) {
        if (uuid != null) {
            try {
                stateEstimationService.deleteStateEstimationParameters(uuid);
            } catch (Exception e) {
                LOGGER.error("Could not delete state estimation parameters with uuid:" + uuid, e);
            }
        }
    }

    private void removePccMinParameters(@Nullable UUID uuid) {
        if (uuid != null) {
            try {
                pccMinService.deletePccMinParameters(uuid);
            } catch (Exception e) {
                LOGGER.error("Could not delete pcc min parameters with uuid:" + uuid, e);
            }
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
    public void deleteStudyIfNotCreationInProgress(UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        Optional<DeleteStudyInfos> deleteStudyInfosOpt = doDeleteStudyIfNotCreationInProgress(studyUuid);
        if (deleteStudyInfosOpt.isPresent()) {
            DeleteStudyInfos deleteStudyInfos = deleteStudyInfosOpt.get();
            startTime.set(System.nanoTime());

            // delete all distant resources linked to rootNetworks
            rootNetworkService.deleteRootNetworkRemoteInfos(deleteStudyInfos.getRootNetworkInfosList());

            // delete all distant resources linked to nodes
            studyServerExecutionService.runAsync(() -> deleteStudyInfos.getModificationGroupUuids().stream().filter(Objects::nonNull).forEach(networkModificationService::deleteModifications));

            LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));

        }
    }

    @Transactional
    public CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, CaseInfos caseInfos, UUID loadFlowParametersUuid,
                                              UUID shortCircuitParametersUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity,
                                              UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                              UUID networkVisualizationParametersUuid, UUID dynamicSecurityAnalysisParametersUuid, UUID stateEstimationParametersUuid, UUID pccMinParametersUuid,
                                              UUID spreadsheetConfigCollectionUuid, UUID diagramGridLayoutUuid, Map<String, String> importParameters, UUID importReportUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkInfos.getNetworkUuid());
        Objects.requireNonNull(networkInfos.getNetworkId());
        Objects.requireNonNull(caseInfos.getCaseFormat());
        Objects.requireNonNull(caseInfos.getCaseUuid());
        Objects.requireNonNull(importParameters);

        StudyEntity studyEntity = saveStudyThenCreateBasicTree(studyUuid, networkInfos,
                caseInfos, loadFlowParametersUuid,
                shortCircuitParametersUuid, dynamicSimulationParametersEntity,
                voltageInitParametersUuid, securityAnalysisParametersUuid,
                sensitivityAnalysisParametersUuid, networkVisualizationParametersUuid, dynamicSecurityAnalysisParametersUuid,
                stateEstimationParametersUuid, pccMinParametersUuid, spreadsheetConfigCollectionUuid, diagramGridLayoutUuid, importParameters, importReportUuid);

        // Need to deal with the study creation (with a default root network ?)
        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    @Transactional
    public CreatedStudyBasicInfos updateNetwork(UUID studyUuid, UUID rootNetworkUuid, NetworkInfos networkInfos, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        RootNetworkEntity rootNetworkEntity = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));

        rootNetworkService.updateNetwork(rootNetworkEntity, networkInfos);

        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudyNetworkRecreationDone(studyEntity.getId(), userId);

        return createdStudyBasicInfos;
    }

    public UUID createGridLayoutFromNadDiagram(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getDiagramConfigId() != null) {
            UUID sourceNadConfig = userProfileInfos.getDiagramConfigId();
            try {
                UUID clonedNadConfig = singleLineDiagramService.duplicateNadConfig(sourceNadConfig);
                String nadConfigName = directoryService.getElementName(sourceNadConfig);
                return studyConfigService.createGridLayoutFromNadDiagram(sourceNadConfig, clonedNadConfig, nadConfigName);
            } catch (Exception e) {
                LOGGER.error(String.format("Could not create a diagram grid layout cloning NAD elment id '%s' from user/profile '%s/%s'. No layout created",
                        sourceNadConfig, userId, userProfileInfos.getName()), e);
            }
        }
        return null;
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

        StudyEntity newStudyEntity = duplicateStudyEntity(sourceStudy, studyInfos.getId(), userId);
        rootNetworkService.duplicateStudyRootNetworks(newStudyEntity, sourceStudy);
        networkModificationTreeService.duplicateStudyNodes(newStudyEntity, sourceStudy);
        duplicateStudyNodeAliases(newStudyEntity, sourceStudy);

        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(newStudyEntity);
        studyInfosService.add(createdStudyBasicInfos);
        notificationService.emitStudiesChanged(studyInfos.getId(), userId);

        return newStudyEntity;
    }

    private StudyEntity duplicateStudyEntity(StudyEntity sourceStudyEntity, UUID newStudyId, String userId) {
        UUID copiedLoadFlowParametersUuid = null;
        if (sourceStudyEntity.getLoadFlowParametersUuid() != null) {
            copiedLoadFlowParametersUuid = loadflowService.duplicateLoadFlowParameters(sourceStudyEntity.getLoadFlowParametersUuid());
        }

        UUID copiedShortCircuitParametersUuid = null;
        if (sourceStudyEntity.getShortCircuitParametersUuid() != null) {
            copiedShortCircuitParametersUuid = shortCircuitService.duplicateParameters(sourceStudyEntity.getShortCircuitParametersUuid());
        }

        UUID copiedSecurityAnalysisParametersUuid = null;
        if (sourceStudyEntity.getSecurityAnalysisParametersUuid() != null) {
            copiedSecurityAnalysisParametersUuid = securityAnalysisService.duplicateSecurityAnalysisParameters(sourceStudyEntity.getSecurityAnalysisParametersUuid());
        }

        UUID copiedSensitivityAnalysisParametersUuid = null;
        if (sourceStudyEntity.getSensitivityAnalysisParametersUuid() != null) {
            copiedSensitivityAnalysisParametersUuid = sensitivityAnalysisService.duplicateSensitivityAnalysisParameters(sourceStudyEntity.getSensitivityAnalysisParametersUuid());
        }

        UUID copiedVoltageInitParametersUuid = null;
        if (sourceStudyEntity.getVoltageInitParametersUuid() != null) {
            copiedVoltageInitParametersUuid = voltageInitService.duplicateVoltageInitParameters(sourceStudyEntity.getVoltageInitParametersUuid());
        }

        UUID copiedNetworkVisualizationParametersUuid = null;
        if (sourceStudyEntity.getNetworkVisualizationParametersUuid() != null) {
            copiedNetworkVisualizationParametersUuid = studyConfigService.duplicateNetworkVisualizationParameters(sourceStudyEntity.getNetworkVisualizationParametersUuid());
        }

        UUID copiedSpreadsheetConfigCollectionUuid = null;
        if (sourceStudyEntity.getSpreadsheetConfigCollectionUuid() != null) {
            copiedSpreadsheetConfigCollectionUuid = studyConfigService.duplicateSpreadsheetConfigCollection(sourceStudyEntity.getSpreadsheetConfigCollectionUuid());
        }

        DynamicSimulationParametersInfos dynamicSimulationParameters = sourceStudyEntity.getDynamicSimulationParameters() != null ? DynamicSimulationService.fromEntity(sourceStudyEntity.getDynamicSimulationParameters(), objectMapper) : DynamicSimulationService.getDefaultDynamicSimulationParameters();

        UUID copiedStateEstimationParametersUuid = null;
        if (sourceStudyEntity.getStateEstimationParametersUuid() != null) {
            copiedStateEstimationParametersUuid = stateEstimationService.duplicateStateEstimationParameters(sourceStudyEntity.getStateEstimationParametersUuid());
        }

        UUID copiedPccMinParametersUuid = null;
        if (sourceStudyEntity.getPccMinParametersUuid() != null) {
            copiedPccMinParametersUuid = pccMinService.duplicatePccMinParameters(sourceStudyEntity.getPccMinParametersUuid());
        }

        UserProfileInfos userProfile = getUserProfile(userId);
        UUID diagramGridLayoutId = createGridLayoutFromNadDiagram(userId, userProfile);

        return studyRepository.save(StudyEntity.builder()
            .id(newStudyId)
            .loadFlowParametersUuid(copiedLoadFlowParametersUuid)
            .securityAnalysisParametersUuid(copiedSecurityAnalysisParametersUuid)
            .dynamicSimulationProvider(sourceStudyEntity.getDynamicSimulationProvider())
            .dynamicSimulationParameters(DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper))
            .shortCircuitParametersUuid(copiedShortCircuitParametersUuid)
            .voltageInitParametersUuid(copiedVoltageInitParametersUuid)
            .sensitivityAnalysisParametersUuid(copiedSensitivityAnalysisParametersUuid)
            .networkVisualizationParametersUuid(copiedNetworkVisualizationParametersUuid)
            .spreadsheetConfigCollectionUuid(copiedSpreadsheetConfigCollectionUuid)
            .stateEstimationParametersUuid(copiedStateEstimationParametersUuid)
            .pccMinParametersUuid(copiedPccMinParametersUuid)
            .diagramGridLayoutUuid(diagramGridLayoutId)
            .build());
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid, String firstRootNetworkName) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(studyUuid, firstRootNetworkName);
        notificationService.emitStudiesChanged(newStudy.getId(), userId);
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
            return singleLineDiagramService.generateVoltageLevelSvgAndMetadata(networkUuid, variantId, voltageLevelId, populateSldRequestInfos(sldRequestInfos, voltageLevelId, nodeUuid, rootNetworkUuid));
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
            shortCircuitService.getVoltageLevelIccValues(shortCircuitResultUuid, voltageLevelId) : Map.of();
    }

    private void persistNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String variantId, String userId, Map<String, Object> importParameters, CaseImportAction caseImportAction) {
        networkConversionService.persistNetwork(rootNetworkInfos, studyUuid, variantId, userId, UUID.randomUUID(), importParameters, caseImportAction);
    }

    public String getLinesGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> linesIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getLinesGraphics(networkUuid, variantId, linesIds);
    }

    public String getSubstationsGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getSubstationsGraphics(networkUuid, variantId, substationsIds);
    }

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
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);
        return networkMapService.getElementsInfos(
            rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
            substationsIds,
            elementType,
            nominalVoltages,
            infoType,
            getOptionalParameters(elementType, studyEntity, loadFlowParameters));
    }

    public String getNetworkElementInfos(UUID studyUuid,
                                         UUID nodeUuid,
                                         UUID rootNetworkUuid,
                                         String elementType,
                                         InfoTypeParameters infoTypeParameters,
                                         String elementId,
                                         boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);
        return networkMapService.getElementInfos(
            rootNetworkService.getNetworkUuid(rootNetworkUuid),
            networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
            elementType,
            infoTypeParameters.getInfoType(),
            getSingleElementOptionalParameters(elementId, elementType, studyEntity, nodeUuid, rootNetworkUuid, loadFlowParameters),
            elementId);
    }

    private Map<String, String> getSingleElementOptionalParameters(String elementId, String elementType, StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, LoadFlowParameters loadFlowParameters) {
        Map<String, String> additionalParameters = getOptionalParameters(elementType, studyEntity, loadFlowParameters);

        if (elementType.equalsIgnoreCase("voltage_level")) {
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

    public String getAllMapData(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);
        Map<String, Map<String, String>> optionalParameters = new HashMap<>();
        Stream.of(
            String.valueOf(ElementType.BRANCH),
            String.valueOf(ElementType.LINE),
            String.valueOf(ElementType.TIE_LINE),
            String.valueOf(ElementType.TWO_WINDINGS_TRANSFORMER)
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

    @Transactional
    public void rerunLoadflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, Boolean withRatioTapChangers, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (networkModificationTreeService.isSecurityNode(nodeUuid)) {
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.builder()
                .invalidationMode(InvalidationMode.ALL)
                .withBlockedNode(true)
                .computationsInvalidationMode(ComputationsInvalidationMode.PRESERVE_LOAD_FLOW_RESULTS)
                .build());

            buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId, RerunLoadFlowInfos.builder()
                .loadflowResultUuid(loadflowResultUuid)
                .withRatioTapChangers(withRatioTapChangers)
                .userId(userId)
                .build());
        } else {
            networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);
            handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
        }
    }

    @Transactional
    public UUID createLoadflowRunningStatus(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean withRatioTapChangers) {
        // since invalidating and building nodes can be long, we create loadflow result status before execution long operations
        UUID loadflowResultUuid = loadflowService.createRunningStatus();
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
        return loadflowResultUuid;
    }

    @Transactional
    public void deleteLoadflowResult(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid) {
        loadflowService.deleteLoadFlowResults(List.of(loadflowResultUuid));
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, null, null);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
    }

    @Transactional
    public void sendLoadflowRequestWorflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
    }

    @Transactional
    public void sendLoadflowRequest(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (networkModificationTreeService.isSecurityNode(nodeUuid)) {
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.builder()
                .invalidationMode(InvalidationMode.ONLY_CHILDREN_BUILD_STATUS)
                .withBlockedNode(true)
                .computationsInvalidationMode(ComputationsInvalidationMode.ALL)
                .build());
        } else {
            networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);
        }

        handleLoadflowRequest(studyEntity, nodeUuid, rootNetworkUuid, loadflowResultUuid, withRatioTapChangers, userId);
    }

    private void handleLoadflowRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, UUID loadflowResultUuid, boolean withRatioTapChangers, String userId) {
        UUID lfParametersUuid = loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        UUID lfReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(LOAD_FLOW.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        boolean isSecurityNode = networkModificationTreeService.isSecurityNode(nodeUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW, lfReportUuid);
        UUID result = loadflowService.runLoadFlow(new NodeReceiver(nodeUuid, rootNetworkUuid), loadflowResultUuid, new VariantInfos(networkUuid, variantId), new LoadFlowService.ParametersInfos(lfParametersUuid, withRatioTapChangers, isSecurityNode), lfReportUuid, userId);
        rootNetworkNodeInfoService.updateLoadflowResultUuid(nodeUuid, rootNetworkUuid, result, withRatioTapChangers);

        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, LOAD_FLOW.getUpdateStatusType());
    }

    public UUID exportNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String fileName, String format, String userId, String parametersJson) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID exportUuid = networkConversionService.exportNetwork(networkUuid, studyUuid, nodeUuid, rootNetworkUuid, variantId, fileName, format, userId, parametersJson);
        rootNetworkNodeInfoService.updateExportNetworkStatus(nodeUuid, rootNetworkUuid, exportUuid, ExportNetworkStatus.RUNNING);
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

    public String getLoadFlowProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowService.getLoadFlowProvider(studyEntity.getLoadFlowParametersUuid());
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

    @Transactional
    public void assertCanUpdateModifications(UUID studyUuid, UUID nodeUuid) {
        assertIsNodeNotReadOnly(nodeUuid);
        assertNoBuildNoComputationForNode(studyUuid, nodeUuid);
    }

    public void assertIsStudyAndNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        assertIsNodeExist(studyUuid, nodeUuid);
    }

    public void assertNoBuildNoComputationForRootNetworkNode(UUID nodeUuid, UUID rootNetworkUuid) {
        rootNetworkNodeInfoService.assertComputationNotRunning(nodeUuid, rootNetworkUuid);
        rootNetworkNodeInfoService.assertNetworkNodeIsNotBuilding(rootNetworkUuid, nodeUuid);
    }

    @Transactional(readOnly = true)
    public void assertNoBlockedNodeInTree(UUID nodeUuid, UUID rootNetworkUuid) {
        rootNetworkNodeInfoService.assertNoBlockedNode(rootNetworkUuid, networkModificationTreeService.getNodeTreeUuids(nodeUuid));
    }

    @Transactional(readOnly = true)
    public void assertNoBlockedNodeInStudy(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        List<UUID> nodesUuids = networkModificationTreeService.getNodeTreeUuids(nodeUuid);
        getStudyRootNetworks(studyUuid).stream().forEach(rootNetwork ->
            rootNetworkNodeInfoService.assertNoBlockedNode(rootNetwork.getId(), nodesUuids)
        );
    }

    public void assertNoBuildNoComputationForNode(UUID studyUuid, UUID nodeUuid) {
        getStudyRootNetworks(studyUuid).forEach(rootNetwork ->
            rootNetworkNodeInfoService.assertComputationNotRunning(nodeUuid, rootNetwork.getId())
        );
        rootNetworkNodeInfoService.assertNoRootNetworkNodeIsBuilding(studyUuid);
    }

    public void assertRootNodeOrBuiltNode(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        if (!(networkModificationTreeService.getStudyRootNodeUuid(studyUuid).equals(nodeUuid)
                || networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt())) {
            throw new StudyException(NODE_NOT_BUILT);
        }
    }

    public LoadFlowParameters getLoadFlowParameters(StudyEntity studyEntity) {
        LoadFlowParametersInfos lfParameters = getLoadFlowParametersInfos(studyEntity);
        return lfParameters.getCommonParameters();
    }

    @Transactional
    public LoadFlowParametersInfos getLoadFlowParametersInfos(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return getLoadFlowParametersInfos(studyEntity);
    }

    @Transactional(readOnly = true)
    public UUID getLoadFlowParametersId(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
    }

    public LoadFlowParametersInfos getLoadFlowParametersInfos(StudyEntity studyEntity) {
        UUID loadFlowParamsUuid = loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        return loadflowService.getLoadFlowParameters(loadFlowParamsUuid);
    }

    @Transactional
    public String getSecurityAnalysisParametersValues(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return securityAnalysisService.getSecurityAnalysisParameters(securityAnalysisService.getSecurityAnalysisParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public boolean setSecurityAnalysisParametersValues(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = createOrUpdateSecurityAnalysisParameters(studyEntity, parameters, userId);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SECURITY_ANALYSIS);
        return userProfileIssue;
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

    @Transactional
    public boolean setLoadFlowParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = createOrUpdateLoadFlowParameters(studyEntity, parameters, userId);
        invalidateAllStudyLoadFlowStatus(studyUuid);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
        invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
        invalidateDynamicSecurityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, LOAD_FLOW);
        return userProfileIssue;
    }

    public String getDefaultLoadflowProvider(String userId) {
        if (userId != null) {
            UserProfileInfos userProfileInfos = userAdminService.getUserProfile(userId);
            if (userProfileInfos.getLoadFlowParameterId() != null) {
                try {
                    return loadflowService.getLoadFlowParameters(userProfileInfos.getLoadFlowParameterId()).getProvider();
                } catch (Exception e) {
                    LOGGER.error(String.format("Could not get loadflow parameters with id '%s' from user/profile '%s/%s'. Using default provider",
                            userProfileInfos.getLoadFlowParameterId(), userId, userProfileInfos.getName()), e);
                    // in case of read error (ex: wrong/dangling uuid in the profile), move on with default provider below
                }
            }
        }
        return loadflowService.getLoadFlowDefaultProvider();
    }

    private void updateProvider(UUID studyUuid, String userId, Consumer<StudyEntity> providerSetter) {
        StudyEntity studyEntity = getStudy(studyUuid);
        providerSetter.accept(studyEntity);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void updateLoadFlowProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            loadflowService.updateLoadFlowProvider(studyEntity.getLoadFlowParametersUuid(), provider);
            invalidateAllStudyLoadFlowStatus(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
            notificationService.emitComputationParamsChanged(studyUuid, LOAD_FLOW);

        });
    }

    public String getDefaultSecurityAnalysisProvider() {
        return securityAnalysisService.getSecurityAnalysisDefaultProvider();
    }

    public void updateSecurityAnalysisProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            securityAnalysisService.updateSecurityAnalysisProvider(studyEntity.getSecurityAnalysisParametersUuid(), provider);
            invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
            notificationService.emitComputationParamsChanged(studyUuid, SECURITY_ANALYSIS);
        });
    }

    public String getDefaultSensitivityAnalysisProvider() {
        return sensitivityAnalysisService.getSensitivityAnalysisDefaultProvider();
    }

    public String getDefaultDynamicSimulationProvider() {
        return defaultDynamicSimulationProvider;
    }

    public String getDynamicSimulationProvider(UUID studyUuid) {
        return getStudy(studyUuid).getDynamicSimulationProvider();
    }

    @Transactional
    public void updateDynamicSimulationProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setDynamicSimulationProvider(provider != null ? provider : defaultDynamicSimulationProvider);
            invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
            invalidateDynamicSecurityAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
            notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
            notificationService.emitComputationParamsChanged(studyUuid, DYNAMIC_SIMULATION);
        });
    }

    public String getDefaultDynamicSecurityAnalysisProvider(String userId) {
        if (userId != null) {
            UserProfileInfos userProfileInfos = userAdminService.getUserProfile(userId);
            if (userProfileInfos.getDynamicSecurityAnalysisParameterId() != null) {
                try {
                    return dynamicSecurityAnalysisService.getProvider(userProfileInfos.getDynamicSecurityAnalysisParameterId());
                } catch (Exception e) {
                    LOGGER.error(String.format("Could not get dynamic security analysis provider with id '%s' from user/profile '%s/%s'. Using default provider",
                            userProfileInfos.getLoadFlowParameterId(), userId, userProfileInfos.getName()), e);
                    // in case of read error (ex: wrong/dangling uuid in the profile), move on with default provider below
                }
            }
        }
        return dynamicSecurityAnalysisService.getDefaultProvider();
    }

    public String getDynamicSecurityAnalysisProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicSecurityAnalysisService.getProvider(studyEntity.getDynamicSecurityAnalysisParametersUuid());
    }

    public void updateDynamicSecurityAnalysisProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            dynamicSecurityAnalysisService.updateProvider(studyEntity.getDynamicSecurityAnalysisParametersUuid(), provider);
            invalidateDynamicSecurityAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
            notificationService.emitComputationParamsChanged(studyUuid, DYNAMIC_SECURITY_ANALYSIS);

        });
    }

    @Transactional
    public String getShortCircuitParametersInfo(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (studyEntity.getShortCircuitParametersUuid() == null) {
            studyEntity.setShortCircuitParametersUuid(shortCircuitService.createParameters(null));
            studyRepository.save(studyEntity);
        }
        return shortCircuitService.getParameters(studyEntity.getShortCircuitParametersUuid());
    }

    @Transactional
    public boolean setShortCircuitParameters(UUID studyUuid, @Nullable String shortCircuitParametersInfos, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = createOrUpdateShortcircuitParameters(studyEntity, shortCircuitParametersInfos, userId);
        invalidateShortCircuitStatusOnAllNodes(studyUuid);
        invalidatePccMinStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SHORT_CIRCUIT);
        return userProfileIssue;
    }

    public boolean createOrUpdateShortcircuitParameters(StudyEntity studyEntity, String parameters, String userId) {
        /* +-----------------------+----------------+-----------------------------------------+
         * | entity.parametersUuid | parametersInfo | action                                  |
         * | no                    | no             | create default ones                     |
         * | no                    | yes            | create new ones                         |
         * | yes                   | no             | reset existing ones (with default ones) |
         * | yes                   | yes            | update existing ones                    |
         * +-----------------------+----------------+-----------------------------------------+
         */
        boolean userProfileIssue = false;
        UUID existingShortcircuitParametersUuid = studyEntity.getShortCircuitParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getShortcircuitParameterId() != null) {
            // reset case, with existing profile, having default short circuit params
            try {
                UUID shortcircuitParametersFromProfileUuid = shortCircuitService.duplicateParameters(userProfileInfos.getShortcircuitParameterId());
                studyEntity.setShortCircuitParametersUuid(shortcircuitParametersFromProfileUuid);
                removeShortcircuitParameters(existingShortcircuitParametersUuid);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate short circuit parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getShortcircuitParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }

        if (existingShortcircuitParametersUuid == null) {
            existingShortcircuitParametersUuid = shortCircuitService.createParameters(parameters);
            studyEntity.setShortCircuitParametersUuid(existingShortcircuitParametersUuid);
        } else {
            shortCircuitService.updateParameters(existingShortcircuitParametersUuid, parameters);
        }
        return userProfileIssue;
    }

    private void removeShortcircuitParameters(@Nullable UUID shortcircuitParametersUuid) {
        if (shortcircuitParametersUuid != null) {
            try {
                shortCircuitService.deleteShortcircuitParameters(shortcircuitParametersUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove short circuit parameters with uuid:" + shortcircuitParametersUuid, e);
            }
        }
    }

    @Transactional
    public UUID runSecurityAnalysis(@NonNull UUID studyUuid, @NonNull List<String> contingencyListNames, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleSecurityAnalysisRequest(study, nodeUuid, rootNetworkUuid, contingencyListNames, userId);
    }

    private UUID handleSecurityAnalysisRequest(StudyEntity study, UUID nodeUuid, UUID rootNetworkUuid, List<String> contingencyListNames, String userId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID saReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(SECURITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, SECURITY_ANALYSIS, saReportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, SECURITY_ANALYSIS);
        if (prevResultUuid != null) {
            securityAnalysisService.deleteSecurityAnalysisResults(List.of(prevResultUuid));
        }

        var runSecurityAnalysisParametersInfos = new RunSecurityAnalysisParametersInfos(study.getSecurityAnalysisParametersUuid(), study.getLoadFlowParametersUuid(), contingencyListNames);
        UUID result = securityAnalysisService.runSecurityAnalysis(networkUuid, variantId, runSecurityAnalysisParametersInfos,
                new ReportInfos(saReportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, SECURITY_ANALYSIS);
        notificationService.emitStudyChanged(study.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        return result;
    }

    public Integer getContingencyCount(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid, UUID rootNetworkUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return actionsService.getContingencyCount(networkuuid, variantId, contingencyListNames);
    }

    public List<LimitViolationInfos> getLimitViolations(@NonNull UUID nodeUuid, UUID rootNetworkUuid, String filters, String globalFilters, Sort sort) {
        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        return loadflowService.getLimitViolations(resultUuid, filters, globalFilters, sort, networkuuid, variantId);
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

    @Transactional
    public UUID saveNadConfig(UUID studyUuid, NadConfigInfos nadConfig) {
        StudyEntity studyEntity = getStudy(studyUuid);

        UUID nadConfigUuid = nadConfigService.saveNadConfig(nadConfig);

        studyEntity.getNadConfigsUuids().add(nadConfigUuid);

        return nadConfigUuid;
    }

    @Transactional
    public void deleteNadConfig(UUID studyUuid, UUID nadConfigUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);

        nadConfigService.deleteNadConfigs(List.of(nadConfigUuid));

        studyEntity.getNadConfigsUuids().remove(nadConfigUuid);
    }

    private void removeNadConfigs(List<UUID> nadConfigUuids) {
        if (nadConfigUuids != null && !nadConfigUuids.isEmpty()) {
            try {
                nadConfigService.deleteNadConfigs(nadConfigUuids);
            } catch (Exception e) {
                LOGGER.error("Could not remove NAD configs with uuids:" + nadConfigUuids, e);
            }
        }
    }

    private void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        loadflowService.invalidateLoadFlowStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, LOAD_FLOW));
    }

    public void invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        securityAnalysisService.invalidateSaStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SECURITY_ANALYSIS));
    }

    public void invalidateSensitivityAnalysisStatusOnAllNodes(UUID studyUuid) {
        sensitivityAnalysisService.invalidateSensitivityAnalysisStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SENSITIVITY_ANALYSIS));
    }

    public void invalidateDynamicSimulationStatusOnAllNodes(UUID studyUuid) {
        dynamicSimulationService.invalidateStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, DYNAMIC_SIMULATION));
    }

    public void invalidateDynamicSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        dynamicSecurityAnalysisService.invalidateStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, DYNAMIC_SECURITY_ANALYSIS));
    }

    public void invalidateAllStudyLoadFlowStatus(UUID studyUuid) {
        invalidateSecurityNodeTreeWithLoadFlowResults(studyUuid);
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
    }

    private void invalidateSecurityNodeTreeWithLoadFlowResults(UUID studyUuid) {
        Map<UUID, List<RootNetworkNodeInfoEntity>> rootNetworkNodeInfosWithLFByRootNetwork = rootNetworkNodeInfoService.getAllByStudyUuidWithLoadFlowResultsNotNull(studyUuid).stream()
            .collect(Collectors.groupingBy(rootNetworkNodeInfoEntity -> rootNetworkNodeInfoEntity.getRootNetwork().getId()));

        rootNetworkNodeInfosWithLFByRootNetwork.forEach((rootNetworkUuid, rootNetworkNodeInfoEntities) -> {
            // since invalidateNodeTree is costly, optimise node tree invalidation by keeping only least deep parents from the set to invalidate them and all their children
            Set<NodeEntity> nodesToInvalidate = rootNetworkNodeInfoEntities.stream().map(rootNetworkNodeInfoEntity -> rootNetworkNodeInfoEntity.getNodeInfo().getNode()).collect(Collectors.toSet());
            Set<NodeEntity> nodeTreesToInvalidate = new HashSet<>(nodesToInvalidate);

            nodesToInvalidate.forEach(node -> {
                NodeEntity currentNode = node.getParentNode();
                while (currentNode != null) {
                    if (nodesToInvalidate.contains(currentNode)) {
                        nodeTreesToInvalidate.remove(node);
                        break;
                    }
                    currentNode = currentNode.getParentNode();
                }
            });

            nodeTreesToInvalidate.forEach(node -> invalidateNodeTree(studyUuid, node.getIdNode(), rootNetworkUuid));
        });
    }

    public void invalidateVoltageInitStatusOnAllNodes(UUID studyUuid) {
        voltageInitService.invalidateVoltageInitStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, VOLTAGE_INITIALIZATION));
    }

    public void invalidateStateEstimationStatusOnAllNodes(UUID studyUuid) {
        stateEstimationService.invalidateStateEstimationStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, STATE_ESTIMATION));
    }

    public void invalidatePccMinStatusOnAllNodes(UUID studyUuid) {
        pccMinService.invalidatePccMinStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, PCC_MIN));
    }

    private StudyEntity updateRootNetworkIndexationStatus(StudyEntity studyEntity, RootNetworkEntity rootNetworkEntity, RootNetworkIndexationStatus indexationStatus) {
        rootNetworkEntity.setIndexationStatus(indexationStatus);
        notificationService.emitRootNetworkIndexationStatusChanged(studyEntity.getId(), rootNetworkEntity.getId(), indexationStatus);
        return studyEntity;
    }

    public StudyEntity updateRootNetworkIndexationStatus(UUID studyUuid, UUID rootNetworkUuid, RootNetworkIndexationStatus indexationStatus) {
        return updateRootNetworkIndexationStatus(getStudy(studyUuid), rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")), indexationStatus);
    }

    private StudyEntity saveStudyThenCreateBasicTree(UUID studyUuid, NetworkInfos networkInfos,
                                                    CaseInfos caseInfos, UUID loadFlowParametersUuid,
                                                    UUID shortCircuitParametersUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity,
                                                    UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                                    UUID networkVisualizationParametersUuid, UUID dynamicSecurityAnalysisParametersUuid, UUID stateEstimationParametersUuid, UUID pccMinParametersUuid,
                                                    UUID spreadsheetConfigCollectionUuid, UUID diagramGridLayoutUuid, Map<String, String> importParameters, UUID importReportUuid) {

        StudyEntity studyEntity = StudyEntity.builder()
                .id(studyUuid)
                .dynamicSimulationProvider(defaultDynamicSimulationProvider)
                .loadFlowParametersUuid(loadFlowParametersUuid)
                .shortCircuitParametersUuid(shortCircuitParametersUuid)
                .dynamicSimulationParameters(dynamicSimulationParametersEntity)
                .voltageInitParametersUuid(voltageInitParametersUuid)
                .securityAnalysisParametersUuid(securityAnalysisParametersUuid)
                .sensitivityAnalysisParametersUuid(sensitivityAnalysisParametersUuid)
                .voltageInitParameters(new StudyVoltageInitParametersEntity())
                .networkVisualizationParametersUuid(networkVisualizationParametersUuid)
                .dynamicSecurityAnalysisParametersUuid(dynamicSecurityAnalysisParametersUuid)
                .stateEstimationParametersUuid(stateEstimationParametersUuid)
                .pccMinParametersUuid(pccMinParametersUuid)
                .spreadsheetConfigCollectionUuid(spreadsheetConfigCollectionUuid)
                .diagramGridLayoutUuid(diagramGridLayoutUuid)
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
        rootNetworkService.createRootNetwork(studyEntity, RootNetworkInfos.builder().id(UUID.randomUUID()).name(firstRootNetworkName).networkInfos(networkInfos).caseInfos(caseInfos).reportUuid(importReportUuid).importParameters(importParameters).tag("1").build());
        networkModificationTreeService.createBasicTree(study);

        return study;
    }

    void updateComputationResultUuid(UUID nodeUuid, UUID rootNetworkUuid, UUID computationResultUuid, ComputationType computationType) {
        rootNetworkNodeInfoService.updateComputationResultUuid(nodeUuid, rootNetworkUuid, computationResultUuid, computationType);
    }

    public List<String> getResultEnumValues(UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType, String enumName) {
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(enumName);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, computationType);
        if (resultUuid != null) {
            return switch (computationType) {
                case LOAD_FLOW -> loadflowService.getEnumValues(enumName, resultUuid);
                case SECURITY_ANALYSIS -> securityAnalysisService.getEnumValues(enumName, resultUuid);
                case SHORT_CIRCUIT, SHORT_CIRCUIT_ONE_BUS -> shortCircuitService.getEnumValues(enumName, resultUuid);
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

    public boolean createOrUpdateLoadFlowParameters(StudyEntity studyEntity, String parameters, String userId) {
        boolean userProfileIssue = false;
        UUID existingLoadFlowParametersUuid = studyEntity.getLoadFlowParametersUuid();

        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getLoadFlowParameterId() != null) {
            // reset case, with existing profile, having default LF params
            try {
                UUID loadFlowParametersFromProfileUuid = loadflowService.duplicateLoadFlowParameters(userProfileInfos.getLoadFlowParameterId());
                if (existingLoadFlowParametersUuid != null) {
                    //For a reset to defaultValues we need to keep the provider if it exists because it's updated separately
                    String keptProvider = loadflowService.getLoadFlowParameters(existingLoadFlowParametersUuid).getProvider();
                    loadflowService.updateLoadFlowProvider(loadFlowParametersFromProfileUuid, keptProvider);
                }
                studyEntity.setLoadFlowParametersUuid(loadFlowParametersFromProfileUuid);
                removeLoadFlowParameters(existingLoadFlowParametersUuid);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate loadflow parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getLoadFlowParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }

        if (existingLoadFlowParametersUuid == null) {
            existingLoadFlowParametersUuid = loadflowService.createLoadFlowParameters(parameters);
            studyEntity.setLoadFlowParametersUuid(existingLoadFlowParametersUuid);
        } else {
            loadflowService.updateLoadFlowParameters(existingLoadFlowParametersUuid, parameters);
        }
        return userProfileIssue;
    }

    private void removeLoadFlowParameters(@Nullable UUID lfParametersUuid) {
        if (lfParametersUuid != null) {
            try {
                loadflowService.deleteLoadFlowParameters(lfParametersUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove loadflow Parameters with uuid:" + lfParametersUuid, e);
            }
        }
    }

    public void updateDynamicSimulationParameters(UUID studyUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> {
            studyEntity1.setDynamicSimulationParameters(dynamicSimulationParametersEntity);
            invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        });
    }

    public boolean createOrUpdateVoltageInitParameters(StudyEntity studyEntity, VoltageInitParametersInfos parameters, String userId) {
        boolean userProfileIssue = false;
        UUID existingVoltageInitParametersUuid = studyEntity.getVoltageInitParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getVoltageInitParameterId() != null) {
            // reset case, with existing profile, having default voltage init params
            try {
                UUID voltageInitParametersFromProfileUuid = voltageInitService.duplicateVoltageInitParameters(userProfileInfos.getVoltageInitParameterId());
                studyEntity.setVoltageInitParametersUuid(voltageInitParametersFromProfileUuid);
                removeVoltageInitParameters(existingVoltageInitParametersUuid);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate voltage init parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getVoltageInitParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }

        if (existingVoltageInitParametersUuid == null) {
            existingVoltageInitParametersUuid = voltageInitService.createVoltageInitParameters(parameters);
            studyEntity.setVoltageInitParametersUuid(existingVoltageInitParametersUuid);
        } else {
            VoltageInitParametersInfos oldParameters = voltageInitService.getVoltageInitParameters(existingVoltageInitParametersUuid);
            if (Objects.isNull(parameters) || !parameters.equals(oldParameters)) {
                voltageInitService.updateVoltageInitParameters(existingVoltageInitParametersUuid, parameters);
            }
        }

        return userProfileIssue;
    }

    private void removeVoltageInitParameters(@Nullable UUID voltageInitParametersUuid) {
        if (voltageInitParametersUuid != null) {
            try {
                voltageInitService.deleteVoltageInitParameters(voltageInitParametersUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove voltage init parameters with uuid:" + voltageInitParametersUuid, e);
            }
        }
    }

    public boolean createOrUpdateSecurityAnalysisParameters(StudyEntity studyEntity, String parameters, String userId) {
        boolean userProfileIssue = false;
        UUID existingSecurityAnalysisParametersUuid = studyEntity.getSecurityAnalysisParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getSecurityAnalysisParameterId() != null) {
            // reset case, with existing profile, having default security analysis params
            try {
                UUID securityAnalysisParametersFromProfileUuid = securityAnalysisService.duplicateSecurityAnalysisParameters(userProfileInfos.getSecurityAnalysisParameterId());
                studyEntity.setSecurityAnalysisParametersUuid(securityAnalysisParametersFromProfileUuid);
                removeSecurityAnalysisParameters(existingSecurityAnalysisParametersUuid);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate security analysis parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getSecurityAnalysisParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }

        if (existingSecurityAnalysisParametersUuid == null) {
            existingSecurityAnalysisParametersUuid = securityAnalysisService.createSecurityAnalysisParameters(parameters);
            studyEntity.setSecurityAnalysisParametersUuid(existingSecurityAnalysisParametersUuid);
        } else {
            securityAnalysisService.updateSecurityAnalysisParameters(existingSecurityAnalysisParametersUuid, parameters);
        }

        return userProfileIssue;
    }

    private void removeSecurityAnalysisParameters(@Nullable UUID securityAnalysisParametersUuid) {
        if (securityAnalysisParametersUuid != null) {
            try {
                securityAnalysisService.deleteSecurityAnalysisParameters(securityAnalysisParametersUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove security analysis parameters with uuid:" + securityAnalysisParametersUuid, e);
            }
        }
    }

    @Transactional
    public void createNetworkModification(UUID studyUuid, UUID nodeUuid, String createModificationAttributes, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            List<RootNetworkEntity> studyRootNetworkEntities = getStudyRootNetworks(studyUuid);

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
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            networkModificationService.updateModification(updateModificationAttributes, modificationUuid);
            invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
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
        buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId, null);
    }

    private void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId, AbstractWorkflowInfos workflowInfos) {
        assertCanBuildNode(studyUuid, rootNetworkUuid, userId);
        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(nodeUuid, rootNetworkUuid);

        // Store all reports (inherited + new) for this node
        networkModificationTreeService.setModificationReports(nodeUuid, rootNetworkUuid, buildInfos.getAllReportsAsMap());
        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
        try {
            networkModificationService.buildNode(nodeUuid, rootNetworkUuid, buildInfos, workflowInfos);
        } catch (Exception e) {
            networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT));
            throw e;
        }
    }

    public void handleBuildSuccess(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, NetworkModificationResult networkModificationResult) {
        LOGGER.info("Build completed for node '{}'", nodeUuid);

        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid,
            NodeBuildStatus.from(networkModificationResult.getLastGroupApplicationStatus(), networkModificationResult.getApplicationStatus()));

        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_BUILD_COMPLETED, networkModificationResult.getImpactedSubstationsIds());
    }

    public void assertCanBuildNode(@NonNull UUID studyUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        // check restrictions on node builds number
        userAdminService.getUserMaxAllowedBuilds(userId).ifPresent(maxBuilds -> {
            long nbBuiltNodes = networkModificationTreeService.countBuiltNodes(studyUuid, rootNetworkUuid);
            if (nbBuiltNodes >= maxBuilds) {
                throw new StudyException(MAX_NODE_BUILDS_EXCEEDED, "max allowed built nodes reached", Map.of("limit", maxBuilds));
            }
        });
    }

    @Transactional
    public void unbuildStudyNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        if (networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isNotBuilt()) {
            return;
        }

        // if loadflow was run on a security node, all children node might have been impacted with loadflow modifications
        // we need to invalidate them all
        boolean invalidateAll = networkModificationTreeService.isSecurityNode(nodeUuid) && rootNetworkNodeInfoService.isLoadflowDone(nodeUuid, rootNetworkUuid);
        if (invalidateAll) {
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid);
        } else {
            invalidateNode(studyUuid, nodeUuid, rootNetworkUuid);
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
        StudyEntity sourceStudyEntity = getStudy(sourceStudyUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.cloneStudyTree(studySubTree, referenceNodeUuid, studyEntity, sourceStudyEntity, false);
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

        getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity -> {
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

    private void invalidateNode(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());

        InvalidateNodeInfos invalidateNodeInfos = networkModificationTreeService.invalidateNode(nodeUuid, rootNetworkUuid);
        invalidateNodeInfos.setNetworkUuid(rootNetworkService.getNetworkUuid(rootNetworkUuid));

        deleteInvalidationInfos(invalidateNodeInfos);

        emitAllComputationStatusChanged(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode.ALL);

        if (startTime.get() != null) {
            LOGGER.trace("unbuild node '{}' of study '{}' : {} seconds", nodeUuid, studyUuid,
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void invalidateNode(UUID studyUuid, UUID nodeUuid) {
        getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            invalidateNode(studyUuid, nodeUuid, rootNetworkEntity.getId()));
    }

    private void invalidateNodeTree(UUID studyUuid, UUID nodeUuid) {
        invalidateNodeTree(studyUuid, nodeUuid, InvalidateNodeTreeParameters.ALL);
    }

    private void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, InvalidateNodeTreeParameters invalidateTreeParameters) {
        getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
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

        invalidateNodeTree(studyUuid, originNodeUuid);

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
        getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            invalidateNodeTreeWithLF(studyUuid, nodeUuid, rootNetworkEntity.getId(), computationsInvalidationMode)
        );
    }

    private void invalidateNodeTreeWithLF(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, ComputationsInvalidationMode computationsInvalidationMode) {
        boolean invalidateAll = networkModificationTreeService.isSecurityNode(nodeUuid) && rootNetworkNodeInfoService.isLoadflowDone(nodeUuid, rootNetworkUuid);
        InvalidateNodeTreeParameters invalidateNodeTreeParameters = InvalidateNodeTreeParameters.builder()
            .invalidationMode(invalidateAll ? InvalidationMode.ALL : InvalidationMode.ONLY_CHILDREN_BUILD_STATUS)
            .withBlockedNode(true)
            .computationsInvalidationMode(invalidateAll ? ComputationsInvalidationMode.ALL : computationsInvalidationMode)
            .build();
        invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, invalidateNodeTreeParameters);
    }

    public void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.ALL);
    }

    private void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, InvalidateNodeTreeParameters invalidateTreeParameters) {
        invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, invalidateTreeParameters, false);
    }

    public void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, InvalidateNodeTreeParameters invalidateTreeParameters, boolean blocking) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());

        InvalidateNodeInfos invalidateNodeInfos = networkModificationTreeService.invalidateNodeTree(nodeUuid, rootNetworkUuid, invalidateTreeParameters);
        invalidateNodeInfos.setNetworkUuid(rootNetworkService.getNetworkUuid(rootNetworkUuid));

        CompletableFuture<Void> cf = deleteInvalidationInfos(invalidateNodeInfos);
        if (blocking) {
            cf.join();
        }

        emitAllComputationStatusChanged(studyUuid, nodeUuid, rootNetworkUuid, invalidateTreeParameters.computationsInvalidationMode());

        if (startTime.get() != null) {
            LOGGER.trace("unbuild node '{}' of study '{}' : {} seconds", nodeUuid, studyUuid,
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    @Transactional
    public void unblockNodeTree(UUID studyUuid, UUID nodeUuid) {
        getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            networkModificationTreeService.unblockNodeTree(rootNetworkEntity.getId(), nodeUuid)
        );
    }

    @Transactional
    public void deleteNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        StudyEntity studyEntity = getStudy(studyUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.deleteModifications(groupId, modificationsUuids);
            // for each root network, remove modifications from excluded ones
            studyEntity.getRootNetworks().forEach(rootNetworkEntity -> rootNetworkNodeInfoService.updateModificationsToExclude(nodeUuid, rootNetworkEntity.getId(), new HashSet<>(modificationsUuids), true));
        } finally {
            notificationService.emitEndDeletionEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_STASHING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.stashModifications(groupId, modificationsUuids);
            invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModificationsActivation(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId, boolean activated) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.updateModificationsActivation(groupId, modificationsUuids, activated);
            invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModificationsActivationInRootNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Set<UUID> modificationsUuids, String userId, boolean activated) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        networkModificationService.verifyModifications(networkModificationTreeService.getModificationGroupUuid(nodeUuid), modificationsUuids);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, Optional.of(rootNetworkUuid), childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            rootNetworkNodeInfoService.updateModificationsToExclude(nodeUuid, rootNetworkUuid, modificationsUuids, activated);
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, Optional.of(rootNetworkUuid), childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void restoreNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_RESTORING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.restoreModifications(groupId, modificationsUuids);
            invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
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

        deleteNodesInfos(deleteNodeInfos);

        notificationService.emitElementUpdated(studyUuid, userId);
    }

    // /!\ Do not wait completion and do not throw exception
    private CompletableFuture<Void> deleteInvalidationInfos(InvalidateNodeInfos invalidateNodeInfos) {
        return CompletableFuture.allOf(
            studyServerExecutionService.runAsync(() -> networkStoreService.deleteVariants(invalidateNodeInfos.getNetworkUuid(), invalidateNodeInfos.getVariantIds())),
            studyServerExecutionService.runAsync(() -> networkModificationService.deleteIndexedModifications(invalidateNodeInfos.getGroupUuids(), invalidateNodeInfos.getNetworkUuid())),
            studyServerExecutionService.runAsync(() -> reportService.deleteReports(invalidateNodeInfos.getReportUuids())),
            studyServerExecutionService.runAsync(() -> loadflowService.deleteLoadFlowResults(invalidateNodeInfos.getLoadFlowResultUuids())),
            studyServerExecutionService.runAsync(() -> securityAnalysisService.deleteSecurityAnalysisResults(invalidateNodeInfos.getSecurityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> sensitivityAnalysisService.deleteSensitivityAnalysisResults(invalidateNodeInfos.getSensitivityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(invalidateNodeInfos.getShortCircuitAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(invalidateNodeInfos.getOneBusShortCircuitAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> voltageInitService.deleteVoltageInitResults(invalidateNodeInfos.getVoltageInitResultUuids())),
            studyServerExecutionService.runAsync(() -> dynamicSimulationService.deleteResults(invalidateNodeInfos.getDynamicSimulationResultUuids())),
            studyServerExecutionService.runAsync(() -> dynamicSecurityAnalysisService.deleteResults(invalidateNodeInfos.getDynamicSecurityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> stateEstimationService.deleteStateEstimationResults(invalidateNodeInfos.getStateEstimationResultUuids())),
            studyServerExecutionService.runAsync(() -> pccMinService.deletePccMinResults(invalidateNodeInfos.getPccMinResultUuids()))
        );
    }

    // /!\ Do not wait completion and do not throw exception
    private void deleteNodesInfos(DeleteNodeInfos deleteNodeInfos) {
        CompletableFuture.allOf(
            studyServerExecutionService.runAsync(() -> deleteNodeInfos.getVariantIds().forEach(networkStoreService::deleteVariants)),
            studyServerExecutionService.runAsync(() -> deleteNodeInfos.getModificationGroupUuids().forEach(networkModificationService::deleteModifications)),
            studyServerExecutionService.runAsync(() -> deleteNodeInfos.getRemovedNodeUuids().forEach(dynamicSimulationEventService::deleteEventsByNodeId)),
            studyServerExecutionService.runAsync(() -> reportService.deleteReports(deleteNodeInfos.getReportUuids())),
            studyServerExecutionService.runAsync(() -> loadflowService.deleteLoadFlowResults(deleteNodeInfos.getLoadFlowResultUuids())),
            studyServerExecutionService.runAsync(() -> securityAnalysisService.deleteSecurityAnalysisResults(deleteNodeInfos.getSecurityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> sensitivityAnalysisService.deleteSensitivityAnalysisResults(deleteNodeInfos.getSensitivityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(deleteNodeInfos.getShortCircuitAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(deleteNodeInfos.getOneBusShortCircuitAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> voltageInitService.deleteVoltageInitResults(deleteNodeInfos.getVoltageInitResultUuids())),
            studyServerExecutionService.runAsync(() -> dynamicSimulationService.deleteResults(deleteNodeInfos.getDynamicSimulationResultUuids())),
            studyServerExecutionService.runAsync(() -> dynamicSecurityAnalysisService.deleteResults(deleteNodeInfos.getDynamicSecurityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> stateEstimationService.deleteStateEstimationResults(deleteNodeInfos.getStateEstimationResultUuids())),
            studyServerExecutionService.runAsync(() -> pccMinService.deletePccMinResults(deleteNodeInfos.getPccMinResultUuids()))
        );
    }

    @Transactional
    public void stashNode(UUID studyUuid, UUID nodeId, boolean stashChildren, String userId) {
        removeNodesFromAliases(studyUuid, List.of(nodeId), stashChildren);

        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());

        boolean unbuildChildren = stashChildren || networkModificationTreeService.hasModifications(nodeId, false);
        List<UUID> rootNetworkUuids = getStudyRootNetworks(studyUuid).stream()
                .map(RootNetworkEntity::getId)
                .toList();

        if (unbuildChildren) {
            rootNetworkUuids.forEach(rootNetworkId ->
                invalidateNodeTree(studyUuid, nodeId, rootNetworkId));
        } else {
            rootNetworkUuids.forEach(rootNetworkId ->
                invalidateNode(studyUuid, nodeId, rootNetworkId)
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

    public void restoreNodes(UUID studyId, List<UUID> nodeIds, UUID anchorNodeId) {
        networkModificationTreeService.assertIsRootOrConstructionNode(anchorNodeId);
        networkModificationTreeService.restoreNode(studyId, nodeIds, anchorNodeId);
    }

    private void reindexRootNetwork(StudyEntity study, UUID rootNetworkUuid) {
        CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study);
        // reindex root network for study in elasticsearch
        studyInfosService.recreateStudyInfos(studyInfos);
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));

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
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Study not found"));
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
    public void moveNetworkModifications(@NonNull UUID studyUuid, UUID targetNodeUuid, @NonNull UUID originNodeUuid, List<UUID> modificationUuidList, UUID beforeUuid, boolean isTargetInDifferentNodeTree, String userId) {
        boolean isTargetDifferentNode = !targetNodeUuid.equals(originNodeUuid);

        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        List<UUID> originNodeChildrenUuids = new ArrayList<>();
        notificationService.emitStartModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        if (isTargetDifferentNode) {
            originNodeChildrenUuids = networkModificationTreeService.getChildrenUuids(originNodeUuid);
            notificationService.emitStartModificationEquipmentNotification(studyUuid, originNodeUuid, originNodeChildrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        }
        try {
            checkStudyContainsNode(studyUuid, targetNodeUuid);

            StudyEntity studyEntity = getStudy(studyUuid);
            List<RootNetworkEntity> studyRootNetworkEntities = studyEntity.getRootNetworks();
            UUID originGroupUuid = networkModificationTreeService.getModificationGroupUuid(originNodeUuid);
            UUID targetGroupUuid = networkModificationTreeService.getModificationGroupUuid(targetNodeUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), targetNodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();

            NetworkModificationsResult networkModificationsResult = networkModificationService.moveModifications(originGroupUuid, targetGroupUuid, beforeUuid, Pair.of(modificationUuidList, modificationApplicationContexts), isTargetInDifferentNodeTree);
            rootNetworkNodeInfoService.moveModificationsToExclude(originNodeUuid, targetNodeUuid, networkModificationsResult.modificationUuids());

            // Target node
            if (isTargetInDifferentNodeTree) {
                emitNetworkModificationImpactsForAllRootNetworks(networkModificationsResult.modificationResults(), studyEntity, targetNodeUuid);
            }
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids);
            if (isTargetDifferentNode) {
                notificationService.emitEndModificationEquipmentNotification(studyUuid, originNodeUuid, originNodeChildrenUuids);
            }
        }
        notificationService.emitElementUpdated(studyUuid, userId);
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
    public void duplicateOrInsertNetworkModifications(UUID targetStudyUuid, UUID targetNodeUuid, UUID originStudyUuid, UUID originNodeUuid, List<UUID> modificationsUuis, String userId, StudyConstants.ModificationsActionType action) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        notificationService.emitStartModificationEquipmentNotification(targetStudyUuid, targetNodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(targetStudyUuid, targetNodeUuid);

            List<RootNetworkEntity> studyRootNetworkEntities = getStudyRootNetworks(targetStudyUuid);
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(targetNodeUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), targetNodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();

            NetworkModificationsResult networkModificationResults = networkModificationService.duplicateOrInsertModifications(groupUuid, action, Pair.of(modificationsUuis, modificationApplicationContexts));

            if (targetStudyUuid.equals(originStudyUuid)) {
                Map<UUID, UUID> originToDuplicateModificationsUuids = new HashMap<>();
                for (int i = 0; i < modificationsUuis.size(); i++) {
                    originToDuplicateModificationsUuids.put(modificationsUuis.get(i), networkModificationResults.modificationUuids().get(i));
                }
                rootNetworkNodeInfoService.copyModificationsToExclude(originNodeUuid, targetNodeUuid, originToDuplicateModificationsUuids);
            }

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

        } finally {
            notificationService.emitEndModificationEquipmentNotification(targetStudyUuid, targetNodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
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
    public UUID runSensitivityAnalysis(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleSensitivityAnalysisRequest(study, nodeUuid, rootNetworkUuid, userId);
    }

    private UUID handleSensitivityAnalysisRequest(StudyEntity study, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS);
        if (prevResultUuid != null) {
            sensitivityAnalysisService.deleteSensitivityAnalysisResults(List.of(prevResultUuid));
        }
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID sensiReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(SENSITIVITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS, sensiReportUuid);

        UUID result = sensitivityAnalysisService.runSensitivityAnalysis(nodeUuid, rootNetworkUuid, networkUuid, variantId, sensiReportUuid, userId, study.getSensitivityAnalysisParametersUuid(), study.getLoadFlowParametersUuid());

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, SENSITIVITY_ANALYSIS);
        notificationService.emitStudyChanged(study.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        return result;
    }

    @Transactional
    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Optional<String> busId, boolean debug, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleShortCircuitRequest(studyEntity, nodeUuid, rootNetworkUuid, busId, debug, userId);
    }

    private UUID handleShortCircuitRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, Optional<String> busId, boolean debug, String userId) {
        ComputationType computationType = busId.isEmpty() ? SHORT_CIRCUIT : SHORT_CIRCUIT_ONE_BUS;
        UUID shortCircuitResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, computationType);
        if (shortCircuitResultUuid != null) {
            shortCircuitService.deleteShortCircuitAnalysisResults(List.of(shortCircuitResultUuid));
        }
        UUID scReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(computationType.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, computationType, scReportUuid);
        final UUID result = shortCircuitService.runShortCircuit(rootNetworkUuid, new VariantInfos(networkUuid, variantId), busId.orElse(null), studyEntity.getShortCircuitParametersUuid(), new ReportInfos(scReportUuid, nodeUuid), userId, debug);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, computationType);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid,
                busId.isEmpty() ? NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS : NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        return result;
    }

    @Transactional
    public UUID runVoltageInit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleVoltageInitRequest(studyEntity, nodeUuid, rootNetworkUuid, debug, userId);
    }

    private UUID handleVoltageInitRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, boolean debug, String userId) {
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION);
        if (prevResultUuid != null) {
            voltageInitService.deleteVoltageInitResults(List.of(prevResultUuid));
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(VOLTAGE_INITIALIZATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION, reportUuid);

        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        NetworkModificationNodeInfoEntity nodeEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(nodeUuid);

        UUID result = voltageInitService.runVoltageInit(new VariantInfos(networkUuid, variantId), studyEntity.getVoltageInitParametersUuid(),
                                                        new ReportInfos(reportUuid, nodeUuid), rootNetworkUuid, userId, debug,
                                                        new ContextInfos(rootNetwork.getName(), nodeEntity.getName()));

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, VOLTAGE_INITIALIZATION);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        return result;
    }

    @Transactional
    public boolean setVoltageInitParameters(UUID studyUuid, StudyVoltageInitParameters parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        var voltageInitParameters = studyEntity.getVoltageInitParameters();
        if (voltageInitParameters == null) {
            var newVoltageInitParameters = new StudyVoltageInitParametersEntity(parameters.isApplyModifications());
            studyEntity.setVoltageInitParameters(newVoltageInitParameters);
        } else {
            voltageInitParameters.setApplyModifications(parameters.isApplyModifications());
        }
        boolean userProfileIssue = createOrUpdateVoltageInitParameters(studyEntity, parameters.getComputationParameters(), userId);
        invalidateVoltageInitStatusOnAllNodes(studyEntity.getId());
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, VOLTAGE_INITIALIZATION);
        return userProfileIssue;
    }

    public StudyVoltageInitParameters getVoltageInitParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return new StudyVoltageInitParameters(
                Optional.ofNullable(studyEntity.getVoltageInitParametersUuid()).map(voltageInitService::getVoltageInitParameters).orElse(null),
                Optional.ofNullable(studyEntity.getVoltageInitParameters()).map(StudyVoltageInitParametersEntity::shouldApplyModifications).orElse(true)
        );
    }

    @Transactional
    public String getSpreadsheetConfigCollection(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyConfigService.getSpreadsheetConfigCollection(studyConfigService.getSpreadsheetConfigCollectionUuidOrElseCreateDefaults(studyEntity));
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

    // --- Dynamic Simulation service methods BEGIN --- //

    public List<MappingInfos> getDynamicSimulationMappings(UUID studyUuid) {
        // get mapping from study uuid
        return dynamicSimulationService.getMappings(studyUuid);

    }

    @Transactional(readOnly = true)
    public List<ModelInfos> getDynamicSimulationModels(UUID studyUuid) {
        // load configured parameters persisted in the study server DB
        DynamicSimulationParametersInfos configuredParameters = getDynamicSimulationParameters(getStudy(studyUuid));
        String mapping = configuredParameters.getMapping();

        // get model from mapping
        return dynamicSimulationService.getModels(mapping);
    }

    @Transactional
    public void setDynamicSimulationParameters(UUID studyUuid, DynamicSimulationParametersInfos dsParameter, String userId) {
        updateDynamicSimulationParameters(studyUuid, DynamicSimulationService.toEntity(dsParameter != null ? dsParameter : DynamicSimulationService.getDefaultDynamicSimulationParameters(), objectMapper));

        // Dynamic security analysis depend on dynamic simulation => must invalidate
        invalidateDynamicSecurityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);

        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, DYNAMIC_SIMULATION);
    }

    @Transactional(readOnly = true)
    public DynamicSimulationParametersInfos getDynamicSimulationParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return getDynamicSimulationParameters(studyEntity);
    }

    private DynamicSimulationParametersInfos getDynamicSimulationParameters(StudyEntity studyEntity) {
        return studyEntity.getDynamicSimulationParameters() != null ? DynamicSimulationService.fromEntity(studyEntity.getDynamicSimulationParameters(), objectMapper) : DynamicSimulationService.getDefaultDynamicSimulationParameters();
    }

    @Transactional(readOnly = true)
    public List<EventInfos> getDynamicSimulationEvents(UUID nodeUuid) {
        return dynamicSimulationEventService.getEventsByNodeId(nodeUuid);
    }

    @Transactional(readOnly = true)
    public EventInfos getDynamicSimulationEvent(UUID nodeUuid, String equipmentId) {
        return dynamicSimulationEventService.getEventByNodeIdAndEquipmentId(nodeUuid, equipmentId);
    }

    private void postProcessEventCrud(UUID studyUuid, UUID nodeUuid) {
        // for delete old result and refresh dynamic simulation run button in UI
        invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    @Transactional
    public void createDynamicSimulationEvent(UUID studyUuid, UUID nodeUuid, String userId, EventInfos event) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartEventCrudNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.EVENTS_CRUD_CREATING_IN_PROGRESS);
        try {
            dynamicSimulationEventService.saveEvent(nodeUuid, event);
        } finally {
            notificationService.emitEndEventCrudNotification(studyUuid, nodeUuid, childrenUuids);
        }
        postProcessEventCrud(studyUuid, nodeUuid);
    }

    @Transactional
    public void updateDynamicSimulationEvent(UUID studyUuid, UUID nodeUuid, String userId, EventInfos event) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartEventCrudNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.EVENTS_CRUD_UPDATING_IN_PROGRESS);
        try {
            dynamicSimulationEventService.saveEvent(nodeUuid, event);
        } finally {
            notificationService.emitEndEventCrudNotification(studyUuid, nodeUuid, childrenUuids);
        }
        postProcessEventCrud(studyUuid, nodeUuid);
    }

    @Transactional
    public void deleteDynamicSimulationEvents(UUID studyUuid, UUID nodeUuid, String userId, List<UUID> eventUuids) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartEventCrudNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.EVENTS_CRUD_DELETING_IN_PROGRESS);
        try {
            dynamicSimulationEventService.deleteEvents(eventUuids);
        } finally {
            notificationService.emitEndEventCrudNotification(studyUuid, nodeUuid, childrenUuids);
        }
        postProcessEventCrud(studyUuid, nodeUuid);
    }

    @Transactional
    public UUID runDynamicSimulation(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, DynamicSimulationParametersInfos parameters,
                                     String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleDynamicSimulationRequest(studyEntity, nodeUuid, rootNetworkUuid, parameters, debug, userId);
    }

    private UUID handleDynamicSimulationRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, DynamicSimulationParametersInfos parameters, boolean debug, String userId) {
        // pre-condition check
        if (!rootNetworkNodeInfoService.isLoadflowConverged(nodeUuid, rootNetworkUuid)) {
            throw new StudyException(NOT_ALLOWED, "Load flow must run successfully before running dynamic simulation");
        }

        // clean previous result if exist
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SIMULATION);
        if (prevResultUuid != null) {
            dynamicSimulationService.deleteResults(List.of(prevResultUuid));
        }

        // load configured parameters persisted in the study server DB
        DynamicSimulationParametersInfos configuredParameters = getDynamicSimulationParameters(studyEntity);

        // load configured events persisted in the study server DB
        List<EventInfos> events = dynamicSimulationEventService.getEventsByNodeId(nodeUuid);

        // override configured parameters by provided parameters (only provided fields)
        DynamicSimulationParametersInfos mergeParameters = new DynamicSimulationParametersInfos();
        // attach events to the merged parameters
        mergeParameters.setEvents(events);

        if (configuredParameters != null) {
            PropertyUtils.copyNonNullProperties(configuredParameters, mergeParameters);
        }
        if (parameters != null) {
            PropertyUtils.copyNonNullProperties(parameters, mergeParameters);
        }
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(DYNAMIC_SIMULATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SIMULATION, reportUuid);

        // launch dynamic simulation
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID dynamicSimulationResultUuid = dynamicSimulationService.runDynamicSimulation(studyEntity.getDynamicSimulationProvider(),
                nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid, mergeParameters, userId, debug);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicSimulationResultUuid, DYNAMIC_SIMULATION);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);

        return dynamicSimulationResultUuid;
    }

    // --- Dynamic Simulation service methods END --- //

    // --- Dynamic Security Analysis service methods BEGIN --- //

    public UUID getDynamicSecurityAnalysisParametersUuid(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyEntity.getDynamicSecurityAnalysisParametersUuid();
    }

    @Transactional
    public String getDynamicSecurityAnalysisParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return dynamicSecurityAnalysisService.getParameters(
                dynamicSecurityAnalysisService.getDynamicSecurityAnalysisParametersUuidOrElseCreateDefault(studyEntity));
    }

    @Transactional
    public boolean setDynamicSecurityAnalysisParameters(UUID studyUuid, String dsaParameter, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = createOrUpdateDynamicSecurityAnalysisParameters(studyEntity, dsaParameter, userId);
        invalidateDynamicSecurityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, DYNAMIC_SECURITY_ANALYSIS);
        return userProfileIssue;
    }

    public boolean createOrUpdateDynamicSecurityAnalysisParameters(StudyEntity studyEntity, String parameters, String userId) {
        boolean userProfileIssue = false;
        UUID existingDynamicSecurityAnalysisParametersUuid = studyEntity.getDynamicSecurityAnalysisParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getDynamicSecurityAnalysisParameterId() != null) {
            // reset case, with existing profile, having default dynamic security analysis params
            try {
                UUID dynamicSecurityAnalysisParametersFromProfileUuid = dynamicSecurityAnalysisService.duplicateParameters(userProfileInfos.getDynamicSecurityAnalysisParameterId());
                studyEntity.setDynamicSecurityAnalysisParametersUuid(dynamicSecurityAnalysisParametersFromProfileUuid);
                removeDynamicSecurityAnalysisParameters(existingDynamicSecurityAnalysisParametersUuid);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate dynamic security analysis parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getDynamicSecurityAnalysisParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }

        if (existingDynamicSecurityAnalysisParametersUuid == null) {
            UUID newDynamicSecurityAnalysisParametersUuid = dynamicSecurityAnalysisService.createParameters(parameters);
            studyEntity.setDynamicSecurityAnalysisParametersUuid(newDynamicSecurityAnalysisParametersUuid);
        } else {
            dynamicSecurityAnalysisService.updateParameters(existingDynamicSecurityAnalysisParametersUuid, parameters);
        }

        return userProfileIssue;
    }

    private void removeDynamicSecurityAnalysisParameters(@Nullable UUID dynamicSecurityAnalysisParametersUuid) {
        if (dynamicSecurityAnalysisParametersUuid != null) {
            try {
                dynamicSecurityAnalysisService.deleteParameters(dynamicSecurityAnalysisParametersUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove dynamic security analysis parameters with uuid:" + dynamicSecurityAnalysisParametersUuid, e);
            }
        }
    }

    @Transactional
    public UUID runDynamicSecurityAnalysis(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId, boolean debug) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleDynamicSecurityAnalysisRequest(studyEntity, nodeUuid, rootNetworkUuid, debug, userId);
    }

    private UUID handleDynamicSecurityAnalysisRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, boolean debug, String userId) {

        // pre-condition check
        if (!rootNetworkNodeInfoService.isLoadflowConverged(nodeUuid, rootNetworkUuid)) {
            throw new StudyException(NOT_ALLOWED, "Load flow must run successfully before running dynamic security analysis");
        }

        DynamicSimulationStatus dsStatus = rootNetworkNodeInfoService.getDynamicSimulationStatus(nodeUuid, rootNetworkUuid);
        if (DynamicSimulationStatus.CONVERGED != dsStatus) {
            throw new StudyException(NOT_ALLOWED, "Dynamic simulation must run successfully before running dynamic security analysis");
        }

        // clean previous result if exist
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SECURITY_ANALYSIS);
        if (prevResultUuid != null) {
            dynamicSecurityAnalysisService.deleteResults(List.of(prevResultUuid));
        }

        // get dynamic simulation result uuid
        UUID dynamicSimulationResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SIMULATION);

        // get dynamic security analysis parameters uuid
        UUID dynamicSecurityAnalysisParametersUuid = getDynamicSecurityAnalysisParametersUuid(studyEntity.getId());

        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(DYNAMIC_SECURITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SECURITY_ANALYSIS, reportUuid);

        // launch dynamic security analysis
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID dynamicSecurityAnalysisResultUuid = dynamicSecurityAnalysisService.runDynamicSecurityAnalysis(studyEntity.getDynamicSimulationProvider(),
            nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid, dynamicSimulationResultUuid, dynamicSecurityAnalysisParametersUuid, userId, debug);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicSecurityAnalysisResultUuid, DYNAMIC_SECURITY_ANALYSIS);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);

        return dynamicSecurityAnalysisResultUuid;
    }

    // --- Dynamic Security Analysis service methods END --- //

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
        UUID voltageInitModificationsGroupUuid = voltageInitService.getModificationsGroupUuid(nodeUuid, resultUuid);
        return networkModificationService.getModifications(voltageInitModificationsGroupUuid, false, false);
    }

    @Transactional
    public void insertVoltageInitModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, String.format("Missing results for rootNetwork %s on node %s", rootNetworkUuid, nodeUuid));
        }
        UUID voltageInitModificationsGroupUuid = voltageInitService.getModificationsGroupUuid(nodeUuid, resultUuid);

        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);

            invalidateNodeTreeWithLF(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode.PRESERVE_VOLTAGE_INIT_RESULTS);

            // voltageInit modification should apply only on the root network where the computation has been made:
            // - application context will point to the computation root network only
            // - after creation, we deactivate the new modification for all other root networks
            List<RootNetworkEntity> studyRootNetworkEntities = getStudyRootNetworks(studyUuid);
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
            NetworkModificationsResult networkModificationResults = networkModificationService.duplicateModificationsFromGroup(networkModificationTreeService.getModificationGroupUuid(nodeUuid), voltageInitModificationsGroupUuid, Pair.of(List.of(), modificationApplicationContexts));

            // We expect a single voltageInit modification in the result list
            if (networkModificationResults != null && networkModificationResults.modificationUuids().size() == 1) {
                for (UUID otherRootNetwork : rootNetworkToDeactivateUuids) {
                    rootNetworkNodeInfoService.updateModificationsToExclude(nodeUuid, otherRootNetwork, Set.of(networkModificationResults.modificationUuids().getFirst()), false);
                }
                int index = 0;
                // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
                for (Optional<NetworkModificationResult> modificationResultOpt : networkModificationResults.modificationResults()) {
                    if (modificationResultOpt.isPresent() && studyRootNetworkEntities.get(index) != null) {
                        emitNetworkModificationImpacts(studyUuid, nodeUuid, studyRootNetworkEntities.get(index).getId(), modificationResultOpt.get());
                    }
                    index++;
                }
            }

            voltageInitService.resetModificationsGroupUuid(resultUuid);

            // send notification voltage init result has changed
            notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        } finally {
            networkModificationTreeService.unblockNodeTree(rootNetworkUuid, nodeUuid);
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public String getSensitivityAnalysisParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return sensitivityAnalysisService.getSensitivityAnalysisParameters(
                sensitivityAnalysisService.getSensitivityAnalysisParametersUuidOrElseCreateDefault(studyEntity));
    }

    @Transactional
    public boolean setSensitivityAnalysisParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        boolean userProfileIssue = createOrUpdateSensitivityAnalysisParameters(studyEntity, parameters, userId);
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SENSITIVITY_ANALYSIS);
        return userProfileIssue;
    }

    public boolean createOrUpdateSensitivityAnalysisParameters(StudyEntity studyEntity, String parameters, String userId) {
        boolean userProfileIssue = false;
        UUID existingSensitivityAnalysisParametersUuid = studyEntity.getSensitivityAnalysisParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId) : null;
        if (parameters == null && userProfileInfos.getSensitivityAnalysisParameterId() != null) {
            // reset case, with existing profile, having default sensitivity analysis params
            try {
                UUID sensitivityAnalysisParametersFromProfileUuid = sensitivityAnalysisService.duplicateSensitivityAnalysisParameters(userProfileInfos.getSensitivityAnalysisParameterId());
                studyEntity.setSensitivityAnalysisParametersUuid(sensitivityAnalysisParametersFromProfileUuid);
                removeSensitivityAnalysisParameters(existingSensitivityAnalysisParametersUuid);
                return userProfileIssue;
            } catch (Exception e) {
                userProfileIssue = true;
                LOGGER.error(String.format("Could not duplicate sensitivity analysis parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getSensitivityAnalysisParameterId(), userId, userProfileInfos.getName()), e);
                // in case of duplication error (ex: wrong/dangling uuid in the profile), move on with default params below
            }
        }

        if (existingSensitivityAnalysisParametersUuid == null) {
            existingSensitivityAnalysisParametersUuid = sensitivityAnalysisService.createSensitivityAnalysisParameters(parameters);
            studyEntity.setSensitivityAnalysisParametersUuid(existingSensitivityAnalysisParametersUuid);
        } else {
            sensitivityAnalysisService.updateSensitivityAnalysisParameters(existingSensitivityAnalysisParametersUuid, parameters);
        }

        return userProfileIssue;
    }

    private void removeSensitivityAnalysisParameters(@Nullable UUID sensitivityAnalysisParametersUuid) {
        if (sensitivityAnalysisParametersUuid != null) {
            try {
                sensitivityAnalysisService.deleteSensitivityAnalysisParameters(sensitivityAnalysisParametersUuid);
            } catch (Exception e) {
                LOGGER.error("Could not remove sensitivity analysis parameters with uuid:" + sensitivityAnalysisParametersUuid, e);
            }
        }
    }

    public void invalidateShortCircuitStatusOnAllNodes(UUID studyUuid) {
        shortCircuitService.invalidateShortCircuitStatus(Stream.concat(
            rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SHORT_CIRCUIT).stream(),
            rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SHORT_CIRCUIT_ONE_BUS).stream()
        ).toList());
    }

    private void emitAllComputationStatusChanged(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode computationsInvalidationMode) {
        if (!InvalidateNodeTreeParameters.ComputationsInvalidationMode.isPreserveLoadFlowResults(computationsInvalidationMode)) {
            notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        }
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
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

    @Transactional(readOnly = true)
    public String getNetworkElementsInfosByGlobalFilter(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, EquipmentType equipmentType, String infoType, GlobalFilter filter) {
        // Get the list of equipment ids that match the filter
        List<String> equipmentIds = self.evaluateGlobalFilter(nodeUuid, rootNetworkUuid, List.of(equipmentType), filter);

        // Get the requested info for the filtered equipment ids
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, true);
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);

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
    public UUID runStateEstimation(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handleStateEstimationRequest(studyEntity, nodeUuid, rootNetworkUuid, userId);
    }

    @Transactional
    public UUID runPccMin(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        networkModificationTreeService.blockNode(rootNetworkUuid, nodeUuid);

        return handlePccMinRequest(studyEntity, nodeUuid, rootNetworkUuid, userId);
    }

    private UUID handleStateEstimationRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(STATE_ESTIMATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, STATE_ESTIMATION, reportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, STATE_ESTIMATION);
        if (prevResultUuid != null) {
            stateEstimationService.deleteStateEstimationResults(List.of(prevResultUuid));
        }

        UUID result = stateEstimationService.runStateEstimation(networkUuid, variantId, studyEntity.getStateEstimationParametersUuid(), new ReportInfos(reportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, STATE_ESTIMATION);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        return result;
    }

    private UUID handlePccMinRequest(StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(PCC_MIN.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, PCC_MIN, reportUuid);
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, PCC_MIN);
        if (prevResultUuid != null) {
            pccMinService.deletePccMinResults(List.of(prevResultUuid));
        }
        var runPccMinParametersInfos = new RunPccMinParametersInfos(studyEntity.getShortCircuitParametersUuid(), studyEntity.getPccMinParametersUuid(), null);

        UUID result = pccMinService.runPccMin(networkUuid, variantId, runPccMinParametersInfos, new ReportInfos(reportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, PCC_MIN);
        notificationService.emitStudyChanged(studyEntity.getId(), nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        return result;
    }

    @Transactional
    public String getStateEstimationParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return stateEstimationService.getStateEstimationParameters(stateEstimationService.getStateEstimationParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public void setStateEstimationParametersValues(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        createOrUpdateStateEstimationParameters(studyEntity, parameters);
        invalidateStateEstimationStatusOnAllNodes(studyEntity.getId());
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, STATE_ESTIMATION);
    }

    public void createOrUpdateStateEstimationParameters(StudyEntity studyEntity, String parameters) {
        UUID existingStateEstimationParametersUuid = studyEntity.getStateEstimationParametersUuid();
        if (existingStateEstimationParametersUuid == null) {
            existingStateEstimationParametersUuid = stateEstimationService.createStateEstimationParameters(parameters);
            studyEntity.setStateEstimationParametersUuid(existingStateEstimationParametersUuid);
        } else {
            stateEstimationService.updateStateEstimationParameters(existingStateEstimationParametersUuid, parameters);
        }
    }

    @Transactional
    public String getPccMinParameters(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return pccMinService.getPccMinParameters(pccMinService.getPccMinParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public void setPccMinParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = getStudy(studyUuid);
        createOrUpdatePccMinParameters(studyEntity, parameters);
        invalidatePccMinStatusOnAllNodes(studyEntity.getId());
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, PCC_MIN);
    }

    public void createOrUpdatePccMinParameters(StudyEntity studyEntity, String parameters) {
        UUID existingPccMinParametersUuid = studyEntity.getPccMinParametersUuid();
        if (existingPccMinParametersUuid == null) {
            existingPccMinParametersUuid = pccMinService.createPccMinParameters(parameters);
            studyEntity.setPccMinParametersUuid(existingPccMinParametersUuid);
        } else {
            pccMinService.updatePccMinParameters(existingPccMinParametersUuid, parameters);
        }
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

    private List<RootNetworkEntity> getStudyRootNetworks(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return studyEntity.getRootNetworks();
    }

    private List<RootNetworkInfos> getStudyRootNetworksInfos(UUID studyUuid) {
        List<RootNetworkEntity> rootNetworkEntities = getStudyRootNetworks(studyUuid);
        // using the Hibernate First-Level Cache or Persistence Context
        // cf.https://vladmihalcea.com/spring-data-jpa-multiplebagfetchexception/
        rootNetworkService.getRootNetworkInfosWithLinksInfos(studyUuid);
        return rootNetworkEntities.stream().map(RootNetworkEntity::toDto).toList();
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
        return getStudyRootNetworks(studyUuid).stream().map(RootNetworkEntity::toBasicDto).toList();
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
    public void updateNodeAliases(UUID studyUuid, List<NodeAlias> nodeAliases) {
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

    public void renameSpreadsheetConfig(UUID studyUuid, UUID configUuid, String newName) {
        studyConfigService.renameSpreadsheetConfig(configUuid, newName);
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

    @Transactional(readOnly = true)
    public String getVoltageInitResult(UUID nodeUuid, UUID rootNetworkUuid, String globalFilters) {
        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION);
        return voltageInitService.getVoltageInitResult(resultUuid, networkuuid, variantId, globalFilters);
    }

    public DiagramGridLayout getDiagramGridLayout(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        UUID diagramGridLayoutUuid = studyEntity.getDiagramGridLayoutUuid();
        return diagramGridLayoutService.getDiagramGridLayout(diagramGridLayoutUuid);
    }

    @Transactional
    public UUID saveDiagramGridLayout(UUID studyUuid, DiagramGridLayout diagramGridLayout) {
        StudyEntity studyEntity = getStudy(studyUuid);

        UUID existingDiagramGridLayoutUuid = studyEntity.getDiagramGridLayoutUuid();

        if (existingDiagramGridLayoutUuid == null) {
            UUID newDiagramGridLayoutUuid = diagramGridLayoutService.createDiagramGridLayout(diagramGridLayout);
            studyEntity.setDiagramGridLayoutUuid(newDiagramGridLayoutUuid);
            return newDiagramGridLayoutUuid;
        } else {
            return diagramGridLayoutService.updateDiagramGridLayout(existingDiagramGridLayoutUuid, diagramGridLayout);
        }
    }

    private void removeDiagramGridLayout(@Nullable UUID diagramGridLayoutUuid) {
        diagramGridLayoutService.removeDiagramGridLayout(diagramGridLayoutUuid);
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

    @Transactional
    public void createNadPositionsConfigFromCsv(MultipartFile file) {
        singleLineDiagramService.createNadPositionsConfigFromCsv(file);
    }

    private List<CurrentLimitViolationInfos> getCurrentLimitViolations(UUID nodeUuid, UUID rootNetworkUuid) {
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        if (resultUuid == null) {
            return List.of();
        }
        return loadflowService.getCurrentLimitViolations(resultUuid)
            .stream()
            .map(l -> new CurrentLimitViolationInfos(l.getSubjectId(), null))
            .toList();
    }
}
