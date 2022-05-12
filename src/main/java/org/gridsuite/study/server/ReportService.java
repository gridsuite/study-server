/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import static org.gridsuite.study.server.StudyConstants.REPORT_API_VERSION;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelDeserializer;
import com.powsybl.commons.reporter.ReporterModelJsonModule;

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
                         @Value("${backing-services.report-server.base-uri:http://report-server/}") String reportServerBaseUri) {
        this.reportServerBaseUri = reportServerBaseUri;
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

    public ReporterModel getReport(UUID networkUuid) {
        ReporterModel result = null;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            result = restTemplate.exchange(this.getReportServerURI() + networkUuid, HttpMethod.GET, new HttpEntity<>(headers), ReporterModel.class).getBody();
        } catch (HttpClientErrorException e) {
            if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw e;
            }
        } catch (Exception e) {
            throw e;
        }

        return result;
    }

    public void deleteReport(UUID networkUuid) {
        try {
            restTemplate.delete(this.getReportServerURI() + networkUuid);
        } catch (HttpStatusCodeException e)   {
         // Ignore if 404 because report may not exist
            if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw e;
            }
        }
    }
}
