/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.model.VariantInfos;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.EquipmentDeletionInfos;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.elasticsearch.EquipmentInfosServiceImpl.EQUIPMENT_TYPE_SCORES;

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

    @Autowired
    NotificationService notificationService;

    NetworkModificationTreeService networkModificationTreeService;

    StudyServerExecutionService studyServerExecutionService;

    private String defaultLoadflowProvider;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;
    private final LoadflowService loadflowService;
    private final ShortCircuitService shortCircuitService;
    private final CaseService caseService;
    private final SingleLineDiagramService singleLineDiagramService;
    private final NetworkConversionService networkConversionService;
    private final GeoDataService geoDataService;
    private final NetworkMapService networkMapService;
    private final SecurityAnalysisService securityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final ActionsService actionsService;

    private final ObjectMapper objectMapper;

    @Autowired
    StudyService self;

    @Autowired
    public StudyService(
            @Value("${loadflow.default-provider}") String defaultLoadflowProvider,
            StudyRepository studyRepository,
            StudyCreationRequestRepository studyCreationRequestRepository,
            NetworkService networkStoreService,
            NetworkModificationService networkModificationService,
            ReportService reportService,
            @Lazy StudyInfosService studyInfosService,
            @Lazy EquipmentInfosService equipmentInfosService,
            NetworkModificationTreeService networkModificationTreeService,
            ObjectMapper objectMapper,
            StudyServerExecutionService studyServerExecutionService,
            LoadflowService loadflowService,
            ShortCircuitService shortCircuitService,
            CaseService caseService,
            SingleLineDiagramService singleLineDiagramService,
            NetworkConversionService networkConversionService,
            GeoDataService geoDataService,
            NetworkMapService networkMapService,
            SecurityAnalysisService securityAnalysisService,
            ActionsService actionsService,
            SensitivityAnalysisService sensitivityAnalysisService) {
        this.studyRepository = studyRepository;
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.networkStoreService = networkStoreService;
        this.networkModificationService = networkModificationService;
        this.reportService = reportService;
        this.studyInfosService = studyInfosService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.defaultLoadflowProvider = defaultLoadflowProvider;
        this.objectMapper = objectMapper;
        this.studyServerExecutionService = studyServerExecutionService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.loadflowService = loadflowService;
        this.shortCircuitService = shortCircuitService;
        this.caseService = caseService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.networkConversionService = networkConversionService;
        this.geoDataService = geoDataService;
        this.networkMapService = networkMapService;
        this.securityAnalysisService = securityAnalysisService;
        this.actionsService = actionsService;
    }

    private static StudyInfos toStudyInfos(StudyEntity entity) {
        return StudyInfos.builder()
                .id(entity.getId())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .userId(entity.getUserId())
                .caseFormat(entity.getCaseFormat())
                .build();
    }

    private static BasicStudyInfos toBasicStudyInfos(StudyCreationRequestEntity entity) {
        return BasicStudyInfos.builder()
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .userId(entity.getUserId())
                .id(entity.getId())
                .build();
    }

    private static CreatedStudyBasicInfos toCreatedStudyBasicInfos(StudyEntity entity) {
        return CreatedStudyBasicInfos.builder()
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .userId(entity.getUserId())
                .id(entity.getId())
                .caseFormat(entity.getCaseFormat())
                .build();
    }

    public List<CreatedStudyBasicInfos> getStudies() {
        return studyRepository.findAll().stream()
                .map(StudyService::toCreatedStudyBasicInfos)
                .sorted(Comparator.comparing(CreatedStudyBasicInfos::getCreationDate).reversed())
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
                .sorted(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed()).collect(Collectors.toList());
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

    public BasicStudyInfos createStudy(MultipartFile caseFile, String userId, UUID studyUuid) {
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        try {
            createStudyFromFile(caseFile, userId, basicStudyInfos);
        } catch (Exception e) {
            self.deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            throw e;
        }
        return basicStudyInfos;
    }

    private void createStudyFromFile(MultipartFile caseFile, String userId, BasicStudyInfos basicStudyInfos) {
        UUID importReportUuid = UUID.randomUUID();
        UUID caseUuid = caseService.importCase(caseFile);
        if (caseUuid != null) {
            persistentStoreWithNotificationOnError(caseUuid, basicStudyInfos.getId(), userId, importReportUuid, null);
        }
    }

    public BasicStudyInfos createStudy(UUID sourceStudyUuid, UUID studyUuid, String userId) {
        Objects.requireNonNull(sourceStudyUuid);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElse(null);
        if (sourceStudy == null) {
            return null;
        }
        LoadFlowParameters sourceLoadFlowParameters = LoadflowService.fromEntity(sourceStudy.getLoadFlowParameters());
        ShortCircuitParameters copiedShortCircuitParameters = ShortCircuitAnalysisService.fromEntity(sourceStudy.getShortCircuitParameters());

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
            ShortCircuitParameters shortCircuitParameters = copiedShortCircuitParameters != null ? copiedShortCircuitParameters : ShortCircuitAnalysisService.getDefaultShortCircuitParamters();
            insertDuplicatedStudy(basicStudyInfos, sourceStudy, LoadflowService.toEntity(newLoadFlowParameters), ShortCircuitAnalysisService.toEntity(shortCircuitParameters), userId, clonedNetworkUuid);
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

    public List<EquipmentInfos> searchEquipments(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull String userInput,
                                                 @NonNull EquipmentInfosService.FieldSelector fieldSelector, String equipmentType,
                                                 boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
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

        FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionsForScoreQueries = new FunctionScoreQueryBuilder.FilterFunctionBuilder[ EQUIPMENT_TYPE_SCORES.size() + 1 ];

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
        UUID networkUuid = null;
        List<NodeModificationInfos> nodesModificationInfos = new ArrayList<>();
        if (studyCreationRequestEntity.isEmpty()) {
            networkUuid = networkStoreService.doGetNetworkUuid(studyUuid);
            nodesModificationInfos = networkModificationTreeService.getAllNodesModificationInfos(studyUuid);
            studyRepository.findById(studyUuid).ifPresent(s -> {
                networkModificationTreeService.doDeleteTree(studyUuid);
                studyRepository.deleteById(studyUuid);
                studyInfosService.deleteByUuid(studyUuid);
            });
        } else {
            studyCreationRequestRepository.deleteById(studyCreationRequestEntity.get().getId());
        }
        notificationService.emitStudyDelete(studyUuid, userId);

        return networkUuid != null ? Optional.of(new DeleteStudyInfos(networkUuid, nodesModificationInfos)) : Optional.empty();
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
                                              UUID caseUuid, boolean casePrivate, String caseName, LoadFlowParametersEntity loadFlowParameters,
                                              ShortCircuitParametersEntity shortCircuitParametersEntity, UUID importReportUuid) {
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertStudyEntity(
                studyUuid, userId, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), caseFormat, caseUuid, casePrivate, caseName, loadFlowParameters, importReportUuid, shortCircuitParametersEntity));
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
        StudyEntity studyEntity = new StudyEntity(studyInfos.getId(), userId, LocalDateTime.now(ZoneOffset.UTC), clonedNetworkUuid, sourceStudy.getNetworkId(), sourceStudy.getCaseFormat(), sourceStudy.getCaseUuid(), sourceStudy.isCasePrivate(), sourceStudy.getCaseName(), sourceStudy.getLoadFlowProvider(), newLoadFlowParameters, newShortCircuitParameters);
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertDuplicatedStudy(studyEntity, sourceStudy.getId(), reportUuid));

        studyInfosService.add(createdStudyBasicInfos);
        notificationService.emitStudiesChanged(studyInfos.getId(), userId);

        return createdStudyBasicInfos;
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(userId, studyUuid);
        notificationService.emitStudiesChanged(newStudy.getId(), userId);
        return newStudy;
    }

    private StudyException handleStudyCreationError(HttpStatusCodeException httpException, String serverName) {
        HttpStatus httpStatusCode = httpException.getStatusCode();
        String errorMessage = httpException.getResponseBodyAsString();
        String errorToParse = errorMessage.isEmpty() ? "{\"message\": \"" + serverName + ": " + httpStatusCode + "\"}"
                : errorMessage;

        LOGGER.error(errorToParse, httpException);

        try {
            JsonNode node = new ObjectMapper().readTree(errorToParse).path("message");
            if (!node.isMissingNode()) {
                return new StudyException(STUDY_CREATION_FAILED, node.asText());
            }
        } catch (JsonProcessingException e) {
            if (!errorToParse.isEmpty()) {
                return new StudyException(STUDY_CREATION_FAILED, errorToParse);
            }
        }

        return new StudyException(STUDY_CREATION_FAILED, errorToParse);
    }

    public byte[] getVoltageLevelSvg(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
                                     UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return singleLineDiagramService.getVoltageLevelSvg(networkUuid, variantId, voltageLevelId, diagramParameters);
    }

    public String getVoltageLevelSvgAndMetadata(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
                                                UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return singleLineDiagramService.getVoltageLevelSvgAndMetadata(networkUuid, variantId, voltageLevelId, diagramParameters);
    }

    private void persistentStoreWithNotificationOnError(UUID caseUuid, UUID studyUuid, String userId, UUID importReportUuid, Map<String, Object> importParameters) {
        try {
            networkConversionService.persistentStore(caseUuid, studyUuid, userId, importReportUuid, importParameters);
        } catch (HttpStatusCodeException e) {
            throw handleStudyCreationError(e, "network-conversion-server");
        } catch (Exception e) {
            throw new StudyException(STUDY_CREATION_FAILED, e.getMessage());
        }
    }

    public String getLinesGraphics(UUID networkUuid, UUID nodeUuid) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return geoDataService.getLinesGraphics(networkUuid, variantId);
    }

    public String getSubstationsGraphics(UUID networkUuid, UUID nodeUuid) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return geoDataService.getSubstationsGraphics(networkUuid, variantId);
    }

    public String getSubstationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "substations");
    }

    public String getSubstationMapData(UUID studyUuid, UUID nodeUuid, String substationId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "substations", substationId);
    }

    public String getLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "lines");
    }

    public String getLineMapData(UUID studyUuid, UUID nodeUuid, String lineId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "lines", lineId);
    }

    public String getTwoWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "2-windings-transformers");
    }

    public String getTwoWindingsTransformerMapData(UUID studyUuid, UUID nodeUuid, String twoWindingsTransformerId,
                                                   boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "2-windings-transformers", twoWindingsTransformerId);
    }

    public String getThreeWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "3-windings-transformers");
    }

    public String getGeneratorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "generators");
    }

    public String getGeneratorMapData(UUID studyUuid, UUID nodeUuid, String generatorId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "generators", generatorId);
    }

    public String getBatteriesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "batteries");
    }

    public String getDanglingLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "dangling-lines");
    }

    public String getHvdcLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "hvdc-lines");
    }

    public String getLccConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "lcc-converter-stations");
    }

    public String getVscConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "vsc-converter-stations");
    }

    public String getLoadsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds,
                                  boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "loads");
    }

    public String getLoadMapData(UUID studyUuid, UUID nodeUuid, String loadId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "loads", loadId);
    }

    public String getShuntCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "shunt-compensators");
    }

    public String getShuntCompensatorMapData(UUID studyUuid, UUID nodeUuid, String shuntCompensatorId,
                                             boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "shunt-compensators", shuntCompensatorId);
    }

    public String getStaticVarCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "static-var-compensators");
    }

    public String getVoltageLevelMapData(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
                                         boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                "voltage-levels", voltageLevelId);
    }

    public String getVoltageLevelsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "voltage-levels");
    }

    public String getVoltageLevelsAndEquipment(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn),
                substationsIds, "voltage-levels-equipments");
    }

    public String getAllMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return networkMapService.getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), networkModificationTreeService.getVariantId(nodeUuid),
                substationsIds, "all");
    }

    public void changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            List<EquipmentModificationInfos> equipmentModificationsInfos = networkModificationService
                    .changeSwitchState(studyUuid, switchId, open, groupUuid, variantId, reportUuid);
            Set<String> substationIds = getSubstationIds(equipmentModificationsInfos);

            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
            updateStatuses(studyUuid, nodeUuid);
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SWITCH);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    public void applyGroovyScript(UUID studyUuid, String groovyScript, UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            List<ModificationInfos> modificationsInfos = networkModificationService.applyGroovyScript(studyUuid,
                    groovyScript, groupUuid, variantId, reportUuid);

            Set<String> substationIds = getSubstationIds(modificationsInfos);

            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
            updateStatuses(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
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

    public void changeLineStatus(@NonNull UUID studyUuid, @NonNull String lineId, @NonNull String status,
                                 @NonNull UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            List<ModificationInfos> modificationInfosList = networkModificationService.changeLineStatus(studyUuid, lineId,
                    status, groupUuid, variantId, reportUuid);

            Set<String> substationIds = getSubstationIds(modificationInfosList);

            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
            updateStatuses(studyUuid, nodeUuid);
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LINE);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
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

    public void assertComputationNotRunning(UUID nodeUuid) {
        assertLoadFlowNotRunning(nodeUuid);
        securityAnalysisService.assertSecurityAnalysisNotRunning(nodeUuid);
        sensitivityAnalysisService.assertSensitivityAnalysisNotRunning(nodeUuid);
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

    public void assertNoNodeIsBuilding(UUID studyUuid) {
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
    public void setLoadFlowParameters(UUID studyUuid, LoadFlowParameters parameters) {
        updateLoadFlowParameters(studyUuid, LoadflowService.toEntity(parameters != null ? parameters : LoadFlowParameters.load()));
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        invalidateSensitivityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
    }

    public void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE);
    }

    public String getLoadFlowProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(StudyEntity::getLoadFlowProvider)
                .orElse("");
    }

    @Transactional
    public void updateLoadFlowProvider(UUID studyUuid, String provider) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setLoadFlowProvider(provider != null ? provider : defaultLoadflowProvider));
        networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
    }

    public ShortCircuitParameters getShortCircuitParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> ShortCircuitAnalysisService.fromEntity(studyEntity.getShortCircuitParameters()))
                .orElse(null);
    }

    @Transactional
    public void setShortCircuitParameters(UUID studyUuid, ShortCircuitParameters parameters) {
        updateShortCircuitParameters(studyUuid, ShortCircuitAnalysisService.toEntity(parameters != null ? parameters : ShortCircuitAnalysisService.getDefaultShortCircuitParamters()));
    }

    @Transactional
    public UUID runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, String parameters,
                                    UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getLoadFlowProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UUID result = securityAnalysisService.runSecurityAnalysis(networkUuid, reportUuid, variantId, provider, contingencyListNames, parameters, receiver);

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

        return singleLineDiagramService.getSubstationSvg(networkUuid, variantId, substationId, diagramParameters, substationLayout);
    }

    public String getSubstationSvgAndMetadata(UUID studyUuid, String substationId, DiagramParameters diagramParameters,
                                              String substationLayout, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return singleLineDiagramService.getSubstationSvgAndMetadata(networkUuid, variantId, substationId, diagramParameters, substationLayout);
    }

    public String getNeworkAreaDiagram(UUID studyUuid, UUID nodeUuid, List<String> voltageLevelsIds, int depth) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);

        return singleLineDiagramService.getNeworkAreaDiagram(networkUuid, variantId, voltageLevelsIds, depth);
    }

    public void invalidateSecurityAnalysisStatus(UUID nodeUuid) {
        securityAnalysisService.invalidateSaStatus(networkModificationTreeService.getSecurityAnalysisResultUuidsFromNode(nodeUuid));
    }

    public void invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        securityAnalysisService.invalidateSaStatus(networkModificationTreeService.getStudySecurityAnalysisResultUuids(studyUuid));
    }

    public void invalidateSensitivityAnalysisStatusOnAllNodes(UUID studyUuid) {
        sensitivityAnalysisService.invalidateSensitivityAnalysisStatus(networkModificationTreeService.getStudySensitivityAnalysisResultUuids(studyUuid));
    }

    private StudyEntity insertStudyEntity(UUID uuid, String userId, UUID networkUuid, String networkId,
                                          String caseFormat, UUID caseUuid, boolean casePrivate, String caseName, LoadFlowParametersEntity loadFlowParameters,
                                          UUID importReportUuid, ShortCircuitParametersEntity shortCircuitParameters) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowParameters);
        Objects.requireNonNull(shortCircuitParameters);

        StudyEntity studyEntity = new StudyEntity(uuid, userId, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, caseFormat, caseUuid, casePrivate, caseName, defaultLoadflowProvider, loadFlowParameters, shortCircuitParameters);
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

    void updateSensitivityAnalysisResultUuid(UUID nodeUuid, UUID sensitivityAnalysisResultUuid) {
        networkModificationTreeService.updateSensitivityAnalysisResultUuid(nodeUuid, sensitivityAnalysisResultUuid);
    }

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(String userId, UUID studyUuid) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid, userId, LocalDateTime.now(ZoneOffset.UTC));
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

    public void createEquipment(UUID studyUuid, String createEquipmentAttributes, ModificationType modificationType,
                                UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();
            List<EquipmentModificationInfos> equipmentModificationInfosList = networkModificationService
                    .createEquipment(studyUuid, createEquipmentAttributes, groupUuid, modificationType, variantId, reportUuid);
            Set<String> substationIds = getSubstationIds(equipmentModificationInfosList);
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
            updateStatuses(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    public void modifyEquipment(UUID studyUuid, String modifyEquipmentAttributes, ModificationType modificationType,
                                UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            List<EquipmentModificationInfos> equipmentModificationInfosList = networkModificationService
                    .modifyEquipment(studyUuid, modifyEquipmentAttributes, groupUuid, modificationType, variantId, reportUuid);
            Set<String> substationIds = getSubstationIds(equipmentModificationInfosList);

            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, substationIds);
            updateStatuses(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    public void updateEquipmentCreation(UUID studyUuid, String createEquipmentAttributes,
                                        ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            networkModificationService.updateEquipmentCreation(createEquipmentAttributes, modificationType,
                    modificationUuid);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    public void updateEquipmentModification(UUID studyUuid, String modifyEquipmentAttributes, ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            networkModificationService.updateEquipmentModification(modifyEquipmentAttributes, modificationType, modificationUuid);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    public void deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            List<EquipmentDeletionInfos> equipmentDeletionInfosList = networkModificationService.deleteEquipment(studyUuid,
                    equipmentType, equipmentId, groupUuid, variantId, reportUuid);

            equipmentDeletionInfosList.forEach(deletionInfo ->
                    notificationService.emitStudyEquipmentDeleted(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY, deletionInfo.getSubstationIds(),
                            deletionInfo.getEquipmentType(), deletionInfo.getEquipmentId())
            );
            updateStatuses(studyUuid, nodeUuid);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
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
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuidToSearchIn, voltageLevelId, "configured-buses");
    }

    public List<IdentifiableInfos> getVoltageLevelBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
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

    private BuildInfos getBuildInfos(UUID nodeUuid) {
        return networkModificationTreeService.getBuildInfos(nodeUuid);
    }

    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        BuildInfos buildInfos = getBuildInfos(nodeUuid);
        networkModificationTreeService.updateBuildStatus(nodeUuid, BuildStatus.BUILDING);
        buildInfos.getReportUuids().forEach(reportService::deleteReport);

        try {
            networkModificationService.buildNode(studyUuid, nodeUuid, buildInfos);
        } catch (Exception e) {
            networkModificationTreeService.updateBuildStatus(nodeUuid, BuildStatus.NOT_BUILT);
            throw new StudyException(NODE_BUILD_ERROR, e.getMessage());
        }

    }

    public void stopBuild(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        networkModificationService.stopBuild(nodeUuid);
    }

    @Transactional
    public void duplicateStudyNode(UUID studyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode) {
        checkStudyContainsNode(studyUuid, nodeToCopyUuid);
        checkStudyContainsNode(studyUuid, referenceNodeUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.duplicateStudyNode(nodeToCopyUuid, referenceNodeUuid, insertMode);
        updateStatuses(studyUuid, duplicatedNodeUuid);
    }

    private void invalidateBuild(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        invalidateNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid));
        networkModificationTreeService.invalidateBuild(nodeUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos);

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getReportUuids().forEach(reportService::deleteReport)),  // TODO delete all with one request only
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
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
        invalidateBuild(studyUuid, nodeUuid, invalidateOnlyChildrenBuildStatus);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
    }

    @Transactional
    public void changeModificationActiveState(@NonNull UUID studyUuid, @NonNull UUID nodeUuid,
                                              @NonNull UUID modificationUuid, boolean active) {
        if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        networkModificationTreeService.handleExcludeModification(nodeUuid, modificationUuid, active);
        updateStatuses(studyUuid, nodeUuid, false);
    }

    @Transactional
    public void deleteModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationService.deleteModifications(groupId, modificationsUuids);
            networkModificationTreeService.removeModificationsToExclude(nodeUuid, modificationsUuids);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    @Transactional
    public void deleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        DeleteNodeInfos deleteNodeInfos = new DeleteNodeInfos();
        deleteNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid));
        networkModificationTreeService.doDeleteNode(studyUuid, nodeId, deleteChildren, deleteNodeInfos);

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getModificationGroupUuids().forEach(networkModificationService::deleteModifications)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getReportUuids().forEach(reportService::deleteReport)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getSecurityAnalysisResultUuids().forEach(securityAnalysisService::deleteSaResult)),
                studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getSensitivityAnalysisResultUuids().forEach(sensitivityAnalysisService::deleteSensitivityAnalysisResult)),
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
            invalidateBuild(studyUuid, networkModificationTreeService.getStudyRootNodeUuid(studyUuid), false);
            LOGGER.info("Study with id = '{}' has been reindexed", studyUuid);
        } else {
            throw new StudyException(STUDY_NOT_FOUND);
        }
    }

    @Transactional
    public void reorderModification(UUID studyUuid, UUID nodeUuid, UUID modificationUuid, UUID beforeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            List<UUID> modificationUuidList = Collections.singletonList(modificationUuid);
            networkModificationService.reorderModification(groupUuid, modificationUuidList, beforeUuid);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    @Transactional
    public String duplicateModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationUuidList) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        String response;
        try {
            checkStudyContainsNode(studyUuid, nodeUuid);
            UUID targetGroupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            response = networkModificationService.duplicateModification(targetGroupUuid, modificationUuidList);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
        return response;
    }

    private void checkStudyContainsNode(UUID studyUuid, UUID nodeUuid) {
        if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public List<Pair<UUID, String>> getReportUuidsAndNames(UUID nodeUuid, boolean nodeOnlyReport) {
        return networkModificationTreeService.getReportUuidsAndNames(nodeUuid, nodeOnlyReport);
    }

    public List<ReporterModel> getNodeReport(UUID nodeUuid, boolean nodeOnlyReport) {
        List<Pair<UUID, String>> reportUuidsAndNames = getReportUuidsAndNames(nodeUuid, nodeOnlyReport);
        return reportUuidsAndNames.stream().map(reportInfo -> {
            ReporterModel reporter = reportService.getReport(reportInfo.getLeft(), reportInfo.getRight());
            ReporterModel newReporter = new ReporterModel(reporter.getTaskKey(), reportInfo.getRight(), reporter.getTaskValues());
            reporter.getReports().forEach(newReporter::report);
            reporter.getSubReporters().forEach(newReporter::addSubReporter);
            return newReporter;
        }).collect(Collectors.toList());
    }

    public void deleteNodeReport(UUID nodeUuid) {
        reportService.deleteReport(networkModificationTreeService.getReportUuid(nodeUuid));
    }

    public String getDefaultLoadflowProviderValue() {
        return defaultLoadflowProvider;
    }

    private Set<String> getSubstationIds(List<? extends ModificationInfos> modificationInfosList) {
        return modificationInfosList.stream().flatMap(modification -> modification.getSubstationIds().stream())
                .collect(Collectors.toSet());
    }

    public void lineSplitWithVoltageLevel(UUID studyUuid, String lineSplitWithVoltageLevelAttributes,
                                          ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            Objects.requireNonNull(studyUuid);
            Objects.requireNonNull(lineSplitWithVoltageLevelAttributes);
            NodeModificationInfos nodeInfos = networkModificationTreeService.getNodeModificationInfos(nodeUuid);
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();
            List<EquipmentModificationInfos> modifications = List.of();
            if (modificationUuid == null) {
                modifications = networkModificationService.splitLineWithVoltageLevel(studyUuid, lineSplitWithVoltageLevelAttributes,
                        groupUuid, modificationType, variantId, reportUuid);
            } else {
                networkModificationService.updateLineSplitWithVoltageLevel(lineSplitWithVoltageLevelAttributes,
                        modificationType, modificationUuid);
            }
            Set<String> allImpactedSubstationIds = modifications.stream()
                    .map(ModificationInfos::getSubstationIds).flatMap(Set::stream).collect(Collectors.toSet());
            List<EquipmentModificationInfos> deletions = modifications.stream()
                    .filter(modif -> modif.getType() == ModificationType.EQUIPMENT_DELETION)
                    .collect(Collectors.toList());
            deletions.forEach(modif -> notificationService.emitStudyEquipmentDeleted(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STUDY,
                    allImpactedSubstationIds, modif.getEquipmentType(), modif.getEquipmentId()));
            updateStatuses(studyUuid, nodeUuid, modificationUuid == null);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
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
    public UUID runSensitivityAnalysis(UUID studyUuid,
                                       List<UUID> variablesFiltersListUuids,
                                       List<UUID> contingencyListUuids,
                                       List<UUID> branchFiltersListUuids,
                                       String parameters,
                                       UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(variablesFiltersListUuids);
        Objects.requireNonNull(contingencyListUuids);
        Objects.requireNonNull(branchFiltersListUuids);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getLoadFlowProvider(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);

        UUID result = sensitivityAnalysisService.runSensitivityAnalysis(nodeUuid, networkUuid, variantId, reportUuid, provider,
                variablesFiltersListUuids, contingencyListUuids, branchFiltersListUuids,
                parameters);

        updateSensitivityAnalysisResultUuid(nodeUuid, result);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        return result;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid) {
        //TODO
        String provider = "Provider";
        ShortCircuitParameters shortCircuitParameters = getShortCircuitParameters(studyUuid);
        return shortCircuitService.runShortCircuit(studyUuid, nodeUuid, shortCircuitParameters, provider);
    }

    public void invalidateShortCircuitStatusOnAllNodes(UUID studyUuid) {
        networkModificationTreeService.updateStudyShortCircuitStatus(studyUuid, ShortCircuitStatus.NOT_DONE);
    }
}
