/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.StateEstimationStatus;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.COMPUTATION_RUNNING;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class StateEstimationService extends AbstractComputationService {

    static final String RESULT_UUID = "resultUuid";

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Setter
    private String stateEstimationServerServerBaseUri;

    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";

    @Autowired
    public StateEstimationService(RemoteServicesProperties remoteServicesProperties,
                                  ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.stateEstimationServerServerBaseUri = remoteServicesProperties.getServiceUri("state-estimation-server");
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public String getStateEstimationResult(UUID resultUuid) {

        if (resultUuid == null) {
            return null;
        }

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}");
        String path = pathBuilder.buildAndExpand(resultUuid).toUriString();

        return restTemplate.getForObject(stateEstimationServerServerBaseUri + path, String.class);
    }

    public UUID runStateEstimation(UUID networkUuid, String variantId, UUID parametersUuid, ReportInfos reportInfos, String receiver, String userId) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportInfos.reportUuid().toString())
                .queryParam("reporterId", reportInfos.nodeUuid())
                .queryParam("reportType", StudyService.ReportType.STATE_ESTIMATION.reportKey);
        if (parametersUuid != null) {
            uriComponentsBuilder.queryParam("parametersUuid", parametersUuid.toString());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate.exchange(stateEstimationServerServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void stopStateEstimation(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        if (resultUuid == null) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restTemplate.put(stateEstimationServerServerBaseUri + path, Void.class);
    }

    public String getStateEstimationStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();
        return restTemplate.getForObject(stateEstimationServerServerBaseUri + path, String.class);
    }

    public void deleteStateEstimationResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + STATE_ESTIMATION_API_VERSION + "/results", restTemplate, stateEstimationServerServerBaseUri);
    }

    public void deleteAllStateEstimationResults() {
        deleteStateEstimationResults(null);
    }

    public Integer getStateEstimationResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(stateEstimationServerServerBaseUri + path, Integer.class);
    }

    public void assertStateEstimationNotRunning(UUID resultUuid) {
        String status = getStateEstimationStatus(resultUuid);
        if (StateEstimationStatus.RUNNING.name().equals(status)) {
            throw new StudyException(COMPUTATION_RUNNING);
        }
    }

    public UUID getStateEstimationParametersUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getStateEstimationParametersUuid() == null) {
            studyEntity.setStateEstimationParametersUuid(createDefaultStateEstimationParameters());
        }
        return studyEntity.getStateEstimationParametersUuid();
    }

    public UUID createDefaultStateEstimationParameters() {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/parameters/default")
            .buildAndExpand()
            .toUriString();

        return restTemplate.exchange(stateEstimationServerServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
    }

    public UUID createStateEstimationParameters(String parameters) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/parameters")
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        return restTemplate.exchange(stateEstimationServerServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void updateStateEstimationParameters(UUID parametersUuid, @Nullable String parameters) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/parameters/{uuid}");
        String path = uriBuilder.buildAndExpand(parametersUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        restTemplate.put(stateEstimationServerServerBaseUri + path, httpEntity);
    }

    public String getStateEstimationParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String path = UriComponentsBuilder.fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid).toUriString();

        return restTemplate.getForObject(stateEstimationServerServerBaseUri + path, String.class);
    }

    public UUID duplicateStateEstimationParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS)
            .queryParam("duplicateFrom", sourceParametersUuid)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate.exchange(stateEstimationServerServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void deleteStateEstimationParameters(UUID uuid) {
        Objects.requireNonNull(uuid);

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(stateEstimationServerServerBaseUri + path);
    }

    public void invalidateStateEstimationStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/invalidate-status")
                .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(stateEstimationServerServerBaseUri + path, Void.class);
        }
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }
}
