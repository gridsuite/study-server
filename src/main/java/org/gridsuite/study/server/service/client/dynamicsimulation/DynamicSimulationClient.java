/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation;

import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DYNAMIC_SIMULATION_API_VERSION;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface DynamicSimulationClient extends RestClient {
    String API_VERSION = DYNAMIC_SIMULATION_API_VERSION;
    String DELIMITER = "/";
    String DYNAMIC_SIMULATION_END_POINT_RUN = "networks";
    String DYNAMIC_SIMULATION_END_POINT_RESULT = "results";
    String DYNAMIC_SIMULATION_END_POINT_RESULT_COUNT = "supervision/results-count";

    UUID run(String provider, String receiver, UUID networkUuid, String variantId, ReportInfos reportInfos,
             DynamicSimulationParametersInfos parameters, String userId, boolean debug);

    UUID getTimeSeriesResult(UUID resultUuid);

    UUID getTimelineResult(UUID resultUuid);

    DynamicSimulationStatus getStatus(UUID resultUuid);

    void invalidateStatus(List<UUID> resultUuids);

    void deleteResults(List<UUID> resultsUuids);

    void deleteAllResults();

    Integer getResultsCount();
}
