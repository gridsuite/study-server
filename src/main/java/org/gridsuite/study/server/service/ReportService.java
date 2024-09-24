/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.Report;
import org.gridsuite.study.server.dto.ReportLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class ReportService {

    private static final String DELIMITER = "/";

    private String reportServerBaseUri;

    private final RestTemplate restTemplate;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    public ReportService(RemoteServicesProperties remoteServicesProperties,
                         RestTemplate restTemplate) {
        this.reportServerBaseUri = remoteServicesProperties.getServiceUri("report-server");
        this.restTemplate = restTemplate;
    }

    public void setReportServerBaseUri(String reportServerBaseUri) {
        this.reportServerBaseUri = reportServerBaseUri;
    }

    private String getReportsServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    public Report getReport(@NonNull UUID id, @NonNull String defaultName, Set<String> severityLevels) {
        var uriBuilder = UriComponentsBuilder.fromPath("{id}")
                .queryParam(QUERY_PARAM_REPORT_DEFAULT_NAME, defaultName)
                .queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        var path = uriBuilder.buildAndExpand(id).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<Report>() {
        }).getBody();
    }

    public void deleteReports(@NonNull List<UUID> reportsUuids) {
        if (reportsUuids.isEmpty()) {
            return;
        }
        var path = UriComponentsBuilder.fromPath("reports").toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<UUID>> httpEntity = new HttpEntity<>(reportsUuids, headers);

        try {
            restTemplate.exchange(this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + path, HttpMethod.DELETE, httpEntity, Void.class);
        } catch (Exception e) {
            LOGGER.error("Error while deleting reports : {}", e.getMessage());
    public List<ReportLog> getReportLogs(@NonNull UUID id, String messageFilter, Set<String> severityLevels) {
        var uriBuilder = UriComponentsBuilder.fromPath("{id}/logs")
                .queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        if (!StringUtil.isBlank(messageFilter)) {
            uriBuilder.queryParam(QUERY_PARAM_MESSAGE_FILTER, messageFilter);
        }
        var path = uriBuilder.buildAndExpand(id).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<ReportLog>>() {
        }).getBody();
    }

        }
    }
}
