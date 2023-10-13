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

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public ReportService(
                         ObjectMapper objectMapper,
                         RemoteServicesProperties remoteServicesProperties) {
        this.reportServerBaseUri = remoteServicesProperties.getServiceUri("report-server");
        ReporterModelJsonModule reporterModelJsonModule = new ReporterModelJsonModule();
        reporterModelJsonModule.setSerializers(null); // FIXME: remove when dicos will be used on the front side
        objectMapper.registerModule(reporterModelJsonModule);
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReporterModelDeserializer.DICTIONARY_VALUE_ID, null)); //FIXME : remove with powsyble core
    }

    public void setReportServerBaseUri(String reportServerBaseUri) {
        this.reportServerBaseUri = reportServerBaseUri;
    }

    private String getReportServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    private String getReporterServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reporters" + DELIMITER;
    }

    public ReporterModel getReportReporters(@NonNull UUID reportUuid, @NonNull String defaultName) {
        var uriBuilder = UriComponentsBuilder.fromPath("{reportUuid}/reporters")
                .queryParam(QUERY_PARAM_REPORT_DEFAULT_NAME, defaultName)
                .queryParam(QUERY_PARAM_ERROR_ON_REPORT_NOT_FOUND, false);
        return reportServerCall(reportUuid, this.getReportServerURI(), uriBuilder);
    }

    public ReporterModel getReportElements(@NonNull UUID reportUuid, Set<String> severityLevels, String nameFilter) {
        var uriBuilder = UriComponentsBuilder.fromPath("{reportUuid}/elements")
                .queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        if (nameFilter != null && !nameFilter.isEmpty()) {
            uriBuilder.queryParam(QUERY_PARAM_REPORT_NAME_FILTER, nameFilter);
        }
        return reportServerCall(reportUuid, this.getReportServerURI(), uriBuilder);
    }

    public ReporterModel getReporterElements(@NonNull UUID reporterUuid, Set<String> severityLevels) {
        var uriBuilder = UriComponentsBuilder.fromPath("/{reporterUuid}/elements")
                .queryParam(QUERY_PARAM_REPORT_SEVERITY_LEVEL, severityLevels);
        return reportServerCall(reporterUuid, this.getReporterServerURI(), uriBuilder);
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
        restTemplate.delete(this.getReportServerURI() + path);
    }

    public void deleteTreeReports(@NonNull Map<UUID, String> treeReportsKeys) {
        var path = UriComponentsBuilder.fromPath("treereports").toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<UUID, String>> httpEntity = new HttpEntity<>(treeReportsKeys, headers);

        restTemplate.exchange(this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + path, HttpMethod.DELETE, httpEntity, Void.class);
    }
}
