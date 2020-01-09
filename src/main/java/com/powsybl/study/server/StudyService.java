/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server;

import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.study.server.dto.NetworkInfos;
import com.powsybl.study.server.dto.StudyInfos;
import com.powsybl.study.server.dto.VoltageLevelAttributes;
import com.powsybl.study.server.repository.Study;
import com.powsybl.study.server.repository.StudyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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

import static com.powsybl.study.server.StudyConstants.*;

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
    private String caseServerBaseUri;
    private String singleLineDiagramServerBaseUri;
    private String networkConversionServerBaseUri;
    private String geoDataServerBaseUri;

    @Autowired
    private NetworkStoreService networkStoreClient;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    public StudyService(
            @Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
            @Value("${backing-services.single-line-diagram.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
            @Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
            @Value("${backing-services.geo-data.base-uri:http://geo-data-store-server/}") String geoDataServerBaseUri
    ) {
        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        caseServerRest = restTemplateBuilder.build();
        caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));
        this.caseServerBaseUri = caseServerBaseUri;

        singleLineDiagramServerRest = restTemplateBuilder.build();
        singleLineDiagramServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(singleLineDiagramServerBaseUri));
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;

        networkConversionServerRest = restTemplateBuilder.build();
        networkConversionServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(networkConversionServerBaseUri));
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;

        geoDataServerRest = restTemplateBuilder.build();
        geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));
        this.geoDataServerBaseUri = geoDataServerBaseUri;
    }

    List<StudyInfos> getStudyList() {
        List<Study> studyList = studyRepository.findAll();
        return studyList.stream().map(study -> new StudyInfos(study.getName(), study.getDescription())).collect(Collectors.toList());
    }

    void createStudy(String studyName, String caseName, String description) {
        NetworkInfos networkInfos = persistentStore(caseName);
        Study study = new Study(studyName, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), caseName, description);
        studyRepository.insert(study);
    }

    void createStudy(String studyName, MultipartFile caseFile, String description) throws IOException {
        importCase(caseFile);
        NetworkInfos networkInfos = persistentStore(caseFile.getOriginalFilename());
        Study study = new Study(studyName, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(), caseFile.getOriginalFilename(), description);
        studyRepository.insert(study);
    }

    Study getStudy(String studyName) {
        return studyRepository.findByName(studyName).orElse(null);
    }

    void deleteStudy(String studyName) {
        studyRepository.deleteByName(studyName);
    }

    Map<String, String> getCaseList() {
        ParameterizedTypeReference<Map<String, String>> parameterizedTypeReference = new ParameterizedTypeReference<Map<String, String>>() { };
        ResponseEntity<Map<String, String>> responseEntity;
        try {
            responseEntity = caseServerRest.exchange("/" + CASE_API_VERSION + "/cases",
                    HttpMethod.GET,
                    null,
                    parameterizedTypeReference);

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return responseEntity.getBody();
            } else {
                return null;
            }
        } catch (HttpStatusCodeException e) {
            throw new StudyException("getCaseList HttpStatusCodeException", e);
        }
    }

    String importCase(MultipartFile multipartFile) throws IOException {
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
            ResponseEntity<String> responseEntity = caseServerRest.exchange("/" + CASE_API_VERSION + "/cases",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            throw new StudyException("importCase " + e.getStatusCode() + " : " + e.getResponseBodyAsString(), e);
        }
    }

    byte[] getVoltageLevelSvg(UUID networkUuid, String voltageLevelId) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put(NETWORK_UUID, networkUuid);
        urlParams.put("voltageLevelId", voltageLevelId);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(singleLineDiagramServerBaseUri + "/" + SINGLE_LINE_DIAGRAM_API_VERSION +
                "/svg/{networkUuid}/{voltageLevelId}")
                .uriVariables(urlParams);

        ResponseEntity<byte[]> responseEntity = singleLineDiagramServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                byte[].class);
        return responseEntity.getBody();
    }

    private NetworkInfos persistentStore(String caseName) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put(CASE_NAME, caseName);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(networkConversionServerBaseUri + "/" + NETWORK_CONVERSION_API_VERSION +
                "/networks").queryParam(CASE_NAME, caseName);
        ResponseEntity<NetworkInfos> responseEntity = networkConversionServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.POST,
                requestEntity,
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
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(geoDataServerBaseUri + "/" + GEO_DATA_API_VERSION +
                "/lines")
                .queryParam("networkUuid", networkUuid);

        ResponseEntity<String> responseEntity = geoDataServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                String.class);

        return responseEntity.getBody();
    }

    String getSubstationsGraphics(UUID networkUuid) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(geoDataServerBaseUri + "/" + GEO_DATA_API_VERSION +
                "/substations")
                .queryParam("networkUuid", networkUuid);

        ResponseEntity<String> responseEntity = geoDataServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                String.class);

        return responseEntity.getBody();
    }

    Boolean caseExists(String caseName) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put(CASE_NAME, caseName);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/{caseName}/exists")
                .uriVariables(urlParams);

        ResponseEntity<Boolean> responseEntity = caseServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                Boolean.class);

        return responseEntity.getBody();
    }

    UUID getStudyUuid(String studyName) {
        Optional<Study> study = studyRepository.findByName(studyName);
        if (study.isPresent()) {
            return study.get().getNetworkUuid();
        } else {
            throw new StudyException("study doesn't exist");
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

}
