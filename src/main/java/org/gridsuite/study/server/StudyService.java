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
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.model.TopLevelDocument;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Synchronized;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repositories.*;
import org.gridsuite.study.server.entities.*;
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
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.transaction.Transactional;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
@Transactional
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
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_STATUS = "securityAnalysis_status";
    static final String HEADER_ERROR = "error";
    static final String UPDATE_TYPE_STUDY = "study";
    static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";

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

                    studyRepository.updateSecurityAnalysisResultUuid(receiverObj.getStudyName(), receiverObj.getUserId(), resultUuid);
                    // update DB
                    return Mono.fromCallable(() -> {
                        // send notifications
                        emitStudyChanged(receiverObj.getStudyName(), UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
                        emitStudyChanged(receiverObj.getStudyName(), UPDATE_TYPE_SECURITY_ANALYSIS_RESULT);
                        return null;
                    });
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
                .creationDate(entity.getDate())
                .userId(entity.getUserId())
                .description(entity.getDescription()).caseFormat(entity.getCaseFormat())
                .loadFlowStatus(entity.getLoadFlowStatus())
                .loadFlowResult(fromEntity(entity.getLoadFlowResult()))
                .studyPrivate(entity.isPrivate())
                .build();
    }

    private static BasicStudyInfos toBasicInfos(BasicStudyEntity entity) {
        return BasicStudyInfos.builder().studyName(entity.getStudyName())
                .creationDate(entity.getDate())
                .userId(entity.getUserId())
                .build();
    }

    Flux<StudyInfos> getStudyList(String userId) {
        Stream<StudyEntity> s1 = studyRepository.findAllByUserId(userId).stream();
        Stream<StudyEntity> s2 = studyRepository.findAll().stream().filter(s -> !s.getUserId().equals(userId) && !s.isPrivate());
        return Flux.fromIterable(Stream.concat(s1, s2).map(StudyService::toInfos)
                .sorted(Comparator.comparing(StudyInfos::getCreationDate).reversed()).collect(Collectors.toList()));
    }

    Flux<BasicStudyInfos> getStudyCreationRequests(String userId) {
        return Flux.fromIterable(studyCreationRequestRepository.findAllByUserId(userId).stream().map(StudyService::toBasicInfos)
                .sorted(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed()).collect(Collectors.toList()));
    }

    public Mono<StudyEntity> createStudy(String studyName, UUID caseUuid, String description, String userId, Boolean isPrivate) {
        return insertStudyCreationRequest(studyName, userId, isPrivate)
                .then(Mono.zip(persistentStore(caseUuid, studyName), getCaseFormat(caseUuid))
                          .flatMap(t -> {
                              LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                              return insertStudy(studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                                 description, t.getT2(), caseUuid, false, LoadFlowStatus.NOT_DONE, null,  toEntity(loadFlowParameters), null);
                          })
                )
                .doOnError(throwable ->
                        LOGGER.error(throwable.toString(), throwable))
                .doFinally(s -> deleteStudyIfNotCreationInProgress(studyName, userId).subscribe());
    }

    public Mono<StudyEntity> createStudy(String studyName, Mono<FilePart> caseFile, String description, String userId, Boolean isPrivate) {
        return insertStudyCreationRequest(studyName, userId, isPrivate)
                .then(importCase(caseFile, studyName).flatMap(uuid ->
                     Mono.zip(persistentStore(uuid, studyName), getCaseFormat(uuid))
                         .flatMap(t -> {
                             LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                             return insertStudy(studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(),
                                                description, t.getT2(), uuid, true, LoadFlowStatus.NOT_DONE, null, toEntity(loadFlowParameters), null);
                         })
                ))
                .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
                .doFinally(s -> deleteStudyIfNotCreationInProgress(studyName, userId).subscribe()); // delete the study if the creation has been canceled
    }

    Mono<StudyInfos> getCurrentUserStudy(String studyName, String userId, String headerUserId) {
        Mono<Optional<StudyEntity>> studyMono = Mono.fromCallable(() -> studyRepository.findByUserIdAndStudyName(userId, studyName));
        return studyMono.flatMap(study -> {
            if (study.isPresent()) {
                if (study.get().isPrivate() && !userId.equals(headerUserId)) {
                    return Mono.error(new StudyException(NOT_ALLOWED));
                } else {
                    return Mono.just(study.get());
                }
            }
            return Mono.error(new StudyException(STUDY_NOT_FOUND));
        }).map(StudyService::toInfos);
    }

    Mono<StudyEntity> getStudy(String studyName, String userId) {
        Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
        if (studyEntity.isPresent()) {
            return Mono.just(studyEntity.get());
        } else {
            return Mono.empty();
        }
    }

    private Optional<StudyCreationRequestEntity> getStudyCreationRequest(String studyName, String userId) {
        return studyCreationRequestRepository.findByUserIdAndStudyName(userId, studyName);
    }

    @Synchronized
    public Mono<Void> deleteStudyIfNotCreationInProgress(String studyName, String userId) {
        return Mono.fromRunnable(() -> {
            if (getStudyCreationRequest(studyName, userId).isEmpty()) {
                removeStudy(studyName, userId);
            }
            deleteStudyCreationRequest(studyName, userId);
        });
    }

    private Mono<StudyEntity> insertStudy(String studyName, String userId, boolean isPrivate, UUID networkUuid, String networkId,
                                          String description, String caseFormat, UUID caseUuid, boolean casePrivate, LoadFlowStatus loadFlowStatus,
                                          LoadFlowResultEntity loadFlowResult, LoadFlowParametersEntity loadFlowParameters, UUID securityAnalysisUuid) {
        StudyEntity studyEntity = new StudyEntity(userId, studyName, ZonedDateTime.now(ZoneId.of("UTC")), networkUuid, networkId, description, caseFormat, caseUuid, casePrivate, isPrivate, loadFlowStatus, loadFlowResult, loadFlowParameters, securityAnalysisUuid);
        studyRepository.save(studyEntity);
        emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES);
        return Mono.empty();
    }

    private Mono<Void> removeStudy(String studyName, String userId) {
        studyRepository.deleteByStudyNameAndUserId(userId, studyName);
        emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES);
        return Mono.empty();
    }

    private Mono<Void> insertStudyCreationRequest(String studyName, String userId, boolean isPrivate) {
        return Mono.fromRunnable(() -> {
            StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(userId, studyName, ZonedDateTime.now(ZoneId.of("UTC")));
            studyCreationRequestRepository.save(studyCreationRequestEntity);
            emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES);
        });
    }

    private void deleteStudyCreationRequest(String studyName, String userId) {
        studyCreationRequestRepository.deleteByStudyNameAndUserId(studyName, userId);
        emitStudyChanged(studyName, StudyService.UPDATE_TYPE_STUDIES);
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

    private Mono<? extends Throwable> handleStudyCreationError(String studyName, ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class).flatMap(body -> {
            try {
                String message;
                JsonNode node = new ObjectMapper().readTree(body).path("message");
                if (!node.isMissingNode()) {
                    message = node.asText();
                    emitStudyError(studyName, UPDATE_TYPE_STUDIES, message);
                }
            } catch (JsonProcessingException e) {
                if (!body.isEmpty()) {
                    emitStudyError(studyName, UPDATE_TYPE_STUDIES, body);
                }
            }
            return Mono.error(new StudyException(STUDY_CREATION_FAILED));
        });
    }

    Mono<UUID> importCase(Mono<FilePart> multipartFile, String studyName) {

        return multipartFile.flatMap(file -> {
            MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
            multipartBodyBuilder.part("file", file);

            return webClient.post()
                    .uri(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA.toString())
                    .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                            handleStudyCreationError(studyName, clientResponse)
                    )
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

    private Mono<NetworkInfos> persistentStore(UUID caseUuid, String studyName) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid)
                .buildAndExpand()
                .toUriString();

        return webClient.post()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, clientResponse ->
                        handleStudyCreationError(studyName, clientResponse)
                )
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

    Mono<Void> changeSwitchState(String studyName, String userId, String switchId, boolean open) {
        Mono<UUID> networkUuid = getNetworkUuid(studyName, userId);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/switches/{switchId}")
                    .queryParam("open", open)
                    .buildAndExpand(uuid, switchId)
                    .toUriString();

            Mono<Void> monoUpdateLfRes = Mono.fromRunnable(() -> {
                Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
                if (studyEntity.isPresent()) {
                    studyEntity.get().setLoadFlowResult(null);
                    studyRepository.save(studyEntity.get());
                }
            });
            Mono<Void> monoUpdateLfState = Mono.fromRunnable(() ->
                    studyRepository.updateLoadFlowStatus(studyName, userId, LoadFlowStatus.NOT_DONE))
                    .doOnSuccess(e ->
                            emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS))
                    .then(invalidateSecurityAnalysisStatus(studyName, userId)
                            .doOnSuccess(e ->
                                    emitStudyChanged(studyName, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)))
                    .doOnSuccess(e ->
                            emitStudyChanged(studyName, UPDATE_TYPE_SWITCH));

            Mono<Set<String>> monoChangeSwitchState = webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<>() { });

            return monoChangeSwitchState.flatMap(s -> Mono.fromRunnable(() -> emitStudyChanged(studyName, UPDATE_TYPE_STUDY, new TreeSet<>(s))))
                    .then(monoUpdateLfRes)
                    .then(monoUpdateLfState);
        });
    }

    public Mono<Void> applyGroovyScript(String studyName, String userId, String groovyScript) {
        Mono<UUID> networkUuid = getNetworkUuid(studyName, userId);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/groovy/")
                    .buildAndExpand(uuid)
                    .toUriString();

            Mono<Void> monoUpdateLfRes = Mono.fromRunnable(() -> {
                Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
                studyEntity.ifPresent(studyEntity1 -> {
                    studyEntity1.setLoadFlowResult(null);
                    studyRepository.save(studyEntity1);
                });
            });
            Mono<Void> monoUpdateLfState = Mono.fromRunnable(() -> studyRepository.updateLoadFlowStatus(studyName, userId, LoadFlowStatus.NOT_DONE))
                    .doOnSuccess(e -> emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS))
                    .then(invalidateSecurityAnalysisStatus(studyName, userId)
                            .doOnSuccess(e -> emitStudyChanged(studyName, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)));

            Mono<Set<String>> monoApplyGroovy = webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .body(BodyInserters.fromValue(groovyScript))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<>() {
                    });

            return monoApplyGroovy.flatMap(s -> {
                emitStudyChanged(studyName, UPDATE_TYPE_STUDY, new TreeSet<>(s));
                return Mono.empty();
            })
                    .then(monoUpdateLfRes)
                    .then(monoUpdateLfState);
        });
    }

    Mono<Object> runLoadFlow(String studyName, String userId) {
        return setLoadFlowRunning(studyName, userId).then(getNetworkUuid(studyName, userId).flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                    .buildAndExpand(uuid)
                    .toUriString();
            return webClient.put()
                .uri(loadFlowServerBaseUri + path)
                .retrieve()
                .bodyToMono(LoadFlowResult.class)
                .flatMap(result -> Mono.fromRunnable(() -> {
                    Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
                    if (studyEntity.isPresent()) {
                        studyEntity.get().setLoadFlowResult(toEntity(result));
                        studyRepository.save(studyEntity.get());
                    }
                    studyRepository.updateLoadFlowStatus(studyName, userId, result.isOk() ? LoadFlowStatus.CONVERGED : LoadFlowStatus.DIVERGED);
                })
                .doOnError(e ->
                        studyRepository.updateLoadFlowStatus(studyName, userId, LoadFlowStatus.NOT_DONE))
                .doOnCancel(() ->
                        studyRepository.updateLoadFlowStatus(studyName, userId, LoadFlowStatus.NOT_DONE)));
        }).doFinally(s ->
            emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW)
        ));

    }

    public Mono<StudyInfos> renameStudy(String studyName, String userId, String newStudyName) {
        Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
        if (studyEntity.isPresent()) {
            studyEntity.get().setStudyName(newStudyName);
            StudyEntity newStudyEntity = studyRepository.save(studyEntity.get());
            return Mono.just(toInfos(newStudyEntity));
        } else {
            return Mono.error(new StudyException(STUDY_NOT_FOUND));
        }
    }

    private Mono<Void> setLoadFlowRunning(String studyName, String userId) {
        return Mono.fromRunnable(() -> {
            studyRepository.updateLoadFlowStatus(studyName, userId, LoadFlowStatus.RUNNING);
            emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS);
        });
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

    public Mono<StudyInfos> changeStudyAccessRights(String studyName, String userId, String headerUserId, boolean toPrivate) {
        //only the owner of a study can change the access rights
        if (!headerUserId.equals(userId)) {
            throw new StudyException(NOT_ALLOWED);
        }

        return getStudy(studyName, userId).switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND))).flatMap(studyEntity -> {
            if (studyEntity.isPrivate() != toPrivate) {
                studyRepository.updateIsPrivate(userId, studyName, toPrivate);
                studyEntity.setPrivate(toPrivate);
            }
            return Mono.just(studyEntity);
        }).map(StudyService::toInfos);
    }

    Mono<UUID> getNetworkUuid(String studyName, String userId) {
        return Mono.fromCallable(() -> {
            Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
            if (studyEntity.isPresent()) {
                return studyEntity.get().getNetworkUuid();
            } else {
                throw new StudyException(STUDY_NOT_FOUND);
            }
        });
    }

    private void emitStudyChanged(String studyName, String updateType) {
        studyUpdatePublisher
                .onNext(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_NAME, studyName)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .build()
        );
    }

    private void emitStudyError(String studyName, String updateType, String errorMessage) {
        studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_NAME, studyName)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .setHeader(HEADER_ERROR, errorMessage)
                .build()
        );
    }

    private void emitStudyChanged(String studyName, String updateType, Set<String> substationsIds) {
        studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_NAME, studyName)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
                .build()
        );
    }

    Mono<Boolean> studyExists(String studyName, String userId) {
        return getStudy(studyName, userId).cast(BasicStudyEntity.class).hasElement().or(Mono.just(getStudyCreationRequest(studyName, userId)).hasElement());
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
        Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
        if (studyEntity.isPresent()) {
            if (studyEntity.get().getLoadFlowStatus().equals(LoadFlowStatus.NOT_DONE)) {
                return Mono.empty();
            } else {
                return Mono.error(new StudyException(LOADFLOW_NOT_RUNNABLE));
            }
        } else {
            return Mono.error(new StudyException(STUDY_NOT_FOUND));
        }
    }

    public Mono<Void> assertUserAllowed(String userId, String headerUserId) {
        return (userId.equals(headerUserId)) ? Mono.empty() : Mono.error(new StudyException(NOT_ALLOWED));
    }

    private Mono<Void> assertLoadFlowNotRunning(String studyName, String userId) {
        Mono<StudyEntity> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName).map(Mono::just).orElse(Mono.empty());
        return studyMono.map(StudyEntity::getLoadFlowStatus)
                .switchIfEmpty(Mono.error(new StudyException(STUDY_NOT_FOUND)))
                .flatMap(lfs -> lfs.equals(LoadFlowStatus.RUNNING) ? Mono.error(new StudyException(LOADFLOW_RUNNING)) : Mono.empty());
    }

    private Mono<Void> assertSecurityAnalysisNotRunning(String studyName, String userId) {
        Mono<String> statusMono = getSecurityAnalysisStatus(studyName, userId);
        return statusMono
                .flatMap(s -> s.equals(SecurityAnalysisStatus.RUNNING.name()) ? Mono.error(new StudyException(SECURITY_ANALYSIS_RUNNING)) : Mono.empty());
    }

    public Mono<Void> assertComputationNotRunning(String studyName, String userId) {
        return assertLoadFlowNotRunning(studyName, userId).and(assertSecurityAnalysisNotRunning(studyName, userId));
    }

    public static LoadFlowParametersEntity toEntity(LoadFlowParameters parameters) {
        Objects.requireNonNull(parameters);
        return new LoadFlowParametersEntity(
                parameters.getVoltageInitMode(),
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
                entity.getBalanceType());
    }

    public static LoadFlowResultEntity toEntity(LoadFlowResult result) {
        Objects.requireNonNull(result);
        return new LoadFlowResultEntity(
                result.isOk(),
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

    public static ComponentResultEntity toEntity(LoadFlowResult.ComponentResult componentResult) {
        Objects.requireNonNull(componentResult);
        return new ComponentResultEntity(
                componentResult.getComponentNum(),
                componentResult.getStatus(),
                componentResult.getIterationCount(),
                componentResult.getSlackBusId(),
                componentResult.getSlackBusActivePowerMismatch(),
                null
        );
    }

    public static LoadFlowResult.ComponentResult fromEntity(ComponentResultEntity entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowResultImpl.ComponentResultImpl(entity.getComponentNum(),
                entity.getStatus(),
                entity.getIterationCount(),
                entity.getSlackBusId(),
                entity.getSlackBusActivePowerMismatch());
    }

    public Mono<LoadFlowParameters> getLoadFlowParameters(String studyName, String userId) {
        return getStudy(studyName, userId).map(study -> fromEntity(study.getLoadFlowParameters()));
    }

    Mono<Void> setLoadFlowParameters(String studyName, String userId, LoadFlowParameters parameters) {
        studyRepository.findByUserIdAndStudyName(userId, studyName).ifPresent(study -> {
            study.setLoadFlowParameters(toEntity(parameters != null ? parameters : LoadFlowParameters.load()));
            study.setLoadFlowStatus(LoadFlowStatus.NOT_DONE);
            studyRepository.save(study);
            emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS);
            invalidateSecurityAnalysisStatus(studyName, userId)
                    .doOnSuccess(e -> emitStudyChanged(studyName, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS)).subscribe();

        });
        return Mono.empty();
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
        })
                .flatMap(result ->
                        Mono.fromRunnable(() ->
                                studyRepository.updateSecurityAnalysisResultUuid(studyName, userId, result))
                                .doOnSuccess(e ->
                                        emitStudyChanged(studyName, StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS))
                                .thenReturn(result)
                );
    }

    public Mono<String> getSecurityAnalysisResult(String studyName, String userId, List<String> limitTypes) {
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(limitTypes);

        Mono<StudyEntity> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName).map(Mono::just).orElse(Mono.empty());
        return   studyMono.flatMap(entity -> {
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

    public Mono<String> getSecurityAnalysisStatus(String studyName, String userId) {
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);

        Mono<StudyEntity> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName).map(Mono::just).orElse(Mono.empty());
        return studyMono.flatMap(entity -> {
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

    public Mono<Void> invalidateSecurityAnalysisStatus(String studyName, String userId) {
        Objects.requireNonNull(studyName);
        Objects.requireNonNull(userId);

        Optional<StudyEntity> studyEntity = studyRepository.findByUserIdAndStudyName(userId, studyName);
        if (studyEntity.isPresent()) {
            UUID resultUuid = studyEntity.get().getSecurityAnalysisResultUuid();
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
        } else {
            throw new StudyException(STUDY_NOT_FOUND);
        }
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
