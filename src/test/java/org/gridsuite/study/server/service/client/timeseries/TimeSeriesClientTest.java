/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.timeseries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.IrregularTimeSeriesIndex;
import com.powsybl.timeseries.StringTimeSeries;
import com.powsybl.timeseries.TimeSeries;
import com.powsybl.timeseries.TimeSeriesIndex;
import jakarta.validation.constraints.NotNull;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesMetadataRest;
import org.gridsuite.study.server.service.client.AbstractRestClientTest;
import org.gridsuite.study.server.service.client.timeseries.impl.TimeSeriesClientImpl;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient.API_VERSION;
import static org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient.TIME_SERIES_END_POINT;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public class TimeSeriesClientTest extends AbstractRestClientTest {

    public static final String TIME_SERIES_GROUP_UUID = "33333333-0000-0000-0000-000000000000";
    public static final String TIMELINE_GROUP_UUID = "44444444-0000-0000-0000-000000000000";

    public static final String TIME_SERIES_NAME_1 = "NETWORK__BUS____2-BUS____5-1_AC_iSide2";
    public static final String TIME_SERIES_NAME_2 = "NETWORK__BUS____1_TN_Upu_value";
    public static final String TIME_SERIES_NAME_UNKNOWN = "TIME_SERIES_NAME_UNKNOWN";
    public static final String TIMELINE_NAME = "Timeline";

    private final Map<String, List<TimeSeries>> database = new HashMap<>();

    private final TimeSeriesGroupRest timeSeriesGroupMetadata = new TimeSeriesGroupRest();

    private TimeSeriesClient timeSeriesClient;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    RemoteServicesProperties remoteServicesProperties;

    @Override
    @NotNull
    protected Dispatcher getDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
                String path = Objects.requireNonNull(recordedRequest.getPath());
                String endPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, TIME_SERIES_END_POINT);
                String method = recordedRequest.getMethod();

                MockResponse response = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                List<String> pathSegments = ListUtils.emptyIfNull(recordedRequest.getRequestUrl().pathSegments());

                // timeseries-group/{groupUuid}/xxx
                if ("GET".equals(method)
                        && path.matches(endPointUrl + ".*")) {

                    String pathEnding = pathSegments.get(pathSegments.size() - 1);
                    if ("metadata".equals(pathEnding)) {  // timeseries-group/{groupUuid}/metadata
                        String groupUuid = pathSegments.stream().limit(pathSegments.size() - 1).reduce((first, second) -> second).orElse("");
                        try {
                            if (TIME_SERIES_GROUP_UUID.equals(groupUuid)) {
                                String timeSeriesGroupMetadataJson = null;

                                    timeSeriesGroupMetadataJson = objectMapper.writeValueAsString(timeSeriesGroupMetadata);
                                    response = new MockResponse()
                                            .setResponseCode(HttpStatus.OK.value())
                                            .addHeader("Content-Type", "application/json; charset=utf-8")
                                            .setBody(timeSeriesGroupMetadataJson);
                            }
                        } catch (JsonProcessingException e) {
                            return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                        }
                    } else {
                        // take {groupUuid} at the last
                        String groupUuid = emptyIfNull(recordedRequest.getRequestUrl().pathSegments()).stream().reduce((first, second) -> second).orElse("");
                        String timeSeriesNames = recordedRequest.getRequestUrl().queryParameter("timeSeriesNames");
                        getLogger().info("sent timeSeriesNames = " + timeSeriesNames);
                        List<TimeSeries> timeseries;
                        if (timeSeriesNames == null) {
                            timeseries = database.get(groupUuid); // get all timeseries of the same groupUuid
                        } else {
                            // get only precise time series names
                            timeseries = database.get(groupUuid).stream().filter(series -> timeSeriesNames.contains(series.getMetadata().getName())).collect(Collectors.toList());
                        }

                        if (CollectionUtils.isEmpty(timeseries)) {
                            return new MockResponse().setResponseCode(HttpStatus.NO_CONTENT.value());
                        }

                        response = new MockResponse()
                                .setResponseCode(HttpStatus.OK.value())
                                .addHeader("Content-Type", "application/json; charset=utf-8")
                                .setBody(TimeSeries.toJson(timeseries));
                    }
                }
                return response;
            }
        };
    }

    @Override
    public void setup() {
        super.setup();

        // setup fake database
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<TimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble(TIME_SERIES_NAME_1, index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble(TIME_SERIES_NAME_2, index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));

        // timeline
        index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        StringTimeSeries timeline = TimeSeries.createString(TIMELINE_NAME, index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint");

        database.put(TIME_SERIES_GROUP_UUID, timeSeries);
        database.put(TIMELINE_GROUP_UUID, new ArrayList<>(Arrays.asList(timeline)));

        // group metadata for timeseries
        timeSeriesGroupMetadata.setId(UUID.fromString(TIME_SERIES_GROUP_UUID));
        timeSeriesGroupMetadata.setMetadatas(List.of(new TimeSeriesMetadataRest(TIME_SERIES_NAME_1), new TimeSeriesMetadataRest(TIME_SERIES_NAME_2)));

        // config client
        remoteServicesProperties.setServiceUri("timeseries-server", initMockWebServer());
        timeSeriesClient = new TimeSeriesClientImpl(remoteServicesProperties, restTemplate);
    }

    @Test
    public void testGetTimeSeriesGroup() throws JsonProcessingException {
        List<TimeSeries> timeSeries = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), null);
        List<TimeSeries> timelines = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIMELINE_GROUP_UUID), null);

        // --- check result --- //
        // check time series
        // must contain two elements
        getLogger().info("Timeseries size = " + timeSeries.size());
        assertThat(timeSeries).hasSize(2);
        // content must be the same
        String expectedTimeSeriesJson = TimeSeries.toJson(database.get(TIME_SERIES_GROUP_UUID));
        getLogger().info("expectedTimeSeriesJson = " + expectedTimeSeriesJson);
        String resultTimeSeriesJson = TimeSeries.toJson(timeSeries);
        getLogger().info("resultTimeSeriesJson = " + resultTimeSeriesJson);
        assertThat(objectMapper.readTree(resultTimeSeriesJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesJson));

        // check timeline
        // must contain only one element
        getLogger().info("Timeline size = " + timelines.size());
        assertThat(timelines).hasSize(1);
        // content must be the same
        String expectedTimelinesJson = TimeSeries.toJson(database.get(TIMELINE_GROUP_UUID));
        getLogger().info("expectedTimelinesJson = " + expectedTimelinesJson);
        String resultTimelinesJson = TimeSeries.toJson(timelines);
        getLogger().info("resultTimelinesJson = " + resultTimelinesJson);
        assertThat(objectMapper.readTree(resultTimelinesJson)).isEqualTo(objectMapper.readTree(expectedTimelinesJson));
    }

    @Test
    public void testGetTimeSeriesGroupGivenTimeSeriesNames() throws JsonProcessingException {
        List<TimeSeries> timeSeries = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), List.of(TIME_SERIES_NAME_1));
        List<TimeSeries> timeSeriesNameUnknown = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), List.of(TIME_SERIES_NAME_UNKNOWN));
        List<TimeSeries> timelines = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIMELINE_GROUP_UUID), List.of(TIMELINE_NAME));

        // --- check result --- //
        // check time series
        // must contain only one element
        getLogger().info("Timeseries size = " + timeSeries.size());
        assertThat(timeSeries).hasSize(1);
        // content must be the same
        String expectedTimeSeriesJson = TimeSeries.toJson(database.get(TIME_SERIES_GROUP_UUID).stream().filter(series -> series.getMetadata().getName().equals(TIME_SERIES_NAME_1)).collect(Collectors.toList()));
        getLogger().info("expectedTimeSeriesJson = " + expectedTimeSeriesJson);
        String resultTimeSeriesJson = TimeSeries.toJson(timeSeries);
        getLogger().info("resultTimeSeriesJson = " + resultTimeSeriesJson);
        assertThat(objectMapper.readTree(resultTimeSeriesJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesJson));

        // check time series unknown
        // must contain only zero element
        getLogger().info("Timeseries size = " + timeSeriesNameUnknown.size());
        assertThat(timeSeriesNameUnknown).isEmpty();

        // check timeline
        // must contain only one element
        getLogger().info("Timeline size = " + timelines.size());
        assertThat(timelines).hasSize(1);
        // content must be the same
        String expectedTimelinesJson = TimeSeries.toJson(database.get(TIMELINE_GROUP_UUID).stream().filter(series -> series.getMetadata().getName().equals(TIMELINE_NAME)).collect(Collectors.toList()));
        getLogger().info("expectedTimelinesJson = " + expectedTimelinesJson);
        String resultTimelinesJson = TimeSeries.toJson(timelines);
        getLogger().info("resultTimelinesJson = " + resultTimelinesJson);
        assertThat(objectMapper.readTree(resultTimelinesJson)).isEqualTo(objectMapper.readTree(expectedTimelinesJson));
    }

    @Test
    public void testGetTimeSeriesGroupMetadata() throws JsonProcessingException {
        TimeSeriesGroupRest resultTimeSeriesGroupMetadata = timeSeriesClient.getTimeSeriesGroupMetadata(UUID.fromString(TIME_SERIES_GROUP_UUID));

        // --- check result --- //
        // metadata must be identical to expected
        String expectedTimeSeriesGroupMetadataJson = objectMapper.writeValueAsString(timeSeriesGroupMetadata);
        String resultTimeSeriesGroupMetadataJson = objectMapper.writeValueAsString(resultTimeSeriesGroupMetadata);
        getLogger().info("expectedTimeSeriesGroupMetadataJson = " + expectedTimeSeriesGroupMetadataJson);
        getLogger().info("resultTimeSeriesGroupMetadataJson = " + resultTimeSeriesGroupMetadataJson);
        assertThat(objectMapper.readTree(resultTimeSeriesGroupMetadataJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesGroupMetadataJson));
    }
}
