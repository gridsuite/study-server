/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation.impl;

import com.powsybl.timeseries.TimeSeries;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.DynamicSimulationStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_SIMULATION_RUNNING;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationServiceImpl implements DynamicSimulationService {

    private final TimeSeriesClient timeSeriesClient;

    private final DynamicSimulationClient dynamicSimulationClient;

    private final NetworkModificationTreeService networkModificationTreeService;

    public DynamicSimulationServiceImpl(TimeSeriesClient timeSeriesClient,
                                        DynamicSimulationClient dynamicSimulationClient,
                                        NetworkModificationTreeService networkModificationTreeService) {
        this.timeSeriesClient = timeSeriesClient;
        this.dynamicSimulationClient = dynamicSimulationClient;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @Override
    public UUID runDynamicSimulation(UUID networkUuid, String variantId, int startTime, int stopTime, String mappingName) {
        return dynamicSimulationClient.run(networkUuid, variantId, startTime, stopTime, mappingName);
    }

    @Override
    public List<TimeSeries> getTimeSeriesResult(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return Collections.emptyList();
        }
        UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(resultUuidOpt.get()); // get timeseries uuid
        return timeSeriesClient.getTimeSeriesGroup(timeSeriesUuid); // get timeseries data
    }

    @Override
    public List<TimeSeries> getTimeLineResult(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return Collections.emptyList();
        }
        UUID timeLineUuid = dynamicSimulationClient.getTimeLineResult(resultUuidOpt.get()); // get timeline uuid
        return timeSeriesClient.getTimeSeriesGroup(timeLineUuid); // get timeline data
    }

    @Override
    public String getStatus(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }
        return dynamicSimulationClient.getStatus(resultUuidOpt.get());
    }

    @Override
    public void deleteResult(UUID nodeUuid) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return;
        }
        dynamicSimulationClient.deleteResult(resultUuidOpt.get());
    }

    @Override
    public void assertDynamicSimulationNotRunning(UUID nodeUuid) {
        String status = getStatus(nodeUuid);
        if (DynamicSimulationStatus.RUNNING.name().equals(status)) {
            throw new StudyException(DYNAMIC_SIMULATION_RUNNING);
        }
    }
}
