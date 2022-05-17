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
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@Service
public class StudyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyService.class);

    public static final String ROOT_CATEGORY_REACTOR = "reactor.";

    public static final String CATEGORY_BROKER_INPUT = StudyService.class.getName() + ".input-broker-messages";

    private static final String CATEGORY_BROKER_OUTPUT = StudyService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    static final String HEADER_USER_ID = "userId";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_NODE = "node";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_STUDIES = "studies";
    static final String UPDATE_TYPE_STUDY_DELETE = "deleteStudy";
    static final String UPDATE_TYPE_LOADFLOW = "loadflow";
    static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    static final String UPDATE_TYPE_SWITCH = "switch";
    static final String UPDATE_TYPE_LINE = "line";
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_RESULT = "securityAnalysisResult";
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_STATUS = "securityAnalysis_status";
    static final String UPDATE_TYPE_BUILD_COMPLETED = "buildCompleted";
    static final String UPDATE_TYPE_BUILD_CANCELLED = "buildCancelled";
    static final String HEADER_ERROR = "error";
    static final String UPDATE_TYPE_STUDY = "study";
    static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID = "deletedEquipmentId";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE = "deletedEquipmentType";
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

    // Self injection for @transactional support in internal calls to other methods of this service
    @Autowired
    StudyService self;

    NetworkModificationTreeService networkModificationTreeService;

    private final WebClient webClient;
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
    private StreamBridge studyUpdatePublisher;

    @Bean
    public Consumer<Flux<Message<String>>> consumeSaResult() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE)
            .flatMap(message -> {
                UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
                String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
                if (receiver != null) {
                    Receiver receiverObj;
                    try {
                        receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), Receiver.class);

                        LOGGER.info("Security analysis result '{}' available for node '{}'",
                            resultUuid, receiverObj.getNodeUuid());

                        // update DB
                        return updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid)
                            .then(Mono.fromCallable(() -> {
                                // send notifications
                                UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                                emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
                                emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_RESULT);
                                return null;
                            }));
                    } catch (JsonProcessingException e) {
                        LOGGER.error(e.toString());
                    }
                }
                return Mono.empty();
            })
            .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
            .subscribe();
    }

    @Autowired
    public StudyService(
        @Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
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
        WebClient.Builder webClientBuilder,
        NetworkModificationTreeService networkModificationTreeService,
        ObjectMapper objectMapper) {
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
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
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

    public Flux<CreatedStudyBasicInfos> getStudies() {
        return Flux.fromStream(() -> studyRepository.findAll().stream())
            .map(StudyService::toCreatedStudyBasicInfos)
            .sort(Comparator.comparing(CreatedStudyBasicInfos::getCreationDate).reversed());
    }

    public Flux<CreatedStudyBasicInfos> getStudiesMetadata(List<UUID> uuids) {
        return Flux.fromStream(() -> studyRepository.findAllById(uuids).stream().map(StudyService::toCreatedStudyBasicInfos));
    }

    Flux<BasicStudyInfos> getStudiesCreationRequests() {
        return Flux.fromStream(() -> studyCreationRequestRepository.findAll().stream())
            .map(StudyService::toBasicStudyInfos)
            .sort(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed());
    }

    public Mono<BasicStudyInfos> createStudy(UUID caseUuid, String userId, UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        return insertStudyCreationRequest(userId, studyUuid)
            .doOnSubscribe(x -> startTime.set(System.nanoTime()))
            .map(StudyService::toBasicStudyInfos)
            .doOnSuccess(s -> {
                UUID importReportUuid = UUID.randomUUID();
                Mono.zip(persistentStore(caseUuid, s.getId(), userId, importReportUuid), getCaseFormat(caseUuid))
                    .flatMap(t -> {
                        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                        return insertStudy(s.getId(), userId, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                            t.getT2(), caseUuid, false, toEntity(loadFlowParameters), importReportUuid);
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                    .doFinally(st -> {
                        deleteStudyIfNotCreationInProgress(s.getId(), userId).subscribe();
                        LOGGER.trace("Create study '{}' : {} seconds", s.getId(), TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                    })
                    .subscribe();
            });
    }

    public Mono<BasicStudyInfos> createStudy(Mono<FilePart> caseFile, String userId, UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        return insertStudyCreationRequest(userId, studyUuid)
            .doOnSubscribe(x -> startTime.set(System.nanoTime()))
            .map(StudyService::toBasicStudyInfos)
            .doOnSuccess(s -> {
                UUID importReportUuid = UUID.randomUUID();
                importCase(caseFile, s.getId(), userId)
                    .flatMap(uuid ->
                        Mono.zip(persistentStore(uuid, s.getId(), userId, importReportUuid), getCaseFormat(uuid))
                            .flatMap(t -> {
                                LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
                                return insertStudy(s.getId(), userId, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                    t.getT2(), uuid, true, toEntity(loadFlowParameters), importReportUuid);
                            }))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                    .doFinally(r -> {
                        deleteStudyIfNotCreationInProgress(s.getId(), userId).subscribe();  // delete the study if the creation has been canceled
                        LOGGER.trace("Create study '{}' : {} seconds", s.getId(), TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                    })
                    .subscribe();
            });
    }

    public Mono<StudyInfos> getStudyInfos(UUID studyUuid) {
        return getStudy(studyUuid).map(StudyService::toStudyInfos);
    }

    @Transactional(readOnly = true)
    public StudyEntity doGetStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElse(null);
    }

    public Mono<StudyEntity> getStudy(UUID studyUuid) {
        return Mono.fromCallable(() -> self.doGetStudy(studyUuid));
    }

    Flux<CreatedStudyBasicInfos> searchStudies(@NonNull String query) {
        return Mono.fromCallable(() -> studyInfosService.search(query)).flatMapMany(Flux::fromIterable);
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

    Flux<EquipmentInfos> searchEquipments(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull String userInput,
                                          @NonNull EquipmentInfosService.FieldSelector fieldSelector, String equipmentType,
                                          boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn))
                .flatMapIterable(tuple -> {
                    UUID networkUuid = tuple.getT1();
                    String variantId = tuple.getT2();
                    if (variantId.isEmpty()) {
                        variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
                    }
                    // Get equipments in initial variant matching query
                    String queryInitialVariant = buildEquipmentSearchQuery(userInput, fieldSelector, networkUuid, VariantManagerConstants.INITIAL_VARIANT_ID, equipmentType);
                    List<EquipmentInfos> equipmentInfosInInitVariant = equipmentInfosService.searchEquipments(queryInitialVariant);

                    // Get added/removed equipment to/from the chosen variant
                    return (variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID))
                            ? equipmentInfosInInitVariant
                            : completeSearchWithCurrentVariant(networkUuid, variantId, userInput, fieldSelector, equipmentInfosInInitVariant, equipmentType);
                });
    }

    private List<EquipmentInfos> completeSearchWithCurrentVariant(UUID networkUuid, String variantId, String userInput,
                                                                  EquipmentInfosService.FieldSelector fieldSelector,
                                                                  List<EquipmentInfos> equipmentInfosInInitVariant, String equipmentType) {
        String queryTombstonedEquipments = buildTombstonedEquipmentSearchQuery(networkUuid, variantId);
        Set<String> removedEquipmentIdsInVariant = equipmentInfosService.searchTombstonedEquipments(queryTombstonedEquipments)
                .stream()
                .map(TombstonedEquipmentInfos::getId)
                .collect(Collectors.toSet());

        String queryVariant = buildEquipmentSearchQuery(userInput, fieldSelector, networkUuid, variantId, equipmentType);
        List<EquipmentInfos> addedEquipmentInfosInVariant = equipmentInfosService.searchEquipments(queryVariant);

        List<EquipmentInfos> equipmentInfos = equipmentInfosInInitVariant
                .stream()
                .filter(ei -> !removedEquipmentIdsInVariant.contains(ei.getId()))
                .collect(Collectors.toList());

        equipmentInfos.addAll(addedEquipmentInfosInVariant);

        return equipmentInfos;
    }

    private String buildEquipmentSearchQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String variantId, String equipmentType) {
        String query = "networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND %s:(*%s*)" + (equipmentType == null ? "" : " AND equipmentType.keyword:(%s)");
        return String.format(query, networkUuid, variantId,
                fieldSelector == EquipmentInfosService.FieldSelector.NAME ? "equipmentName.fullascii" : "equipmentId.fullascii",
                escapeLucene(userInput), equipmentType);
    }

    private String buildTombstonedEquipmentSearchQuery(UUID networkUuid, String variantId) {
        return String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s)", networkUuid, variantId);
    }

    @Transactional
    public Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        UUID networkUuid = null;
        List<NodeModificationInfos> nodesModificationInfos = new ArrayList<>();
        if (studyCreationRequestEntity.isEmpty()) {
            networkUuid = networkStoreService.doGetNetworkUuid(studyUuid).orElse(null);
            nodesModificationInfos = networkModificationTreeService.getAllNodesModificationInfos(studyUuid);
            studyRepository.findById(studyUuid).ifPresent(s -> {
                networkModificationTreeService.doDeleteTree(studyUuid);
                studyRepository.deleteById(studyUuid);
                studyInfosService.deleteByUuid(studyUuid);
            });
        } else {
            studyCreationRequestRepository.deleteById(studyCreationRequestEntity.get().getId());
        }
        emitStudyDelete(studyUuid, userId);

        return networkUuid != null ? Optional.of(new DeleteStudyInfos(networkUuid, nodesModificationInfos)) : Optional.empty();
    }

    public Mono<Void> deleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        return Mono.fromCallable(() -> self.doDeleteStudyIfNotCreationInProgress(studyUuid, userId))
            .flatMap(Mono::justOrEmpty)
            .map(u -> {
                startTime.set(System.nanoTime());
                return u;
            })
            .publish(deleteStudyInfosMono ->
                Mono.when(// in parallel
                    deleteStudyInfosMono.flatMapMany(infos -> Flux.fromIterable(infos.getNodesModificationInfos().stream().map(NodeModificationInfos::getModificationGroupUuid).filter(m -> m != null).collect(Collectors.toList()))).flatMap(networkModificationService::deleteModifications),
                    deleteStudyInfosMono.flatMap(infos -> deleteEquipmentIndexes(infos.getNetworkUuid())),
                    deleteStudyInfosMono.flatMapMany(infos -> Flux.fromIterable(infos.getNodesModificationInfos().stream().map(NodeModificationInfos::getReportUuid).filter(r -> r != null).collect(Collectors.toList()))).flatMap(reportService::deleteReport),
                    deleteStudyInfosMono.flatMap(infos -> networkStoreService.deleteNetwork(infos.getNetworkUuid()))
                )
            )
            .doOnSuccess(r -> {
                if (startTime.get() != null) {
                    LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                }
            })
            .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable));
    }

    public Mono<Void> deleteEquipmentIndexes(UUID networkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        return Mono.fromRunnable(() -> equipmentInfosService.deleteAll(networkUuid))
            .doOnSubscribe(x -> startTime.set(System.nanoTime()))
            .then()
            .doFinally(x -> LOGGER.trace("Indexes deletion for network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get())));
    }

    private Mono<CreatedStudyBasicInfos> insertStudy(UUID studyUuid, String userId, UUID networkUuid, String networkId,
                                                     String caseFormat, UUID caseUuid, boolean casePrivate,
                                                     LoadFlowParametersEntity loadFlowParameters,
                                                     UUID importReportUuid) {
        return insertStudyEntity(studyUuid, userId, networkUuid, networkId, caseFormat, caseUuid, casePrivate, loadFlowParameters, importReportUuid)
            .map(StudyService::toCreatedStudyBasicInfos)
            .map(studyInfosService::add)
            .doOnSuccess(infos -> emitStudiesChanged(studyUuid, userId));
    }

    private Mono<StudyCreationRequestEntity> insertStudyCreationRequest(String userId, UUID studyUuid) {
        return insertStudyCreationRequestEntity(userId, studyUuid)
            .doOnSuccess(s -> emitStudiesChanged(s.getId(), userId));
    }

    private Mono<String> getCaseFormat(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/format")
            .buildAndExpand(caseUuid)
            .toUriString();

        return webClient.get()
            .uri(caseServerBaseUri + path)
            .retrieve()
            .bodyToMono(String.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    private Mono<? extends Throwable> handleStudyCreationError(UUID studyUuid, String userId, ClientResponse clientResponse, String serverName) {
        return clientResponse.bodyToMono(String.class)
            .switchIfEmpty(Mono.just("{\"message\": \"" + serverName + ": " + clientResponse.statusCode() + "\"}"))
            .flatMap(body -> {
                try {
                    JsonNode node = new ObjectMapper().readTree(body).path("message");
                    if (!node.isMissingNode()) {
                        emitStudyCreationError(studyUuid, userId, node.asText());
                    } else {
                        emitStudyCreationError(studyUuid, userId, body);
                    }
                } catch (JsonProcessingException e) {
                    if (!body.isEmpty()) {
                        emitStudyCreationError(studyUuid, userId, body);
                    }
                }
                return Mono.error(new StudyException(STUDY_CREATION_FAILED));
            });
    }

    Mono<UUID> importCase(Mono<FilePart> multipartFile, UUID studyUuid, String userId) {
        return multipartFile
            .flatMap(file -> {
                MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
                multipartBodyBuilder.part("file", file);

                return webClient.post()
                    .uri(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA.toString())
                    .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                        handleStudyCreationError(studyUuid, userId, clientResponse, "case-server")
                    )
                    .bodyToMono(UUID.class)
                    .publishOn(Schedulers.boundedElastic())
                    .log(ROOT_CATEGORY_REACTOR, Level.FINE);
            })
            .doOnError(t -> !(t instanceof StudyException), t -> emitStudyCreationError(studyUuid, userId, t.getMessage()));
    }

    Mono<byte[]> getVoltageLevelSvg(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters, UUID nodeUuid) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}")
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

            return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(byte[].class);
        });
    }

    Mono<String> getVoltageLevelSvgAndMetadata(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters, UUID nodeUuid) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-and-metadata/{networkUuid}/{voltageLevelId}")
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

            return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
        });
    }

    private Mono<NetworkInfos> persistentStore(UUID caseUuid, UUID studyUuid, String userId, UUID importReportUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
            .queryParam(CASE_UUID, caseUuid)
            .queryParam(QUERY_PARAM_VARIANT_ID, FIRST_VARIANT_ID)
            .queryParam(REPORT_UUID, importReportUuid)
            .buildAndExpand()
            .toUriString();

        return webClient.post()
            .uri(networkConversionServerBaseUri + path)
            .retrieve()
            .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                handleStudyCreationError(studyUuid, userId, clientResponse, "network-conversion-server")
            )
            .bodyToMono(NetworkInfos.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE)
            .doOnError(t -> !(t instanceof StudyException), t -> emitStudyCreationError(studyUuid, userId, t.getMessage()));
    }

    Mono<String> getLinesGraphics(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/lines")
            .queryParam(NETWORK_UUID, networkUuid)
            .buildAndExpand()
            .toUriString();

        return webClient.get()
            .uri(geoDataServerBaseUri + path)
            .retrieve()
            .bodyToMono(String.class);
    }

    Mono<String> getSubstationsGraphics(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/substations")
            .queryParam(NETWORK_UUID, networkUuid)
            .buildAndExpand()
            .toUriString();

        return webClient.get()
            .uri(geoDataServerBaseUri + path)
            .retrieve()
            .bodyToMono(String.class);
    }

    Mono<Boolean> caseExists(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/exists")
            .buildAndExpand(caseUuid)
            .toUriString();

        return webClient.get()
            .uri(caseServerBaseUri + path)
            .retrieve()
            .bodyToMono(Boolean.class);
    }

    Mono<String> getEquipmentsMapData(UUID networkUuid, String variantId, List<String> substationsIds, String equipmentPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath);
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        return webClient.get()
            .uri(networkMapServerBaseUri + path)
            .retrieve()
            .bodyToMono(String.class);
    }

    Mono<String> getEquipmentMapData(UUID networkUuid, String variantId, String equipmentPath, String equipmentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath + "/{equipmentUuid}");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, equipmentId).toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new StudyException(EQUIPMENT_NOT_FOUND)))
                .bodyToMono(String.class);
    }

    Mono<String> getSubstationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "substations")
        );
    }

    Mono<String> getSubstationMapData(UUID studyUuid, UUID nodeUuid, String substationId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
                getEquipmentMapData(tuple.getT1(), tuple.getT2(), "substations", substationId)
        );
    }

    Mono<String> getLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "lines")
        );
    }

    Mono<String> getLineMapData(UUID studyUuid, UUID nodeUuid, String lineId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
                getEquipmentMapData(tuple.getT1(), tuple.getT2(), "lines", lineId)
        );
    }

    Mono<String> getTwoWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "2-windings-transformers")
        );
    }

    Mono<String> getTwoWindingsTransformerMapData(UUID studyUuid, UUID nodeUuid, String twoWindingsTransformerId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
                getEquipmentMapData(tuple.getT1(), tuple.getT2(), "2-windings-transformers", twoWindingsTransformerId)
        );
    }

    Mono<String> getThreeWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "3-windings-transformers")
        );
    }

    Mono<String> getGeneratorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "generators")
        );
    }

    Mono<String> getGeneratorMapData(UUID studyUuid, UUID nodeUuid, String generatorId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
                getEquipmentMapData(tuple.getT1(), tuple.getT2(), "generators", generatorId)
        );
    }

    Mono<String> getBatteriesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "batteries")
        );
    }

    Mono<String> getDanglingLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "dangling-lines")
        );
    }

    Mono<String> getHvdcLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "hvdc-lines")
        );
    }

    Mono<String> getLccConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "lcc-converter-stations")
        );
    }

    Mono<String> getVscConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "vsc-converter-stations")
        );
    }

    Mono<String> getLoadsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "loads")
        );
    }

    Mono<String> getLoadMapData(UUID studyUuid, UUID nodeUuid, String loadId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
                getEquipmentMapData(tuple.getT1(), tuple.getT2(), "loads", loadId)
        );
    }

    Mono<String> getShuntCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "shunt-compensators")
        );
    }

    Mono<String> getShuntCompensatorMapData(UUID studyUuid, UUID nodeUuid, String shuntCompensatorId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
                getEquipmentMapData(tuple.getT1(), tuple.getT2(), "shunt-compensators", shuntCompensatorId)
        );
    }

    Mono<String> getStaticVarCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "static-var-compensators")
        );
    }

    Mono<String> getVoltageLevelMapData(UUID studyUuid, UUID nodeUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn)).flatMap(tuple ->
                getEquipmentMapData(tuple.getT1(), tuple.getT2(), "voltage-levels", voltageLevelId)
        );
    }

    Mono<String> getAllMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple ->
            getEquipmentsMapData(tuple.getT1(), tuple.getT2(), substationsIds, "all")
        );
    }

    Mono<Void> changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID nodeUuid) {
        return getNodeModificationInfos(nodeUuid).flatMap(nodeInfos -> {
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_SWITCH));

            return networkModificationService.changeSwitchState(studyUuid, switchId, open, groupUuid, variantId, reportUuid)
                .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                .collect(Collectors.toSet())
                .doOnSuccess(substationIds ->
                    emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds)
                )
                .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                .then(monoUpdateStatusResult);
        });
    }

    public Mono<Void> applyGroovyScript(UUID studyUuid, String groovyScript, UUID nodeUuid) {
        return getNodeModificationInfos(nodeUuid).flatMap(nodeInfos -> {
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid);

            return networkModificationService.applyGroovyScript(studyUuid, groovyScript, groupUuid, variantId, reportUuid)
                .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                .collect(Collectors.toSet())
                .doOnSuccess(substationIds ->
                    emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds)
                )
                .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                .then(monoUpdateStatusResult);
        });
    }

    private LoadFlowStatus computeLoadFlowStatus(LoadFlowResult result) {
        return result.getComponentResults().stream().filter(cr -> cr.getConnectedComponentNum() == 0
            && cr.getSynchronousComponentNum() == 0
            && cr.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED).collect(Collectors.toList()).isEmpty() ? LoadFlowStatus.DIVERGED
            : LoadFlowStatus.CONVERGED;
    }

    Mono<Void> runLoadFlow(UUID studyUuid, UUID nodeUuid) {
        return setLoadFlowRunning(studyUuid, nodeUuid).then(Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getLoadFlowProvider(studyUuid), getNodeModificationInfos(nodeUuid))).flatMap(tuple3 -> {
            UUID networkUuid = tuple3.getT1();
            String provider = tuple3.getT2();
            String variantId = tuple3.getT3().getVariantId();
            UUID reportUuid = tuple3.getT3().getReportUuid();

            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                .queryParam("reportId", reportUuid.toString())
                .queryParam("reportName", "loadflow")
                .queryParam("overwrite", true);
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
            }
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                .buildAndExpand(networkUuid)
                .toUriString();
            return webClient.put()
                .uri(loadFlowServerBaseUri + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(getLoadFlowParameters(studyUuid), LoadFlowParameters.class)
                .retrieve()
                .bodyToMono(LoadFlowResult.class)
                .flatMap(result -> updateLoadFlowResultAndStatus(nodeUuid, result, computeLoadFlowStatus(result), false))
                .doOnError(e -> updateLoadFlowStatus(nodeUuid, LoadFlowStatus.NOT_DONE).subscribe())
                .doOnCancel(() -> updateLoadFlowStatus(nodeUuid, LoadFlowStatus.NOT_DONE).subscribe());
        }).doFinally(s ->
            emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LOADFLOW)
        );
    }

    private Mono<Void> setLoadFlowRunning(UUID studyUuid, UUID nodeUuid) {
        return updateLoadFlowStatus(nodeUuid, LoadFlowStatus.RUNNING)
            .doOnSuccess(s ->
                emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LOADFLOW_STATUS)
            );
    }

    public Mono<Collection<String>> getExportFormats() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/export/formats")
            .toUriString();

        ParameterizedTypeReference<Collection<String>> typeRef = new ParameterizedTypeReference<>() {
        };

        return webClient.get()
            .uri(networkConversionServerBaseUri + path)
            .retrieve()
            .bodyToMono(typeRef);
    }

    public Mono<ExportNetworkInfos> exportNetwork(UUID studyUuid, UUID nodeUuid, String format) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export/{format}");
            if (!variantId.isEmpty()) {
                uriComponentsBuilder.queryParam("variantId", variantId);
            }
            String path = uriComponentsBuilder.buildAndExpand(networkUuid, format)
                .toUriString();

            Mono<ResponseEntity<byte[]>> responseEntity = webClient.get()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .toEntity(byte[].class);

            return responseEntity.map(res -> {
                byte[] bytes = res.getBody();
                String filename = res.getHeaders().getContentDisposition().getFilename();
                return new ExportNetworkInfos(filename, bytes);
            });
        });
    }

    public Mono<Void> changeLineStatus(@NonNull UUID studyUuid, @NonNull String lineId, @NonNull  String status, @NonNull UUID nodeUuid) {
        return getNodeModificationInfos(nodeUuid).flatMap(nodeInfos -> {
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LINE));

            return networkModificationService.changeLineStatus(studyUuid, lineId, status, groupUuid, variantId, reportUuid)
                .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                .collect(Collectors.toSet())
                .doOnSuccess(substationIds ->
                    emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds)
                )
                .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                .then(monoUpdateStatusResult);
        });
    }

    private void emitStudiesChanged(UUID studyUuid, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
            .build());
    }

    private void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_NODE, nodeUuid)
            .setHeader(HEADER_UPDATE_TYPE, updateType)
            .build()
        );
    }

    private void emitStudyCreationError(UUID studyUuid, String userId, String errorMessage) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
            .setHeader(HEADER_ERROR, errorMessage)
            .build()
        );
    }

    private void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType, Set<String> substationsIds) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_NODE, nodeUuid)
            .setHeader(HEADER_UPDATE_TYPE, updateType)
            .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
            .build()
        );
    }

    private void emitStudyDelete(UUID studyUuid, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDY_DELETE)
            .build());
    }

    private void emitStudyEquipmentDeleted(UUID studyUuid, UUID nodeUuid, String updateType, Set<String> substationsIds, String equipmentType, String equipmentId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_NODE, nodeUuid)
            .setHeader(HEADER_UPDATE_TYPE, updateType)
            .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
            .setHeader(HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE, equipmentType)
            .setHeader(HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, equipmentId)
            .build()
        );
    }

    public Mono<Void> assertCaseExists(UUID caseUuid) {
        Mono<Boolean> caseExists = caseExists(caseUuid);
        return caseExists.flatMap(c -> Boolean.TRUE.equals(c) ? Mono.empty() : Mono.error(new StudyException(CASE_NOT_FOUND)));
    }

    public Mono<Void> assertLoadFlowRunnable(UUID nodeUuid) {
        return getLoadFlowStatus(nodeUuid)
            .switchIfEmpty(Mono.error(new StudyException(ELEMENT_NOT_FOUND)))
            .flatMap(lfs -> lfs.equals(LoadFlowStatus.NOT_DONE) ? Mono.empty() : Mono.error(new StudyException(LOADFLOW_NOT_RUNNABLE)));
    }

    private Mono<Void> assertLoadFlowNotRunning(UUID nodeUuid) {
        return getLoadFlowStatus(nodeUuid)
            .switchIfEmpty(Mono.error(new StudyException(ELEMENT_NOT_FOUND)))
            .flatMap(lfs -> lfs.equals(LoadFlowStatus.RUNNING) ? Mono.error(new StudyException(LOADFLOW_RUNNING)) : Mono.empty());
    }

    private Mono<Void> assertSecurityAnalysisNotRunning(UUID nodeUuid) {
        return getSecurityAnalysisStatus(nodeUuid)
            .flatMap(sas -> sas.equals(SecurityAnalysisStatus.RUNNING.name()) ? Mono.error(new StudyException(SECURITY_ANALYSIS_RUNNING)) : Mono.empty());
    }

    public Mono<Void> assertComputationNotRunning(UUID nodeUuid) {
        return assertLoadFlowNotRunning(nodeUuid).and(assertSecurityAnalysisNotRunning(nodeUuid));
    }

    public Mono<Void> assertCanModifyNode(UUID nodeUuid) {
        return networkModificationTreeService.isReadOnly(nodeUuid).switchIfEmpty(Mono.just(Boolean.FALSE))
                .flatMap(ro -> Boolean.TRUE.equals(ro) ? Mono.error(new StudyException(NOT_ALLOWED)) : Mono.empty());
    }

    public Mono<Void> assertRootNodeOrBuiltNode(UUID studyUuid, UUID nodeUuid) {
        return Mono.fromCallable(() -> networkModificationTreeService.getStudyRootNodeUuid(studyUuid).equals(nodeUuid)
            || networkModificationTreeService.getBuildStatus(nodeUuid) == BuildStatus.BUILT)
        .flatMap(p -> Boolean.TRUE.equals(p) ? Mono.empty() : Mono.error(new StudyException(NODE_NOT_BUILT)));
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
            parameters.getBalanceType());
    }

    public static LoadFlowParameters fromEntity(LoadFlowParametersEntity entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowParameters(entity.getVoltageInitMode(),
            entity.isTransformerVoltageControlOn(),
            entity.isNoGeneratorReactiveLimits(),
            entity.isPhaseShifterRegulationOn(),
            entity.isTwtSplitShuntAdmittance(),
            entity.isSimulShunt(),
            entity.isReadSlackBus(),
            entity.isWriteSlackBus(),
            entity.isDc(),
            entity.isDistributedSlack(),
            entity.getBalanceType(),
            true, // FIXME to persist
            EnumSet.noneOf(Country.class), // FIXME to persist
            LoadFlowParameters.ConnectedComponentMode.MAIN, // FIXME to persist
            true// FIXME to persist
            );
    }

    public static LoadFlowResultEntity toEntity(LoadFlowResult result) {
        return result != null ?
            new LoadFlowResultEntity(result.isOk(),
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
            componentResult.getSlackBusActivePowerMismatch()
        );
    }

    public static LoadFlowResult.ComponentResult fromEntity(ComponentResultEmbeddable entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowResultImpl.ComponentResultImpl(entity.getConnectedComponentNum(),
            entity.getSynchronousComponentNum(),
            entity.getStatus(),
            entity.getIterationCount(),
            entity.getSlackBusId(),
            entity.getSlackBusActivePowerMismatch());
    }

    @Transactional(readOnly = true)
    public LoadFlowParameters doGetLoadFlowParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .map(studyEntity -> fromEntity(studyEntity.getLoadFlowParameters()))
            .orElse(null);
    }

    public Mono<LoadFlowParameters> getLoadFlowParameters(UUID studyUuid) {
        return Mono.fromCallable(() -> self.doGetLoadFlowParameters(studyUuid));
    }

    Mono<Void> setLoadFlowParameters(UUID studyUuid, LoadFlowParameters parameters) {
        return updateLoadFlowParameters(studyUuid, toEntity(parameters != null ? parameters : LoadFlowParameters.load()))
            .then(invalidateLoadFlowStatusOnAllNodes(studyUuid))
            .doOnSuccess(e -> emitStudyChanged(studyUuid, null, UPDATE_TYPE_LOADFLOW_STATUS))
            .then(invalidateSecurityAnalysisStatusOnAllNodes(studyUuid))
            .doOnSuccess(e -> emitStudyChanged(studyUuid, null, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS));
    }

    public Mono<Void> invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        return networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE);
    }

    @Transactional(readOnly = true)
    public String doGetLoadFlowProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .map(StudyEntity::getLoadFlowProvider)
            .orElse("");
    }

    public Mono<String> getLoadFlowProvider(UUID studyUuid) {
        return Mono.fromCallable(() -> self.doGetLoadFlowProvider(studyUuid));
    }

    @Transactional
    public void doUpdateLoadFlowProvider(UUID studyUuid, String provider) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setLoadFlowProvider(provider != null ? provider : defaultLoadflowProvider));
    }

    public Mono<Void> updateLoadFlowProvider(UUID studyUuid, String provider) {
        Mono<Void> updateProvider = Mono.fromRunnable(() -> self.doUpdateLoadFlowProvider(studyUuid, provider));
        return updateProvider
            .then(networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE))
            .doOnSuccess(e -> emitStudyChanged(studyUuid, null, UPDATE_TYPE_LOADFLOW_STATUS));
    }

    public Mono<UUID> runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, String parameters, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(nodeUuid);

        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getLoadFlowProvider(studyUuid), getVariantId(nodeUuid)).flatMap(tuple3 -> {
            UUID networkUuid = tuple3.getT1();
            String provider = tuple3.getT2();
            String variantId = tuple3.getT3();

            String receiver;
            try {
                receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)), StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                return Mono.error(new UncheckedIOException(e));
            }
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save");
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
            }
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder
                .queryParam("contingencyListName", contingencyListNames)
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .buildAndExpand(networkUuid)
                .toUriString();

            return webClient
                .post()
                .uri(securityAnalysisServerBaseUri + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(parameters))
                .retrieve()
                .bodyToMono(UUID.class);
        }).flatMap(result ->
            updateSecurityAnalysisResultUuid(nodeUuid, result)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, nodeUuid, StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS))
                .thenReturn(result)
        );
    }

    public Mono<String> getSecurityAnalysisResult(UUID nodeUuid, List<String> limitTypes) {
        Objects.requireNonNull(limitTypes);

        return getSecurityAnalysisResultUuid(nodeUuid).flatMap(resultUuid ->
            Mono.justOrEmpty(resultUuid).flatMap(uuid -> {
                String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
                    .queryParam("limitType", limitTypes)
                    .buildAndExpand(resultUuid)
                    .toUriString();
                return webClient
                    .get()
                    .uri(securityAnalysisServerBaseUri + path)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new StudyException(SECURITY_ANALYSIS_NOT_FOUND)))
                    .bodyToMono(String.class);
            }));
    }

    public Mono<Integer> getContingencyCount(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID uuid = tuple.getT1();
            String variantId = tuple.getT2();

            return Flux.fromIterable(contingencyListNames)
                .flatMap(contingencyListName -> {
                    var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{contingencyListName}/export")
                        .queryParam("networkUuid", uuid);
                    if (!StringUtils.isBlank(variantId)) {
                        uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
                    }
                    var path = uriComponentsBuilder
                        .buildAndExpand(contingencyListName)
                        .toUriString();
                    Mono<List<Contingency>> contingencies = webClient
                        .get()
                        .uri(actionsServerBaseUri + path)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<>() {
                        });
                    return contingencies.map(List::size);
                })
                .reduce(0, Integer::sum);
        });
    }

    Mono<byte[]> getSubstationSvg(UUID studyUuid, String substationId, DiagramParameters diagramParameters, String substationLayout, UUID nodeUuid) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}")
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
            var path = uriComponentsBuilder
                .buildAndExpand(networkUuid, substationId)
                .toUriString();

            return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(byte[].class);
        });
    }

    Mono<String> getSubstationSvgAndMetadata(UUID studyUuid, String substationId, DiagramParameters diagramParameters, String substationLayout, UUID nodeUuid) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
                "/substation-svg-and-metadata/{networkUuid}/{substationId}")
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
            var path = uriComponentsBuilder
                .buildAndExpand(networkUuid, substationId)
                .toUriString();

            return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
        });
    }

    Mono<String> getNeworkAreaDiagram(UUID studyUuid, UUID nodeUuid, List<String> voltageLevelsIds, int depth) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

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

            return webClient.get()
                    .uri(singleLineDiagramServerBaseUri + path)
                    .retrieve()
                    .bodyToMono(String.class);
        });
    }

    public Mono<String> getSecurityAnalysisStatus(UUID nodeUuid) {
        return getSecurityAnalysisResultUuid(nodeUuid).flatMap(resultUuid ->
            Mono.justOrEmpty(resultUuid).flatMap(uuid -> {
                String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
                    .buildAndExpand(resultUuid)
                    .toUriString();
                return webClient
                    .get()
                    .uri(securityAnalysisServerBaseUri + path)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new StudyException(SECURITY_ANALYSIS_NOT_FOUND)))
                    .bodyToMono(String.class);
            }));
    }

    private Mono<Void> invalidateSaStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                .queryParam(RESULT_UUID, uuids)
                .build()
                .toUriString();
            return webClient
                .put()
                .uri(securityAnalysisServerBaseUri + path)
                .retrieve()
                .bodyToMono(Void.class);
        } else {
            return Mono.empty();
        }
    }

    public Mono<Void> invalidateSecurityAnalysisStatus(UUID nodeUuid) {
        return networkModificationTreeService.getSecurityAnalysisResultUuidsFromNode(nodeUuid).flatMap(this::invalidateSaStatus);
    }

    public Mono<Void> invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        return networkModificationTreeService.getStudySecurityAnalysisResultUuids(studyUuid).flatMap(this::invalidateSaStatus);
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

    public Mono<Void> stopSecurityAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        return getSecurityAnalysisResultUuid(nodeUuid).flatMap(resultUuid ->
           Mono.justOrEmpty(resultUuid).flatMap(uuid -> {
               String receiver;
               try {
                   receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)), StandardCharsets.UTF_8);
               } catch (JsonProcessingException e) {
                   throw new UncheckedIOException(e);
               }
               String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                   .queryParam(QUERY_PARAM_RECEIVER, receiver)
                   .buildAndExpand(resultUuid)
                   .toUriString();
               return webClient
                   .put()
                   .uri(securityAnalysisServerBaseUri + path)
                   .retrieve()
                   .bodyToMono(Void.class);
           }));
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeSaStopped() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE)
            .flatMap(message -> {
                String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
                if (receiver != null) {
                    Receiver receiverObj;
                    try {
                        receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), Receiver.class);

                        LOGGER.info("Security analysis stopped for node '{}'", receiverObj.getNodeUuid());

                        // delete security analysis result in database
                        return updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null)
                            .then(Mono.fromCallable(() -> {
                                // send notification for stopped computation
                                UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                                emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
                                return null;
                            }));
                    } catch (JsonProcessingException e) {
                        LOGGER.error(e.toString());
                    }
                }
                return Mono.empty();
            })
            .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
            .subscribe();
    }

    // wrappers to Mono/Flux for repositories

    private Mono<StudyEntity> insertStudyEntity(UUID uuid, String userId, UUID networkUuid, String networkId,
                                                String caseFormat, UUID caseUuid, boolean casePrivate,
                                                LoadFlowParametersEntity loadFlowParameters,
                                                UUID importReportUuid) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowParameters);
        return Mono.fromCallable(() -> {
            StudyEntity studyEntity = new StudyEntity(uuid, userId, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, caseFormat, caseUuid, casePrivate, defaultLoadflowProvider, loadFlowParameters);
            return insertStudy(studyEntity, importReportUuid);
        });
    }

    @Transactional
    public StudyEntity insertStudy(StudyEntity studyEntity, UUID importReportUuid) {
        var study = studyRepository.save(studyEntity);
        // create 2 nodes : root node, modification node 0
        NodeEntity rootNodeEntity = networkModificationTreeService.createRoot(studyEntity, importReportUuid);
        NetworkModificationNode modificationNode = NetworkModificationNode
            .builder()
            .name("modification node 0")
            .variantId(FIRST_VARIANT_ID)
            .loadFlowStatus(LoadFlowStatus.NOT_DONE)
            .buildStatus(BuildStatus.BUILT)
            .build();
        networkModificationTreeService.createNode(studyEntity.getId(), rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER).subscribe();

        return study;
    }

    Mono<Void> updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        return networkModificationTreeService.updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid);
    }

    Mono<Void> updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        return networkModificationTreeService.updateLoadFlowStatus(nodeUuid, loadFlowStatus);
    }

    private Mono<StudyCreationRequestEntity> insertStudyCreationRequestEntity(String userId, UUID studyUuid) {
        return Mono.fromCallable(() -> {
            StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(studyUuid == null ? UUID.randomUUID() : studyUuid, userId, LocalDateTime.now(ZoneOffset.UTC));
            return studyCreationRequestRepository.save(studyCreationRequestEntity);
        });
    }

    private Mono<Void> updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        return networkModificationTreeService.updateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus, updateChildren);
    }

    private Mono<Void> updateLoadFlowParameters(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity) {
        return Mono.fromRunnable(() -> self.doUpdateLoadFlowParameters(studyUuid, loadFlowParametersEntity));
    }

    @Transactional
    public void doUpdateLoadFlowParameters(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setLoadFlowParameters(loadFlowParametersEntity));
    }

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        studyUpdatePublisher.send("publishStudyUpdate-out-0", message);
    }

    Mono<List<String>> getAvailableSvgComponentLibraries() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-component-libraries")
            .toUriString();

        return webClient.get()
            .uri(singleLineDiagramServerBaseUri + path)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<>() {
            });
    }

    public Mono<String> getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    public Mono<Void> createEquipment(UUID studyUuid, String createEquipmentAttributes, ModificationType modificationType, UUID nodeUuid) {
        return getNodeModificationInfos(nodeUuid).flatMap(nodeInfos -> {
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid);

            return networkModificationService.createEquipment(studyUuid, createEquipmentAttributes, groupUuid, modificationType, variantId, reportUuid)
                    .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                    .collect(Collectors.toSet())
                    .doOnSuccess(substationIds ->
                            emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds)
                    )
                    .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                    .then(monoUpdateStatusResult);
        });
    }

    public Mono<Void> modifyEquipment(UUID studyUuid, String modifyEquipmentAttributes, ModificationType modificationType, UUID nodeUuid) {
        return getNodeModificationInfos(nodeUuid).flatMap(nodeInfos -> {
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid);

            return networkModificationService.modifyEquipment(studyUuid, modifyEquipmentAttributes, groupUuid, modificationType, variantId, reportUuid)
                    .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                    .collect(Collectors.toSet())
                    .doOnSuccess(substationIds ->
                            emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds)
                    )
                    .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                    .then(monoUpdateStatusResult);
        });
    }

    public Mono<Void> updateEquipmentCreation(UUID studyUuid, String createEquipmentAttributes, ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {

        Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid, false);

        return networkModificationService.updateEquipmentCreation(createEquipmentAttributes, modificationType, modificationUuid)
            .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
            .then(monoUpdateStatusResult);
    }

    public Mono<Void> updateEquipmentModification(UUID studyUuid, String modifyEquipmentAttributes, ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {
        Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid, false);

        return networkModificationService.updateEquipmentModification(modifyEquipmentAttributes, modificationType, modificationUuid)
                .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                .then(monoUpdateStatusResult);
    }

    Mono<Void> deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID nodeUuid) {
        return getNodeModificationInfos(nodeUuid).flatMap(nodeInfos -> {
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid);

            return networkModificationService.deleteEquipment(studyUuid, equipmentType, equipmentId, groupUuid, variantId, reportUuid)
                .flatMap(modification -> Flux.fromIterable(Collections.singletonList(modification)))
                .collect(Collectors.toList())
                .doOnSuccess(deletionInfos -> deletionInfos.forEach(deletionInfo ->
                        emitStudyEquipmentDeleted(studyUuid, nodeUuid, UPDATE_TYPE_STUDY,
                            deletionInfo.getSubstationIds(), deletionInfo.getEquipmentType(), deletionInfo.getEquipmentId())
                    )
                )
                .doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                .then(monoUpdateStatusResult);
        });
    }

    Mono<List<VoltageLevelInfos>> getVoltageLevels(UUID studyUuid, UUID nodeUuid) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/voltage-levels");
            if (!StringUtils.isBlank(variantId)) {
                builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            String path = builder.buildAndExpand(networkUuid).toUriString();

            Mono<List<VoltageLevelMapData>> voltageLevelsMapData = webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {
                });

            return voltageLevelsMapData.map(d -> d.stream()
                .map(e -> VoltageLevelInfos.builder().id(e.getId()).name(e.getName()).substationId(e.getSubstationId()).build())
                .collect(Collectors.toList()));
        });
    }

    Mono<List<IdentifiableInfos>> getVoltageLevelBusesOrBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
                                                                       String busPath) {
        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String variantId = tuple.getT2();

            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + busPath);
            if (!StringUtils.isBlank(variantId)) {
                builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            String path = builder.buildAndExpand(networkUuid, voltageLevelId).toUriString();

            return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {
                });
        });
    }

    Mono<List<IdentifiableInfos>> getVoltageLevelBuses(UUID studyUuid, UUID nodeUuid, String voltageLevelId) {
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuid, voltageLevelId, "configured-buses");
    }

    Mono<List<IdentifiableInfos>> getVoltageLevelBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId) {
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuid, voltageLevelId, "busbar-sections");
    }

    public Mono<LoadFlowStatus> getLoadFlowStatus(UUID nodeUuid) {
        return networkModificationTreeService.getLoadFlowStatus(nodeUuid);
    }

    public Mono<UUID> getSecurityAnalysisResultUuid(UUID nodeUuid) {
        return networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);
    }

    @Transactional(readOnly = true)
    public UUID getStudyUuidFromNodeUuid(UUID nodeUuid) {
        return networkModificationTreeService.getStudyUuidForNodeId(nodeUuid);
    }

    public Mono<LoadFlowInfos> getLoadFlowInfos(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        return networkModificationTreeService.getLoadFlowInfos(nodeUuid);
    }

    private Mono<BuildInfos> getBuildInfos(UUID nodeUuid) {
        return Mono.fromCallable(() -> networkModificationTreeService.getBuildInfos(nodeUuid));
    }

    public Mono<Void> buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        return getBuildInfos(nodeUuid).flatMap(infos -> networkModificationService.buildNode(studyUuid, nodeUuid, infos))
            .then(updateBuildStatus(nodeUuid, BuildStatus.BUILDING));
    }

    public Mono<Void> stopBuild(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        return networkModificationService.stopBuild(studyUuid, nodeUuid);
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeBuildResult() {
        return f -> f.log(StudyService.CATEGORY_BROKER_INPUT, Level.FINE)
            .flatMap(message -> {
                Set<String> substationsIds = Stream.of(message.getPayload().trim().split(",")).collect(Collectors.toSet());
                String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
                if (receiver != null) {
                    Receiver receiverObj;
                    try {
                        receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), Receiver.class);

                        LOGGER.info("Build completed for node '{}'", receiverObj.getNodeUuid());

                        return updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.BUILT)
                            .then(Mono.fromRunnable(() -> {
                                // send notification
                                UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                                emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_BUILD_COMPLETED, substationsIds);
                            }));
                    } catch (JsonProcessingException e) {
                        LOGGER.error(e.toString());
                    }
                }
                return Mono.empty();
            })
            .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
            .subscribe();
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeBuildStopped() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE)
            .flatMap(message -> {
                String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
                if (receiver != null) {
                    Receiver receiverObj;
                    try {
                        receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), Receiver.class);

                        LOGGER.info("Build stopped for node '{}'", receiverObj.getNodeUuid());

                        return updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.NOT_BUILT)
                            .then(Mono.fromRunnable(() -> {
                                // send notification
                                UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                                emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_BUILD_CANCELLED);
                            }));
                    } catch (JsonProcessingException e) {
                        LOGGER.error(e.toString());
                    }
                }
                return Mono.empty();
            })
            .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
            .subscribe();
    }

    Mono<Void> updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        return networkModificationTreeService.updateBuildStatus(nodeUuid, buildStatus);
    }

    Mono<Void> invalidateBuildStatus(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        return networkModificationTreeService.invalidateBuildStatus(nodeUuid, invalidateOnlyChildrenBuildStatus);
    }

    private Mono<Void> updateStatuses(UUID studyUuid, UUID nodeUuid) {
        return updateStatuses(studyUuid, nodeUuid, true);
    }

    private Mono<Void> updateStatuses(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        return updateLoadFlowStatus(nodeUuid, LoadFlowStatus.NOT_DONE)
            .doOnSuccess(e -> emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LOADFLOW_STATUS))
            .then(invalidateSecurityAnalysisStatus(nodeUuid)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)))
            .then(invalidateBuildStatus(nodeUuid, invalidateOnlyChildrenBuildStatus));
    }

    public Mono<Void> changeModificationActiveState(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID modificationUuid, boolean active) {
        if (!self.getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        return networkModificationTreeService.handleExcludeModification(nodeUuid, modificationUuid, active)
            .then(updateStatuses(studyUuid, nodeUuid, false));
    }

    @Transactional
    public Mono<Void> deleteModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids) {
        if (!getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        return networkModificationTreeService.getModificationGroupUuid(nodeUuid).flatMap(groupId ->
            networkModificationService.deleteModifications(groupId, modificationsUuids)
        ).doOnSuccess(
                e -> networkModificationTreeService.removeModificationsToExclude(nodeUuid, modificationsUuids)
                    .doOnSuccess(r -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                    .then(updateStatuses(studyUuid, nodeUuid, false))
                    .subscribe()
        );
    }

    private Mono<Void> deleteSaResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();
        return webClient
            .delete()
            .uri(securityAnalysisServerBaseUri + path)
            .retrieve()
            .bodyToMono(Void.class);
    }

    @Transactional
    public DeleteNodeInfos doDeleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren) {
        DeleteNodeInfos deleteNodeInfos = new DeleteNodeInfos();
        deleteNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid).orElse(null));
        networkModificationTreeService.doDeleteNode(studyUuid, nodeId, deleteChildren, deleteNodeInfos);
        return deleteNodeInfos;
    }

    public Mono<Void> deleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        return Mono.fromCallable(() -> self.doDeleteNode(studyUuid, nodeId, deleteChildren))
            .flatMap(Mono::justOrEmpty)
            .map(u -> {
                startTime.set(System.nanoTime());
                return u;
            })
            .publish(deleteNodeInfosMono ->
                Mono.when(// in parallel
                    // delete modifications
                    deleteNodeInfosMono.flatMapMany(infos -> Flux.fromIterable(infos.getModificationGroupUuids())).flatMap(networkModificationService::deleteModifications),
                    // delete reports
                    deleteNodeInfosMono.flatMapMany(infos -> Flux.fromIterable(infos.getReportUuids())).flatMap(reportService::deleteReport),
                    // delete security analysis result
                    deleteNodeInfosMono.flatMapMany(infos -> Flux.fromIterable(infos.getSecurityAnalysisResultUuids())).flatMap(this::deleteSaResult),
                    // delete network variant
                    deleteNodeInfosMono.flatMap(infos -> networkStoreService.deleteVariants(infos.getNetworkUuid(), infos.getVariantIds()))
                )
            )
            .doOnSuccess(r -> {
                if (startTime.get() != null) {
                    LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId, studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                }
            })
            .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable));
    }

    public Mono<Void> reindexStudy(UUID studyUuid) {
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

            return webClient.post()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse -> Mono.error(new StudyException(NETWORK_INDEXATION_FAILED)))
                .bodyToMono(Void.class)
                .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                // invalid all built model nodes in study
                .then(invalidateBuildStatus(networkModificationTreeService.getStudyRootNodeUuid(studyUuid), false))
                .doOnSuccess(r -> LOGGER.info("Study with id = '{}' has been reindexed", studyUuid));
        } else {
            return Mono.error(new StudyException(STUDY_NOT_FOUND));
        }
    }

    public Mono<Void> reorderModification(UUID studyUuid, UUID nodeUuid, UUID modificationUuid, UUID beforeUuid) {
        checkStudyContainsNode(studyUuid, nodeUuid);
        return networkModificationTreeService.getModificationGroupUuid(nodeUuid).flatMap(groupUuid ->
            networkModificationService.reorderModification(groupUuid, modificationUuid, beforeUuid)
        ).then(updateStatuses(studyUuid, nodeUuid))
        .doOnSuccess(r -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid));
    }

    private void checkStudyContainsNode(UUID studyUuid, UUID nodeUuid) {
        if (!getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public Mono<UUID> getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    public Flux<Pair<UUID, String>> getReportUuidsAndNames(UUID nodeUuid, boolean nodeOnlyReport) {
        return networkModificationTreeService.getReportUuidsAndNames(nodeUuid, nodeOnlyReport);
    }

    public Flux<ReporterModel> getNodeReport(UUID studyUuid, UUID nodeUuid, boolean nodeOnlyReport) {
        return getReportUuidsAndNames(nodeUuid, nodeOnlyReport).concatMap(info ->
            reportService.getReport(info.getLeft()).switchIfEmpty(Mono.just(new ReporterModel(UUID.randomUUID().toString(), info.getLeft().toString())))
                .map(reporter -> {
                    // set reporter default name to node name
                    ReporterModel newReporter = new ReporterModel(reporter.getTaskKey(), info.getRight(), reporter.getTaskValues());
                    reporter.getReports().forEach(newReporter::report);
                    reporter.getSubReporters().forEach(newReporter::addSubReporter);
                    return newReporter;
                })
        );
    }

    public Mono<Void> deleteNodeReport(UUID studyUuid, UUID nodeUuid) {
        return getReportUuid(nodeUuid).flatMap(reportService::deleteReport);
    }

    private Mono<NodeModificationInfos> getNodeModificationInfos(UUID nodeUuid) {
        return networkModificationTreeService.getNodeModificationInfos(nodeUuid);
    }

    public String getDefaultLoadflowProviderValue() {
        return defaultLoadflowProvider;
    }

    public Mono<Void> lineSplitWithVoltageLevel(UUID studyUuid, String lineSplitWithVoltageLevelAttributes,
        ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {

        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(lineSplitWithVoltageLevelAttributes);

        return getNodeModificationInfos(nodeUuid).flatMap(nodeInfos -> {
            UUID groupUuid = nodeInfos.getModificationGroupUuid();
            String variantId = nodeInfos.getVariantId();
            UUID reportUuid = nodeInfos.getReportUuid();

            Mono<Void> monoUpdateStatusResult = updateStatuses(studyUuid, nodeUuid, modificationUuid == null);

            Flux<ModificationInfos> modificationInfosFlux;
            if (modificationUuid == null) {
                modificationInfosFlux = networkModificationService.splitLineWithVoltageLevel(studyUuid, lineSplitWithVoltageLevelAttributes,
                    groupUuid, modificationType, variantId, reportUuid);
            } else {

                modificationInfosFlux = networkModificationService.updateLineSplitWithVoltageLevel(lineSplitWithVoltageLevelAttributes,
                    modificationType, modificationUuid);
            }

            return modificationInfosFlux
                .collect(Collectors.toSet())
                .doOnSuccess(modifications -> {
                    Set<String> allImpactedSubstationIds = modifications.stream()
                        .map(ModificationInfos::getSubstationIds).flatMap(Set::stream).collect(Collectors.toSet());
                    List<EquipmentModificationInfos> deletions = modifications.stream()
                        .filter(modif -> modif.getType() == ModificationType.EQUIPMENT_DELETION)
                        .map(EquipmentModificationInfos.class::cast)
                        .collect(Collectors.toList());
                    deletions.forEach(modif -> emitStudyEquipmentDeleted(studyUuid, nodeUuid, UPDATE_TYPE_STUDY,
                        allImpactedSubstationIds, modif.getEquipmentType(), modif.getEquipmentId()));
                }).doOnSuccess(e -> networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid))
                .then(monoUpdateStatusResult);
        });
    }
}

