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

import org.gridsuite.study.server.dto.ReportingInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class ReportService {

    private static final Logger LOGGER    = LoggerFactory.getLogger(StudyService.class);

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

    public List<ReporterModel> getReporterModels(List<ReportingInfos> reportingInfos) {
        LinkedList<UUID> uppingNeededDefNodeUuids = new LinkedList<>();
        Map<UUID, UUID> groupToDefiningNode = new HashMap<>();
        Map<UUID, ReportingInfos> definingNodesInfosByUuid = new HashMap<>();
        for (ReportingInfos reportingInfo : reportingInfos) {
            uppingNeededDefNodeUuids.add(reportingInfo.getDefiningNodeUuid());

            UUID modificationGroupUuid = reportingInfo.getModificationGroupUuid();
            if (modificationGroupUuid != null) {
                groupToDefiningNode.put(modificationGroupUuid, reportingInfo.getDefiningNodeUuid());
            }

            definingNodesInfosByUuid.put(reportingInfo.getDefiningNodeUuid(), reportingInfo);
        }

        Map<UUID, UUID> seenDefToBuildNodeUuuids = new HashMap<>();
        Map<UUID, ReporterModel> seenBuildNodeToRootReporter = new HashMap<>();

        this.fillMapsOfSeenNodes(uppingNeededDefNodeUuids, groupToDefiningNode, definingNodesInfosByUuid,
            seenDefToBuildNodeUuuids, seenBuildNodeToRootReporter);

        List<ReporterModel> res = new ArrayList<>();

        while (!uppingNeededDefNodeUuids.isEmpty()) {
            UUID downingDefNodeUuid = uppingNeededDefNodeUuids.removeLast();
            ReportingInfos info = definingNodesInfosByUuid.get(downingDefNodeUuid);

            ReporterModel mayReporter = seenBuildNodeToRootReporter.get(downingDefNodeUuid);
            ReporterModel newReporter;
            boolean wantSubs = mayReporter == null;
            if (wantSubs) {
                UUID buildNodeUuid = seenDefToBuildNodeUuuids.get(downingDefNodeUuid);
                mayReporter = seenBuildNodeToRootReporter.get(buildNodeUuid);
            }

            newReporter = filterAndAdaptReporter(groupToDefiningNode, downingDefNodeUuid, info,
                mayReporter, wantSubs);

            if (newReporter != null) {
                res.add(newReporter);
            }
        }

        return res;
    }

    private static ReporterModel filterAndAdaptReporter(Map<UUID, UUID> groupToDefiningNode, UUID downingDefNodeUuid,
            ReportingInfos info, ReporterModel mayReporter, boolean wantsSubs) {

        ReporterModel newReporter = null;

        for (ReporterModel subReporter : mayReporter.getSubReporters()) {
            UUID groupUuid = getGroupUuidFromReporter(subReporter);

            boolean avoids = groupUuid == null ? wantsSubs : !Objects.equals(downingDefNodeUuid, groupToDefiningNode.get(groupUuid));

            if (avoids) {
                continue;
            }

            if (newReporter == null) {
                newReporter = new ReporterModel(mayReporter.getDefaultName(),
                    info.getDefiningNodeName(), mayReporter.getTaskValues());
            }

            ReporterModel newSub = new ReporterModel(subReporter.getDefaultName(), subReporter.getDefaultName(), subReporter.getTaskValues());
            newReporter.addSubReporter(newSub);
            subReporter.getReports().forEach(newSub::report);
            subReporter.getSubReporters().forEach(newSub::addSubReporter);
        }

        return newReporter;
    }

    private void fillMapsOfSeenNodes(List<UUID> neededDefiningNodeUuids,
            Map<UUID, UUID> groupToDefiningNode,
            Map<UUID, ReportingInfos> definingNodesInfosByUuid,
            Map<UUID, UUID> seenDefToBuildNodeUuuids,
            Map<UUID, ReporterModel> seenBuildNodeToRootReporter) {

        for (UUID buildNodeUuid : neededDefiningNodeUuids) {
            if (seenDefToBuildNodeUuuids.containsKey(buildNodeUuid)) {
                continue;
            }

            ReportingInfos reportingInfo = definingNodesInfosByUuid.get(buildNodeUuid);
            UUID reportUuid = reportingInfo.getReportUuid();

            ReporterModel reporter;
            try {
                reporter = this.getReport(reportUuid, "useless roundtrip string");
            } catch (RestClientException ex) {
                LOGGER.warn("while retrieving report", ex);
                continue;
            }

            if (reporter == null) {
                continue;
            }

            seenBuildNodeToRootReporter.put(buildNodeUuid, reporter);

            for (ReporterModel subReport : reporter.getSubReporters()) {
                UUID groupUuid = getGroupUuidFromReporter(subReport);

                if (groupUuid != null) {
                    UUID definingNodeUuid = groupToDefiningNode.get(groupUuid);
                    if (definingNodeUuid == null) {
                        LOGGER.warn("Unexpected group uuid {}", groupUuid);
                    } else {
                        UUID prev = seenDefToBuildNodeUuuids.get(definingNodeUuid);
                        if (prev == null) {
                            seenDefToBuildNodeUuuids.put(definingNodeUuid, buildNodeUuid);
                        } else if (!Objects.equals(prev, buildNodeUuid)) {
                            LOGGER.warn("Already found elsewhere {}", prev);
                        }
                    }
                }
            }

        }
    }

    private static UUID getGroupUuidFromReporter(ReporterModel subReport) {
        String taskKey = subReport.getTaskKey();
        UUID groupUuid;
        try {
            groupUuid = UUID.fromString(taskKey);
        } catch (IllegalArgumentException ex) {
            groupUuid = null;
        }
        return groupUuid;
    }

    public void setReportServerBaseUri(String reportServerBaseUri) {
        this.reportServerBaseUri = reportServerBaseUri;
    }

    private String getReportServerURI() {
        return this.reportServerBaseUri + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    private ReporterModel getReport(@NonNull UUID reportUuid, @NonNull String defaultName) {
        var path = UriComponentsBuilder.fromPath("{reportUuid}")
            .queryParam(QUERY_PARAM_REPORT_DEFAULT_NAME, defaultName)
            .queryParam(QUERY_PARAM_ERROR_ON_REPORT_NOT_FOUND, false)
            .buildAndExpand(reportUuid)
            .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(this.getReportServerURI() + path, HttpMethod.GET, new HttpEntity<>(headers), ReporterModel.class).getBody();
    }

    public void deleteReport(@NonNull UUID reportUuid) {
        var path = UriComponentsBuilder.fromPath("{reportUuid}")
            .queryParam(QUERY_PARAM_ERROR_ON_REPORT_NOT_FOUND, false)
            .buildAndExpand(reportUuid)
            .toUriString();
        restTemplate.delete(this.getReportServerURI() + path);
    }
}
