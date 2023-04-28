/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ShortCircuitStatus;
import org.gridsuite.study.server.dto.VoltageInitStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;

    private final ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public VoltageInitService(
            @Value("${gridsuite.services.shortcircuit-server.base-uri:http://voltage-init-server/}") String voltageInitServerBaseUri,
            NetworkModificationTreeService networkModificationTreeService,
            NetworkService networkStoreService, ObjectMapper objectMapper) {
        this.voltageInitServerBaseUri = voltageInitServerBaseUri;
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public UUID runVoltageInit(UUID studyUuid, UUID nodeUuid, String userId) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);
        UUID reportUuid = getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/networks/{networkUuid}/run")
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

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

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
//
//    public void stopShortCircuitAnalysis(UUID studyUuid, UUID nodeUuid) {
//        Objects.requireNonNull(studyUuid);
//        Objects.requireNonNull(nodeUuid);
//
//        Optional<UUID> resultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid);
//        if (resultUuidOpt.isEmpty()) {
//            return;
//        }
//
//        String receiver;
//        try {
//            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
//        } catch (JsonProcessingException e) {
//            throw new UncheckedIOException(e);
//        }
//        String path = UriComponentsBuilder
//                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/stop")
//                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();
//
//        restTemplate.put(shortCircuitServerBaseUri + path, Void.class);
//    }
//
    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }
//
    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }
//
//    public static ShortCircuitParametersEntity toEntity(ShortCircuitParameters parameters) {
//        Objects.requireNonNull(parameters);
//        return new ShortCircuitParametersEntity(parameters.isWithLimitViolations(),
//                parameters.isWithVoltageResult(),
//                parameters.isWithFortescueResult(),
//                parameters.isWithFeederResult(),
//                parameters.getStudyType(),
//                parameters.getMinVoltageDropProportionalThreshold());
//    }
//
//    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
//        Objects.requireNonNull(entity);
//        return newShortCircuitParameters(entity.getStudyType(), entity.getMinVoltageDropProportionalThreshold(), entity.isWithFeederResult(), entity.isWithLimitViolations(), entity.isWithVoltageResult(), entity.isWithFortescueResult());
//    }
//
//    public static ShortCircuitParameters copy(ShortCircuitParameters shortCircuitParameters) {
//        return newShortCircuitParameters(shortCircuitParameters.getStudyType(), shortCircuitParameters.getMinVoltageDropProportionalThreshold(), shortCircuitParameters.isWithFeederResult(), shortCircuitParameters.isWithLimitViolations(), shortCircuitParameters.isWithVoltageResult(), shortCircuitParameters.isWithFortescueResult());
//    }
//
//    private static ShortCircuitParameters newShortCircuitParameters(StudyType studyType, double minVoltageDropProportionalThreshold, boolean withFeederResult, boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult) {
//        ShortCircuitParameters shortCircuitParametersCopy = new ShortCircuitParameters()
//                .setStudyType(studyType)
//                .setMinVoltageDropProportionalThreshold(minVoltageDropProportionalThreshold)
//                .setWithFeederResult(withFeederResult)
//                .setWithLimitViolations(withLimitViolations)
//                .setWithVoltageResult(withVoltageResult)
//                .setWithFortescueResult(withFortescueResult);
//        return shortCircuitParametersCopy;
//    }
//
//    public static ShortCircuitParameters getDefaultShortCircuitParameters() {
//        return newShortCircuitParameters(StudyType.TRANSIENT, 20, true, true, false, false);
//    }
//
//    public void setShortCircuitServerBaseUri(String shortCircuitServerBaseUri) {
//        this.shortCircuitServerBaseUri = shortCircuitServerBaseUri;
//    }
//
    public void deleteVoltageInitResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(voltageInitServerBaseUri + path);
    }
//
    public void assertVoltageInitNotRunning(UUID nodeUuid) {
        String scs = getVoltageInitStatus(nodeUuid);
        if (VoltageInitStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(VOLTAGE_INIT_RUNNING);
        }
    }
}
