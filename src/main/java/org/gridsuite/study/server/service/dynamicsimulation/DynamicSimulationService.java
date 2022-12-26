/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.powsybl.timeseries.TimeSeries;

import java.util.List;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface DynamicSimulationService {
    /**
     * Run a dynamic simulation from a given network UUID and some configured parameters
     * @param networkUuid
     * @param variantId
     * @param startTime
     * @param stopTime
     * @param mappingName
     * @return the UUID of the dynamic simulation
     */
    UUID runDynamicSimulation(UUID networkUuid, String variantId, int startTime, int stopTime, String mappingName);

    /**
     * Get a list of curves from a given node UUID
     *
     * @param nodeUuid a given node UUID
     * @return a list of curves
     */
    List<TimeSeries> getTimeSeriesResult(UUID nodeUuid);

    /**
     * Get timeline from a given node UUID
     *
     * @param nodeUuid a given node UUID
     * @return a list of timeline (only one element)
     */
    List<TimeSeries> getTimeLineResult(UUID nodeUuid);

    /**
     * Get the current status of the simulation
     * @param nodeUuid a given node UUID
     * @return the status of the dynamic simulation
     */
    String getStatus(UUID nodeUuid);

    /**
     * Delete result uuid
     * @param resultUuid a given result UUID
     */
    void deleteResult(UUID resultUuid);

    void assertDynamicSimulationNotRunning(UUID nodeUuid);
}
