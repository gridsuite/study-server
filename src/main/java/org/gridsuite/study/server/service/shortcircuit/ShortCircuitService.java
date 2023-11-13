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
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.RemoteServicesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.addPageableToQueryParams;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitService {

    static final String RESULT_UUID = "resultUuid";

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

    private String getShortCircuitAnalysisResultsPageResourcePath(UUID nodeUuid, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
        if (resultPath == null) {
            return null;
        }
        if (type == ShortcircuitAnalysisType.ALL_BUSES) {
            resultPath += "/fault_results";
        } else if (type == ShortcircuitAnalysisType.ONE_BUS) {
            resultPath += "/feeder_results";
        }
        return resultPath + "/paged";
    }

    public String getShortCircuitAnalysisResult(UUID nodeUuid, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, boolean paged, Pageable pageable) {
        if (paged) {
            return getShortCircuitAnalysisResultsPage(nodeUuid, mode, type, filters, pageable);
        } else {
            return getShortCircuitAnalysisResult(nodeUuid, mode, type);
        }
    }

    public String getShortCircuitAnalysisResult(UUID nodeUuid, FaultResultsMode mode, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
        if (resultPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultPath)
                .queryParam("mode", mode);

        return getShortCircuitAnalysisResource(builder.build().toUri());
    }

    public String getShortCircuitAnalysisResultsPage(UUID nodeUuid, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, Pageable pageable) {
        String resultsPath = getShortCircuitAnalysisResultsPageResourcePath(nodeUuid, type);
        if (resultsPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultsPath)
                .queryParam("mode", mode);

        if (filters != null && !filters.isEmpty()) {
            builder.queryParam("filters", filters);
        }

        addPageableToQueryParams(builder, pageable);

        return getShortCircuitAnalysisResource(builder.build().encode().toUri()); // need to encode because of filter JSON array
    }

    public String getShortCircuitAnalysisStatus(UUID nodeUuid, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
        if (resultPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultPath + "/status");

        return getShortCircuitAnalysisResource(builder.build().toUri());
    }

    public String getShortCircuitAnalysisResource(URI resourcePath) {
        String result;
        try {
            result = restTemplate.getForObject(resourcePath, String.class);
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
                // predefinedParameters default value is ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP
                ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
    }

    public static ShortCircuitParametersEntity toEntity(ShortCircuitParametersInfos shortCircuitParametersInfos) {
        Objects.requireNonNull(shortCircuitParametersInfos);
        ShortCircuitParameters parameters = shortCircuitParametersInfos.getParameters();
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
                shortCircuitParametersInfos.getPredefinedParameters());
    }

    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        boolean isNominal = InitialVoltageProfileMode.NOMINAL.equals(entity.getInitialVoltageProfileMode());
        List<VoltageRange> voltageRanges = isNominal ? NOMINAL_VOLTAGE_RANGES : CEI909_VOLTAGE_RANGES;
        return newShortCircuitParameters(entity.getStudyType(), entity.getMinVoltageDropProportionalThreshold(), entity.isWithFeederResult(), entity.isWithLimitViolations(), entity.isWithVoltageResult(), entity.isWithFortescueResult(), entity.isWithLoads(), entity.isWithShuntCompensators(), entity.isWithVscConverterStations(), entity.isWithNeutralPosition(), entity.getInitialVoltageProfileMode(), voltageRanges);
    }

    public static ShortCircuitParameters copy(ShortCircuitParameters shortCircuitParameters) {
        return newShortCircuitParameters(shortCircuitParameters.getStudyType(), shortCircuitParameters.getMinVoltageDropProportionalThreshold(), shortCircuitParameters.isWithFeederResult(), shortCircuitParameters.isWithLimitViolations(), shortCircuitParameters.isWithVoltageResult(), shortCircuitParameters.isWithFortescueResult(), shortCircuitParameters.isWithLoads(), shortCircuitParameters.isWithShuntCompensators(), shortCircuitParameters.isWithVSCConverterStations(), shortCircuitParameters.isWithNeutralPosition(), shortCircuitParameters.getInitialVoltageProfileMode(), shortCircuitParameters.getVoltageRanges());
    }

    public static ShortCircuitParameters newShortCircuitParameters(StudyType studyType, double minVoltageDropProportionalThreshold, boolean withFeederResult, boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult, boolean withLoads, boolean withShuntCompensators, boolean withVscConverterStations, boolean withNeutralPosition, InitialVoltageProfileMode initialVoltageProfileMode, List<VoltageRange> voltageRanges) {
        return new ShortCircuitParameters()
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
                // the voltageRanges is not taken in account when initialVoltageProfileMode=NOMINAL
                .setVoltageRanges(voltageRanges);
    }

    public static ShortCircuitParameters getDefaultShortCircuitParameters() {
        return newShortCircuitParameters(StudyType.TRANSIENT, 20, true, true, false, false, true, true, true, false, InitialVoltageProfileMode.NOMINAL, null);
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

    private static final List<VoltageRange> NOMINAL_VOLTAGE_RANGES = List.of(
            new VoltageRange(20.0, 20.0, 1),
            new VoltageRange(45.0, 45.0, 1),
            new VoltageRange(63.0, 63.0, 1),
            new VoltageRange(90.0, 90.0, 1),
            new VoltageRange(150.0, 150.0, 1),
            new VoltageRange(225.0, 225.0, 1),
            new VoltageRange(400.0, 400.0, 1)
    );

    private static final List<VoltageRange> CEI909_VOLTAGE_RANGES = List.of(
            new VoltageRange(20.0, 22.0, 1.1),
            new VoltageRange(45.0, 49.5, 1.1),
            new VoltageRange(63.0, 69.3, 1.1),
            new VoltageRange(90.0, 99.0, 1.1),
            new VoltageRange(150.0, 165.0, 1.1),
            new VoltageRange(225.0, 245.0, 1.0),
            new VoltageRange(400.0, 420.0, 1.05)
    );

    public static ShortCircuitParametersInfos toShortCircuitParametersInfo(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        Map<String, List<VoltageRange>> voltageRangesMap = new HashMap<>();
        voltageRangesMap.put(ShortCircuitInitialVoltageProfileMode.NOMINAL.toString(), NOMINAL_VOLTAGE_RANGES);
        voltageRangesMap.put(ShortCircuitInitialVoltageProfileMode.CEI909.toString(), CEI909_VOLTAGE_RANGES);
        return ShortCircuitParametersInfos.builder()
                .predefinedParameters(entity.getPredefinedParameters())
                .parameters(fromEntity(entity))
                .voltageRangesInfo(voltageRangesMap)
                .build();
    }

    public void invalidateShortCircuitStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(shortCircuitServerBaseUri + path, Void.class);
        }
    }

}
