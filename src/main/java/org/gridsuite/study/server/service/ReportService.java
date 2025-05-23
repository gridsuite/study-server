/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import lombok.NonNull;
import org.apache.poi.util.StringUtil;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.Report;
import org.gridsuite.study.server.dto.ReportPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_MESSAGE_FILTER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_DEFAULT_NAME;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_SEVERITY_LEVEL;
import static org.gridsuite.study.server.StudyConstants.REPORT_API_VERSION;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class ReportService {

    private static final String DELIMITER = "/";

    private String reportServerBaseUri;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

    @Autowired
    public ReportService(RemoteServicesProperties remoteServicesProperties,
                         RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.reportServerBaseUri = remoteServicesProperties.getServiceUri("report-server");
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
        }
    }

    public ReportPage getPagedReportLogs(@NonNull UUID id, String messageFilter, Set<String> severityLevels, boolean paged, Pageable pageable) {
        var uriBuilder = UriComponentsBuilder.fromPath("{id}/logs");

        if (paged) {
            uriBuilder.queryParam("paged", paged);
            uriBuilder.queryParam("page", pageable.getPageNumber());
            uriBuilder.queryParam("size", pageable.getPageSize());
        }

        if (severityLevels != null && !severityLevels.isEmpty()) {
            uriBuilder.queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        }
        if (!StringUtil.isBlank(messageFilter)) {
            uriBuilder.queryParam(QUERY_PARAM_MESSAGE_FILTER, URLEncoder.encode(messageFilter, StandardCharsets.UTF_8));
        }
        var path = uriBuilder.buildAndExpand(id).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<ReportPage>() { }).getBody();
    }

    public UUID duplicateReport(@NonNull UUID id) {
        var path = UriComponentsBuilder.fromPath("{id}/duplicate").buildAndExpand(id).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.POST, new HttpEntity<>(headers), UUID.class).getBody();
        } catch (Exception e) {
            LOGGER.error("Error while duplicating report : {}", e.getMessage());
            return UUID.randomUUID();
        }
    }

    public Set<String> getReportAggregatedSeverities(@NonNull UUID id) {
        var path = UriComponentsBuilder.fromPath("{id}/aggregated-severities").buildAndExpand(id).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<Set<String>>() {
        }).getBody();
    }

    public String getSearchTermMatchesInFilteredLogs(
        @NonNull UUID reportId,
        Set<String> severityLevels,
        String messageFilter,
        @NonNull String searchTerm,
        int pageSize
    ) {
        var uriBuilder = UriComponentsBuilder
                .fromPath("{id}/logs/search")
                .queryParam("searchTerm", searchTerm)
                .queryParam("pageSize", pageSize);

        if (severityLevels != null && !severityLevels.isEmpty()) {
            uriBuilder.queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        }
        if (!StringUtil.isBlank(messageFilter)) {
            uriBuilder.queryParam(QUERY_PARAM_MESSAGE_FILTER, URLEncoder.encode(messageFilter, StandardCharsets.UTF_8));
        }

        var path = uriBuilder.buildAndExpand(reportId).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    public void sendReport(UUID reportUuid, ReportNode reportNode) {
        var path = UriComponentsBuilder.fromPath("{reportUuid}")
            .buildAndExpand(reportUuid)
            .toUriString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reportNode), headers), ReportNode.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("error creating report", error);
        }
    }
}
