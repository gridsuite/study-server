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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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

    /**
     * From a sequence of ReportInfos from the deepest node to root, ask report server for reports
     * and pack them by node/modification-group, putting the name of the defining node as the name of the corresponding report.
     */
    public List<ReporterModel> getReporterModels(List<ReportingInfos> uppingReportingInfos) {
        LinkedList<UUID> uppingNeededDefNodeUuids = new LinkedList<>();
        Map<UUID, UUID> groupToDefiningNode = new HashMap<>();
        Map<UUID, ReportingInfos> definingNodesInfosByUuid = new HashMap<>();
        for (ReportingInfos reportingInfo : uppingReportingInfos) {
            uppingNeededDefNodeUuids.add(reportingInfo.getDefiningNodeUuid());

            UUID modificationGroupUuid = reportingInfo.getModificationGroupUuid();
            if (modificationGroupUuid != null) {
                groupToDefiningNode.put(modificationGroupUuid, reportingInfo.getDefiningNodeUuid());
            }

            definingNodesInfosByUuid.put(reportingInfo.getDefiningNodeUuid(), reportingInfo);
        }

        // ask report server reports from build nodes, noticing for each definition node encountered in them
        // the first (in leaf to root order) building node for which the report mentions the definition node.
        Map<UUID, UUID> seenDefToBuildNodeUuuids = new HashMap<>();
        Map<UUID, ReporterModel> seenBuildNodeToRootReporter = new HashMap<>();
        this.fillMapsOfSeenNodes(uppingNeededDefNodeUuids, groupToDefiningNode, definingNodesInfosByUuid,
            seenDefToBuildNodeUuuids, seenBuildNodeToRootReporter);

        // from root to leaf defining nodes, filters in sub reports that where marked (via modification group uuid in task key)
        // as related to the defining node and translate modification group (found) in report to the node name associated.
        List<ReporterModel> res = new ArrayList<>();
        while (!uppingNeededDefNodeUuids.isEmpty()) {
            UUID downingDefNodeUuid = uppingNeededDefNodeUuids.removeLast();
            ReportingInfos info = definingNodesInfosByUuid.get(downingDefNodeUuid);

            ReporterModel mayReporter = seenBuildNodeToRootReporter.get(downingDefNodeUuid);
            boolean wantSubs = mayReporter == null;
            if (wantSubs) {
                UUID buildNodeUuid = seenDefToBuildNodeUuuids.get(downingDefNodeUuid);
                mayReporter = seenBuildNodeToRootReporter.get(buildNodeUuid);
            }

            ReporterModel newReporter = filterAndAdaptReporter(groupToDefiningNode, downingDefNodeUuid, info,
                mayReporter, wantSubs);

            res.add(newReporter);
        }

        return res;
    }

    /**
     * For given defining node uuid, craft a ReportModel extracted from reports that were found by fillMapsOfSeenNodes,
     * giving it the name of the defining node.
     * @param groupToDefiningNode mapping from modification group uuid to defining node uuid. Not mutated.
     * @param downingDefNodeUuid the uuid of the defining node for which to craft the ReportModel
     * @param info the record giving the node for the defining node.
     * @param receivedReporter the reporter from which to craft the new report.
     * @param wantsSubs are sub reporters having no modification group associated to be copied over ?
     * @return a ReportModel with the defining node name as (default, i.e. shown) name.
     */
    private static ReporterModel filterAndAdaptReporter(Map<UUID, UUID> groupToDefiningNode, UUID downingDefNodeUuid,
            ReportingInfos info, ReporterModel receivedReporter, boolean wantsSubs) {

        ReporterModel newReporter = new ReporterModel(receivedReporter.getDefaultName(),
            info.getDefiningNodeName(), receivedReporter.getTaskValues());

        for (ReporterModel subReporter : receivedReporter.getSubReporters()) {
            UUID groupUuid = getGroupUuidFromReporter(subReporter);

            boolean avoids = groupUuid == null ? wantsSubs : !Objects.equals(downingDefNodeUuid, groupToDefiningNode.get(groupUuid));

            if (avoids) {
                continue;
            }

            ReporterModel newSub = new ReporterModel(subReporter.getDefaultName(), subReporter.getDefaultName(), subReporter.getTaskValues());
            newReporter.addSubReporter(newSub);
            subReporter.getReports().forEach(newSub::report);
            subReporter.getSubReporters().forEach(newSub::addSubReporter);
        }

        return newReporter;
    }

    /**
     * Ask report server reports from build nodes, noticing for each definition node encountered in them
     * the first (in leaf to root order) building node for which the report mentions the definition node.
     * @param neededDefiningNodeUuids the uuids of needed definition nodes, in leaf to root order. Not mutated.
     * @param groupToDefiningNode mapping from modification group uuid to defining node uuid. Not Mutated.
     * @param definingNodesInfosByUuid mapping of definition nodes from their uuid. Not mutated.
     * @param seenDefToBuildNodeUuuids built mapping from definition node uuid to the (first) build node uuid.
     * @param seenBuildNodeToRootReporter built mapping from building node uuid to its root reporter.
     */
    private void fillMapsOfSeenNodes(List<UUID> neededDefiningNodeUuids,
            Map<UUID, UUID> groupToDefiningNode,
            Map<UUID, ReportingInfos> definingNodesInfosByUuid,
            Map<UUID, UUID> seenDefToBuildNodeUuuids,
            Map<UUID, ReporterModel> seenBuildNodeToRootReporter) {

        // try each needed definition node as if building node,
        for (UUID buildNodeUuid : neededDefiningNodeUuids) {
            // but if we already have a build node (and a reporter, by construction),
            // bypass (because it will not be considered as a build node afterward in the loop)
            if (seenDefToBuildNodeUuuids.containsKey(buildNodeUuid)) {
                continue;
            }

            ReportingInfos reportingInfo = definingNodesInfosByUuid.get(buildNodeUuid);
            UUID reportUuid = reportingInfo.getReportUuid();

            // getReport asks to silently return empty reportmodel if not found.
            ReporterModel reporter = this.getReport(reportUuid, "useless roundtrip string");
            if (reporter == null) {
                continue;
            }

            // we found an associated reporter for the current build node. Keep that association
            seenBuildNodeToRootReporter.put(buildNodeUuid, reporter);

            // scan sub reporter to extract modification group uuid to find back definition node
            // and keep association for each such defining node to the building node it was found in the report.
            for (ReporterModel subReport : reporter.getSubReporters()) {
                UUID groupUuid = getGroupUuidFromReporter(subReport);

                if (groupUuid != null) {
                    UUID definingNodeUuid = groupToDefiningNode.get(groupUuid);
                    // may be null if sub report happens to exhibit a UUID-like task-key
                    if (definingNodeUuid != null) {
                        // First (downmost) wins. Can happen when an empty ancestor is built afterward
                        seenDefToBuildNodeUuuids.putIfAbsent(definingNodeUuid, buildNodeUuid);
                    }
                }
            }

        }
    }

    /**
     * Extract and convert the task key of a ReportModel to a UUID.
     * @param subReport a report model (found as sub report, but does not really matter)
     * @return the convert UUID from subReport taskKey string, or null if conversion failed.
     */
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
