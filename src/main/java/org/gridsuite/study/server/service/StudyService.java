/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.model.VariantInfos;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.timeseries.TimeSeries;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.modification.EquipmentDeletionInfos;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
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
import static org.gridsuite.study.server.service.NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS;

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

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;
    private final LoadflowService loadflowService;
    private final ShortCircuitService shortCircuitService;

    private final SingleLineDiagramService singleLineDiagramService;
    private final NetworkConversionService networkConversionService;
    private final GeoDataService geoDataService;
    private final NetworkMapService networkMapService;
    private final SecurityAnalysisService securityAnalysisService;
    private final DynamicSimulationService dynamicSimulationService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final ActionsService actionsService;

    private final ObjectMapper objectMapper;

    @Autowired
    StudyService self;

    @Autowired
    public StudyService(
            @Value("${loadflow.default-provider}") String defaultLoadflowProvider,
            @Value("${security-analysis.default-provider}") String defaultSecurityAnalysisProvider,
            @Value("${sensitivity-analysis.default-provider}") String defaultSensitivityAnalysisProvider,
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
            SensitivityAnalysisService sensitivityAnalysisService,
            DynamicSimulationService dynamicSimulationService) {
        this.defaultLoadflowProvider = defaultLoadflowProvider;
        this.defaultSecurityAnalysisProvider = defaultSecurityAnalysisProvider;
        this.defaultSensitivityAnalysisProvider = defaultSensitivityAnalysisProvider;
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
        this.dynamicSimulationService = dynamicSimulationService;
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

    public BasicStudyInfos createStudy(UUID caseUuid, String userId, UUID studyUuid, Map<String, Object> importParameters) {
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        UUID importReportUuid = UUID.randomUUID();
        try {
            persistentStoreWithNotificationOnError(caseUuid, basicStudyInfos.getId(), userId, importReportUuid, importParameters);
        } catch (Exception e) {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            throw e;
        }

        return basicStudyInfos;
    }

    public BasicStudyInfos createStudy(UUID sourceStudyUuid, UUID studyUuid, String userId) {
        Objects.requireNonNull(sourceStudyUuid);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElse(null);
        if (sourceStudy == null) {
            return null;
        }
        LoadFlowParameters sourceLoadFlowParameters = LoadflowService.fromEntity(sourceStudy.getLoadFlowParameters());
        ShortCircuitParameters copiedShortCircuitParameters = ShortCircuitService.fromEntity(sourceStudy.getShortCircuitParameters());

        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        studyServerExecutionService.runAsync(() -> duplicateStudyAsync(basicStudyInfos, sourceStudy, sourceLoadFlowParameters, copiedShortCircuitParameters, userId));
        return basicStudyInfos;
    }

    private void duplicateStudyAsync(BasicStudyInfos basicStudyInfos, StudyEntity sourceStudy, LoadFlowParameters sourceLoadFlowParameters, ShortCircuitParameters copiedShortCircuitParameters, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        try {
            startTime.set(System.nanoTime());

            List<VariantInfos> networkVariants = networkStoreService.getNetworkVariants(sourceStudy.getNetworkUuid());
            List<String> targetVariantIds = networkVariants.stream().map(VariantInfos::getId).limit(2).collect(Collectors.toList());
            Network clonedNetwork = networkStoreService.cloneNetwork(sourceStudy.getNetworkUuid(), targetVariantIds);
            UUID clonedNetworkUuid = networkStoreService.getNetworkUuid(clonedNetwork);

            LoadFlowParameters newLoadFlowParameters = sourceLoadFlowParameters != null ? sourceLoadFlowParameters.copy() : new LoadFlowParameters();
            ShortCircuitParameters shortCircuitParameters = copiedShortCircuitParameters != null ? copiedShortCircuitParameters : ShortCircuitService.getDefaultShortCircuitParameters();
            insertDuplicatedStudy(basicStudyInfos, sourceStudy, LoadflowService.toEntity(newLoadFlowParameters), ShortCircuitService.toEntity(shortCircuitParameters), userId, clonedNetworkUuid);
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
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(initialNodeUuid);
        }
        return nodeUuidToSearchIn;
    }

    public List<EquipmentInfos> searchEquipments(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull String userInput,
                                                 @NonNull EquipmentInfosService.FieldSelector  fieldSelector, String equipmentType,
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
        DeleteStudyInfos deleteStudyInfos = null;
        if (studyCreationRequestEntity.isEmpty()) {
            UUID networkUuid = networkStoreService.doGetNetworkUuid(studyUuid);
            List<NodeModificationInfos> nodesModificationInfos;
            nodesModificationInfos = networkModificationTreeService.getAllNodesModificationInfos(studyUuid);
            deleteStudyInfos = new DeleteStudyInfos(networkUuid, nodesModificationInfos);
            studyRepository.findById(studyUuid).ifPresent(s -> {
                networkModificationTreeService.doDeleteTree(studyUuid);
                studyRepository.deleteById(studyUuid);
                studyInfosService.deleteByUuid(studyUuid);
            });
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
                        .map(NodeModificationInfos::getDynamicSimulationUuid).filter(Objects::nonNull).forEach(dynamicSimulationService::deleteResult)), // TODO delete all with one request only
                    studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                        .map(NodeModificationInfos::getSensitivityAnalysisUuid).filter(Objects::nonNull).forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)), // TODO delete all with one request only
                    studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream()
                        .map(NodeModificationInfos::getShortCircuitAnalysisUuid).filter(Objects::nonNull).forEach(shortCircuitService::deleteShortCircuitAnalysisResult)), // TODO delete all with one request only
                    studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream().map(NodeModificationInfos::getModificationGroupUuid).filter(Objects::nonNull).forEach(networkModificationService::deleteModifications)), // TODO delete all with one request only
                    studyServerExecutionService.runAsync(() -> deleteStudyInfos.getNodesModificationInfos().stream().map(NodeModificationInfos::getReportUuid).filter(Objects::nonNull).forEach(reportService::deleteReport)), // TODO delete all with one request only
                    studyServerExecutionService.runAsync(() -> deleteEquipmentIndexes(deleteStudyInfos.getNetworkUuid())),
                    studyServerExecutionService.runAsync(() -> networkStoreService.deleteNetwork(deleteStudyInfos.getNetworkUuid()))
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
                                              ShortCircuitParametersEntity shortCircuitParametersEntity, UUID importReportUuid) {
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertStudyEntity(
                studyUuid, userId, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), caseFormat, caseUuid, caseName, loadFlowParameters, importReportUuid, shortCircuitParametersEntity));
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    @Transactional
    public CreatedStudyBasicInfos insertDuplicatedStudy(BasicStudyInfos studyInfos, StudyEntity sourceStudy, LoadFlowParametersEntity newLoadFlowParameters, ShortCircuitParametersEntity newShortCircuitParameters, String userId, UUID clonedNetworkUuid) {
        Objects.requireNonNull(studyInfos.getId());
        Objects.requireNonNull(userId);
        Objects.requireNonNull(clonedNetworkUuid);
        Objects.requireNonNull(sourceStudy.getNetworkId());
        Objects.requireNonNull(sourceStudy.getCaseFormat());
        Objects.requireNonNull(sourceStudy.getCaseUuid());
        Objects.requireNonNull(newLoadFlowParameters);

        UUID reportUuid = UUID.randomUUID();
        StudyEntity studyEntity = new StudyEntity(studyInfos.getId(), clonedNetworkUuid, sourceStudy.getNetworkId(), sourceStudy.getCaseFormat(),
                sourceStudy.getCaseUuid(), sourceStudy.getCaseName(), sourceStudy.getLoadFlowProvider(), sourceStudy.getSecurityAnalysisProvider(),
                sourceStudy.getSensitivityAnalysisProvider(), newLoadFlowParameters, newShortCircuitParameters);
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertDuplicatedStudy(studyEntity, sourceStudy.getId(), reportUuid));

        studyInfosService.add(createdStudyBasicInfos);
        notificationService.emitStudiesChanged(studyInfos.getId(), userId);

        return createdStudyBasicInfos;
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

    public String getSubstationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "substations");
    }

    public String getSubstationMapData(UUID studyUuid, UUID nodeUuid, String substationId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "substations", substationId);
    }

    public String getLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "lines");
    }

    public String getLineMapData(UUID studyUuid, UUID nodeUuid, String lineId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "lines", lineId);
    }

    public String getTwoWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "2-windings-transformers");
    }

    public String getTwoWindingsTransformerMapData(UUID studyUuid, UUID nodeUuid, String twoWindingsTransformerId,
                                                   boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "2-windings-transformers", twoWindingsTransformerId);
    }

    public String getThreeWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "3-windings-transformers");
    }

    public String getGeneratorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "generators");
    }

    public String getGeneratorMapData(UUID studyUuid, UUID nodeUuid, String generatorId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "generators", generatorId);
    }

    public String getBatteriesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "batteries");
    }

    public String getDanglingLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "dangling-lines");
    }

    public String getHvdcLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "hvdc-lines");
    }

    public String getLccConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "lcc-converter-stations");
    }

    public String getVscConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "vsc-converter-stations");
    }

    public String getLoadsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds,
                                  boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "loads");
    }

    public String getLoadMapData(UUID studyUuid, UUID nodeUuid, String loadId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "loads", loadId);
    }

    public String getShuntCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "shunt-compensators");
    }

    public String getShuntCompensatorMapData(UUID studyUuid, UUID nodeUuid, String shuntCompensatorId,
                                             boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "shunt-compensators", shuntCompensatorId);
    }

    public String getStaticVarCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "static-var-compensators");
    }

    public String getVoltageLevelMapData(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
                                         boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "voltage-levels", voltageLevelId);
    }

    public String getVoltageLevelsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "voltage-levels");
    }

    public String getVoltageLevelsAndEquipment(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, inUpstreamBuiltParentNode);
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "voltage-levels-equipments");
    }

    public String getAllMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "all");
    }

    public void runLoadFlow(UUID studyUuid, UUID nodeUuid) {
        String provider = getLoadFlowProvider(studyUuid);
        LoadFlowParameters loadflowParameters = getLoadFlowParameters(studyUuid);

        loadflowService.runLoadFlow(studyUuid, nodeUuid, loadflowParameters, provider);
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
            if (networkModificationTreeService.getBuildStatus(node.getIdNode()) == BuildStatus.BUILDING) {
                throw new StudyException(NOT_ALLOWED, "No modification is allowed during a node building.");
            }
        });
    }

    public void assertRootNodeOrBuiltNode(UUID studyUuid, UUID nodeUuid) {
        if (!(networkModificationTreeService.getStudyRootNodeUuid(studyUuid).equals(nodeUuid)
                || networkModificationTreeService.getBuildStatus(nodeUuid) == BuildStatus.BUILT)) {
            throw new StudyException(NODE_NOT_BUILT);
        }
    }

    public LoadFlowParameters getLoadFlowParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> LoadflowService.fromEntity(studyEntity.getLoadFlowParameters()))
                .orElse(null);
    }

    @Transactional
    public void setLoadFlowParameters(UUID studyUuid, LoadFlowParameters parameters, String userId) {
        updateLoadFlowParameters(studyUuid, LoadflowService.toEntity(parameters != null ? parameters : LoadFlowParameters.load()));
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
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
    public UUID runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, String parameters, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(parameters);
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

        SecurityAnalysisParameters securityAnalysisParameters = SecurityAnalysisParameters.load();
        if (StringUtils.isEmpty(parameters)) {
            LoadFlowParameters loadFlowParameters = getLoadFlowParameters(studyUuid);
            securityAnalysisParameters.setLoadFlowParameters(loadFlowParameters);
        } else {
            try {
                securityAnalysisParameters = objectMapper.readValue(parameters, SecurityAnalysisParameters.class);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        UUID result = securityAnalysisService.runSecurityAnalysis(networkUuid, reportUuid, nodeUuid, variantId, provider, contingencyListNames, securityAnalysisParameters, receiver);

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
            return singleLineDiagramService.getNeworkAreaDiagram(networkUuid, variantId, voltageLevelsIds, depth);
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

    private StudyEntity insertStudyEntity(UUID uuid, String userId, UUID networkUuid, String networkId,
                                          String caseFormat, UUID caseUuid, String caseName, LoadFlowParametersEntity loadFlowParameters,
                                          UUID importReportUuid, ShortCircuitParametersEntity shortCircuitParameters) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowParameters);
        Objects.requireNonNull(shortCircuitParameters);

        StudyEntity studyEntity = new StudyEntity(uuid, networkUuid, networkId, caseFormat, caseUuid, caseName, defaultLoadflowProvider,
                defaultSecurityAnalysisProvider, defaultSensitivityAnalysisProvider, loadFlowParameters, shortCircuitParameters);
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

    public void createNetworkModification(UUID studyUuid, String createModificationAttributes, UUID nodeUuid, String userId) {
        ModificationType modificationType = getModificationType(createModificationAttributes);
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();
            List<ModificationInfos> modificationInfosList = networkModificationService
                    .createModification(studyUuid, createModificationAttributes, groupUuid, variantId, reportUuid, nodeInfos.getId().toString());
            updateStatuses(studyUuid, nodeUuid, modificationInfosList, modificationType);
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

    public List<VoltageLevelInfos> getVoltageLevels(UUID studyUuid, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        List<VoltageLevelMapData> voltageLevelsMapData = networkMapService.getVoltageLevelMapData(networkUuid, variantId);

        return voltageLevelsMapData != null ?
                voltageLevelsMapData.stream().map(e -> VoltageLevelInfos.builder().id(e.getId()).name(e.getName())
                        .substationId(e.getSubstationId()).build()).collect(Collectors.toList())
                : null;
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

    @Transactional(readOnly = true)
    public UUID getStudyUuidFromNodeUuid(UUID nodeUuid) {
        return networkModificationTreeService.getStudyUuidForNodeId(nodeUuid);
    }

    public LoadFlowInfos getLoadFlowInfos(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        return networkModificationTreeService.getLoadFlowInfos(nodeUuid);
    }

    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(nodeUuid);
        networkModificationTreeService.updateBuildStatus(nodeUuid, BuildStatus.BUILDING);
        reportService.deleteReport(buildInfos.getReportUuid());

        try {
            networkModificationService.buildNode(studyUuid, nodeUuid, buildInfos);
        } catch (Exception e) {
            networkModificationTreeService.updateBuildStatus(nodeUuid, BuildStatus.NOT_BUILT);
            throw new StudyException(NODE_BUILD_ERROR, e.getMessage());
        }

    }

    public void stopBuild(@NonNull UUID nodeUuid) {
        networkModificationService.stopBuild(nodeUuid);
    }

    @Transactional
    public void duplicateStudyNode(UUID studyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        checkStudyContainsNode(studyUuid, nodeToCopyUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
        boolean invalidateBuild = !EMPTY_ARRAY.equals(networkModificationTreeService.getNetworkModifications(nodeToCopyUuid));
        updateStatuses(studyUuid, duplicatedNodeUuid, true, invalidateBuild);
        notificationService.emitElementUpdated(studyUuid, userId);
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
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getReportUuids().forEach(reportService::deleteReport)),  // TODO delete all with one request only
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getDynamicSimulationResultUuids().forEach(dynamicSimulationService::deleteResult)),
                studyServerExecutionService.runAsync(() ->  networkStoreService.deleteVariants(invalidateNodeInfos.getNetworkUuid(), invalidateNodeInfos.getVariantIds()))
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
        boolean invalidateChildrenBuild = !EMPTY_ARRAY.equals(networkModificationTreeService.getNetworkModifications(nodeId));
        List<NodeEntity> childrenNodes = networkModificationTreeService.getChildrenByParentUuid(nodeId);
        networkModificationTreeService.doDeleteNode(studyUuid, nodeId, deleteChildren, deleteNodeInfos);

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getModificationGroupUuids().forEach(networkModificationService::deleteModifications)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getReportUuids().forEach(reportService::deleteReport)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getDynamicSimulationResultUuids().forEach(dynamicSimulationService::deleteResult)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getShortCircuitAnalysisResultUuids().forEach(shortCircuitService::deleteShortCircuitAnalysisResult)),
                studyServerExecutionService.runAsync(() ->  networkStoreService.deleteVariants(deleteNodeInfos.getNetworkUuid(), deleteNodeInfos.getVariantIds()))
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

    public void reindexStudy(UUID studyUuid) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        if (studyEntity.isPresent()) {
            StudyEntity study = studyEntity.get();

            CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study);
            UUID networkUuid = study.getNetworkUuid();

            // reindex study in elasticsearch
            studyInfosService.recreateStudyInfos(studyInfos);

            try {
                networkConversionService.reindexStudyNetworkEquipments(networkUuid);
            } catch (HttpStatusCodeException e) {
                LOGGER.error(e.toString(), e);
                throw e;
            }
            invalidateBuild(studyUuid, networkModificationTreeService.getStudyRootNodeUuid(studyUuid), false, false);
            LOGGER.info("Study with id = '{}' has been reindexed", studyUuid);
        } else {
            throw new StudyException(STUDY_NOT_FOUND);
        }
    }

    @Transactional
    public String moveModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationUuidList, UUID beforeUuid, String userId) {
        String modificationsInError;
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
            modificationsInError = networkModificationService.moveModifications(originGroupUuid, modificationUuidList, beforeUuid, networkUuid, nodeInfos, buildTargetNode);
            if (!targetNodeBelongsToSourceNodeSubTree) {
                // invalidate the whole subtree except maybe the target node itself (depends if we have built this node during the move)
                updateStatuses(studyUuid, targetNodeUuid, buildTargetNode, true);
            }
            if (moveBetweenNodes) {
                // invalidate the whole subtree including the source node
                updateStatuses(studyUuid, originNodeUuid, false, true);
            }
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, targetNodeUuid, childrenUuids);
            if (moveBetweenNodes) {
                notificationService.emitEndModificationEquipmentNotification(studyUuid, originNodeUuid, originNodeChildrenUuids);
            }
        }
        notificationService.emitElementUpdated(studyUuid, userId);

        return modificationsInError;
    }

    @Transactional
    public String duplicateModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationUuidList, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildren(nodeUuid);
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        String response;
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            response = networkModificationService.duplicateModification(modificationUuidList, networkUuid, nodeInfos);
            // invalidate the whole subtree except the target node (we have built this node during the duplication)
            updateStatuses(studyUuid, nodeUuid, true, true);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
        return response;
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
            Optional<UUID> parentUuid =  networkModificationTreeService.getParentNodeUuid(UUID.fromString(subReporters.get(0).getTaskKey()));
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

    private Set<String> getSubstationIds(List<ModificationInfos> modificationInfosList) {
        return modificationInfosList.stream().flatMap(modification -> modification.getSubstationIds().stream())
                .collect(Collectors.toSet());
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid, List<ModificationInfos> modifications, ModificationType modificationType) {
        switch (modificationType) {
            case EQUIPMENT_ATTRIBUTE_MODIFICATION: {
                // for now only one sub type (switch), but after need to extract the equipment type to select the right update type
                Set<String> substationIds = getSubstationIds(modifications);
                notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
                updateStatuses(studyUuid, nodeUuid);
                notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SWITCH);
                break;
            }
            case BRANCH_STATUS_MODIFICATION: {
                Set<String> substationIds = getSubstationIds(modifications);
                notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
                updateStatuses(studyUuid, nodeUuid);
                notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LINE);
                break;
            }
            case EQUIPMENT_DELETION: {
                modifications.stream()
                    .map(EquipmentDeletionInfos.class::cast)
                    .forEach(deletionInfo ->
                        notificationService.emitStudyEquipmentDeleted(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, deletionInfo.getSubstationIds(),
                            deletionInfo.getEquipmentType(), deletionInfo.getEquipmentId()));
                updateStatuses(studyUuid, nodeUuid);
                break;
            }
            case LINE_SPLIT_WITH_VOLTAGE_LEVEL: {
                Set<String> allImpactedSubstationIds = modifications.stream()
                        .map(ModificationInfos::getSubstationIds).flatMap(Set::stream).collect(Collectors.toSet());
                List<EquipmentModificationInfos> deletions = modifications.stream()
                        .filter(modif -> modif.getType() == ModificationType.EQUIPMENT_DELETION)
                        .map(EquipmentDeletionInfos.class::cast)
                        .collect(Collectors.toList());
                deletions.forEach(modif -> notificationService.emitStudyEquipmentDeleted(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY,
                        allImpactedSubstationIds, modif.getEquipmentType(), modif.getEquipmentId()));
                updateStatuses(studyUuid, nodeUuid);
                break;
            }
            default: {
                Set<String> substationIds = getSubstationIds(modifications);
                notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
                updateStatuses(studyUuid, nodeUuid);
                break;
            }
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
                sensitivityAnalysisParameters.setLoadFlowParameters(loadFlowParameters);
                sensitivityAnalysisInputData.setParameters(sensitivityAnalysisParameters);
            }
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID result = sensitivityAnalysisService.runSensitivityAnalysis(nodeUuid, networkUuid, variantId, reportUuid, provider, sensitivityAnalysisInputData);

        updateSensitivityAnalysisResultUuid(nodeUuid, result);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        return result;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid) {
        ShortCircuitParameters shortCircuitParameters = getShortCircuitParameters(studyUuid);
        UUID result =  shortCircuitService.runShortCircuit(studyUuid, nodeUuid, shortCircuitParameters);

        updateShortCircuitAnalysisResultUuid(nodeUuid, result);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        return result;
    }

    public String getMapEquipments(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn), substationsIds, "map-equipments");
    }

    private ModificationType getModificationType(String modificationAttributes) {
        try {
            return objectMapper.readValue(modificationAttributes, ModificationInfos.class).getType();
        } catch (InvalidTypeIdException e) {
            throw new StudyException(BAD_MODIFICATION_TYPE, e.getMessage());
        } catch (JsonProcessingException e) {
            throw new StudyException(BAD_JSON_FORMAT, e.getMessage());
        }
    }

    public List<MappingInfos> getDynamicSimulationMappings(UUID nodeUuid) {
        // get mapping from node uuid
        List<MappingInfos> mappings = dynamicSimulationService.getMappings(nodeUuid);

        return mappings;
    }

    @Transactional
    public UUID runDynamicSimulation(UUID studyUuid, UUID nodeUuid, String parameters, String mappingName) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(mappingName);

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

        // destructured parameters
        DynamicSimulationParametersInfos dynamicSimulationParameters;
        try {
            dynamicSimulationParameters = objectMapper.readValue(parameters, DynamicSimulationParametersInfos.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        // clean previous result if exist
        Optional<UUID> prevResultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);
        prevResultUuidOpt.ifPresent(dynamicSimulationService::deleteResult);

        // launch dynamic simulation
        UUID resultUuid = dynamicSimulationService.runDynamicSimulation(receiver, networkUuid, "", dynamicSimulationParameters.getStartTime(), dynamicSimulationParameters.getStopTime(), mappingName);

        // update result uuid and notification
        updateDynamicSimulationResultUuid(nodeUuid, resultUuid);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);

        return resultUuid;
    }

    public String getDynamicSimulationTimeSeries(UUID nodeUuid) {
        // get timeseries from node uuid
        List<TimeSeries> timeseries = dynamicSimulationService.getTimeSeriesResult(nodeUuid);

        return TimeSeries.toJson(timeseries);
    }

    public String getDynamicSimulationTimeLine(UUID nodeUuid) {
        // get timeline from node uuid
        List<TimeSeries> timeline = dynamicSimulationService.getTimeLineResult(nodeUuid);

        return TimeSeries.toJson(timeline); // timeline has only one element
    }

    public String getDynamicSimulationStatus(UUID nodeUuid) {
        return dynamicSimulationService.getStatus(nodeUuid);
    }
}
