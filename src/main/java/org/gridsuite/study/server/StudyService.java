/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.StudyInfos;
import org.gridsuite.study.server.dto.VoltageLevelAttributes;
import org.gridsuite.study.server.repository.Study;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.DefaultUriBuilderFactory;
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

    private RestTemplate caseServerRest;
    private RestTemplate singleLineDiagramServerRest;
    private RestTemplate networkConversionServerRest;
    private RestTemplate geoDataServerRest;
    private RestTemplate networkMapServerRest;
    private RestTemplate networkModificationServerRest;
    private RestTemplate loadFlowServerRest;

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
            @Value("${backing-services.network-modification.base-uri:http://network-modification-server/}") String networkModificationServerBaseUri,
            @Value("${backing-services.loadflow.base-uri:http://loadflow-server/}") String loadFlowServerBaseUri) {
        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        caseServerRest = restTemplateBuilder.build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));

        singleLineDiagramServerRest = restTemplateBuilder.build();
        singleLineDiagramServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(singleLineDiagramServerBaseUri));

        networkConversionServerRest = restTemplateBuilder.build();
        networkConversionServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(networkConversionServerBaseUri));

        geoDataServerRest = restTemplateBuilder.build();
        geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));

        networkMapServerRest = restTemplateBuilder.build();
        networkMapServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(networkMapServerBaseUri));

        networkModificationServerRest = restTemplateBuilder.build();
        networkModificationServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(networkModificationServerBaseUri));

        loadFlowServerRest = restTemplateBuilder.build();
        loadFlowServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(loadFlowServerBaseUri));
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

        ResponseEntity<String> responseEntity = caseServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        return responseEntity.getBody();
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
        if (study.isCasePrivate()) {
            try {
                caseServerRest.delete("/" + CASE_API_VERSION + "/cases/{caseUuid}", study.getCaseUuid());
            } catch (HttpStatusCodeException e) {
                throw new StudyException("deleteStudy HttpStatusCodeException", e);
            }
        }
        studyRepository.deleteByName(studyName);
    }

    List<CaseInfos> getCaseList() {
        ParameterizedTypeReference<List<CaseInfos>> parameterizedTypeReference = new ParameterizedTypeReference<List<CaseInfos>>() { };
        ResponseEntity<List<CaseInfos>> responseEntity;
        try {
            responseEntity = caseServerRest.exchange("/" + CASE_API_VERSION + "/cases",
                    HttpMethod.GET,
                    null,
                    parameterizedTypeReference);

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return responseEntity.getBody();
            } else {
                return Collections.emptyList();
            }
        } catch (HttpStatusCodeException e) {
            throw new StudyException("getCaseList HttpStatusCodeException", e);
        }
    }

    UUID importCase(MultipartFile multipartFile) throws IOException {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

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

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(map, requestHeaders);

        try {
            ResponseEntity<UUID> responseEntity = caseServerRest.exchange("/" + CASE_API_VERSION + "/cases/private",
                    HttpMethod.POST,
                    requestEntity,
                    UUID.class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            throw new StudyException("importCase " + e.getStatusCode() + " : " + e.getResponseBodyAsString(), e);
        }
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

        ResponseEntity<byte[]> responseEntity = singleLineDiagramServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                byte[].class);
        return responseEntity.getBody();
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

        ResponseEntity<String> responseEntity = singleLineDiagramServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        return responseEntity.getBody();
    }

    private NetworkInfos persistentStore(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath("/" + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid)
                .buildAndExpand()
                .toUriString();

        ResponseEntity<NetworkInfos> responseEntity = networkConversionServerRest.exchange(path,
                HttpMethod.POST,
                HttpEntity.EMPTY,
                NetworkInfos.class);
        return responseEntity.getBody();
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

        ResponseEntity<String> responseEntity = geoDataServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        return responseEntity.getBody();
    }

    String getSubstationsGraphics(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("/" + GEO_DATA_API_VERSION + "/substations")
                .queryParam(NETWORK_UUID, networkUuid)
                .buildAndExpand()
                .toUriString();

        ResponseEntity<String> responseEntity = geoDataServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        return responseEntity.getBody();
    }

    boolean caseExists(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/cases/{caseUuid}/exists")
                .buildAndExpand(caseUuid)
                .toUriString();

        ResponseEntity<Boolean> responseEntity = caseServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Boolean.class);

        return Boolean.TRUE.equals(responseEntity.getBody());
    }

    String getSubstationsMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/substations/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        ResponseEntity<String> responseEntity = networkMapServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        return responseEntity.getBody();
    }

    String getLinesMapData(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath("/" + CASE_API_VERSION + "/lines/{networkUuid}")
                .buildAndExpand(networkUuid)
                .toUriString();

        ResponseEntity<String> responseEntity = networkMapServerRest.exchange(path,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        return responseEntity.getBody();
    }

    void changeSwitchState(String studyName, String switchId, boolean open) {
        UUID networkUuid = getStudyUuid(studyName);

        String path = UriComponentsBuilder.fromPath("/" + NETWORK_MODIFICATION_API_VERSION + "/networks/{networkUuid}/switches/{switchId}")
                .queryParam("open", open)
                .buildAndExpand(networkUuid, switchId)
                .toUriString();

        networkModificationServerRest.exchange(path,
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void.class);
    }

    void runLoadFlow(String studyName) {
        UUID networkUuid = getStudyUuid(studyName);

        String path = UriComponentsBuilder.fromPath("/" + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                .buildAndExpand(networkUuid)
                .toUriString();

        loadFlowServerRest.exchange(path,
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void.class);
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

    void setCaseServerRest(RestTemplate caseServerRest) {
        this.caseServerRest = Objects.requireNonNull(caseServerRest);
    }

    void setSingleLineDiagramServerRest(RestTemplate singleLineDiagramServerRest) {
        this.singleLineDiagramServerRest = Objects.requireNonNull(singleLineDiagramServerRest);
    }

    void setNetworkConversionServerRest(RestTemplate networkConversionServerRest) {
        this.networkConversionServerRest = Objects.requireNonNull(networkConversionServerRest);
    }

    void setGeoDataServerRest(RestTemplate geoDataServerRest) {
        this.geoDataServerRest = Objects.requireNonNull(geoDataServerRest);
    }

    void setNetworkMapServerRest(RestTemplate networkMapServerRest) {
        this.networkMapServerRest = Objects.requireNonNull(networkMapServerRest);
    }

    void setNetworkModificationServerRest(RestTemplate networkModificationServerRest) {
        this.networkModificationServerRest = Objects.requireNonNull(networkModificationServerRest);
    }
}
