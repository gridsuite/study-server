/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import com.powsybl.timeseries.TimeSeries;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.gridsuite.study.server.service.common.ComputationParameters;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORTER_ID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_TYPE;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_UUID;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.COMPUTATION_RUNNING;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.TIME_SERIES_BAD_TYPE;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationRestService extends AbstractComputationRestService implements ComputationParameters {

    public static final String API_VERSION = DYNAMIC_SIMULATION_API_VERSION;
    public static final String DYNAMIC_SIMULATION_END_POINT_PARAMETER = "parameters";
    public static final String DYNAMIC_SIMULATION_END_POINT_RUN = "networks";
    public static final String DYNAMIC_SIMULATION_END_POINT_RESULT = "results";
    public static final String DYNAMIC_SIMULATION_END_POINT_RESULT_COUNT = "supervision/results-count";

    private final ObjectMapper objectMapper;
    private final TimeSeriesClient timeSeriesClient;

    public DynamicSimulationRestService(RemoteServicesProperties remoteServicesProperties,
                                        RestTemplate restTemplate,
                                        ObjectMapper objectMapper,
                                        TimeSeriesClient timeSeriesClient) {
        super(remoteServicesProperties.getServiceUri("dynamic-simulation-server"), restTemplate);
        this.objectMapper = objectMapper;
        this.timeSeriesClient = timeSeriesClient;
    }

    // --- Parameters related methods --- //

    public String getProvider(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}/provider")
                .buildAndExpand(parametersUuid)
                .toUriString();

        return getRestTemplate().getForObject(url, String.class);
    }

    private String getParametersWithUuidUrl(UUID parametersUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        return UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();
    }

    public String getParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String url = getParametersWithUuidUrl(parametersUuid);

        return getRestTemplate().getForObject(url, String.class);
    }

    public UUID createParameters(String parameters) {
        Objects.requireNonNull(parameters);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        return getRestTemplate().postForObject(url, httpEntity, UUID.class);
    }

    public UUID createDefaultParameters() {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/default")
                .buildAndExpand()
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        Objects.requireNonNull(parametersUuid);
        Objects.requireNonNull(parametersInfos);

        String url = getParametersWithUuidUrl(parametersUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        getRestTemplate().put(url, httpEntity);
    }

    public UUID duplicateParameters(UUID sourceParameterId) {
        Objects.requireNonNull(sourceParameterId);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}/duplicate")
                .buildAndExpand(sourceParameterId)
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    public void deleteParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String url = getParametersWithUuidUrl(parametersUuid);

        // call dynamic-simulation REST API
        getRestTemplate().delete(url);
    }

    public UUID getDynamicSimulationParametersUuidOrElseCreateDefault(StudyEntity studyEntity) {
        if (studyEntity.getDynamicSimulationParametersUuid() == null) {
            // not supposed to happen because we create it as the study creation
            studyEntity.setDynamicSimulationParametersUuid(createDefaultParameters());
        }
        return studyEntity.getDynamicSimulationParametersUuid();
    }

    /**
     * Run a dynamic simulation from a given study, node UUID and some configured parameters
     * @param nodeUuid node uuid
     * @param rootNetworkUuid root network uuid
     * @param networkUuid network uuid
     * @param variantId variant id
     * @param reportUuid report uuid
     * @param parametersUuid parameters uuid of dynamic simulation
     * @param events list of events to be used in the simulation
     * @param userId id of user
     * @param debug run in debug mode
     * @return the UUID of the dynamic simulation
     */
    public UUID runDynamicSimulation(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid, String variantId,
                                     UUID reportUuid, UUID parametersUuid, List<EventInfos> events, String userId, boolean debug) {

        // create receiver for getting back the notification in rabbitmq
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        ReportInfos reportInfos = new ReportInfos(reportUuid, nodeUuid);

        Objects.requireNonNull(networkUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(endPointUrl + "/{networkUuid}/run");
        if (StringUtils.isNotBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (debug) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, true);
        }
        uriComponentsBuilder
                .queryParam("parametersUuid", parametersUuid)
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid())
                .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.DYNAMIC_SIMULATION.reportKey);
        var uriComponent = uriComponentsBuilder
                .buildAndExpand(networkUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // call dynamic-simulation REST API
        HttpEntity<List<EventInfos>> httpEntity = new HttpEntity<>(events, headers);
        return getRestTemplate().postForObject(uriComponent.toUriString(), httpEntity, UUID.class);
    }

    /**
     * Get list of time-series metadata
     * @param resultUuid a given result UUID
     * @return a list of time-series metadata
     */
    public List<TimeSeriesMetadataInfos> getTimeSeriesMetadataList(UUID resultUuid) {
        List<TimeSeriesMetadataInfos> metadataList = new ArrayList<>();

        if (resultUuid != null) {
            UUID timeSeriesUuid = getTimeSeriesResult(resultUuid); // get timeseries uuid
            if (timeSeriesUuid != null) {
                // get timeseries metadata
                TimeSeriesGroupRest timeSeriesGroupMetadata = timeSeriesClient.getTimeSeriesGroupMetadata(timeSeriesUuid);

                if (timeSeriesGroupMetadata != null &&
                        !CollectionUtils.isEmpty(timeSeriesGroupMetadata.getMetadatas())) {
                    metadataList = timeSeriesGroupMetadata
                            .getMetadatas()
                            .stream()
                            .map(TimeSeriesMetadataInfos::fromRest)
                            .toList();
                }
            }
        }

        return metadataList;
    }

    /**
     * Get a list of curves from a given result UUID
     *
     * @param resultUuid a given result UUID
     * @param timeSeriesNames a given list of time-series names
     * @return a list of curves
     */
    public List<DoubleTimeSeries> getTimeSeriesResult(UUID resultUuid, List<String> timeSeriesNames) {
        List<TimeSeries> timeSeries = new ArrayList<>();

        if (resultUuid != null) {
            UUID timeSeriesUuid = getTimeSeriesResult(resultUuid); // get timeseries uuid
            if (timeSeriesUuid != null) {
                // get timeseries data
                timeSeries = timeSeriesClient.getTimeSeriesGroup(timeSeriesUuid, timeSeriesNames);

                // get first element to check type
                if (!CollectionUtils.isEmpty(timeSeries) &&
                        !(timeSeries.get(0) instanceof DoubleTimeSeries)) {
                    throw new StudyException(TIME_SERIES_BAD_TYPE, "Time series can not be a type: "
                            + timeSeries.get(0).getClass().getSimpleName()
                            + ", expected type: " + DoubleTimeSeries.class.getSimpleName());
                }
            }
        }

        return (List) timeSeries;
    }

    public UUID getTimeSeriesResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl + "/{resultUuid}/timeseries")
                .buildAndExpand(resultUuid);

        return getRestTemplate().getForObject(uriComponents.toUriString(), UUID.class);
    }

    public UUID getTimelineRestResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl + "/{resultUuid}/timeline")
                .buildAndExpand(resultUuid);

        return getRestTemplate().getForObject(uriComponents.toUriString(), UUID.class);
    }

    /**
     * Get timeline from a given result UUID
     *
     * @param resultUuid a given result UUID
     * @return a list of {@link TimelineEventInfos}
     */
    public List<TimelineEventInfos> getTimelineResult(UUID resultUuid) {
        if (resultUuid != null) {
            UUID timelineUuid = getTimelineRestResult(resultUuid); // get timeline uuid
            if (timelineUuid != null) {
                // get timeline data
                List<TimeSeries> timelines = timeSeriesClient.getTimeSeriesGroup(timelineUuid, null);

                // get first element to check type
                if (!CollectionUtils.isEmpty(timelines) &&
                        !(timelines.getFirst() instanceof StringTimeSeries)) {
                    throw new StudyException(TIME_SERIES_BAD_TYPE, "Timelines can not be a type: "
                            + timelines.getFirst().getClass().getSimpleName()
                            + ", expected type: " + StringTimeSeries.class.getSimpleName());
                }

                // convert {@link StringTimeSeries} to {@link TimelineEventInfos}
                // note that each {@link StringTimeSeries} corresponds to an array of {@link TimelineEventInfos}
                return CollectionUtils.emptyIfNull(timelines).stream()
                        .flatMap(series -> Stream.of(((StringTimeSeries) series).toArray()))
                        .map(eventJson -> {
                            try {
                                return objectMapper.readValue(eventJson, TimelineEventInfos.class);
                            } catch (JsonProcessingException e) {
                                throw new IllegalStateException("Error while deserializing timeline event: " + eventJson, e);
                            }
                        }).toList();
            }
        }

        return Collections.emptyList();
    }

    /**
     * Get the current status of the simulation
     * @param resultUuid a given result UUID
     * @return the status of the dynamic simulation
     */
    public DynamicSimulationStatus getStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl + "/{resultUuid}/status")
                .buildAndExpand(resultUuid);

        return getRestTemplate().getForObject(uriComponents.toUriString(), DynamicSimulationStatus.class);
    }

    /**
     * invalidate status of the simulation results
     * @param resultUuids a given list of result UUIDs
     */
    public void invalidateStatus(List<UUID> resultUuids) {
        if (CollectionUtils.isEmpty(resultUuids)) {
            return;
        }

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(endPointUrl + "/invalidate-status");

        uriComponentsBuilder.queryParam("resultUuid", resultUuids);

        var uriComponents = uriComponentsBuilder.build();

        getRestTemplate().put(uriComponents.toUriString(), null);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }

    /**
     * Delete results
     * @param resultUuids a given results UUID
     */
    public void deleteResults(List<UUID> resultUuids) {
        if (CollectionUtils.isEmpty(resultUuids)) {
            return;
        }

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultUuids);
        // call dynamic-simulation REST API
        getRestTemplate().delete(uriComponents.build().toUriString());
    }

    /**
     * Delete all results
     */
    public void deleteAllResults() {
        deleteResults(null);
    }

    /**
     * Get results count
     */
    public Integer getResultsCount() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT_COUNT);
        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl);
        // call dynamic-simulation REST API
        return getRestTemplate().getForObject(uriComponents.toUriString(), Integer.class);
    }

    public void assertDynamicSimulationNotRunning(UUID resultUuid) {
        DynamicSimulationStatus status = getStatus(resultUuid);
        if (DynamicSimulationStatus.RUNNING == status) {
            throw new StudyException(COMPUTATION_RUNNING);
        }
    }

    public String getProviders() {
        String url = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, "providers");
        return getRestTemplate().getForObject(url, String.class);
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        String url = UriComponentsBuilder.fromUriString(resultBaseUrl + "/{resultUuid}/download-debug-file")
                .buildAndExpand(resultUuid)
                .toUriString();
        return getRestTemplate().exchange(url, org.springframework.http.HttpMethod.GET, null, Resource.class);
    }
}
