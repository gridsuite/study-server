/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.model.VariantInfos;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.EquipmentDeletionInfos;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.gridsuite.study.server.StudyConstants.*;
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

    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";
    static final String QUERY_PARAM_COMPONENT_LIBRARY = "componentLibrary";
    static final String QUERY_PARAM_USE_NAME = "useName";
    static final String QUERY_PARAM_CENTER_LABEL = "centerLabel";
    static final String QUERY_PARAM_DIAGONAL_LABEL = "diagonalLabel";
    static final String QUERY_PARAM_TOPOLOGICAL_COLORING = "topologicalColoring";
    static final String QUERY_PARAM_SUBSTATION_LAYOUT = "substationLayout";
    static final String QUERY_PARAM_DEPTH = "depth";
    static final String QUERY_PARAM_VOLTAGE_LEVELS_IDS = "voltageLevelsIds";
    static final String RESULT_UUID = "resultUuid";

    static final String QUERY_PARAM_RECEIVER = "receiver";

    static final String HEADER_RECEIVER = "receiver";

    static final String FIRST_VARIANT_ID = "first_variant_id";

    static final String EQUIPMENT_NAME = "equipmentName.fullascii";

    static final String EQUIPMENT_ID = "equipmentId.keyword";

    @Autowired
    NotificationService notificationService;

    NetworkModificationTreeService networkModificationTreeService;

    StudyServerExecutionService studyServerExecutionService;

    @Autowired
    private RestTemplate restTemplate;
    private String caseServerBaseUri;
    private String singleLineDiagramServerBaseUri;
    private String networkConversionServerBaseUri;
    private String geoDataServerBaseUri;
    private String networkMapServerBaseUri;
    private String loadFlowServerBaseUri;
    private String securityAnalysisServerBaseUri;
    private String actionsServerBaseUri;
    private String defaultLoadflowProvider;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;

    private final ObjectMapper objectMapper;

    @Autowired
    private TempFileService tempFileService;

    @Bean
    @Transactional
    public Consumer<Message<String>> consumeSaResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Security analysis result '{}' available for node '{}'", resultUuid,
                            receiverObj.getNodeUuid());

                    // update DB
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);
                                // send notifications
                    UUID studyUuid = getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Autowired
    StudyService self;

    @Autowired
    public StudyService(@Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
        @Value("${backing-services.single-line-diagram.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
        @Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
        @Value("${backing-services.geo-data.base-uri:http://geo-data-server/}") String geoDataServerBaseUri,
        @Value("${backing-services.network-map.base-uri:http://network-map-server/}") String networkMapServerBaseUri,
        @Value("${backing-services.loadflow.base-uri:http://loadflow-server/}") String loadFlowServerBaseUri,
        @Value("${backing-services.security-analysis-server.base-uri:http://security-analysis-server/}") String securityAnalysisServerBaseUri,
        @Value("${backing-services.actions-server.base-uri:http://actions-server/}") String actionsServerBaseUri,
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
        StudyServerExecutionService studyServerExecutionService) {
        this.caseServerBaseUri = caseServerBaseUri;
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.geoDataServerBaseUri = geoDataServerBaseUri;
        this.networkMapServerBaseUri = networkMapServerBaseUri;
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
        this.actionsServerBaseUri = actionsServerBaseUri;
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

    public String getCaseName(UUID studyUuid) {
        StudyEntity study = studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/name")
                .buildAndExpand(study.getCaseUuid())
                .toUriString();

        try {
            return restTemplate.exchange(caseServerBaseUri + path, HttpMethod.GET, null, String.class, studyUuid).getBody();
        } catch (HttpStatusCodeException e) {
            throw new StudyException(CASE_NOT_FOUND, e.getMessage());
        }
    }

    public List<CreatedStudyBasicInfos> getStudiesMetadata(List<UUID> uuids) {
        return studyRepository.findAllById(uuids).stream().map(StudyService::toCreatedStudyBasicInfos)
                .collect(Collectors.toList());

    }

    List<BasicStudyInfos> getStudiesCreationRequests() {
        return studyCreationRequestRepository.findAll().stream()
                .map(StudyService::toBasicStudyInfos)
                .sorted(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed()).collect(Collectors.toList());
    }

    public BasicStudyInfos createStudy(UUID caseUuid, String userId, UUID studyUuid, Map<String, Object> importParameters) {
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        studyServerExecutionService.runAsync(() -> createStudyAsync(caseUuid, userId, basicStudyInfos, importParameters));
        return basicStudyInfos;
    }

    private void createStudyAsync(UUID caseUuid, String userId, BasicStudyInfos basicStudyInfos, Map<String, Object> importParameters) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        try {
            UUID importReportUuid = UUID.randomUUID();
            String caseFormat = getCaseFormat(caseUuid, basicStudyInfos.getId(), userId);
            NetworkInfos networkInfos = persistentStore(caseUuid, basicStudyInfos.getId(), userId, importReportUuid, importParameters);
            LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
            insertStudy(basicStudyInfos.getId(), userId, networkInfos, caseFormat, caseUuid, false, toEntity(loadFlowParameters), importReportUuid);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        } finally {
            deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            LOGGER.trace("Create study '{}' : {} seconds", basicStudyInfos.getId(),
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    public BasicStudyInfos createStudy(MultipartFile caseFile, String userId, UUID studyUuid) {
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        // Using temp file to store caseFile here because multipartfile are deleted once the request using it is over
        // Since the next action is asynchronous, the multipartfile could be deleted before being read and cause exceptions
        File tempFile = createTempFile(caseFile, basicStudyInfos);
        studyServerExecutionService.runAsync(() -> createStudyAsync(tempFile, caseFile.getOriginalFilename(), userId, basicStudyInfos));
        return basicStudyInfos;
    }

    private File createTempFile(MultipartFile caseFile, BasicStudyInfos basicStudyInfos) {
        File tempFile = null;
        try {
            tempFile = tempFileService.createTempFile(caseFile.getOriginalFilename());
            caseFile.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            LOGGER.error(e.toString(), e);
            deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), basicStudyInfos.getUserId());
            if (tempFile != null) {
                deleteFile(tempFile);
            }
            throw new StudyException(STUDY_CREATION_FAILED, e.getMessage());
        }
    }

    private void deleteFile(@NonNull File file) {
        try {
            Files.delete(file.toPath());
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }

    private void createStudyAsync(File caseFile, String originalFilename, String userId, BasicStudyInfos basicStudyInfos) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        try {
            UUID importReportUuid = UUID.randomUUID();
            UUID caseUuid = importCase(caseFile, originalFilename, basicStudyInfos.getId(), userId);
            if (caseUuid != null) {
                String caseFormat = getCaseFormat(caseUuid, basicStudyInfos.getId(), userId);
                NetworkInfos networkInfos = persistentStore(caseUuid, basicStudyInfos.getId(), userId, importReportUuid, null);
                LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                insertStudy(basicStudyInfos.getId(), userId, networkInfos, caseFormat, caseUuid, false, toEntity(loadFlowParameters), importReportUuid);
            }
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        } finally {
            deleteFile(caseFile);
            deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
            LOGGER.trace("Create study '{}' : {} seconds", basicStudyInfos.getId(),
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    public BasicStudyInfos createStudy(UUID sourceStudyUuid, UUID studyUuid, String userId) {
        Objects.requireNonNull(sourceStudyUuid);

        StudyEntity sourceStudy = studyRepository.findById(sourceStudyUuid).orElse(null);
        if (sourceStudy == null) {
            return null;
        }
        LoadFlowParameters sourceLoadFlowParameters = fromEntity(sourceStudy.getLoadFlowParameters());

        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        studyServerExecutionService.runAsync(() -> duplicateStudyAsync(basicStudyInfos, sourceStudy, sourceLoadFlowParameters, userId));
        return basicStudyInfos;
    }

    private void duplicateStudyAsync(BasicStudyInfos basicStudyInfos, StudyEntity sourceStudy, LoadFlowParameters sourceLoadFlowParameters, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        try {
            startTime.set(System.nanoTime());

            List<VariantInfos> networkVariants = networkStoreService.getNetworkVariants(sourceStudy.getNetworkUuid());
            List<String> targetVariantIds = networkVariants.stream().map(VariantInfos::getId).limit(2).collect(Collectors.toList());
            Network clonedNetwork = networkStoreService.cloneNetwork(sourceStudy.getNetworkUuid(), targetVariantIds);
            UUID clonedNetworkUuid = networkStoreService.getNetworkUuid(clonedNetwork);

            LoadFlowParameters newLoadFlowParameters = sourceLoadFlowParameters != null ? sourceLoadFlowParameters.copy() : new LoadFlowParameters();
            insertDuplicatedStudy(basicStudyInfos, sourceStudy, toEntity(newLoadFlowParameters), userId, clonedNetworkUuid);
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

    List<CreatedStudyBasicInfos> searchStudies(@NonNull String query) {
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

    List<EquipmentInfos> searchEquipments(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull String userInput,
                                          @NonNull EquipmentInfosService.FieldSelector fieldSelector, String equipmentType,
                                          boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuidToSearchIn);

        if (variantId.isEmpty()) {
            variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
        }

        String queryInitialVariant = buildEquipmentSearchQuery(userInput, fieldSelector, networkUuid,
                VariantManagerConstants.INITIAL_VARIANT_ID, equipmentType);

        List<EquipmentInfos> equipmentInfosInInitVariant = equipmentInfosService.searchEquipments(queryInitialVariant);

        return (variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID)) ? equipmentInfosInInitVariant
                : completeSearchWithCurrentVariant(networkUuid, variantId, userInput, fieldSelector,
                        equipmentInfosInInitVariant, equipmentType);
    }

    List<EquipmentInfos> searchAllEquipments(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull String userInput,
                                          @NonNull EquipmentInfosService.FieldSelector fieldSelector,
                                          boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuidToSearchIn);

        if (variantId.isEmpty()) {
            variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
        }

        BoolQueryBuilder query = buildSearchAllEquipmentQuery(userInput, fieldSelector, networkUuid,
                VariantManagerConstants.INITIAL_VARIANT_ID, variantId);

        List<EquipmentInfos> equipmentInfos = equipmentInfosService.searchEquipments(query);

        String queryTombstonedEquipments = buildTombstonedEquipmentSearchQuery(networkUuid, variantId);
        Set<String> removedEquipmentIdsInVariant = equipmentInfosService.searchTombstonedEquipments(queryTombstonedEquipments)
                .stream()
                .map(TombstonedEquipmentInfos::getId)
                .collect(Collectors.toSet());

        equipmentInfos = equipmentInfos.stream()
                .filter(ei -> !removedEquipmentIdsInVariant.contains(ei.getId()))
                .collect(Collectors.toList());

        return equipmentInfos;
    }

    private List<EquipmentInfos> completeSearchWithCurrentVariant(UUID networkUuid, String variantId, String userInput,
                                                                  EquipmentInfosService.FieldSelector fieldSelector, List<EquipmentInfos> equipmentInfosInInitVariant,
                                                                  String equipmentType) {
        String queryTombstonedEquipments = buildTombstonedEquipmentSearchQuery(networkUuid, variantId);
        Set<String> removedEquipmentIdsInVariant = equipmentInfosService.searchTombstonedEquipments(queryTombstonedEquipments)
                .stream()
                .map(TombstonedEquipmentInfos::getId)
                .collect(Collectors.toSet());

        String queryVariant = buildEquipmentSearchQuery(userInput, fieldSelector, networkUuid, variantId,
                equipmentType);
        List<EquipmentInfos> addedEquipmentInfosInVariant = equipmentInfosService.searchEquipments(queryVariant);

        List<EquipmentInfos> equipmentInfos = equipmentInfosInInitVariant
                .stream()
                .filter(ei -> !removedEquipmentIdsInVariant.contains(ei.getId()))
                .collect(Collectors.toList());

        equipmentInfos.addAll(addedEquipmentInfosInVariant);

        return equipmentInfos;
    }

    private String buildEquipmentSearchQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String variantId, String equipmentType) {
        String query = "networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND %s:(*%s*)"
                + (equipmentType == null ? "" : " AND equipmentType.keyword:(%s)");
        return String.format(query, networkUuid, variantId,
                fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID,
                escapeLucene(userInput), equipmentType);
    }

    private BoolQueryBuilder buildSearchAllEquipmentQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String initialVariantId, String variantId) {
        WildcardQueryBuilder equipmentSearchQuery = QueryBuilders.wildcardQuery(fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID, "*" + escapeLucene(userInput) + "*");
        MatchQueryBuilder networkUuidSearchQuery = matchQuery("networkUuid.keyword", networkUuid.toString());
        TermsQueryBuilder variantIdSearchQuery = termsQuery("variantId.keyword", initialVariantId, variantId);

        List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionsForScoreQueries = new ArrayList<>();
        filterFunctionsForScoreQueries.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                matchQuery(fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID, escapeLucene(userInput)),
                ScoreFunctionBuilders.weightFactorFunction(EQUIPMENT_TYPE_SCORES.entrySet().size())
        ));

        EQUIPMENT_TYPE_SCORES.entrySet().forEach(equipmentTypeScore -> filterFunctionsForScoreQueries.add(new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                matchQuery("equipmentType", equipmentTypeScore.getKey()),
                ScoreFunctionBuilders.weightFactorFunction(equipmentTypeScore.getValue())
        )));

        FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionsForScoreQueriesAsArray = new FunctionScoreQueryBuilder.FilterFunctionBuilder[ filterFunctionsForScoreQueries.size() ];
        filterFunctionsForScoreQueries.toArray(filterFunctionsForScoreQueriesAsArray);
        FunctionScoreQueryBuilder functionScoreBoostQuery = QueryBuilders.functionScoreQuery(filterFunctionsForScoreQueriesAsArray);

        BoolQueryBuilder esQuery = QueryBuilders.boolQuery();
        esQuery.filter(equipmentSearchQuery).filter(networkUuidSearchQuery).filter(variantIdSearchQuery).must(functionScoreBoostQuery);
        return esQuery;
    }

    private String buildTombstonedEquipmentSearchQuery(UUID networkUuid, String variantId) {
        return String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s)", networkUuid, variantId);
    }

    @Transactional
    public Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        List<UUID> buildReportsUuids = new ArrayList<>();
        UUID networkUuid = null;
        List<NodeModificationInfos> nodesModificationInfos = new ArrayList<>();
        if (studyCreationRequestEntity.isEmpty()) {
            networkUuid = networkStoreService.doGetNetworkUuid(studyUuid);
            nodesModificationInfos = networkModificationTreeService.getAllNodesModificationInfos(studyUuid);
            studyRepository.findById(studyUuid).ifPresent(s -> {
                networkModificationTreeService.doDeleteTree(studyUuid, buildReportsUuids);
                studyRepository.deleteById(studyUuid);
                studyInfosService.deleteByUuid(studyUuid);
            });
        } else {
            studyCreationRequestRepository.deleteById(studyCreationRequestEntity.get().getId());
        }
        notificationService.emitStudyDelete(studyUuid, userId);

        return networkUuid != null ? Optional.of(new DeleteStudyInfos(networkUuid, nodesModificationInfos, buildReportsUuids)) : Optional.empty();
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
                    studyServerExecutionService.runAsync(() -> deleteStudyInfos.getBuildReportsUuids().forEach(reportService::deleteReport)), // TODO delete all with one request only
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

    private CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos,
            String caseFormat, UUID caseUuid, boolean casePrivate, LoadFlowParametersEntity loadFlowParameters, UUID importReportUuid) {
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertStudyEntity(
                studyUuid, userId, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), caseFormat, caseUuid, casePrivate, loadFlowParameters, importReportUuid));
        studyInfosService.add(createdStudyBasicInfos);

        notificationService.emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    @Transactional
    public CreatedStudyBasicInfos insertDuplicatedStudy(BasicStudyInfos studyInfos, StudyEntity sourceStudy, LoadFlowParametersEntity newLoadFlowParameters, String userId, UUID clonedNetworkUuid) {
        Objects.requireNonNull(studyInfos.getId());
        Objects.requireNonNull(userId);
        Objects.requireNonNull(clonedNetworkUuid);
        Objects.requireNonNull(sourceStudy.getNetworkId());
        Objects.requireNonNull(sourceStudy.getCaseFormat());
        Objects.requireNonNull(sourceStudy.getCaseUuid());
        Objects.requireNonNull(newLoadFlowParameters);

        UUID reportUuid = UUID.randomUUID();
        StudyEntity studyEntity = new StudyEntity(studyInfos.getId(), userId, LocalDateTime.now(ZoneOffset.UTC), clonedNetworkUuid, sourceStudy.getNetworkId(), sourceStudy.getCaseFormat(), sourceStudy.getCaseUuid(), sourceStudy.isCasePrivate(), sourceStudy.getLoadFlowProvider(), newLoadFlowParameters);
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

    public String getCaseFormat(UUID caseUuid, UUID studyUuid, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/format")
            .buildAndExpand(caseUuid)
            .toUriString();

        try {
            return restTemplate.getForObject(caseServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            throw handleStudyCreationError(studyUuid, userId, e, "case-server");
        }
    }

    private StudyException handleStudyCreationError(UUID studyUuid, String userId, HttpStatusCodeException httpException, String serverName) {
        HttpStatus httpStatusCode = httpException.getStatusCode();
        String errorMessage = httpException.getResponseBodyAsString();
        String errorToParse = errorMessage.isEmpty() ? "{\"message\": \"" + serverName + ": " + httpStatusCode + "\"}"
                : errorMessage;

        try {
            JsonNode node = new ObjectMapper().readTree(errorToParse).path("message");
            if (!node.isMissingNode()) {
                notificationService.emitStudyCreationError(studyUuid, userId, node.asText());
            } else {
                notificationService.emitStudyCreationError(studyUuid, userId, errorToParse);
            }
        } catch (JsonProcessingException e) {
            if (!errorToParse.isEmpty()) {
                notificationService.emitStudyCreationError(studyUuid, userId, errorToParse);
            }
        }

        LOGGER.error(errorToParse, httpException);

        return new StudyException(STUDY_CREATION_FAILED, errorToParse);
    }

    UUID importCase(File file, String originalFilename, UUID studyUuid, String userId) {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        UUID caseUuid;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            multipartBodyBuilder
                .part("file", new FileSystemResource(file))
                .filename(originalFilename);

            HttpEntity<MultiValueMap<String, HttpEntity<?>>> request = new HttpEntity<>(
                    multipartBodyBuilder.build(), headers);

            try {
                caseUuid = restTemplate.postForObject(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private",
                        request, UUID.class);
            } catch (HttpStatusCodeException e) {
                throw handleStudyCreationError(studyUuid, userId, e, "case-server");
            }
        } catch (StudyException e) {
            throw e;
        } catch (Exception e) {
            notificationService.emitStudyCreationError(studyUuid, userId, e.getMessage());
            throw new StudyException(STUDY_CREATION_FAILED, e.getMessage());
        }

        return caseUuid;
    }

    byte[] getVoltageLevelSvg(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
            UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}")
                .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
                .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
                .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring());
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid, voltageLevelId)
            .toUriString();

        byte[] result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + path, byte[].class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, "Voltage level " + voltageLevelId + " not found");
            } else {
                throw e;
            }
        }
        return result;
    }

    String getVoltageLevelSvgAndMetadata(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
            UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION
                        + "/svg-and-metadata/{networkUuid}/{voltageLevelId}")
                .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
                .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
                .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring());
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid, voltageLevelId)
            .toUriString();

        String result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, "Voltage level " + voltageLevelId + " not found");
            } else {
                throw e;
            }
        }
        return result;
    }

    private NetworkInfos persistentStore(UUID caseUuid, UUID studyUuid, String userId, UUID importReportUuid, Map<String, Object> importParameters) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
            .queryParam(CASE_UUID, caseUuid)
            .queryParam(QUERY_PARAM_VARIANT_ID, FIRST_VARIANT_ID)
            .queryParam(REPORT_UUID, importReportUuid)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(importParameters, headers);

        try {
            ResponseEntity<NetworkInfos> networkInfosResponse = restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, httpEntity,
                    NetworkInfos.class);
            NetworkInfos networkInfos = networkInfosResponse.getBody();
            if (networkInfos == null) {
                throw handleStudyCreationError(studyUuid, userId, new HttpClientErrorException(HttpStatus.BAD_REQUEST), "network-conversion-server");
            }
            return networkInfos;
        } catch (HttpStatusCodeException e) {
            throw handleStudyCreationError(studyUuid, userId, e, "network-conversion-server");
        } catch (Exception e) {
            if (!(e instanceof StudyException)) {
                notificationService.emitStudyCreationError(studyUuid, userId, e.getMessage());
            }
            throw e;
        }

    }

    String getLinesGraphics(UUID networkUuid, UUID nodeUuid) {

        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/lines")
            .queryParam(NETWORK_UUID, networkUuid);

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
            .buildAndExpand()
            .toUriString();

        return restTemplate.getForObject(geoDataServerBaseUri + path, String.class);
    }

    String getSubstationsGraphics(UUID networkUuid, UUID nodeUuid) {
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/substations")
                .queryParam(NETWORK_UUID, networkUuid);

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();

        return restTemplate.getForObject(geoDataServerBaseUri + path, String.class);
    }

    Boolean caseExists(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/exists")
            .buildAndExpand(caseUuid)
            .toUriString();

        return restTemplate.exchange(caseServerBaseUri + path, HttpMethod.GET, null, Boolean.class, caseUuid).getBody();
    }

    String getEquipmentsMapData(UUID networkUuid, String variantId, List<String> substationsIds, String equipmentPath) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath);
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        return restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
    }

    String getEquipmentMapData(UUID networkUuid, String variantId, String equipmentPath, String equipmentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath + "/{equipmentUuid}");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, equipmentId).toUriString();

        String equipmentMapData;
        try {
            equipmentMapData = restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(EQUIPMENT_NOT_FOUND);
            } else {
                throw e;
            }
        }
        return equipmentMapData;
    }

    String getSubstationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                substationsIds, "substations");
    }

    String getSubstationMapData(UUID studyUuid, UUID nodeUuid, String substationId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "substations", substationId);
    }

    String getLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                substationsIds, "lines");
    }

    String getLineMapData(UUID studyUuid, UUID nodeUuid, String lineId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "lines", lineId);
    }

    String getTwoWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "2-windings-transformers");
    }

    String getTwoWindingsTransformerMapData(UUID studyUuid, UUID nodeUuid, String twoWindingsTransformerId,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "2-windings-transformers", twoWindingsTransformerId);
    }

    String getThreeWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "3-windings-transformers");
    }

    String getGeneratorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                substationsIds, "generators");
    }

    String getGeneratorMapData(UUID studyUuid, UUID nodeUuid, String generatorId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "generators", generatorId);
    }

    String getBatteriesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "batteries");
    }

    String getDanglingLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "dangling-lines");
    }

    String getHvdcLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "hvdc-lines");
    }

    String getLccConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "lcc-converter-stations");
    }

    String getVscConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "vsc-converter-stations");
    }

    String getLoadsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                substationsIds, "loads");
    }

    String getLoadMapData(UUID studyUuid, UUID nodeUuid, String loadId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "loads", loadId);
    }

    String getShuntCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "shunt-compensators");
    }

    String getShuntCompensatorMapData(UUID studyUuid, UUID nodeUuid, String shuntCompensatorId,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "shunt-compensators", shuntCompensatorId);
    }

    String getStaticVarCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "static-var-compensators");
    }

    String getVoltageLevelMapData(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "voltage-levels", voltageLevelId);
    }

    String getVoltageLevelsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                substationsIds, "voltage-levels");
    }

    String getAllMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "all");
    }

    void changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = getNodeModificationInfos(nodeUuid);
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
            NodeModificationInfos nodeInfos = getNodeModificationInfos(nodeUuid);
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

    private LoadFlowStatus computeLoadFlowStatus(LoadFlowResult result) {
        return result.getComponentResults().stream()
                .filter(cr -> cr.getConnectedComponentNum() == 0 && cr.getSynchronousComponentNum() == 0
                        && cr.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED)
                .collect(Collectors.toList()).isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    public void runLoadFlow(UUID studyUuid, UUID nodeUuid) {
        try {
            LoadFlowResult result;

            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            String provider = getLoadFlowProvider(studyUuid);
            String variantId = getVariantId(nodeUuid);
            UUID reportUuid = getReportUuid(nodeUuid);

            var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                .queryParam("reportId", reportUuid.toString())
                .queryParam("reportName", "loadflow");
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
            }
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<LoadFlowParameters> httpEntity = new HttpEntity<>(getLoadFlowParameters(studyUuid),
                headers);

            setLoadFlowRunning(studyUuid, nodeUuid);
            ResponseEntity<LoadFlowResult> resp = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.PUT,
                httpEntity, LoadFlowResult.class);
            result = resp.getBody();
            updateLoadFlowResultAndStatus(nodeUuid, result, computeLoadFlowStatus(result), false);
        } catch (Exception e) {
            updateLoadFlowStatus(nodeUuid, LoadFlowStatus.NOT_DONE);
            throw e;
        } finally {
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW);
        }
    }

    public void setLoadFlowRunning(UUID studyUuid, UUID nodeUuid) {
        updateLoadFlowStatus(nodeUuid, LoadFlowStatus.RUNNING);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
    }

    public String getExportFormats() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/export/formats")
            .toUriString();

        ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<>() {
        };

        return restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.GET, null, typeRef).getBody();
    }

    public ExportNetworkInfos exportNetwork(UUID studyUuid, UUID nodeUuid, String format, String paramatersJson) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION
            + "/networks/{networkUuid}/export/{format}");
        if (!variantId.isEmpty()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        String path = uriComponentsBuilder.buildAndExpand(networkUuid, format)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(paramatersJson, headers);

        ResponseEntity<byte[]> responseEntity = restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST,
            httpEntity, byte[].class);

        byte[] bytes = responseEntity.getBody();
        String filename = responseEntity.getHeaders().getContentDisposition().getFilename();
        return new ExportNetworkInfos(filename, bytes);
    }

    public void changeLineStatus(@NonNull UUID studyUuid, @NonNull String lineId, @NonNull String status,
            @NonNull UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = getNodeModificationInfos(nodeUuid);
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

    public void assertCaseExists(UUID caseUuid) {
        Boolean caseExists = caseExists(caseUuid);
        if (Boolean.FALSE.equals(caseExists)) {
            throw new StudyException(CASE_NOT_FOUND, "The case '" + caseUuid + "' does not exist");
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

    private void assertSecurityAnalysisNotRunning(UUID nodeUuid) {
        String sas = getSecurityAnalysisStatus(nodeUuid);
        if (SecurityAnalysisStatus.RUNNING.name().equals(sas)) {
            throw new StudyException(SECURITY_ANALYSIS_RUNNING);
        }
    }

    public void assertComputationNotRunning(UUID nodeUuid) {
        assertLoadFlowNotRunning(nodeUuid);
        assertSecurityAnalysisNotRunning(nodeUuid);
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

    public static LoadFlowParametersEntity toEntity(LoadFlowParameters parameters) {
        Objects.requireNonNull(parameters);
        return new LoadFlowParametersEntity(parameters.getVoltageInitMode(),
                parameters.isTransformerVoltageControlOn(),
                parameters.isNoGeneratorReactiveLimits(),
                parameters.isPhaseShifterRegulationOn(),
                parameters.isTwtSplitShuntAdmittance(),
                parameters.isShuntCompensatorVoltageControlOn(),
                parameters.isReadSlackBus(),
                parameters.isWriteSlackBus(),
                parameters.isDc(),
                parameters.isDistributedSlack(),
                parameters.getBalanceType(),
                parameters.isDcUseTransformerRatio(),
                parameters.getCountriesToBalance().stream().map(Country::toString).collect(Collectors.toSet()),
                parameters.getConnectedComponentMode(),
                parameters.isHvdcAcEmulation());
    }

    public static LoadFlowParameters fromEntity(LoadFlowParametersEntity entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowParameters(entity.getVoltageInitMode(),
            entity.isTransformerVoltageControlOn(),
            entity.isNoGeneratorReactiveLimits(),
            entity.isPhaseShifterRegulationOn(),
            entity.isTwtSplitShuntAdmittance(),
            entity.isShuntCompensatorVoltageControlOn(),
            entity.isReadSlackBus(),
            entity.isWriteSlackBus(),
            entity.isDc(),
            entity.isDistributedSlack(),
            entity.getBalanceType(),
            entity.isDcUseTransformerRatio(),
            entity.getCountriesToBalance().stream().map(Country::valueOf).collect(Collectors.toSet()),
            entity.getConnectedComponentMode(),
            entity.isHvdcAcEmulation()
            );
    }

    public static LoadFlowResultEntity toEntity(LoadFlowResult result) {
        return result != null
                ? new LoadFlowResultEntity(result.isOk(),
                      result.getMetrics(),
                      result.getLogs(),
                      result.getComponentResults().stream().map(StudyService::toEntity).collect(Collectors.toList())) : null;
    }

    public static LoadFlowResult fromEntity(LoadFlowResultEntity entity) {
        LoadFlowResult result = null;
        if (entity != null) {
            // This is a workaround to prepare the componentResultEmbeddables which will be used later in the webflux pipeline
            // The goal is to avoid LazyInitializationException
            @SuppressWarnings("unused")
            int ignoreSize = entity.getComponentResults().size();
            @SuppressWarnings("unused")
            int ignoreSize2 = entity.getMetrics().size();

            result = new LoadFlowResultImpl(entity.isOk(),
                    entity.getMetrics(),
                    entity.getLogs(),
                    entity.getComponentResults().stream().map(StudyService::fromEntity).collect(Collectors.toList()));
        }
        return result;
    }

    public static ComponentResultEmbeddable toEntity(LoadFlowResult.ComponentResult componentResult) {
        Objects.requireNonNull(componentResult);
        return new ComponentResultEmbeddable(componentResult.getConnectedComponentNum(),
            componentResult.getSynchronousComponentNum(),
            componentResult.getStatus(),
            componentResult.getIterationCount(),
            componentResult.getSlackBusId(),
            componentResult.getSlackBusActivePowerMismatch(),
            componentResult.getDistributedActivePower());
    }

    public static LoadFlowResult.ComponentResult fromEntity(ComponentResultEmbeddable entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowResultImpl.ComponentResultImpl(entity.getConnectedComponentNum(),
            entity.getSynchronousComponentNum(),
            entity.getStatus(),
            entity.getIterationCount(),
            entity.getSlackBusId(),
            entity.getSlackBusActivePowerMismatch(),
            entity.getDistributedActivePower());
    }

    public LoadFlowParameters getLoadFlowParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .map(studyEntity -> fromEntity(studyEntity.getLoadFlowParameters()))
            .orElse(null);
    }

    void setLoadFlowParameters(UUID studyUuid, LoadFlowParameters parameters) {
        updateLoadFlowParameters(studyUuid, toEntity(parameters != null ? parameters : LoadFlowParameters.load()));
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        notificationService.emitStudyChanged(studyUuid, null, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
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

    @Transactional
    public UUID runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, String parameters,
            UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getLoadFlowProvider(studyUuid);
        String variantId = getVariantId(nodeUuid);
        UUID reportUuid = getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportUuid.toString());
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.queryParam("contingencyListName", contingencyListNames)
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        UUID result = restTemplate
                .exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();

        updateSecurityAnalysisResultUuid(nodeUuid, result);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        return result;
    }

    public String getSecurityAnalysisResult(UUID nodeUuid, List<String> limitTypes) {
        Objects.requireNonNull(limitTypes);
        String result = null;
        Optional<UUID> resultUuidOpt = getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
                .queryParam("limitType", limitTypes).buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            } else {
                throw e;
            }
        }

        return result;
    }

    public Integer getContingencyCount(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        UUID uuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        return contingencyListNames.stream().map(contingencyListName -> {
            var uriComponentsBuilder = UriComponentsBuilder
                    .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{contingencyListName}/export")
                    .queryParam("networkUuid", uuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(contingencyListName).toUriString();

            List<Contingency> contingencies = restTemplate.exchange(actionsServerBaseUri + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Contingency>>() {
                    }).getBody();

            return contingencies.size();
        }).reduce(0, Integer::sum);
    }

    byte[] getSubstationSvg(UUID studyUuid, String substationId, DiagramParameters diagramParameters,
            String substationLayout, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}")
                .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
                .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
                .queryParam(QUERY_PARAM_SUBSTATION_LAYOUT, substationLayout);
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid, substationId).toUriString();

        byte[] result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + path, byte[].class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, "Substation " + substationId + " not found");
            } else {
                throw e;
            }
        }
        return result;
    }

    String getSubstationSvgAndMetadata(UUID studyUuid, String substationId, DiagramParameters diagramParameters,
            String substationLayout, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg-and-metadata/{networkUuid}/{substationId}")
                .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
                .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
                .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
                .queryParam(QUERY_PARAM_SUBSTATION_LAYOUT, substationLayout);
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        String result;
        try {
            result = restTemplate.getForEntity(singleLineDiagramServerBaseUri + uriComponentsBuilder.build(), String.class, networkUuid, substationId).getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, "Substation " + substationId + " not found");
            } else {
                throw e;
            }
        }
        return result;
    }

    String getNeworkAreaDiagram(UUID studyUuid, UUID nodeUuid, List<String> voltageLevelsIds, int depth) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
                "/network-area-diagram/{networkUuid}")
                .queryParam(QUERY_PARAM_DEPTH, depth)
                .queryParam(QUERY_PARAM_VOLTAGE_LEVELS_IDS, voltageLevelsIds);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
                .buildAndExpand(networkUuid)
                .toUriString();

        return restTemplate.getForObject(singleLineDiagramServerBaseUri + path, String.class);
    }

    public String getSecurityAnalysisStatus(UUID nodeUuid) {
        String result = null;
        Optional<UUID> resultUuidOpt = getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
                .buildAndExpand(resultUuidOpt.get()).toUriString();

        try {
            result = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }

        return result;
    }

    private void invalidateSaStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void invalidateSecurityAnalysisStatus(UUID nodeUuid) {
        invalidateSaStatus(networkModificationTreeService.getSecurityAnalysisResultUuidsFromNode(nodeUuid));
    }

    public void invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        invalidateSaStatus(networkModificationTreeService.getStudySecurityAnalysisResultUuids(studyUuid));
    }

    void setCaseServerBaseUri(String caseServerBaseUri) {
        this.caseServerBaseUri = caseServerBaseUri;
    }

    void setNetworkConversionServerBaseUri(String networkConversionServerBaseUri) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
    }

    void setGeoDataServerBaseUri(String geoDataServerBaseUri) {
        this.geoDataServerBaseUri = geoDataServerBaseUri;
    }

    void setSingleLineDiagramServerBaseUri(String singleLineDiagramServerBaseUri) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
    }

    void setNetworkMapServerBaseUri(String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }

    void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }

    public void setSecurityAnalysisServerBaseUri(String securityAnalysisServerBaseUri) {
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
    }

    public void setActionsServerBaseUri(String actionsServerBaseUri) {
        this.actionsServerBaseUri = actionsServerBaseUri;
    }

    public void stopSecurityAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
    }

    @Bean
    @Transactional
    public Consumer<Message<String>> consumeSaStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Security analysis stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete security analysis result in database
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                    // send notification for stopped computation
                    UUID studyUuid = getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);

                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    @Transactional
    public Consumer<Message<String>> consumeSaFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Security analysis failed for node '{}'", receiverObj.getNodeUuid());

                    // delete security analysis result in database
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                    // send notification for failed computation
                    UUID studyUuid = getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED);

                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    private StudyEntity insertStudyEntity(UUID uuid, String userId, UUID networkUuid, String networkId,
            String caseFormat, UUID caseUuid, boolean casePrivate, LoadFlowParametersEntity loadFlowParameters,
            UUID importReportUuid) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowParameters);

        StudyEntity studyEntity = new StudyEntity(uuid, userId, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, caseFormat, caseUuid, casePrivate, defaultLoadflowProvider, loadFlowParameters);
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

    void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        networkModificationTreeService.updateLoadFlowStatus(nodeUuid, loadFlowStatus);
    }

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(String userId, UUID studyUuid) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid, userId, LocalDateTime.now(ZoneOffset.UTC));
        return studyCreationRequestRepository.save(studyCreationRequestEntity);
    }

    private void updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult,
            LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        networkModificationTreeService.updateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus,
                updateChildren);
    }

    @Transactional
    public void updateLoadFlowParameters(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setLoadFlowParameters(loadFlowParametersEntity));
    }

    List<String> getAvailableSvgComponentLibraries() {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-component-libraries").toUriString();

        return restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<String>>() {
                }).getBody();
    }

    // TODO remove
    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    public void createEquipment(UUID studyUuid, String createEquipmentAttributes, ModificationType modificationType,
            UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = getNodeModificationInfos(nodeUuid);
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
            NodeModificationInfos nodeInfos = getNodeModificationInfos(nodeUuid);
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

    void deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID nodeUuid) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS);
        try {
            NodeModificationInfos nodeInfos = getNodeModificationInfos(nodeUuid);
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

    List<VoltageLevelInfos> getVoltageLevels(UUID studyUuid, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/voltage-levels");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        List<VoltageLevelMapData> voltageLevelsMapData = restTemplate.exchange(networkMapServerBaseUri + path,
                HttpMethod.GET, null, new ParameterizedTypeReference<List<VoltageLevelMapData>>() {
                }).getBody();

        return voltageLevelsMapData != null ?
                voltageLevelsMapData.stream().map(e -> VoltageLevelInfos.builder().id(e.getId()).name(e.getName())
                        .substationId(e.getSubstationId()).build()).collect(Collectors.toList())
                : null;
    }

    List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
            String busPath) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + busPath);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, voltageLevelId).toUriString();

        return restTemplate.exchange(networkMapServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<IdentifiableInfos>>() {
                }).getBody();
    }

    List<IdentifiableInfos> getVoltageLevelBuses(UUID studyUuid, UUID nodeUuid, String voltageLevelId, Boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuidToSearchIn, voltageLevelId, "configured-buses");
    }

    List<IdentifiableInfos> getVoltageLevelBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId, Boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuidToSearchIn, voltageLevelId, "busbar-sections");
    }

    public LoadFlowStatus getLoadFlowStatus(UUID nodeUuid) {
        return networkModificationTreeService.getLoadFlowStatus(nodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND));
    }

    public Optional<UUID> getSecurityAnalysisResultUuid(UUID nodeUuid) {
        return networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);
    }

    @Transactional(readOnly = true)
    public UUID getStudyUuidFromNodeUuid(UUID nodeUuid) {
        return networkModificationTreeService.getStudyUuidForNodeId(nodeUuid);
    }

    public LoadFlowInfos getLoadFlowInfos(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        LoadFlowInfos lfInfos = networkModificationTreeService.getLoadFlowInfos(nodeUuid);

        return lfInfos;
    }

    private BuildInfos fillBuildInfos(UUID nodeUuid) {
        return networkModificationTreeService.prepareBuild(nodeUuid);
    }

    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        BuildInfos buildInfos = fillBuildInfos(nodeUuid);
        List<UUID> reportsUuids = buildInfos.getModificationReportUuids();
        updateBuildStatus(nodeUuid, BuildStatus.BUILDING);
        reportsUuids.forEach(reportService::deleteReport);

        try {
            networkModificationService.buildNode(studyUuid, nodeUuid, buildInfos);
        } catch (Exception e) {
            updateBuildStatus(nodeUuid, BuildStatus.NOT_BUILT);
            throw new StudyException(NODE_BUILD_ERROR, e.getMessage());
        }

    }

    public void stopBuild(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        networkModificationService.stopBuild(studyUuid, nodeUuid);
    }

    @Bean
    @Transactional
    public Consumer<Message<String>> consumeBuildResult() {
        return message -> {
            Set<String> substationsIds = Stream.of(message.getPayload().trim().split(",")).collect(Collectors.toSet());
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.BUILT);

                    UUID studyUuid = getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_BUILD_COMPLETED, substationsIds);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    @Transactional
    public Consumer<Message<String>> consumeBuildStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Build stopped for node '{}'", receiverObj.getNodeUuid());

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.NOT_BUILT);
                    // send notification
                    UUID studyUuid = getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_BUILD_CANCELLED);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    @Transactional
    public Consumer<Message<String>> consumeBuildFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Build failed for node '{}'", receiverObj.getNodeUuid());

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.NOT_BUILT);
                    // send notification
                    UUID studyUuid = getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_BUILD_FAILED);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    private void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        networkModificationTreeService.updateBuildStatus(nodeUuid, buildStatus);
    }

    private void invalidateBuild(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        InvalidateNodeInfos invalidateNodeInfos = new InvalidateNodeInfos();
        invalidateNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid));
        networkModificationTreeService.invalidateBuild(nodeUuid, invalidateOnlyChildrenBuildStatus, invalidateNodeInfos);

        CompletableFuture<Void> executeInParallel = CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getReportUuids().forEach(reportService::deleteReport)),  // TODO delete all with one request only
                studyServerExecutionService.runAsync(() ->  invalidateNodeInfos.getSecurityAnalysisResultUuids().forEach(this::deleteSaResult)),
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
    }

    @Transactional
    public void changeModificationActiveState(@NonNull UUID studyUuid, @NonNull UUID nodeUuid,
            @NonNull UUID modificationUuid, boolean active) {
        if (!getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        networkModificationTreeService.handleExcludeModification(nodeUuid, modificationUuid, active);
        updateStatuses(studyUuid, nodeUuid, false);
    }

    @Transactional
    public void deleteModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids) {
        notificationService.emitStartModificationEquipmentNotification(studyUuid, nodeUuid, NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS);
        try {
            if (!getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
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

    private void deleteSaResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(securityAnalysisServerBaseUri + path);
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
            studyServerExecutionService.runAsync(() ->  deleteNodeInfos.getSecurityAnalysisResultUuids().forEach(this::deleteSaResult)),
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

            // reindex study network equipments in elasticsearch
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/reindex-all")
                .buildAndExpand(networkUuid)
                .toUriString();

            try {
                restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, null, Void.class);
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
            networkModificationService.reorderModification(groupUuid, modificationUuid, beforeUuid);
            updateStatuses(studyUuid, nodeUuid, false);
        } finally {
            notificationService.emitEndModificationEquipmentNotification(studyUuid, nodeUuid);
        }
    }

    private void checkStudyContainsNode(UUID studyUuid, UUID nodeUuid) {
        if (!getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    private List<Pair<UUID, String>> getReportUuidsAndNames(UUID nodeUuid, boolean nodeOnlyReport) {
        return networkModificationTreeService.getReportUuidsAndNames(nodeUuid, nodeOnlyReport);
    }

    public List<ReporterModel> getNodeReport(UUID studyUuid, UUID nodeUuid, boolean nodeOnlyReport) {
        List<Pair<UUID, String>> reportUuidsAndNames = getReportUuidsAndNames(nodeUuid, nodeOnlyReport);
        return reportUuidsAndNames.stream().map(reportInfo -> {
            ReporterModel reporter = reportService.getReport(reportInfo.getLeft(), reportInfo.getRight());
            ReporterModel newReporter = new ReporterModel(reporter.getTaskKey(), reportInfo.getRight(), reporter.getTaskValues());
            reporter.getReports().forEach(newReporter::report);
            reporter.getSubReporters().forEach(newReporter::addSubReporter);
            return newReporter;
        }).collect(Collectors.toList());
    }

    public void deleteNodeReport(UUID studyUuid, UUID nodeUuid) {
        reportService.deleteReport(getReportUuid(nodeUuid));
    }

    private NodeModificationInfos getNodeModificationInfos(UUID nodeUuid) {
        return networkModificationTreeService.getNodeModificationInfos(nodeUuid);
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
            NodeModificationInfos nodeInfos = getNodeModificationInfos(nodeUuid);
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
}

