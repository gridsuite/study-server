/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.timeseries.impl;

import com.powsybl.timeseries.TimeSeries;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class TimeSeriesClientImpl extends AbstractRestClient implements TimeSeriesClient {

    public TimeSeriesClientImpl(@Value("${backing-services.timeseries-server.base-uri:http://timeseries-server/}") String baseUri) {
        this.baseUri = baseUri;
    }

    @Override
    public List<TimeSeries> getTimeSeriesGroup(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        String url = getBaseUri() + DELIMITER + API_VERSION + DELIMITER + TIME_SERIES_END_POINT + DELIMITER;

        var uriBuilder = UriComponentsBuilder.fromPath(url + "{uuid}")
                .buildAndExpand(groupUuid);

        // call time-series Rest API
        var timeSeriesJson = getRestTemplate().getForObject(uriBuilder.toUriString(), String.class);
        // convert timeseries to json
        var timeSeriesObj = TimeSeries.parseJson(timeSeriesJson);
        return timeSeriesObj;
    }
}