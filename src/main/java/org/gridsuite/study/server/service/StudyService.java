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
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.modification.dto.ModificationInfos;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.dto.nonevacuatedenergy.*;
import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationEventService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.dto.ComputationType.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

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

    private final String defaultNonEvacuatedEnergyProvider;

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
    private final NonEvacuatedEnergyService nonEvacuatedEnergyService;
    private final DynamicSimulationEventService dynamicSimulationEventService;
    private final StudyConfigService studyConfigService;
    private final FilterService filterService;
    private final ActionsService actionsService;
    private final CaseService caseService;
    private final StateEstimationService stateEstimationService;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;

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
        NON_EVACUATED_ENERGY_ANALYSIS("NonEvacuatedEnergyAnalysis"),
        VOLTAGE_INITIALIZATION("VoltageInit"),
        STATE_ESTIMATION("StateEstimation");

        public final String reportKey;

        ReportType(String reportKey) {
            this.reportKey = reportKey;
        }
    }

    private final StudyService self;

    @Autowired
    public StudyService(
        @Value("${non-evacuated-energy.default-provider}") String defaultNonEvacuatedEnergyProvider,
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
        NonEvacuatedEnergyService nonEvacuatedEnergyService,
        DynamicSimulationService dynamicSimulationService,
        DynamicSecurityAnalysisService dynamicSecurityAnalysisService,
        VoltageInitService voltageInitService,
        DynamicSimulationEventService dynamicSimulationEventService,
        StudyConfigService studyConfigService,
        FilterService filterService,
        StateEstimationService stateEstimationService,
        @Lazy StudyService studyService,
        RootNetworkService rootNetworkService,
        RootNetworkNodeInfoService rootNetworkNodeInfoService) {
        this.defaultNonEvacuatedEnergyProvider = defaultNonEvacuatedEnergyProvider;
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
        this.nonEvacuatedEnergyService = nonEvacuatedEnergyService;
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
        this.filterService = filterService;
        this.stateEstimationService = stateEstimationService;
        this.self = studyService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    private CreatedStudyBasicInfos toStudyInfos(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        UUID importReportUuid = UUID.randomUUID();
        UUID caseUuidToUse = caseUuid;
        try {
            if (duplicateCase) {
                caseUuidToUse = caseService.duplicateCase(caseUuid, true);
            }
            persistNetwork(caseUuidToUse, basicStudyInfos.getId(), null, NetworkModificationTreeService.FIRST_VARIANT_ID, userId, importReportUuid, caseFormat, importParameters, CaseImportAction.STUDY_CREATION);
        } catch (Exception e) {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            throw e;
        }

        return basicStudyInfos;
    }

    @Transactional
    public void deleteRootNetworks(UUID studyUuid, List<UUID> rootNetworksUuids) {
        assertIsStudyExist(studyUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        List<RootNetworkEntity> allRootNetworkEntities = getStudyRootNetworks(studyUuid);
        if (rootNetworksUuids.size() >= allRootNetworkEntities.size()) {
            throw new StudyException(ROOT_NETWORK_DELETE_FORBIDDEN);
        }
        if (!allRootNetworkEntities.stream().map(RootNetworkEntity::getId).collect(Collectors.toSet()).containsAll(rootNetworksUuids)) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
        notificationService.emitRootNetworksDeletionStarted(studyUuid, rootNetworksUuids);

        rootNetworkService.deleteRootNetworks(studyEntity, rootNetworksUuids.stream());

        notificationService.emitRootNetworksUpdated(studyUuid);
    }

    @Transactional
    public RootNetworkRequestInfos createRootNetworkRequest(UUID studyUuid, String rootNetworkName, String rootNetworkTag, UUID caseUuid, String caseFormat, Map<String, Object> importParameters, String userId) {
        rootNetworkService.assertCanCreateRootNetwork(studyUuid, rootNetworkName, rootNetworkTag);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        UUID importReportUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        RootNetworkRequestEntity rootNetworkCreationRequestEntity = rootNetworkService.insertCreationRequest(rootNetworkUuid, studyEntity.getId(), rootNetworkName, rootNetworkTag, userId);
        try {
            UUID clonedCaseUuid = caseService.duplicateCase(caseUuid, true);
            persistNetwork(clonedCaseUuid, studyUuid, rootNetworkUuid, null, userId, importReportUuid, caseFormat, importParameters, CaseImportAction.ROOT_NETWORK_CREATION);
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkCreationRequestEntity);
            throw new StudyException(ROOT_NETWORK_CREATION_FAILED);
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        Optional<RootNetworkRequestEntity> rootNetworkCreationRequestEntityOpt = rootNetworkService.getRootNetworkRequest(rootNetworkInfos.getId());
        if (rootNetworkCreationRequestEntityOpt.isPresent()) {
            rootNetworkInfos.setName(rootNetworkCreationRequestEntityOpt.get().getName());
            rootNetworkInfos.setTag(rootNetworkCreationRequestEntityOpt.get().getTag());
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
    public void updateRootNetworkRequest(UUID studyUuid, RootNetworkInfos rootNetworkInfos, Map<String, Object> importParameters, String userId) {
        rootNetworkService.assertCanModifyRootNetwork(studyUuid, rootNetworkInfos.getId(), rootNetworkInfos.getName(), rootNetworkInfos.getTag());
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        if (rootNetworkInfos.getCaseInfos().getCaseUuid() != null) {
            RootNetworkRequestEntity requestEntity = rootNetworkService.insertModificationRequest(rootNetworkInfos.getId(), studyEntity.getId(), rootNetworkInfos.getName(), rootNetworkInfos.getTag(), userId);
            updateRootNetworkCaseInfos(studyEntity.getId(), rootNetworkInfos, userId, importParameters, requestEntity);
        } else {
            updateRootNetworkBasicInfos(studyEntity.getId(), rootNetworkInfos, false);
        }
    }

    private void updateRootNetworkCaseInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId, Map<String, Object> importParameters, RootNetworkRequestEntity rootNetworkModificationRequestEntity) {
        UUID importReportUuid = UUID.randomUUID();
        UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getCaseUuid(), true);
        try {
            persistNetwork(clonedCaseUuid, studyUuid, rootNetworkInfos.getId(), null, userId, importReportUuid, rootNetworkInfos.getCaseInfos().getCaseFormat(), importParameters, CaseImportAction.ROOT_NETWORK_MODIFICATION);
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkModificationRequestEntity);
            throw new StudyException(ROOT_NETWORK_MODIFICATION_FAILED);
        }
    }

    @Transactional
    public void modifyRootNetwork(UUID studyUuid, RootNetworkInfos rootNetworkInfos, boolean updateCase) {
        updateRootNetworkBasicInfos(studyUuid, rootNetworkInfos, true);
    }

    private void postRootNetworkUpdate(UUID studyUuid, UUID rootNetworkUuid, boolean updateCase) {
        if (updateCase) {
            Optional<RootNetworkRequestEntity> rootNetworkModificationRequestEntityOpt = rootNetworkService.getRootNetworkRequest(rootNetworkUuid);
            rootNetworkModificationRequestEntityOpt.ifPresent(rootNetworkService::deleteRootNetworkRequest);
            UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);

            invalidateNodeTree(studyUuid, rootNodeUuid, rootNetworkUuid);
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
        recreateNetwork(caseUuid, userId, studyUuid, rootNetworkUuid, caseFormat, importParameters, false);
    }

    /**
     * Recreates study network from existing case and import parameters
     * @param userId
     * @param studyUuid
     */
    public void recreateNetwork(String userId, UUID studyUuid, UUID rootNetworkUuid, String caseFormat) {
        UUID caseUuid = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)).getCaseUuid();
        recreateNetwork(caseUuid, userId, studyUuid, rootNetworkUuid, caseFormat, null, true);
    }

    private void recreateNetwork(UUID caseUuid, String userId, UUID studyUuid, UUID rootNetworkUuid, String caseFormat, Map<String, Object> importParameters, boolean shouldLoadPreviousImportParameters) {
        caseService.assertCaseExists(caseUuid);
        UUID importReportUuid = UUID.randomUUID();
        Map<String, Object> importParametersToUse = shouldLoadPreviousImportParameters
            ? new HashMap<>(rootNetworkService.getImportParameters(rootNetworkUuid))
            : importParameters;

        persistNetwork(caseUuid, studyUuid, rootNetworkUuid, null, userId, importReportUuid, caseFormat, importParametersToUse, CaseImportAction.NETWORK_RECREATION);
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
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            LOGGER.trace("Create study '{}' from source {} : {} seconds", basicStudyInfos.getId(), sourceStudyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    @Transactional(readOnly = true)
    public CreatedStudyBasicInfos getStudyInfos(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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

    private Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
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
                if (s.getLoadFlowParametersUuid() != null) {
                    loadflowService.deleteLoadFlowParameters(s.getLoadFlowParametersUuid());
                }
                if (s.getSecurityAnalysisParametersUuid() != null) {
                    securityAnalysisService.deleteSecurityAnalysisParameters(s.getSecurityAnalysisParametersUuid());
                }
                if (s.getVoltageInitParametersUuid() != null) {
                    voltageInitService.deleteVoltageInitParameters(s.getVoltageInitParametersUuid());
                }
                if (s.getSensitivityAnalysisParametersUuid() != null) {
                    sensitivityAnalysisService.deleteSensitivityAnalysisParameters(s.getSensitivityAnalysisParametersUuid());
                }
                if (s.getNetworkVisualizationParametersUuid() != null) {
                    studyConfigService.deleteNetworkVisualizationParameters(s.getNetworkVisualizationParametersUuid());
                }
                if (s.getStateEstimationParametersUuid() != null) {
                    stateEstimationService.deleteStateEstimationParameters(s.getStateEstimationParametersUuid());
                }
                if (s.getSpreadsheetConfigCollectionUuid() != null) {
                    studyConfigService.deleteSpreadsheetConfigCollection(s.getSpreadsheetConfigCollectionUuid());
                }
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

    @Transactional
    public void deleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        try {
            Optional<DeleteStudyInfos> deleteStudyInfosOpt = doDeleteStudyIfNotCreationInProgress(studyUuid, userId);
            if (deleteStudyInfosOpt.isPresent()) {
                DeleteStudyInfos deleteStudyInfos = deleteStudyInfosOpt.get();
                startTime.set(System.nanoTime());

                //TODO: now we have a n-n relation between node and rootNetworks, it's even more important to delete results in a single request
                CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                    Stream.concat(
                        // delete all distant resources linked to rootNetworks
                        rootNetworkService.getDeleteRootNetworkInfosFutures(deleteStudyInfos.getRootNetworkInfosList()),
                        // delete all distant resources linked to nodes
                        Stream.of(studyServerExecutionService.runAsync(() -> deleteStudyInfos.getModificationGroupUuids().stream().filter(Objects::nonNull).forEach(networkModificationService::deleteModifications))) // TODO delete all with one request only
                    ).toArray(CompletableFuture[]::new)
                );

                executeInParallel.get();
                if (startTime.get() != null) {
                    LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StudyException(DELETE_STUDY_FAILED, e.getMessage());
        } catch (Exception e) {
            throw new StudyException(DELETE_STUDY_FAILED, e.getMessage());
        }
    }

    @Transactional
    public CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, CaseInfos caseInfos, UUID loadFlowParametersUuid,
                                              UUID shortCircuitParametersUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity,
                                              UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                              UUID networkVisualizationParametersUuid, UUID dynamicSecurityAnalysisParametersUuid, UUID stateEstimationParametersUuid,
                                              UUID spreadsheetConfigCollectionUuid, Map<String, String> importParameters, UUID importReportUuid) {
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
                stateEstimationParametersUuid, spreadsheetConfigCollectionUuid, importParameters, importReportUuid);

        // Need to deal with the study creation (with a default root network ?)
        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    @Transactional
    public CreatedStudyBasicInfos updateNetwork(UUID studyUuid, UUID rootNetworkUuid, NetworkInfos networkInfos, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        RootNetworkEntity rootNetworkEntity = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        rootNetworkService.updateNetwork(rootNetworkEntity, networkInfos);

        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudyNetworkRecreationDone(studyEntity.getId(), userId);

        return createdStudyBasicInfos;
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

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        StudyEntity newStudyEntity = duplicateStudyEntity(sourceStudy, studyInfos.getId());
        rootNetworkService.duplicateStudyRootNetworks(newStudyEntity, sourceStudy);
        networkModificationTreeService.duplicateStudyNodes(newStudyEntity, sourceStudy);
        duplicateStudyNodeAliases(newStudyEntity, sourceStudy);

        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(newStudyEntity);
        studyInfosService.add(createdStudyBasicInfos);
        notificationService.emitStudiesChanged(studyInfos.getId(), userId);

        return newStudyEntity;
    }

    private StudyEntity duplicateStudyEntity(StudyEntity sourceStudyEntity, UUID newStudyId) {
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

        NonEvacuatedEnergyParametersInfos nonEvacuatedEnergyParametersInfos = sourceStudyEntity.getNonEvacuatedEnergyParameters() == null ?
            NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos() :
            NonEvacuatedEnergyService.fromEntity(sourceStudyEntity.getNonEvacuatedEnergyParameters());

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

        return studyRepository.save(StudyEntity.builder()
            .id(newStudyId)
            .loadFlowParametersUuid(copiedLoadFlowParametersUuid)
            .securityAnalysisParametersUuid(copiedSecurityAnalysisParametersUuid)
            .nonEvacuatedEnergyProvider(sourceStudyEntity.getNonEvacuatedEnergyProvider())
            .dynamicSimulationProvider(sourceStudyEntity.getDynamicSimulationProvider())
            .dynamicSimulationParameters(DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper))
            .shortCircuitParametersUuid(copiedShortCircuitParametersUuid)
            .voltageInitParametersUuid(copiedVoltageInitParametersUuid)
            .sensitivityAnalysisParametersUuid(copiedSensitivityAnalysisParametersUuid)
            .networkVisualizationParametersUuid(copiedNetworkVisualizationParametersUuid)
            .spreadsheetConfigCollectionUuid(copiedSpreadsheetConfigCollectionUuid)
            .nonEvacuatedEnergyParameters(NonEvacuatedEnergyService.toEntity(nonEvacuatedEnergyParametersInfos))
            .stateEstimationParametersUuid(copiedStateEstimationParametersUuid)
            .build());
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid, String firstRootNetworkName) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(studyUuid, firstRootNetworkName);
        notificationService.emitStudiesChanged(newStudy.getId(), userId);
        return newStudy;
    }

    public byte[] getVoltageLevelSvg(String voltageLevelId, DiagramParameters diagramParameters,
                                     UUID nodeUuid, UUID rootNetworkUuid) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getVoltageLevelSvg(networkUuid, variantId, voltageLevelId, diagramParameters);
        } else {
            return null;
        }
    }

    public String getVoltageLevelSvgAndMetadata(String voltageLevelId, DiagramParameters diagramParameters,
                                                UUID nodeUuid, UUID rootNetworkUuid) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getVoltageLevelSvgAndMetadata(networkUuid, variantId, voltageLevelId, diagramParameters);
        } else {
            return null;
        }
    }

    private void persistNetwork(UUID caseUuid, UUID studyUuid, UUID rootNetworkUuid, String variantId, String userId, UUID importReportUuid, String caseFormat, Map<String, Object> importParameters, CaseImportAction caseImportAction) {
        try {
            networkConversionService.persistNetwork(caseUuid, studyUuid, rootNetworkUuid, variantId, userId, importReportUuid, caseFormat, importParameters, caseImportAction);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, STUDY_CREATION_FAILED);
        }
    }

    public String getLinesGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> linesIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getLinesGraphics(networkUuid, variantId, linesIds);
    }

    public String getSubstationsGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getSubstationsGraphics(networkUuid, variantId, substationsIds);
    }

    public String getNetworkElementsInfos(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds, String infoType, String elementType, boolean inUpstreamBuiltParentNode, List<Double> nominalVoltages) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);
        return networkMapService.getElementsInfos(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                substationsIds, elementType, nominalVoltages, infoType, loadFlowParameters.getDcPowerFactor());
    }

    public String getNetworkElementInfos(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String elementType, InfoTypeParameters infoTypeParameters, String elementId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);
        return networkMapService.getElementInfos(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                elementType, infoTypeParameters.getInfoType(), loadFlowParameters.getDcPowerFactor(), elementId);
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

    public String getAllMapData(UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid),
                substationsIds, "all");
    }

    @Transactional
    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        if (prevResultUuid != null) {
            loadflowService.deleteLoadFlowResults(List.of(prevResultUuid));
        }

        UUID lfParametersUuid = loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        UUID lfReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(LOAD_FLOW.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW, lfReportUuid);
        UUID result = loadflowService.runLoadFlow(nodeUuid, rootNetworkUuid, networkUuid, variantId, lfParametersUuid, lfReportUuid, userId);

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, LOAD_FLOW);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        return result;
    }

    public void exportNetwork(UUID nodeUuid, UUID rootNetworkUuid, String format, String parametersJson, String fileName, HttpServletResponse exportNetworkResponse) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        networkConversionService.exportNetwork(networkUuid, variantId, format, parametersJson, fileName, exportNetworkResponse);
    }

    public void assertIsNodeNotReadOnly(UUID nodeUuid) {
        Boolean isReadOnly = networkModificationTreeService.isReadOnly(nodeUuid);
        if (Boolean.TRUE.equals(isReadOnly)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public void assertIsNodeExist(UUID studyUuid, UUID nodeUuid) {
        boolean exists = networkModificationTreeService.getAllNodes(studyUuid).stream()
                .anyMatch(nodeEntity -> nodeUuid.equals(nodeEntity.getIdNode()));

        if (!exists) {
            throw new StudyException(NODE_NOT_FOUND);
        }
    }

    public void assertIsStudyExist(UUID studyUuid) {
        boolean exists = studyRepository.existsById(studyUuid);
        if (!exists) {
            throw new StudyException(NODE_NOT_FOUND);
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return getLoadFlowParametersInfos(studyEntity);
    }

    public LoadFlowParametersInfos getLoadFlowParametersInfos(StudyEntity studyEntity) {
        UUID loadFlowParamsUuid = loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        return loadflowService.getLoadFlowParameters(loadFlowParamsUuid);
    }

    @Transactional
    public String getSecurityAnalysisParametersValues(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return securityAnalysisService.getSecurityAnalysisParameters(securityAnalysisService.getSecurityAnalysisParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public boolean setSecurityAnalysisParametersValues(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        boolean userProfileIssue = createOrUpdateSecurityAnalysisParameters(studyUuid, studyEntity, parameters, userId);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SECURITY_ANALYSIS);
        return userProfileIssue;
    }

    @Transactional
    public String getNetworkVisualizationParametersValues(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return studyConfigService.getNetworkVisualizationParameters(studyConfigService.getNetworkVisualizationParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public void setNetworkVisualizationParametersValues(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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

    @Transactional(readOnly = true)
    public NonEvacuatedEnergyParametersInfos getNonEvacuatedEnergyParametersInfos(UUID studyUuid) {
        return getNonEvacuatedEnergyParametersInfos(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)));
    }

    private NonEvacuatedEnergyParametersInfos getNonEvacuatedEnergyParametersInfos(StudyEntity studyEntity) {
        return studyEntity.getNonEvacuatedEnergyParameters() != null ?
            NonEvacuatedEnergyService.fromEntity(studyEntity.getNonEvacuatedEnergyParameters()) :
            NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos();
    }

    @Transactional
    public boolean setLoadFlowParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        boolean userProfileIssue = createOrUpdateLoadFlowParameters(studyEntity, parameters, userId);
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
        invalidateNonEvacuatedEnergyAnalysisStatusOnAllNodes(studyUuid);
        invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
        invalidateDynamicSecurityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, LOAD_FLOW);
        return userProfileIssue;
    }

    public String getDefaultLoadflowProvider(String userId) {
        if (userId != null) {
            UserProfileInfos userProfileInfos = userAdminService.getUserProfile(userId).orElse(null);
            if (userProfileInfos != null && userProfileInfos.getLoadFlowParameterId() != null) {
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        providerSetter.accept(studyEntity);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void updateLoadFlowProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            loadflowService.updateLoadFlowProvider(studyEntity.getLoadFlowParametersUuid(), provider);
            invalidateLoadFlowStatusOnAllNodes(studyUuid);
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

    public String getDefaultNonEvacuatedEnergyProvider() {
        return defaultNonEvacuatedEnergyProvider;
    }

    public String getDefaultDynamicSimulationProvider() {
        return defaultDynamicSimulationProvider;
    }

    public String getDynamicSimulationProvider(UUID studyUuid) {
        return getDynamicSimulationProvider(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)));
    }

    private String getDynamicSimulationProvider(StudyEntity studyEntity) {
        return studyEntity.getDynamicSimulationProvider();
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
            UserProfileInfos userProfileInfos = userAdminService.getUserProfile(userId).orElse(null);
            if (userProfileInfos != null && userProfileInfos.getDynamicSecurityAnalysisParameterId() != null) {
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        if (studyEntity.getShortCircuitParametersUuid() == null) {
            studyEntity.setShortCircuitParametersUuid(shortCircuitService.createParameters(null));
            studyRepository.save(studyEntity);
        }
        return shortCircuitService.getParameters(studyEntity.getShortCircuitParametersUuid());
    }

    @Transactional
    public boolean setShortCircuitParameters(UUID studyUuid, @Nullable String shortCircuitParametersInfos, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        boolean userProfileIssue = createOrUpdateShortcircuitParameters(studyEntity, shortCircuitParametersInfos, userId);
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
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId).orElse(null) : null;
        if (parameters == null && userProfileInfos != null && userProfileInfos.getShortcircuitParameterId() != null) {
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
    public UUID runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID saReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(SECURITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, SECURITY_ANALYSIS, saReportUuid);
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
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
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.LOAD_FLOW);
        return loadflowService.getLimitViolations(resultUuid, filters, globalFilters, sort, networkuuid, variantId);
    }

    public byte[] getSubstationSvg(String substationId, DiagramParameters diagramParameters,
                                   String substationLayout, UUID nodeUuid, UUID rootNetworkUuid) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getSubstationSvg(networkUuid, variantId, substationId, diagramParameters, substationLayout);
        } else {
            return null;
        }
    }

    public String getSubstationSvgAndMetadata(String substationId, DiagramParameters diagramParameters,
                                              String substationLayout, UUID nodeUuid, UUID rootNetworkUuid) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getSubstationSvgAndMetadata(networkUuid, variantId, substationId, diagramParameters, substationLayout);
        } else {
            return null;
        }
    }

    public String getNetworkAreaDiagram(UUID nodeUuid, UUID rootNetworkUuid, List<String> voltageLevelsIds, int depth, boolean withGeoData) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getNetworkAreaDiagram(networkUuid, variantId, voltageLevelsIds, depth, withGeoData);
        } else {
            return null;
        }
    }

    public String getNetworkAreaDiagram(UUID nodeUuid, UUID rootNetworkUuid, UUID nadConfigUuid) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(ROOT_NETWORK_NOT_FOUND);
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getNetworkAreaDiagram(networkUuid, variantId, nadConfigUuid);
        } else {
            return null;
        }
    }

    public void invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        securityAnalysisService.invalidateSaStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SECURITY_ANALYSIS));
    }

    public void invalidateSensitivityAnalysisStatusOnAllNodes(UUID studyUuid) {
        sensitivityAnalysisService.invalidateSensitivityAnalysisStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SENSITIVITY_ANALYSIS));
    }

    public void invalidateNonEvacuatedEnergyAnalysisStatusOnAllNodes(UUID studyUuid) {
        nonEvacuatedEnergyService.invalidateNonEvacuatedEnergyStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, NON_EVACUATED_ENERGY_ANALYSIS));
    }

    public void invalidateDynamicSimulationStatusOnAllNodes(UUID studyUuid) {
        dynamicSimulationService.invalidateStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, DYNAMIC_SIMULATION));
    }

    public void invalidateDynamicSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        dynamicSecurityAnalysisService.invalidateStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, DYNAMIC_SECURITY_ANALYSIS));
    }

    public void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        loadflowService.invalidateLoadFlowStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, LOAD_FLOW));
    }

    public void invalidateVoltageInitStatusOnAllNodes(UUID studyUuid) {
        voltageInitService.invalidateVoltageInitStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, VOLTAGE_INITIALIZATION));
    }

    public void invalidateStateEstimationStatusOnAllNodes(UUID studyUuid) {
        stateEstimationService.invalidateStateEstimationStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, STATE_ESTIMATION));
    }

    private StudyEntity updateRootNetworkIndexationStatus(StudyEntity studyEntity, RootNetworkEntity rootNetworkEntity, RootNetworkIndexationStatus indexationStatus) {
        rootNetworkEntity.setIndexationStatus(indexationStatus);
        notificationService.emitRootNetworkIndexationStatusChanged(studyEntity.getId(), rootNetworkEntity.getId(), indexationStatus);
        return studyEntity;
    }

    public StudyEntity updateRootNetworkIndexationStatus(UUID studyUuid, UUID rootNetworkUuid, RootNetworkIndexationStatus indexationStatus) {
        return updateRootNetworkIndexationStatus(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)), rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)), indexationStatus);
    }

    private StudyEntity saveStudyThenCreateBasicTree(UUID studyUuid, NetworkInfos networkInfos,
                                                    CaseInfos caseInfos, UUID loadFlowParametersUuid,
                                                    UUID shortCircuitParametersUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity,
                                                    UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                                    UUID networkVisualizationParametersUuid, UUID dynamicSecurityAnalysisParametersUuid, UUID stateEstimationParametersUuid,
                                                    UUID spreadsheetConfigCollectionUuid, Map<String, String> importParameters, UUID importReportUuid) {

        StudyEntity studyEntity = StudyEntity.builder()
                .id(studyUuid)
                .nonEvacuatedEnergyProvider(defaultNonEvacuatedEnergyProvider)
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
                .spreadsheetConfigCollectionUuid(spreadsheetConfigCollectionUuid)
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

        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId).orElse(null) : null;
        if (parameters == null && userProfileInfos != null && userProfileInfos.getLoadFlowParameterId() != null) {
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
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId).orElse(null) : null;
        if (parameters == null && userProfileInfos != null && userProfileInfos.getVoltageInitParameterId() != null) {
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
        invalidateVoltageInitStatusOnAllNodes(studyEntity.getId());

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

    public boolean createOrUpdateSecurityAnalysisParameters(UUID studyUuid, StudyEntity studyEntity, String parameters, String userId) {
        boolean userProfileIssue = false;
        UUID existingSecurityAnalysisParametersUuid = studyEntity.getSecurityAnalysisParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId).orElse(null) : null;
        if (parameters == null && userProfileInfos != null && userProfileInfos.getSecurityAnalysisParameterId() != null) {
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
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);

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
    public void createNetworkModification(UUID studyUuid, String createModificationAttributes, UUID nodeUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NetworkModificationsResult networkModificationResults = null;
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            List<RootNetworkEntity> studyRootNetworkEntities = getStudyRootNetworks(studyUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), nodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();
            networkModificationResults = networkModificationService.createModification(groupUuid, Pair.of(createModificationAttributes, modificationApplicationContexts));

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
            // invalidate all nodeUuid children
            invalidateNodeTree(studyUuid, nodeUuid, true);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
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

    public String getVoltageLevelSwitches(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId,
                                                           String switchesPath) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return networkMapService.getVoltageLevelSwitches(networkUuid, variantId, voltageLevelId, switchesPath);
    }

    public String getVoltageLevelSubstationId(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelSubstationId(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId);
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelBusesOrBusbarSections(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId, "buses-or-busbar-sections");
    }

    public String getVoltageLevelSwitches(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelSwitches(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId, "switches");
    }

    @Transactional(readOnly = true)
    public UUID getStudyUuidFromNodeUuid(UUID nodeUuid) {
        return networkModificationTreeService.getStudyUuidForNodeId(nodeUuid);
    }

    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        assertCanBuildNode(studyUuid, rootNetworkUuid, userId);
        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(nodeUuid, rootNetworkUuid);
        Map<UUID, UUID> nodeUuidToReportUuid = buildInfos.getReportsInfos().stream().collect(Collectors.toMap(ReportInfos::nodeUuid, ReportInfos::reportUuid));
        networkModificationTreeService.setModificationReports(nodeUuid, rootNetworkUuid, nodeUuidToReportUuid);
        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
        try {
            networkModificationService.buildNode(nodeUuid, rootNetworkUuid, buildInfos);
        } catch (Exception e) {
            networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT));
            throw new StudyException(NODE_BUILD_ERROR, e.getMessage());
        }
    }

    private void assertCanBuildNode(@NonNull UUID studyUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        // check restrictions on node builds number
        userAdminService.getUserMaxAllowedBuilds(userId).ifPresent(maxBuilds -> {
            long nbBuiltNodes = networkModificationTreeService.countBuiltNodes(studyUuid, rootNetworkUuid);
            if (nbBuiltNodes >= maxBuilds) {
                throw new StudyException(MAX_NODE_BUILDS_EXCEEDED, "max allowed built nodes : " + maxBuilds);
            }
        });
    }

    @Transactional
    public void unbuildStudyNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        invalidateNode(studyUuid, nodeUuid, rootNetworkUuid);
    }

    public void stopBuild(@NonNull UUID nodeUuid, UUID rootNetworkUuid) {
        networkModificationService.stopBuild(nodeUuid, rootNetworkUuid);
    }

    @Transactional
    public void duplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        checkStudyContainsNode(sourceStudyUuid, nodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
        boolean invalidateBuild = networkModificationTreeService.hasModifications(nodeToCopyUuid, false);
        if (invalidateBuild) {
            invalidateNodeTree(targetStudyUuid, duplicatedNodeUuid, true);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void moveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        List<NodeEntity> oldChildren = null;
        checkStudyContainsNode(studyUuid, nodeToMoveUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);
        boolean shouldUnbuildChildren = networkModificationTreeService.hasModifications(nodeToMoveUuid, false);

        //Unbuild previous children if necessary
        if (shouldUnbuildChildren) {
            oldChildren = networkModificationTreeService.getChildrenByParentUuid(nodeToMoveUuid);
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

    @Transactional
    public void duplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid, String userId) {
        checkStudyContainsNode(sourceStudyUuid, parentNodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);

        StudyEntity studyEntity = studyRepository.findById(targetStudyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        StudyEntity sourceStudyEntity = studyRepository.findById(sourceStudyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        AbstractNode studySubTree = networkModificationTreeService.getStudySubtree(sourceStudyUuid, parentNodeToCopyUuid, null);
        UUID duplicatedNodeUuid = networkModificationTreeService.cloneStudyTree(studySubTree, referenceNodeUuid, studyEntity, sourceStudyEntity, false);
        notificationService.emitSubtreeInserted(targetStudyUuid, duplicatedNodeUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void moveStudySubtree(UUID studyUuid, UUID parentNodeToMoveUuid, UUID referenceNodeUuid, String userId) {
        checkStudyContainsNode(studyUuid, parentNodeToMoveUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);

        List<UUID> allChildren = networkModificationTreeService.getChildren(parentNodeToMoveUuid);
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

    public void invalidateBuild(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateOnlyTargetNode, boolean deleteVoltageInitResults) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        invalidateNodeInfos.setNetworkUuid(rootNetworkService.getNetworkUuid(rootNetworkUuid));
        // we might want to invalidate target node without impacting other nodes (when moving an empty node for example)
        if (invalidateOnlyTargetNode) {
            networkModificationTreeService.invalidateBuildOfNodeOnly(nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos, deleteVoltageInitResults);
        } else {
            networkModificationTreeService.invalidateBuild(nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos, deleteVoltageInitResults);
        }

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() -> reportService.deleteReports(invalidateNodeInfos.getReportUuids())),
                studyServerExecutionService.runAsync(() -> loadflowService.deleteLoadFlowResults(invalidateNodeInfos.getLoadFlowResultUuids())),
                studyServerExecutionService.runAsync(() -> securityAnalysisService.deleteSecurityAnalysisResults(invalidateNodeInfos.getSecurityAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> sensitivityAnalysisService.deleteSensitivityAnalysisResults(invalidateNodeInfos.getSensitivityAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> nonEvacuatedEnergyService.deleteNonEvacuatedEnergyResults(invalidateNodeInfos.getNonEvacuatedEnergyResultUuids())),
                studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(invalidateNodeInfos.getShortCircuitAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(invalidateNodeInfos.getOneBusShortCircuitAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> voltageInitService.deleteVoltageInitResults(invalidateNodeInfos.getVoltageInitResultUuids())),
                studyServerExecutionService.runAsync(() -> dynamicSimulationService.deleteResults(invalidateNodeInfos.getDynamicSimulationResultUuids())),
                studyServerExecutionService.runAsync(() -> dynamicSecurityAnalysisService.deleteResults(invalidateNodeInfos.getDynamicSecurityAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> stateEstimationService.deleteStateEstimationResults(invalidateNodeInfos.getStateEstimationResultUuids())),
                studyServerExecutionService.runAsync(() -> networkStoreService.deleteVariants(invalidateNodeInfos.getNetworkUuid(), invalidateNodeInfos.getVariantIds())),
                studyServerExecutionService.runAsync(() -> networkModificationService.deleteIndexedModifications(invalidateNodeInfos.getGroupUuids(), invalidateNodeInfos.getNetworkUuid()))
        );
        try {
            executeInParallel.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StudyException(INVALIDATE_BUILD_FAILED, e.getMessage());
        } catch (Exception e) {
            throw new StudyException(INVALIDATE_BUILD_FAILED, e.getMessage());
        }

        if (startTime.get() != null) {
            LOGGER.trace("Invalidate node '{}' of study '{}' : {} seconds", nodeUuid, studyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    // Invalidate only one node
    private void invalidateNode(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());

        InvalidateNodeInfos invalidateNodeInfos = networkModificationTreeService.invalidateNode(nodeUuid, rootNetworkUuid);
        invalidateNodeInfos.setNetworkUuid(rootNetworkService.getNetworkUuid(rootNetworkUuid));

        deleteInvalidationInfos(invalidateNodeInfos);

        emitAllComputationStatusChanged(studyUuid, nodeUuid, rootNetworkUuid);

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
        invalidateNodeTree(studyUuid, nodeUuid, false);
    }

    private void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            invalidateNodeTree(studyUuid, nodeUuid, rootNetworkEntity.getId(), invalidateOnlyChildrenBuildStatus));
    }

    // Invalidate the node and its children
    public void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid, false);
    }

    private void invalidateNodeTree(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());

        InvalidateNodeInfos invalidateNodeInfos = networkModificationTreeService.invalidateNodeTree(nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus);
        invalidateNodeInfos.setNetworkUuid(rootNetworkService.getNetworkUuid(rootNetworkUuid));

        deleteInvalidationInfos(invalidateNodeInfos);

        emitAllComputationStatusChanged(studyUuid, nodeUuid, rootNetworkUuid);

        if (startTime.get() != null) {
            LOGGER.trace("unbuild node '{}' of study '{}' : {} seconds", nodeUuid, studyUuid,
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    public void deleteInvalidationInfos(InvalidateNodeInfos invalidateNodeInfos) {
        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
            studyServerExecutionService.runAsync(() -> networkModificationService.deleteIndexedModifications(invalidateNodeInfos.getGroupUuids(), invalidateNodeInfos.getNetworkUuid())),
            studyServerExecutionService.runAsync(() -> networkStoreService.deleteVariants(invalidateNodeInfos.getNetworkUuid(), invalidateNodeInfos.getVariantIds())),
            studyServerExecutionService.runAsync(() -> reportService.deleteReports(invalidateNodeInfos.getReportUuids())),
            studyServerExecutionService.runAsync(() -> loadflowService.deleteLoadFlowResults(invalidateNodeInfos.getLoadFlowResultUuids())),
            studyServerExecutionService.runAsync(() -> securityAnalysisService.deleteSecurityAnalysisResults(invalidateNodeInfos.getSecurityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> sensitivityAnalysisService.deleteSensitivityAnalysisResults(invalidateNodeInfos.getSensitivityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> nonEvacuatedEnergyService.deleteNonEvacuatedEnergyResults(invalidateNodeInfos.getNonEvacuatedEnergyResultUuids())),
            studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(invalidateNodeInfos.getShortCircuitAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(invalidateNodeInfos.getOneBusShortCircuitAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> voltageInitService.deleteVoltageInitResults(invalidateNodeInfos.getVoltageInitResultUuids())),
            studyServerExecutionService.runAsync(() -> dynamicSimulationService.deleteResults(invalidateNodeInfos.getDynamicSimulationResultUuids())),
            studyServerExecutionService.runAsync(() -> dynamicSecurityAnalysisService.deleteResults(invalidateNodeInfos.getDynamicSecurityAnalysisResultUuids())),
            studyServerExecutionService.runAsync(() -> stateEstimationService.deleteStateEstimationResults(invalidateNodeInfos.getStateEstimationResultUuids()))
        );
        try {
            executeInParallel.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StudyException(INVALIDATE_BUILD_FAILED, e.getMessage());
        } catch (Exception e) {
            throw new StudyException(INVALIDATE_BUILD_FAILED, e.getMessage());
        }

    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateBuild, boolean deleteVoltageInitResults) {
        getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity -> {
            UUID rootNetworkUuid = rootNetworkEntity.getId();
            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus, invalidateBuild, deleteVoltageInitResults);
        });
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateBuild, boolean deleteVoltageInitResults) {
        invalidateBuild(studyUuid, nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus, false, deleteVoltageInitResults);
        emitAllComputationStatusChanged(studyUuid, nodeUuid, rootNetworkUuid);
    }

    @Transactional
    public void deleteNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.deleteModifications(groupId, modificationsUuids);
            // for each root network, remove modifications from excluded ones
            studyEntity.getRootNetworks().forEach(rootNetworkEntity -> {
                rootNetworkNodeInfoService.updateModificationsToExclude(nodeUuid, rootNetworkEntity.getId(), new HashSet<>(modificationsUuids), true);
            });
        } finally {
            notificationService.emitEndDeletionEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
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
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
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
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
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
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        if (!CollectionUtils.isEmpty(studyEntity.getNodeAliases())) {
            Set<UUID> allNodeIds = new HashSet<>(nodeIds);
            if (removeChildren) {
                nodeIds.forEach(n -> allNodeIds.addAll(networkModificationTreeService.getAllChildrenFromParentUuid(n).stream().map(UUID::fromString).toList()));
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
            List<NodeEntity> childrenNodes = networkModificationTreeService.getChildrenByParentUuid(nodeId);
            List<UUID> removedNodes = networkModificationTreeService.doDeleteNode(nodeId, deleteChildren, deleteNodeInfos);

            CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getModificationGroupUuids().forEach(networkModificationService::deleteModifications)),
                    studyServerExecutionService.runAsync(() -> reportService.deleteReports(deleteNodeInfos.getReportUuids())),
                    studyServerExecutionService.runAsync(() -> loadflowService.deleteLoadFlowResults(deleteNodeInfos.getLoadFlowResultUuids())),
                    studyServerExecutionService.runAsync(() -> securityAnalysisService.deleteSecurityAnalysisResults(deleteNodeInfos.getSecurityAnalysisResultUuids())),
                    studyServerExecutionService.runAsync(() -> sensitivityAnalysisService.deleteSensitivityAnalysisResults(deleteNodeInfos.getSensitivityAnalysisResultUuids())),
                    studyServerExecutionService.runAsync(() -> nonEvacuatedEnergyService.deleteNonEvacuatedEnergyResults(deleteNodeInfos.getNonEvacuatedEnergyResultUuids())),
                    studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(deleteNodeInfos.getShortCircuitAnalysisResultUuids())),
                    studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(deleteNodeInfos.getOneBusShortCircuitAnalysisResultUuids())),
                    studyServerExecutionService.runAsync(() -> voltageInitService.deleteVoltageInitResults(deleteNodeInfos.getVoltageInitResultUuids())),
                    studyServerExecutionService.runAsync(() -> dynamicSimulationService.deleteResults(deleteNodeInfos.getDynamicSimulationResultUuids())),
                    studyServerExecutionService.runAsync(() -> dynamicSecurityAnalysisService.deleteResults(deleteNodeInfos.getDynamicSecurityAnalysisResultUuids())),
                    studyServerExecutionService.runAsync(() -> stateEstimationService.deleteStateEstimationResults(deleteNodeInfos.getStateEstimationResultUuids())),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getVariantIds().forEach(networkStoreService::deleteVariants)),
                    studyServerExecutionService.runAsync(() -> removedNodes.forEach(dynamicSimulationEventService::deleteEventsByNodeId))
            );

            try {
                executeInParallel.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StudyException(DELETE_NODE_FAILED, e.getMessage());
            } catch (Exception e) {
                throw new StudyException(DELETE_NODE_FAILED, e.getMessage());
            }

            if (startTime.get() != null && LOGGER.isTraceEnabled()) {
                LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId.toString().replaceAll("[\n\r]", "_"), studyUuid,
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
            }

            if (invalidateChildrenBuild) {
                childrenNodes.forEach(nodeEntity -> invalidateNodeTree(studyUuid, nodeEntity.getIdNode()));
            }
        }

        notificationService.emitElementUpdated(studyUuid, userId);
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
        networkModificationTreeService.restoreNode(studyId, nodeIds, anchorNodeId);
    }

    private void reindexRootNetwork(StudyEntity study, UUID rootNetworkUuid) {
        CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study);
        // reindex root network for study in elasticsearch
        studyInfosService.recreateStudyInfos(studyInfos);
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));

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
        reindexRootNetwork(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)), rootNetworkUuid);
    }

    @Transactional
    public RootNetworkIndexationStatus getRootNetworkIndexationStatus(UUID studyUuid, UUID rootNetworkUuid) {
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
        if (rootNetwork.getIndexationStatus() == RootNetworkIndexationStatus.INDEXED
                && !networkConversionService.checkStudyIndexationStatus(rootNetworkService.getNetworkUuid(rootNetworkUuid))) {
            updateRootNetworkIndexationStatus(study, rootNetwork, RootNetworkIndexationStatus.NOT_INDEXED);
        }
        return rootNetwork.getIndexationStatus();
    }

    @Transactional
    public void moveNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationUuidList, UUID beforeUuid, String userId) {
        if (originNodeUuid == null) {
            throw new StudyException(MISSING_PARAMETER, "The parameter 'originNodeUuid' must be defined when moving modifications");
        }

        boolean moveBetweenNodes = !targetNodeUuid.equals(originNodeUuid);
        // Target node must not be built (incremental mode) when:
        // - the move is a cut & paste or a position change inside the same node
        // - the move is a cut & paste between 2 nodes and the target node belongs to the source node subtree
        boolean targetNodeBelongsToSourceNodeSubTree = moveBetweenNodes && networkModificationTreeService.hasAncestor(targetNodeUuid, originNodeUuid);
        boolean buildTargetNode = moveBetweenNodes && !targetNodeBelongsToSourceNodeSubTree;

        List<UUID> childrenUuids = networkModificationTreeService.getChildren(targetNodeUuid);
        List<UUID> originNodeChildrenUuids = new ArrayList<>();
        notificationService.emitStartModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        if (moveBetweenNodes) {
            originNodeChildrenUuids = networkModificationTreeService.getChildren(originNodeUuid);
            notificationService.emitStartModificationEquipmentNotification(studyUuid, originNodeUuid, originNodeChildrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        }
        try {
            checkStudyContainsNode(studyUuid, targetNodeUuid);
            StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
            List<RootNetworkEntity> studyRootNetworkEntities = studyEntity.getRootNetworks();
            UUID originGroupUuid = networkModificationTreeService.getModificationGroupUuid(originNodeUuid);
            UUID targetGroupUuid = networkModificationTreeService.getModificationGroupUuid(targetNodeUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), targetNodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();

            NetworkModificationsResult networkModificationsResult = networkModificationService.moveModifications(originGroupUuid, targetGroupUuid, beforeUuid, Pair.of(modificationUuidList, modificationApplicationContexts), buildTargetNode);
            rootNetworkNodeInfoService.moveModificationsToExclude(originNodeUuid, targetNodeUuid, networkModificationsResult.modificationUuids());

            if (!targetNodeBelongsToSourceNodeSubTree) {
                // invalidate the whole subtree except maybe the target node itself (depends if we have built this node during the move)
                emitNetworkModificationImpactsForAllRootNetworks(networkModificationsResult.modificationResults(), studyEntity, targetNodeUuid, buildTargetNode);
            }
            if (moveBetweenNodes) {
                emitNetworkModificationImpactsForAllRootNetworks(networkModificationsResult.modificationResults(), studyEntity, originNodeUuid, false);
            }
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids);
            if (moveBetweenNodes) {
                notificationService.emitEndModificationEquipmentNotification(studyUuid, originNodeUuid, originNodeChildrenUuids);
            }
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void emitNetworkModificationImpactsForAllRootNetworks(List<Optional<NetworkModificationResult>> modificationResults, StudyEntity studyEntity, UUID impactedNode, boolean invalidateOnlyChildrenBuildStatus) {
        int index = 0;
        List<RootNetworkEntity> rootNetworkEntities = studyEntity.getRootNetworks();
        // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
        for (Optional<NetworkModificationResult> modificationResultOpt : modificationResults) {
            if (modificationResultOpt.isPresent() && rootNetworkEntities.get(index) != null) {
                emitNetworkModificationImpacts(studyEntity.getId(), impactedNode, rootNetworkEntities.get(index).getId(), modificationResultOpt.get());
            }
            index++;

        }
        invalidateNodeTree(studyEntity.getId(), impactedNode, invalidateOnlyChildrenBuildStatus);
    }

    @Transactional
    public void duplicateOrInsertNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsUuis, String userId, StudyConstants.ModificationsActionType action) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(targetNodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(studyUuid, targetNodeUuid);
            List<RootNetworkEntity> studyRootNetworkEntities = getStudyRootNetworks(studyUuid);
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(targetNodeUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), targetNodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();
            NetworkModificationsResult networkModificationResults = networkModificationService.duplicateOrInsertModifications(groupUuid, action, Pair.of(modificationsUuis, modificationApplicationContexts));

            Map<UUID, UUID> originToDuplicateModificationsUuids = new HashMap<>();
            for (int i = 0; i < modificationsUuis.size(); i++) {
                originToDuplicateModificationsUuids.put(modificationsUuis.get(i), networkModificationResults.modificationUuids().get(i));
            }

            rootNetworkNodeInfoService.copyModificationsToExclude(originNodeUuid, targetNodeUuid, originToDuplicateModificationsUuids);

            if (networkModificationResults != null) {
                int index = 0;
                // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
                for (Optional<NetworkModificationResult> modificationResultOpt : networkModificationResults.modificationResults()) {
                    if (modificationResultOpt.isPresent() && studyRootNetworkEntities.get(index) != null) {
                        emitNetworkModificationImpacts(studyUuid, targetNodeUuid, studyRootNetworkEntities.get(index).getId(), modificationResultOpt.get());
                    }
                    index++;
                }
            }
            // invalidate all nodeUuid children
            invalidateNodeTree(studyUuid, targetNodeUuid, true);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void checkStudyContainsNode(UUID studyUuid, UUID nodeUuid) {
        if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    @Transactional(readOnly = true)
    public List<ReportLog> getReportLogs(String reportId, String messageFilter, Set<String> severityLevels) {
        return reportService.getReportLogs(UUID.fromString(reportId), messageFilter, severityLevels);
    }

    public Set<String> getNodeReportAggregatedSeverities(UUID reportId) {
        return reportService.getReportAggregatedSeverities(reportId);
    }

    @Transactional(readOnly = true)
    public Set<String> getParentNodesAggregatedReportSeverities(UUID nodeUuid, UUID rootNetworkUuid) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        Set<String> severities = new HashSet<>();
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        for (UUID nodeId : nodeIds) {
            UUID reportId = modificationReportsMap.getOrDefault(nodeId, networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid));
            severities.addAll(reportService.getReportAggregatedSeverities(reportId));
        }
        return severities;
    }

    @Transactional(readOnly = true)
    public List<ReportLog> getParentNodesReportLogs(UUID nodeUuid, UUID rootNetworkUuid, String messageFilter, Set<String> severityLevels) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        List<ReportLog> reportLogs = new ArrayList<>();
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        for (UUID nodeId : nodeIds) {
            UUID reportId = modificationReportsMap.getOrDefault(nodeId, networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid));
            reportLogs.addAll(reportService.getReportLogs(reportId, messageFilter, severityLevels));
        }
        return reportLogs;
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
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid, rootNetworkUuid);
        return List.of(reportService.getReport(reportUuid, nodeUuid.toString(), severityLevels));
    }

    private List<Report> getAllModificationReports(UUID nodeUuid, UUID rootNetworkUuid, Set<String> severityLevels) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        List<Report> modificationReports = new ArrayList<>();
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        for (UUID nodeId : nodeIds) {
            UUID reportId = modificationReportsMap.getOrDefault(nodeId, networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid));
            modificationReports.add(reportService.getReport(reportId, nodeId.toString(), severityLevels));
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

    public void notify(@NonNull String notificationName, @NonNull UUID studyUuid) {
        if (notificationName.equals(NotificationService.UPDATE_TYPE_STUDY_METADATA_UPDATED)) {
            notificationService.emitStudyMetadataChanged(studyUuid);
        } else {
            throw new StudyException(UNKNOWN_NOTIFICATION_TYPE);
        }
    }

    @Transactional
    public UUID runSensitivityAnalysis(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS);
        if (prevResultUuid != null) {
            sensitivityAnalysisService.deleteSensitivityAnalysisResults(List.of(prevResultUuid));
        }
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID sensiReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(SENSITIVITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS, sensiReportUuid);

        UUID result = sensitivityAnalysisService.runSensitivityAnalysis(nodeUuid, rootNetworkUuid, networkUuid, variantId, sensiReportUuid, userId, study.getSensitivityAnalysisParametersUuid(), study.getLoadFlowParametersUuid());

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, SENSITIVITY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        return result;
    }

    @Transactional
    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Optional<String> busId, String userId) {
        ComputationType computationType = busId.isEmpty() ? SHORT_CIRCUIT : SHORT_CIRCUIT_ONE_BUS;
        UUID shortCircuitResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, computationType);
        if (shortCircuitResultUuid != null) {
            shortCircuitService.deleteShortCircuitAnalysisResults(List.of(shortCircuitResultUuid));
        }
        final Optional<UUID> parametersUuid = studyRepository.findById(studyUuid).map(StudyEntity::getShortCircuitParametersUuid);
        UUID scReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(computationType.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, computationType, scReportUuid);
        final UUID result = shortCircuitService.runShortCircuit(nodeUuid, rootNetworkUuid, networkUuid, variantId, busId.orElse(null), parametersUuid, scReportUuid, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, computationType);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid,
                busId.isEmpty() ? NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS : NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        return result;
    }

    @Transactional
    public UUID runVoltageInit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION);
        if (prevResultUuid != null) {
            voltageInitService.deleteVoltageInitResults(List.of(prevResultUuid));
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(VOLTAGE_INITIALIZATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION, reportUuid);
        UUID result = voltageInitService.runVoltageInit(networkUuid, variantId, studyEntity.getVoltageInitParametersUuid(), reportUuid, nodeUuid, rootNetworkUuid, userId);

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, VOLTAGE_INITIALIZATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        return result;
    }

    @Transactional
    public boolean setVoltageInitParameters(UUID studyUuid, StudyVoltageInitParameters parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        var voltageInitParameters = studyEntity.getVoltageInitParameters();
        if (voltageInitParameters == null) {
            var newVoltageInitParameters = new StudyVoltageInitParametersEntity(parameters.isApplyModifications());
            studyEntity.setVoltageInitParameters(newVoltageInitParameters);
        } else {
            voltageInitParameters.setApplyModifications(parameters.isApplyModifications());
        }
        boolean userProfileIssue = createOrUpdateVoltageInitParameters(studyEntity, parameters.getComputationParameters(), userId);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, VOLTAGE_INITIALIZATION);
        return userProfileIssue;
    }

    public StudyVoltageInitParameters getVoltageInitParameters(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return new StudyVoltageInitParameters(
                Optional.ofNullable(studyEntity.getVoltageInitParametersUuid()).map(voltageInitService::getVoltageInitParameters).orElse(null),
                Optional.ofNullable(studyEntity.getVoltageInitParameters()).map(StudyVoltageInitParametersEntity::shouldApplyModifications).orElse(true)
        );
    }

    @Transactional
    public String getSpreadsheetConfigCollection(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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

        UserProfileInfos userProfileInfos = configCollection == null ? userAdminService.getUserProfile(userId).orElse(null) : null;
        if (configCollection == null && userProfileInfos != null && userProfileInfos.getSpreadsheetConfigCollectionId() != null) {
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        DynamicSimulationParametersInfos configuredParameters = getDynamicSimulationParameters(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)));
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
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
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
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
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartEventCrudNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.EVENTS_CRUD_DELETING_IN_PROGRESS);
        try {
            dynamicSimulationEventService.deleteEvents(eventUuids);
        } finally {
            notificationService.emitEndEventCrudNotification(studyUuid, nodeUuid, childrenUuids);
        }
        postProcessEventCrud(studyUuid, nodeUuid);
    }

    @Transactional
    public UUID runDynamicSimulation(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, DynamicSimulationParametersInfos parameters, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        // pre-condition check
        String lfStatus = rootNetworkNodeInfoService.getLoadFlowStatus(nodeUuid, rootNetworkUuid);
        if (!LoadFlowStatus.CONVERGED.name().equals(lfStatus)) {
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
        UUID dynamicSimulationResultUuid = dynamicSimulationService.runDynamicSimulation(getDynamicSimulationProvider(studyEntity), nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid, mergeParameters, userId);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicSimulationResultUuid, DYNAMIC_SIMULATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);

        return dynamicSimulationResultUuid;
    }

    // --- Dynamic Simulation service methods END --- //

    // --- Dynamic Security Analysis service methods BEGIN --- //

    public UUID getDynamicSecurityAnalysisParametersUuid(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return studyEntity.getDynamicSecurityAnalysisParametersUuid();
    }

    @Transactional
    public String getDynamicSecurityAnalysisParameters(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return dynamicSecurityAnalysisService.getParameters(
                dynamicSecurityAnalysisService.getDynamicSecurityAnalysisParametersUuidOrElseCreateDefault(studyEntity));
    }

    @Transactional
    public boolean setDynamicSecurityAnalysisParameters(UUID studyUuid, String dsaParameter, String userId) {
        boolean userProfileIssue = createOrUpdateDynamicSecurityAnalysisParameters(studyUuid, dsaParameter, userId);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, DYNAMIC_SECURITY_ANALYSIS);
        return userProfileIssue;
    }

    public boolean createOrUpdateDynamicSecurityAnalysisParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        boolean userProfileIssue = false;
        UUID existingDynamicSecurityAnalysisParametersUuid = studyEntity.getDynamicSecurityAnalysisParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId).orElse(null) : null;
        if (parameters == null && userProfileInfos != null && userProfileInfos.getDynamicSecurityAnalysisParameterId() != null) {
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
        invalidateDynamicSecurityAnalysisStatusOnAllNodes(studyUuid);

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
    public UUID runDynamicSecurityAnalysis(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        // pre-condition check
        String lfStatus = rootNetworkNodeInfoService.getLoadFlowStatus(nodeUuid, rootNetworkUuid);
        if (!LoadFlowStatus.CONVERGED.name().equals(lfStatus)) {
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
        UUID dynamicSecurityAnalysisParametersUuid = getDynamicSecurityAnalysisParametersUuid(studyUuid);

        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(DYNAMIC_SECURITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SECURITY_ANALYSIS, reportUuid);

        // launch dynamic security analysis
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID dynamicSecurityAnalysisResultUuid = dynamicSecurityAnalysisService.runDynamicSecurityAnalysis(getDynamicSimulationProvider(studyUuid),
            nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid, dynamicSimulationResultUuid, dynamicSecurityAnalysisParametersUuid, userId);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicSecurityAnalysisResultUuid, DYNAMIC_SECURITY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);

        return dynamicSecurityAnalysisResultUuid;
    }

    // --- Dynamic Security Analysis service methods END --- //

    public String getNetworkElementsIds(UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String equipmentType, List<Double> nominalVoltages) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getElementsIds(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                substationsIds, equipmentType, nominalVoltages);
    }

    @Transactional(readOnly = true)
    public List<ModificationInfos> getVoltageInitModifications(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        UUID voltageInitModificationsGroupUuid = voltageInitService.getModificationsGroupUuid(nodeUuid, resultUuid);
        return networkModificationService.getModifications(voltageInitModificationsGroupUuid, false, false);
    }

    @Transactional
    public void insertVoltageInitModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        UUID voltageInitModificationsGroupUuid = voltageInitService.getModificationsGroupUuid(nodeUuid, resultUuid);
        if (voltageInitModificationsGroupUuid == null) {
            return;
        }

        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);

            List<RootNetworkEntity> studyRootNetworkEntities = getStudyRootNetworks(studyUuid);
            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), nodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();
            NetworkModificationsResult networkModificationResults = networkModificationService.duplicateModificationsFromGroup(networkModificationTreeService.getModificationGroupUuid(nodeUuid), voltageInitModificationsGroupUuid, Pair.of(List.of(), modificationApplicationContexts));

            if (networkModificationResults != null) {
                int index = 0;
                // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
                for (Optional<NetworkModificationResult> modificationResultOpt : networkModificationResults.modificationResults()) {
                    if (modificationResultOpt.isPresent() && studyRootNetworkEntities.get(index) != null) {
                        emitNetworkModificationImpacts(studyUuid, nodeUuid, studyRootNetworkEntities.get(index).getId(), modificationResultOpt.get());
                    }
                    index++;
                }
            }

            voltageInitService.resetModificationsGroupUuid(nodeUuid, resultUuid);

            // invalidate the whole subtree except the target node (we have built this node during the duplication)
            notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT); // send notification voltage init result has changed
            updateStatuses(studyUuid, nodeUuid, true, true, false);  // do not delete the voltage init results
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public String getSensitivityAnalysisParameters(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return sensitivityAnalysisService.getSensitivityAnalysisParameters(
                sensitivityAnalysisService.getSensitivityAnalysisParametersUuidOrElseCreateDefault(studyEntity));
    }

    @Transactional
    public boolean setSensitivityAnalysisParameters(UUID studyUuid, String parameters, String userId) {
        boolean userProfileIssue = createOrUpdateSensitivityAnalysisParameters(studyUuid, parameters, userId);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SENSITIVITY_ANALYSIS);
        return userProfileIssue;
    }

    @Transactional
    public void setNonEvacuatedEnergyParametersInfos(UUID studyUuid, NonEvacuatedEnergyParametersInfos parameters, String userId) {
        updateNonEvacuatedEnergyParameters(studyUuid,
                NonEvacuatedEnergyService.toEntity(parameters != null ? parameters :
                        NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos()));
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, NON_EVACUATED_ENERGY_ANALYSIS);

    }

    public boolean createOrUpdateSensitivityAnalysisParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        boolean userProfileIssue = false;
        UUID existingSensitivityAnalysisParametersUuid = studyEntity.getSensitivityAnalysisParametersUuid();
        UserProfileInfos userProfileInfos = parameters == null ? userAdminService.getUserProfile(userId).orElse(null) : null;
        if (parameters == null && userProfileInfos != null && userProfileInfos.getSensitivityAnalysisParameterId() != null) {
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
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);

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

    public void updateNonEvacuatedEnergyParameters(UUID studyUuid, NonEvacuatedEnergyParametersEntity nonEvacuatedEnergyParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setNonEvacuatedEnergyParameters(nonEvacuatedEnergyParametersEntity));
    }

    @Transactional
    public void invalidateLoadFlowStatus(UUID studyUuid, String userId) {
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void invalidateShortCircuitStatusOnAllNodes(UUID studyUuid) {
        shortCircuitService.invalidateShortCircuitStatus(Stream.concat(
            rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SHORT_CIRCUIT).stream(),
            rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, SHORT_CIRCUIT_ONE_BUS).stream()
        ).toList());
    }

    @Transactional
    public void invalidateShortCircuitStatus(UUID studyUuid) {
        invalidateShortCircuitStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
    }

    @Transactional
    public UUID runNonEvacuatedEnergy(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, NON_EVACUATED_ENERGY_ANALYSIS);

        if (prevResultUuid != null) {
            nonEvacuatedEnergyService.deleteNonEvacuatedEnergyResults(List.of(prevResultUuid));
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String provider = getNonEvacuatedEnergyProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(NON_EVACUATED_ENERGY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, NON_EVACUATED_ENERGY_ANALYSIS, reportUuid);

        NonEvacuatedEnergyParametersInfos nonEvacuatedEnergyParametersInfos = getNonEvacuatedEnergyParametersInfos(studyEntity);
        SensitivityAnalysisParameters sensitivityAnalysisParameters = SensitivityAnalysisParameters.load();
        sensitivityAnalysisParameters.setFlowFlowSensitivityValueThreshold(nonEvacuatedEnergyParametersInfos.getGeneratorsCappings().getSensitivityThreshold());

        NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData = new NonEvacuatedEnergyInputData();
        nonEvacuatedEnergyInputData.setParameters(sensitivityAnalysisParameters);

        nonEvacuatedEnergyInputData.setNonEvacuatedEnergyStagesDefinition(nonEvacuatedEnergyParametersInfos.getStagesDefinition());

        nonEvacuatedEnergyInputData.setNonEvacuatedEnergyStagesSelection(nonEvacuatedEnergyParametersInfos.getStagesSelection()
                .stream()
                .filter(NonEvacuatedEnergyStagesSelection::isActivated)
                .collect(Collectors.toList()));

        List<NonEvacuatedEnergyGeneratorCappingsByType> generatorsCappingsByType = nonEvacuatedEnergyParametersInfos.getGeneratorsCappings().getGenerators()
                .stream()
                .filter(NonEvacuatedEnergyGeneratorCappingsByType::isActivated)
                .collect(Collectors.toList());
        NonEvacuatedEnergyGeneratorsCappings generatorsCappings = new NonEvacuatedEnergyGeneratorsCappings(nonEvacuatedEnergyParametersInfos.getGeneratorsCappings().getSensitivityThreshold(), generatorsCappingsByType);
        nonEvacuatedEnergyInputData.setNonEvacuatedEnergyGeneratorsCappings(generatorsCappings);

        nonEvacuatedEnergyInputData.setNonEvacuatedEnergyMonitoredBranches(nonEvacuatedEnergyParametersInfos.getMonitoredBranches()
                .stream()
                .filter(NonEvacuatedEnergyMonitoredBranches::isActivated)
                .collect(Collectors.toList()));

        nonEvacuatedEnergyInputData.setNonEvacuatedEnergyContingencies(nonEvacuatedEnergyParametersInfos.getContingencies()
                .stream()
                .filter(NonEvacuatedEnergyContingencies::isActivated)
                .collect(Collectors.toList()));

        UUID result = nonEvacuatedEnergyService.runNonEvacuatedEnergy(nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid, provider, nonEvacuatedEnergyInputData, studyEntity.getLoadFlowParametersUuid(), userId);

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, NON_EVACUATED_ENERGY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        return result;
    }

    @Transactional
    public void updateNonEvacuatedEnergyProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setNonEvacuatedEnergyProvider(provider != null ? provider : defaultNonEvacuatedEnergyProvider);
            invalidateNonEvacuatedEnergyAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, null, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
            notificationService.emitComputationParamsChanged(studyUuid, NON_EVACUATED_ENERGY_ANALYSIS);
        });
    }

    public String getNonEvacuatedEnergyProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(StudyEntity::getNonEvacuatedEnergyProvider)
                .orElse("");
    }

    private void emitAllComputationStatusChanged(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
    }

    @Transactional(readOnly = true)
    public String evaluateFilter(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String filter) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return filterService.evaluateFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid), filter);
    }

    @Transactional(readOnly = true)
    public String exportFilter(UUID rootNetworkUuid, UUID filterUuid) {
        return filterService.exportFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), filterUuid);
    }

    @Transactional(readOnly = true)
    public String exportFilterFromFirstRootNetwork(UUID studyUuid, UUID filterUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return filterService.exportFilter(studyEntity.getFirstRootNetwork().getNetworkUuid(), filterUuid);
    }

    @Transactional(readOnly = true)
    public String exportFilters(UUID rootNetworkUuid, List<UUID> filtersUuid) {
        return filterService.exportFilters(rootNetworkService.getNetworkUuid(rootNetworkUuid), filtersUuid);
    }

    @Transactional
    public UUID runStateEstimation(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        if (studyRepository.findById(studyUuid).isEmpty()) {
            throw new StudyException(STUDY_NOT_FOUND);
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID parametersUuid = studyRepository.findById(studyUuid).orElseThrow().getStateEstimationParametersUuid();
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

        UUID result = stateEstimationService.runStateEstimation(networkUuid, variantId, parametersUuid, new ReportInfos(reportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, STATE_ESTIMATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        return result;
    }

    @Transactional
    public String getStateEstimationParameters(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return stateEstimationService.getStateEstimationParameters(stateEstimationService.getStateEstimationParametersUuidOrElseCreateDefaults(studyEntity));
    }

    @Transactional
    public void setStateEstimationParametersValues(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        createOrUpdateStateEstimationParameters(studyEntity, parameters);
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
        invalidateStateEstimationStatusOnAllNodes(studyEntity.getId());
    }

    @Transactional
    public NetworkModificationNode createNode(UUID studyUuid, UUID nodeId, NetworkModificationNode nodeInfo, InsertMode insertMode, String userId) {
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        NetworkModificationNode newNode = networkModificationTreeService.createNode(study, nodeId, nodeInfo, insertMode, userId);

        UUID parentUuid = networkModificationTreeService.getParentNodeUuid(newNode.getId()).orElse(null);
        notificationService.emitNodeInserted(study.getId(), parentUuid, newNode.getId(), insertMode, nodeId);
        // userId is null when creating initial nodes, we don't need to send element update notifications in this case
        if (userId != null) {
            notificationService.emitElementUpdated(study.getId(), userId);
        }
        return newNode;
    }

    private List<RootNetworkEntity> getStudyRootNetworks(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
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

    public void reorderColumns(UUID studyUuid, UUID configUuid, List<UUID> columnOrder) {
        studyConfigService.reorderColumns(configUuid, columnOrder);
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
}
