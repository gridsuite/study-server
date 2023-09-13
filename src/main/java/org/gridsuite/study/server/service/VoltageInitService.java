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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.VoltageInitStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitService {
    private String voltageInitServerBaseUri;

    @Autowired
    NotificationService notificationService;

    NetworkModificationTreeService networkModificationTreeService;

    private final ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public VoltageInitService(RemoteServicesProperties remoteServicesProperties,
            NetworkModificationTreeService networkModificationTreeService, ObjectMapper objectMapper) {
        this.voltageInitServerBaseUri = remoteServicesProperties.getServiceUri("voltage-init-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public UUID runVoltageInit(UUID networkUuid, String variantId, UUID parametersUuid, UUID nodeUuid, String userId) {
        UUID reportUuid = getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString());

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

    public String getVoltageInitResult(UUID nodeUuid) {
        return getVoltageInitResultOrStatus(nodeUuid, "");
    }

    public String getVoltageInitStatus(UUID nodeUuid) {
        return getVoltageInitResultOrStatus(nodeUuid, "/status");
    }

    public String getVoltageInitResultOrStatus(UUID nodeUuid, String suffix) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getVoltageInitResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}" + suffix)
                .buildAndExpand(resultUuidOpt.get()).toUriString();
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

    public String getVoltageInitParameters(UUID parametersUuid) {
        String parameters;

        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/parameters/{parametersUuid}")
            .buildAndExpand(parametersUuid).toUriString();
        try {
            parameters = restTemplate.getForObject(voltageInitServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(VOLTAGE_INIT_PARAMETERS_NOT_FOUND);
            }
            throw e;
        }
        return parameters;
    }

    public UUID createVoltageInitParameters(String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/parameters")
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        UUID parametersUuid;

        try {
            parametersUuid = restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_VOLTAGE_INIT_PARAMETERS_FAILED);
        }

        return parametersUuid;
    }

    public void updateVoltageInitParameters(UUID parametersUuid, String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/parameters/{parametersUuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.PUT, httpEntity, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_VOLTAGE_INIT_PARAMETERS_FAILED);
        }
    }

    public void stopVoltageInit(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getVoltageInitResultUuid(nodeUuid);
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
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(voltageInitServerBaseUri + path, Void.class);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
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
            throw handleHttpError(e, DELETE_RESULTS_FAILED);
        }
    }

    public Integer getVoltageInitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(voltageInitServerBaseUri + path, Integer.class);
    }

    public void assertVoltageInitNotRunning(UUID nodeUuid) {
        String scs = getVoltageInitStatus(nodeUuid);
        if (VoltageInitStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(VOLTAGE_INIT_RUNNING);
        }
    }

    public UUID getModificationsGroupUuid(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getVoltageInitResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, "The node " + nodeUuid + " has no voltage init results");
        }

        UUID modificationsGroupUuid;
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/modifications-group-uuid")
            .buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            modificationsGroupUuid = restTemplate.getForObject(voltageInitServerBaseUri + path, UUID.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NO_VOLTAGE_INIT_MODIFICATIONS_GROUP_FOR_NODE, "The node " + nodeUuid + " has no voltage init modifications group");
            }
            throw e;
        }
        return modificationsGroupUuid;
    }

}
