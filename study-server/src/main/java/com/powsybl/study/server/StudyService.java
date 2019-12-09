/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.converter.model.NetworkIds;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.study.server.dto.Study;
import com.powsybl.study.server.dto.VoltageLevelAttributes;
import infrastructure.LineGraphic;
import infrastructure.SubstationGraphic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static com.powsybl.study.server.StudyConstants.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */


@ComponentScan(basePackageClasses = {NetworkStoreService.class, StudyRepository.class})
@Service
public class StudyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyService.class);

    private RestTemplate caseServerRest;
    private RestTemplate voltageLevelDiagramServerRest;
    private RestTemplate iidmConverterServerRest;
    private RestTemplate geoDataServerRest;
    private String caseServerBaseUri;
    private String voltageLevelDiagramServerBaseUri;
    private String iidmServerBaseUri;
    private String geoDataServerBaseUri;

    @Autowired
    private NetworkStoreService networkStoreClient;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    public StudyService(
            @Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
            @Value("${backing-services.voltage-level-diagram.base-uri:http://voltage-level-diagram-server/}") String voltageLevelDiagramServerBaseUri,
            @Value("${backing-services.iidm-converter.base-uri:http://iidm-converter-server/}") String iidmServerBaseUri,
            @Value("${backing-services.geo-data.base-uri:http://geo-data-store-server/}") String geoDataServerBaseUri
    ) {
        RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
        this.caseServerRest = restTemplateBuilder.build();
        this.caseServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(caseServerBaseUri));
        this.caseServerBaseUri = caseServerBaseUri;

        restTemplateBuilder = new RestTemplateBuilder();
        this.voltageLevelDiagramServerRest = restTemplateBuilder.build();
        this.voltageLevelDiagramServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(voltageLevelDiagramServerBaseUri));
        this.voltageLevelDiagramServerBaseUri = voltageLevelDiagramServerBaseUri;

        restTemplateBuilder = new RestTemplateBuilder();
        this.iidmConverterServerRest = restTemplateBuilder.build();
        this.iidmConverterServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(iidmServerBaseUri));
        this.iidmServerBaseUri = iidmServerBaseUri;

        restTemplateBuilder = new RestTemplateBuilder();
        this.geoDataServerRest = restTemplateBuilder.build();
        this.geoDataServerRest.setUriTemplateHandler(new DefaultUriBuilderFactory(geoDataServerBaseUri));
        this.geoDataServerBaseUri = geoDataServerBaseUri;
    }

    List<Study> getStudyList() {
        List<Study> studies;
        studies = studyRepository.findAll();

        return studies;
    }

    NetworkIds createStudy(String studyName, String caseName, String description) {
        NetworkIds networkIds = persistentStore(caseName);
        Study study = new Study(studyName, networkIds.getNetworkUuid(), networkIds.getNetworkId(), caseName, description);
        studyRepository.insert(study);
        return networkIds;
    }

    NetworkIds createStudy(String studyName, MultipartFile caseFile, String description) throws IOException {
        importCase(caseFile);
        NetworkIds networkIds = persistentStore(caseFile.getOriginalFilename());
        Study study = new Study(studyName, networkIds.getNetworkUuid(), networkIds.getNetworkId(), caseFile.getOriginalFilename(), description);
        studyRepository.insert(study);
        return networkIds;
    }

    Study getStudy(String studyName) {
        Optional<Study> study = studyRepository.findByName(studyName);
        return study.orElse(null);
    }

    void deleteStudy(String studyName) {
        studyRepository.deleteByName(studyName);
    }

    Map<String, String> getCaseList() {
        ParameterizedTypeReference<Map<String, String>> parameterizedTypeReference = new ParameterizedTypeReference<Map<String, String>>() { };
        ResponseEntity<Map<String, String>> responseEntity;
        try {
            responseEntity = caseServerRest.exchange("/" + CASE_API_VERSION + "/case-server/cases",
                    HttpMethod.GET,
                    null,
                    parameterizedTypeReference);

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return responseEntity.getBody();
            } else {
                return null;
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR && e.getMessage() != null) {
                throw new PowsyblException(e.getMessage());
            } else {
                throw e;
            }
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
            ResponseEntity<String> responseEntity = caseServerRest.exchange("/" + CASE_API_VERSION + "/case-server/import-case",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            HttpStatus exceptionStatus = e.getStatusCode();
            if ((exceptionStatus == HttpStatus.CONFLICT || exceptionStatus == HttpStatus.INTERNAL_SERVER_ERROR) && e.getMessage() != null) {
                throw new PowsyblException(e.getMessage());
            } else {
                throw e;
            }
        }
    }

    byte[] getVoltageLevelSvg(UUID networkUuid, String voltageLevelId) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("networkUuid", networkUuid);
        urlParams.put("voltageLevelId", voltageLevelId);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(voltageLevelDiagramServerBaseUri + "/" + VOLTAGE_LEVEL_DIAGRAM_API_VERSION +
                "/voltage-level-diagram-server/svg/{networkUuid}/{voltageLevelId}")
                .uriVariables(urlParams);

        ResponseEntity<byte[]> responseEntity = voltageLevelDiagramServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                byte[].class);
        return responseEntity.getBody();
    }

    NetworkIds persistentStore(String caseName) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("caseName", caseName);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(iidmServerBaseUri + "/" + IIDM_CONVERTER_API_VERSION +
                "/iidm-converter-server/persistent-store/{caseName}").uriVariables(urlParams);
        ResponseEntity<NetworkIds> responseEntity = iidmConverterServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.POST,
                requestEntity,
                NetworkIds.class);
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

    List<LineGraphic> getLinesGraphicsWithPagination(UUID networkUuid, int page, int size) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("networkUuid", networkUuid);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(geoDataServerBaseUri + "/" + GEO_DATA_API_VERSION +
                "/lines-graphics-with-pagination/{networkUuid}")
                .uriVariables(urlParams)
                .queryParam("page", page)
                .queryParam("size", size);

        ParameterizedTypeReference<List<LineGraphic>> parameterizedTypeReference = new ParameterizedTypeReference<List<LineGraphic>>() { };

        ResponseEntity<List<LineGraphic>> responseEntity = geoDataServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                parameterizedTypeReference);

        return responseEntity.getBody();
    }

    List<LineGraphic> getLinesGraphics(UUID networkUuid) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("networkUuid", networkUuid);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(geoDataServerBaseUri + "/" + GEO_DATA_API_VERSION +
                "/lines-graphics/{networkUuid}").uriVariables(urlParams);

        ParameterizedTypeReference<List<LineGraphic>> parameterizedTypeReference = new ParameterizedTypeReference<List<LineGraphic>>() { };

        ResponseEntity<List<LineGraphic>> responseEntity = geoDataServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                parameterizedTypeReference);

        return responseEntity.getBody();
    }

    List<SubstationGraphic> getSubstationsGraphicsWithPagination(UUID networkUuid, int page, int size) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("networkUuid", networkUuid);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(geoDataServerBaseUri + "/" + GEO_DATA_API_VERSION +
                "/substations-graphics-with-pagination/{networkUuid}")
                .uriVariables(urlParams)
                .queryParam("page", page)
                .queryParam("size", size);

        ParameterizedTypeReference<List<SubstationGraphic>> parameterizedTypeReference = new ParameterizedTypeReference<List<SubstationGraphic>>() { };

        ResponseEntity<List<SubstationGraphic>> responseEntity = geoDataServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                parameterizedTypeReference);

        return responseEntity.getBody();
    }

    List<SubstationGraphic> getSubstationsGraphics(UUID networkUuid) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);

        Map<String, Object> urlParams = new HashMap<>();
        urlParams.put("networkUuid", networkUuid);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(geoDataServerBaseUri + "/" + GEO_DATA_API_VERSION +
                "/substations-graphics/{networkUuid}")
                .uriVariables(urlParams);

        ParameterizedTypeReference<List<SubstationGraphic>> parameterizedTypeReference = new ParameterizedTypeReference<List<SubstationGraphic>>() { };

        ResponseEntity<List<SubstationGraphic>> responseEntity = geoDataServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                parameterizedTypeReference);

        return responseEntity.getBody();
    }

    Boolean caseExists(String caseName) {
        HttpHeaders requestHeaders = new HttpHeaders();
        HttpEntity requestEntity = new HttpEntity(requestHeaders);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(caseServerBaseUri + "/" + CASE_API_VERSION + "/case-server/exists")
                .queryParam(CASE_PARAM, caseName);

        ResponseEntity<Boolean> responseEntity = caseServerRest.exchange(uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                Boolean.class);

        return responseEntity.getBody();
    }

    boolean studyExists(String studyName) {
        return getStudy(studyName) != null;
    }

    void setCaseServerRest(RestTemplate caseServerRest) {
        this.caseServerRest = caseServerRest;
    }

    void setVoltageLevelDiagramServerRest(RestTemplate voltageLevelDiagramServerRest) {
        this.voltageLevelDiagramServerRest = voltageLevelDiagramServerRest;
    }

    void setIidmConverterServerRest(RestTemplate iidmConverterServerRest) {
        this.iidmConverterServerRest = iidmConverterServerRest;
    }

    void setGeoDataServerRest(RestTemplate geoDataServerRest) {
        this.geoDataServerRest = geoDataServerRest;
    }

}
