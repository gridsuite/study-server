/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.VoltageInitStatus;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitService {

    static final String RESULT_UUID = "resultUuid";
    static final String PARAMETERS_URI = "/parameters/{parametersUuid}";
    static final String THE_NODE = "The node ";

    private String voltageInitServerBaseUri;

    NetworkModificationTreeService networkModificationTreeService;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    @Autowired
    public VoltageInitService(RemoteServicesProperties remoteServicesProperties,
                              NetworkModificationTreeService networkModificationTreeService,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.voltageInitServerBaseUri = remoteServicesProperties.getServiceUri("voltage-init-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID runVoltageInit(UUID networkUuid, String variantId, UUID parametersUuid, UUID reportUuid, UUID nodeUuid, UUID timePointUuid, String userId) {

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, timePointUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString())
                .queryParam("reportType", StudyService.ReportType.VOLTAGE_INITIALIZATION.reportKey);

        if (parametersUuid != null) {
            uriComponentsBuilder.queryParam("parametersUuid", parametersUuid.toString());
        }

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.POST, new HttpEntity<>(headers), UUID.class).getBody();
    }

    private String getVoltageInitResultOrStatus(UUID nodeUuid, UUID timePointUuid, String suffix) {
        String result;
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, timePointUuid, ComputationType.VOLTAGE_INITIALIZATION);

        if (resultUuid == null) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}" + suffix)
            .buildAndExpand(resultUuid).toUriString();

        try {
            result = restTemplate.getForObject(voltageInitServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(VOLTAGE_INIT_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public String getVoltageInitResult(UUID nodeUuid, UUID timePointUuid) {
        return getVoltageInitResultOrStatus(nodeUuid, timePointUuid, "");
    }

    public String getVoltageInitStatus(UUID nodeUuid, UUID timePointUuid) {
        return getVoltageInitResultOrStatus(nodeUuid, timePointUuid, "/status");
    }

    public VoltageInitParametersInfos getVoltageInitParameters(UUID parametersUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid).toUriString();
        try {
            return restTemplate.getForObject(voltageInitServerBaseUri + path, VoltageInitParametersInfos.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(VOLTAGE_INIT_PARAMETERS_NOT_FOUND);
            }
            throw e;
        }
    }

    public UUID createVoltageInitParameters(VoltageInitParametersInfos parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/parameters")
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<VoltageInitParametersInfos> httpEntity = new HttpEntity<>(parameters, headers);

        UUID parametersUuid;

        try {
            parametersUuid = restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_VOLTAGE_INIT_PARAMETERS_FAILED);
        }

        return parametersUuid;
    }

    public void updateVoltageInitParameters(UUID parametersUuid, VoltageInitParametersInfos parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(parametersUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<VoltageInitParametersInfos> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.PUT, httpEntity, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_VOLTAGE_INIT_PARAMETERS_FAILED);
        }
    }

    public UUID duplicateVoltageInitParameters(UUID sourceParametersUuid) {

        Objects.requireNonNull(sourceParametersUuid);

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS)
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand()
                .toUriString();

        UUID parametersUuid;

        try {
            parametersUuid = restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_VOLTAGE_INIT_PARAMETERS_FAILED);
        }

        return parametersUuid;
    }

    public void deleteVoltageInitParameters(UUID parametersUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid).toUriString();

        restTemplate.delete(voltageInitServerBaseUri + path);
    }

    public void stopVoltageInit(UUID studyUuid, UUID nodeUuid, UUID timePointUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(userId);

        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, timePointUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, timePointUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    public void setVoltageInitServerBaseUri(String voltageInitServerBaseUri) {
        this.voltageInitServerBaseUri = voltageInitServerBaseUri;
    }

    public void deleteVoltageInitResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(voltageInitServerBaseUri + path);
    }

    public void deleteVoltageInitResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results")
                .toUriString();
            restTemplate.delete(voltageInitServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getVoltageInitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(voltageInitServerBaseUri + path, Integer.class);
    }

    public void assertVoltageInitNotRunning(UUID nodeUuid, UUID timePointUuid) {
        String scs = getVoltageInitStatus(nodeUuid, timePointUuid);
        if (VoltageInitStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(VOLTAGE_INIT_RUNNING);
        }
    }

    public UUID getModificationsGroupUuid(UUID nodeUuid, UUID timePointUuid) {
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, timePointUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, THE_NODE + nodeUuid + " has no voltage init results");
        }

        UUID modificationsGroupUuid;
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/modifications-group-uuid")
            .buildAndExpand(resultUuid).toUriString();
        try {
            modificationsGroupUuid = restTemplate.getForObject(voltageInitServerBaseUri + path, UUID.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NO_VOLTAGE_INIT_MODIFICATIONS_GROUP_FOR_NODE, THE_NODE + nodeUuid + " has no voltage init modifications group");
            }
            throw e;
        }
        return modificationsGroupUuid;
    }

    public void invalidateVoltageInitStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(voltageInitServerBaseUri + path, Void.class);
        }
    }

    public void resetModificationsGroupUuid(UUID nodeUuid, UUID timePointUuid) {
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, timePointUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, THE_NODE + nodeUuid + " has no voltage init results");
        }
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/modifications-group-uuid")
            .buildAndExpand(resultUuid).toUriString();

        restTemplate.put(voltageInitServerBaseUri + path, Void.class);
    }
}
