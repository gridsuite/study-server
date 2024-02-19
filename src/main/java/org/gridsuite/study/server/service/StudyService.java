/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.model.VariantInfos;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import lombok.NonNull;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact.SimpleImpactType;
import org.gridsuite.study.server.dto.nonevacuatedenergy.*;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationEventService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
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
import static org.gridsuite.study.server.service.NetworkModificationTreeService.ROOT_NODE_NAME;
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

    private final String defaultLoadflowProvider;

    private final String defaultSecurityAnalysisProvider;

    private final String defaultSensitivityAnalysisProvider;

    private final String defaultNonEvacuatedEnergyProvider;

    private final String defaultDynamicSimulationProvider;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
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

    private final ObjectMapper objectMapper;

    public enum ComputationUsingLoadFlow {
        LOAD_FLOW, SECURITY_ANALYSIS, SENSITIVITY_ANALYSIS
    }

    public enum ReportNameMatchingType {
        EXACT_MATCHING, ENDS_WITH
    }

    public enum ReportType {
        NETWORK_MODIFICATION("NetworkModification"),
        LOADFLOW("LoadFlow"),
        SECURITY_ANALYSIS("SecurityAnalysis"),
        ALL_BUSES_SHORTCIRCUIT_ANALYSIS("AllBusesShortCircuitAnalysis"),
        ONE_BUS_SHORTCIRCUIT_ANALYSIS("OneBusShortCircuitAnalysis"),
        SENSITIVITY_ANALYSIS("SensitivityAnalysis"),
        NON_EVACUATED_ENERGY_ANALYSIS("NonEvacuatedEnergyAnalysis"),
        VOLTAGE_INIT("VoltageInit");

        public final String reportKey;

        ReportType(String reportKey) {
            this.reportKey = reportKey;
        }
    }

    @Autowired
    StudyService self;

    @Autowired
    public StudyService(
            @Value("${loadflow.default-provider}") String defaultLoadflowProvider,
            @Value("${security-analysis.default-provider}") String defaultSecurityAnalysisProvider,
            @Value("${sensitivity-analysis.default-provider}") String defaultSensitivityAnalysisProvider,
            @Value("${non-evacuated-energy.default-provider}") String defaultNonEvacuatedEnergyProvider,
            @Value("${dynamic-simulation.default-provider}") String defaultDynamicSimulationProvider,
            StudyRepository studyRepository,
            StudyCreationRequestRepository studyCreationRequestRepository,
            NetworkService networkStoreService,
            NetworkModificationService networkModificationService,
            ReportService reportService,
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
            FilterService filterService) {
        this.defaultLoadflowProvider = defaultLoadflowProvider;
        this.defaultSecurityAnalysisProvider = defaultSecurityAnalysisProvider;
        this.defaultSensitivityAnalysisProvider = defaultSensitivityAnalysisProvider;
        this.defaultNonEvacuatedEnergyProvider = defaultNonEvacuatedEnergyProvider;
        this.defaultDynamicSimulationProvider = defaultDynamicSimulationProvider;
        this.studyRepository = studyRepository;
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.networkStoreService = networkStoreService;
        this.networkModificationService = networkModificationService;
        this.reportService = reportService;
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
    }

    private static StudyInfos toStudyInfos(StudyEntity entity) {
        return StudyInfos.builder()
                .id(entity.getId())
                .caseFormat(entity.getCaseFormat())
                .build();
    }

    private static BasicStudyInfos toBasicStudyInfos(StudyCreationRequestEntity entity) {
        return BasicStudyInfos.builder()
                .id(entity.getId())
                .build();
    }

    private static CreatedStudyBasicInfos toCreatedStudyBasicInfos(StudyEntity entity) {
        return CreatedStudyBasicInfos.builder()
                .id(entity.getId())
                .caseFormat(entity.getCaseFormat())
                .build();
    }

    public List<CreatedStudyBasicInfos> getStudies() {
        return studyRepository.findAll().stream()
                .map(StudyService::toCreatedStudyBasicInfos)
                .collect(Collectors.toList());
    }

    public String getStudyCaseName(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return study != null ? study.getCaseName() : "";
    }

    public List<CreatedStudyBasicInfos> getStudiesMetadata(List<UUID> uuids) {
        return studyRepository.findAllById(uuids).stream().map(StudyService::toCreatedStudyBasicInfos)
                .collect(Collectors.toList());

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
            persistentStoreWithNotificationOnError(caseUuidToUse, basicStudyInfos.getId(), userId, importReportUuid, caseFormat, importParameters);
        } catch (Exception e) {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            throw e;
        }

        return basicStudyInfos;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getStudyImportParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)).getImportParameters();
    }

    /**
     * Recreates study network from <caseUuid> and <importParameters>
     * @param caseUuid
     * @param userId
     * @param studyUuid
     * @param importParameters
     */
    public void recreateStudyRootNetwork(UUID caseUuid, String userId, UUID studyUuid, String caseFormat, Map<String, Object> importParameters) {
        recreateStudyRootNetwork(caseUuid, userId, studyUuid, caseFormat, importParameters, false);
    }

    /**
     * Recreates study network from existing case and import parameters
     * @param userId
     * @param studyUuid
     */
    public void recreateStudyRootNetwork(String userId, UUID studyUuid, String caseFormat) {
        UUID caseUuid = self.getStudyCaseUuid(studyUuid);
        recreateStudyRootNetwork(caseUuid, userId, studyUuid, caseFormat, null, true);
    }

    private void recreateStudyRootNetwork(UUID caseUuid, String userId, UUID studyUuid, String caseFormat, Map<String, Object> importParameters, boolean shouldLoadPreviousImportParameters) {
        caseService.assertCaseExists(caseUuid);
        UUID importReportUuid = UUID.randomUUID();
        Map<String, Object> importParametersToUse = shouldLoadPreviousImportParameters
            ? new HashMap<>(self.getStudyImportParameters(studyUuid))
            : importParameters;

        persistentStoreWithNotificationOnError(caseUuid, studyUuid, userId, importReportUuid, caseFormat, importParametersToUse);
    }

    public BasicStudyInfos duplicateStudy(UUID sourceStudyUuid, UUID studyUuid, String userId) {
        Objects.requireNonNull(sourceStudyUuid);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElse(null);
        if (sourceStudy == null) {
            return null;
        }
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));

        studyServerExecutionService.runAsync(() -> self.duplicateStudyAsync(basicStudyInfos, sourceStudyUuid, userId));

        return basicStudyInfos;
    }

    @Transactional
    public void duplicateStudyAsync(BasicStudyInfos basicStudyInfos, UUID sourceStudyUuid, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        try {
            startTime.set(System.nanoTime());

            StudyEntity duplicatedStudy = insertDuplicatedStudy(basicStudyInfos, sourceStudyUuid, userId);

            reindexStudy(duplicatedStudy);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        } finally {
            deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            LOGGER.trace("Create study '{}' from source {} : {} seconds", basicStudyInfos.getId(), sourceStudyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    @Transactional(readOnly = true)
    public StudyInfos getStudyInfos(UUID studyUuid) {
        return StudyService.toStudyInfos(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public UUID getStudyCaseUuid(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)).getCaseUuid();
    }

    public List<CreatedStudyBasicInfos> searchStudies(@NonNull String query) {
        return studyInfosService.search(query);
    }

    private UUID getNodeUuidToSearchIn(UUID initialNodeUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = initialNodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuiltUuid(initialNodeUuid);
        }
        return nodeUuidToSearchIn;
    }

    public List<EquipmentInfos> searchEquipments(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull String userInput,
                                                 @NonNull EquipmentInfosService.FieldSelector fieldSelector, String equipmentType,
                                                 boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn);
        return equipmentInfosService.searchEquipments(networkUuid, variantId, userInput, fieldSelector, equipmentType);
    }

    @Transactional
    public Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        DeleteStudyInfos deleteStudyInfos = null;
        if (studyCreationRequestEntity.isEmpty()) {
            AtomicReference<UUID> caseUuid = new AtomicReference<>(null);
            UUID networkUuid = networkStoreService.doGetNetworkUuid(studyUuid);
            List<NodeModificationInfos> nodesModificationInfos;
            nodesModificationInfos = networkModificationTreeService.getAllNodesModificationInfos(studyUuid);
            studyEntity.ifPresent(s -> {
                caseUuid.set(studyEntity.get().getCaseUuid());
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
            deleteStudyInfos = new DeleteStudyInfos(networkUuid, caseUuid.get(), nodesModificationInfos);
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
            Optional<DeleteStudyInfos> deleteStudyInfosOpt = doDeleteStudyIfNotCreationInProgress(studyUuid,
                    userId);
            if (deleteStudyInfosOpt.isPresent()) {
                DeleteStudyInfos deleteStudyInfos = deleteStudyInfosOpt.get();
                startTime.set(System.nanoTime());

                CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getLoadFlowUuid).filter(Objects::nonNull).forEach(loadflowService::deleteLoadFlowResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getSecurityAnalysisUuid).filter(Objects::nonNull).forEach(securityAnalysisService::deleteSaResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getSensitivityAnalysisUuid).filter(Objects::nonNull).forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getNonEvacuatedEnergyUuid).filter(Objects::nonNull).forEach(nonEvacuatedEnergyService::deleteNonEvacuatedEnergyResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getShortCircuitAnalysisUuid).filter(Objects::nonNull).forEach(shortCircuitService::deleteShortCircuitAnalysisResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getOneBusShortCircuitAnalysisUuid).filter(Objects::nonNull).forEach(shortCircuitService::deleteShortCircuitAnalysisResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getVoltageInitUuid).filter(Objects::nonNull).forEach(voltageInitService::deleteVoltageInitResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getDynamicSimulationUuid).filter(Objects::nonNull).forEach(dynamicSimulationService::deleteResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream().map(NodeModificationInfos::getModificationGroupUuid).filter(Objects::nonNull).forEach(networkModificationService::deleteModifications)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream().map(NodeModificationInfos::getReportUuid).filter(Objects::nonNull).forEach(reportService::deleteReport)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteEquipmentIndexes(deleteStudyInfos.getNetworkUuid())),
                        studyServerExecutionService.runAsync(() -> networkStoreService.deleteNetwork(deleteStudyInfos.getNetworkUuid())),
                        studyServerExecutionService.runAsync(deleteStudyInfos.getCaseUuid() != null ? () -> caseService.deleteCase(deleteStudyInfos.getCaseUuid()) : () -> {
                        })
                );

                executeInParallel.get();
                if (startTime.get() != null) {
                    LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.error(e.toString(), e);
            throw new StudyException(DELETE_STUDY_FAILED, e.getMessage());
        }
    }

    public void deleteEquipmentIndexes(UUID networkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        equipmentInfosService.deleteAllByNetworkUuid(networkUuid);
        LOGGER.trace("Indexes deletion for network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
    }

    public CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, String caseFormat,
                                              UUID caseUuid, String caseName, UUID loadFlowParametersUuid,
                                              ShortCircuitParametersEntity shortCircuitParametersEntity, DynamicSimulationParametersEntity dynamicSimulationParametersEntity,
                                              UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                              Map<String, String> importParameters, UUID importReportUuid) {
        StudyEntity studyEntity = insertStudyEntity(studyUuid, userId, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), caseFormat, caseUuid, caseName, loadFlowParametersUuid, importReportUuid, shortCircuitParametersEntity, dynamicSimulationParametersEntity, voltageInitParametersUuid, securityAnalysisParametersUuid, sensitivityAnalysisParametersUuid, importParameters);
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    public CreatedStudyBasicInfos updateStudyNetwork(StudyEntity studyEntity, String userId, NetworkInfos networkInfos) {
        self.updateStudyEntityNetwork(studyEntity, networkInfos);

        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(studyEntity);
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudyNetworkRecreationDone(studyEntity.getId(), userId);

        return createdStudyBasicInfos;
    }

    private StudyEntity insertDuplicatedStudy(BasicStudyInfos studyInfos, UUID sourceStudyUuid, String userId) {
        Objects.requireNonNull(studyInfos.getId());
        Objects.requireNonNull(userId);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));

        List<VariantInfos> networkVariants = networkStoreService.getNetworkVariants(sourceStudy.getNetworkUuid());
        List<String> targetVariantIds = networkVariants.stream().map(VariantInfos::getId).limit(2).collect(Collectors.toList());
        Network clonedNetwork = networkStoreService.cloneNetwork(sourceStudy.getNetworkUuid(), targetVariantIds);
        UUID clonedNetworkUuid = networkStoreService.getNetworkUuid(clonedNetwork);

        UUID clonedCaseUuid = caseService.duplicateCase(sourceStudy.getCaseUuid(), false);

        Map<String, String> newImportParameters = Map.copyOf(sourceStudy.getImportParameters());

        UUID copiedLoadFlowParametersUuid = null;
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            copiedLoadFlowParametersUuid = loadflowService.duplicateLoadFlowParameters(sourceStudy.getLoadFlowParametersUuid());
        }

        UUID securityAnalysisParametersUuid = sourceStudy.getSecurityAnalysisParametersUuid();
        UUID copiedSecurityAnalysisParametersUuid = null;
        if (securityAnalysisParametersUuid != null) {
            copiedSecurityAnalysisParametersUuid = securityAnalysisService.duplicateSecurityAnalysisParameters(securityAnalysisParametersUuid);
        }

        UUID sensitivityAnalysisParametersUuid = sourceStudy.getSensitivityAnalysisParametersUuid();
        UUID copiedSensitivityAnalysisParametersUuid = null;
        if (sensitivityAnalysisParametersUuid != null) {
            copiedSensitivityAnalysisParametersUuid = sensitivityAnalysisService.duplicateSensitivityAnalysisParameters(sensitivityAnalysisParametersUuid);
        }

        NonEvacuatedEnergyParametersInfos nonEvacuatedEnergyParametersInfos = sourceStudy.getNonEvacuatedEnergyParameters() == null ?
            NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos() :
            NonEvacuatedEnergyService.fromEntity(sourceStudy.getNonEvacuatedEnergyParameters());

        UUID copiedVoltageInitParametersUuid = null;
        if (sourceStudy.getVoltageInitParametersUuid() != null) {
            copiedVoltageInitParametersUuid = voltageInitService.duplicateVoltageInitParameters(sourceStudy.getVoltageInitParametersUuid());
        }

        ShortCircuitParameters shortCircuitParameters = ShortCircuitService.fromEntity(sourceStudy.getShortCircuitParameters());
        ShortCircuitPredefinedConfiguration shortCircuitPredefinedConfiguration = sourceStudy.getShortCircuitParameters().getPredefinedParameters();
        DynamicSimulationParametersInfos dynamicSimulationParameters = sourceStudy.getDynamicSimulationParameters() != null ? DynamicSimulationService.fromEntity(sourceStudy.getDynamicSimulationParameters(), objectMapper) : DynamicSimulationService.getDefaultDynamicSimulationParameters();

        StudyEntity studyEntity = StudyEntity.builder()
                .id(studyInfos.getId())
                .networkUuid(clonedNetworkUuid).networkId(sourceStudy.getNetworkId())
                .caseFormat(sourceStudy.getCaseFormat()).caseUuid(clonedCaseUuid).caseName(sourceStudy.getCaseName())
                .loadFlowProvider(sourceStudy.getLoadFlowProvider())
                .loadFlowParametersUuid(copiedLoadFlowParametersUuid)
                .securityAnalysisProvider(sourceStudy.getSecurityAnalysisProvider())
                .securityAnalysisParametersUuid(copiedSecurityAnalysisParametersUuid)
                .sensitivityAnalysisProvider(sourceStudy.getSensitivityAnalysisProvider())
                .nonEvacuatedEnergyProvider(sourceStudy.getNonEvacuatedEnergyProvider())
                .dynamicSimulationProvider(sourceStudy.getDynamicSimulationProvider())
                .dynamicSimulationParameters(DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper))
                .shortCircuitParameters(ShortCircuitService.toEntity(shortCircuitParameters, shortCircuitPredefinedConfiguration))
                .voltageInitParametersUuid(copiedVoltageInitParametersUuid)
                .sensitivityAnalysisParametersUuid(copiedSensitivityAnalysisParametersUuid)
                .nonEvacuatedEnergyParameters(NonEvacuatedEnergyService.toEntity(nonEvacuatedEnergyParametersInfos))
                .importParameters(newImportParameters)
                .build();
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertDuplicatedStudy(studyEntity, sourceStudy.getId(), UUID.randomUUID()));

        studyInfosService.add(createdStudyBasicInfos);
        notificationService.emitStudiesChanged(studyInfos.getId(), userId);

        return studyEntity;
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(studyUuid);
        notificationService.emitStudiesChanged(newStudy.getId(), userId);
        return newStudy;
    }

    public byte[] getVoltageLevelSvg(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
                                     UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getVoltageLevelSvg(networkUuid, variantId, voltageLevelId, diagramParameters);
        } else {
            return null;
        }
    }

    public String getVoltageLevelSvgAndMetadata(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
                                                UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getVoltageLevelSvgAndMetadata(networkUuid, variantId, voltageLevelId, diagramParameters);
        } else {
            return null;
        }
    }

    private void persistentStoreWithNotificationOnError(UUID caseUuid, UUID studyUuid, String userId, UUID importReportUuid, String caseFormat, Map<String, Object> importParameters) {
        try {
            networkConversionService.persistentStore(caseUuid, studyUuid, userId, importReportUuid, caseFormat, importParameters);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, STUDY_CREATION_FAILED);
        }
    }

    public String getLinesGraphics(UUID networkUuid, UUID nodeUuid, List<String> linesIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return geoDataService.getLinesGraphics(networkUuid, variantId, linesIds);
    }

    public String getSubstationsGraphics(UUID networkUuid, UUID nodeUuid, List<String> substationsIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return geoDataService.getSubstationsGraphics(networkUuid, variantId, substationsIds);
    }

    public String getSubstationMapData(UUID studyUuid, UUID nodeUuid, String substationId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "substations", substationId);
    }

    public String getNetworkElementsInfos(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, String elementType, String infoType, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);
        return networkMapService.getElementsInfos(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, elementType, infoType, loadFlowParameters.getDcPowerFactor());
    }

    public String getNetworkElementInfos(UUID studyUuid, UUID nodeUuid, String elementType, String infoType, String elementId, String operation, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyEntity);
        return networkMapService.getElementInfos(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                elementType, infoType, operation, loadFlowParameters.getDcPowerFactor(), elementId);
    }

    public String getNetworkCountries(UUID studyUuid, UUID nodeUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getCountries(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn));
    }

    public String getVoltageLevelEquipments(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String voltageLevelId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        String equipmentPath = "voltage-level-equipments" + (voltageLevelId == null ? "" : StudyConstants.DELIMITER + voltageLevelId);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, equipmentPath);
    }

    public String getHvdcLineShuntCompensators(UUID studyUuid, UUID nodeUuid, boolean inUpstreamBuiltParentNode, String hvdcId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn);
        return networkMapService.getHvdcLineShuntCompensators(networkUuid, variantId, hvdcId);
    }

    public String getBranchOrThreeWindingsTransformer(UUID studyUuid, UUID nodeUuid, String equipmentId) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        return networkMapService.getEquipmentMapData(networkUuid, variantId, "branch-or-3wt", equipmentId);
    }

    public String getAllMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "all");
    }

    @Transactional
    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, String userId, Float limitReduction) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, LOAD_FLOW);
        prevResultUuidOpt.ifPresent(loadflowService::deleteLoadFlowResult);

        UUID lfParametersUuid = loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        UUID result = loadflowService.runLoadFlow(studyUuid, nodeUuid, lfParametersUuid, studyEntity.getLoadFlowProvider(), userId, limitReduction);

        updateComputationResultUuid(nodeUuid, result, LOAD_FLOW);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        return result;
    }

    public ExportNetworkInfos exportNetwork(UUID studyUuid, UUID nodeUuid, String format, String paramatersJson) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return networkConversionService.exportNetwork(networkUuid, variantId, format, paramatersJson);
    }

    private void assertComputationNotRunning(UUID nodeUuid) {
        loadflowService.assertLoadFlowNotRunning(nodeUuid);
        securityAnalysisService.assertSecurityAnalysisNotRunning(nodeUuid);
        dynamicSimulationService.assertDynamicSimulationNotRunning(nodeUuid);
        sensitivityAnalysisService.assertSensitivityAnalysisNotRunning(nodeUuid);
        nonEvacuatedEnergyService.assertNonEvacuatedEnergyNotRunning(nodeUuid);
        shortCircuitService.assertShortCircuitAnalysisNotRunning(nodeUuid);
        voltageInitService.assertVoltageInitNotRunning(nodeUuid);
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
        boolean exists = getStudies().stream()
                .anyMatch(study -> studyUuid.equals(study.getId()));
        if (!exists) {
            throw new StudyException(NODE_NOT_FOUND);
        }
    }

    public void assertCanModifyNode(UUID studyUuid, UUID nodeUuid) {
        assertIsNodeNotReadOnly(nodeUuid);
        assertNoBuildNoComputation(studyUuid, nodeUuid);
    }

    public void assertIsStudyAndNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        assertIsNodeExist(studyUuid, nodeUuid);
    }

    public void assertNoBuildNoComputation(UUID studyUuid, UUID nodeUuid) {
        assertComputationNotRunning(nodeUuid);
        assertNoNodeIsBuilding(studyUuid);
    }

    private void assertNoNodeIsBuilding(UUID studyUuid) {
        networkModificationTreeService.getAllNodes(studyUuid).stream().forEach(node -> {
            if (networkModificationTreeService.getNodeBuildStatus(node.getIdNode()).isBuilding()) {
                throw new StudyException(NOT_ALLOWED, "No modification is allowed during a node building.");
            }
        });
    }

    public void assertRootNodeOrBuiltNode(UUID studyUuid, UUID nodeUuid) {
        if (!(networkModificationTreeService.getStudyRootNodeUuid(studyUuid).equals(nodeUuid)
                || networkModificationTreeService.getNodeBuildStatus(nodeUuid).isBuilt())) {
            throw new StudyException(NODE_NOT_BUILT);
        }
    }

    public LoadFlowParameters getLoadFlowParameters(StudyEntity studyEntity) {
        LoadFlowParametersInfos lfParameters = getLoadFlowParametersInfos(studyEntity);
        return lfParameters.getCommonParameters();
    }

    public LoadFlowParametersInfos getLoadFlowParametersInfos(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        return getLoadFlowParametersInfos(studyEntity);
    }

    public LoadFlowParametersInfos getLoadFlowParametersInfos(StudyEntity studyEntity) {
        UUID loadFlowParamsUuid = loadflowService.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        return loadflowService.getLoadFlowParameters(loadFlowParamsUuid);
    }

    private LoadFlowParametersValues getLoadFlowParametersValues(StudyEntity studyEntity, ComputationUsingLoadFlow computation) {
        LoadFlowParametersInfos params = getLoadFlowParametersInfos(studyEntity);
        final String lfProvider = switch (computation) {
            case SECURITY_ANALYSIS -> studyEntity.getSecurityAnalysisProvider();
            case SENSITIVITY_ANALYSIS -> studyEntity.getSensitivityAnalysisProvider();
            case LOAD_FLOW -> studyEntity.getLoadFlowProvider();
        };
        return LoadFlowParametersValues.builder()
                .commonParameters(params.getCommonParameters())
                .specificParameters(lfProvider != null ? params.getSpecificParametersPerProvider().getOrDefault(lfProvider, Map.of()) : Map.of())
                .build();
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
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public NonEvacuatedEnergyParametersInfos getNonEvacuatedEnergyParametersInfos(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .map(studyEntity -> studyEntity.getNonEvacuatedEnergyParameters() != null ?
                NonEvacuatedEnergyService.fromEntity(studyEntity.getNonEvacuatedEnergyParameters()) :
                NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos())
            .orElse(null);
    }

    @Transactional
    public void setLoadFlowParameters(UUID studyUuid, String parameters, String userId) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        createOrUpdateLoadFlowParameters(studyEntity, parameters);
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
    }

    public String getDefaultLoadflowProvider() {
        return defaultLoadflowProvider;
    }

    public String getLoadFlowProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(StudyEntity::getLoadFlowProvider)
                .orElse("");
    }

    private void updateProvider(UUID studyUuid, String userId, Consumer<StudyEntity> providerSetter) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        providerSetter.accept(studyEntity);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateLoadFlowProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setLoadFlowProvider(provider != null ? provider : defaultLoadflowProvider);
            invalidateLoadFlowStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        });
    }

    public String getDefaultSecurityAnalysisProvider() {
        return defaultSecurityAnalysisProvider;
    }

    public String getSecurityAnalysisProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(StudyEntity::getSecurityAnalysisProvider)
                .orElse("");
    }

    @Transactional
    public void updateSecurityAnalysisProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setSecurityAnalysisProvider(provider != null ? provider : defaultSecurityAnalysisProvider);
            invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        });
    }

    public String getDefaultSensitivityAnalysisProvider() {
        return defaultSensitivityAnalysisProvider;
    }

    public String getDefaultNonEvacuatedEnergyProvider() {
        return defaultNonEvacuatedEnergyProvider;
    }

    public String getSensitivityAnalysisProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(StudyEntity::getSensitivityAnalysisProvider)
                .orElse("");
    }

    @Transactional
    public void updateSensitivityAnalysisProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setSensitivityAnalysisProvider(provider != null ? provider : defaultSensitivityAnalysisProvider);
            invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        });
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
        });
    }

    public ShortCircuitParameters getShortCircuitParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> ShortCircuitService.fromEntity(studyEntity.getShortCircuitParameters()))
                .orElse(null);
    }

    public ShortCircuitParametersInfos getShortCircuitParametersInfo(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> ShortCircuitService.toShortCircuitParametersInfo(studyEntity.getShortCircuitParameters()))
                .orElse(null);
    }

    @Transactional
    public void setShortCircuitParameters(UUID studyUuid, ShortCircuitParametersInfos shortCircuitParametersInfos, String userId) {
        Objects.requireNonNull(shortCircuitParametersInfos);
        Objects.requireNonNull(shortCircuitParametersInfos.getParameters());
        ShortCircuitParametersEntity shortCircuitParametersEntity = ShortCircuitService.toEntity(shortCircuitParametersInfos.getParameters(), shortCircuitParametersInfos.getPredefinedParameters());
        updateShortCircuitParameters(studyUuid, shortCircuitParametersEntity);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public UUID runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getSecurityAnalysisProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, SECURITY_ANALYSIS);
        prevResultUuidOpt.ifPresent(securityAnalysisService::deleteSaResult);

        LoadFlowParametersValues loadFlowParameters = getLoadFlowParametersValues(study, ComputationUsingLoadFlow.SECURITY_ANALYSIS);
       
        var runSecurityAnalysisParametersInfos = new RunSecurityAnalysisParametersInfos(study.getSecurityAnalysisParametersUuid(), loadFlowParameters.getSpecificParameters(), loadFlowParameters.getCommonParameters(), contingencyListNames);
        UUID result = securityAnalysisService.runSecurityAnalysis(networkUuid, variantId, runSecurityAnalysisParametersInfos,
                new ReportInfos(reportUuid, nodeUuid.toString()), provider, receiver, userId);
        updateComputationResultUuid(nodeUuid, result, SECURITY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        return result;
    }

    public Integer getContingencyCount(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        UUID networkuuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return actionsService.getContingencyCount(networkuuid, variantId, contingencyListNames);
    }

    public List<LimitViolationInfos> getLimitViolations(UUID studyUuid, UUID nodeUuid, String filters, Sort sort) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        return loadflowService.getLimitViolations(nodeUuid, filters, sort);
    }

    public byte[] getSubstationSvg(UUID studyUuid, String substationId, DiagramParameters diagramParameters,
                                   String substationLayout, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getSubstationSvg(networkUuid, variantId, substationId, diagramParameters, substationLayout);
        } else {
            return null;
        }
    }

    public String getSubstationSvgAndMetadata(UUID studyUuid, String substationId, DiagramParameters diagramParameters,
                                              String substationLayout, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getSubstationSvgAndMetadata(networkUuid, variantId, substationId, diagramParameters, substationLayout);
        } else {
            return null;
        }
    }

    public String getNeworkAreaDiagram(UUID studyUuid, UUID nodeUuid, List<String> voltageLevelsIds, int depth) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.getNetworkAreaDiagram(networkUuid, variantId, voltageLevelsIds, depth);
        } else {
            return null;
        }
    }

    public void invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        securityAnalysisService.invalidateSaStatus(networkModificationTreeService.getComputationResultUuids(studyUuid, SECURITY_ANALYSIS));
    }

    public void invalidateSensitivityAnalysisStatusOnAllNodes(UUID studyUuid) {
        sensitivityAnalysisService.invalidateSensitivityAnalysisStatus(networkModificationTreeService.getComputationResultUuids(studyUuid, SENSITIVITY_ANALYSIS));
    }

    public void invalidateNonEvacuatedEnergyAnalysisStatusOnAllNodes(UUID studyUuid) {
        nonEvacuatedEnergyService.invalidateNonEvacuatedEnergyStatus(networkModificationTreeService.getComputationResultUuids(studyUuid, NON_EVACUATED_ENERGY_ANALYSIS));
    }

    public void invalidateDynamicSimulationStatusOnAllNodes(UUID studyUuid) {
        dynamicSimulationService.invalidateStatus(networkModificationTreeService.getComputationResultUuids(studyUuid, DYNAMIC_SIMULATION));
    }

    public void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        loadflowService.invalidateLoadFlowStatus(networkModificationTreeService.getComputationResultUuids(studyUuid, LOAD_FLOW));
    }

    public void invalidateVoltageInitStatusOnAllNodes(UUID studyUuid) {
        voltageInitService.invalidateVoltageInitStatus(networkModificationTreeService.getComputationResultUuids(studyUuid, VOLTAGE_INITIALIZATION));
    }

    private StudyEntity insertStudyEntity(UUID uuid, String userId, UUID networkUuid, String networkId,
                                          String caseFormat, UUID caseUuid, String caseName, UUID loadFlowParametersUuid,
                                          UUID importReportUuid, ShortCircuitParametersEntity shortCircuitParameters, DynamicSimulationParametersEntity dynamicSimulationParameters,
                                          UUID voltageInitParametersUuid, UUID securityAnalysisParametersUuid, UUID sensitivityAnalysisParametersUuid,
                                          Map<String, String> importParameters) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(shortCircuitParameters);
        Objects.requireNonNull(importParameters);

        StudyEntity studyEntity = new StudyEntity(uuid, networkUuid, networkId, caseFormat, caseUuid, caseName, defaultLoadflowProvider,
                defaultSecurityAnalysisProvider, defaultSensitivityAnalysisProvider, defaultNonEvacuatedEnergyProvider, defaultDynamicSimulationProvider,
                loadFlowParametersUuid, null, shortCircuitParameters, dynamicSimulationParameters, voltageInitParametersUuid, null, securityAnalysisParametersUuid,
                null, sensitivityAnalysisParametersUuid, null, importParameters, StudyIndexationStatus.INDEXED);
        return self.saveStudyThenCreateBasicTree(studyEntity, importReportUuid);
    }

    @Transactional
    public StudyEntity updateStudyEntityNetwork(StudyEntity studyEntity, NetworkInfos networkInfos) {
        if (networkInfos != null) {
            studyEntity.setNetworkId(networkInfos.getNetworkId());
            studyEntity.setNetworkUuid(networkInfos.getNetworkUuid());

            studyRepository.save(studyEntity);
        }

        return studyEntity;
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
    public StudyEntity saveStudyThenCreateBasicTree(StudyEntity studyEntity, UUID importReportUuid) {
        var study = studyRepository.save(studyEntity);

        networkModificationTreeService.createBasicTree(study, importReportUuid);
        return study;
    }

    @Transactional
    public StudyEntity insertDuplicatedStudy(StudyEntity studyEntity, UUID sourceStudyUuid, UUID reportUuid) {
        var study = studyRepository.save(studyEntity);

        networkModificationTreeService.createRoot(study, reportUuid);
        AbstractNode rootNode = networkModificationTreeService.getStudyTree(sourceStudyUuid);
        networkModificationTreeService.cloneStudyTree(rootNode, null, studyEntity);
        return study;
    }

    void updateComputationResultUuid(UUID nodeUuid, UUID computationResultUuid, ComputationType computationType) {
        networkModificationTreeService.updateComputationResultUuid(nodeUuid, computationResultUuid, computationType);
    }

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(UUID studyUuid) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid);
        return studyCreationRequestRepository.save(studyCreationRequestEntity);
    }

    public void createOrUpdateLoadFlowParameters(StudyEntity studyEntity, String parameters) {
        UUID loadFlowParametersUuid = studyEntity.getLoadFlowParametersUuid();
        if (loadFlowParametersUuid == null) {
            loadFlowParametersUuid = loadflowService.createLoadFlowParameters(parameters);
            studyEntity.setLoadFlowParametersUuid(loadFlowParametersUuid);
        } else {
            loadflowService.updateLoadFlowParameters(loadFlowParametersUuid, parameters);
        }
    }

    public void updateShortCircuitParameters(UUID studyUuid, ShortCircuitParametersEntity shortCircuitParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setShortCircuitParameters(shortCircuitParametersEntity));
    }

    public void updateDynamicSimulationParameters(UUID studyUuid, DynamicSimulationParametersEntity dynamicSimulationParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> {
            studyEntity1.setDynamicSimulationParameters(dynamicSimulationParametersEntity);
            invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        });
    }

    public void createOrUpdateVoltageInitParameters(UUID studyUuid, String parameters) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID voltageInitParametersUuid = studyEntity.getVoltageInitParametersUuid();
        if (voltageInitParametersUuid == null) {
            voltageInitParametersUuid = voltageInitService.createVoltageInitParameters(parameters);
            studyEntity.setVoltageInitParametersUuid(voltageInitParametersUuid);
        } else {
            voltageInitService.updateVoltageInitParameters(voltageInitParametersUuid, parameters);
        }
        invalidateVoltageInitStatusOnAllNodes(studyUuid);
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

    public void createNetworkModification(UUID studyUuid, String createModificationAttributes, UUID nodeUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.createModification(studyUuid, createModificationAttributes, groupUuid, variantId, reportUuid, nodeInfos.getId().toString());
            updateNode(studyUuid, nodeUuid, networkModificationResult);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            networkModificationService.updateModification(updateModificationAttributes, modificationUuid);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
                                                                        String busPath) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return networkMapService.getVoltageLevelBusesOrBusbarSections(networkUuid, variantId, voltageLevelId, busPath);
    }

    public List<IdentifiableInfos> getVoltageLevelBuses(UUID studyUuid, UUID nodeUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuidToSearchIn, voltageLevelId, "configured-buses");
    }

    public List<IdentifiableInfos> getVoltageLevelBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuidToSearchIn, voltageLevelId, "busbar-sections");
    }

    @Transactional(readOnly = true)
    public UUID getStudyUuidFromNodeUuid(UUID nodeUuid) {
        return networkModificationTreeService.getStudyUuidForNodeId(nodeUuid);
    }

    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(nodeUuid);
        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, NodeBuildStatus.from(BuildStatus.BUILDING));

        try {
            networkModificationService.buildNode(studyUuid, nodeUuid, buildInfos);
        } catch (Exception e) {
            networkModificationTreeService.updateNodeBuildStatus(nodeUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT));
            throw new StudyException(NODE_BUILD_ERROR, e.getMessage());
        }

    }

    public void unbuildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        invalidateBuild(studyUuid, nodeUuid, false, true, true);
        emitAllComputationStatusChanged(studyUuid, nodeUuid);
    }

    public void stopBuild(@NonNull UUID nodeUuid) {
        networkModificationService.stopBuild(nodeUuid);
    }

    @Transactional
    public void duplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        checkStudyContainsNode(sourceStudyUuid, nodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
        boolean invalidateBuild = networkModificationTreeService.hasModifications(nodeToCopyUuid, false);
        updateStatuses(targetStudyUuid, duplicatedNodeUuid, true, invalidateBuild, true);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void moveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
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
            updateStatuses(studyUuid, nodeToMoveUuid, false, true, true);
            oldChildren.forEach(child -> updateStatuses(studyUuid, child.getIdNode(), false, true, true));
        } else {
            invalidateBuild(studyUuid, nodeToMoveUuid, false, true, true);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void duplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid, String userId) {
        checkStudyContainsNode(sourceStudyUuid, parentNodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);

        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudySubtree(parentNodeToCopyUuid, referenceNodeUuid, new HashSet<>());
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

        if (networkModificationTreeService.getNodeBuildStatus(parentNodeToMoveUuid).isBuilt()) {
            updateStatuses(studyUuid, parentNodeToMoveUuid, false, true, true);
        }
        allChildren.stream()
                .filter(childUuid -> networkModificationTreeService.getNodeBuildStatus(childUuid).isBuilt())
                .forEach(childUuid -> updateStatuses(studyUuid, childUuid, false, true, true));

        notificationService.emitSubtreeMoved(studyUuid, parentNodeToMoveUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void invalidateBuild(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateOnlyTargetNode, boolean deleteVoltageInitResults) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        invalidateNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid));
        // we might want to invalidate target node without impacting other nodes (when moving an empty node for example)
        if (invalidateOnlyTargetNode) {
            networkModificationTreeService.invalidateBuildOfNodeOnly(nodeUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos, deleteVoltageInitResults);
        } else {
            networkModificationTreeService.invalidateBuild(nodeUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos, deleteVoltageInitResults);
        }

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getReportUuids().forEach(reportService::deleteReport)),  // TODO delete all with one request only
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getLoadFlowResultUuids().forEach(loadflowService::deleteLoadFlowResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getNonEvacuatedEnergyResultUuids().forEach(nonEvacuatedEnergyService::deleteNonEvacuatedEnergyResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getOneBusShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getVoltageInitResultUuids().forEach(voltageInitService::deleteVoltageInitResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getDynamicSimulationResultUuids().forEach(dynamicSimulationService::deleteResult)),
                studyServerExecutionService.runAsync(() -> networkStoreService.deleteVariants(invalidateNodeInfos.getNetworkUuid(), invalidateNodeInfos.getVariantIds()))
        );
        try {
            executeInParallel.get();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.error(e.toString(), e);
            throw new StudyException(INVALIDATE_BUILD_FAILED, e.getMessage());
        }

        if (startTime.get() != null) {
            LOGGER.trace("Invalidate node '{}' of study '{}' : {} seconds", nodeUuid, studyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid) {
        updateStatuses(studyUuid, nodeUuid, true);
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        updateStatuses(studyUuid, nodeUuid, invalidateOnlyChildrenBuildStatus, true, true);
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateBuild, boolean deleteVoltageInitResults) {
        if (invalidateBuild) {
            invalidateBuild(studyUuid, nodeUuid, invalidateOnlyChildrenBuildStatus, false, deleteVoltageInitResults);
        }
        emitAllComputationStatusChanged(studyUuid, nodeUuid);
    }

    @Transactional
    public void changeModificationActiveState(@NonNull UUID studyUuid, @NonNull UUID nodeUuid,
                                              @NonNull UUID modificationUuid, boolean active, String userId) {
        if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        networkModificationTreeService.handleExcludeModification(nodeUuid, modificationUuid, active);
        updateStatuses(studyUuid, nodeUuid, false);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void deleteNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, boolean onlyStashed, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.deleteModifications(groupId, modificationsUuids, onlyStashed);
            if (modificationsUuids != null) {
                networkModificationTreeService.removeModificationsToExclude(nodeUuid, modificationsUuids);
            }
            updateStatuses(studyUuid, nodeUuid, false, false, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.stashModifications(groupId, modificationsUuids);
            networkModificationTreeService.removeModificationsToExclude(nodeUuid, modificationsUuids);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void restoreNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.restoreModifications(groupId, modificationsUuids);
            networkModificationTreeService.removeModificationsToExclude(nodeUuid, modificationsUuids);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void deleteNodes(UUID studyUuid, List<UUID> nodeIds, boolean deleteChildren, String userId) {

        DeleteNodeInfos deleteNodeInfos = new DeleteNodeInfos();
        deleteNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid));

        for (UUID nodeId : nodeIds) {
            AtomicReference<Long> startTime = new AtomicReference<>(null);
            startTime.set(System.nanoTime());

            boolean invalidateChildrenBuild = !deleteChildren && networkModificationTreeService.hasModifications(nodeId, false);
            List<NodeEntity> childrenNodes = networkModificationTreeService.getChildrenByParentUuid(nodeId);
            List<UUID> removedNodes = networkModificationTreeService.doDeleteNode(studyUuid, nodeId, deleteChildren, deleteNodeInfos);

            CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getModificationGroupUuids().forEach(networkModificationService::deleteModifications)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getReportUuids().forEach(reportService::deleteReport)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getLoadFlowResultUuids().forEach(loadflowService::deleteLoadFlowResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getNonEvacuatedEnergyResultUuids().forEach(nonEvacuatedEnergyService::deleteNonEvacuatedEnergyResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getOneBusShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getVoltageInitResultUuids().forEach(voltageInitService::deleteVoltageInitResult)),
                    studyServerExecutionService.runAsync(() -> deleteNodeInfos.getDynamicSimulationResultUuids().forEach(dynamicSimulationService::deleteResult)),
                    studyServerExecutionService.runAsync(() -> networkStoreService.deleteVariants(deleteNodeInfos.getNetworkUuid(), deleteNodeInfos.getVariantIds())),
                    studyServerExecutionService.runAsync(() -> removedNodes.forEach(dynamicSimulationEventService::deleteEventsByNodeId))
            );

            try {
                executeInParallel.get();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LOGGER.error(e.toString(), e);
                throw new StudyException(DELETE_NODE_FAILED, e.getMessage());
            }

            if (startTime.get() != null) {
                LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId.toString().replaceAll("[\n\r]", "_"), studyUuid,
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
            }

            if (invalidateChildrenBuild) {
                childrenNodes.forEach(nodeEntity -> updateStatuses(studyUuid, nodeEntity.getIdNode(), false, true, true));
            }
        }

        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void stashNode(UUID studyUuid, UUID nodeId, boolean stashChildren, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        boolean invalidateChildrenBuild = stashChildren || networkModificationTreeService.hasModifications(nodeId, false);
        invalidateBuild(studyUuid, nodeId, false, !invalidateChildrenBuild, true);
        networkModificationTreeService.doStashNode(studyUuid, nodeId, stashChildren);

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

    private void reindexStudy(StudyEntity study) {
        CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study);
        // reindex study in elasticsearch
        studyInfosService.recreateStudyInfos(studyInfos);

        // Reset indexation status
        updateStudyIndexationStatus(study, StudyIndexationStatus.INDEXING_ONGOING);
        try {
            networkConversionService.reindexStudyNetworkEquipments(study.getNetworkUuid());
            updateStudyIndexationStatus(study, StudyIndexationStatus.INDEXED);
        } catch (Exception e) {
            // Allow to retry indexation
            updateStudyIndexationStatus(study, StudyIndexationStatus.NOT_INDEXED);
            throw e;
        }
        invalidateBuild(study.getId(), networkModificationTreeService.getStudyRootNodeUuid(study.getId()), false, false, true);
        LOGGER.info("Study with id = '{}' has been reindexed", study.getId());
    }

    @Transactional
    public void reindexStudy(UUID studyUuid) {
        reindexStudy(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)));
    }

    @Transactional
    public StudyIndexationStatus getStudyIndexationStatus(UUID studyUuid) {
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        if (study.getIndexationStatus() == StudyIndexationStatus.INDEXED
            && !networkConversionService.checkStudyIndexationStatus(study.getNetworkUuid())) {
            updateStudyIndexationStatus(study, StudyIndexationStatus.NOT_INDEXED);
        }
        return study.getIndexationStatus();
    }

    @Transactional
    public void moveModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationUuidList, UUID beforeUuid, String userId) {
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
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(targetNodeUuid);
            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.moveModifications(originGroupUuid, modificationUuidList, beforeUuid, networkUuid, nodeInfos, buildTargetNode);
            if (!targetNodeBelongsToSourceNodeSubTree) {
                // invalidate the whole subtree except maybe the target node itself (depends if we have built this node during the move)
                networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, targetNodeUuid, modificationResult));
                updateStatuses(studyUuid, targetNodeUuid, buildTargetNode, true, true);
            }
            if (moveBetweenNodes) {
                // invalidate the whole subtree including the source node
                networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, originNodeUuid, modificationResult));
                updateStatuses(studyUuid, originNodeUuid, false, true, true);
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
    public void duplicateModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationUuidList, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.duplicateModification(modificationUuidList, networkUuid, nodeInfos);
            // invalidate the whole subtree except the target node (we have built this node during the duplication)
            networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, nodeUuid, modificationResult));
            updateStatuses(studyUuid, nodeUuid, true, true, true);
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
    public ReporterModel getSubReport(String subReportId, Set<String> severityLevels) {
        return reportService.getSubReport(UUID.fromString(subReportId), severityLevels);
    }

    @Transactional(readOnly = true)
    public List<ReporterModel> getNodeReport(UUID nodeUuid, String reportId, ReportType reportType, Set<String> severityLevels) {
        return getSubReporters(nodeUuid, UUID.fromString(reportId), nodeUuid + "@" + reportType.reportKey, ReportNameMatchingType.EXACT_MATCHING, severityLevels);
    }

    private Pair<String, ReportNameMatchingType> getFiltersParamaters(UUID nodeUuid, boolean nodeOnlyReport, ReportType reportType) {
        String reportNameFilter;
        ReportNameMatchingType reportNameMatchingType;
        if (nodeOnlyReport) {
            reportNameFilter = nodeUuid + "@" + reportType.reportKey;
            reportNameMatchingType = ReportNameMatchingType.EXACT_MATCHING;
        } else {
            // in "all logs/nodes" mode, we have to filter only on the report type (ex: anything ending with "@NetWorkModification")
            reportNameFilter = "@" + reportType.reportKey;
            reportNameMatchingType = ReportNameMatchingType.ENDS_WITH;
        }
        return Pair.of(reportNameFilter, reportNameMatchingType);
    }

    @Transactional(readOnly = true)
    public List<ReporterModel> getParentNodesReport(UUID nodeUuid, boolean nodeOnlyReport, ReportType reportType, Set<String> severityLevels) {
        Pair<String, ReportNameMatchingType> filtersParameters = getFiltersParamaters(nodeUuid, nodeOnlyReport, reportType);
        AbstractNode nodeInfos = networkModificationTreeService.getNode(nodeUuid);
        List<ReporterModel> subReporters = getSubReporters(nodeUuid, nodeInfos.getReportUuid(), filtersParameters.getFirst(), filtersParameters.getSecond(), severityLevels);
        if (subReporters.isEmpty()) {
            return subReporters;
        } else if (nodeOnlyReport) {
            return List.of(subReporters.get(subReporters.size() - 1));
        } else {
            if (subReporters.get(0).getTaskKey().equals(ROOT_NODE_NAME)) {
                return subReporters;
            }
            Optional<UUID> parentUuid = networkModificationTreeService.getParentNodeUuid(UUID.fromString(subReporters.get(0).getTaskKey()));
            if (parentUuid.isEmpty()) {
                return subReporters;
            }
            List<ReporterModel> parentReporters = self.getParentNodesReport(parentUuid.get(), false, reportType, severityLevels);
            return Stream.concat(parentReporters.stream(), subReporters.stream()).collect(Collectors.toList());
        }
    }

    private List<ReporterModel> getSubReporters(UUID nodeUuid, UUID reportUuid, String reportNameFilter, ReportNameMatchingType reportNameMatchingType, Set<String> severityLevels) {
        ReporterModel reporter = reportService.getReport(reportUuid, nodeUuid.toString(), reportNameFilter, reportNameMatchingType, severityLevels);
        Map<String, List<ReporterModel>> subReportersByNode = new LinkedHashMap<>();
        reporter.getSubReporters().forEach(subReporter -> subReportersByNode.putIfAbsent(getNodeIdFromReportKey(subReporter), new ArrayList<>()));
        reporter.getSubReporters().forEach(subReporter ->
            subReportersByNode.get(getNodeIdFromReportKey(subReporter)).addAll(subReporter.getSubReporters())
        );
        return subReportersByNode.keySet().stream().map(nodeId -> {
            // For a node report, pass the reportId to the Front as taskValues, to allow direct access
            Map<String, TypedValue> taskValues = Map.of("id", new TypedValue(reportUuid.toString(), "ID"));
            ReporterModel newSubReporter = new ReporterModel(nodeId, nodeId, taskValues);
            subReportersByNode.get(nodeId).forEach(newSubReporter::addSubReporter);
            return newSubReporter;
        }).collect(Collectors.toList());
    }

    private String getNodeIdFromReportKey(ReporterModel reporter) {
        return Arrays.stream(reporter.getTaskKey().split("@")).findFirst().orElseThrow();
    }

    public void deleteNodeReport(UUID nodeUuid) {
        reportService.deleteReport(networkModificationTreeService.getReportUuid(nodeUuid));
    }

    private void updateNode(UUID studyUuid, UUID nodeUuid, Optional<NetworkModificationResult> networkModificationResult) {
        networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, nodeUuid, modificationResult));
        updateStatuses(studyUuid, nodeUuid);
    }

    private void emitNetworkModificationImpacts(UUID studyUuid, UUID nodeUuid, NetworkModificationResult networkModificationResult) {
        //TODO move this / rename parent method when refactoring notifications
        networkModificationTreeService.updateNodeBuildStatus(nodeUuid,
                NodeBuildStatus.from(networkModificationResult.getLastGroupApplicationStatus(), networkModificationResult.getApplicationStatus()));

        Set<org.gridsuite.study.server.notification.dto.EquipmentDeletionInfos> deletionsInfos =
            networkModificationResult.getNetworkImpacts().stream()
                .filter(impact -> impact.getImpactType() == SimpleImpactType.DELETION)
                .map(impact -> new org.gridsuite.study.server.notification.dto.EquipmentDeletionInfos(impact.getElementId(), impact.getElementType().name()))
            .collect(Collectors.toSet());

        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY,
            NetworkImpactsInfos.builder()
                .deletedEquipments(deletionsInfos)
                .impactedSubstationsIds(networkModificationResult.getImpactedSubstationsIds())
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
    public UUID runSensitivityAnalysis(UUID studyUuid, UUID nodeUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, SENSITIVITY_ANALYSIS);
        prevResultUuidOpt.ifPresent(sensitivityAnalysisService::deleteSensitivityAnalysisResult);
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getSensitivityAnalysisProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParametersValues loadFlowParameters = getLoadFlowParametersValues(study, ComputationUsingLoadFlow.SENSITIVITY_ANALYSIS);

        UUID result = sensitivityAnalysisService.runSensitivityAnalysis(nodeUuid, networkUuid, variantId, reportUuid, provider, userId, studyEntity.getSensitivityAnalysisParametersUuid(), loadFlowParameters);

        updateComputationResultUuid(nodeUuid, result, SENSITIVITY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        return result;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, String userId) {
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, SHORT_CIRCUIT);
        prevResultUuidOpt.ifPresent(shortCircuitService::deleteShortCircuitAnalysisResult);

        ShortCircuitParameters shortCircuitParameters = getShortCircuitParameters(studyUuid);
        shortCircuitParameters.setWithFortescueResult(false);
        UUID result = shortCircuitService.runShortCircuit(studyUuid, nodeUuid, null, shortCircuitParameters, userId);

        updateComputationResultUuid(nodeUuid, result, SHORT_CIRCUIT);

        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        return result;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, String userId, String busId) {
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, SHORT_CIRCUIT_ONE_BUS);
        prevResultUuidOpt.ifPresent(shortCircuitService::deleteShortCircuitAnalysisResult);

        ShortCircuitParameters shortCircuitParameters = getShortCircuitParameters(studyUuid);
        shortCircuitParameters.setWithFortescueResult(true);
        UUID result = shortCircuitService.runShortCircuit(studyUuid, nodeUuid, busId, shortCircuitParameters, userId);

        updateComputationResultUuid(nodeUuid, result, SHORT_CIRCUIT_ONE_BUS);

        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        return result;
    }

    public UUID runVoltageInit(UUID studyUuid, UUID nodeUuid, String userId) {
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, VOLTAGE_INITIALIZATION);
        prevResultUuidOpt.ifPresent(voltageInitService::deleteVoltageInitResult);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID result = voltageInitService.runVoltageInit(networkUuid, variantId, studyEntity.getVoltageInitParametersUuid(), nodeUuid, userId);

        updateComputationResultUuid(nodeUuid, result, VOLTAGE_INITIALIZATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        return result;
    }

    @Transactional
    public void setVoltageInitParameters(UUID studyUuid, String parameters, String userId) {
        createOrUpdateVoltageInitParameters(studyUuid, parameters);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public String getVoltageInitParameters(UUID studyUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        UUID voltageInitParametersUuid = studyEntity.getVoltageInitParametersUuid();
        if (voltageInitParametersUuid == null) {
            return "{}";
        }
        return voltageInitService.getVoltageInitParameters(studyEntity.getVoltageInitParametersUuid());
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
    public UUID runDynamicSimulation(UUID studyUuid, UUID nodeUuid, DynamicSimulationParametersInfos parameters, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        // pre-condition check
        String lfStatus = loadflowService.getLoadFlowStatus(nodeUuid);
        if (!LoadFlowStatus.CONVERGED.name().equals(lfStatus)) {
            throw new StudyException(NOT_ALLOWED, "Load flow must run successfully before running dynamic simulation");
        }

        // create receiver for getting back the notification in rabbitmq
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        // get associated network
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        // clean previous result if exist
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, DYNAMIC_SIMULATION);
        prevResultUuidOpt.ifPresent(dynamicSimulationService::deleteResult);

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

        // launch dynamic simulation
        UUID resultUuid = dynamicSimulationService.runDynamicSimulation(getDynamicSimulationProvider(studyUuid), receiver, networkUuid, "", mergeParameters, userId);

        // update result uuid and notification
        updateComputationResultUuid(nodeUuid, resultUuid, DYNAMIC_SIMULATION);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);

        return resultUuid;
    }

    public List<TimeSeriesMetadataInfos> getDynamicSimulationTimeSeriesMetadata(UUID nodeUuid) {
        return dynamicSimulationService.getTimeSeriesMetadataList(nodeUuid);
    }

    public List<DoubleTimeSeries> getDynamicSimulationTimeSeries(UUID nodeUuid, List<String> timeSeriesNames) {
        // get timeseries from node uuid
        return dynamicSimulationService.getTimeSeriesResult(nodeUuid, timeSeriesNames);
    }

    public List<StringTimeSeries> getDynamicSimulationTimeLine(UUID nodeUuid) {
        // get timeline from node uuid
        return dynamicSimulationService.getTimeLineResult(nodeUuid); // timeline has only one element
    }

    public DynamicSimulationStatus getDynamicSimulationStatus(UUID nodeUuid) {
        return dynamicSimulationService.getStatus(nodeUuid);
    }

    // --- Dynamic Simulation service methods END --- //

    public String getNetworkElementsIds(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String equipmentType) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getElementsIds(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, equipmentType);
    }

    @Transactional(readOnly = true)
    public String getVoltageInitModifications(@NonNull UUID nodeUuid) {
        // get modifications group uuid associated to voltage init results
        UUID voltageInitModificationsGroupUuid = voltageInitService.getModificationsGroupUuid(nodeUuid);
        return networkModificationService.getModifications(voltageInitModificationsGroupUuid, false, false);
    }

    public void copyVoltageInitModifications(UUID studyUuid, UUID nodeUuid, String userId) {
        // get modifications group uuid associated to voltage init results
        UUID voltageInitModificationsGroupUuid = voltageInitService.getModificationsGroupUuid(nodeUuid);

        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            Optional<NetworkModificationResult> networkModificationResult = networkModificationService.duplicateModificationsInGroup(voltageInitModificationsGroupUuid, networkUuid, nodeInfos);

            voltageInitService.resetModificationsGroupUuid(nodeUuid);

            // invalidate the whole subtree except the target node (we have built this node during the duplication)
            networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, nodeUuid, modificationResult));
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT); // send notification voltage init result has changed
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
    public void setSensitivityAnalysisParameters(UUID studyUuid, String parameters, String userId) {
        createOrUpdateSensitivityAnalysisParameters(studyUuid, parameters);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void setNonEvacuatedEnergyParametersInfos(UUID studyUuid, NonEvacuatedEnergyParametersInfos parameters, String userId) {
        updateNonEvacuatedEnergyParameters(studyUuid,
            NonEvacuatedEnergyService.toEntity(parameters != null ? parameters :
                NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos()));
        notificationService.emitElementUpdated(studyUuid, userId);
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
        shortCircuitService.invalidateShortCircuitStatus(networkModificationTreeService.getShortCircuitResultUuids(studyUuid));
    }

    @Transactional
    public void invalidateShortCircuitStatus(UUID studyUuid) {
        invalidateShortCircuitStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
    }

    public UUID runNonEvacuatedEnergy(UUID studyUuid, UUID nodeUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, NON_EVACUATED_ENERGY_ANALYSIS);

        prevResultUuidOpt.ifPresent(nonEvacuatedEnergyService::deleteNonEvacuatedEnergyResult);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getNonEvacuatedEnergyProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);

        NonEvacuatedEnergyParametersInfos nonEvacuatedEnergyParametersInfos = getNonEvacuatedEnergyParametersInfos(studyUuid);
        SensitivityAnalysisParameters sensitivityAnalysisParameters = SensitivityAnalysisParameters.load();
        sensitivityAnalysisParameters.setFlowFlowSensitivityValueThreshold(nonEvacuatedEnergyParametersInfos.getGeneratorsCappings().getSensitivityThreshold());

        LoadFlowParametersValues loadFlowParameters = getLoadFlowParametersValues(studyEntity, ComputationUsingLoadFlow.SENSITIVITY_ANALYSIS);
        sensitivityAnalysisParameters.setLoadFlowParameters(loadFlowParameters.getCommonParameters());

        NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData = new NonEvacuatedEnergyInputData();
        nonEvacuatedEnergyInputData.setParameters(sensitivityAnalysisParameters);
        nonEvacuatedEnergyInputData.setLoadFlowSpecificParameters(loadFlowParameters.getSpecificParameters());

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

        UUID result = nonEvacuatedEnergyService.runNonEvacuatedEnergy(nodeUuid, networkUuid, variantId, reportUuid, provider, nonEvacuatedEnergyInputData, userId);

        updateComputationResultUuid(nodeUuid, result, NON_EVACUATED_ENERGY_ANALYSIS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        return result;
    }

    @Transactional
    public void updateNonEvacuatedEnergyProvider(UUID studyUuid, String provider, String userId) {
        updateProvider(studyUuid, userId, studyEntity -> {
            studyEntity.setNonEvacuatedEnergyProvider(provider != null ? provider : defaultNonEvacuatedEnergyProvider);
            invalidateNonEvacuatedEnergyAnalysisStatusOnAllNodes(studyUuid);
            notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
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
    }

    public String evaluateFilter(UUID studyUuid, UUID nodeUuid, boolean inUpstreamBuiltParentNode, String filter) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return filterService.evaluateFilter(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn), filter);
    }
}
