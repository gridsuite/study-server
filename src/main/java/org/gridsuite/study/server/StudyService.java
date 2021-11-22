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
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.lang.NonNull;
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

    private static final String CATEGORY_BROKER_INPUT = StudyService.class.getName() + ".input-broker-messages";

    private static final String CATEGORY_BROKER_OUTPUT = StudyService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    static final String HEADER_USER_ID = "userId";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_IS_PUBLIC_STUDY = "isPublicStudy";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_STUDIES = "studies";
    static final String UPDATE_TYPE_LOADFLOW = "loadflow";
    static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    static final String UPDATE_TYPE_SWITCH = "switch";
    static final String UPDATE_TYPE_LINE = "line";
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_RESULT = "securityAnalysisResult";
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_STATUS = "securityAnalysis_status";
    static final String HEADER_ERROR = "error";
    static final String UPDATE_TYPE_STUDY = "study";
    static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID = "deletedEquipmentId";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE = "deletedEquipmentType";
    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";
    static final String RECEIVER = "receiver";

    // Self injection for @transactional support in internal calls to other methods of this service
    @Autowired
    StudyService self;

    NetworkModificationTreeService networkModificationTreeService;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Receiver {
        private UUID studyUuid;
    }

    private final WebClient webClient;

    private String caseServerBaseUri;
    private String singleLineDiagramServerBaseUri;
    private String networkConversionServerBaseUri;
    private String geoDataServerBaseUri;
    private String networkMapServerBaseUri;
    private String loadFlowServerBaseUri;
    private String securityAnalysisServerBaseUri;
    private String actionsServerBaseUri;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkStoreService networkStoreService;
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
                    UUID resultUuid = UUID.fromString(message.getHeaders().get("resultUuid", String.class));
                    String receiver = message.getHeaders().get(RECEIVER, String.class);
                    if (receiver != null) {
                        Receiver receiverObj;
                        try {
                            receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), Receiver.class);

                            LOGGER.info("Security analysis result '{}' available for study '{}'",
                                    resultUuid, receiverObj.getStudyUuid());

                            // update DB
                            return updateSecurityAnalysisResultUuid(receiverObj.getStudyUuid(), resultUuid)
                                    .then(Mono.fromCallable(() -> {
                                        // send notifications
                                        emitStudyChanged(receiverObj.getStudyUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
                                        emitStudyChanged(receiverObj.getStudyUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_RESULT);
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
            StudyRepository studyRepository,
            StudyCreationRequestRepository studyCreationRequestRepository,
            NetworkStoreService networkStoreService,
            NetworkModificationService networkModificationService,
            ReportService reportService,
            StudyInfosService studyInfosService,
            EquipmentInfosService equipmentInfosService,
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
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    private static StudyInfos toStudyInfos(StudyEntity entity) {
        return StudyInfos.builder()
                .id(entity.getId())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .userId(entity.getUserId())
                .caseFormat(entity.getCaseFormat())
                .loadFlowStatus(entity.getLoadFlowStatus())
                .loadFlowResult(fromEntity(entity.getLoadFlowResult()))
                .studyPrivate(entity.isPrivate())
                .build();
    }

    private static BasicStudyInfos toBasicStudyInfos(StudyCreationRequestEntity entity) {
        return BasicStudyInfos.builder()
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .userId(entity.getUserId())
                .id(entity.getId())
                .studyPrivate(entity.getIsPrivate())
                .build();
    }

    private static CreatedStudyBasicInfos toCreatedStudyBasicInfos(StudyEntity entity) {
        return CreatedStudyBasicInfos.builder()
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .userId(entity.getUserId())
                .id(entity.getId())
                .caseFormat(entity.getCaseFormat())
                .studyPrivate(entity.isPrivate())
                .build();
    }

    public Flux<CreatedStudyBasicInfos> getStudyList(String userId) {
        return Flux.fromStream(() -> studyRepository.findByUserIdOrIsPrivate(userId, false).stream())
                .map(StudyService::toCreatedStudyBasicInfos)
                .sort(Comparator.comparing(CreatedStudyBasicInfos::getCreationDate).reversed());
    }

    public Flux<CreatedStudyBasicInfos> getStudyListMetadata(List<UUID> uuids) {
        return Flux.fromStream(() -> studyRepository.findAllByUuids(uuids).stream().map(StudyService::toCreatedStudyBasicInfos));
    }

    Flux<BasicStudyInfos> getStudyCreationRequests(String userId) {
        return Flux.fromStream(() -> studyCreationRequestRepository.findByUserIdOrIsPrivate(userId, false).stream())
                .map(StudyService::toBasicStudyInfos)
                .sort(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed());
    }

    public Mono<BasicStudyInfos> createStudy(UUID caseUuid, String userId, Boolean isPrivate, UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        return insertStudyCreationRequest(userId, isPrivate, studyUuid)
                .doOnSubscribe(x -> startTime.set(System.nanoTime()))
                .map(StudyService::toBasicStudyInfos)
                .doOnSuccess(s -> Mono.zip(persistentStore(caseUuid, s.getId(), userId, isPrivate), getCaseFormat(caseUuid))
                        .flatMap(t -> {
                            LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                            return insertStudy(s.getId(), userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                    t.getT2(), caseUuid, false, LoadFlowStatus.NOT_DONE, null, toEntity(loadFlowParameters), null);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                        .doFinally(st -> {
                            deleteStudyIfNotCreationInProgress(s.getId(), userId).subscribe();
                            LOGGER.trace("Create study '{}' : {} seconds", s.getId(), TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                        })
                        .subscribe()
                );
    }

    public Mono<BasicStudyInfos> createStudy(Mono<FilePart> caseFile, String userId, Boolean isPrivate, UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        return insertStudyCreationRequest(userId, isPrivate, studyUuid)
                .doOnSubscribe(x -> startTime.set(System.nanoTime()))
                .map(StudyService::toBasicStudyInfos)
                .doOnSuccess(s -> importCase(caseFile, s.getId(), userId, isPrivate)
                        .flatMap(uuid ->
                                Mono.zip(persistentStore(uuid, s.getId(), userId, isPrivate), getCaseFormat(uuid))
                                        .flatMap(t -> {
                                            LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
                                            return insertStudy(s.getId(), userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                                    t.getT2(), uuid, true, LoadFlowStatus.NOT_DONE, null, toEntity(loadFlowParameters), null);
                                        }))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                        .doFinally(r -> {
                            deleteStudyIfNotCreationInProgress(s.getId(), userId).subscribe();  // delete the study if the creation has been canceled
                            LOGGER.trace("Create study '{}' : {} seconds", s.getId(), TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                        })
                        .subscribe()
                );
    }

    public Mono<StudyInfos> getCurrentUserStudy(UUID studyUuid, String headerUserId) {
        Mono<StudyEntity> studyMono = getStudyWithPreFetchedLoadFlowResult(studyUuid);
        return studyMono.flatMap(study -> {
            if (study.isPrivate() && !study.getUserId().equals(headerUserId)) {
                return Mono.error(new StudyException(NOT_ALLOWED));
            } else {
                return Mono.just(study);
            }
        }).map(StudyService::toStudyInfos);
    }

    Mono<StudyEntity> getStudyByUuid(UUID studyUuid) {
        return Mono.fromCallable(() -> studyRepository.findById(studyUuid).orElse(null));
    }

    @Transactional(readOnly = true)
    public StudyEntity doGetStudyWithPreFetchedLoadFlowResult(UUID studyUuid) {
        return studyRepository.findById(studyUuid).map(studyEntity -> {
            if (studyEntity.getLoadFlowResult() != null) {
                // This is a workaround to prepare the componentResultEmbeddables which will be used later in the webflux pipeline
                // The goal is to avoid LazyInitializationException
                @SuppressWarnings("unused")
                int ignoreSize = studyEntity.getLoadFlowResult().getComponentResults().size();
                @SuppressWarnings("unused")
                int ignoreSize2 = studyEntity.getLoadFlowResult().getMetrics().size();
            }
            return studyEntity;
        }).orElse(null);
    }

    @Transactional
    public StudyEntity doGetStudyWithPreFetchedLoadFlowResultAndUpdateIsPrivate(UUID studyUuid, String headerUserId, boolean toPrivate) {
        StudyEntity studyEntity = doGetStudyWithPreFetchedLoadFlowResult(studyUuid);
        if (studyEntity != null) {
            //only the owner of a study can change the access rights
            if (!headerUserId.equals(studyEntity.getUserId())) {
                throw new StudyException(NOT_ALLOWED);
            }
            studyEntity.setPrivate(toPrivate);
        }
        return studyEntity;
    }

    public Mono<StudyEntity> getStudyWithPreFetchedLoadFlowResult(UUID studyUuid) {
        return Mono.fromCallable(() -> self.doGetStudyWithPreFetchedLoadFlowResult(studyUuid));
    }

    public Mono<StudyEntity> getStudyWithPreFetchedLoadFlowResultAndUpdateIsPrivate(UUID studyUuid, String headerUserId, boolean toPrivate) {
        return Mono.fromCallable(() -> self.doGetStudyWithPreFetchedLoadFlowResultAndUpdateIsPrivate(studyUuid, headerUserId, toPrivate));
    }

    Flux<CreatedStudyBasicInfos> searchStudies(@NonNull String query) {
        return Mono.fromCallable(() -> studyInfosService.search(query)).flatMapMany(Flux::fromIterable);
    }

    Flux<EquipmentInfos> searchEquipments(@NonNull UUID studyUuid, @NonNull String query) {
        return networkStoreService
                .getNetworkUuid(studyUuid)
                .flatMapIterable(networkUuid -> equipmentInfosService.search(String.format("networkUuid.keyword:(%s) AND %s", networkUuid, query)));
    }

    @Transactional
    public Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID uuid, String userId) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(uuid);
        UUID networkUuid = null;
        UUID groupUuid = null;
        if (studyCreationRequestEntity.isEmpty()) {
            networkUuid = networkStoreService.doGetNetworkUuid(uuid).orElse(null);
            groupUuid = doGetGroupUuid(uuid, false).orElse(null);
            studyRepository.findById(uuid).ifPresent(s -> {
                if (!s.getUserId().equals(userId)) {
                    throw new StudyException(NOT_ALLOWED);
                }
                networkModificationTreeService.doDeleteTree(uuid);
                studyRepository.deleteById(uuid);
                studyInfosService.deleteByUuid(uuid);
                emitStudiesChanged(uuid, userId, s.isPrivate());
            });
        } else {
            studyCreationRequestRepository.deleteById(studyCreationRequestEntity.get().getId());
            emitStudiesChanged(uuid, userId, studyCreationRequestEntity.get().getIsPrivate());
        }
        return networkUuid != null ? Optional.of(new DeleteStudyInfos(networkUuid, groupUuid)) : Optional.empty();
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
                            deleteStudyInfosMono.flatMap(infos -> infos.getGroupUuid() != null
                                ? Mono.just(infos.getGroupUuid())
                                : Mono.empty()).flatMap(networkModificationService::deleteNetworkModifications),
                            deleteStudyInfosMono.flatMap(infos -> deleteEquipmentIndexes(infos.getNetworkUuid())),
                                deleteStudyInfosMono.flatMap(infos -> reportService.deleteReport(infos.getNetworkUuid())),
                                deleteStudyInfosMono.flatMap(infos -> networkStoreService.deleteNetwork(infos.getNetworkUuid()))
                        )
                )
                .doOnSuccess(r -> {
                            if (startTime.get() != null) {
                                LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                            }
                        }
                )
                .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable));
    }

    public Mono<Void> deleteEquipmentIndexes(UUID networkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        return Mono.fromRunnable(() -> equipmentInfosService.deleteAll(networkUuid))
                .doOnSubscribe(x -> startTime.set(System.nanoTime()))
                .then()
                .doFinally(x -> LOGGER.trace("Indexes deletion for network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get())));
    }

    private Mono<CreatedStudyBasicInfos> insertStudy(UUID studyUuid, String userId, boolean isPrivate, UUID networkUuid, String networkId,
                                                     String caseFormat, UUID caseUuid, boolean casePrivate, LoadFlowStatus loadFlowStatus,
                                                     LoadFlowResultEntity loadFlowResult, LoadFlowParametersEntity loadFlowParameters, UUID securityAnalysisUuid) {
        return insertStudyEntity(studyUuid, userId, isPrivate, networkUuid, networkId, caseFormat, caseUuid, casePrivate, loadFlowStatus, loadFlowResult,
                loadFlowParameters, securityAnalysisUuid)
                .map(StudyService::toCreatedStudyBasicInfos)
                .map(studyInfosService::add)
                .doOnSuccess(infos -> emitStudiesChanged(studyUuid, userId, isPrivate));
    }

    private Mono<StudyCreationRequestEntity> insertStudyCreationRequest(String userId, boolean isPrivate, UUID studyUuid) {
        return insertStudyCreationRequestEntity(userId, isPrivate, studyUuid)
                .doOnSuccess(s -> emitStudiesChanged(s.getId(), userId, isPrivate));
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

    private Mono<? extends Throwable> handleStudyCreationError(UUID studyUuid, String userId, boolean isPrivate, ClientResponse clientResponse, String serverName) {
        return clientResponse.bodyToMono(String.class)
                .switchIfEmpty(Mono.just("{\"message\": \"" + serverName + ": " + clientResponse.statusCode() + "\"}"))
                .flatMap(body -> {
                    try {
                        JsonNode node = new ObjectMapper().readTree(body).path("message");
                        if (!node.isMissingNode()) {
                            emitStudyCreationError(studyUuid, userId, isPrivate, node.asText());
                        } else {
                            emitStudyCreationError(studyUuid, userId, isPrivate, body);
                        }
                    } catch (JsonProcessingException e) {
                        if (!body.isEmpty()) {
                            emitStudyCreationError(studyUuid, userId, isPrivate, body);
                        }
                    }
                    return Mono.error(new StudyException(STUDY_CREATION_FAILED));
                });
    }

    Mono<UUID> importCase(Mono<FilePart> multipartFile, UUID studyUuid, String userId, boolean isPrivate) {
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
                                    handleStudyCreationError(studyUuid, userId, isPrivate, clientResponse, "case-server")
                            )
                            .bodyToMono(UUID.class)
                            .publishOn(Schedulers.boundedElastic())
                            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
                })
                .doOnError(t -> !(t instanceof StudyException), t -> emitStudyCreationError(studyUuid, userId, isPrivate, t.getMessage()));
    }

    Mono<byte[]> getVoltageLevelSvg(UUID networkUuid, String voltageLevelId, boolean useName, boolean centerLabel, boolean diagonalLabel,
                                    boolean topologicalColoring, String componentLibrary) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .queryParamIfPresent("componentLibrary", Optional.ofNullable(componentLibrary))
                .buildAndExpand(networkUuid, voltageLevelId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    Mono<String> getVoltageLevelSvgAndMetadata(UUID networkUuid, String voltageLevelId, boolean useName, boolean centerLabel, boolean diagonalLabel,
                                               boolean topologicalColoring, String componentLibrary) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-and-metadata/{networkUuid}/{voltageLevelId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .queryParamIfPresent("componentLibrary", Optional.ofNullable(componentLibrary))
                .buildAndExpand(networkUuid, voltageLevelId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    private Mono<NetworkInfos> persistentStore(UUID caseUuid, UUID studyUuid, String userId, boolean isPrivate) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid)
                .buildAndExpand()
                .toUriString();

        return webClient.post()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                        handleStudyCreationError(studyUuid, userId, isPrivate, clientResponse, "network-conversion-server")
                )
                .bodyToMono(NetworkInfos.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE)
                .doOnError(t -> !(t instanceof StudyException), t -> emitStudyCreationError(studyUuid, userId, isPrivate, t.getMessage()));
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

    Mono<String> getEquipmentsMapData(UUID networkUuid, List<String> substationsIds, String equipmentPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/" + equipmentPath + "/{networkUuid}");
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    Mono<String> getSubstationsMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "substations");
    }

    Mono<String> getLinesMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "lines");
    }

    Mono<String> getTwoWindingsTransformersMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "2-windings-transformers");
    }

    Mono<String> getThreeWindingsTransformersMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "3-windings-transformers");
    }

    Mono<String> getGeneratorsMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "generators");
    }

    Mono<String> getBatteriesMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "batteries");
    }

    Mono<String> getDanglingLinesMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "dangling-lines");
    }

    Mono<String> getHvdcLinesMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "hvdc-lines");
    }

    Mono<String> getLccConverterStationsMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "lcc-converter-stations");
    }

    Mono<String> getVscConverterStationsMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "vsc-converter-stations");
    }

    Mono<String> getLoadsMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "loads");
    }

    Mono<String> getShuntCompensatorsMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "shunt-compensators");
    }

    Mono<String> getStaticVarCompensatorsMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "static-var-compensators");
    }

    Mono<String> getAllMapData(UUID networkUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkUuid, substationsIds, "all");
    }

    Mono<Void> changeSwitchState(UUID studyUuid, String switchId, boolean open) {
        return getGroupUuid(studyUuid, true).flatMap(groupUuid -> {
            Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                    .then(invalidateSecurityAnalysisStatus(studyUuid)
                            .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)))
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SWITCH));

            return networkModificationService.changeSwitchState(studyUuid, switchId, open, groupUuid)
                    .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                    .collect(Collectors.toSet())
                    .doOnSuccess(substationIds ->
                            emitStudyChanged(studyUuid, UPDATE_TYPE_STUDY, substationIds)
                    )
                    .then(monoUpdateLfState);
        });
    }

    public Mono<Void> applyGroovyScript(UUID studyUuid, String groovyScript) {
        return getGroupUuid(studyUuid, true).flatMap(groupUuid -> {
            Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                    .then(invalidateSecurityAnalysisStatus(studyUuid)
                            .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)));

            return networkModificationService.applyGroovyScript(studyUuid, groovyScript, groupUuid)
                    .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                    .collect(Collectors.toSet())
                    .doOnSuccess(substationIds ->
                            emitStudyChanged(studyUuid, UPDATE_TYPE_STUDY, substationIds)
                    )
                    .then(monoUpdateLfState);
        });
    }

    Mono<Void> runLoadFlow(UUID studyUuid) {
        return setLoadFlowRunning(studyUuid).then(Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getLoadFlowProvider(studyUuid))).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String provider = tuple.getT2();
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                    .queryParam("reportId", networkUuid.toString()).queryParam("reportName", "loadflow").queryParam("overwrite", true);
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
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
                    .flatMap(result -> updateLoadFlowResultAndStatus(studyUuid, toEntity(result), result.isOk() ? LoadFlowStatus.CONVERGED : LoadFlowStatus.DIVERGED))
                    .doOnError(e -> updateLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE).subscribe())
                    .doOnCancel(() -> updateLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE).subscribe());
        }).doFinally(s ->
                emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW)
        );
    }

    private Mono<Void> setLoadFlowRunning(UUID studyUuid) {
        return updateLoadFlowStatus(studyUuid, LoadFlowStatus.RUNNING)
                .doOnSuccess(s -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS));
    }

    public Mono<Collection<String>> getExportFormats() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/export/formats")
                .toUriString();

        ParameterizedTypeReference<Collection<String>> typeRef = new ParameterizedTypeReference<Collection<String>>() {
        };

        return webClient.get()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .bodyToMono(typeRef);
    }

    public Mono<ExportNetworkInfos> exportNetwork(UUID studyUuid, String format) {
        Mono<UUID> networkUuidMono = networkStoreService.getNetworkUuid(studyUuid);

        return networkUuidMono.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export/{format}")
                    .buildAndExpand(uuid, format)
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

    public Mono<StudyInfos> changeStudyAccessRights(UUID studyUuid, String headerUserId, boolean toPrivate) {
        return getStudyWithPreFetchedLoadFlowResultAndUpdateIsPrivate(studyUuid, headerUserId, toPrivate)
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)))
                .map(StudyService::toStudyInfos);
    }

    public Mono<Void> changeLineStatus(UUID studyUuid, String lineId, String status) {
        return getGroupUuid(studyUuid, true).flatMap(groupUuid -> {
            Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                .then(invalidateSecurityAnalysisStatus(studyUuid)
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)))
                .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LINE));

            return networkModificationService.applyLineChanges(studyUuid, lineId, status, groupUuid)
                .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                .collect(Collectors.toSet())
                .doOnSuccess(substationIds ->
                    emitStudyChanged(studyUuid, UPDATE_TYPE_STUDY, substationIds)
                )
                .then(monoUpdateLfState);
        });
    }

    private void emitStudiesChanged(UUID studyUuid, String userId, boolean isPrivateStudy) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_IS_PUBLIC_STUDY, !isPrivateStudy)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
                .build());
    }

    private void emitStudyChanged(UUID studyUuid, String updateType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .build()
        );
    }

    private void emitStudyCreationError(UUID studyUuid, String userId, boolean isPrivate, String errorMessage) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_IS_PUBLIC_STUDY, !isPrivate)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
                .setHeader(HEADER_ERROR, errorMessage)
                .build()
        );
    }

    private void emitStudyChanged(UUID studyUuid, String updateType, Set<String> substationsIds) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
                .build()
        );
    }

    private void emitStudyEquipmentDeleted(UUID studyUuid, String updateType, Set<String> substationsIds, String equipmentType, String equipmentId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
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

    public Mono<Void> assertLoadFlowRunnable(UUID studyUuid) {
        Mono<StudyEntity> studyMono = getStudyByUuid(studyUuid);
        return studyMono.map(StudyEntity::getLoadFlowStatus)
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)))
                .flatMap(lfs -> lfs.equals(LoadFlowStatus.NOT_DONE) ? Mono.empty() : Mono.error(new StudyException(LOADFLOW_NOT_RUNNABLE)));
    }

    private Mono<Void> assertLoadFlowNotRunning(UUID studyUuid) {
        return getStudyByUuid(studyUuid).map(StudyEntity::getLoadFlowStatus)
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)))
                .flatMap(lfs -> lfs.equals(LoadFlowStatus.RUNNING) ? Mono.error(new StudyException(LOADFLOW_RUNNING)) : Mono.empty());
    }

    private Mono<Void> assertSecurityAnalysisNotRunning(UUID studyUuid) {
        Mono<String> statusMono = getSecurityAnalysisStatus(studyUuid);
        return statusMono
                .flatMap(s -> s.equals(SecurityAnalysisStatus.RUNNING.name()) ? Mono.error(new StudyException(SECURITY_ANALYSIS_RUNNING)) : Mono.empty());
    }

    public Mono<Void> assertComputationNotRunning(UUID studyUuid) {
        return assertLoadFlowNotRunning(studyUuid).and(assertSecurityAnalysisNotRunning(studyUuid));
    }

    public static LoadFlowParametersEntity toEntity(LoadFlowParameters parameters) {
        Objects.requireNonNull(parameters);
        return new LoadFlowParametersEntity(parameters.getVoltageInitMode(),
                parameters.isTransformerVoltageControlOn(),
                parameters.isNoGeneratorReactiveLimits(),
                parameters.isPhaseShifterRegulationOn(),
                parameters.isTwtSplitShuntAdmittance(),
                parameters.isSimulShunt(),
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
                LoadFlowParameters.ConnectedComponentMode.MAIN); // FIXME to persist
    }

    public static LoadFlowResultEntity toEntity(LoadFlowResult result) {
        Objects.requireNonNull(result);
        return new LoadFlowResultEntity(result.isOk(),
                result.getMetrics(),
                result.getLogs(),
                result.getComponentResults().stream().map(StudyService::toEntity).collect(Collectors.toList()));
    }

    public static LoadFlowResult fromEntity(LoadFlowResultEntity entity) {
        return entity == null ? null : new LoadFlowResultImpl(entity.isOk(),
                entity.getMetrics(),
                entity.getLogs(),
                entity.getComponentResults().stream().map(StudyService::fromEntity).collect(Collectors.toList()));
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

    @Transactional
    public LoadFlowParameters doGetLoadFlowParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
                .map(studyEntity -> fromEntity(studyEntity.getLoadFlowParameters()))
                .orElse(null);
    }

    public Mono<LoadFlowParameters> getLoadFlowParameters(UUID studyUuid) {
        return Mono.fromCallable(() -> self.doGetLoadFlowParameters(studyUuid));
    }

    Mono<Void> setLoadFlowParameters(UUID studyUuid, LoadFlowParameters parameters) {
        return updateLoadFlowParametersAndStatus(studyUuid, toEntity(parameters != null ? parameters : LoadFlowParameters.load()), LoadFlowStatus.NOT_DONE)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                .then(invalidateSecurityAnalysisStatus(studyUuid)
                        .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)));
    }

    @Transactional
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
        studyEntity.ifPresent(studyEntity1 -> {
            studyEntity1.setLoadFlowProvider(provider);
            studyEntity1.setLoadFlowStatus(LoadFlowStatus.NOT_DONE);
        });
    }

    public Mono<Void> updateLoadFlowProvider(UUID studyUuid, String provider) {
        Mono<Void> updateProvider = Mono.fromRunnable(() -> self.doUpdateLoadFlowProvider(studyUuid, provider));
        return updateProvider.doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS));
    }

    public Mono<UUID> runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, String parameters) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(parameters);

        return Mono.zip(networkStoreService.getNetworkUuid(studyUuid), getLoadFlowProvider(studyUuid)).flatMap(tuple -> {
            UUID networkUuid = tuple.getT1();
            String provider = tuple.getT2();

            String receiver;
            try {
                receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(studyUuid)), StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                return Mono.error(new UncheckedIOException(e));
            }
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save");
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
            }
            var path = uriComponentsBuilder
                    .queryParam("contingencyListName", contingencyListNames)
                    .queryParam(RECEIVER, receiver)
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
                updateSecurityAnalysisResultUuid(studyUuid, result)
                        .doOnSuccess(e -> emitStudyChanged(studyUuid, StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS))
                        .thenReturn(result)
        );
    }

    public Mono<String> getSecurityAnalysisResult(UUID studyUuid, List<String> limitTypes) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(limitTypes);

        return getStudyByUuid(studyUuid).flatMap(entity -> {
            UUID resultUuid = entity.getSecurityAnalysisResultUuid();
            return Mono.justOrEmpty(resultUuid).flatMap(uuid -> {
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
            });
        });
    }

    public Mono<Integer> getContingencyCount(UUID studyUuid, List<String> contingencyListNames) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);

        Mono<UUID> networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        return networkUuid.flatMap(uuid ->
                Flux.fromIterable(contingencyListNames)
                        .flatMap(contingencyListName -> {
                            String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{contingencyListName}/export")
                                    .queryParam("networkUuid", uuid)
                                    .buildAndExpand(contingencyListName)
                                    .toUriString();
                            Mono<List<Contingency>> contingencies = webClient
                                    .get()
                                    .uri(actionsServerBaseUri + path)
                                    .retrieve()
                                    .bodyToMono(new ParameterizedTypeReference<List<Contingency>>() {
                                    });
                            return contingencies.map(List::size);
                        })
                        .reduce(0, Integer::sum)
        );
    }

    Mono<byte[]> getSubstationSvg(UUID networkUuid, String substationId, boolean useName, boolean centerLabel, boolean diagonalLabel,
                                  boolean topologicalColoring, String substationLayout, String componentLibrary) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .queryParam("substationLayout", substationLayout)
                .queryParamIfPresent("componentLibrary", Optional.ofNullable(componentLibrary))
                .buildAndExpand(networkUuid, substationId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    Mono<String> getSubstationSvgAndMetadata(UUID networkUuid, String substationId, boolean useName, boolean centerLabel,
                                             boolean diagonalLabel, boolean topologicalColoring, String substationLayout, String componentLibrary) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
                        "/substation-svg-and-metadata/{networkUuid}/{substationId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .queryParam("substationLayout", substationLayout)
                .queryParamIfPresent("componentLibrary", Optional.ofNullable(componentLibrary))
                .buildAndExpand(networkUuid, substationId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> getSecurityAnalysisStatus(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);

        return getStudyByUuid(studyUuid).flatMap(entity -> {
            UUID resultUuid = entity.getSecurityAnalysisResultUuid();
            return Mono.justOrEmpty(resultUuid).flatMap(uuid -> {
                String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
                        .buildAndExpand(resultUuid)
                        .toUriString();
                return webClient
                        .get()
                        .uri(securityAnalysisServerBaseUri + path)
                        .retrieve()
                        .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new StudyException(SECURITY_ANALYSIS_NOT_FOUND)))
                        .bodyToMono(String.class);
            });
        });
    }

    public Mono<Void> invalidateSecurityAnalysisStatus(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);

        return getStudyByUuid(studyUuid).flatMap(entity -> {
            UUID resultUuid = entity.getSecurityAnalysisResultUuid();
            return Mono.justOrEmpty(resultUuid).flatMap(uuid -> {
                String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/invalidate-status")
                        .buildAndExpand(resultUuid)
                        .toUriString();
                return webClient
                        .put()
                        .uri(securityAnalysisServerBaseUri + path)
                        .retrieve()
                        .bodyToMono(Void.class);
            });
        });
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

    public Mono<Void> stopSecurityAnalysis(UUID studyUuid) {
        Objects.requireNonNull(studyUuid);

        return getStudyByUuid(studyUuid).flatMap(entity -> {
            UUID resultUuid = entity.getSecurityAnalysisResultUuid();

            String receiver;
            try {
                receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(studyUuid)), StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
            return Mono.justOrEmpty(resultUuid).flatMap(uuid -> {
                String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                        .queryParam(RECEIVER, receiver)
                        .buildAndExpand(resultUuid)
                        .toUriString();
                return webClient
                        .put()
                        .uri(securityAnalysisServerBaseUri + path)
                        .retrieve()
                        .bodyToMono(Void.class);
            });
        });
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeSaStopped() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE)
                .flatMap(message -> {
                    UUID resultUuid = UUID.fromString(message.getHeaders().get("resultUuid", String.class));
                    String receiver = message.getHeaders().get(RECEIVER, String.class);
                    if (receiver != null) {
                        Receiver receiverObj;
                        try {
                            receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), Receiver.class);

                            LOGGER.info("Security analysis stopped for study '{}'",
                                    resultUuid, receiverObj.getStudyUuid());

                            // delete security analysis result in database
                            return updateSecurityAnalysisResultUuid(receiverObj.getStudyUuid(), null)
                                    .then(Mono.fromCallable(() -> {
                                        // send notification for stopped computation
                                        emitStudyChanged(receiverObj.getStudyUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
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

    private Mono<StudyEntity> insertStudyEntity(UUID uuid, String userId, boolean isPrivate, UUID networkUuid, String networkId,
                                                String caseFormat, UUID caseUuid, boolean casePrivate,
                                                LoadFlowStatus loadFlowStatus, LoadFlowResultEntity loadFlowResult, LoadFlowParametersEntity loadFlowParameters, UUID securityAnalysisUuid) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowStatus);
        Objects.requireNonNull(loadFlowParameters);
        return Mono.fromCallable(() -> {
            StudyEntity studyEntity = new StudyEntity(uuid, userId, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, caseFormat, caseUuid, casePrivate, isPrivate, loadFlowStatus, loadFlowResult, null, loadFlowParameters, securityAnalysisUuid, null);
            return insertStudy(studyEntity);
        });
    }

    @Transactional
    public StudyEntity insertStudy(StudyEntity studyEntity) {
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    @Transactional
    public void doUpdateSecurityAnalysisResultUuid(UUID studyUuid, UUID securityAnalysisResultUuid) {
        studyRepository.findById(studyUuid).ifPresent(studyEntity -> studyEntity.setSecurityAnalysisResultUuid(securityAnalysisResultUuid));
    }

    Mono<Void> updateSecurityAnalysisResultUuid(UUID studyUuid, UUID securityAnalysisResultUuid) {
        return Mono.fromRunnable(() -> self.doUpdateSecurityAnalysisResultUuid(studyUuid, securityAnalysisResultUuid));
    }

    @Transactional
    public void doUpdateLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        studyRepository.findById(studyUuid).ifPresent(studyEntity -> studyEntity.setLoadFlowStatus(loadFlowStatus));
    }

    Mono<Void> updateLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        return Mono.fromRunnable(() -> self.doUpdateLoadFlowStatus(studyUuid, loadFlowStatus));
    }

    private Mono<StudyCreationRequestEntity> insertStudyCreationRequestEntity(String userId, boolean isPrivate, UUID studyUuid) {
        return Mono.fromCallable(() -> {
            StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(studyUuid == null ? UUID.randomUUID() : studyUuid, userId, LocalDateTime.now(ZoneOffset.UTC), isPrivate);
            return studyCreationRequestRepository.save(studyCreationRequestEntity);
        });
    }

    private Mono<Void> updateLoadFlowResultAndStatus(UUID studyUuid, LoadFlowResultEntity loadFlowResultEntity, LoadFlowStatus loadFlowStatus) {
        return Mono.fromRunnable(() -> self.doUpdateLoadFlowResultAndStatus(studyUuid, loadFlowResultEntity, loadFlowStatus));
    }

    @Transactional
    public void doUpdateLoadFlowResultAndStatus(UUID studyUuid, LoadFlowResultEntity loadFlowResultEntity, LoadFlowStatus loadFlowStatus) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> {
            studyEntity1.setLoadFlowResult(loadFlowResultEntity);
            studyEntity1.setLoadFlowStatus(loadFlowStatus);
        });
    }

    private Mono<Void> updateLoadFlowParametersAndStatus(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity, LoadFlowStatus loadFlowStatus) {
        return Mono.fromRunnable(() -> self.doUpdateLoadFlowParametersAndStatus(studyUuid, loadFlowParametersEntity, loadFlowStatus));
    }

    @Transactional
    public void doUpdateLoadFlowParametersAndStatus(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity, LoadFlowStatus loadFlowStatus) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> {
            studyEntity1.setLoadFlowParameters(loadFlowParametersEntity);
            studyEntity1.setLoadFlowStatus(loadFlowStatus);
        });
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

    @Transactional
    public Optional<UUID> doGetGroupUuid(UUID studyUuid, boolean generateId) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        if (studyEntity.isPresent()) {
            if (studyEntity.get().getModificationGroupUuid() == null && generateId) {
                studyEntity.get().setModificationGroupUuid(UUID.randomUUID());
            }
            return Optional.ofNullable(studyEntity.get().getModificationGroupUuid());
        } else {
            return Optional.empty();
        }
    }

    Mono<UUID> getGroupUuid(UUID studyUuid, boolean generateId) {
        return Mono.fromCallable(() -> self.doGetGroupUuid(studyUuid, generateId).orElse(null))
            .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)));
    }

    public Mono<Void> createEquipment(UUID studyUuid, String createEquipmentAttributes, ModificationType modificationType) {
        return getGroupUuid(studyUuid, true).flatMap(groupUuid -> {
            Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                .then(invalidateSecurityAnalysisStatus(studyUuid)
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)));

            return networkModificationService.createEquipment(studyUuid, createEquipmentAttributes, groupUuid, modificationType)
                .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                .collect(Collectors.toSet())
                .doOnSuccess(substationIds ->
                    emitStudyChanged(studyUuid, UPDATE_TYPE_STUDY, substationIds)
                )
                .then(monoUpdateLfState);
        });
    }

    Mono<Void> deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId) {
        return getGroupUuid(studyUuid, true).flatMap(groupUuid -> {
            Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                .then(invalidateSecurityAnalysisStatus(studyUuid)
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)));

            return networkModificationService.deleteEquipment(studyUuid, equipmentType, equipmentId, groupUuid)
                .flatMap(modification -> Flux.fromIterable(Arrays.asList(modification)))
                .collect(Collectors.toList())
                .doOnSuccess(deletionInfos -> deletionInfos.forEach(deletionInfo ->
                        emitStudyEquipmentDeleted(studyUuid, UPDATE_TYPE_STUDY,
                            deletionInfo.getSubstationIds(), deletionInfo.getEquipmentType(), deletionInfo.getEquipmentId())
                    )
                )
                .then(monoUpdateLfState);
        });
    }
}
