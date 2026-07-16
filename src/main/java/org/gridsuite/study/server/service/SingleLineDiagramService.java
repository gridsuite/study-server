/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class SingleLineDiagramService {

    private final RestTemplate restTemplate;

    private String singleLineDiagramServerBaseUri;

    public SingleLineDiagramService(@Value("${powsybl.services.single-line-diagram-server.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
                                    RestTemplate restTemplate) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.restTemplate = restTemplate;
    }

    public List<String> getAvailableSvgComponentLibraries() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-component-libraries").toUriString();

        return restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.GET, null,
            new ParameterizedTypeReference<List<String>>() {
            }).getBody();
    }

    public byte[] generateVoltageLevelSvg(UUID networkUuid, String variantId, String voltageLevelId, Map<String, Object> sldRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid, voltageLevelId)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(sldRequestInfos, headers);

        return restTemplate.postForObject(singleLineDiagramServerBaseUri + path, httpEntity, byte[].class);
    }

    public String generateVoltageLevelSvgAndMetadata(UUID networkUuid, String variantId, String voltageLevelId, Map<String, Object> sldRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION
                + "/svg-and-metadata/{networkUuid}/{voltageLevelId}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(sldRequestInfos, headers);
        var path = uriComponentsBuilder.buildAndExpand(networkUuid, voltageLevelId).toUriString();
        return restTemplate.postForObject(singleLineDiagramServerBaseUri + path, httpEntity, String.class);
    }

    public byte[] generateSubstationSvg(UUID networkUuid, String variantId, String substationId, Map<String, Object> sldRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid, substationId).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(sldRequestInfos, headers);
        return restTemplate.postForObject(singleLineDiagramServerBaseUri + path, httpEntity, byte[].class);
    }

    public String generateSubstationSvgAndMetadata(UUID networkUuid, String variantId, String substationId, Map<String, Object> sldRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg-and-metadata/{networkUuid}/{substationId}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(sldRequestInfos, headers);
        return restTemplate.postForEntity(singleLineDiagramServerBaseUri + uriComponentsBuilder.build().toUriString(), httpEntity, String.class, networkUuid, substationId).getBody();
    }

    public String generateNetworkAreaDiagram(UUID networkUuid, String variantId, Map<String, Object> nadRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
            "/network-area-diagram/{networkUuid}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid)
            .toUriString();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(nadRequestInfos, headers);

        return restTemplate.postForObject(singleLineDiagramServerBaseUri + path, request, String.class);
    }

    public void deleteDiagramConfigs(List<UUID> configUuids) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/network-area-diagram/configs")
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<UUID>> httpEntity = new HttpEntity<>(configUuids, headers);

        restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.DELETE, httpEntity, Void.class);
    }

    public void setSingleLineDiagramServerBaseUri(String singleLineDiagramServerBaseUri) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
    }
}
