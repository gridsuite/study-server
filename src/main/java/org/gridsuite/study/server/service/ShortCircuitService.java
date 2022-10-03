/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.SHORT_CIRCUIT_ANALYSIS_NOT_FOUND;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class ShortCircuitService {
    private String shortCircuitServerBaseUri;

    @Autowired
    NotificationService notificationService;

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;

    private final ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public ShortCircuitService(
            @Value("${backing-services.shortcircuit-server.base-uri:http://shortcircuit-server/}") String shortCircuitServerBaseUri,
            NetworkModificationTreeService networkModificationTreeService,
            NetworkService networkStoreService, ObjectMapper objectMapper) {
        this.shortCircuitServerBaseUri = shortCircuitServerBaseUri;
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, ShortCircuitParameters shortCircuitParameters, String provider) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);
        UUID reportUuid = getReportUuid(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/networks/{networkUuid}/run")
                .queryParam("reportId", reportUuid.toString())
                .queryParam("reportName", "shortcircuit");
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ShortCircuitParameters> httpEntity = new HttpEntity<>(shortCircuitParameters, headers);

        return restTemplate.exchange(shortCircuitServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getShortCircuitAnalysisResult(UUID nodeUuid) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}")
                .buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(shortCircuitServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SHORT_CIRCUIT_ANALYSIS_NOT_FOUND);
            } else {
                throw e;
            }
        }
        return result;
    }

    public String getShortCircuitAnalysisStatus(UUID nodeUuid) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/status")
                .buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(shortCircuitServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SHORT_CIRCUIT_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public void stopShortCircuitAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSensitivityAnalysisResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam("receiver", receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(shortCircuitServerBaseUri + path, Void.class);
    }

    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        ShortCircuitParameters parameters = new ShortCircuitParameters();
        parameters.setWithLimitViolations(entity.isWithLimitViolations());
        parameters.setWithVoltageMap(entity.isWithVoltageMap());
        parameters.setWithFeederResult(entity.isWithFeederResult());
        parameters.setStudyType(entity.getStudyType());
        parameters.setMinVoltageDropProportionalThreshold(entity.getMinVoltageDropProportionalThreshold());
        return parameters;
    }

    public static ShortCircuitParametersEntity toEntity(ShortCircuitParameters parameters) {
        Objects.requireNonNull(parameters);
        return new ShortCircuitParametersEntity(null,
                parameters.isWithLimitViolations(),
                parameters.isWithVoltageMap(),
                parameters.isWithFeederResult(),
                parameters.getStudyType(),
                parameters.getMinVoltageDropProportionalThreshold()
        );
    }

    public void setShortCircuitServerBaseUri(String shortCircuitServerBaseUri) {
        this.shortCircuitServerBaseUri = shortCircuitServerBaseUri;
    }
}
