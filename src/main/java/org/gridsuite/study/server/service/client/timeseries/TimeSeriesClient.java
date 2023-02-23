/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.timeseries;

import com.powsybl.timeseries.TimeSeries;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesGroupInfos;
import org.gridsuite.study.server.service.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.TIME_SERIES_API_VERSION;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface TimeSeriesClient extends RestClient {
    String API_VERSION = TIME_SERIES_API_VERSION;
    String DELIMITER = "/";
    String TIME_SERIES_END_POINT = "timeseries-group";

    List<TimeSeries> getTimeSeriesGroup(UUID groupUuid, List<String> timeSeriesNames);

    TimeSeriesGroupInfos getTimeSeriesGroupMetadata(UUID groupUuid);
}
