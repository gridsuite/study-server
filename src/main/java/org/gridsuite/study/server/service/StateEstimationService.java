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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.StateEstimationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyConstants.STATE_ESTIMATION_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.DELETE_COMPUTATION_RESULTS_FAILED;
import static org.gridsuite.study.server.StudyException.Type.STATE_ESTIMATION_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.STATE_ESTIMATION_RUNNING;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class StateEstimationService {

    static final String RESULT_UUID = "resultUuid";

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Setter
    private String stateEstimationServerServerBaseUri;

    private final NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    public StateEstimationService(RemoteServicesProperties remoteServicesProperties,
                                  NetworkModificationTreeService networkModificationTreeService,
                                  ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.stateEstimationServerServerBaseUri = remoteServicesProperties.getServiceUri("state-estimation-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public String getStateEstimationResult(UUID nodeUuid) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.STATE_ESTIMATION);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}");
        String path = pathBuilder.buildAndExpand(resultUuidOpt.get()).toUriString();

        try {
            result = restTemplate.getForObject(stateEstimationServerServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(STATE_ESTIMATION_NOT_FOUND);
            } else {
                throw e;
            }
        }

        return result;
    }

    public UUID runStateEstimation(UUID networkUuid, String variantId, ReportInfos reportInfos, String receiver, String userId) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportInfos.reportUuid().toString())
                .queryParam("reporterId", reportInfos.reporterId())
                .queryParam("reportType", StudyService.ReportType.STATE_ESTIMATION.reportKey);
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

    public void stopStateEstimation(UUID studyUuid, UUID nodeUuid, UUID timePointUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.STATE_ESTIMATION);

        if (resultUuidOpt.isEmpty()) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, timePointUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(stateEstimationServerServerBaseUri + path, Void.class);
    }

    public String getStateEstimationStatus(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.STATE_ESTIMATION);
        if (resultUuidOpt.isEmpty()) {
            return null;
        }
        String status;
        try {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}/status")
                    .buildAndExpand(resultUuidOpt.get()).toUriString();
            status = restTemplate.getForObject(stateEstimationServerServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(STATE_ESTIMATION_NOT_FOUND);
            }
            throw e;
        }
        return status;
    }

    public void deleteStateEstimationResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(stateEstimationServerServerBaseUri + path);
    }

    public void deleteStateEstimationResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results").toUriString();
            restTemplate.delete(stateEstimationServerServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getStateEstimationResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(stateEstimationServerServerBaseUri + path, Integer.class);
    }

    public void assertStateEstimationNotRunning(UUID nodeUuid) {
        String status = getStateEstimationStatus(nodeUuid);
        if (StateEstimationStatus.RUNNING.name().equals(status)) {
            throw new StudyException(STATE_ESTIMATION_RUNNING);
        }
    }
}
