/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.timeseries.impl;

import com.powsybl.timeseries.TimeSeries;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;


/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class TimeSeriesClientImpl extends AbstractRestClient implements TimeSeriesClient {

    @Autowired
    public TimeSeriesClientImpl(@Value("${gridsuite.services.timeseries-server.base-uri:http://timeseries-server/}") String baseUri,
                                RestTemplate restTemplate) {
        super(baseUri, restTemplate);
    }

    @Override
    public List<TimeSeries> getTimeSeriesGroup(UUID groupUuid, List<String> timeSeriesNames) {
        Objects.requireNonNull(groupUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, TIME_SERIES_END_POINT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{uuid}");
        if (timeSeriesNames != null && !timeSeriesNames.isEmpty()) {
            uriComponentsBuilder.queryParam("timeSeriesNames", timeSeriesNames);
        }
        var uriComponents = uriComponentsBuilder
                .buildAndExpand(groupUuid);

        // call time-series Rest API
        var timeSeriesJson = getRestTemplate().getForObject(uriComponents.toUriString(), String.class);
        // convert timeseries to json
        var timeSeriesObj = TimeSeries.parseJson(timeSeriesJson);
        return timeSeriesObj;
    }

    @Override
    public TimeSeriesGroupRest getTimeSeriesGroupMetadata(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, TIME_SERIES_END_POINT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{uuid}/metadata");
        var uriComponents = uriComponentsBuilder
                .buildAndExpand(groupUuid);

        // call time-series Rest API
        var timeSeriesGroupMetadata = getRestTemplate().getForObject(uriComponents.toUriString(), TimeSeriesGroupRest.class);

        return timeSeriesGroupMetadata;
    }
}