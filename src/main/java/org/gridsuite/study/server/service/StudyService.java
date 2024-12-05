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
import lombok.NonNull;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.nonevacuatedenergy.*;
import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
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
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final NonEvacuatedEnergyService nonEvacuatedEnergyService;
    private final DynamicSimulationEventService dynamicSimulationEventService;
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
        VoltageInitService voltageInitService,
        DynamicSimulationEventService dynamicSimulationEventService,
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
        this.voltageInitService = voltageInitService;
        this.dynamicSimulationEventService = dynamicSimulationEventService;
        this.filterService = filterService;
        this.stateEstimationService = stateEstimationService;
        this.self = studyService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
    }

    private CreatedStudyBasicInfos toStudyInfos(UUID studyUuid, UUID rootNetworkUuid) {
        String caseFormat = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)).getCaseFormat();
        return CreatedStudyBasicInfos.builder()
                .id(studyUuid)
                .caseFormat(caseFormat)
                .build();
    }

    private static BasicStudyInfos toBasicStudyInfos(StudyCreationRequestEntity entity) {
        return BasicStudyInfos.builder()
                .id(entity.getId())
                .build();
    }

    private CreatedStudyBasicInfos toCreatedStudyBasicInfos(StudyEntity entity, UUID rootNetworkUuid) {
        return CreatedStudyBasicInfos.builder()
                .id(entity.getId())
                .caseFormat(rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND)).getCaseFormat())
                .build();
    }

    public List<CreatedStudyBasicInfos> getStudies() {
        return studyRepository.findAll().stream()
                .map(studyEntity -> {
                    UUID rootNetworkUuid = self.getStudyFirstRootNetworkUuid(studyEntity.getId());
                    return toCreatedStudyBasicInfos(studyEntity, rootNetworkUuid);
                })
                .collect(Collectors.toList());
    }

    public List<UUID> getAllOrphanIndexedEquipmentsNetworkUuids() {
        return equipmentInfosService.getOrphanEquipmentInfosNetworkUuids(rootNetworkService.getAllNetworkUuids());
    }

    public List<CreatedStudyBasicInfos> getStudiesMetadata(List<UUID> uuids) {
        return studyRepository.findAllById(uuids).stream().map(studyEntity -> {
            //TODO: for now, gridexplore uses caseFormat as metadata. Now studies can have several different caseFormat, what should we do ?
            UUID rootNetworkUuid = self.getStudyFirstRootNetworkUuid(studyEntity.getId());
            return toCreatedStudyBasicInfos(studyEntity, rootNetworkUuid);
        }).toList();

    }

    public List<BasicStudyInfos> getStudiesCreationRequests() {
        return studyCreationRequestRepository.findAll().stream()
                .map(StudyService::toBasicStudyInfos)
                .collect(Collectors.toList());
    }

    public BasicStudyInfos createStudy(UUID caseUuid, String userId, UUID studyUuid, Map<String, Object> importParameters, boolean duplicateCase, String caseFormat) {
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
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
    public void deleteRootNetwork(UUID studyUuid, UUID rootNetworkUuid, String userId) {
        assertIsStudyExist(studyUuid);
        rootNetworkService.assertIsRootNetworkInStudy(rootNetworkUuid, studyUuid);

        rootNetworkService.delete(rootNetworkUuid);
    }

    @Transactional
    public RootNetworkCreationRequestInfos createRootNetwork(UUID studyUuid, UUID caseUuid, String caseFormat, Map<String, Object> importParameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        UUID importReportUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        RootNetworkCreationRequestEntity rootNetworkCreationRequestEntity = rootNetworkService.insertCreationRequest(rootNetworkUuid, studyEntity, userId);
        try {
            networkConversionService.persistNetwork(caseUuid, studyUuid, rootNetworkUuid, null, userId, importReportUuid, caseFormat, importParameters, CaseImportAction.ROOT_NETWORK_CREATION);
        } catch (Exception e) {
            rootNetworkService.deleteCreationRequest(rootNetworkCreationRequestEntity);
            throw new StudyException(ROOT_NETWORK_CREATION_FAILED);
        }

        return rootNetworkCreationRequestEntity.toDto();
    }

    public void updateRootNetworkCase(UUID studyUuid, UUID rootNetworkUuid, UUID caseUuid, String caseFormat, Map<String, Object> importParameters, String userId) {
        UUID importReportUuid = UUID.randomUUID();
        persistNetwork(caseUuid, studyUuid, rootNetworkUuid, null, userId, importReportUuid, caseFormat, importParameters, CaseImportAction.ROOT_NETWORK_MODIFICATION);
    }

    public void updateRootNetworkCase(UUID studyUuid, UUID rootNetworkUuid, NetworkInfos networkInfos, CaseInfos caseInfos,
                                      Map<String, String> importParameters, UUID importReportUuid) {
        // Update case for a given root network
        rootNetworkService.updateRootNetworkCase(rootNetworkUuid, RootNetworkInfos.builder()
            .id(rootNetworkUuid)
            .caseInfos(caseInfos)
            .reportUuid(importReportUuid)
            .networkInfos(networkInfos)
            .importParameters(importParameters)
            .build());
        // Invalidate nodes of the updated root network
        UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        invalidateBuild(studyUuid, rootNodeUuid, rootNetworkUuid, false, false, true);
    }

    @Transactional
    public void createRootNetwork(@NonNull UUID studyUuid, @NonNull RootNetworkInfos rootNetworkInfos) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        Optional<RootNetworkCreationRequestEntity> rootNetworkCreationRequestEntityOpt = rootNetworkService.getCreationRequest(rootNetworkInfos.getId());
        if (rootNetworkCreationRequestEntityOpt.isPresent()) {
            rootNetworkService.createRootNetwork(studyEntity, rootNetworkInfos);
            rootNetworkService.deleteCreationRequest(rootNetworkCreationRequestEntityOpt.get());
            // TODO: send notification to frontend
        } else {
            rootNetworkService.delete(rootNetworkInfos);
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
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, null));

        studyServerExecutionService.runAsync(() -> self.duplicateStudyAsync(basicStudyInfos, sourceStudyUuid, userId));

        return basicStudyInfos.getId();
    }

    @Transactional
    public void duplicateStudyAsync(BasicStudyInfos basicStudyInfos, UUID sourceStudyUuid, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        try {
            startTime.set(System.nanoTime());

            StudyEntity duplicatedStudy = duplicateStudy(basicStudyInfos, sourceStudyUuid, userId);

            reindexStudy(duplicatedStudy, getStudyFirstRootNetworkUuid(duplicatedStudy.getId()));

        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        } finally {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            LOGGER.trace("Create study '{}' from source {} : {} seconds", basicStudyInfos.getId(), sourceStudyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    @Transactional(readOnly = true)
    public CreatedStudyBasicInfos getStudyInfos(UUID studyUuid, UUID rootNetworkUuid) {
        return toStudyInfos(studyUuid, rootNetworkUuid);
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
            List<RootNetworkInfos> rootNetworkInfos = rootNetworkService.getStudyRootNetworkInfosWithRootNetworkNodeInfos(studyUuid);
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
                        rootNetworkService.getDeleteRootNetworkInfosFutures(deleteStudyInfos.getRootNetworkInfosList()).stream(),
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

    public CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, CaseInfos caseInfos, UUID loadFlowParametersUuid,
                                              UUID shortCircuitParametersUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity,
                                              UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                              Map<String, String> importParameters, UUID importReportUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkInfos.getNetworkUuid());
        Objects.requireNonNull(networkInfos.getNetworkId());
        Objects.requireNonNull(caseInfos.getCaseFormat());
        Objects.requireNonNull(caseInfos.getCaseUuid());
        Objects.requireNonNull(importParameters);

        StudyEntity studyEntity = self.saveStudyThenCreateBasicTree(studyUuid, networkInfos,
                caseInfos, loadFlowParametersUuid,
                shortCircuitParametersUuid, dynamicSimulationParametersEntity,
                voltageInitParametersUuid, securityAnalysisParametersUuid, sensitivityAnalysisParametersUuid,
                importParameters, importReportUuid);

        // Need to deal with the study creation (with a default root network ?)
        UUID studyFirstRootNetworkUuid = self.getStudyFirstRootNetworkUuid(studyUuid);
        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity, studyFirstRootNetworkUuid);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    @Transactional
    public CreatedStudyBasicInfos updateNetwork(UUID studyUuid, UUID rootNetworkUuid, String userId, NetworkInfos networkInfos) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        RootNetworkEntity rootNetworkEntity = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));

        rootNetworkService.updateNetwork(rootNetworkEntity, networkInfos);

        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(studyEntity, rootNetworkEntity.getId());
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudyNetworkRecreationDone(studyEntity.getId(), userId);

        return createdStudyBasicInfos;
    }

    private StudyEntity duplicateStudy(BasicStudyInfos studyInfos, UUID sourceStudyUuid, String userId) {
        Objects.requireNonNull(studyInfos.getId());
        Objects.requireNonNull(userId);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        StudyEntity newStudyEntity = duplicateStudyEntity(sourceStudy, studyInfos.getId());
        networkModificationTreeService.duplicateStudyNodes(newStudyEntity, sourceStudyUuid);
        rootNetworkService.duplicateStudyRootNetworks(newStudyEntity, sourceStudyUuid);

        UUID sourceStudyFirstRootNetworkUuid = self.getStudyFirstRootNetworkUuid(newStudyEntity.getId());
        CreatedStudyBasicInfos createdStudyBasicInfos = toCreatedStudyBasicInfos(newStudyEntity, sourceStudyFirstRootNetworkUuid);
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

        DynamicSimulationParametersInfos dynamicSimulationParameters = sourceStudyEntity.getDynamicSimulationParameters() != null ? DynamicSimulationService.fromEntity(sourceStudyEntity.getDynamicSimulationParameters(), objectMapper) : DynamicSimulationService.getDefaultDynamicSimulationParameters();

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
            .nonEvacuatedEnergyParameters(NonEvacuatedEnergyService.toEntity(nonEvacuatedEnergyParametersInfos))
            .build());
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(studyUuid);
        notificationService.emitStudiesChanged(newStudy.getId(), userId);
        return newStudy;
    }

    public byte[] getVoltageLevelSvg(String voltageLevelId, DiagramParameters diagramParameters,
                                     UUID nodeUuid, UUID rootNetworkUuid) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
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

    public String getVoltageLevelEquipments(UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String voltageLevelId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        String equipmentPath = "voltage-levels" + StudyConstants.DELIMITER + voltageLevelId + StudyConstants.DELIMITER + "equipments";
        return networkMapService.getEquipmentsMapData(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                substationsIds, equipmentPath);
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
    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId, Float limitReduction) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        if (prevResultUuid != null) {
            loadflowService.deleteLoadFlowResult(prevResultUuid);
        }

        UUID lfParametersUuid = loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        UUID lfReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(LOAD_FLOW.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW, lfReportUuid);
        UUID result = loadflowService.runLoadFlow(nodeUuid, rootNetworkUuid, networkUuid, variantId, lfParametersUuid, lfReportUuid, userId, limitReduction);

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, LOAD_FLOW);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        return result;
    }

    public ExportNetworkInfos exportNetwork(UUID nodeUuid, UUID rootNetworkUuid, String format, String paramatersJson, String fileName) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return networkConversionService.exportNetwork(networkUuid, variantId, format, paramatersJson, fileName);
    }

    public void assertIsNodeNotReadOnly(UUID nodeUuid) {
        Boolean isReadOnly = networkModificationTreeService.isReadOnly(nodeUuid).orElse(Boolean.FALSE);
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
    // TODO Need to deal with all root networks
    public void assertCanModifyNode(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        assertIsNodeNotReadOnly(nodeUuid);
        assertNoBuildNoComputation(studyUuid, nodeUuid, rootNetworkUuid);
    }

    public void assertIsStudyAndNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        assertIsNodeExist(studyUuid, nodeUuid);
    }

    public void assertNoBuildNoComputation(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        rootNetworkNodeInfoService.assertComputationNotRunning(nodeUuid, rootNetworkUuid);
        rootNetworkNodeInfoService.assertNoRootNetworkModificationInfoIsBuilding(studyUuid);
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
    public void setSecurityAnalysisParametersValues(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        createOrUpdateSecurityAnalysisParameters(studyUuid, studyEntity, parameters);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SECURITY_ANALYSIS);
    }

    public NonEvacuatedEnergyParametersInfos getNonEvacuatedEnergyParametersInfos(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> studyEntity.getNonEvacuatedEnergyParameters() != null ?
                        NonEvacuatedEnergyService.fromEntity(studyEntity.getNonEvacuatedEnergyParameters()) :
                        NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos())
                .orElse(null);
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
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
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
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
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
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
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
        return studyRepository.findById(studyUuid)
                .map(StudyEntity::getDynamicSimulationProvider)
                .orElse("");
    }

    @Transactional
    public void updateDynamicSimulationProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setDynamicSimulationProvider(provider != null ? provider : defaultDynamicSimulationProvider);
            invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
            notificationService.emitComputationParamsChanged(studyUuid, DYNAMIC_SIMULATION);
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
    public void setShortCircuitParameters(UUID studyUuid, @Nullable String shortCircuitParametersInfos, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        /* +-----------------------+----------------+-----------------------------------------+
         * | entity.parametersUuid | parametersInfo | action                                  |
         * | no                    | no             | create default ones                     |
         * | no                    | yes            | create new ones                         |
         * | yes                   | no             | reset existing ones (with default ones) |
         * | yes                   | yes            | update existing ones                    |
         * +-----------------------+----------------+-----------------------------------------+
         */
        if (studyEntity.getShortCircuitParametersUuid() != null) {
            shortCircuitService.updateParameters(studyEntity.getShortCircuitParametersUuid(), shortCircuitParametersInfos);
        } else {
            studyEntity.setShortCircuitParametersUuid(shortCircuitService.createParameters(shortCircuitParametersInfos));
            studyRepository.save(studyEntity);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SHORT_CIRCUIT);

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
            securityAnalysisService.deleteSaResult(prevResultUuid);
        }

        var runSecurityAnalysisParametersInfos = new RunSecurityAnalysisParametersInfos(study.getSecurityAnalysisParametersUuid(), study.getLoadFlowParametersUuid(), contingencyListNames);
        UUID result = securityAnalysisService.runSecurityAnalysis(networkUuid, variantId, runSecurityAnalysisParametersInfos,
                new ReportInfos(saReportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, SECURITY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
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
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getSubstationSvgAndMetadata(networkUuid, variantId, substationId, diagramParameters, substationLayout);
        } else {
            return null;
        }
    }

    public String getNetworkAreaDiagram(UUID nodeUuid, UUID rootNetworkUuid, List<String> voltageLevelsIds, int depth, boolean withGeoData) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getNetworkAreaDiagram(networkUuid, variantId, voltageLevelsIds, depth, withGeoData);
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

    public void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        loadflowService.invalidateLoadFlowStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, LOAD_FLOW));
    }

    public void invalidateVoltageInitStatusOnAllNodes(UUID studyUuid) {
        voltageInitService.invalidateVoltageInitStatus(rootNetworkNodeInfoService.getComputationResultUuids(studyUuid, VOLTAGE_INITIALIZATION));
    }

    private StudyEntity updateStudyIndexationStatus(StudyEntity studyEntity, StudyIndexationStatus indexationStatus) {
        studyEntity.setIndexationStatus(indexationStatus);
        notificationService.emitStudyIndexationStatusChanged(studyEntity.getId(), indexationStatus);
        return studyEntity;
    }

    public StudyEntity updateStudyIndexationStatus(UUID studyUuid, StudyIndexationStatus indexationStatus) {
        return updateStudyIndexationStatus(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)), indexationStatus);
    }

    @Transactional
    public StudyEntity saveStudyThenCreateBasicTree(UUID studyUuid, NetworkInfos networkInfos,
                                                    CaseInfos caseInfos, UUID loadFlowParametersUuid,
                                                    UUID shortCircuitParametersUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity,
                                                    UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                                    Map<String, String> importParameters, UUID importReportUuid) {

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
                .indexationStatus(StudyIndexationStatus.INDEXED)
                .voltageInitParameters(new StudyVoltageInitParametersEntity())
                .build();

        var study = studyRepository.save(studyEntity);
        rootNetworkService.createRootNetwork(studyEntity, RootNetworkInfos.builder().id(UUID.randomUUID()).networkInfos(networkInfos).caseInfos(caseInfos).reportUuid(importReportUuid).importParameters(importParameters).build());
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

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(UUID studyUuid) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid);
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
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        });
    }

    public void createOrUpdateVoltageInitParameters(StudyEntity studyEntity, VoltageInitParametersInfos parameters) {
        UUID voltageInitParametersUuid = studyEntity.getVoltageInitParametersUuid();
        if (voltageInitParametersUuid == null) {
            voltageInitParametersUuid = voltageInitService.createVoltageInitParameters(parameters);
            studyEntity.setVoltageInitParametersUuid(voltageInitParametersUuid);
        } else {
            VoltageInitParametersInfos oldParameters = voltageInitService.getVoltageInitParameters(voltageInitParametersUuid);
            if (!Objects.isNull(parameters) && parameters.equals(oldParameters)) {
                return;
            }
            voltageInitService.updateVoltageInitParameters(voltageInitParametersUuid, parameters);
        }
        invalidateVoltageInitStatusOnAllNodes(studyEntity.getId());
    }

    public void createOrUpdateSecurityAnalysisParameters(UUID studyUuid, StudyEntity studyEntity, String parameters) {
        UUID securityAnalysisParametersUuid = studyEntity.getSecurityAnalysisParametersUuid();
        if (securityAnalysisParametersUuid == null) {
            securityAnalysisParametersUuid = securityAnalysisService.createSecurityAnalysisParameters(parameters);
            studyEntity.setSecurityAnalysisParametersUuid(securityAnalysisParametersUuid);
        } else {
            securityAnalysisService.updateSecurityAnalysisParameters(securityAnalysisParametersUuid, parameters);
        }
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
    }

    public void createNetworkModification(UUID studyUuid, String createModificationAttributes, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            String variantId = rootNetworkNodeInfoEntity.getVariantId();
            UUID reportUuid = rootNetworkNodeInfoEntity.getModificationReports().get(nodeUuid);

            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.createModification(studyUuid, createModificationAttributes, groupUuid, variantId, reportUuid, nodeUuid, rootNetworkUuid);
            updateNode(studyUuid, nodeUuid, rootNetworkUuid, networkModificationResult);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID rootNetworkUuid, UUID modificationUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            networkModificationService.updateModification(updateModificationAttributes, modificationUuid);
            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, false);
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

    public String getVoltageLevelSubstationId(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelSubstationId(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId);
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelBusesOrBusbarSections(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId, "buses-or-busbar-sections");
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
    public void unbuildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        invalidateBuild(studyUuid, nodeUuid, rootNetworkUuid, false, true, true);
        emitAllComputationStatusChanged(studyUuid, nodeUuid);
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
        updateStatuses(targetStudyUuid, duplicatedNodeUuid, self.getStudyFirstRootNetworkUuid(targetStudyUuid), true, invalidateBuild, true);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void moveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID rootNetworkUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        List<NodeEntity> oldChildren = null;
        checkStudyContainsNode(studyUuid, nodeToMoveUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);
        boolean shouldInvalidateChildren = networkModificationTreeService.hasModifications(nodeToMoveUuid, false);

        //Invalidating previous children if necessary
        if (shouldInvalidateChildren) {
            oldChildren = networkModificationTreeService.getChildrenByParentUuid(nodeToMoveUuid);
        }

        networkModificationTreeService.moveStudyNode(nodeToMoveUuid, referenceNodeUuid, insertMode);

        //Invalidating moved node or new children if necessary
        if (shouldInvalidateChildren) {
            updateStatuses(studyUuid, nodeToMoveUuid, rootNetworkUuid, false, true, true);
            oldChildren.forEach(child -> updateStatuses(studyUuid, child.getIdNode(), rootNetworkUuid, false, true, true));
        } else {
            invalidateBuild(studyUuid, nodeToMoveUuid, rootNetworkUuid, false, true, true);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void duplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid, String userId) {
        checkStudyContainsNode(sourceStudyUuid, parentNodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);

        StudyEntity studyEntity = studyRepository.findById(targetStudyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        AbstractNode studySubTree = networkModificationTreeService.getStudySubtree(sourceStudyUuid, parentNodeToCopyUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.cloneStudyTree(studySubTree, referenceNodeUuid, studyEntity);
        notificationService.emitSubtreeInserted(targetStudyUuid, duplicatedNodeUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void moveStudySubtree(UUID studyUuid, UUID parentNodeToMoveUuid, UUID rootNetworkUuid, UUID referenceNodeUuid, String userId) {
        checkStudyContainsNode(studyUuid, parentNodeToMoveUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);

        List<UUID> allChildren = networkModificationTreeService.getChildren(parentNodeToMoveUuid);
        if (allChildren.contains(referenceNodeUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        networkModificationTreeService.moveStudySubtree(parentNodeToMoveUuid, referenceNodeUuid);

        if (networkModificationTreeService.getNodeBuildStatus(parentNodeToMoveUuid, rootNetworkUuid).isBuilt()) {
            updateStatuses(studyUuid, parentNodeToMoveUuid, rootNetworkUuid, false, true, true);
        }
        allChildren.stream()
                .filter(childUuid -> networkModificationTreeService.getNodeBuildStatus(childUuid, rootNetworkUuid).isBuilt())
                .forEach(childUuid -> updateStatuses(studyUuid, childUuid, rootNetworkUuid, false, true, true));

        notificationService.emitSubtreeMoved(studyUuid, parentNodeToMoveUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    // TODO Need to deal with all root networks
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
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getLoadFlowResultUuids().forEach(loadflowService::deleteLoadFlowResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getNonEvacuatedEnergyResultUuids().forEach(nonEvacuatedEnergyService::deleteNonEvacuatedEnergyResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getOneBusShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getVoltageInitResultUuids().forEach(voltageInitService::deleteVoltageInitResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getDynamicSimulationResultUuids().forEach(dynamicSimulationService::deleteResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getStateEstimationResultUuids().forEach(stateEstimationService::deleteStateEstimationResult)),
                studyServerExecutionService.runAsync(() -> networkStoreService.deleteVariants(invalidateNodeInfos.getNetworkUuid(), invalidateNodeInfos.getVariantIds()))
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

    // TODO Need to deal with all root networks
    private void updateStatuses(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, true);
    }

    // TODO Need to deal with all root networks
    private void updateStatuses(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus) {
        updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus, true, true);
    }

    // TODO Need to deal with all root networks
    private void updateStatuses(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateBuild, boolean deleteVoltageInitResults) {
        if (invalidateBuild) {
            invalidateBuild(studyUuid, nodeUuid, rootNetworkUuid, invalidateOnlyChildrenBuildStatus, false, deleteVoltageInitResults);
        }
        emitAllComputationStatusChanged(studyUuid, nodeUuid);
    }

    @Transactional
    public void deleteNetworkModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.deleteModifications(groupId, modificationsUuids);

            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, false, false, false);
        } finally {
            notificationService.emitEndDeletionEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_STASHING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.stashModifications(groupId, modificationsUuids);
            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModificationsActivation(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<UUID> modificationsUuids, String userId, boolean activated) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.updateModificationsActivation(groupId, modificationsUuids, activated);
            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void restoreNetworkModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_RESTORING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.restoreModifications(groupId, modificationsUuids);
            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void deleteNodes(UUID studyUuid, List<UUID> nodeIds, UUID rootNetworkUuid, boolean deleteChildren, String userId) {

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
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getLoadFlowResultUuids().forEach(loadflowService::deleteLoadFlowResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getNonEvacuatedEnergyResultUuids().forEach(nonEvacuatedEnergyService::deleteNonEvacuatedEnergyResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getOneBusShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getVoltageInitResultUuids().forEach(voltageInitService::deleteVoltageInitResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getDynamicSimulationResultUuids().forEach(dynamicSimulationService::deleteResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getStateEstimationResultUuids().forEach(stateEstimationService::deleteStateEstimationResult)),
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

            if (startTime.get() != null) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId.toString().replaceAll("[\n\r]", "_"), studyUuid,
                            TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                }
            }

            if (invalidateChildrenBuild) {
                childrenNodes.forEach(nodeEntity -> updateStatuses(studyUuid, nodeEntity.getIdNode(), rootNetworkUuid, false, true, true));
            }
        }

        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void stashNode(UUID studyUuid, UUID nodeId, UUID rootNetworkUuid, boolean stashChildren, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        boolean invalidateChildrenBuild = stashChildren || networkModificationTreeService.hasModifications(nodeId, false);
        invalidateBuild(studyUuid, nodeId, rootNetworkUuid, false, !invalidateChildrenBuild, true);
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

    private void reindexStudy(StudyEntity study, UUID rootNetworkUuid) {
        CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study, rootNetworkUuid);
        // reindex study in elasticsearch
        studyInfosService.recreateStudyInfos(studyInfos);

        // Reset indexation status
        updateStudyIndexationStatus(study, StudyIndexationStatus.INDEXING_ONGOING);
        try {
            networkConversionService.reindexStudyNetworkEquipments(rootNetworkService.getNetworkUuid(rootNetworkUuid));
            updateStudyIndexationStatus(study, StudyIndexationStatus.INDEXED);
        } catch (Exception e) {
            // Allow to retry indexation
            updateStudyIndexationStatus(study, StudyIndexationStatus.NOT_INDEXED);
            throw e;
        }
        invalidateBuild(study.getId(), networkModificationTreeService.getStudyRootNodeUuid(study.getId()), rootNetworkUuid, false, false, true);
        LOGGER.info("Study with id = '{}' has been reindexed", study.getId());
    }

    @Transactional
    public void reindexStudy(UUID studyUuid, UUID rootNetworkUuid) {
        reindexStudy(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)), rootNetworkUuid);
    }

    @Transactional
    public StudyIndexationStatus getStudyIndexationStatus(UUID studyUuid, UUID rootNetworkUuid) {
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        if (study.getIndexationStatus() == StudyIndexationStatus.INDEXED
                && !networkConversionService.checkStudyIndexationStatus(rootNetworkService.getNetworkUuid(rootNetworkUuid))) {
            updateStudyIndexationStatus(study, StudyIndexationStatus.NOT_INDEXED);
        }
        return study.getIndexationStatus();
    }

    @Transactional
    public void moveModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, UUID rootNetworkUuid, List<UUID> modificationUuidList, UUID beforeUuid, String userId) {
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
            UUID originGroupUuid = networkModificationTreeService.getModificationGroupUuid(originNodeUuid);
            NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(targetNodeUuid);
            RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(targetNodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
            UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.moveModifications(originGroupUuid, modificationUuidList, beforeUuid, networkUuid, networkModificationNodeInfoEntity, rootNetworkNodeInfoEntity, buildTargetNode);
            if (!targetNodeBelongsToSourceNodeSubTree) {
                // invalidate the whole subtree except maybe the target node itself (depends if we have built this node during the move)
                networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, targetNodeUuid, rootNetworkUuid, modificationResult));
                updateStatuses(studyUuid, targetNodeUuid, rootNetworkUuid, buildTargetNode, true, true);
            }
            if (moveBetweenNodes) {
                // invalidate the whole subtree including the source node
                networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, originNodeUuid, rootNetworkUuid, modificationResult));
                updateStatuses(studyUuid, originNodeUuid, rootNetworkUuid, false, true, true);
            }
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids);
            if (moveBetweenNodes) {
                notificationService.emitEndModificationEquipmentNotification(studyUuid, originNodeUuid, originNodeChildrenUuids);
            }
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void createModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<UUID> modificationUuidList, String userId, StudyConstants.ModificationsActionType action) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);
            NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(nodeUuid);
            RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
            UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.createModifications(modificationUuidList, networkUuid, networkModificationNodeInfoEntity, rootNetworkNodeInfoEntity, action);
            // invalidate the whole subtree except the target node (we have built this node during the duplication)
            networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, nodeUuid, rootNetworkUuid, modificationResult));
            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, true, true, true);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
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
        AbstractNode nodeInfos = networkModificationTreeService.getNode(nodeUuid);

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

    private void updateNode(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Optional<NetworkModificationResult> networkModificationResult) {
        networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, nodeUuid, rootNetworkUuid, modificationResult));
        updateStatuses(studyUuid, nodeUuid, rootNetworkUuid);
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

        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY,
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
            sensitivityAnalysisService.deleteSensitivityAnalysisResult(prevResultUuid);
        }
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID sensiReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(SENSITIVITY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS, sensiReportUuid);

        UUID result = sensitivityAnalysisService.runSensitivityAnalysis(nodeUuid, rootNetworkUuid, networkUuid, variantId, sensiReportUuid, userId, study.getSensitivityAnalysisParametersUuid(), study.getLoadFlowParametersUuid());

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, SENSITIVITY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        return result;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Optional<String> busId, String userId) {
        ComputationType computationType = busId.isEmpty() ? SHORT_CIRCUIT : SHORT_CIRCUIT_ONE_BUS;
        UUID shortCircuitResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, computationType);
        if (shortCircuitResultUuid != null) {
            shortCircuitService.deleteShortCircuitAnalysisResult(shortCircuitResultUuid);
        }
        final Optional<UUID> parametersUuid = studyRepository.findById(studyUuid).map(StudyEntity::getShortCircuitParametersUuid);
        UUID scReportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(computationType.name(), UUID.randomUUID());
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, computationType, scReportUuid);
        final UUID result = shortCircuitService.runShortCircuit(nodeUuid, rootNetworkUuid, networkUuid, variantId, busId.orElse(null), parametersUuid, scReportUuid, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, computationType);
        notificationService.emitStudyChanged(studyUuid, nodeUuid,
                busId.isEmpty() ? NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS : NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        return result;
    }

    public UUID runVoltageInit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION);
        if (prevResultUuid != null) {
            voltageInitService.deleteVoltageInitResult(prevResultUuid);
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(VOLTAGE_INITIALIZATION.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, VOLTAGE_INITIALIZATION, reportUuid);
        UUID result = voltageInitService.runVoltageInit(networkUuid, variantId, studyEntity.getVoltageInitParametersUuid(), reportUuid, nodeUuid, rootNetworkUuid, userId);

        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, VOLTAGE_INITIALIZATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        return result;
    }

    @Transactional
    public void setVoltageInitParameters(UUID studyUuid, StudyVoltageInitParameters parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        var voltageInitParameters = studyEntity.getVoltageInitParameters();
        if (voltageInitParameters == null) {
            var newVoltageInitParameters = new StudyVoltageInitParametersEntity(parameters.isApplyModifications());
            studyEntity.setVoltageInitParameters(newVoltageInitParameters);
        } else {
            voltageInitParameters.setApplyModifications(parameters.isApplyModifications());
        }
        createOrUpdateVoltageInitParameters(studyEntity, parameters.getComputationParameters());
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, VOLTAGE_INITIALIZATION);

    }

    public StudyVoltageInitParameters getVoltageInitParameters(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return new StudyVoltageInitParameters(
                Optional.ofNullable(studyEntity.getVoltageInitParametersUuid()).map(voltageInitService::getVoltageInitParameters).orElse(null),
                Optional.ofNullable(studyEntity.getVoltageInitParameters()).map(StudyVoltageInitParametersEntity::shouldApplyModifications).orElse(true)
        );
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

    public List<ModelInfos> getDynamicSimulationModels(UUID studyUuid, UUID nodeUuid) {
        // load configured parameters persisted in the study server DB
        DynamicSimulationParametersInfos configuredParameters = getDynamicSimulationParameters(studyUuid);
        String mapping = configuredParameters.getMapping();

        // get model from mapping
        return dynamicSimulationService.getModels(mapping);
    }

    @Transactional
    public void setDynamicSimulationParameters(UUID studyUuid, DynamicSimulationParametersInfos dsParameter, String userId) {
        updateDynamicSimulationParameters(studyUuid, DynamicSimulationService.toEntity(dsParameter != null ? dsParameter : DynamicSimulationService.getDefaultDynamicSimulationParameters(), objectMapper));
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, DYNAMIC_SIMULATION);

    }

    public DynamicSimulationParametersInfos getDynamicSimulationParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> studyEntity.getDynamicSimulationParameters() != null ? DynamicSimulationService.fromEntity(studyEntity.getDynamicSimulationParameters(), objectMapper) : DynamicSimulationService.getDefaultDynamicSimulationParameters())
                .orElse(null);
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
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
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

        // pre-condition check
        String lfStatus = rootNetworkNodeInfoService.getLoadFlowStatus(nodeUuid, rootNetworkUuid);
        if (!LoadFlowStatus.CONVERGED.name().equals(lfStatus)) {
            throw new StudyException(NOT_ALLOWED, "Load flow must run successfully before running dynamic simulation");
        }

        // clean previous result if exist
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, DYNAMIC_SIMULATION);
        if (prevResultUuid != null) {
            dynamicSimulationService.deleteResult(prevResultUuid);
        }

        // load configured parameters persisted in the study server DB
        DynamicSimulationParametersInfos configuredParameters = getDynamicSimulationParameters(studyUuid);

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
        UUID dynamicSResultUuid = dynamicSimulationService.runDynamicSimulation(getDynamicSimulationProvider(studyUuid), nodeUuid, rootNetworkUuid, networkUuid, variantId, reportUuid, mergeParameters, userId);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, dynamicSResultUuid, DYNAMIC_SIMULATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);

        return dynamicSResultUuid;
    }

    // --- Dynamic Simulation service methods END --- //

    public String getNetworkElementsIds(UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String equipmentType, List<Double> nominalVoltages) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getElementsIds(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                substationsIds, equipmentType, nominalVoltages);
    }

    @Transactional(readOnly = true)
    public String getVoltageInitModifications(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        UUID voltageInitModificationsGroupUuid = voltageInitService.getModificationsGroupUuid(nodeUuid, resultUuid);
        return networkModificationService.getModifications(voltageInitModificationsGroupUuid, false, false);
    }

    @Transactional
    public void copyVoltageInitModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
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
            NetworkModificationNodeInfoEntity networkModificationNodeInfoEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(nodeUuid);
            RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid).orElseThrow(() -> new StudyException(ROOT_NETWORK_NOT_FOUND));
            UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.duplicateModificationsInGroup(voltageInitModificationsGroupUuid, networkUuid, networkModificationNodeInfoEntity, rootNetworkNodeInfoEntity);

            voltageInitService.resetModificationsGroupUuid(nodeUuid, resultUuid);

            // invalidate the whole subtree except the target node (we have built this node during the duplication)
            networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, nodeUuid, rootNetworkUuid, modificationResult));
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT); // send notification voltage init result has changed
            updateStatuses(studyUuid, nodeUuid, rootNetworkUuid, true, true, false);  // do not delete the voltage init results
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
    public void setSensitivityAnalysisParameters(UUID studyUuid, String parameters, String userId) {
        createOrUpdateSensitivityAnalysisParameters(studyUuid, parameters);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, SENSITIVITY_ANALYSIS);
    }

    @Transactional
    public void setNonEvacuatedEnergyParametersInfos(UUID studyUuid, NonEvacuatedEnergyParametersInfos parameters, String userId) {
        updateNonEvacuatedEnergyParameters(studyUuid,
                NonEvacuatedEnergyService.toEntity(parameters != null ? parameters :
                        NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos()));
        notificationService.emitElementUpdated(studyUuid, userId);
        notificationService.emitComputationParamsChanged(studyUuid, NON_EVACUATED_ENERGY_ANALYSIS);

    }

    public void createOrUpdateSensitivityAnalysisParameters(UUID studyUuid, String parameters) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID sensitivityAnalysisParametersUuid = studyEntity.getSensitivityAnalysisParametersUuid();
        if (sensitivityAnalysisParametersUuid == null) {
            sensitivityAnalysisParametersUuid = sensitivityAnalysisService.createSensitivityAnalysisParameters(parameters);
            studyEntity.setSensitivityAnalysisParametersUuid(sensitivityAnalysisParametersUuid);
        } else {
            sensitivityAnalysisService.updateSensitivityAnalysisParameters(sensitivityAnalysisParametersUuid, parameters);
        }
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
    }

    public void updateNonEvacuatedEnergyParameters(UUID studyUuid, NonEvacuatedEnergyParametersEntity nonEvacuatedEnergyParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setNonEvacuatedEnergyParameters(nonEvacuatedEnergyParametersEntity));
    }

    @Transactional
    public void invalidateLoadFlowStatus(UUID studyUuid, String userId) {
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
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
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
    }

    public UUID runNonEvacuatedEnergy(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID prevResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, NON_EVACUATED_ENERGY_ANALYSIS);

        if (prevResultUuid != null) {
            nonEvacuatedEnergyService.deleteNonEvacuatedEnergyResult(prevResultUuid);
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String provider = getNonEvacuatedEnergyProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID reportUuid = networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).getOrDefault(NON_EVACUATED_ENERGY_ANALYSIS.name(), UUID.randomUUID());
        networkModificationTreeService.updateComputationReportUuid(nodeUuid, rootNetworkUuid, NON_EVACUATED_ENERGY_ANALYSIS, reportUuid);

        NonEvacuatedEnergyParametersInfos nonEvacuatedEnergyParametersInfos = getNonEvacuatedEnergyParametersInfos(studyUuid);
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
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        return result;
    }

    @Transactional
    public void updateNonEvacuatedEnergyProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setNonEvacuatedEnergyProvider(provider != null ? provider : defaultNonEvacuatedEnergyProvider);
            invalidateNonEvacuatedEnergyAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
            notificationService.emitComputationParamsChanged(studyUuid, NON_EVACUATED_ENERGY_ANALYSIS);
        });
    }

    public String getNonEvacuatedEnergyProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(StudyEntity::getNonEvacuatedEnergyProvider)
                .orElse("");
    }

    private void emitAllComputationStatusChanged(UUID studyUuid, UUID nodeUuid) {
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
    }

    public String evaluateFilter(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String filter) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return filterService.evaluateFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid), filter);
    }

    public String exportFilter(UUID rootNetworkUuid, UUID filterUuid) {
        // will use root node network of the study
        //TODO: buggy, what networkUuid to choose ?
        return filterService.exportFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), filterUuid);
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
            stateEstimationService.deleteStateEstimationResult(prevResultUuid);
        }

        UUID result = stateEstimationService.runStateEstimation(networkUuid, variantId, new ReportInfos(reportUuid, nodeUuid), receiver, userId);
        updateComputationResultUuid(nodeUuid, rootNetworkUuid, result, STATE_ESTIMATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        return result;
    }

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

    //TODO: temporary method, once frontend had been implemented, each operation will need to target a specific rootNetwork UUID, here we manually target the first one
    @Transactional
    public UUID getStudyFirstRootNetworkUuid(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)).getFirstRootNetwork().getId();
    }
}
