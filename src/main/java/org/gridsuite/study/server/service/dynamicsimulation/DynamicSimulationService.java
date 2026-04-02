/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.powsybl.timeseries.DoubleTimeSeries;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;

import java.util.List;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface DynamicSimulationService {

    // --- Parameters related methods --- //

    String getProvider(UUID parametersUuid);

    String getParameters(UUID parametersUuid);

    UUID createParameters(String parameters);

    UUID createDefaultParameters();

    void updateParameters(UUID parametersUuid, String parametersInfos);

    UUID duplicateParameters(UUID sourceParameterId);

    void deleteParameters(UUID parametersUuid);

    UUID getDynamicSimulationParametersUuidOrElseCreateDefault(StudyEntity studyEntity);

    // --- Run computation related methods --- //

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
    UUID runDynamicSimulation(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid, String variantId,
                              UUID reportUuid, UUID parametersUuid, List<EventInfos> events, String userId, boolean debug);

    /**
     * Get a list of curves from a given result UUID
     *
     * @param resultUuid a given result UUID
     * @param timeSeriesNames a given list of time-series names
     * @return a list of curves
     */
    List<DoubleTimeSeries> getTimeSeriesResult(UUID resultUuid, List<String> timeSeriesNames);

    /**
     * Get timeline from a given result UUID
     *
     * @param resultUuid a given result UUID
     * @return a list of {@link TimelineEventInfos}
     */
    List<TimelineEventInfos> getTimelineResult(UUID resultUuid);

    /**
     * Get the current status of the simulation
     * @param resultUuid a given result UUID
     * @return the status of the dynamic simulation
     */
    DynamicSimulationStatus getStatus(UUID resultUuid);

    /**
     * invalidate status of the simulation results
     * @param resultUuids a given list of result UUIDs
     */
    void invalidateStatus(List<UUID> resultUuids);

    /**
     * Delete results
     * @param resultsUuids a given results UUID
     */
    void deleteResults(List<UUID> resultsUuids);

    /**
     * Delete all results
     */
    void deleteAllResults();

    /**
     * Get results count
     */
    Integer getResultsCount();

    /**
     * @param resultUuid a given result UUID
     * @throws StudyException with type DYNAMIC_SIMULATION_RUNNING if this node is in RUNNING status
     */
    void assertDynamicSimulationNotRunning(UUID resultUuid);

    /**
     * Get list of time-series metadata
     * @param resultUuid a given result UUID
     * @return a list of time-series metadata
     */
    List<TimeSeriesMetadataInfos> getTimeSeriesMetadataList(UUID resultUuid);
}
