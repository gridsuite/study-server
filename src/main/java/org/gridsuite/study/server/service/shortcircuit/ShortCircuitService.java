/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.shortcircuit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ShortCircuitStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.RemoteServicesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.gridsuite.study.server.StudyException.Type.SHORT_CIRCUIT_ANALYSIS_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.SHORT_CIRCUIT_ANALYSIS_RUNNING;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
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
    public ShortCircuitService(RemoteServicesProperties remoteServicesProperties,
                               NetworkModificationTreeService networkModificationTreeService,
                               NetworkService networkStoreService, ObjectMapper objectMapper) {
        this.shortCircuitServerBaseUri = remoteServicesProperties.getServiceUri("shortcircuit-server");
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, String busId, ShortCircuitParameters shortCircuitParameters, String userId) {
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
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString());

        if (!StringUtils.isBlank(busId)) {
            uriComponentsBuilder.queryParam("busId", busId);
        }

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ShortCircuitParameters> httpEntity = new HttpEntity<>(shortCircuitParameters, headers);

        return restTemplate.exchange(shortCircuitServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    private String getShortCircuitAnalysisResultResourcePath(UUID nodeUuid, ShortcircuitAnalysisType type) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid, type);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }
        return UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results" + "/{resultUuid}").buildAndExpand(resultUuidOpt.get()).toUriString();
    }

    private String getShortCircuitAnalysisFaultResultsResourcePath(UUID nodeUuid) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, ShortcircuitAnalysisType.ALL_BUSES);
        if (resultPath == null) {
            return null;
        }
        return UriComponentsBuilder.fromPath(resultPath + "/fault_results/paged").toUriString();
    }

    public String getShortCircuitAnalysisResult(UUID nodeUuid, String mode, ShortcircuitAnalysisType type) {
        // For ONE_BUS results, we always want full results mode
        String overridedMode = type == ShortcircuitAnalysisType.ONE_BUS ? "FULL" : mode;
        String params = "?mode=" + overridedMode;
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
        if (resultPath == null) {
            return null;
        }
        return getShortCircuitAnalysisResource(resultPath + params);
    }

    public String getShortCircuitAnalysisFaultResultsPage(UUID nodeUuid, String mode, Pageable pageable) {
        String params = "?mode=" + mode + "&page=" + pageable.getPageNumber() + "&size=" + pageable.getPageSize();
        for (Sort.Order order : pageable.getSort())
        {
            params += "&sort=" + order.getProperty() + "," + order.getDirection();
        }
        String faultResultsPath = getShortCircuitAnalysisFaultResultsResourcePath(nodeUuid);
        if (faultResultsPath == null) {
            return null;
        }
        return getShortCircuitAnalysisResource(faultResultsPath + params);
    }

    public String getShortCircuitAnalysisStatus(UUID nodeUuid, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
        if (resultPath == null) {
            return null;
        }
        return getShortCircuitAnalysisResource(resultPath + "/status");
    }

    public String getShortCircuitAnalysisResource(String resourcePath) {
        String result;
        try {
            result = restTemplate.getForObject(shortCircuitServerBaseUri + resourcePath, String.class);
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

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid, ShortcircuitAnalysisType.ALL_BUSES);
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
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(shortCircuitServerBaseUri + path, Void.class);
    }

    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    public static ShortCircuitParametersEntity toEntity(ShortCircuitParameters parameters) {
        Objects.requireNonNull(parameters);
        return new ShortCircuitParametersEntity(parameters.isWithLimitViolations(),
                parameters.isWithVoltageResult(),
                parameters.isWithFortescueResult(),
                parameters.isWithFeederResult(),
                parameters.getStudyType(),
                parameters.getMinVoltageDropProportionalThreshold());
    }

    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        return newShortCircuitParameters(entity.getStudyType(), entity.getMinVoltageDropProportionalThreshold(), entity.isWithFeederResult(), entity.isWithLimitViolations(), entity.isWithVoltageResult(), entity.isWithFortescueResult());
    }

    public static ShortCircuitParameters copy(ShortCircuitParameters shortCircuitParameters) {
        return newShortCircuitParameters(shortCircuitParameters.getStudyType(), shortCircuitParameters.getMinVoltageDropProportionalThreshold(), shortCircuitParameters.isWithFeederResult(), shortCircuitParameters.isWithLimitViolations(), shortCircuitParameters.isWithVoltageResult(), shortCircuitParameters.isWithFortescueResult());
    }

    private static ShortCircuitParameters newShortCircuitParameters(StudyType studyType, double minVoltageDropProportionalThreshold, boolean withFeederResult, boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult) {
        ShortCircuitParameters shortCircuitParametersCopy = new ShortCircuitParameters()
                .setStudyType(studyType)
                .setMinVoltageDropProportionalThreshold(minVoltageDropProportionalThreshold)
                .setWithFeederResult(withFeederResult)
                .setWithLimitViolations(withLimitViolations)
                .setWithVoltageResult(withVoltageResult)
                .setWithFortescueResult(withFortescueResult);
        return shortCircuitParametersCopy;
    }

    public static ShortCircuitParameters getDefaultShortCircuitParameters() {
        return newShortCircuitParameters(StudyType.TRANSIENT, 20, true, true, false, false);
    }

    public void setShortCircuitServerBaseUri(String shortCircuitServerBaseUri) {
        this.shortCircuitServerBaseUri = shortCircuitServerBaseUri;
    }

    public void deleteShortCircuitAnalysisResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(shortCircuitServerBaseUri + path);
    }

    public void assertShortCircuitAnalysisNotRunning(UUID nodeUuid) {
        String scs = getShortCircuitAnalysisStatus(nodeUuid, ShortcircuitAnalysisType.ALL_BUSES);
        String oneBusScs = getShortCircuitAnalysisStatus(nodeUuid, ShortcircuitAnalysisType.ONE_BUS);
        if (ShortCircuitStatus.RUNNING.name().equals(scs) || ShortCircuitStatus.RUNNING.name().equals(oneBusScs)) {
            throw new StudyException(SHORT_CIRCUIT_ANALYSIS_RUNNING);
        }
    }
}
