/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.openreac.parameters.input.OpenReacParameters;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.VoltageInitStatus;
import org.gridsuite.study.server.dto.voltageinit.FilterEquipments;
import org.gridsuite.study.server.dto.voltageinit.VoltageInitParametersInfos;
import org.gridsuite.study.server.dto.voltageinit.VoltageInitVoltageLimitsParameterInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.FilterEquipmentsEmbeddable;
import org.gridsuite.study.server.repository.VoltageInitParametersEntity;
import org.gridsuite.study.server.repository.VoltageInitParametersVoltageLimitsEntity;
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
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;

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
    public VoltageInitService(
            @Value("${gridsuite.services.voltage-init-server.base-uri:http://voltage-init-server/}") String voltageInitServerBaseUri,
            NetworkModificationTreeService networkModificationTreeService, ObjectMapper objectMapper) {
        this.voltageInitServerBaseUri = voltageInitServerBaseUri;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public UUID runVoltageInit(UUID networkUuid, String variantId, OpenReacParameters openReacParameters, UUID nodeUuid, String userId) {
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

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OpenReacParameters> httpEntity = new HttpEntity<>(openReacParameters, headers);
        return restTemplate.exchange(voltageInitServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
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

    public void assertVoltageInitNotRunning(UUID nodeUuid) {
        String scs = getVoltageInitStatus(nodeUuid);
        if (VoltageInitStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(VOLTAGE_INIT_RUNNING);
        }
    }

    public static VoltageInitParametersEntity toEntity(VoltageInitParametersInfos parameters) {
        Objects.requireNonNull(parameters);
        List<VoltageInitParametersVoltageLimitsEntity> voltageLimits = new ArrayList<>();
        if (parameters.getVoltageLimits() != null) {
            parameters.getVoltageLimits().stream().forEach(voltageLimit ->
                    voltageLimits.add(new VoltageInitParametersVoltageLimitsEntity(null, voltageLimit.getLowVoltageLimit(), voltageLimit.getHighVoltageLimit(), voltageLimit.getPriority(), FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(voltageLimit.getFilters())))
            );
        }
        List<FilterEquipmentsEmbeddable> constantQGenerators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(parameters.getConstantQGenerators());
        List<FilterEquipmentsEmbeddable> variableTwoWindingsTransformers = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(parameters.getVariableTwoWindingsTransformers());
        List<FilterEquipmentsEmbeddable> variableShuntCompensators = FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(parameters.getVariableShuntCompensators());

        return new VoltageInitParametersEntity(null, voltageLimits, constantQGenerators, variableTwoWindingsTransformers, variableShuntCompensators);
    }

    public static VoltageInitParametersInfos fromEntity(VoltageInitParametersEntity voltageInitParameters) {
        Objects.requireNonNull(voltageInitParameters);
        List<VoltageInitVoltageLimitsParameterInfos> voltageLimits = new ArrayList<>();
        voltageInitParameters.getVoltageLimits().stream().forEach(voltageLimit ->
                voltageLimits.add(new VoltageInitVoltageLimitsParameterInfos(voltageLimit.getPriority(),
                        voltageLimit.getLowVoltageLimit(),
                        voltageLimit.getHighVoltageLimit(),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(voltageLimit.getFilters())))
        );
        List<FilterEquipments> constantQGenerators = FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(voltageInitParameters.getConstantQGenerators());
        List<FilterEquipments> variableTwoWindingsTransformers = FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(voltageInitParameters.getVariableTwoWindingsTransformers());
        List<FilterEquipments> variableShuntCompensators = FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(voltageInitParameters.getVariableShuntCompensators());
        return new VoltageInitParametersInfos(voltageLimits, constantQGenerators, variableTwoWindingsTransformers, variableShuntCompensators);
    }

    public static VoltageInitParametersInfos getDefaultVoltageInitParameters() {
        return new VoltageInitParametersInfos();
    }

    public UUID getModificationsGroupUuid(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getVoltageInitResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, "The node " + nodeUuid + " has no voltage init results");
        }

        UUID modificationsGroupUuid;
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/modifications-group")
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
