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
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.VoltageInitStatus;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
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
public class VoltageInitService extends AbstractComputationService {

    static final String RESULT_UUID = "resultUuid";
    static final String PARAMETERS_URI = "/parameters/{parametersUuid}";
    static final String THE_NODE = "The node ";

    private String voltageInitServerBaseUri;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    @Autowired
    public VoltageInitService(RemoteServicesProperties remoteServicesProperties,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.voltageInitServerBaseUri = remoteServicesProperties.getServiceUri("voltage-init-server");
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID runVoltageInit(UUID networkUuid, String variantId, UUID parametersUuid, UUID reportUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId, boolean debug) {

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
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

        if (debug) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, true);
        }

        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.POST, new HttpEntity<>(headers), UUID.class).getBody();
    }

    private String getVoltageInitResultOrStatus(UUID resultUuid, String suffix, UUID networkUuid, String variantId, String globalFilters) {
        String result;

        if (resultUuid == null) {
            return null;
        }

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}" + suffix);
        if (!StringUtils.isEmpty(globalFilters)) {
            uriComponentsBuilder.queryParam("globalFilters", URLEncoder.encode(globalFilters, StandardCharsets.UTF_8));
            uriComponentsBuilder.queryParam("networkUuid", networkUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
        }
        String path = uriComponentsBuilder.buildAndExpand(resultUuid).toUriString();
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

    public String getVoltageInitResult(UUID resultUuid, UUID networkUuid, String variantId, String globalFilters) {
        return getVoltageInitResultOrStatus(resultUuid, "", networkUuid, variantId, globalFilters);
    }

    public String getVoltageInitStatus(UUID resultUuid) {
        return getVoltageInitResultOrStatus(resultUuid, "/status", null, null, null);
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

    public UUID createVoltageInitParameters(@Nullable VoltageInitParametersInfos parameters) {
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

    public void updateVoltageInitParameters(UUID parametersUuid, @Nullable VoltageInitParametersInfos parameters) {
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        try {
            return restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_VOLTAGE_INIT_PARAMETERS_FAILED);
        }
    }

    public void deleteVoltageInitParameters(UUID parametersUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid).toUriString();

        restTemplate.delete(voltageInitServerBaseUri + path);
    }

    public void stopVoltageInit(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(userId);

        if (resultUuid == null) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
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

    public void deleteVoltageInitResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + VOLTAGE_INIT_API_VERSION + "/results", restTemplate, voltageInitServerBaseUri);
    }

    public void deleteAllVoltageInitResults() {
        deleteVoltageInitResults(null);
    }

    public Integer getVoltageInitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(voltageInitServerBaseUri + path, Integer.class);
    }

    public void assertVoltageInitNotRunning(UUID resultUuid) {
        String scs = getVoltageInitStatus(resultUuid);
        if (VoltageInitStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(VOLTAGE_INIT_RUNNING);
        }
    }

    public UUID getModificationsGroupUuid(UUID nodeUuid, UUID resultUuid) {
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

    public void resetModificationsGroupUuid(UUID resultUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/modifications-group-uuid")
            .buildAndExpand(resultUuid).toUriString();

        restTemplate.put(voltageInitServerBaseUri + path, Void.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }
}
