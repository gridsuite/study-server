/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.contingency.Contingency;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.model.TopLevelDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.integration.json.JsonPathUtils;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class StudyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyService.class);

    public static final String ROOT_CATEGORY_REACTOR = "reactor.";

    private static final String CATEGORY_BROKER_INPUT = StudyService.class.getName() + ".input-broker-messages";
    private static final String CATEGORY_BROKER_OUTPUT = StudyService.class.getName() + ".output-broker-messages";

    static final String HEADER_STUDY_NAME = "studyName";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_STUDIES = "studies";
    static final String UPDATE_TYPE_LOADFLOW = "loadflow";
    static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    static final String UPDATE_TYPE_SWITCH = "switch";
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_RESULT = "securityAnalysisResult";

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Receiver {

        private String studyName;

        private String userId;
    }

    private WebClient webClient;

    private String caseServerBaseUri;
    private String singleLineDiagramServerBaseUri;
    private String networkConversionServerBaseUri;
    private String geoDataServerBaseUri;
    private String networkMapServerBaseUri;
    private String networkModificationServerBaseUri;
    private String loadFlowServerBaseUri;
    private String networkStoreServerBaseUri;
    private String securityAnalysisServerBaseUri;
    private String actionsServerBaseUri;

    private StudyRepository studyRepository;
    private StudyCreationRequestRepository studyCreationRequestRepository;

    private ObjectMapper objectMapper;

    private EmitterProcessor<Message<String>> studyUpdatePublisher = EmitterProcessor.create();

    @Bean
    public Supplier<Flux<Message<String>>> publishStudyUpdate() {
        return () -> studyUpdatePublisher.log(CATEGORY_BROKER_OUTPUT, Level.FINE);
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeSaResult() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE).flatMap(message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get("resultUuid", String.class));
            String receiver = message.getHeaders().get("receiver", String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), Receiver.class);

                    LOGGER.info("Security analysis result '{}' available for study '{}' and user '{}'",
                            resultUuid, receiverObj.getStudyName(), receiverObj.getUserId());

                    // update DB
                    return studyRepository.updateSecurityAnalysisResultUuid(receiverObj.getStudyName(), receiverObj.getUserId(), resultUuid)
                            .then(Mono.fromCallable(() -> {
                                // send notification
                                studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                                        .setHeader(HEADER_STUDY_NAME, receiverObj.getStudyName())
                                        .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_SECURITY_ANALYSIS_RESULT)
                                        .build());
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
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
        this.networkStoreServerBaseUri = networkStoreServerBaseUri;
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
        this.actionsServerBaseUri = actionsServerBaseUri;

        this.studyRepository = studyRepository;
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.webClient =  webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    private static StudyInfos toInfos(StudyEntity entity) {
        return StudyInfos.builder().studyName(entity.getStudyName())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC")))
                .userId(entity.getUserId())
                .description(entity.getDescription()).caseFormat(entity.getCaseFormat())
                .loadFlowResult(new LoadFlowResult(entity.getLoadFlowResult().getStatus()))
                .build();
    }

    private static BasicStudyInfos toBasicInfos(BasicStudyEntity entity) {
        return BasicStudyInfos.builder().studyName(entity.getStudyName())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC")))
                .userId(entity.getUserId())
                .build();
    }

    Flux<StudyInfos> getStudyList(String userId) {
        return studyRepository.getStudies(userId).map(StudyService::toInfos)
                .sort(Comparator.comparing(StudyInfos::getCreationDate).reversed());
    }

    Flux<BasicStudyInfos> getStudyCreationRequests(String userId) {
        return studyCreationRequestRepository.getStudyCreationRequests(userId).map(StudyService::toBasicInfos)
                .sort(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed());
    }

    public Mono<StudyEntity> createStudy(String studyName, UUID caseUuid, String description, String userId, Boolean isPrivate, LoadFlowResult loadFlowResult) {
        return insertStudyCreationRequest(studyName, userId, isPrivate)
                .then(Mono.zip(persistentStore(caseUuid), getCaseFormat(caseUuid))
                          .flatMap(t -> {
                              LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                              return insertStudy(studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                                 description, t.getT2(), caseUuid, false, loadFlowResult, toEntity(loadFlowParameters));
                          })
                )
                .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                .doFinally(s -> deleteStudyCreationRequest(studyName, userId, isPrivate));
    }

    public Mono<StudyEntity> createStudy(String studyName, Mono<FilePart> caseFile, String description, String userId, Boolean isPrivate) {
        return insertStudyCreationRequest(studyName, userId, isPrivate)
                .then(importCase(caseFile).flatMap(uuid ->
                     Mono.zip(persistentStore(uuid), getCaseFormat(uuid))
                         .flatMap(t -> {
                             LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                             return insertStudy(studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                                description, t.getT2(), uuid, true, new LoadFlowResult(), toEntity(loadFlowParameters));
                         })
                ))
                .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                .doFinally(s -> deleteStudyCreationRequest(studyName, userId, isPrivate));
    }

    Mono<StudyInfos> getCurrentUserStudy(String studyName, String userId, String headerUserId) {
        Mono<StudyEntity> studyMono = studyRepository.findStudy(userId, studyName);
        return studyMono.flatMap(study -> {
            if (study.isPrivate() && !userId.equals(headerUserId)) {
                return Mono.error(new StudyException(NOT_ALLOWED));
            } else {
                return Mono.just(study);
            }
        }).map(StudyService::toInfos);
    }

    Mono<StudyEntity> getStudy(String studyName, String userId) {
        return studyRepository.findStudy(userId, studyName);
    }

    private Mono<BasicStudyEntity> getStudyCreationRequest(String studyName, String userId) {
        return studyCreationRequestRepository.findStudy(userId, studyName);
    }

    public Mono<Void> deleteStudy(String studyName, String userId, String headerUserId) {
        //we need to ensure that it's the initial creator that deletes it
        if (!userId.equals(headerUserId)) {
            return Mono.error(new StudyException(NOT_ALLOWED));
        }
        Mono<StudyEntity> studyMono = studyRepository.findStudy(userId, studyName);
        return studyMono.flatMap(study -> {
            if (study.isCasePrivate()) {
                String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}")
                        .buildAndExpand(study.getCaseUuid())
                        .toUriString();

                return webClient.delete()
                        .uri(caseServerBaseUri + path)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .then(deleteStudy(userId, studyName));
            } else {
                return deleteStudy(userId, studyName);
            }
        });
    }

    private Mono<StudyEntity> insertStudy(String studyName, String userId, boolean isPrivate, UUID networkUuid, String networkId,
                                         String description, String caseFormat, UUID caseUuid, boolean casePrivate,
                                          LoadFlowResult loadFlowResult, LoadFlowParametersEntity loadFlowParameters) {
        return studyRepository.insertStudy(studyName, userId, isPrivate, networkUuid, networkId, description, caseFormat, caseUuid, casePrivate, loadFlowResult,
                                           loadFlowParameters)
                .doOnSuccess(s -> emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES));
    }

    private Mono<Void> deleteStudy(String userId, String studyName) {
        return studyRepository.deleteStudy(userId, studyName)
                .doOnSuccess(s -> emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES));
    }

    private Mono<Void> insertStudyCreationRequest(String studyName, String userId, boolean isPrivate) {
        return studyCreationRequestRepository.insertStudyCreationRequest(studyName, userId, isPrivate)
                .doOnSuccess(s -> emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES));
    }

    private void deleteStudyCreationRequest(String studyName, String userId, boolean isPrivate) {
        studyCreationRequestRepository.deleteStudyCreationRequest(studyName, userId, isPrivate)
                .doOnSuccess(s -> emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES))
                .subscribe();
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

    Mono<UUID> importCase(Mono<FilePart> multipartFile) {

        return multipartFile.flatMap(file -> {
            MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
            multipartBodyBuilder.part("file", file);

            return webClient.post()
                    .uri(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA.toString())
                    .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(UUID.class)
                    .publishOn(Schedulers.boundedElastic())
                    .log(ROOT_CATEGORY_REACTOR, Level.FINE);
        });
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

    private Mono<NetworkInfos> persistentStore(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid)
                .buildAndExpand()
                .toUriString();

        return webClient.post()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .bodyToMono(NetworkInfos.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    // This function call directly the network store server without using the dedicated client because it's a blocking client.
    // If we'll have new needs to call the network store server, then we'll migrate the network store client to be nonblocking
    Mono<List<VoltageLevelAttributes>> getNetworkVoltageLevels(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("v1/networks/{networkId}/voltage-levels")
                .buildAndExpand(networkUuid)
                .toUriString();

        Mono<TopLevelDocument<com.powsybl.network.store.model.VoltageLevelAttributes>> mono = webClient.get()
                .uri(networkStoreServerBaseUri + path)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TopLevelDocument<com.powsybl.network.store.model.VoltageLevelAttributes>>() { });

        return mono.map(t -> t.getData().stream().map(e -> new VoltageLevelAttributes(e.getId(), e.getAttributes().getName(), e.getAttributes().getSubstationId())).collect(Collectors.toList()));
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

    Mono<String> getSubstationsMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/substations/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    Mono<String> getLinesMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/lines/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    Mono<String> getTwoWindingsTransformersMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/2-windings-transformers/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    Mono<String> getThreeWindingsTransformersMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/3-windings-transformers/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    Mono<String> getGeneratorsMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/generators/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    Mono<Void> changeSwitchState(String studyName, String userId, String switchId, boolean open) {
        Mono<UUID> networkUuid = getNetworkUuid(studyName, userId);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/switches/{switchId}")
                    .queryParam("open", open)
                    .buildAndExpand(uuid, switchId)
                    .toUriString();
            return webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .retrieve()
                    .bodyToMono(Void.class);
        }).then(studyRepository.updateLoadFlowState(studyName, userId, LoadFlowStatus.NOT_DONE)
        .doOnSuccess(e -> emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS)))
        .doOnSuccess(e -> emitStudyChanged(studyName, UPDATE_TYPE_SWITCH));
    }

    Mono<Void> runLoadFlow(String studyName, String userId) {
        Mono<UUID> networkUuid = getNetworkUuid(studyName, userId);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                    .buildAndExpand(uuid)
                    .toUriString();

            return webClient.put()
                .uri(loadFlowServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(e -> studyRepository.updateLoadFlowResult(studyName, userId, jsonToLoadFlowResult(e)))
                .doOnError(e -> studyRepository.updateLoadFlowState(studyName, userId, LoadFlowStatus.NOT_DONE)
                    .subscribe())
                .doOnCancel(() -> studyRepository.updateLoadFlowState(studyName, userId, LoadFlowStatus.NOT_DONE)
                    .subscribe());
        }).doFinally(s ->
            emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW)
        );
    }

    private LoadFlowResult jsonToLoadFlowResult(String strLfResult) {
        try {
            Boolean bStatus = JsonPathUtils.evaluate(strLfResult, "$.ok");
            LoadFlowStatus status;
            if (bStatus) {
                status = LoadFlowStatus.CONVERGED;
            } else {
                status = LoadFlowStatus.DIVERGED;
            }
            return new LoadFlowResult(status);
        } catch (IOException e) {
            return new LoadFlowResult(LoadFlowStatus.NOT_DONE);
        }
    }

    public Mono<StudyInfos> renameStudy(String studyName, String userId, String headerUserId, String newStudyName) {
        //we need to ensure that it's the initial creator that deletes it
        if (!userId.equals(headerUserId)) {
            return Mono.error(new StudyException(NOT_ALLOWED));
        }
        Mono<StudyEntity> studyMono = studyRepository.findStudy(userId, studyName);
        return studyMono.switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND))).flatMap(study -> {
            study.setStudyName(newStudyName);

            Mono<Void> deleteStudy = deleteStudy(userId, studyName);
            Mono<StudyEntity> insertStudy = insertStudy(newStudyName, userId, study.isPrivate(), study.getNetworkUuid(), study.getNetworkId(),
                    study.getDescription(), study.getCaseFormat(), study.getCaseUuid(), study.isCasePrivate(), new LoadFlowResult(study.getLoadFlowResult().getStatus()),
                    study.getLoadFlowParameters());

            return deleteStudy.then(insertStudy);
        }).map(StudyService::toInfos);
    }

    public Mono<Void> setLoadFlowRunning(String studyName, String userId) {
        return Mono.when(assertLoadFlowRunnable(studyName, userId))
            .then(studyRepository.updateLoadFlowState(studyName, userId, LoadFlowStatus.RUNNING))
            .doOnSuccess(s -> emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS));
    }

    public Mono<Collection<String>> getExportFormats() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/export/formats")
                .toUriString();

        ParameterizedTypeReference<Collection<String>> typeRef = new ParameterizedTypeReference<Collection<String>>() { };

        return webClient.get()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .bodyToMono(typeRef);
    }

    public Mono<ExportNetworkInfos> exportNetwork(String studyName, String userId, String format) {
        Mono<UUID> networkUuidMono = getNetworkUuid(studyName, userId);

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

    public Mono<ResponseEntity<Boolean>> lockoutLine(String studyName, String userId, String lineId, boolean lockout) {
        Mono<UUID> networkUuidMono = getNetworkUuid(studyName, userId);

        return networkUuidMono.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/lines/{lineId}/switches")
                    .queryParam("lockout", lockout)
                    .buildAndExpand(uuid, lineId)
                    .toUriString();

            return webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .retrieve()
                    .toEntity(Boolean.class);
        });
    }

    Mono<UUID> getNetworkUuid(String studyName, String userId) {
        Mono<StudyEntity> studyMono = studyRepository.findStudy(userId, studyName);
        return studyMono.map(StudyEntity::getNetworkUuid)
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)));

    }

    private void emitStudyChanged(String studyName, String updateType) {
        studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_NAME, studyName)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .build()
        );
    }

    Mono<Boolean> studyExists(String studyName, String userId) {
        return getStudy(studyName, userId).cast(BasicStudyEntity.class).switchIfEmpty(getStudyCreationRequest(studyName, userId)).hasElement();
    }

    public Mono<Void> assertCaseExists(UUID caseUuid) {
        Mono<Boolean> caseExists = caseExists(caseUuid);
        return caseExists.flatMap(c -> (boolean) c ? Mono.empty() : Mono.error(new StudyException(CASE_NOT_FOUND)));
    }

    public Mono<Void> assertStudyNotExists(String studyName, String userId) {
        Mono<Boolean> studyExists = studyExists(studyName, userId);
        return studyExists.flatMap(s -> (boolean) s ? Mono.error(new StudyException(STUDY_ALREADY_EXISTS)) : Mono.empty());
    }

    public Mono<Void> assertLoadFlowRunnable(String studyName, String userId) {
        Mono<StudyEntity> studyMono = studyRepository.findStudy(userId, studyName);
        return studyMono.map(StudyEntity::getLoadFlowResult)
            .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)))
            .flatMap(lfr -> lfr.getStatus().equals(LoadFlowStatus.NOT_DONE) ? Mono.empty() : Mono.error(new StudyException(LOADFLOW_NOT_RUNNABLE)));
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
                                            parameters.isWriteSlackBus());
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
                                      entity.isWriteSlackBus());
    }

    public Mono<LoadFlowParameters> getLoadFlowParameters(String studyName, String userId) {
        return getStudy(studyName, userId).map(study -> fromEntity(study.getLoadFlowParameters()));
    }

    Mono<Void> setLoadFlowParameters(String studyName, String userId, LoadFlowParameters parameters) {
        return studyRepository.updateLoadFlowParameters(studyName, userId, toEntity(parameters != null ? parameters : LoadFlowParameters.load())).then(
                studyRepository.updateLoadFlowState(studyName, userId, LoadFlowStatus.NOT_DONE)
                        .doOnSuccess(e -> emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS)));
    }

    public Mono<UUID> runSecurityAnalysis(String studyName, String userId, List<String> contingencyListNames, String parameters) {
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(parameters);

        Mono<UUID> networkUuid = getNetworkUuid(studyName, userId);

        return networkUuid.flatMap(uuid -> {
            String receiver;
            try {
                receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(studyName, userId)), StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
            String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
                    .queryParam("contingencyListName", contingencyListNames)
                    .queryParam("receiver", receiver)
                    .buildAndExpand(uuid)
                    .toUriString();
            return webClient
                    .post()
                    .uri(securityAnalysisServerBaseUri + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(parameters))
                    .retrieve()
                    .bodyToMono(UUID.class);
        });
    }

    public Mono<String> getSecurityAnalysisResult(String studyName, String userId, List<String> limitTypes) {
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(limitTypes);

        return studyRepository.findStudy(userId, studyName).flatMap(entity -> {
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

    public Mono<Integer> getContingencyCount(String studyName, String userId, List<String> contingencyListNames) {
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(contingencyListNames);

        Mono<UUID> networkUuid = getNetworkUuid(studyName, userId);

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
                                .bodyToMono(new ParameterizedTypeReference<>() { });
                        return contingencies.map(List::size);
                    })
                    .reduce(0, Integer::sum)
        );
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
}
