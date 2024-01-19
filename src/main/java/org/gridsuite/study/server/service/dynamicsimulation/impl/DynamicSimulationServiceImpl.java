/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation.impl;

import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import com.powsybl.timeseries.TimeSeries;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.*;

import static org.gridsuite.study.server.StudyException.Type.DELETE_COMPUTATION_RESULTS_FAILED;
import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_SIMULATION_RUNNING;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationServiceImpl implements DynamicSimulationService {

    private final DynamicMappingClient dynamicMappingClient;

    private final TimeSeriesClient timeSeriesClient;

    private final DynamicSimulationClient dynamicSimulationClient;

    private final NetworkModificationTreeService networkModificationTreeService;

    public DynamicSimulationServiceImpl(DynamicMappingClient dynamicMappingClient,
                                        TimeSeriesClient timeSeriesClient,
                                        DynamicSimulationClient dynamicSimulationClient,
                                        NetworkModificationTreeService networkModificationTreeService) {
        this.dynamicMappingClient = dynamicMappingClient;
        this.timeSeriesClient = timeSeriesClient;
        this.dynamicSimulationClient = dynamicSimulationClient;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @Override
    public UUID runDynamicSimulation(String provider, String receiver, UUID networkUuid, String variantId, DynamicSimulationParametersInfos parameters, String userId) {
        return dynamicSimulationClient.run(provider, receiver, networkUuid, variantId, parameters, userId);
    }

    @Override
    public List<TimeSeriesMetadataInfos> getTimeSeriesMetadataList(UUID nodeUuid) {
        List<TimeSeriesMetadataInfos> metadataList = new ArrayList<>();

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.DYNAMIC_SIMULATION);

        if (resultUuidOpt.isPresent()) {
            UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(resultUuidOpt.get()); // get timeseries uuid
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
    public List<DoubleTimeSeries> getTimeSeriesResult(UUID nodeUuid, List<String> timeSeriesNames) {
        List<TimeSeries> timeSeries = new ArrayList<>();

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.DYNAMIC_SIMULATION);

        if (resultUuidOpt.isPresent()) {
            UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(resultUuidOpt.get()); // get timeseries uuid
            if (timeSeriesUuid != null) {
                // get timeseries data
                timeSeries = timeSeriesClient.getTimeSeriesGroup(timeSeriesUuid, timeSeriesNames);

                // get first element to check type
                if (!CollectionUtils.isEmpty(timeSeries) &&
                    !(timeSeries.get(0) instanceof DoubleTimeSeries)) {
                    throw new StudyException(StudyException.Type.TIME_SERIES_BAD_TYPE, "Time series can not be a type: "
                       + timeSeries.get(0).getClass().getSimpleName()
                       + ", expected type: " + DoubleTimeSeries.class.getSimpleName());
                }
            }
        }

        return (List) timeSeries;
    }

    @Override
    public List<StringTimeSeries> getTimeLineResult(UUID nodeUuid) {
        List<TimeSeries> timeLines = new ArrayList<>();

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.DYNAMIC_SIMULATION);

        if (resultUuidOpt.isPresent()) {
            UUID timeLineUuid = dynamicSimulationClient.getTimeLineResult(resultUuidOpt.get()); // get timeline uuid
            if (timeLineUuid != null) {
                // get timeline data
                timeLines = timeSeriesClient.getTimeSeriesGroup(timeLineUuid, null);

                // get first element to check type
                if (CollectionUtils.isEmpty(timeLines) &&
                    !(timeLines.get(0) instanceof StringTimeSeries)) {
                    throw new StudyException(StudyException.Type.TIME_SERIES_BAD_TYPE, "Time lines can not be a type: "
                       + timeLines.get(0).getClass().getSimpleName()
                       + ", expected type: " + StringTimeSeries.class.getSimpleName());
                }
            }
        }

        return (List) timeLines;
    }

    @Override
    public DynamicSimulationStatus getStatus(UUID nodeUuid) {
        return networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.DYNAMIC_SIMULATION)
                .map(dynamicSimulationClient::getStatus)
                .orElse(null);
    }

    @Override
    public void invalidateStatus(List<UUID> resultUuids) {

        if (resultUuids.isEmpty()) {
            return;
        }

        dynamicSimulationClient.invalidateStatus(resultUuids);
    }

    @Override
    public void deleteResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        dynamicSimulationClient.deleteResult(resultUuid);
    }

    @Override
    public void deleteResults() {
        try {
            dynamicSimulationClient.deleteResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    @Override
    public Integer getResultsCount() {
        return dynamicSimulationClient.getResultsCount();
    }

    @Override
    public void assertDynamicSimulationNotRunning(UUID nodeUuid) {
        DynamicSimulationStatus status = getStatus(nodeUuid);
        if (DynamicSimulationStatus.RUNNING == status) {
            throw new StudyException(DYNAMIC_SIMULATION_RUNNING);
        }
    }

    @Override
    public List<MappingInfos> getMappings(UUID studyUuid) {
        return dynamicMappingClient.getAllMappings();
    }

    @Override
    public List<ModelInfos> getModels(String mapping) {
        return dynamicMappingClient.getModels(mapping);
    }
}
