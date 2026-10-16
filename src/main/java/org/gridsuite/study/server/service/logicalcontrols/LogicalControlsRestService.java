/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.logicalcontrols;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class LogicalControlsRestService extends AbstractComputationRestService {
    static final String RESULTS = "results";
    private static final String LOGICAL_CONTROLS = "logical-controls";
    private static final String LOGICAL_CONTROLS_URI = DELIMITER + STATE_ESTIMATION_API_VERSION + DELIMITER + LOGICAL_CONTROLS;

    @Autowired
    public LogicalControlsRestService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("state-estimation-server"), restTemplate);
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

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate.exchange(baseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getLogicalControlsStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        String path = UriComponentsBuilder
            .fromPath(LOGICAL_CONTROLS_URI + DELIMITER + "results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();
        return restTemplate.getForObject(baseUri + path, String.class);
    }

    public void deleteLogicalControlsResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, LOGICAL_CONTROLS_URI + "/results", restTemplate, baseUri);
    }

    public void deleteAllLogicalControlsResults() {
        deleteLogicalControlsResults(null);
    }

    public Integer getLogicalControlsResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + DELIMITER + "supervision/logical-controls/results-count").toUriString();
        return restTemplate.getForObject(baseUri + path, Integer.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }

    public String getLogicalControlsResult(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        String path = UriComponentsBuilder
            .fromPath(LOGICAL_CONTROLS_URI + DELIMITER + RESULTS + "/{resultUuid}")
            .buildAndExpand(resultUuid).toUriString();
        return restTemplate.getForObject(baseUri + path, String.class);
    }
}
