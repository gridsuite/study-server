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

import static org.gridsuite.study.server.StudyConstants.CASE_UUID;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.NETWORK_CONVERSION_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyConstants.REPORT_UUID;
import java.util.Map;
import java.util.UUID;

import org.gridsuite.study.server.dto.ExportNetworkInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NetworkConversionService {

    static final String FIRST_VARIANT_ID = "first_variant_id";

    @Autowired
    private RestTemplate restTemplate;

    private String networkConversionServerBaseUri;

    public NetworkConversionService(@Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
    }

    public NetworkInfos persistentStore(UUID caseUuid, UUID importReportUuid, Map<String, Object> importParameters) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
            .queryParam(CASE_UUID, caseUuid)
            .queryParam(QUERY_PARAM_VARIANT_ID, FIRST_VARIANT_ID)
            .queryParam(REPORT_UUID, importReportUuid)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(importParameters, headers);

        return restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, httpEntity,
                    NetworkInfos.class).getBody();
    }

    public String getExportFormats() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/export/formats")
            .toUriString();

        ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<>() {
        };

        return restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.GET, null, typeRef).getBody();
    }

    public ExportNetworkInfos exportNetwork(UUID networkUuid, String variantId, String format, String paramatersJson) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION
                + "/networks/{networkUuid}/export/{format}");
        if (!variantId.isEmpty()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        String path = uriComponentsBuilder.buildAndExpand(networkUuid, format)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(paramatersJson, headers);

        ResponseEntity<byte[]> responseEntity = restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST,
            httpEntity, byte[].class);

        byte[] bytes = responseEntity.getBody();
        String filename = responseEntity.getHeaders().getContentDisposition().getFilename();
        return new ExportNetworkInfos(filename, bytes);
    }

    public void reindexStudyNetworkEquipments(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/reindex-all")
            .buildAndExpand(networkUuid)
            .toUriString();

        restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, null, Void.class);
    }

    public void setNetworkConversionServerBaseUri(String networkConversionServerBaseUri) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
    }
}
