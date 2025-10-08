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
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
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
    public void persistNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String variantId, String userId, UUID importReportUuid, Map<String, Object> importParameters, CaseImportAction caseImportAction) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(
                        new CaseImportReceiver(studyUuid, rootNetworkInfos.getId(), rootNetworkInfos.getCaseInfos().getCaseUuid(), rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), importReportUuid, userId, System.nanoTime(), caseImportAction
                    )), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
            .queryParam(CASE_UUID, rootNetworkInfos.getCaseInfos().getCaseUuid())
            .queryParamIfPresent(QUERY_PARAM_VARIANT_ID, Optional.ofNullable(variantId))
            .queryParam(REPORT_UUID, importReportUuid)
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .queryParam(CASE_FORMAT, rootNetworkInfos.getCaseInfos().getCaseFormat());

        String path = builder
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

    public void exportNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid, String variantId, String format,
                              String parametersJson, String fileName, String userId, HttpServletResponse exportNetworkResponse) {

        try (ServletOutputStream outputStream = exportNetworkResponse.getOutputStream()) {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION
                + "/networks/{networkUuid}/export/{format}");
            if (!StringUtils.isEmpty(variantId)) {
                uriComponentsBuilder.queryParam("variantId", variantId);
            }

            if (!StringUtils.isEmpty(fileName)) {
                uriComponentsBuilder.queryParam("fileName", fileName);
            }
            String receiver = studyUuid + "|" + nodeUuid + "|" + rootNetworkUuid + "|" + userId;
            uriComponentsBuilder.queryParam("receiver", receiver);
            String path = uriComponentsBuilder.buildAndExpand(networkUuid, format)
                .toUriString();

            restTemplate.execute(
                    networkConversionServerBaseUri + path,
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        if (parametersJson != null && !parametersJson.isEmpty()) {
                            StreamUtils.copy(parametersJson, StandardCharsets.UTF_8, request.getBody());
                        }
                    },
                    networkConversionServerResponse -> {
                        exportNetworkResponse.setStatus(HttpStatus.ACCEPTED.value());
                        exportNetworkResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        StreamUtils.copy(networkConversionServerResponse.getBody(), outputStream);
                        return null;
                    }
            );
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, NETWORK_EXPORT_FAILED);
        } catch (IOException e) {
            throw new StudyException(NETWORK_EXPORT_FAILED, e.getMessage());
        }
    }

    public void setNetworkConversionServerBaseUri(String networkConversionServerBaseUri) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
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
