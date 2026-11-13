/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.logicalcontrols;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.service.AbstractStateEstimationRestService;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class LogicalControlsRestService extends AbstractStateEstimationRestService {
    private static final String LOGICAL_CONTROLS_URI = DELIMITER + STATE_ESTIMATION_API_VERSION + DELIMITER + LogicalControlsService.LOGICAL_CONTROLS_STR;

    private final ObjectMapper objectMapper;

    @Autowired
    public LogicalControlsRestService(RemoteServicesProperties remoteServicesProperties, ObjectMapper objectMapper, RestClient restClient) {
        super(remoteServicesProperties, restClient);
        this.objectMapper = objectMapper;
    }

    public UUID runLogicalControls(UUID networkUuid, String variantId, UUID parametersUuid, ReportInfos reportInfos, String receiver, String userId) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(LOGICAL_CONTROLS_URI + DELIMITER + "networks/{networkUuid}/run-and-save")
            .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid().toString())
            .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid())
            .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.LOGICAL_CONTROLS.reportKey);
        if (parametersUuid != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_PARAMETERS_UUID, parametersUuid.toString());
        }

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        return restClient.post().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    public void stopLogicalControls(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

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
            .fromPath(LOGICAL_CONTROLS_URI + DELIMITER + "results/{resultUuid}/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restClient.put().uri(baseUri + path)
            .retrieve().toBodilessEntity();
    }

    public String getLogicalControlsStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        String path = UriComponentsBuilder
            .fromPath(LOGICAL_CONTROLS_URI + DELIMITER + "results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
    }

    public void invalidateLogicalControlsStatus(List<UUID> resultUuids) {
        if (!resultUuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(LOGICAL_CONTROLS_URI + DELIMITER + "results/invalidate-status")
                .queryParam("resultUuid", resultUuids).build().toUriString();
            restClient.put().uri(baseUri + path)
                .retrieve().toBodilessEntity();
        }
    }

    public void deleteLogicalControlsResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, LOGICAL_CONTROLS_URI + "/results", restClient, baseUri);
    }

    public void deleteAllLogicalControlsResults() {
        deleteLogicalControlsResults(null);
    }

    public Integer getLogicalControlsResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + DELIMITER + "supervision/logical-controls/results-count").toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(Integer.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }

    public String getLogicalControlsResult(UUID resultUuid, UUID networkUuid, String variantId, String globalFilters) {
        return getStateEstimationServerResult(resultUuid, networkUuid, variantId, globalFilters,
            STATE_ESTIMATION_API_VERSION, LogicalControlsService.LOGICAL_CONTROLS_STR, RESULTS, "{resultUuid}");
    }
}
