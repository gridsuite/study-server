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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.CaseImportReceiver;
import org.gridsuite.study.server.dto.ExportNetworkInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class NetworkConversionService {

    private static final String FIRST_VARIANT_ID = "first_variant_id";

    @Autowired
    private RestTemplate restTemplate;

    private String networkConversionServerBaseUri;

    private final ObjectMapper objectMapper;

    public NetworkConversionService(@Value("${powsybl.services.network-conversion-server.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
            ObjectMapper objectMapper) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.objectMapper = objectMapper;
    }

    public void persistentStore(UUID caseUuid, UUID studyUuid, String userId, UUID importReportUuid, Map<String, Object> importParameters) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(
                        new CaseImportReceiver(studyUuid, caseUuid, importReportUuid, userId, System.nanoTime()
                    )),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid)
                .queryParam(QUERY_PARAM_VARIANT_ID, FIRST_VARIANT_ID)
                .queryParam(REPORT_UUID, importReportUuid)
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(importParameters, headers);

        restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, httpEntity,
                Void.class);
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
