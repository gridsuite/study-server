/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelDeserializer;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import lombok.NonNull;
import org.apache.poi.util.StringUtil;
import org.gridsuite.study.server.RemoteServicesProperties;
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
import java.util.Map;
import java.util.Objects;
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

    @Autowired
    public ReportService(ObjectMapper objectMapper,
                         RemoteServicesProperties remoteServicesProperties,
                         RestTemplate restTemplate) {
        this.reportServerBaseUri = remoteServicesProperties.getServiceUri("report-server");
        ReporterModelJsonModule reporterModelJsonModule = new ReporterModelJsonModule();
        reporterModelJsonModule.setSerializers(null); // FIXME: remove when dicos will be used on the front side
        objectMapper.registerModule(reporterModelJsonModule);
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReporterModelDeserializer.DICTIONARY_VALUE_ID, null)); //FIXME : remove with powsyble core
        this.restTemplate = restTemplate;
    }

    public void setReportServerBaseUri(String reportServerBaseUri) {
        this.reportServerBaseUri = reportServerBaseUri;
    }

    private String getReportsServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    private String getSubReportsServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "subreports" + DELIMITER;
    }

    public ReporterModel getReport(@NonNull UUID id, @NonNull String defaultName, String reportNameFilter, StudyService.ReportNameMatchingType reportNameMatchingType, Set<String> severityLevels) {
        var uriBuilder = UriComponentsBuilder.fromPath("{id}")
                .queryParam(QUERY_PARAM_REPORT_DEFAULT_NAME, defaultName)
                .queryParam(QUERY_PARAM_REPORT_WITH_ELEMENTS, true)
                .queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        if (!StringUtil.isBlank(reportNameFilter)) {
            uriBuilder.queryParam(QUERY_PARAM_REPORT_NAME_FILTER, reportNameFilter);
            uriBuilder.queryParam(QUERY_PARAM_REPORT_NAME_MATCHING_TYPE, reportNameMatchingType);
        }
        return reportServerCall(id, this.getReportsServerURI(), uriBuilder);
    }

    public ReporterModel getSubReport(@NonNull UUID id, Set<String> severityLevels) {
        var uriBuilder = UriComponentsBuilder.fromPath("{id}")
                .queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        return reportServerCall(id, this.getSubReportsServerURI(), uriBuilder);
    }

    private ReporterModel reportServerCall(UUID id, String serverUri, UriComponentsBuilder uriBuilder) {
        var path = uriBuilder.buildAndExpand(id).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<ReporterModel> reporters = restTemplate.exchange(serverUri + path, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<List<ReporterModel>>() {
        }).getBody();
        // TODO : Remove this hack when fix to avoid key collision in hades2 will be done
        ReporterModel reporter = new ReporterModel(id.toString(), id.toString());
        if (reporters != null) {
            reporters.forEach(reporter::addSubReporter);
        }
        return reporter;
    }

    public void deleteReport(@NonNull UUID reportUuid) {
        var path = UriComponentsBuilder.fromPath("{reportUuid}")
            .queryParam(QUERY_PARAM_ERROR_ON_REPORT_NOT_FOUND, false)
            .buildAndExpand(reportUuid)
            .toUriString();
        restTemplate.delete(this.getReportsServerURI() + path);
    }

    public void deleteReportByType(UUID reportUuid, StudyService.ReportType reportType) {
        Objects.requireNonNull(reportUuid);
        Objects.requireNonNull(reportType);

        var path = UriComponentsBuilder.fromPath("{reportUuid}")
                .queryParam(QUERY_PARAM_REPORT_TYPE_FILTER, reportType.reportKey)
                .buildAndExpand(reportUuid)
                .toUriString();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(this.getReportsServerURI() + path, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }

    public void deleteTreeReports(@NonNull Map<UUID, String> treeReportsKeys) {
        var path = UriComponentsBuilder.fromPath("treereports").toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<UUID, String>> httpEntity = new HttpEntity<>(treeReportsKeys, headers);

        restTemplate.exchange(this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + path, HttpMethod.DELETE, httpEntity, Void.class);
    }
}
