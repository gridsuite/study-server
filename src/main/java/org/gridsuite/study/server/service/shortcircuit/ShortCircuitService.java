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
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationService;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.addPageableToQueryParams;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitService extends AbstractComputationService {

    static final String RESULT_UUID = "resultUuid";

    static final List<VoltageRange> CEI909_VOLTAGE_PROFILE = List.of(
            new VoltageRange(10.0, 199.99, 1.1),
            new VoltageRange(200.0, 299.99, 1.09),
            new VoltageRange(300.0, 500.0, 1.05)
    );

    private String shortCircuitServerBaseUri;

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    @Autowired
    public ShortCircuitService(RemoteServicesProperties remoteServicesProperties,
                               NetworkModificationTreeService networkModificationTreeService,
                               NetworkService networkStoreService,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.shortCircuitServerBaseUri = remoteServicesProperties.getServiceUri("shortcircuit-server");
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.restTemplate = restTemplate;
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
                .queryParam("reporterId", nodeUuid.toString())
                .queryParam("reportType", StringUtils.isBlank(busId) ? StudyService.ReportType.SHORT_CIRCUIT.reportKey :
                        StudyService.ReportType.SHORT_CIRCUIT_ONE_BUS.reportKey);

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
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid,
                type == ShortcircuitAnalysisType.ALL_BUSES ? ComputationType.SHORT_CIRCUIT : ComputationType.SHORT_CIRCUIT_ONE_BUS);

        return resultUuidOpt.map(uuid -> UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results" + "/{resultUuid}").buildAndExpand(uuid).toUriString()).orElse(null);
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

    private String getShortCircuitAnalysisCsvResultResourcePath(UUID nodeUuid, ShortcircuitAnalysisType type) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid,
                type == ShortcircuitAnalysisType.ALL_BUSES ? ComputationType.SHORT_CIRCUIT : ComputationType.SHORT_CIRCUIT_ONE_BUS);
        if (resultUuidOpt.isEmpty()) {
            throw new StudyException(SHORT_CIRCUIT_ANALYSIS_NOT_FOUND);
        }
        String path = DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/csv";
        return UriComponentsBuilder.fromPath(path).buildAndExpand(resultUuidOpt.get()).toUriString();
    }

    public byte[] getShortCircuitAnalysisCsvResultResource(URI resourcePath, String headersCsv) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headersCsv, headers);
        try {
            return restTemplate.exchange(resourcePath, HttpMethod.POST, entity, byte[].class).getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SHORT_CIRCUIT_ANALYSIS_NOT_FOUND);
            } else {
                throw handleHttpError(e, SHORT_CIRCUIT_ANALYSIS_ERROR);
            }
        }
    }

    public byte[] getShortCircuitAnalysisCsvResult(UUID nodeUuid, ShortcircuitAnalysisType type, String headersCsv) {
        String resultPath = getShortCircuitAnalysisCsvResultResourcePath(nodeUuid, type);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultPath);
        return getShortCircuitAnalysisCsvResultResource(builder.build().toUri(), headersCsv);
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

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.SHORT_CIRCUIT);
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

    public static ShortCircuitParametersEntity toEntity(ShortCircuitParameters parameters, ShortCircuitPredefinedConfiguration shortCircuitPredefinedConfiguration) {
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
                shortCircuitPredefinedConfiguration);
    }

    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        List<VoltageRange> voltageRanges = InitialVoltageProfileMode.CONFIGURED.equals(entity.getInitialVoltageProfileMode()) ? CEI909_VOLTAGE_PROFILE : null;
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
        return newShortCircuitParameters(StudyType.TRANSIENT, 20, true, true, false, false, false, false, true, true, InitialVoltageProfileMode.NOMINAL, null);
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

    public static ShortCircuitParametersInfos toShortCircuitParametersInfo(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        return ShortCircuitParametersInfos.builder()
                .predefinedParameters(entity.getPredefinedParameters())
                .parameters(fromEntity(entity))
                .cei909VoltageRanges(CEI909_VOLTAGE_PROFILE)
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

    public List<String> getEnumValues(String enumName, UUID resultUuid) {
        return getEnumValues(enumName, resultUuid, SHORT_CIRCUIT_API_VERSION, shortCircuitServerBaseUri, SHORT_CIRCUIT_ANALYSIS_NOT_FOUND, restTemplate);
    }
}
