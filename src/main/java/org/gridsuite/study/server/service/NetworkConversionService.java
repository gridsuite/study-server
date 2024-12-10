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
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.dto.ExportNetworkInfos;
import org.gridsuite.study.server.StudyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

@Service
public class NetworkConversionService {

    private final RestTemplate restTemplate;

    private String networkConversionServerBaseUri;

    private final ObjectMapper objectMapper;

    public NetworkConversionService(@Value("${powsybl.services.network-conversion-server.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
            ObjectMapper objectMapper,
            RestTemplate restTemplate) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * if *variantId* is not null, 2 variant will be created from network-conversion-server
     * - one variant for root node - INITIAL_VARIANT
     * - one variant cloned from the previous one for the 1st node - *variantId*
     */
    public void persistNetwork(UUID caseUuid, UUID studyUuid, UUID rootNetworkUuid, String variantId, String userId, UUID importReportUuid, String caseFormat, Map<String, Object> importParameters, CaseImportAction caseImportAction) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(
                        new CaseImportReceiver(studyUuid, rootNetworkUuid, caseUuid, importReportUuid, userId, System.nanoTime(), caseImportAction
                    )),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid)
                .queryParam(QUERY_PARAM_VARIANT_ID, variantId)
                .queryParam(REPORT_UUID, importReportUuid)
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(CASE_FORMAT, caseFormat)
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

    public ExportNetworkInfos exportNetwork(UUID networkUuid, String variantId, String format, String paramatersJson, String fileName) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION
                + "/networks/{networkUuid}/export/{format}");
        if (!variantId.isEmpty()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }

        if (!StringUtils.isEmpty(fileName)) {
            uriComponentsBuilder.queryParam("fileName", fileName);
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
        try {
            restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, null, Void.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NETWORK_NOT_FOUND);
            } else {
                throw handleHttpError(e, STUDY_INDEXATION_FAILED);
            }
        }
    }

    public void setNetworkConversionServerBaseUri(String networkConversionServerBaseUri) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
    }

    public boolean checkStudyIndexationStatus(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/indexed-equipments")
            .buildAndExpand(networkUuid)
            .toUriString();

        ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<>() {
        };
        try {
            return restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.HEAD, null, typeRef).getStatusCode() == HttpStatus.OK;
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NETWORK_NOT_FOUND);
            } else {
                throw handleHttpError(e, STUDY_CHECK_INDEXATION_FAILED);
            }
        }
    }
}
