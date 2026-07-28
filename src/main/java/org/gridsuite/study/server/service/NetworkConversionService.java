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
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.CompressionType;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.dto.networkexport.NetworkExportReceiver;
import org.gridsuite.study.server.dto.networkexport.NodeExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NETWORK_EXPORT_FAILED;

@Service
public class NetworkConversionService {

    private final RestTemplate restTemplate;
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionService.class);

    @Setter
    @Getter
    private String networkConversionServerBaseUri;

    private final ObjectMapper objectMapper;

    private final StudyImportContextService studyImportContextService;

    public NetworkConversionService(@Value("${powsybl.services.network-conversion-server.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            StudyImportContextService studyImportContextService) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.studyImportContextService = studyImportContextService;
    }

    /**
     * if *variantId* is not null, 2 variant will be created from network-conversion-server
     * - one variant for root node - INITIAL_VARIANT
     * - one variant cloned from the previous one for the 1st node - *variantId*
     */
    public void persistNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String variantId, String userId, UUID importReportUuid, Map<String, Object> importParameters,
            CaseImportAction caseImportAction, org.gridsuite.study.server.dto.studyexport.StudyImportContext importContext) {

        LOGGER.info("persistNetwork: Study {}, Action: {}, Case: {}, HasContext: {}",
                studyUuid, caseImportAction, rootNetworkInfos.getCaseInfos().getCaseUuid(), importContext != null);

        // Store StudyImportContext in cache if provided (for STUDY_IMPORT action)
        Boolean hasImportContext = false;
        if (importContext != null) {
            LOGGER.info("persistNetwork: Storing import context in cache for study {}", studyUuid);
            studyImportContextService.storeImportContext(studyUuid, importContext);
            hasImportContext = true;
            LOGGER.info("persistNetwork: Import context stored successfully for study {}", studyUuid);
        }

        String receiver;
        try {
            CaseImportReceiver caseImportReceiver = new CaseImportReceiver(
                    studyUuid, rootNetworkInfos.getId(), rootNetworkInfos.getCaseInfos().getCaseUuid(),
                    rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), importReportUuid, userId,
                    System.nanoTime(), caseImportAction, hasImportContext);

            receiver = URLEncoder.encode(objectMapper.writeValueAsString(caseImportReceiver), StandardCharsets.UTF_8);
            LOGGER.info("persistNetwork: Created receiver for study {}, receiver size: {} bytes",
                    studyUuid, receiver.length());
        } catch (JsonProcessingException e) {
            LOGGER.error("persistNetwork: Failed to serialize CaseImportReceiver for study {}", studyUuid, e);
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

        LOGGER.info("persistNetwork: Sending case import request to network-conversion-server for study {}", studyUuid);
        LOGGER.debug("persistNetwork: Request path: {}", path);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(importParameters, headers);

        restTemplate.exchange(getNetworkConversionServerBaseUri() + path, HttpMethod.POST, httpEntity,
                Void.class);

        LOGGER.info("persistNetwork: Case import request sent successfully for study {}, action: {}",
                studyUuid, caseImportAction);
    }

    public String getExportFormats() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/export/formats")
            .toUriString();

        ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<>() {
        };

        return restTemplate.exchange(getNetworkConversionServerBaseUri() + path, HttpMethod.GET, null, typeRef).getBody();
    }

    public UUID exportNetwork(UUID networkUuid, UUID studyUuid, String variantId, NodeExportInfos exportInfos, String format, CompressionType compression, String userId, String parametersJson) {

        try {
            var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION
                + "/networks/{networkUuid}/export/{format}");

            if (compression != null) {
                uriComponentsBuilder.queryParam("compression", compression);
            }
            if (!StringUtils.isEmpty(variantId)) {
                uriComponentsBuilder.queryParam("variantId", variantId);
            }

            if (!StringUtils.isEmpty(exportInfos.fileName())) {
                uriComponentsBuilder.queryParam("fileName", exportInfos.fileName());
            }
            String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NetworkExportReceiver(studyUuid, userId)), StandardCharsets.UTF_8);
            uriComponentsBuilder.queryParam(QUERY_PARAM_RECEIVER, receiver);

            String exportInfosStr = URLEncoder.encode(objectMapper.writeValueAsString(exportInfos), StandardCharsets.UTF_8);
            uriComponentsBuilder.queryParam(QUERY_PARAM_EXPORT_INFOS, exportInfosStr);

            String path = uriComponentsBuilder.buildAndExpand(networkUuid, format).toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> requestEntity = new HttpEntity<>(parametersJson, headers);

            return restTemplate.exchange(
                getNetworkConversionServerBaseUri() + path,
                    HttpMethod.POST,
                    requestEntity,
                    UUID.class
            ).getBody();
        } catch (Exception e) {
            throw new StudyException(NETWORK_EXPORT_FAILED, e.getMessage());
        }
    }

    public ResponseEntity<Resource> downloadExportedNetworkFile(UUID exportUuid, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/download-file/{exportUuid}")
                .buildAndExpand(exportUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
            getNetworkConversionServerBaseUri() + path,
                HttpMethod.GET,
                entity,
                Resource.class
        );
    }

    public void reindexStudyNetworkEquipments(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/reindex-all")
            .buildAndExpand(networkUuid)
            .toUriString();
        restTemplate.exchange(getNetworkConversionServerBaseUri() + path, HttpMethod.POST, null, Void.class);
    }

    public boolean checkStudyIndexationStatus(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/indexed-equipments")
            .buildAndExpand(networkUuid)
            .toUriString();

        ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<>() {
        };
        return restTemplate.exchange(getNetworkConversionServerBaseUri() + path, HttpMethod.HEAD, null, typeRef).getStatusCode() == HttpStatus.OK;
    }
}
