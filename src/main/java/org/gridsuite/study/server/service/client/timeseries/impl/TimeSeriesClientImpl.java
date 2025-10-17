/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.timeseries.impl;

import com.powsybl.timeseries.TimeSeries;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;


/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class TimeSeriesClientImpl extends AbstractRestClient implements TimeSeriesClient {

    public TimeSeriesClientImpl(RemoteServicesProperties remoteServicesProperties,
                                RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("timeseries-server"), restTemplate);
    }

    @Override
    public List<TimeSeries> getTimeSeriesGroup(UUID groupUuid, List<String> timeSeriesNames) {
        Objects.requireNonNull(groupUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, TIME_SERIES_END_POINT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/{uuid}");
        if (!CollectionUtils.isEmpty(timeSeriesNames)) {
            uriComponentsBuilder.queryParam("timeSeriesNames", timeSeriesNames);
        }
        var uriComponents = uriComponentsBuilder
                .buildAndExpand(groupUuid);

        // call time-series Rest API
        var timeSeriesJson = getRestTemplate().getForObject(uriComponents.toUriString(), String.class);
        if (!StringUtils.isBlank(timeSeriesJson)) {
            // convert json to TimeSeries
            return TimeSeries.parseJson(timeSeriesJson);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public TimeSeriesGroupRest getTimeSeriesGroupMetadata(UUID groupUuid) {
        Objects.requireNonNull(groupUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, TIME_SERIES_END_POINT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/{uuid}/metadata");
        var uriComponents = uriComponentsBuilder
                .buildAndExpand(groupUuid);

        // call time-series Rest API
        return getRestTemplate().getForObject(uriComponents.toUriString(), TimeSeriesGroupRest.class);
    }
}
