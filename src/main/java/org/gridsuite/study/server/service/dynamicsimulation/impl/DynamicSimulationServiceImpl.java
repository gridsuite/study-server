/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import com.powsybl.timeseries.TimeSeries;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.COMPUTATION_RUNNING;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.TIME_SERIES_BAD_TYPE;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationServiceImpl implements DynamicSimulationService {

    private final ObjectMapper objectMapper;

    private final TimeSeriesClient timeSeriesClient;

    private final DynamicSimulationClient dynamicSimulationClient;

    public DynamicSimulationServiceImpl(ObjectMapper objectMapper,
                                        TimeSeriesClient timeSeriesClient,
                                        DynamicSimulationClient dynamicSimulationClient) {
        this.objectMapper = objectMapper;
        this.timeSeriesClient = timeSeriesClient;
        this.dynamicSimulationClient = dynamicSimulationClient;
    }

    @Override
    public String getProvider(UUID parametersUuid) {
        return dynamicSimulationClient.getProvider(parametersUuid);
    }

    @Override
    public String getParameters(UUID parametersUuid) {
        return dynamicSimulationClient.getParameters(parametersUuid);
    }

    @Override
    public UUID createParameters(String parameters) {
        return dynamicSimulationClient.createParameters(parameters);
    }

    @Override
    public UUID createDefaultParameters() {
        return dynamicSimulationClient.createDefaultParameters();
    }

    @Override
    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        dynamicSimulationClient.updateParameters(parametersUuid, parametersInfos);
    }

    @Override
    public UUID duplicateParameters(UUID sourceParameterId) {
        return dynamicSimulationClient.duplicateParameters(sourceParameterId);
    }

    @Override
    public void deleteParameters(UUID parametersUuid) {
        dynamicSimulationClient.deleteParameters(parametersUuid);
    }

    @Override
    public UUID getDynamicSimulationParametersUuidOrElseCreateDefault(StudyEntity studyEntity) {
        if (studyEntity.getDynamicSimulationParametersUuid() == null) {
            // not supposed to happen because we create it as the study creation
            studyEntity.setDynamicSimulationParametersUuid(dynamicSimulationClient.createDefaultParameters());
        }
        return studyEntity.getDynamicSimulationParametersUuid();
    }

    @Override
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

        return dynamicSimulationClient.run(receiver, networkUuid, variantId, new ReportInfos(reportUuid, nodeUuid), parametersUuid, events, userId, debug);
    }

    @Override
    public List<TimeSeriesMetadataInfos> getTimeSeriesMetadataList(UUID resultUuid) {
        List<TimeSeriesMetadataInfos> metadataList = new ArrayList<>();

        if (resultUuid != null) {
            UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(resultUuid); // get timeseries uuid
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

    @Override
    public List<DoubleTimeSeries> getTimeSeriesResult(UUID resultUuid, List<String> timeSeriesNames) {
        List<TimeSeries> timeSeries = new ArrayList<>();

        if (resultUuid != null) {
            UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(resultUuid); // get timeseries uuid
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

    @Override
    public List<TimelineEventInfos> getTimelineResult(UUID resultUuid) {
        if (resultUuid != null) {
            UUID timelineUuid = dynamicSimulationClient.getTimelineResult(resultUuid); // get timeline uuid
            if (timelineUuid != null) {
                // get timeline data
                List<TimeSeries> timelines = timeSeriesClient.getTimeSeriesGroup(timelineUuid, null);

                // get first element to check type
                if (!CollectionUtils.isEmpty(timelines) &&
                    !(timelines.get(0) instanceof StringTimeSeries)) {
                    throw new StudyException(TIME_SERIES_BAD_TYPE, "Timelines can not be a type: "
                                                                                       + timelines.get(0).getClass().getSimpleName()
                                                                                       + ", expected type: " + StringTimeSeries.class.getSimpleName());
                }

                // convert {@link StringTimeSeries} to {@link TimelineEventInfos}
                // note that each {@link StringTimeSeries} corresponds to an array of {@link TimelineEventInfos}
                return timelines.stream()
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

    @Override
    public DynamicSimulationStatus getStatus(UUID resultUuid) {
        return resultUuid == null ? null : dynamicSimulationClient.getStatus(resultUuid);
    }

    @Override
    public void invalidateStatus(List<UUID> resultUuids) {
        if (CollectionUtils.isNotEmpty(resultUuids)) {
            dynamicSimulationClient.invalidateStatus(resultUuids);
        }
    }

    @Override
    public void deleteResults(List<UUID> resultUuids) {
        dynamicSimulationClient.deleteResults(resultUuids);
    }

    @Override
    public void deleteAllResults() {
        deleteResults(null);
    }

    @Override
    public Integer getResultsCount() {
        return dynamicSimulationClient.getResultsCount();
    }

    @Override
    public void assertDynamicSimulationNotRunning(UUID resultUuid) {
        DynamicSimulationStatus status = getStatus(resultUuid);
        if (DynamicSimulationStatus.RUNNING == status) {
            throw new StudyException(COMPUTATION_RUNNING);
        }
    }
}
