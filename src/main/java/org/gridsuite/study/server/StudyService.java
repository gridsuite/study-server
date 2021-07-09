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
import com.powsybl.network.store.model.TopLevelDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ElementaryModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.util.Pair;
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
    static final String STUDY = "STUDY";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_STUDY_NAME = "studyName";
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
    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";
    static final String RECEIVER = "receiver";

    // Self injection for @transactional support in internal calls to other methods of this service
    @Autowired
    StudyService self;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Receiver {
        private UUID studyUuid;
    }

    private WebClient webClient;

    private String caseServerBaseUri;
    private String singleLineDiagramServerBaseUri;
    private String networkConversionServerBaseUri;
    private String geoDataServerBaseUri;
    private String networkMapServerBaseUri;
    private String networkModificationServerBaseUri;
    private String reportServerBaseUri;
    private String loadFlowServerBaseUri;
    private String networkStoreServerBaseUri;
    private String securityAnalysisServerBaseUri;
    private String actionsServerBaseUri;
    private String directoryServerBaseUri;

    private StudyRepository studyRepository;
    private StudyCreationRequestRepository studyCreationRequestRepository;

    private ObjectMapper objectMapper;

    @Autowired
    private StreamBridge studyUpdatePublisher;

    @Bean
    public Consumer<Flux<Message<String>>> consumeSaResult() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE).flatMap(message -> {
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
            @Value("${network-store-server.base-uri:http://network-store-server/}") String networkStoreServerBaseUri,
            @Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
            @Value("${backing-services.single-line-diagram.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
            @Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
            @Value("${backing-services.geo-data.base-uri:http://geo-data-store-server/}") String geoDataServerBaseUri,
            @Value("${backing-services.network-map.base-uri:http://network-map-store-server/}") String networkMapServerBaseUri,
            @Value("${backing-services.network-modification.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri,
            @Value("${backing-services.loadflow.base-uri:http://loadflow-server/}") String loadFlowServerBaseUri,
            @Value("${backing-services.security-analysis-server.base-uri:http://security-analysis-server/}") String securityAnalysisServerBaseUri,
            @Value("${backing-services.actions-server.base-uri:http://actions-server/}") String actionsServerBaseUri,
            @Value("${backing-services.directory-server.base-uri:http://directory-server/}") String directoryServerBaseUri,
            @Value("${backing-services.report-server.base-uri:http://report-server/}") String reportServerBaseUri,
            StudyRepository studyRepository,
            StudyCreationRequestRepository studyCreationRequestRepository,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.caseServerBaseUri = caseServerBaseUri;
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.geoDataServerBaseUri = geoDataServerBaseUri;
        this.networkMapServerBaseUri = networkMapServerBaseUri;
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
        this.reportServerBaseUri = reportServerBaseUri;
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
        this.networkStoreServerBaseUri = networkStoreServerBaseUri;
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
        this.actionsServerBaseUri = actionsServerBaseUri;
        this.directoryServerBaseUri = directoryServerBaseUri;

        this.studyRepository = studyRepository;
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    private static StudyInfos toStudyInfos(StudyEntity entity) {
        return StudyInfos.builder().studyName(entity.getStudyName())
                .studyUuid(entity.getId())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .userId(entity.getUserId())
                .description(entity.getDescription())
                .caseFormat(entity.getCaseFormat())
                .loadFlowStatus(entity.getLoadFlowStatus())
                .loadFlowResult(fromEntity(entity.getLoadFlowResult()))
                .studyPrivate(entity.isPrivate())
                .build();
    }

    private static BasicStudyInfos toBasicStudyInfos(StudyCreationRequestEntity entity) {
        return BasicStudyInfos.builder().studyName(entity.getStudyName())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .userId(entity.getUserId())
                .studyUuid(entity.getId())
                .studyPrivate(entity.getIsPrivate())
                .build();
    }

    private static CreatedStudyBasicInfos toCreatedStudyBasicInfos(StudyEntity entity) {
        return CreatedStudyBasicInfos.builder().studyName(entity.getStudyName())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .userId(entity.getUserId())
                .studyUuid(entity.getId())
                .caseFormat(entity.getCaseFormat())
                .studyPrivate(entity.isPrivate())
                .description(entity.getDescription())
                .build();
    }

    public Flux<CreatedStudyBasicInfos> getStudyList(String userId) {
        return Flux.fromStream(() -> studyRepository.findByUserIdOrIsPrivate(userId, false).stream())
                .map(StudyService::toCreatedStudyBasicInfos)
                .sort(Comparator.comparing(CreatedStudyBasicInfos::getCreationDate).reversed());
    }

    public Flux<CreatedStudyBasicInfos> getStudyListMetadata(List<UUID> uuids, String userId) {
        return Flux.fromStream(() -> studyRepository.findAllByUuids(uuids, userId).stream().map(StudyService::toCreatedStudyBasicInfos));
    }

    Flux<BasicStudyInfos> getStudyCreationRequests(String userId) {
        return Flux.fromStream(() -> studyCreationRequestRepository.findByUserIdOrIsPrivate(userId, false).stream())
                .map(StudyService::toBasicStudyInfos)
                .sort(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed());
    }

    private Mono<Void> insertDirectoryElement(UUID parentDirectoryUuid, DirectoryElement directoryElement) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_SERVER_API_VERSION + "/directories/{parentUuid}")
                .buildAndExpand(parentDirectoryUuid)
                .toUriString();

        return webClient.put()
                .uri(directoryServerBaseUri + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(directoryElement))
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                        handleStudyCreationError(directoryElement.getElementUuid(), directoryElement.getElementName(),
                                directoryElement.getOwner(), directoryElement.getAccessRights().isPrivate(), clientResponse,
                                "directory-server")
                )
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    private Mono<Void> insertDirectoryElement(UUID parentDirectoryUuid, BasicStudyInfos studyInfos) {
        DirectoryElement directoryElement = DirectoryElement.builder().elementUuid(studyInfos.getStudyUuid())
                .elementName(studyInfos.getStudyName())
                .type(STUDY)
                .owner(studyInfos.getUserId())
                .accessRights(new AccessRightsAttributes(studyInfos.isStudyPrivate()))
                .build();

        return insertDirectoryElement(parentDirectoryUuid, directoryElement)
                .doOnError(throwable -> {
                    LOGGER.error(throwable.toString(), throwable);
                    deleteDirectoryElement(studyInfos.getStudyUuid()).subscribe();
                });
    }

    private Mono<Void> renameDirectoryElement(StudyInfos studyInfos, String newName) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_SERVER_API_VERSION + "/directories/{elementUuid}/rename/{elementUuid}")
                .buildAndExpand(studyInfos.getStudyUuid(), newName)
                .toUriString();

        return webClient.put()
                .uri(directoryServerBaseUri + path)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse -> Mono.error(new StudyException(DIRECTORY_REQUEST_FAILED)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    private Mono<Void> deleteDirectoryElement(UUID elementUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_SERVER_API_VERSION + "/directories/{elementUuid}")
                .buildAndExpand(elementUuid)
                .toUriString();

        return webClient.delete()
                .uri(directoryServerBaseUri + path)
                .retrieve()
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<BasicStudyInfos> createStudy(String studyName, UUID caseUuid, String description, String userId, Boolean isPrivate, UUID parentDirectoryUuid) {
        return insertStudyCreationRequest(studyName, userId, isPrivate)
                .map(StudyService::toBasicStudyInfos)
                .doOnSuccess(s -> insertDirectoryElement(parentDirectoryUuid, s) // insert study in directory server
                        .doOnSuccess(unused -> {
                            emitStudiesChanged(s.getStudyUuid(), s.getUserId(), s.isStudyPrivate());
                            Mono.zip(persistentStore(caseUuid, s.getStudyUuid(), studyName, userId, isPrivate), getCaseFormat(caseUuid))
                                    .flatMap(t -> {
                                        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                                        return insertStudy(s.getStudyUuid(), studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                                description, t.getT2(), caseUuid, false, LoadFlowStatus.NOT_DONE, null, toEntity(loadFlowParameters), null);
                                    })
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doOnError(throwable -> {
                                        LOGGER.error(throwable.toString(), throwable);
                                        deleteDirectoryElement(s.getStudyUuid()).then(deleteStudyIfNotCreationInProgress(s.getStudyUuid(), userId)).subscribe();
                                    })
                                    .doOnSuccess(r -> deleteStudyIfNotCreationInProgress(s.getStudyUuid(), userId).subscribe())
                                    .subscribe();
                        }).subscribe()
                );
    }

    public Mono<BasicStudyInfos> createStudy(String studyName, Mono<FilePart> caseFile, String description, String userId, Boolean isPrivate, UUID parentDirectoryUuid) {
        return insertStudyCreationRequest(studyName, userId, isPrivate)
                .map(StudyService::toBasicStudyInfos)
                .doOnSuccess(s -> insertDirectoryElement(parentDirectoryUuid, s)  // insert study in directory server
                        .doOnSuccess(unused -> {
                            emitStudiesChanged(s.getStudyUuid(), s.getUserId(), s.isStudyPrivate());
                            importCase(caseFile, s.getStudyUuid(), studyName, userId, isPrivate).flatMap(uuid ->
                                    Mono.zip(persistentStore(uuid, s.getStudyUuid(), studyName, userId, isPrivate), getCaseFormat(uuid))
                                            .flatMap(t -> {
                                                LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                                                return insertStudy(s.getStudyUuid(), studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                                        description, t.getT2(), uuid, true, LoadFlowStatus.NOT_DONE, null, toEntity(loadFlowParameters), null);
                                            }))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doOnError(throwable -> {
                                        LOGGER.error(throwable.toString(), throwable);
                                        deleteDirectoryElement(s.getStudyUuid()).then(deleteStudyIfNotCreationInProgress(s.getStudyUuid(), userId)).subscribe();
                                    })
                                    .doOnSuccess(r -> deleteStudyIfNotCreationInProgress(s.getStudyUuid(), userId).subscribe())
                                    .subscribe();
                        }).subscribe());
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

    Mono<StudyEntity> getStudyByNameAndUserId(String studyName, String userId) {
        return Mono.fromCallable(() -> studyRepository.findByUserIdAndStudyName(userId, studyName).orElse(null));
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

    private Mono<BasicStudyEntity> getStudyCreationRequestByNameAndUserId(String studyName, String userId) {
        return Mono.fromCallable(() -> studyCreationRequestRepository.findByUserIdAndStudyName(userId, studyName).orElse(null));
    }

    @Transactional
    public Optional<UUID> doDeleteStudyIfNotCreationInProgress(UUID uuid, String userId) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(uuid);
        Optional<UUID> networkUuid = Optional.empty();
        if (studyCreationRequestEntity.isEmpty()) {
            networkUuid = doGetNetworkUuid(uuid);
            studyRepository.findById(uuid).ifPresent(s -> {
                if (!s.getUserId().equals(userId)) {
                    throw new StudyException(NOT_ALLOWED);
                }
                studyRepository.deleteById(uuid);
                emitStudiesChanged(uuid, userId, s.isPrivate());
                /*deleteDirectoryElement(uuid)
                        .doOnSuccess(unused -> emitStudiesChanged(uuid, userId, s.isPrivate()))
                        .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable)).subscribe();*/
            });
        } else {
            studyCreationRequestRepository.deleteById(studyCreationRequestEntity.get().getId());
            emitStudiesChanged(uuid, userId, studyCreationRequestEntity.get().getIsPrivate());
            /*deleteDirectoryElement(uuid)
                    .doOnSuccess(unused -> emitStudiesChanged(uuid, userId, studyCreationRequestEntity.get().getIsPrivate()))
                    .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable)).subscribe();*/
        }
        return networkUuid;
    }

    public Mono<Void> deleteStudyIfNotCreationInProgress(UUID uuid, String userId) {
        var allDeletedInParallel = Mono.fromCallable(() -> self.doDeleteStudyIfNotCreationInProgress(uuid, userId))
                .flatMap(Mono::justOrEmpty)
                .publish(networkIdMono ->
                        Mono.when(
                                networkIdMono.flatMap(this::deleteNetwork),
                                networkIdMono.flatMap(this::deleteNetworkModifications),
                                networkIdMono.flatMap(this::deleteReport)
                        )
                );

        return allDeletedInParallel.doOnError(throwable -> LOGGER.error(throwable.toString(), throwable));
    }

    // This function call directly the network store server without using the dedicated client because it's a blocking client.
    // If we'll have new needs to call the network store server, then we'll migrate the network store client to be nonblocking
    Mono<Void> deleteNetwork(UUID networkUuid) {
        var path = UriComponentsBuilder.fromPath("v1/networks/{networkId}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.delete()
                .uri(networkStoreServerBaseUri + path)
                .retrieve()
                .bodyToMono(Void.class);
    }

    private Mono<StudyEntity> insertStudy(UUID uuid, String studyName, String userId, boolean isPrivate, UUID networkUuid, String networkId,
                                          String description, String caseFormat, UUID caseUuid, boolean casePrivate, LoadFlowStatus loadFlowStatus,
                                          LoadFlowResultEntity loadFlowResult, LoadFlowParametersEntity loadFlowParameters, UUID securityAnalysisUuid) {
        return insertStudyEntity(uuid, studyName, userId, isPrivate, networkUuid, networkId, description, caseFormat, caseUuid, casePrivate, loadFlowStatus, loadFlowResult,
                loadFlowParameters, securityAnalysisUuid)
                .doOnSuccess(s -> emitStudiesChanged(uuid, userId, isPrivate));
    }

    private Mono<StudyCreationRequestEntity> insertStudyCreationRequest(String studyName, String userId, boolean isPrivate) {
        return insertStudyCreationRequestEntity(studyName, userId, isPrivate)
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

    private Mono<? extends Throwable> handleStudyCreationError(UUID studyUuid, String studyName, String userId, boolean isPrivate, ClientResponse clientResponse, String serverName) {
        return clientResponse.bodyToMono(String.class)
            .switchIfEmpty(Mono.just("{\"message\": \"" + serverName + ": " + clientResponse.statusCode() + "\"}"))
            .flatMap(body -> {
                try {
                    JsonNode node = new ObjectMapper().readTree(body).path("message");
                    if (!node.isMissingNode()) {
                        emitStudyCreationError(studyUuid, studyName, userId, isPrivate, node.asText());
                    } else {
                        emitStudyCreationError(studyUuid, studyName, userId, isPrivate, body);
                    }
                } catch (JsonProcessingException e) {
                    if (!body.isEmpty()) {
                        emitStudyCreationError(studyUuid, studyName, userId, isPrivate, body);
                    }
                }
                return Mono.error(new StudyException(STUDY_CREATION_FAILED));
            });
    }

    Mono<UUID> importCase(Mono<FilePart> multipartFile, UUID studyUuid, String studyName, String userId, boolean isPrivate) {
        return multipartFile.flatMap(file -> {
            MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
            multipartBodyBuilder.part("file", file);

            return webClient.post()
                .uri(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA.toString())
                .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                    handleStudyCreationError(studyUuid, studyName, userId, isPrivate, clientResponse, "case-server")
                )
                .bodyToMono(UUID.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
        })
            .doOnError(t -> !(t instanceof StudyException), t -> emitStudyCreationError(studyUuid, studyName, userId, isPrivate, t.getMessage()));
    }

    Mono<byte[]> getVoltageLevelSvg(UUID networkUuid, String voltageLevelId, boolean useName, boolean centerLabel, boolean diagonalLabel,
                                    boolean topologicalColoring) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .buildAndExpand(networkUuid, voltageLevelId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    Mono<String> getVoltageLevelSvgAndMetadata(UUID networkUuid, String voltageLevelId, boolean useName, boolean centerLabel, boolean diagonalLabel,
                                               boolean topologicalColoring) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-and-metadata/{networkUuid}/{voltageLevelId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .buildAndExpand(networkUuid, voltageLevelId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    private Mono<NetworkInfos> persistentStore(UUID caseUuid, UUID studyUuid, String studyName, String userId, boolean isPrivate) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
            .queryParam(CASE_UUID, caseUuid)
            .buildAndExpand()
            .toUriString();

        return webClient.post()
            .uri(networkConversionServerBaseUri + path)
            .retrieve()
            .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                handleStudyCreationError(studyUuid, studyName, userId, isPrivate, clientResponse, "network-conversion-server")
            )
            .bodyToMono(NetworkInfos.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE)
            .doOnError(t -> !(t instanceof StudyException), t -> emitStudyCreationError(studyUuid, studyName, userId, isPrivate, t.getMessage()));
    }

    // This function call directly the network store server without using the dedicated client because it's a blocking client.
    // If we'll have new needs to call the network store server, then we'll migrate the network store client to be nonblocking
    Mono<List<VoltageLevelInfos>> getNetworkVoltageLevels(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("v1/networks/{networkId}/voltage-levels")
                .buildAndExpand(networkUuid)
                .toUriString();

        Mono<TopLevelDocument<com.powsybl.network.store.model.VoltageLevelAttributes>> mono = webClient.get()
                .uri(networkStoreServerBaseUri + path)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TopLevelDocument<com.powsybl.network.store.model.VoltageLevelAttributes>>() {
                });

        return mono.map(t -> t.getData().stream()
                .map(e -> VoltageLevelInfos.builder().id(e.getId()).name(e.getAttributes().getName()).substationId(e.getAttributes().getSubstationId()).build())
                .collect(Collectors.toList()));
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
        Mono<UUID> networkUuid = getNetworkUuid(studyUuid);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/switches/{switchId}")
                    .queryParam("open", open)
                    .buildAndExpand(uuid, switchId)
                    .toUriString();

            Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                    .then(invalidateSecurityAnalysisStatus(studyUuid)
                            .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)))
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SWITCH));

            Flux<ElementaryModificationInfos> fluxChangeSwitchState = webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new StudyException(ELEMENT_NOT_FOUND)))
                    .bodyToFlux(new ParameterizedTypeReference<ElementaryModificationInfos>() {
                    });

            return fluxChangeSwitchState
                    .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                    .collect(Collectors.toSet())
                    .doOnSuccess(substationIds ->
                            emitStudyChanged(studyUuid, UPDATE_TYPE_STUDY, substationIds)
                    )
                    .then(monoUpdateLfState);
        });
    }

    public Mono<Void> applyGroovyScript(UUID studyUuid, String groovyScript) {
        Mono<UUID> networkUuid = getNetworkUuid(studyUuid);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/groovy/")

                    .buildAndExpand(uuid)
                    .toUriString();

            Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                    .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                    .then(invalidateSecurityAnalysisStatus(studyUuid)
                            .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)));

            Flux<ElementaryModificationInfos> fluxApplyGroovy = webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .body(BodyInserters.fromValue(groovyScript))
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ElementaryModificationInfos>() {
                    });

            return fluxApplyGroovy
                    .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                    .collect(Collectors.toSet())
                    .doOnSuccess(substationIds ->
                            emitStudyChanged(studyUuid, UPDATE_TYPE_STUDY, substationIds)
                    )
                    .then(monoUpdateLfState);
        });
    }

    Mono<Void> runLoadFlow(UUID studyUuid) {
        return setLoadFlowRunning(studyUuid).then(Mono.zip(getNetworkUuid(studyUuid), getLoadFlowProvider(studyUuid))).flatMap(tuple -> {
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

    @Transactional
    public Pair<StudyEntity, String> doRenameStudy(UUID studyUuid, String userId, String newStudyName) {
        return studyRepository.findById(studyUuid).map(studyEntity -> {
            String initialStudyName = studyEntity.getStudyName();
            if (!studyEntity.getUserId().equals(userId)) {
                throw new StudyException(NOT_ALLOWED);
            }
            studyEntity.setStudyName(newStudyName);
            return Pair.of(studyEntity, initialStudyName);
        }).orElse(null);
    }

    public Mono<CreatedStudyBasicInfos> renameStudy(UUID studyUuid, String userId, String newStudyName) {
        return Mono.fromCallable(() -> self.doRenameStudy(studyUuid, userId, newStudyName))
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)))
                .flatMap(s -> renameDirectoryElement(toStudyInfos(s.getFirst()), newStudyName)
                        .doOnSuccess(ss ->
                                emitStudiesChanged(studyUuid, userId, s.getFirst().isPrivate())
                        ).doOnError(throwable -> {
                            LOGGER.error(throwable.toString(), throwable);
                            //if the name wasn't changed in the directory server we reset the name is the study server
                            self.doRenameStudy(studyUuid, userId, s.getSecond());
                        }).then(Mono.just(toCreatedStudyBasicInfos(s.getFirst())))
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
        Mono<UUID> networkUuidMono = getNetworkUuid(studyUuid);

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

    private Mono<? extends Throwable> handleChangeLineError(UUID studyUuid,  ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class).flatMap(body -> {
            String message = null;
            try {
                JsonNode node = new ObjectMapper().readTree(body).path("message");
                if (!node.isMissingNode()) {
                    message = node.asText();
                }
            } catch (JsonProcessingException e) {
                if (!body.isEmpty()) {
                    message = body;
                }
            }
            return Mono.error(new StudyException(LINE_MODIFICATION_FAILED, message));
        });
    }

    private Mono<Void> applyLineChanges(UUID studyUuid, String path, String status) {
        Mono<Void> monoUpdateLfState = updateLoadFlowResultAndStatus(studyUuid, null, LoadFlowStatus.NOT_DONE)
                .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LOADFLOW_STATUS))
                .then(invalidateSecurityAnalysisStatus(studyUuid)
                        .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)))
                .doOnSuccess(e -> emitStudyChanged(studyUuid, UPDATE_TYPE_LINE));
        Flux<ElementaryModificationInfos> fluxChangeLineStatus = webClient.put()
                .uri(networkModificationServerBaseUri + path)
                .body(BodyInserters.fromValue(status))
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse -> handleChangeLineError(studyUuid, clientResponse))
                .bodyToFlux(new ParameterizedTypeReference<ElementaryModificationInfos>() {
                });

        return fluxChangeLineStatus
                .flatMap(modification -> Flux.fromIterable(modification.getSubstationIds()))
                .collect(Collectors.toSet())
                .doOnSuccess(substationIds ->
                        emitStudyChanged(studyUuid, UPDATE_TYPE_STUDY, substationIds)
                )
                .then(monoUpdateLfState);
    }

    public Mono<Void> changeLineStatus(UUID studyUuid, String lineId, String status) {
        Mono<UUID> networkUuidMono = getNetworkUuid(studyUuid);

        return networkUuidMono.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/lines/{lineId}/status")
                    .buildAndExpand(uuid, lineId)
                    .toUriString();

            return applyLineChanges(studyUuid, path, status);
        });
    }

    Mono<UUID> getNetworkUuid(UUID studyUuid) {
        return Mono.fromCallable(() -> doGetNetworkUuid(studyUuid).orElse(null))
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)));
    }

    private Optional<UUID> doGetNetworkUuid(UUID studyUuid) {
        return studyRepository.findById(studyUuid).map(StudyEntity::getNetworkUuid);
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

    private void emitStudyCreationError(UUID studyUuid, String studyName, String userId, boolean isPrivate, String errorMessage) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_STUDY_NAME, studyName)
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

    Mono<Boolean> studyExists(String studyName, String userId) {
        return getStudyByNameAndUserId(studyName, userId).cast(BasicStudyEntity.class).switchIfEmpty(getStudyCreationRequestByNameAndUserId(studyName, userId)).hasElement();
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

        return Mono.zip(getNetworkUuid(studyUuid), getLoadFlowProvider(studyUuid)).flatMap(tuple -> {
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

        Mono<UUID> networkUuid = getNetworkUuid(studyUuid);

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
                                  boolean topologicalColoring, String substationLayout) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .queryParam("substationLayout", substationLayout)
                .buildAndExpand(networkUuid, substationId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    Mono<String> getSubstationSvgAndMetadata(UUID networkUuid, String substationId, boolean useName, boolean centerLabel,
                                             boolean diagonalLabel, boolean topologicalColoring, String substationLayout) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
                "/substation-svg-and-metadata/{networkUuid}/{substationId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .queryParam("substationLayout", substationLayout)
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

    void setNetworkModificationServerBaseUri(String networkModificationServerBaseUri) {
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
    }

    void setNetworkMapServerBaseUri(String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }

    void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }

    void setNetworkStoreServerBaseUri(String networkStoreServerBaseUri) {
        this.networkStoreServerBaseUri = networkStoreServerBaseUri + DELIMITER;
    }

    public void setSecurityAnalysisServerBaseUri(String securityAnalysisServerBaseUri) {
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
    }

    public void setActionsServerBaseUri(String actionsServerBaseUri) {
        this.actionsServerBaseUri = actionsServerBaseUri;
    }

    public void setDirectoryServerBaseUri(String directoryServerBaseUri) {
        this.directoryServerBaseUri = directoryServerBaseUri;
    }

    public void setReportServerBaseUri(String actionsServerBaseUri) {
        this.reportServerBaseUri = actionsServerBaseUri;
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
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE).flatMap(message -> {
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

    private Mono<StudyEntity> insertStudyEntity(UUID uuid, String studyName, String userId, boolean isPrivate, UUID networkUuid, String networkId,
                                                String description, String caseFormat, UUID caseUuid, boolean casePrivate,
                                                LoadFlowStatus loadFlowStatus, LoadFlowResultEntity loadFlowResult, LoadFlowParametersEntity loadFlowParameters, UUID securityAnalysisUuid) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowStatus);
        Objects.requireNonNull(loadFlowParameters);
        return Mono.fromCallable(() -> {
            StudyEntity studyEntity = new StudyEntity(uuid, userId, studyName, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, description, caseFormat, caseUuid, casePrivate, isPrivate, loadFlowStatus, loadFlowResult, null, loadFlowParameters, securityAnalysisUuid);
            return studyRepository.save(studyEntity);
        });
    }

    @Transactional
    public void doUpdateSecurityAnalysisResultUuid(UUID studyUuid, UUID securityAnalysisResultUuid) {
        studyRepository.findById(studyUuid).ifPresent(studyEntity -> studyEntity.setSecurityAnalysisResultUuid(securityAnalysisResultUuid));
    }

    Mono<Void> updateSecurityAnalysisResultUuid(UUID studyUuid, UUID securityAnalysisResultUuid) {
        return Mono.fromRunnable(() -> self.doUpdateSecurityAnalysisResultUuid(studyUuid, securityAnalysisResultUuid));
    }

    @Transactional
    public void doUpdateLoadFlowParameters(String studyName, String userId, LoadFlowParametersEntity parameters) {
        studyRepository.findByUserIdAndStudyName(userId, studyName).ifPresent(studyEntity -> {
            studyEntity.setLoadFlowParameters(parameters);
            studyEntity.setLoadFlowStatus(LoadFlowStatus.NOT_DONE);
        });
    }

    @Transactional
    public void doUpdateLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        studyRepository.findById(studyUuid).ifPresent(studyEntity -> studyEntity.setLoadFlowStatus(loadFlowStatus));
    }

    Mono<Void> updateLoadFlowStatus(UUID studyUuid, LoadFlowStatus loadFlowStatus) {
        return Mono.fromRunnable(() -> self.doUpdateLoadFlowStatus(studyUuid, loadFlowStatus));
    }

    private Mono<StudyCreationRequestEntity> insertStudyCreationRequestEntity(String studyName, String userId, boolean isPrivate) {
        return Mono.fromCallable(() -> {
            StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(null, userId, studyName, LocalDateTime.now(ZoneOffset.UTC), isPrivate);
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

    public Flux<ModificationInfos> getModifications(UUID studyUuid) {
        return getNetworkUuid(studyUuid)
                .flatMapMany(networkUuid -> {
                    var path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/modifications")
                            .buildAndExpand(networkUuid)
                            .toUriString();
                    return webClient.get().uri(networkModificationServerBaseUri + path).retrieve().bodyToFlux(new ParameterizedTypeReference<ModificationInfos>() {
                    });
                });
    }

    public Mono<Void> deleteModifications(UUID studyUuid) {
        return getNetworkUuid(studyUuid).flatMap(this::deleteNetworkModifications);
    }

    private Mono<Void> deleteNetworkModifications(UUID networkUuid) {
        var path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/modifications")
                .buildAndExpand(networkUuid)
                .toUriString();
        return webClient.delete()
                .uri(networkModificationServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, r -> Mono.empty()) // Ignore because modification group does not exist if no modifications
                .bodyToMono(Void.class);
    }

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        studyUpdatePublisher.send("publishStudyUpdate-out-0", message);
    }

    private Mono<Void> deleteReport(UUID networkUuid) {
        var path = UriComponentsBuilder.fromPath(DELIMITER + REPORT_API_VERSION + "/report/{networkUuid}")
            .buildAndExpand(networkUuid)
            .toUriString();
        return webClient.delete()
            .uri(reportServerBaseUri + path)
            .retrieve()
            .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, r -> Mono.empty()) // Ignore report server do not return anything
            .bodyToMono(Void.class);
    }
}
