/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.model.TopLevelDocument;
import org.gridsuite.study.server.dto.ExportNetworkInfos;
import org.gridsuite.study.server.dto.LoadFlowResult;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.StudyInfos;
import org.gridsuite.study.server.dto.VoltageLevelAttributes;
import org.gridsuite.study.server.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.integration.json.JsonPathUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.messaging.Message;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.context.annotation.Bean;
import reactor.util.function.Tuple2;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@ComponentScan(basePackageClasses = {NetworkStoreService.class, StudyRepository.class})
@Service
public class StudyService {

    private static final String STUDY_NAME = "studyName";
    private static final String UPDATE_TYPE = "updateType";
    private static final String UPDATE_TYPE_LOADFLOW = "loadflow";
    private static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    private static final String UPDATE_TYPE_SWITCH = "switch";

    private WebClient webClient;

    String caseServerBaseUri;
    String singleLineDiagramServerBaseUri;
    String networkConversionServerBaseUri;
    String geoDataServerBaseUri;
    String networkMapServerBaseUri;
    String networkModificationServerBaseUri;
    String loadFlowServerBaseUri;
    String networkStoreServerBaseUri;

    private final StudyRepository studyRepository;
    private final PrivateStudyRepository privateStudyRepository;
    private final PublicStudyRepository publicStudyRepository;

    private EmitterProcessor<Message<String>> studyUpdatePublisher = EmitterProcessor.create();

    @Bean
    public Supplier<Flux<Message<String>>> publishStudyUpdate() {
        return () -> studyUpdatePublisher;
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
            StudyRepository studyRepository,
            PrivateStudyRepository privateStudyRepository,
            PublicStudyRepository publicStudyRepository,
            WebClient.Builder webClientBuilder) {
        this.caseServerBaseUri = caseServerBaseUri;
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.geoDataServerBaseUri = geoDataServerBaseUri;
        this.networkMapServerBaseUri = networkMapServerBaseUri;
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
        this.networkStoreServerBaseUri = networkStoreServerBaseUri;

        this.webClient =  webClientBuilder.build();

        this.studyRepository = studyRepository;
        this.privateStudyRepository = privateStudyRepository;
        this.publicStudyRepository = publicStudyRepository;
    }

    Flux<StudyInfos> getStudyList(String userId) {
        Flux<PrivateStudy> privateStudyFlux = privateStudyRepository.findAllByUserId(userId);

        return Flux.concat(privateStudyFlux.map(privateStudy ->
            new StudyInfos(privateStudy.getStudyName(), privateStudy.getUserId(), privateStudy.getDescription(), privateStudy.getCaseFormat())
        ), getPublicStudyList());
    }

    Flux<StudyInfos> getPublicStudyList() {
        Flux<PublicStudy> publicStudyFlux = publicStudyRepository.findAll();
        return publicStudyFlux.map(publicStudy ->
            new StudyInfos(publicStudy.getStudyName(), publicStudy.getUserId(), publicStudy.getDescription(), publicStudy.getCaseFormat())
        );
    }

    @Transactional
    public Mono<Study> createStudy(String studyName, UUID caseUuid, String description, String userId, Boolean isPrivate, LoadFlowResult loadFlowResult) {
        Mono<NetworkInfos> networkInfos = persistentStore(caseUuid);
        Mono<String> caseFormat = getCaseFormat(caseUuid);

        return Mono.zip(networkInfos, caseFormat)
            .flatMap(t ->
                    insertStudy(studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(), description, t.getT2(), caseUuid, false, loadFlowResult)
            );
    }

    private Mono<String> getCaseFormat(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/format")
                .buildAndExpand(caseUuid)
                .toUriString();

        return webClient.get()
                .uri(caseServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class);
    }

    @Transactional
     public Mono<Study> createStudy(String studyName, Mono<FilePart> caseFile, String description, String userId, Boolean isPrivate) {
        Mono<UUID> caseUUid;
        caseUUid = importCase(caseFile);

        return caseUUid.flatMap(uuid -> {
            Mono<NetworkInfos> networkInfos = persistentStore(uuid);
            Mono<String> caseFormat = getCaseFormat(uuid);
            return Mono.zip(networkInfos, caseFormat)
                    .flatMap(t ->
                            insertStudy(studyName, userId, isPrivate, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(), description, t.getT2(), uuid, true, new LoadFlowResult())
                    );
        });
    }

    private Mono<Study> insertStudy(String studyName, String userId, boolean isPrivate, UUID networkUuid, String networkId, String description, String caseFormat, UUID caseUuid, boolean isCasePrivate, LoadFlowResult loadFlowResult) {
        final PrivateStudy privateStudy = new PrivateStudy(userId, studyName, networkUuid, networkId, description, caseFormat, caseUuid, isCasePrivate, isPrivate);
        final PublicStudy publicStudy = new PublicStudy(userId, studyName, networkUuid, networkId, description, caseFormat, caseUuid, isCasePrivate, isPrivate);
        final Study study = new Study(userId, studyName, networkUuid, networkId, description, caseFormat, caseUuid, isCasePrivate, isPrivate, loadFlowResult);
        if (!isPrivate) {
            return Mono.zip(publicStudyRepository.insert(publicStudy), studyRepository.insert(study))
                    .map(Tuple2::getT2);
        } else {
            return Mono.zip(privateStudyRepository.insert(privateStudy), studyRepository.insert(study))
                    .map(Tuple2::getT2);
        }
    }

    Mono<Study> getCurrentUserStudy(String studyName, String userId, String headerUserId) {
        Mono<Study> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName);
        return studyMono.flatMap(study -> {
            if (study.isPrivate() && !userId.equals(headerUserId)) {
                return Mono.error(new StudyException(NOT_ALLOWED));
            } else {
                return Mono.just(study);
            }
        });
    }

    Mono<Study> getStudy(String studyName, String userId) {
        return studyRepository.findByUserIdAndStudyName(userId, studyName);
    }

    @Transactional
    public Mono<Void> deleteStudy(String studyName, String userId, String headerUserId) {
        //we need to ensure that it's the initial creator that deletes it
        if (!userId.equals(headerUserId)) {
            return Mono.error(new StudyException(NOT_ALLOWED));
        }
        Mono<Study> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName);
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

    Mono<Void> deleteStudy(String userId, String studyName) {
        return Mono.zip(privateStudyRepository.delete(userId, studyName),
                publicStudyRepository.delete(studyName, userId),
                studyRepository.deleteByStudyNameAndUserId(studyName, userId)).then();
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
                    .bodyToMono(UUID.class);
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
                .bodyToMono(NetworkInfos.class);
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

    Mono<Void> changeSwitchState(String studyName, String userId, String switchId, boolean open) {
        Mono<UUID> networkUuid = getStudyUuid(studyName, userId);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/switches/{switchId}")
                    .queryParam("open", open)
                    .buildAndExpand(uuid, switchId)
                    .toUriString();
            return webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .retrieve()
                    .bodyToMono(Void.class);
        }).then(studyRepository.updateLoadFlowState(studyName, userId, LoadFlowResult.LoadFlowStatus.NOT_DONE)
        .doOnSuccess(e -> emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW_STATUS)))
        .doOnSuccess(e -> studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                .setHeader(STUDY_NAME, studyName)
                .setHeader(UPDATE_TYPE, UPDATE_TYPE_SWITCH)
                .build())
        );
    }

    Mono<Void> runLoadFlow(String studyName, String userId) {
        Mono<UUID> networkUuid = getStudyUuid(studyName, userId);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                    .buildAndExpand(uuid)
                    .toUriString();

            return webClient.put()
                .uri(loadFlowServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(e -> studyRepository.updateLoadFlowResult(studyName, userId, jsonToLoadFlowResult(e)))
                .doOnError(e -> studyRepository.updateLoadFlowState(studyName, userId, LoadFlowResult.LoadFlowStatus.NOT_DONE)
                    .subscribe())
                .doOnCancel(() -> studyRepository.updateLoadFlowState(studyName, userId, LoadFlowResult.LoadFlowStatus.NOT_DONE)
                    .subscribe())
                .doOnTerminate(() -> studyRepository.updateLoadFlowState(studyName, userId, LoadFlowResult.LoadFlowStatus.NOT_DONE)
                    .subscribe());
        }).doFinally(s ->
            emitStudyChanged(studyName, UPDATE_TYPE_LOADFLOW)
        );
    }

    private void emitStudyChanged(String studyName, String updateTypeLoadflow) {
        studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
            .setHeader(STUDY_NAME, studyName)
            .setHeader(UPDATE_TYPE, updateTypeLoadflow)
            .build()
        );
    }

    private LoadFlowResult jsonToLoadFlowResult(String strLfResult) {
        try {
            Boolean bStatus = JsonPathUtils.evaluate(strLfResult, "$.ok");
            LoadFlowResult.LoadFlowStatus status;
            if (bStatus) {
                status = LoadFlowResult.LoadFlowStatus.CONVERGED;
            } else {
                status = LoadFlowResult.LoadFlowStatus.DIVERGED;
            }
            return new LoadFlowResult(status);
        } catch (IOException e) {
            return new LoadFlowResult(LoadFlowResult.LoadFlowStatus.NOT_DONE);
        }
    }

    @Transactional
    public Mono<Study> renameStudy(String studyName, String userId, String headerUserId, String newStudyName) {
        //we need to ensure that it's the initial creator that deletes it
        if (!userId.equals(headerUserId)) {
            return Mono.error(new StudyException(NOT_ALLOWED));
        }
        Mono<Study> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName);
        return studyMono.switchIfEmpty(Mono.error(new StudyException(STUDY_DOESNT_EXISTS))).flatMap(study -> {
            study.setStudyName(newStudyName);

            Mono<Void> deleteStudy = deleteStudy(userId, studyName);
            Mono<Study> insertStudy = insertStudy(newStudyName, userId, study.isPrivate(), study.getNetworkUuid(), study.getNetworkId(),
                    study.getDescription(), study.getCaseFormat(), study.getCaseUuid(), study.isCasePrivate(), study.getLoadFlowResult());

            return deleteStudy.then(insertStudy);
        });
    }

    public Mono<Void> setLoadFlowRunning(String studyName, String userId) {
        return Mono.when(assertLoadFlowRunnable(studyName, userId))
            .then(studyRepository.updateLoadFlowState(studyName, userId, LoadFlowResult.LoadFlowStatus.RUNNING))
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
        Mono<UUID> networkUuidMono = getStudyUuid(studyName, userId);

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

    Mono<UUID> getStudyUuid(String studyName, String userId) {
        Mono<Study> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName);
        return studyMono.map(Study::getNetworkUuid)
                .switchIfEmpty(Mono.error(new StudyException(STUDY_DOESNT_EXISTS)));

    }

    Mono<Boolean> studyExists(String studyName, String userId) {
        return getStudy(studyName, userId).hasElement();
    }

    public Mono<Void> assertCaseExists(UUID caseUuid) {
        Mono<Boolean> caseExists = caseExists(caseUuid);
        return caseExists.flatMap(c -> (boolean) c ? Mono.empty() : Mono.error(new StudyException(CASE_DOESNT_EXISTS)));
    }

    public Mono<Void> assertStudyNotExists(String studyName, String userId) {
        Mono<Boolean> studyExists = studyExists(studyName, userId);
        return studyExists.flatMap(s -> (boolean) s ? Mono.error(new StudyException(STUDY_ALREADY_EXISTS)) : Mono.empty());
    }

    public Mono<Void> assertLoadFlowRunnable(String studyName, String userId) {
        Mono<Study> studyMono = studyRepository.findByUserIdAndStudyName(userId, studyName);
        return studyMono.map(Study::getLoadFlowResult)
            .switchIfEmpty(Mono.error(new StudyException(STUDY_DOESNT_EXISTS)))
            .flatMap(lfr -> lfr.getStatus().equals(LoadFlowResult.LoadFlowStatus.NOT_DONE) ? Mono.empty() : Mono.error(new StudyException(LOADFLOW_NOT_RUNNABLE)));
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
}
