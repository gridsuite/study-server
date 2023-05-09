/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.SecurityAnalysisParametersInfos;
import org.gridsuite.study.server.dto.SecurityAnalysisStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.SECURITY_ANALYSIS_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.SECURITY_ANALYSIS_RUNNING;

@Service
public class SecurityAnalysisService {

    static final String RESULT_UUID = "resultUuid";

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private String securityAnalysisServerBaseUri;

    private final NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    public SecurityAnalysisService(@Value("${gridsuite.services.security-analysis-server.base-uri:http://security-analysis-server/}") String securityAnalysisServerBaseUri,
            NetworkModificationTreeService networkModificationTreeService,
            ObjectMapper objectMapper) {
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public String getSecurityAnalysisResult(UUID nodeUuid, List<String> limitTypes) {
        Objects.requireNonNull(limitTypes);
        String result = null;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
                .queryParam("limitType", limitTypes).buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            } else {
                throw e;
            }
        }

        return result;
    }

    public UUID runSecurityAnalysis(UUID networkUuid, UUID reportUuid, UUID nodeUuid, String variantId, String provider, List<String> contingencyListNames, SecurityAnalysisParametersInfos securityAnalysisParameters,
            String receiver) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString());
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.queryParam("contingencyListName", contingencyListNames)
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<SecurityAnalysisParametersInfos> httpEntity = new HttpEntity<>(securityAnalysisParameters, headers);

        return restTemplate
                .exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void stopSecurityAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
    }

    public SecurityAnalysisStatus getSecurityAnalysisStatus(UUID nodeUuid) {
        SecurityAnalysisStatus status;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        try {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
                    .buildAndExpand(resultUuidOpt.get()).toUriString();

            status = restTemplate.getForObject(securityAnalysisServerBaseUri + path, SecurityAnalysisStatus.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }

        return status;
    }

    public void deleteSaResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(securityAnalysisServerBaseUri + path);
    }

    public void invalidateSaStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void setSecurityAnalysisServerBaseUri(String securityAnalysisServerBaseUri) {
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
    }

    public void assertSecurityAnalysisNotRunning(UUID nodeUuid) {
        SecurityAnalysisStatus sas = getSecurityAnalysisStatus(nodeUuid);
        if (sas == SecurityAnalysisStatus.RUNNING) {
            throw new StudyException(SECURITY_ANALYSIS_RUNNING);
        }
    }

}
