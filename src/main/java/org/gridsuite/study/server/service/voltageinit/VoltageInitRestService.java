/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.voltageinit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.VariantInfos;
import org.gridsuite.study.server.dto.voltageinit.ContextInfos;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.gridsuite.study.server.service.common.ComputationParameters;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class VoltageInitRestService extends AbstractComputationRestService implements ComputationParameters {

    static final String RESULT_UUID = "resultUuid";
    static final String PARAMETERS_URI = "/parameters/{parametersUuid}";
    static final String QUERY_PARAM_ROOT_NETWORK_NAME = "rootNetworkName";
    static final String QUERY_PARAM_NODE_NAME = "nodeName";

    private final ObjectMapper objectMapper;

    public VoltageInitRestService(RemoteServicesProperties remoteServicesProperties,
                                  RestClient restClient,
                                  ObjectMapper objectMapper) {
        super(remoteServicesProperties.getServiceUri("voltage-init-server"), restClient);
        this.objectMapper = objectMapper;
    }

    public UUID runVoltageInit(VariantInfos variantInfos, UUID parametersUuid, ReportInfos reportInfos, UUID rootNetworkUuid, String userId, boolean debug, ContextInfos contextInfos) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(reportInfos.nodeUuid(), rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam("reportUuid", reportInfos.reportUuid().toString())
                .queryParam("reporterId", reportInfos.nodeUuid().toString())
                .queryParam("reportType", StudyService.ReportType.VOLTAGE_INITIALIZATION.reportKey);

        if (parametersUuid != null) {
            uriComponentsBuilder.queryParam("parametersUuid", parametersUuid.toString());
        }

        if (!StringUtils.isBlank(variantInfos.getVariantId())) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantInfos.getVariantId());
        }

        if (debug) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, true);
        }

        uriComponentsBuilder.queryParam(QUERY_PARAM_ROOT_NETWORK_NAME, contextInfos.rootNetworkName());
        uriComponentsBuilder.queryParam(QUERY_PARAM_NODE_NAME, contextInfos.nodeName());

        var path = uriComponentsBuilder.buildAndExpand(variantInfos.getNetworkUuid()).toUriString();

        return restClient.post().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    private String getVoltageInitResultOrStatus(UUID resultUuid, String suffix, UUID networkUuid, String variantId, String globalFilters) {

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
        return restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
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
        return restClient.get().uri(baseUri + path)
            .retrieve().body(VoltageInitParametersInfos.class);
    }

    public UUID createVoltageInitParameters(@Nullable VoltageInitParametersInfos parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/parameters")
                .buildAndExpand()
                .toUriString();

        var requestSpec = restClient.post().uri(baseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (parameters != null) {
            requestSpec.body(parameters);
        }
        return requestSpec.retrieve().body(UUID.class);
    }

    @Override
    public UUID createDefaultParameters() {
        return createVoltageInitParameters(null);
    }

    public void updateVoltageInitParameters(UUID parametersUuid, @Nullable VoltageInitParametersInfos parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(parametersUuid)
                .toUriString();

        var requestSpec = restClient.put().uri(baseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (parameters != null) {
            requestSpec.body(parameters);
        }
        requestSpec.retrieve().toEntity(UUID.class);
    }

    @Override
    public UUID duplicateParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS + "/{uuid}/duplicate")
                .buildAndExpand(sourceParametersUuid)
                .toUriString();

        return restClient.post().uri(baseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    @Override
    public void deleteParameters(UUID parametersUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid).toUriString();

        restClient.delete().uri(baseUri + path)
            .retrieve().toBodilessEntity();
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

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restClient.put().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    public void deleteVoltageInitResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + VOLTAGE_INIT_API_VERSION + "/results", restClient, baseUri);
    }

    public void deleteAllVoltageInitResults() {
        deleteVoltageInitResults(null);
    }

    public Integer getVoltageInitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/supervision/results-count").toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(Integer.class);
    }

    public UUID getModificationsGroupUuid(UUID nodeUuid, UUID resultUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/modifications-group-uuid")
            .buildAndExpand(resultUuid).toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(UUID.class);
    }

    public void invalidateVoltageInitStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restClient.put().uri(baseUri + path)
                .body(Void.class)
                .retrieve().toBodilessEntity();
        }
    }

    public void resetModificationsGroupUuid(UUID resultUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/modifications-group-uuid")
            .buildAndExpand(resultUuid).toUriString();

        restClient.put().uri(baseUri + path)
            .body(Void.class)
            .retrieve().toBodilessEntity();
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/download-debug-file")
            .buildAndExpand(resultUuid).toUriString();
        return restClient.get().uri(getBaseUri() + path)
            .retrieve().toEntity(Resource.class);
    }

    public VoltageInitParametersInfos getParameters(UUID parameterUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        return restClient.get().uri(getBaseUri() + path)
            .retrieve().body(VoltageInitParametersInfos.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }
}
