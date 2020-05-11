/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.StudyInfos;
import org.gridsuite.study.server.dto.VoltageLevelAttributes;
import org.gridsuite.study.server.repository.Study;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@ComponentScan(basePackageClasses = {NetworkStoreService.class, StudyRepository.class})
@Service
public class StudyService {
    private WebClient webClient = WebClient.create();

    String caseServerBaseUri;
    String singleLineDiagramServerBaseUri;
    String networkConversionServerBaseUri;
    String geoDataServerBaseUri;
    String networkMapServerBaseUri;
    String networkModificationServerBaseUri;

    @Autowired
    private NetworkStoreService networkStoreClient;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    public StudyService(
            @Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
            @Value("${backing-services.single-line-diagram.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
            @Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
            @Value("${backing-services.geo-data.base-uri:http://geo-data-store-server/}") String geoDataServerBaseUri,
            @Value("${backing-services.network-map.base-uri:http://network-map-store-server/}") String networkMapServerBaseUri,
            @Value("${backing-services.network-modification.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri) {
        this.caseServerBaseUri = caseServerBaseUri;
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.geoDataServerBaseUri = geoDataServerBaseUri;
        this.networkMapServerBaseUri = networkMapServerBaseUri;
        this.networkModificationServerBaseUri = networkModificationServerBaseUri;
    }

    List<StudyInfos> getStudyList() {
        List<Study> studyList = studyRepository.findAll();
        return studyList.stream().map(study -> new StudyInfos(study.getName(), study.getDescription(), study.getCaseFormat())).collect(Collectors.toList());
    }

    void createStudy(String studyName, UUID caseUuid, String description) {
        NetworkInfos networkInfos = persistentStore(caseUuid);
        String caseFormat = getCaseFormat(caseUuid);
        Study study = new Study(studyName, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), description, caseFormat, caseUuid, false);
        studyRepository.insert(study);
    }

    private String getCaseFormat(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/cases/{caseUuid}/format")
                .buildAndExpand(caseUuid)
                .toUriString();

        return webClient.get()
                .uri(caseServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    void createStudy(String studyName, MultipartFile caseFile, String description) throws IOException {
        UUID caseUUid = importCase(caseFile);
        NetworkInfos networkInfos = persistentStore(caseUUid);
        String caseFormat = getCaseFormat(caseUUid);
        Study study = new Study(studyName, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), description, caseFormat, caseUUid, true);
        studyRepository.insert(study);
    }

    Study getStudy(String studyName) {
        return studyRepository.findByName(studyName).orElse(null);
    }

    void deleteStudy(String studyName) {
        Optional<Study> studyOpt = studyRepository.findByName(studyName);
        if (studyOpt.isEmpty()) {
            return;
        }
        Study study = studyOpt.get();

        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/cases/{caseUuid}")
                .buildAndExpand(study.getCaseUuid())
                .toUriString();

        if (study.isCasePrivate()) {
            webClient.delete()
                    .uri(caseServerBaseUri + path)
                    .retrieve();
        }
        studyRepository.deleteByName(studyName);
    }

    UUID importCase(MultipartFile multipartFile) throws IOException {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        final String filename = multipartFile.getOriginalFilename();
        map.add("name", filename);
        map.add("filename", filename);
        ByteArrayResource contentsAsResource = new ByteArrayResource(multipartFile.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        map.add("file", contentsAsResource);

        return webClient.post()
                .uri(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA.toString())
                .body(BodyInserters.fromValue(map))
                .retrieve()
                .bodyToMono(UUID.class)
                .block();
    }

    byte[] getVoltageLevelSvg(UUID networkUuid, String voltageLevelId, boolean useName, boolean centerLabel, boolean diagonalLabel,
                              boolean topologicalColoring) {
        String path = UriComponentsBuilder.fromPath("/" + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .buildAndExpand(networkUuid, voltageLevelId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    String getVoltageLevelSvgAndMetadata(UUID networkUuid, String voltageLevelId, boolean useName, boolean centerLabel, boolean diagonalLabel,
                                         boolean topologicalColoring) {
        String path = UriComponentsBuilder.fromPath("/" + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-and-metadata/{networkUuid}/{voltageLevelId}")
                .queryParam("useName", useName)
                .queryParam("centerLabel", centerLabel)
                .queryParam("diagonalLabel", diagonalLabel)
                .queryParam("topologicalColoring", topologicalColoring)
                .buildAndExpand(networkUuid, voltageLevelId)
                .toUriString();

        return webClient.get()
                .uri(singleLineDiagramServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private NetworkInfos persistentStore(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath("/" + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid)
                .buildAndExpand()
                .toUriString();

        return webClient.post()
                .uri(networkConversionServerBaseUri + path)
                .retrieve()
                .bodyToMono(NetworkInfos.class)
                .block();

    }

    List<VoltageLevelAttributes> getNetworkVoltageLevels(UUID networkUuid) {
        ArrayList<VoltageLevelAttributes> voltageLevelAttributes = new ArrayList<>();
        Iterable<VoltageLevel> voltageLevels = networkStoreClient.getNetwork(networkUuid).getVoltageLevels();
        for (VoltageLevel voltageLevel : voltageLevels) {
            String voltageLevelId = voltageLevel.getId();
            String voltageName = voltageLevel.getName();
            String substationId = voltageLevel.getSubstation().getId();
            voltageLevelAttributes.add(new VoltageLevelAttributes(voltageLevelId, voltageName, substationId));
        }
        return voltageLevelAttributes;
    }

    String getLinesGraphics(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("/" + GEO_DATA_API_VERSION + "/lines")
                .queryParam(NETWORK_UUID, networkUuid)
                .buildAndExpand()
                .toUriString();

        return webClient.get()
                .uri(geoDataServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    String getSubstationsGraphics(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("/" + GEO_DATA_API_VERSION + "/substations")
                .queryParam(NETWORK_UUID, networkUuid)
                .buildAndExpand()
                .toUriString();

        return webClient.get()
                .uri(geoDataServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    boolean caseExists(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/cases/{caseUuid}/exists")
                .buildAndExpand(caseUuid)
                .toUriString();

        String caseExists = webClient.get()
                .uri(caseServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return Boolean.TRUE.equals(Boolean.parseBoolean(caseExists));
    }

    String getSubstationsMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/substations/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    String getLinesMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/lines/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        return webClient.get()
                .uri(networkMapServerBaseUri + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    void changeSwitchState(String studyName, String switchId, boolean open) {
        UUID networkUuid = getStudyUuid(studyName);

        String path = UriComponentsBuilder.fromPath("/" + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/switches/{switchId}")
                .queryParam("open", open)
                .buildAndExpand(networkUuid, switchId)
                .toUriString();

        webClient.put()
                .uri(networkModificationServerBaseUri + path)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }

    @Transactional
    Study renameStudy(String studyName, String newStudyName) {
        Optional<Study> studyOpt = studyRepository.findByName(studyName);
        if (studyOpt.isEmpty()) {
            throw new StudyException(STUDY_DOESNT_EXISTS);
        }
        Study study = studyOpt.get();
        study.setName(newStudyName);
        studyRepository.insert(study);
        studyRepository.deleteByName(studyName);
        return study;
    }

    UUID getStudyUuid(String studyName) {
        Optional<Study> study = studyRepository.findByName(studyName);
        if (study.isPresent()) {
            return study.get().getNetworkUuid();
        } else {
            throw new StudyException(STUDY_DOESNT_EXISTS);
        }
    }

    boolean studyExists(String studyName) {
        return getStudy(studyName) != null;
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
}
