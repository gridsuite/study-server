/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation.impl;

import com.powsybl.timeseries.StoredDoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import com.powsybl.timeseries.TimeSeries;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_SIMULATION_RUNNING;

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
    public UUID runDynamicSimulation(String receiver, UUID networkUuid, String variantId, int startTime, int stopTime, String mappingName) {
        return dynamicSimulationClient.run(receiver, networkUuid, variantId, startTime, stopTime, mappingName);
    }

    @Override
    public List<StoredDoubleTimeSeries> getTimeSeriesResult(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return Collections.emptyList();
        }
        UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(resultUuidOpt.get()); // get timeseries uuid

        // get timeseries data
        List<TimeSeries> timeSeries = timeSeriesClient.getTimeSeriesGroup(timeSeriesUuid);

        // get first element to check type
        if (timeSeries != null &&
                !timeSeries.isEmpty() &&
                !(timeSeries.get(0) instanceof StoredDoubleTimeSeries)) {
            throw new StudyException(StudyException.Type.TIME_SERIES_ILLEGAL_TYPE, "Time series can not be a type: " + timeSeries.get(0).getClass().getSimpleName());
        }

        return (List) timeSeries;
    }

    @Override
    public List<StringTimeSeries> getTimeLineResult(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return Collections.emptyList();
        }
        UUID timeLineUuid = dynamicSimulationClient.getTimeLineResult(resultUuidOpt.get()); // get timeline uuid

        // get timeline data
        List<TimeSeries> timeLines = timeSeriesClient.getTimeSeriesGroup(timeLineUuid);

        // get first element to check type
        if (timeLines != null &&
                !timeLines.isEmpty() &&
                !(timeLines.get(0) instanceof StringTimeSeries)) {
            throw new StudyException(StudyException.Type.TIME_SERIES_ILLEGAL_TYPE, "Time lines can not be a type: " + timeLines.get(0).getClass().getSimpleName());
        }

        return (List) timeLines;
    }

    @Override
    public DynamicSimulationStatus getStatus(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }
        return dynamicSimulationClient.getStatus(resultUuidOpt.get());
    }

    @Override
    public void deleteResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        dynamicSimulationClient.deleteResult(resultUuid);
    }

    @Override
    public void assertDynamicSimulationNotRunning(UUID nodeUuid) {
        DynamicSimulationStatus status = getStatus(nodeUuid);
        if (DynamicSimulationStatus.RUNNING == status) {
            throw new StudyException(DYNAMIC_SIMULATION_RUNNING);
        }
    }

    @Override
    public List<MappingInfos> getMappings(UUID nodeUuid) {
        return dynamicMappingClient.getAllMappings();
    }
}
