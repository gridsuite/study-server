/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.shortcircuit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import com.powsybl.shortcircuit.VoltageRange;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ShortCircuitCustomParameters;
import org.gridsuite.study.server.dto.ShortCircuitStatus;
import org.gridsuite.study.server.dto.ShortCircuitPredefinedParametersType;
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
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitService {

    public static final String VERSION = "1.2";

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
        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append("?mode=" + mode + "&page=" + pageable.getPageNumber() + "&size=" + pageable.getPageSize());

        for (Sort.Order order : pageable.getSort()) {
            paramsBuilder.append("&sort=" + order.getProperty() + "," + order.getDirection());
        }
        String faultResultsPath = getShortCircuitAnalysisFaultResultsResourcePath(nodeUuid);
        if (faultResultsPath == null) {
            return null;
        }
        return getShortCircuitAnalysisResource(faultResultsPath + paramsBuilder);
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
                parameters.getMinVoltageDropProportionalThreshold(),
                parameters.isWithLoads(),
                parameters.isWithShuntCompensators(),
                parameters.isWithVSCConverterStations(),
                parameters.isWithNeutralPosition(),
                parameters.getInitialVoltageProfileMode(),
                //todo: which value to choose: the same as profile mode?
                ShortCircuitPredefinedParametersType.NOMINAL);
    }

    public static ShortCircuitParametersEntity toEntity(ShortCircuitCustomParameters parameters, ShortCircuitPredefinedParametersType predefinedParameters) {
        Objects.requireNonNull(parameters);
        return new ShortCircuitParametersEntity(parameters.isWithLimitViolations(),
                parameters.isWithVoltageResult(),
                parameters.isWithFortescueResult(),
                parameters.isWithFeederResult(),
                parameters.getStudyType(),
                parameters.getMinVoltageDropProportionalThreshold(),
                parameters.isWithLoads(),
                parameters.isWithShuntCompensators(),
                parameters.isWithVSCConverterStations(),
                parameters.isWithNeutralPosition(),
                parameters.getInitialVoltageProfileMode(),
                predefinedParameters);
    }

    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        return newShortCircuitParameters(entity.getStudyType(), entity.getMinVoltageDropProportionalThreshold(), entity.isWithFeederResult(), entity.isWithLimitViolations(), entity.isWithVoltageResult(), entity.isWithFortescueResult(), entity.isWithLoads(), entity.isWithShuntCompensators(), entity.isWithVscConverterStations(), entity.isWithNeutralPosition(), entity.getInitialVoltageProfileMode());
    }

    private static ShortCircuitParameters newShortCircuitParameters(StudyType studyType, double minVoltageDropProportionalThreshold, boolean withFeederResult, boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult, boolean withLoads, boolean withShuntCompensators, boolean withVscConverterStations, boolean withNeutralPosition, InitialVoltageProfileMode initialVoltageProfileMode) {
        ShortCircuitParameters shortCircuitParametersCopy = new ShortCircuitParameters()
                .setStudyType(studyType)
                .setMinVoltageDropProportionalThreshold(minVoltageDropProportionalThreshold)
                .setWithFeederResult(withFeederResult)
                .setWithLimitViolations(withLimitViolations)
                .setWithVoltageResult(withVoltageResult)
                .setWithFortescueResult(withFortescueResult)
                .setWithLoads(withLoads)
                .setWithShuntCompensators(withShuntCompensators)
                .setWithVSCConverterStations(withVscConverterStations)
                .setWithNeutralPosition(withNeutralPosition)
                .setInitialVoltageProfileMode(initialVoltageProfileMode)
                .setVoltageRanges(getVoltageRanges(initialVoltageProfileMode));
        return shortCircuitParametersCopy;
    }

    public static ShortCircuitParameters copy(ShortCircuitParameters shortCircuitParameters) {
        return newShortCircuitParameters(shortCircuitParameters.getStudyType(), shortCircuitParameters.getMinVoltageDropProportionalThreshold(), shortCircuitParameters.isWithFeederResult(), shortCircuitParameters.isWithLimitViolations(), shortCircuitParameters.isWithVoltageResult(), shortCircuitParameters.isWithFortescueResult(), shortCircuitParameters.isWithLoads(), shortCircuitParameters.isWithShuntCompensators(), shortCircuitParameters.isWithVSCConverterStations(), shortCircuitParameters.isWithNeutralPosition(), shortCircuitParameters.getInitialVoltageProfileMode(), shortCircuitParameters.getVoltageRanges());
    }

    private static ShortCircuitParameters newShortCircuitParameters(StudyType studyType, double minVoltageDropProportionalThreshold, boolean withFeederResult, boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult, boolean withLoads, boolean withShuntCompensators, boolean withVscConverterStations, boolean withNeutralPosition, InitialVoltageProfileMode initialVoltageProfileMode, List<VoltageRange> voltageRanges) {
        ShortCircuitParameters shortCircuitParametersCopy = new ShortCircuitParameters()
                .setStudyType(studyType)
                .setMinVoltageDropProportionalThreshold(minVoltageDropProportionalThreshold)
                .setWithFeederResult(withFeederResult)
                .setWithLimitViolations(withLimitViolations)
                .setWithVoltageResult(withVoltageResult)
                .setWithFortescueResult(withFortescueResult)
                //add fields related to version 1.2
                .setWithLoads(withLoads)
                .setWithShuntCompensators(withShuntCompensators)
                .setWithVSCConverterStations(withVscConverterStations)
                .setWithNeutralPosition(withNeutralPosition)
                .setInitialVoltageProfileMode(initialVoltageProfileMode)
                .setVoltageRanges(voltageRanges);
        return shortCircuitParametersCopy;
    }

    public static ShortCircuitParameters getDefaultShortCircuitParameters() {
        return newShortCircuitParameters(StudyType.TRANSIENT, 20, true, true, false, false, true, true, true, false, InitialVoltageProfileMode.NOMINAL, null);
    }

    public static ShortCircuitCustomParameters getDefaultShortCircuitCustomParameters() {
        return ShortCircuitCustomParameters.builder()
                .studyType(StudyType.TRANSIENT)
                .minVoltageDropProportionalThreshold(20)
                .withFeederResult(true)
                .withLimitViolations(true)
                .withVoltageResult(false)
                .withFortescueResult(false)
                .withLoads(true)
                .withShuntCompensators(true)
                .withVSCConverterStations(true)
                .withNeutralPosition(false)
                .initialVoltageProfileMode(InitialVoltageProfileMode.NOMINAL)
                .predefinedParameters(ShortCircuitPredefinedParametersType.NOMINAL)
                .build();
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

    public void deleteShortCircuitAnalysisResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results")
                .toUriString();
            restTemplate.delete(shortCircuitServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getShortCircuitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(shortCircuitServerBaseUri + path, Integer.class);
    }

    public void assertShortCircuitAnalysisNotRunning(UUID nodeUuid) {
        String scs = getShortCircuitAnalysisStatus(nodeUuid, ShortcircuitAnalysisType.ALL_BUSES);
        String oneBusScs = getShortCircuitAnalysisStatus(nodeUuid, ShortcircuitAnalysisType.ONE_BUS);
        if (ShortCircuitStatus.RUNNING.name().equals(scs) || ShortCircuitStatus.RUNNING.name().equals(oneBusScs)) {
            throw new StudyException(SHORT_CIRCUIT_ANALYSIS_RUNNING);
        }
    }

    public static List<VoltageRange> getVoltageRanges(InitialVoltageProfileMode initialVoltageProfileMode) {
        List<VoltageRange> voltageRanges = new ArrayList<>();
        boolean isConfigured = InitialVoltageProfileMode.CONFIGURED.equals(initialVoltageProfileMode);
        voltageRanges.add(new VoltageRange(20.0, isConfigured ? 22.0 : 20.0, isConfigured ? 1.1 : 1));
        voltageRanges.add(new VoltageRange(45.0, isConfigured ? 49.5 : 45.0, isConfigured ? 1.1 : 1));
        voltageRanges.add(new VoltageRange(63.0, isConfigured ? 69.3 : 63.0, isConfigured ? 1.1 : 1));
        voltageRanges.add(new VoltageRange(90.0, isConfigured ? 99.0 : 90.0, isConfigured ? 1.1 : 1));
        voltageRanges.add(new VoltageRange(150.0, isConfigured ? 165.0 : 150.0, isConfigured ? 1.1 : 1));
        voltageRanges.add(new VoltageRange(225.0, isConfigured ? 245.0 : 225.0, isConfigured ? 1.09 : 1));
        voltageRanges.add(new VoltageRange(400.0, isConfigured ? 420.0 : 400.0, isConfigured ? 1.09 : 1));
        return voltageRanges;
    }

    public static ShortCircuitCustomParameters toCustomParameters(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        ShortCircuitCustomParameters shortCircuitCustomParameters = ShortCircuitCustomParameters.builder()
                .studyType(entity.getStudyType())
                .minVoltageDropProportionalThreshold(entity.getMinVoltageDropProportionalThreshold())
                .withFeederResult(entity.isWithFeederResult())
                .withLimitViolations(entity.isWithLimitViolations())
                .withVoltageResult(entity.isWithVoltageResult())
                .withFortescueResult(entity.isWithFortescueResult())
                .withLoads(entity.isWithLoads())
                .withShuntCompensators(entity.isWithShuntCompensators())
                .withVSCConverterStations(entity.isWithVscConverterStations())
                .withNeutralPosition(entity.isWithNeutralPosition())
                .initialVoltageProfileMode(entity.getInitialVoltageProfileMode())
                .voltageRanges(getVoltageRanges(entity.getInitialVoltageProfileMode()))
                .predefinedParameters(entity.getPredefinedParameters())
                .version(VERSION)
                .build();
        return shortCircuitCustomParameters;
    }

}
