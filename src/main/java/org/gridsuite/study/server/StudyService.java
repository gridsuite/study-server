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
import org.gridsuite.study.server.repository.Study;
import org.gridsuite.study.server.repository.StudyRepository;
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
    }

    Flux<StudyInfos> getStudyList() {
        Flux<Study> studyList = studyRepository.findAll();
        return studyList.map(study -> new StudyInfos(study.getName(), study.getDescription(), study.getCaseFormat()));
    }

    Mono<Study> createStudy(String studyName, UUID caseUuid, String description, LoadFlowResult loadFlowResult) {
        Mono<NetworkInfos> networkInfos = persistentStore(caseUuid);
        Mono<String> caseFormat = getCaseFormat(caseUuid);

        return Mono.zip(networkInfos, caseFormat)
                .flatMap(t -> {
                    Study study = new Study(studyName, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(), description, t.getT2(), caseUuid, false, loadFlowResult);
                    return studyRepository.insert(study);
                });
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

    Mono<Study> createStudy(String studyName, Mono<FilePart> caseFile, String description) {
        Mono<UUID> caseUUid;
        caseUUid = importCase(caseFile);
        return caseUUid.flatMap(uuid -> {
            Mono<NetworkInfos> networkInfos = persistentStore(uuid);
            Mono<String> caseFormat = getCaseFormat(uuid);
            return Mono.zip(networkInfos, caseFormat)
                    .flatMap(t -> {
                        Study study = new Study(studyName, t.getT1().getNetworkUuid(), t.getT1().getNetworkId(), description, t.getT2(), uuid, true, new LoadFlowResult());
                        return studyRepository.insert(study);
                    });
        });
    }

    Mono<Study> getStudy(String studyName) {
        return studyRepository.findByName(studyName);
    }

    Mono<Void> deleteStudy(String studyName) {
        Mono<Study> studyMono = studyRepository.findByName(studyName);

        return studyMono.flatMap(study -> {
            if (study.isCasePrivate()) {
                String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}")
                        .buildAndExpand(study.getCaseUuid())
                        .toUriString();

                return webClient.delete()
                        .uri(caseServerBaseUri + path)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .then(studyRepository.deleteByName(studyName));
            } else {
                return studyRepository.deleteByName(studyName);
            }
        });
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

    Mono<Void> changeSwitchState(String studyName, String switchId, boolean open) {
        Mono<UUID> networkUuid = getStudyUuid(studyName);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/switches/{switchId}")
                    .queryParam("open", open)
                    .buildAndExpand(uuid, switchId)
                    .toUriString();
            return webClient.put()
                    .uri(networkModificationServerBaseUri + path)
                    .retrieve()
                    .bodyToMono(Void.class);
        }).then(studyRepository.updateLoadFlowState(studyName, LoadFlowResult.LoadFlowStatus.NOT_DONE)
        .doOnSuccess(e -> studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
            .setHeader(STUDY_NAME, studyName)
            .setHeader(UPDATE_TYPE, UPDATE_TYPE_LOADFLOW)
            .build())))
        .doOnSuccess(e -> studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                .setHeader(STUDY_NAME, studyName)
                .setHeader(UPDATE_TYPE, UPDATE_TYPE_SWITCH)
                .build())
        );
    }

    Mono<Void> runLoadFlow(String studyName) {
        Mono<UUID> networkUuid = getStudyUuid(studyName);

        return networkUuid.flatMap(uuid -> {
            String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                    .buildAndExpand(uuid)
                    .toUriString();

            return webClient.put()
                .uri(loadFlowServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(e -> studyRepository.updateLoadFlowResult(studyName, jsonToLoadFlowTesult(e)))
                .doOnError(e -> studyRepository.updateLoadFlowState(studyName, LoadFlowResult.LoadFlowStatus.NOT_DONE).block());

        }).doOnSuccess(s ->
            studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                .setHeader(STUDY_NAME, studyName)
                .setHeader(UPDATE_TYPE, UPDATE_TYPE_LOADFLOW)
                .build()
            )
        );
    }

    private LoadFlowResult jsonToLoadFlowTesult(String strLfResult) {
        try {
            String strStatus = JsonPathUtils.evaluate(strLfResult, "$.metrics.network_0_status");
            LoadFlowResult.LoadFlowStatus status;
            switch (strStatus) {
                case "DIVERGED": status = LoadFlowResult.LoadFlowStatus.DIVERGED; break;
                case "CONVERGED": status = LoadFlowResult.LoadFlowStatus.CONVERGED; break;
                default: status = LoadFlowResult.LoadFlowStatus.NOT_DONE;
            }
            return new LoadFlowResult(status);
        } catch (IOException e) {
            return new LoadFlowResult(LoadFlowResult.LoadFlowStatus.NOT_DONE);
        }
    }

    @Transactional
    public Mono<Study> renameStudy(String studyName, String newStudyName) {
        Mono<Study> studyMono = studyRepository.findByName(studyName);
        return studyMono.switchIfEmpty(Mono.error(new StudyException(STUDY_DOESNT_EXISTS))).flatMap(study -> {
            study.setName(newStudyName);
            Mono<Study> newStudyMono = studyRepository.insert(study);
            Mono<Void> deleted = studyRepository.deleteByName(studyName);
            return deleted.then(newStudyMono);
        });
    }

    @Transactional
    public Mono<Void> setLoadFlowRunning(String studyName) {
        return Mono.when(assertLoadFlowRunnable(studyName))
            .then(studyRepository.updateLoadFlowState(studyName, LoadFlowResult.LoadFlowStatus.RUNNING))
            .doOnSuccess(s -> studyUpdatePublisher.onNext(MessageBuilder.withPayload("")
                .setHeader(STUDY_NAME, studyName)
                .setHeader(UPDATE_TYPE, UPDATE_TYPE_LOADFLOW)
                .build()));
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

    public Mono<ExportNetworkInfos> exportNetwork(String studyName, String format) {
        Mono<UUID> networkUuidMono = getStudyUuid(studyName);

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

    Mono<UUID> getStudyUuid(String studyName) {
        Mono<Study> studyMono = studyRepository.findByName(studyName);
        return studyMono.map(Study::getNetworkUuid)
                .switchIfEmpty(Mono.error(new StudyException(STUDY_DOESNT_EXISTS)));

    }

    Mono<Boolean> studyExists(String studyName) {
        return getStudy(studyName).hasElement();
    }

    public Mono<Void> assertCaseExists(UUID caseUuid) {
        Mono<Boolean> caseExists = caseExists(caseUuid);
        return caseExists.flatMap(c -> (boolean) c ? Mono.empty() : Mono.error(new StudyException(CASE_DOESNT_EXISTS)));
    }

    public Mono<Void> assertStudyNotExists(String studyName) {
        Mono<Boolean> studyExists = studyExists(studyName);
        return studyExists.flatMap(s -> (boolean) s ? Mono.error(new StudyException(STUDY_ALREADY_EXISTS)) : Mono.empty());
    }

    public Mono<Void> assertLoadFlowRunnable(String studyName) {
        Mono<Study> studyMono = studyRepository.findByName(studyName);
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
