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
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.*;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import com.powsybl.openreac.parameters.input.VoltageLimitOverride;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.Security;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.model.VariantInfos;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact.SimpleImpactType;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.voltageinit.FilterEquipments;
import org.gridsuite.study.server.dto.voltageinit.VoltageInitParametersInfos;
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
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.gridsuite.study.server.utils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.elasticsearch.EquipmentInfosService.EQUIPMENT_TYPE_SCORES;
import static org.gridsuite.study.server.service.NetworkModificationTreeService.ROOT_NODE_NAME;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@Service
public class StudyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyService.class);

    static final String EQUIPMENT_NAME = "equipmentName.fullascii";

    static final String EQUIPMENT_ID = "equipmentId.fullascii";

    static final String NETWORK_UUID = "networkUuid.keyword";

    static final String VARIANT_ID = "variantId.keyword";

    static final String EQUIPMENT_TYPE = "equipmentType.keyword";
    public static final String EMPTY_ARRAY = "[]";

    NotificationService notificationService;

    NetworkModificationTreeService networkModificationTreeService;

    StudyServerExecutionService studyServerExecutionService;

    private final String defaultLoadflowProvider;

    private final String defaultSecurityAnalysisProvider;

    private final String defaultSensitivityAnalysisProvider;

    private final String defaultDynamicSimulationProvider;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;
    private final LoadflowService loadflowService;
    private final ShortCircuitService shortCircuitService;
    private final VoltageInitService voltageInitService;
    private final SingleLineDiagramService singleLineDiagramService;
    private final NetworkConversionService networkConversionService;
    private final GeoDataService geoDataService;
    private final NetworkMapService networkMapService;
    private final SecurityAnalysisService securityAnalysisService;
    private final DynamicSimulationService dynamicSimulationService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final ActionsService actionsService;
    private final CaseService caseService;
    private final FilterService filterService;
    private final ObjectMapper objectMapper;

    enum ComputationUsingLoadFlow {
        LOAD_FLOW, SECURITY_ANALYSIS, SENSITIVITY_ANALYSIS
    }

    @Autowired
    StudyService self;

    @Autowired
    public StudyService(
            @Value("${loadflow.default-provider}") String defaultLoadflowProvider,
            @Value("${security-analysis.default-provider}") String defaultSecurityAnalysisProvider,
            @Value("${sensitivity-analysis.default-provider}") String defaultSensitivityAnalysisProvider,
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
            LoadflowService loadflowService,
            ShortCircuitService shortCircuitService,
            SingleLineDiagramService singleLineDiagramService,
            NetworkConversionService networkConversionService,
            GeoDataService geoDataService,
            NetworkMapService networkMapService,
            SecurityAnalysisService securityAnalysisService,
            ActionsService actionsService,
            CaseService caseService,
            FilterService filterService,
            SensitivityAnalysisService sensitivityAnalysisService,
            DynamicSimulationService dynamicSimulationService,
            VoltageInitService voltageInitService) {
        this.defaultLoadflowProvider = defaultLoadflowProvider;
        this.defaultSecurityAnalysisProvider = defaultSecurityAnalysisProvider;
        this.defaultSensitivityAnalysisProvider = defaultSensitivityAnalysisProvider;
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
        this.loadflowService = loadflowService;
        this.shortCircuitService = shortCircuitService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.geoDataService = geoDataService;
        this.networkMapService = networkMapService;
        this.securityAnalysisService = securityAnalysisService;
        this.actionsService = actionsService;
        this.caseService = caseService;
        this.filterService = filterService;
        this.dynamicSimulationService = dynamicSimulationService;
        this.voltageInitService = voltageInitService;
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

    public BasicStudyInfos createStudy(UUID caseUuid, String userId, UUID studyUuid, Map<String, Object> importParameters, boolean duplicateCase) {
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        UUID importReportUuid = UUID.randomUUID();
        UUID caseUuidToUse = caseUuid;
        try {
            if (duplicateCase) {
                caseUuidToUse = caseService.duplicateCase(caseUuid, true);
            }
            persistentStoreWithNotificationOnError(caseUuidToUse, basicStudyInfos.getId(), userId, importReportUuid, importParameters);
        } catch (Exception e) {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            throw e;
        }

        return basicStudyInfos;
    }

    public BasicStudyInfos duplicateStudy(UUID sourceStudyUuid, UUID studyUuid, String userId) {
        Objects.requireNonNull(sourceStudyUuid);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElse(null);
        if (sourceStudy == null) {
            return null;
        }
        LoadFlowParameters sourceLoadFlowParameters = LoadflowService.fromEntity(sourceStudy.getLoadFlowParameters());
        List<LoadFlowSpecificParameterInfos> sourceSpecificLoadFlowParameters = getAllSpecificLoadFlowParameters(sourceStudy);
        ShortCircuitParameters copiedShortCircuitParameters = ShortCircuitService.fromEntity(sourceStudy.getShortCircuitParameters());
        DynamicSimulationParametersInfos copiedDynamicSimulationParameters = sourceStudy.getDynamicSimulationParameters() != null ? DynamicSimulationService.fromEntity(sourceStudy.getDynamicSimulationParameters(), objectMapper) : DynamicSimulationService.getDefaultDynamicSimulationParameters();
        SecurityAnalysisParametersValues securityAnalysisParametersValues = sourceStudy.getSecurityAnalysisParameters() == null ? SecurityAnalysisService.getDefaultSecurityAnalysisParametersValues() : SecurityAnalysisService.fromEntity(sourceStudy.getSecurityAnalysisParameters());
        VoltageInitParametersInfos copiedVoltageInitParameters = VoltageInitService.fromEntity(sourceStudy.getVoltageInitParameters());

        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        studyServerExecutionService.runAsync(() -> duplicateStudyAsync(basicStudyInfos, sourceStudy, sourceLoadFlowParameters, sourceSpecificLoadFlowParameters, copiedShortCircuitParameters, copiedDynamicSimulationParameters, copiedVoltageInitParameters, userId, securityAnalysisParametersValues));
        return basicStudyInfos;
    }

    private void duplicateStudyAsync(BasicStudyInfos basicStudyInfos, StudyEntity sourceStudy, LoadFlowParameters sourceLoadFlowParameters, List<LoadFlowSpecificParameterInfos> sourceSpecificLoadFlowParameters, ShortCircuitParameters copiedShortCircuitParameters, DynamicSimulationParametersInfos copiedDynamicSimulationParameters, VoltageInitParametersInfos copiedVoltageInitParameters, String userId, SecurityAnalysisParametersValues securityAnalysisParametersValues) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        try {
            startTime.set(System.nanoTime());

            List<VariantInfos> networkVariants = networkStoreService.getNetworkVariants(sourceStudy.getNetworkUuid());
            List<String> targetVariantIds = networkVariants.stream().map(VariantInfos::getId).limit(2).collect(Collectors.toList());
            Network clonedNetwork = networkStoreService.cloneNetwork(sourceStudy.getNetworkUuid(), targetVariantIds);
            UUID clonedNetworkUuid = networkStoreService.getNetworkUuid(clonedNetwork);
            UUID clonedCaseUuid = caseService.duplicateCase(sourceStudy.getCaseUuid(), false);

            LoadFlowParameters newLoadFlowParameters = sourceLoadFlowParameters != null ? sourceLoadFlowParameters.copy() : new LoadFlowParameters();
            ShortCircuitParameters shortCircuitParameters = copiedShortCircuitParameters != null ? copiedShortCircuitParameters : ShortCircuitService.getDefaultShortCircuitParameters();
            DynamicSimulationParametersInfos dynamicSimulationParameters = copiedDynamicSimulationParameters != null ? copiedDynamicSimulationParameters : DynamicSimulationService.getDefaultDynamicSimulationParameters();
            StudyEntity duplicatedStudy = insertDuplicatedStudy(basicStudyInfos, sourceStudy, LoadflowService.toEntity(newLoadFlowParameters, sourceSpecificLoadFlowParameters), ShortCircuitService.toEntity(shortCircuitParameters), DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper), VoltageInitService.toEntity(copiedVoltageInitParameters), userId, clonedNetworkUuid, clonedCaseUuid, SecurityAnalysisService.toEntity(securityAnalysisParametersValues));
            reindexStudy(duplicatedStudy);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        } finally {
            deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            LOGGER.trace("Create study '{}' from source {} : {} seconds", basicStudyInfos.getId(), sourceStudy.getId(),
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    @Transactional(readOnly = true)
    public StudyInfos getStudyInfos(UUID studyUuid) {
        return StudyService.toStudyInfos(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)));
    }

    public List<CreatedStudyBasicInfos> searchStudies(@NonNull String query) {
        return studyInfosService.search(query);
    }

    public static String escapeLucene(String s) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '+':
                case '\\':
                case '-':
                case '!':
                case '(':
                case ')':
                case ':':
                case '^':
                case '[':
                case ']':
                case '"':
                case '{':
                case '}':
                case '~':
                case '*':
                case '?':
                case '|':
                case '&':
                case '/':

                case ' ': // white space has to be escaped, too
                    sb.append('\\');
                    break;
                default:
                    // do nothing but appease sonarlint
            }

            sb.append(c);
        }

        return sb.toString();
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

        if (variantId.isEmpty()) {
            variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
        }

        if (equipmentType == null) {
            BoolQueryBuilder query = buildSearchAllEquipmentsQuery(userInput, fieldSelector, networkUuid,
                    VariantManagerConstants.INITIAL_VARIANT_ID, variantId);
            List<EquipmentInfos> equipmentInfos = equipmentInfosService.searchEquipments(query);

            return variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID) ? equipmentInfos : cleanRemovedEquipments(networkUuid, variantId, equipmentInfos);
        } else {
            String queryInitialVariant = buildSearchEquipmentsByTypeQuery(userInput, fieldSelector, networkUuid,
                    VariantManagerConstants.INITIAL_VARIANT_ID, equipmentType);

            List<EquipmentInfos> equipmentInfosInInitVariant = equipmentInfosService.searchEquipments(queryInitialVariant);

            return (variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID)) ? equipmentInfosInInitVariant
                    : completeSearchWithCurrentVariant(networkUuid, variantId, userInput, fieldSelector,
                    equipmentInfosInInitVariant, equipmentType);
        }
    }

    private List<EquipmentInfos> cleanRemovedEquipments(UUID networkUuid, String variantId, List<EquipmentInfos> equipmentInfos) {
        String queryTombstonedEquipments = buildTombstonedEquipmentSearchQuery(networkUuid, variantId);
        Set<String> removedEquipmentIdsInVariant = equipmentInfosService.searchTombstonedEquipments(queryTombstonedEquipments)
                .stream()
                .map(TombstonedEquipmentInfos::getId)
                .collect(Collectors.toSet());

        return equipmentInfos
                .stream()
                .filter(ei -> !removedEquipmentIdsInVariant.contains(ei.getId()))
                .collect(Collectors.toList());
    }

    private List<EquipmentInfos> completeSearchWithCurrentVariant(UUID networkUuid, String variantId, String userInput,
                                                                  EquipmentInfosService.FieldSelector fieldSelector, List<EquipmentInfos> equipmentInfosInInitVariant,
                                                                  String equipmentType) {
        // Clean equipments that have been removed in the current variant
        List<EquipmentInfos> cleanedEquipmentsInInitVariant = cleanRemovedEquipments(networkUuid, variantId, equipmentInfosInInitVariant);

        // Get the equipments of the current variant
        String queryVariant = buildSearchEquipmentsByTypeQuery(userInput, fieldSelector, networkUuid, variantId, equipmentType);
        List<EquipmentInfos> addedEquipmentInfosInVariant = equipmentInfosService.searchEquipments(queryVariant);

        // Add equipments of the current variant to the ones of the init variant
        cleanedEquipmentsInInitVariant.addAll(addedEquipmentInfosInVariant);

        return cleanedEquipmentsInInitVariant;
    }

    private String buildSearchEquipmentsByTypeQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String variantId, String equipmentType) {
        String query = NETWORK_UUID + ":(%s) AND " + VARIANT_ID + ":(%s) AND %s:(*%s*)"
                + (equipmentType == null ? "" : " AND " + EQUIPMENT_TYPE + ":(%s)");
        return String.format(query, networkUuid, variantId,
                fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID,
                escapeLucene(userInput), equipmentType);
    }

    private BoolQueryBuilder buildSearchAllEquipmentsQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String initialVariantId, String variantId) {
        WildcardQueryBuilder equipmentSearchQuery = QueryBuilders.wildcardQuery(fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID, "*" + escapeLucene(userInput) + "*");
        TermsQueryBuilder networkUuidSearchQuery = termsQuery(NETWORK_UUID, networkUuid.toString());
        TermsQueryBuilder variantIdSearchQuery = variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID) ?
                termsQuery(VARIANT_ID, initialVariantId)
                : termsQuery(VARIANT_ID, initialVariantId, variantId);

        FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionsForScoreQueries = new FunctionScoreQueryBuilder.FilterFunctionBuilder[EQUIPMENT_TYPE_SCORES.size() + 1];

        int i = 0;
        filterFunctionsForScoreQueries[i++] = new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                matchQuery(fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID, escapeLucene(userInput)),
                ScoreFunctionBuilders.weightFactorFunction(EQUIPMENT_TYPE_SCORES.size()));

        for (Map.Entry<String, Integer> equipmentTypeScore : EQUIPMENT_TYPE_SCORES.entrySet()) {
            filterFunctionsForScoreQueries[i++] =
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            matchQuery("equipmentType", equipmentTypeScore.getKey()),
                            ScoreFunctionBuilders.weightFactorFunction(equipmentTypeScore.getValue())
                    );
        }

        FunctionScoreQueryBuilder functionScoreBoostQuery = QueryBuilders.functionScoreQuery(filterFunctionsForScoreQueries);

        BoolQueryBuilder esQuery = QueryBuilders.boolQuery();
        esQuery.filter(equipmentSearchQuery).filter(networkUuidSearchQuery).filter(variantIdSearchQuery).must(functionScoreBoostQuery);
        return esQuery;
    }

    private String buildTombstonedEquipmentSearchQuery(UUID networkUuid, String variantId) {
        return String.format(NETWORK_UUID + ":(%s) AND " + VARIANT_ID + ":(%s)", networkUuid, variantId);
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
                                .map(NodeModificationInfos::getSecurityAnalysisUuid).filter(Objects::nonNull).forEach(securityAnalysisService::deleteSaResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getSensitivityAnalysisUuid).filter(Objects::nonNull).forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)), // TODO delete all with one request only
                        studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                                .map(NodeModificationInfos::getShortCircuitAnalysisUuid).filter(Objects::nonNull).forEach(shortCircuitService::deleteShortCircuitAnalysisResult)), // TODO delete all with one request only
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
        equipmentInfosService.deleteAll(networkUuid);
        LOGGER.trace("Indexes deletion for network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
    }

    public CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, String caseFormat,
                                              UUID caseUuid, String caseName, LoadFlowParametersEntity loadFlowParameters,
                                              ShortCircuitParametersEntity shortCircuitParametersEntity, DynamicSimulationParametersEntity dynamicSimulationParametersEntity, VoltageInitParametersEntity voltageInitParametersEntity, UUID importReportUuid) {
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertStudyEntity(
                studyUuid, userId, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), caseFormat, caseUuid, caseName, loadFlowParameters, importReportUuid, shortCircuitParametersEntity, dynamicSimulationParametersEntity, voltageInitParametersEntity));
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    @Transactional
    public StudyEntity insertDuplicatedStudy(BasicStudyInfos studyInfos, StudyEntity sourceStudy, LoadFlowParametersEntity newLoadFlowParameters, ShortCircuitParametersEntity newShortCircuitParameters, DynamicSimulationParametersEntity newDynamicSimulationParameters, VoltageInitParametersEntity newVoltageInitParameters, String userId, UUID clonedNetworkUuid, UUID clonedCaseUuid, SecurityAnalysisParametersEntity securityAnalysisParametersEntity) {
        Objects.requireNonNull(studyInfos.getId());
        Objects.requireNonNull(userId);
        Objects.requireNonNull(clonedNetworkUuid);
        Objects.requireNonNull(clonedCaseUuid);
        Objects.requireNonNull(sourceStudy.getNetworkId());
        Objects.requireNonNull(sourceStudy.getCaseFormat());
        Objects.requireNonNull(sourceStudy.getCaseUuid());
        Objects.requireNonNull(newLoadFlowParameters);
        Objects.requireNonNull(securityAnalysisParametersEntity);

        UUID reportUuid = UUID.randomUUID();
        StudyEntity studyEntity = new StudyEntity(studyInfos.getId(), clonedNetworkUuid, sourceStudy.getNetworkId(), sourceStudy.getCaseFormat(),
                clonedCaseUuid, sourceStudy.getCaseName(), sourceStudy.getLoadFlowProvider(), sourceStudy.getSecurityAnalysisProvider(),
                sourceStudy.getSensitivityAnalysisProvider(), sourceStudy.getDynamicSimulationProvider(), newLoadFlowParameters, newShortCircuitParameters, newDynamicSimulationParameters, newVoltageInitParameters, securityAnalysisParametersEntity);
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertDuplicatedStudy(studyEntity, sourceStudy.getId(), reportUuid));

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

    private void persistentStoreWithNotificationOnError(UUID caseUuid, UUID studyUuid, String userId, UUID importReportUuid, Map<String, Object> importParameters) {
        try {
            networkConversionService.persistentStore(caseUuid, studyUuid, userId, importReportUuid, importParameters);
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
        return networkMapService.getElementsInfos(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, elementType, infoType);
    }

    public String getNetworkElementInfos(UUID studyUuid, UUID nodeUuid, String elementType, String infoType, String elementId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getElementInfos(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                elementType, infoType, elementId);
    }

    public String getVoltageLevelsAndEquipment(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "voltage-levels-equipments");
    }

    public String getVoltageLevelEquipments(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String voltageLevelId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        String equipmentPath = "voltage-level-equipments" + (voltageLevelId == null ? "" : StudyConstants.DELIMITER + voltageLevelId);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, equipmentPath);
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

    public void runLoadFlow(UUID studyUuid, UUID nodeUuid) {
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParametersInfos lfParameters = getLoadFlowParametersInfos(studyEntity);
        loadflowService.runLoadFlow(studyUuid, nodeUuid, lfParameters, studyEntity.getLoadFlowProvider());
    }

    public ExportNetworkInfos exportNetwork(UUID studyUuid, UUID nodeUuid, String format, String paramatersJson) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return networkConversionService.exportNetwork(networkUuid, variantId, format, paramatersJson);
    }

    public void assertLoadFlowRunnable(UUID nodeUuid) {
        LoadFlowStatus lfStatus = getLoadFlowStatus(nodeUuid);

        if (!LoadFlowStatus.NOT_DONE.equals(lfStatus)) {
            throw new StudyException(LOADFLOW_NOT_RUNNABLE);
        }
    }

    private void assertLoadFlowNotRunning(UUID nodeUuid) {
        LoadFlowStatus lfStatus = getLoadFlowStatus(nodeUuid);

        if (LoadFlowStatus.RUNNING.equals(lfStatus)) {
            throw new StudyException(LOADFLOW_RUNNING);
        }
    }

    private void assertComputationNotRunning(UUID nodeUuid) {
        assertLoadFlowNotRunning(nodeUuid);
        securityAnalysisService.assertSecurityAnalysisNotRunning(nodeUuid);
        dynamicSimulationService.assertDynamicSimulationNotRunning(nodeUuid);
        sensitivityAnalysisService.assertSensitivityAnalysisNotRunning(nodeUuid);
        shortCircuitService.assertShortCircuitAnalysisNotRunning(nodeUuid);
        voltageInitService.assertVoltageInitNotRunning(nodeUuid);
    }

    public void assertIsNodeNotReadOnly(UUID nodeUuid) {
        Boolean isReadOnly = networkModificationTreeService.isReadOnly(nodeUuid).orElse(Boolean.FALSE);
        if (Boolean.TRUE.equals(isReadOnly)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public void assertCanModifyNode(UUID studyUuid, UUID nodeUuid) {
        assertIsNodeNotReadOnly(nodeUuid);
        assertNoBuildNoComputation(studyUuid, nodeUuid);
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

    private LoadFlowParameters getLoadFlowParameters(StudyEntity studyEntity) {
        return LoadflowService.fromEntity(studyEntity.getLoadFlowParameters());
    }

    public LoadFlowParameters getLoadFlowParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(this::getLoadFlowParameters)
                .orElse(null);
    }

    public LoadFlowParametersInfos getLoadFlowParametersInfos(StudyEntity study) {
        LoadFlowParameters commonParameters = getLoadFlowParameters(study);
        List<LoadFlowSpecificParameterInfos> specificParameters = getSpecificLoadFlowParameters(study, ComputationUsingLoadFlow.LOAD_FLOW);
        return LoadFlowParametersInfos.builder()
                .commonParameters(commonParameters)
                .specificParameters(specificParameters.stream().collect(Collectors.toMap(LoadFlowSpecificParameterInfos::getName, LoadFlowSpecificParameterInfos::getValue)))
                .build();
    }

    public LoadFlowParametersValues getLoadFlowParametersValues(UUID studyUuid) {
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParameters commonParameters = getLoadFlowParameters(study);
        List<LoadFlowSpecificParameterInfos> specificParameters = getAllSpecificLoadFlowParameters(study);
        Map<String, Map<String, Object>> specificParametersPerProvider = specificParameters.stream()
            .collect(Collectors.groupingBy(LoadFlowSpecificParameterInfos::getProvider,
                Collectors.toMap(LoadFlowSpecificParameterInfos::getName, LoadFlowSpecificParameterInfos::getValue)));
        return LoadFlowParametersValues.builder()
                .commonParameters(commonParameters)
                .specificParametersPerProvider(specificParametersPerProvider)
                .build();
    }

    private List<LoadFlowSpecificParameterInfos> getSpecificLoadFlowParameters(StudyEntity study, ComputationUsingLoadFlow computation) {
        List<LoadFlowSpecificParameterEntity> params = study.getLoadFlowParameters().getSpecificParameters();
        String lfProvider;
        if (computation == ComputationUsingLoadFlow.SECURITY_ANALYSIS) {
            lfProvider = study.getSecurityAnalysisProvider();
        } else if (computation == ComputationUsingLoadFlow.SENSITIVITY_ANALYSIS) {
            lfProvider = study.getSensitivityAnalysisProvider();
        } else {
            lfProvider = study.getLoadFlowProvider();
        }
        return params.stream()
                .filter(p -> p.getProvider().equalsIgnoreCase(lfProvider))
                .map(LoadFlowSpecificParameterEntity::toLoadFlowSpecificParameterInfos)
                .collect(Collectors.toList());
    }

    private List<LoadFlowSpecificParameterInfos> getAllSpecificLoadFlowParameters(StudyEntity study) {
        List<LoadFlowSpecificParameterEntity> params = study.getLoadFlowParameters().getSpecificParameters();
        return params.stream()
                .map(LoadFlowSpecificParameterEntity::toLoadFlowSpecificParameterInfos)
                .collect(Collectors.toList());
    }

    private List<LoadFlowSpecificParameterInfos> getSpecificLoadFlowParameters(UUID studyUuid, ComputationUsingLoadFlow computation) {
        return studyRepository.findById(studyUuid)
                .map(study -> getSpecificLoadFlowParameters(study, computation))
                .orElse(List.of());
    }

    private LoadFlowParametersEntity createParametersEntity(LoadFlowParametersValues parameters) {
        LoadFlowParameters allCommonValues;
        List<LoadFlowSpecificParameterInfos> allSpecificValues = new ArrayList<>(List.of());
        if (parameters == null) {
            allCommonValues = LoadFlowParameters.load();
        } else {
            allCommonValues = parameters.getCommonParameters();
            if (parameters.getSpecificParametersPerProvider() != null) {
                parameters.getSpecificParametersPerProvider().forEach((provider, paramsMap) -> {
                    if (paramsMap != null) {
                        paramsMap.forEach((paramName, paramValue) -> {
                                if (paramValue != null) {
                                    allSpecificValues.add(LoadFlowSpecificParameterInfos.builder()
                                            .provider(provider)
                                            .value(Objects.toString(paramValue))
                                            .name(paramName)
                                            .build());
                                }
                            }
                        );
                    }
                });
            }
        }
        return LoadflowService.toEntity(allCommonValues, allSpecificValues);
    }

    public SecurityAnalysisParametersValues getSecurityAnalysisParametersValues(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> studyEntity.getSecurityAnalysisParameters() != null ? SecurityAnalysisService.fromEntity(studyEntity.getSecurityAnalysisParameters()) : SecurityAnalysisService.getDefaultSecurityAnalysisParametersValues())
                .orElse(null);
    }

    @Transactional
    public void setSecurityAnalysisParametersValues(UUID studyUuid, SecurityAnalysisParametersValues parameters, String userId) {
        updateSecurityAnalysisParameters(studyUuid, SecurityAnalysisService.toEntity(parameters != null ? parameters : SecurityAnalysisService.getDefaultSecurityAnalysisParametersValues()));
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void setLoadFlowParameters(UUID studyUuid, LoadFlowParametersValues parameters, String userId) {
        updateLoadFlowParameters(studyUuid, createParametersEntity(parameters));
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
        invalidateDynamicSimulationStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE);
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
            networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE);
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

    @Transactional
    public void setShortCircuitParameters(UUID studyUuid, ShortCircuitParameters parameters, String userId) {
        updateShortCircuitParameters(studyUuid, ShortCircuitService.toEntity(parameters != null ? parameters : ShortCircuitService.getDefaultShortCircuitParameters()));
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public UUID runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getSecurityAnalysisProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);
        prevResultUuidOpt.ifPresent(securityAnalysisService::deleteSaResult);

        List<LoadFlowSpecificParameterInfos> specificParameters = null;
        SecurityAnalysisParameters securityAnalysisParameters = getSecurityAnalysisParameters(studyUuid);
        specificParameters = getSpecificLoadFlowParameters(studyUuid, ComputationUsingLoadFlow.SECURITY_ANALYSIS);
        LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyUuid);
        securityAnalysisParameters.setLoadFlowParameters(loadFlowParameters);

        SecurityAnalysisParametersInfos params = SecurityAnalysisParametersInfos.builder()
                .parameters(securityAnalysisParameters)
                .loadFlowSpecificParameters(specificParameters == null ?
                    Map.of() : specificParameters.stream().collect(Collectors.toMap(LoadFlowSpecificParameterInfos::getName, LoadFlowSpecificParameterInfos::getValue)))
                .build();

        UUID result = securityAnalysisService.runSecurityAnalysis(networkUuid, reportUuid, nodeUuid, variantId, provider, contingencyListNames, params, receiver);

        updateSecurityAnalysisResultUuid(nodeUuid, result);
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

    public static LimitViolationInfos toLimitViolationInfos(LimitViolation violation) {
        return LimitViolationInfos.builder()
                .subjectId(violation.getSubjectId())
                .acceptableDuration(violation.getAcceptableDuration())
                .limit(violation.getLimit())
                .limitName(violation.getLimitName())
                .value(violation.getValue())
                .side(violation.getSide() != null ? violation.getSide().name() : "")
                .limitType(violation.getLimitType()).build();
    }

    public List<LimitViolationInfos> getLimitViolations(UUID studyUuid, UUID nodeUuid, float limitReduction) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION, networkModificationTreeService.getVariantId(nodeUuid));
        List<LimitViolation> violations;
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        LoadFlowParameters lfCommonParams = getLoadFlowParameters(studyEntity);
        if (lfCommonParams.isDc()) {
            violations = Security.checkLimitsDc(network, limitReduction, lfCommonParams.getDcPowerFactor());
        } else {
            violations = Security.checkLimits(network, limitReduction);
        }
        return violations.stream()
                .map(StudyService::toLimitViolationInfos).collect(Collectors.toList());
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
        securityAnalysisService.invalidateSaStatus(networkModificationTreeService.getStudySecurityAnalysisResultUuids(studyUuid));
    }

    public void invalidateSensitivityAnalysisStatusOnAllNodes(UUID studyUuid) {
        sensitivityAnalysisService.invalidateSensitivityAnalysisStatus(networkModificationTreeService.getStudySensitivityAnalysisResultUuids(studyUuid));
    }

    public void invalidateDynamicSimulationStatusOnAllNodes(UUID studyUuid) {
        dynamicSimulationService.invalidateStatus(networkModificationTreeService.getStudyDynamicSimulationResultUuids(studyUuid));
    }

    private StudyEntity insertStudyEntity(UUID uuid, String userId, UUID networkUuid, String networkId,
                                          String caseFormat, UUID caseUuid, String caseName, LoadFlowParametersEntity loadFlowParameters,
                                          UUID importReportUuid, ShortCircuitParametersEntity shortCircuitParameters, DynamicSimulationParametersEntity dynamicSimulationParameters, VoltageInitParametersEntity voltageInitParameters) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowParameters);
        Objects.requireNonNull(shortCircuitParameters);

        StudyEntity studyEntity = new StudyEntity(uuid, networkUuid, networkId, caseFormat, caseUuid, caseName, defaultLoadflowProvider,
                defaultSecurityAnalysisProvider, defaultSensitivityAnalysisProvider, defaultDynamicSimulationProvider, loadFlowParameters, shortCircuitParameters, dynamicSimulationParameters, voltageInitParameters);
        return self.insertStudy(studyEntity, importReportUuid);
    }

    @Transactional
    public StudyEntity insertStudy(StudyEntity studyEntity, UUID importReportUuid) {
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

    void updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        networkModificationTreeService.updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid);
    }

    void updateDynamicSimulationResultUuid(UUID nodeUuid, UUID dynamicSimulationResultUuid) {
        networkModificationTreeService.updateDynamicSimulationResultUuid(nodeUuid, dynamicSimulationResultUuid);
    }

    void updateSensitivityAnalysisResultUuid(UUID nodeUuid, UUID sensitivityAnalysisResultUuid) {
        networkModificationTreeService.updateSensitivityAnalysisResultUuid(nodeUuid, sensitivityAnalysisResultUuid);
    }

    void updateShortCircuitAnalysisResultUuid(UUID nodeUuid, UUID shortCircuitAnalysisResultUuid) {
        networkModificationTreeService.updateShortCircuitAnalysisResultUuid(nodeUuid, shortCircuitAnalysisResultUuid);
    }

    void updateOneBusShortCircuitAnalysisResultUuid(UUID nodeUuid, UUID shortCircuitAnalysisResultUuid) {
        networkModificationTreeService.updateOneBusShortCircuitAnalysisResultUuid(nodeUuid, shortCircuitAnalysisResultUuid);
    }

    void updateVoltageInitResultUuid(UUID nodeUuid, UUID voltageInitResultUuid) {
        networkModificationTreeService.updateVoltageInitResultUuid(nodeUuid, voltageInitResultUuid);
    }

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(UUID studyUuid) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid);
        return studyCreationRequestRepository.save(studyCreationRequestEntity);
    }

    public void updateLoadFlowParameters(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setLoadFlowParameters(loadFlowParametersEntity));
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

    public void updateVoltageInitParameters(UUID studyUuid, VoltageInitParametersEntity voltageInitParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setVoltageInitParameters(voltageInitParametersEntity));
    }

    public void updateSecurityAnalysisParameters(UUID studyUuid, SecurityAnalysisParametersEntity securityAnalysisParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setSecurityAnalysisParameters(securityAnalysisParametersEntity));
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

    public LoadFlowStatus getLoadFlowStatus(UUID nodeUuid) {
        return networkModificationTreeService.getLoadFlowStatus(nodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    public LoadFlowInfos getLoadFlowInfos(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        return networkModificationTreeService.getLoadFlowInfos(nodeUuid);
    }

    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(nodeUuid);
        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
        reportService.deleteReport(buildInfos.getReportUuid());

        try {
            networkModificationService.buildNode(studyUuid, nodeUuid, buildInfos);
        } catch (Exception e) {
            networkModificationTreeService.updateNodeBuildStatus(nodeUuid, NodeBuildStatus.from(BuildStatus.NOT_BUILT));
            throw new StudyException(NODE_BUILD_ERROR, e.getMessage());
        }

    }

    public void stopBuild(@NonNull UUID nodeUuid) {
        networkModificationService.stopBuild(nodeUuid);
    }

    @Transactional
    public void duplicateStudyNode(UUID sourceStudyUuid, UUID targetStudyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        checkStudyContainsNode(sourceStudyUuid, nodeToCopyUuid);
        checkStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
        boolean invalidateBuild = !EMPTY_ARRAY.equals(networkModificationTreeService.getNetworkModifications(nodeToCopyUuid));
        notificationService.emitNodeInserted(targetStudyUuid, referenceNodeUuid, duplicatedNodeUuid, insertMode);
        updateStatuses(targetStudyUuid, duplicatedNodeUuid, true, invalidateBuild);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    @Transactional
    public void moveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        List<NodeEntity> oldChildren = null;
        checkStudyContainsNode(studyUuid, nodeToMoveUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);
        boolean shouldInvalidateChildren = !EMPTY_ARRAY.equals(networkModificationTreeService.getNetworkModifications(nodeToMoveUuid));

        //Invalidating previous children if necessary
        if (shouldInvalidateChildren) {
            oldChildren = networkModificationTreeService.getChildrenByParentUuid(nodeToMoveUuid);
        }

        networkModificationTreeService.moveStudyNode(nodeToMoveUuid, referenceNodeUuid, insertMode);

        //Invalidating moved node or new children if necessary
        if (shouldInvalidateChildren) {
            updateStatuses(studyUuid, nodeToMoveUuid, false, true);
            oldChildren.forEach(child -> updateStatuses(studyUuid, child.getIdNode(), false, true));
        } else {
            invalidateBuild(studyUuid, nodeToMoveUuid, false, true);
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
            updateStatuses(studyUuid, parentNodeToMoveUuid, false, true);
        }
        allChildren.stream()
                .filter(childUuid -> networkModificationTreeService.getNodeBuildStatus(childUuid).isBuilt())
                .forEach(childUuid -> updateStatuses(studyUuid, childUuid, false, true));

        notificationService.emitSubtreeMoved(studyUuid, parentNodeToMoveUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void invalidateBuild(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateOnlyTargetNode) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        invalidateNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid));
        // we might want to invalidate target node without impacting other nodes (when moving an empty node for example)
        if (invalidateOnlyTargetNode) {
            networkModificationTreeService.invalidateBuildOfNodeOnly(nodeUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos);
        } else {
            networkModificationTreeService.invalidateBuild(nodeUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos);
        }

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getReportUuids().forEach(reportService::deleteReport)),  // TODO delete all with one request only
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                studyServerExecutionService.runAsync(() -> invalidateNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
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
        updateStatuses(studyUuid, nodeUuid, invalidateOnlyChildrenBuildStatus, true);
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus, boolean invalidateBuild) {
        if (invalidateBuild) {
            invalidateBuild(studyUuid, nodeUuid, invalidateOnlyChildrenBuildStatus, false);
        }
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
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
    public void deleteNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.deleteModifications(groupId, modificationsUuids);
            networkModificationTreeService.removeModificationsToExclude(nodeUuid, modificationsUuids);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void deleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        DeleteNodeInfos deleteNodeInfos = new DeleteNodeInfos();
        deleteNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid));
        boolean invalidateChildrenBuild = !deleteChildren && !EMPTY_ARRAY.equals(networkModificationTreeService.getNetworkModifications(nodeId));
        List<NodeEntity> childrenNodes = networkModificationTreeService.getChildrenByParentUuid(nodeId);
        networkModificationTreeService.doDeleteNode(studyUuid, nodeId, deleteChildren, deleteNodeInfos);

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() -> deleteNodeInfos.getModificationGroupUuids().forEach(networkModificationService::deleteModifications)),
                studyServerExecutionService.runAsync(() -> deleteNodeInfos.getReportUuids().forEach(reportService::deleteReport)),
                studyServerExecutionService.runAsync(() -> deleteNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() -> deleteNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                studyServerExecutionService.runAsync(() -> deleteNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                studyServerExecutionService.runAsync(() -> deleteNodeInfos.getVoltageInitResultUuids().forEach(voltageInitService::deleteVoltageInitResult)),
                studyServerExecutionService.runAsync(() -> deleteNodeInfos.getDynamicSimulationResultUuids().forEach(dynamicSimulationService::deleteResult)),
                studyServerExecutionService.runAsync(() -> networkStoreService.deleteVariants(deleteNodeInfos.getNetworkUuid(), deleteNodeInfos.getVariantIds()))
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
            LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId, studyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }

        if (invalidateChildrenBuild) {
            childrenNodes.forEach(nodeEntity -> updateStatuses(studyUuid, nodeEntity.getIdNode(), false, true));
        }

        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void reindexStudy(StudyEntity study) {
        CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study);
        // reindex study in elasticsearch
        studyInfosService.recreateStudyInfos(studyInfos);
        try {
            networkConversionService.reindexStudyNetworkEquipments(study.getNetworkUuid());
        } catch (HttpStatusCodeException e) {
            LOGGER.error(e.toString(), e);
            throw e;
        }
        invalidateBuild(study.getId(), networkModificationTreeService.getStudyRootNodeUuid(study.getId()), false, false);
        LOGGER.info("Study with id = '{}' has been reindexed", study.getId());
    }

    public void reindexStudy(UUID studyUuid) {
        reindexStudy(studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND)));
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
                updateStatuses(studyUuid, targetNodeUuid, buildTargetNode, true);
            }
            if (moveBetweenNodes) {
                // invalidate the whole subtree including the source node
                networkModificationResult.ifPresent(modificationResult -> emitNetworkModificationImpacts(studyUuid, originNodeUuid, modificationResult));
                updateStatuses(studyUuid, originNodeUuid, false, true);
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
            updateStatuses(studyUuid, nodeUuid, true, true);
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
    public List<ReporterModel> getNodeReport(UUID nodeUuid, boolean nodeOnlyReport) {
        return getSubReportersByNodeFrom(nodeUuid, nodeOnlyReport);
    }

    private List<ReporterModel> getSubReportersByNodeFrom(UUID nodeUuid, boolean nodeOnlyReport) {
        List<ReporterModel> subReporters = getSubReportersByNodeFrom(nodeUuid);
        if (subReporters.isEmpty()) {
            return subReporters;
        } else if (nodeOnlyReport) {
            return List.of(subReporters.get(subReporters.size() - 1));
        } else {
            if (subReporters.get(0).getTaskKey().equals(ROOT_NODE_NAME)) {
                return subReporters;
            }
            Optional<UUID> parentUuid = networkModificationTreeService.getParentNodeUuid(UUID.fromString(subReporters.get(0).getTaskKey()));
            return parentUuid.isEmpty() ? subReporters : Stream.concat(getSubReportersByNodeFrom(parentUuid.get(), false).stream(), subReporters.stream()).collect(Collectors.toList());
        }
    }

    private List<ReporterModel> getSubReportersByNodeFrom(UUID nodeUuid) {
        AbstractNode nodeInfos = networkModificationTreeService.getNode(nodeUuid);
        ReporterModel reporter = reportService.getReport(nodeInfos.getReportUuid(), nodeInfos.getId().toString());
        Map<String, List<ReporterModel>> subReportersByNode = new LinkedHashMap<>();
        reporter.getSubReporters().forEach(subReporter -> subReportersByNode.putIfAbsent(getNodeIdFromReportKey(subReporter), new ArrayList<>()));
        reporter.getSubReporters().forEach(subReporter ->
            subReportersByNode.get(getNodeIdFromReportKey(subReporter)).addAll(subReporter.getSubReporters())
        );
        return subReportersByNode.keySet().stream().map(nodeId -> {
            ReporterModel newSubReporter = new ReporterModel(nodeId, nodeId);
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

        if (networkModificationResult.getNetworkImpacts().stream()
            .filter(impact -> impact.getImpactType() == SimpleImpactType.MODIFICATION)
            .anyMatch(impact -> impact.getElementType() == IdentifiableType.SWITCH)) {
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SWITCH);
        }

        if (networkModificationResult.getNetworkImpacts().stream()
            .filter(impact -> impact.getImpactType() == SimpleImpactType.MODIFICATION)
            .anyMatch(impact -> impact.getElementType() == IdentifiableType.LINE)) {
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LINE);
        }
    }

    public void notify(@NonNull String notificationName, @NonNull UUID studyUuid) {
        if (notificationName.equals(NotificationService.UPDATE_TYPE_STUDY_METADATA_UPDATED)) {
            notificationService.emitStudyMetadataChanged(studyUuid);
        } else {
            throw new StudyException(UNKNOWN_NOTIFICATION_TYPE);
        }
    }

    @Transactional
    public UUID runSensitivityAnalysis(UUID studyUuid, UUID nodeUuid, String sensitivityAnalysisInput) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(sensitivityAnalysisInput);

        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getSensitivityAnalysisResultUuid(nodeUuid);
        prevResultUuidOpt.ifPresent(sensitivityAnalysisService::deleteSensitivityAnalysisResult);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getSensitivityAnalysisProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);

        SensitivityAnalysisInputData sensitivityAnalysisInputData;
        try {
            sensitivityAnalysisInputData = objectMapper.readValue(sensitivityAnalysisInput, SensitivityAnalysisInputData.class);
            if (sensitivityAnalysisInputData.getParameters() == null) {
                SensitivityAnalysisParameters sensitivityAnalysisParameters = SensitivityAnalysisParameters.load();
                LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyUuid);
                List<LoadFlowSpecificParameterInfos> specificParameters = getSpecificLoadFlowParameters(studyUuid, ComputationUsingLoadFlow.SENSITIVITY_ANALYSIS);
                sensitivityAnalysisParameters.setLoadFlowParameters(loadFlowParameters);
                sensitivityAnalysisInputData.setParameters(sensitivityAnalysisParameters);
                sensitivityAnalysisInputData.setLoadFlowSpecificParameters(specificParameters == null ?
                    Map.of() : specificParameters.stream().collect(Collectors.toMap(LoadFlowSpecificParameterInfos::getName, LoadFlowSpecificParameterInfos::getValue)));
            }
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID result = sensitivityAnalysisService.runSensitivityAnalysis(nodeUuid, networkUuid, variantId, reportUuid, provider, sensitivityAnalysisInputData);

        updateSensitivityAnalysisResultUuid(nodeUuid, result);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        return result;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, String userId) {
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid, ShortcircuitAnalysisType.AllBuses);
        prevResultUuidOpt.ifPresent(shortCircuitService::deleteShortCircuitAnalysisResult);

        ShortCircuitParameters shortCircuitParameters = getShortCircuitParameters(studyUuid);
        UUID result = shortCircuitService.runShortCircuit(studyUuid, nodeUuid, null, shortCircuitParameters, userId);

        updateShortCircuitAnalysisResultUuid(nodeUuid, result);

        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        return result;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, String userId, String busId) {
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid, ShortcircuitAnalysisType.OneBus);
        prevResultUuidOpt.ifPresent(shortCircuitService::deleteShortCircuitAnalysisResult);

        ShortCircuitParameters shortCircuitParameters = getShortCircuitParameters(studyUuid);
        UUID result = shortCircuitService.runShortCircuit(studyUuid, nodeUuid, busId, shortCircuitParameters, userId);

        updateOneBusShortCircuitAnalysisResultUuid(nodeUuid, result);

        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        return result;
    }

    private List<String> toEquipmentIdsList(List<FilterEquipmentsEmbeddable> filters, UUID networkUuid, String variantId) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<FilterEquipments> equipments = filterService.exportFilters(filters.stream().map(filter -> filter.getFilterId()).collect(Collectors.toList()), networkUuid, variantId);
        Set<String> ids = new HashSet<>();
        equipments.forEach(filterEquipment ->
                filterEquipment.getIdentifiableAttributes().forEach(identifiableAttribute ->
                        ids.add(identifiableAttribute.getId())
                )
        );
        return ids.stream().collect(Collectors.toList());
    }

    private OpenReacParameters buildOpenReacParameters(Optional<StudyEntity> studyEntity, UUID networkUuid, String variantId) {
        OpenReacParameters parameters = new OpenReacParameters();
        Map<String, VoltageLimitOverride> specificVoltageLimits = new HashMap<>();
        List<String> constantQGenerators = new ArrayList<>();
        List<String> variableTwoWindingsTransformers = new ArrayList<>();
        List<String> variableShuntCompensators = new ArrayList<>();
        studyEntity.ifPresent(study -> {
            VoltageInitParametersEntity voltageInitParameters = study.getVoltageInitParameters();
            if (voltageInitParameters != null && voltageInitParameters.getVoltageLimits() != null) {
                voltageInitParameters.getVoltageLimits().forEach(voltageLimit -> {
                    var filterEquipments = filterService.exportFilters(voltageLimit.getFilters().stream().map(filter -> filter.getFilterId()).collect(Collectors.toList()), networkUuid, variantId);
                    filterEquipments.forEach(filterEquipment ->
                            filterEquipment.getIdentifiableAttributes().forEach(idenfiableAttribute ->
                                    specificVoltageLimits.put(idenfiableAttribute.getId(), new VoltageLimitOverride(voltageLimit.getLowVoltageLimit(), voltageLimit.getHighVoltageLimit()))
                            )
                    );
                });
                constantQGenerators.addAll(toEquipmentIdsList(voltageInitParameters.getConstantQGenerators(), networkUuid, variantId));
                variableTwoWindingsTransformers.addAll(toEquipmentIdsList(voltageInitParameters.getVariableTwoWindingsTransformers(), networkUuid, variantId));
                variableShuntCompensators.addAll(toEquipmentIdsList(voltageInitParameters.getVariableShuntCompensators(), networkUuid, variantId));
            }
        });
        parameters.addSpecificVoltageLimits(specificVoltageLimits)
                .addConstantQGenerators(constantQGenerators)
                .addVariableTwoWindingsTransformers(variableTwoWindingsTransformers)
                .addVariableShuntCompensators(variableShuntCompensators);

        return parameters;
    }

    public UUID runVoltageInit(UUID studyUuid, UUID nodeUuid, String userId) {
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getVoltageInitResultUuid(nodeUuid);
        prevResultUuidOpt.ifPresent(voltageInitService::deleteVoltageInitResult);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);

        OpenReacParameters parameters = buildOpenReacParameters(studyEntity, networkUuid, variantId);

        UUID result = voltageInitService.runVoltageInit(networkUuid, variantId, parameters, nodeUuid, userId);

        updateVoltageInitResultUuid(nodeUuid, result);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        return result;
    }

    @Transactional
    public void setVoltageInitParameters(UUID studyUuid, VoltageInitParametersInfos parameters, String userId) {
        updateVoltageInitParameters(studyUuid, VoltageInitService.toEntity(parameters));
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public VoltageInitParametersInfos getVoltageInitParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> VoltageInitService.fromEntity(studyEntity.getVoltageInitParameters()))
                .orElse(null);
    }

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

    @Transactional
    public UUID runDynamicSimulation(UUID studyUuid, UUID nodeUuid, DynamicSimulationParametersInfos parameters) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        // pre-condition check
        LoadFlowStatus lfStatus = getLoadFlowStatus(nodeUuid);
        if (lfStatus != LoadFlowStatus.CONVERGED) {
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
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);
        prevResultUuidOpt.ifPresent(dynamicSimulationService::deleteResult);

        // load configured parameters persisted in the study server DB
        DynamicSimulationParametersInfos configuredParameters = getDynamicSimulationParameters(studyUuid);
        // override configured parameters by provided parameters (only provided fields)
        DynamicSimulationParametersInfos mergeParameters = new DynamicSimulationParametersInfos();
        if (configuredParameters != null) {
            PropertyUtils.copyNonNullProperties(configuredParameters, mergeParameters);
        }
        if (parameters != null) {
            PropertyUtils.copyNonNullProperties(parameters, mergeParameters);
        }

        // launch dynamic simulation
        UUID resultUuid = dynamicSimulationService.runDynamicSimulation(getDynamicSimulationProvider(studyUuid), receiver, networkUuid, "", mergeParameters);

        // update result uuid and notification
        updateDynamicSimulationResultUuid(nodeUuid, resultUuid);
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

    public String getNetworkElementsIds(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String equipmentType) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getElementsIds(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, equipmentType);
    }

    public SecurityAnalysisParameters getSecurityAnalysisParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> SecurityAnalysisService.toSecurityAnalysisParameters(studyEntity.getSecurityAnalysisParameters()))
                .orElse(null);
    }

}
