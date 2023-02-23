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
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.collections4.ListUtils;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesGroupInfos;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.service.client.AbstractRestClientTest;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.service.client.timeseries.impl.TimeSeriesClientImpl;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient.API_VERSION;
import static org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient.TIME_SERIES_END_POINT;
import static org.junit.Assert.assertEquals;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public class TimeSeriesClientTest extends AbstractRestClientTest {

    public static final String TIME_SERIES_GROUP_UUID = "33333333-0000-0000-0000-000000000000";
    public static final String TIME_LINE_GROUP_UUID = "44444444-0000-0000-0000-000000000000";

    public static final String TIME_SERIES_NAME_1 = "NETWORK__BUS____2-BUS____5-1_AC_iSide2";
    public static final String TIME_SERIES_NAME_2 = "NETWORK__BUS____1_TN_Upu_value";
    public static final String TIME_LINE_NAME = "TimeLine";

    private final Map<String, List<TimeSeries>> database = new HashMap<>();

    private final TimeSeriesGroupInfos timeSeriesGroupInfos = new TimeSeriesGroupInfos();

    private TimeSeriesClient timeSeriesClient;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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

                                    timeSeriesGroupMetadataJson = objectMapper.writeValueAsString(timeSeriesGroupInfos);
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

                        if (timeseries == null) {
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
        StringTimeSeries timeLine = TimeSeries.createString(TIME_LINE_NAME, index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint");

        database.put(TIME_SERIES_GROUP_UUID, timeSeries);
        database.put(TIME_LINE_GROUP_UUID, new ArrayList<>(Arrays.asList(timeLine)));

        // group metadata for timeseries
        timeSeriesGroupInfos.setId(UUID.fromString(TIME_SERIES_GROUP_UUID));
        timeSeriesGroupInfos.setMetadatas(List.of(new TimeSeriesMetadataInfos(TIME_SERIES_NAME_1), new TimeSeriesMetadataInfos(TIME_SERIES_NAME_2)));

        // config client
        timeSeriesClient = new TimeSeriesClientImpl(initMockWebServer(), restTemplate);
    }

    @Test
    public void testGetTimeSeriesGroup() throws JsonProcessingException {
        List<TimeSeries> timeSeries = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), null);
        List<TimeSeries> timeLines = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_LINE_GROUP_UUID), null);

        // --- check result --- //
        // check time series
        // must contain two elements
        getLogger().info("Timeseries size = " + timeSeries.size());
        assertEquals(2, timeSeries.size());
        // content must be the same
        String expectedTimeSeriesJson = TimeSeries.toJson(database.get(TIME_SERIES_GROUP_UUID));
        getLogger().info("expectedTimeSeriesJson = " + expectedTimeSeriesJson);
        String resultTimeSeriesJson = TimeSeries.toJson(timeSeries);
        getLogger().info("resultTimeSeriesJson = " + resultTimeSeriesJson);
        assertEquals(objectMapper.readTree(expectedTimeSeriesJson), objectMapper.readTree(resultTimeSeriesJson));

        // check timeline
        // must contain only one element
        getLogger().info("Timeline size = " + timeLines.size());
        assertEquals(1, timeLines.size());
        // content must be the same
        String expectedTimeLinesJson = TimeSeries.toJson(database.get(TIME_LINE_GROUP_UUID));
        getLogger().info("expectedTimeLinesJson = " + expectedTimeLinesJson);
        String resultTimeLinesJson = TimeSeries.toJson(timeLines);
        getLogger().info("resultTimeLinesJson = " + resultTimeLinesJson);
        assertEquals(objectMapper.readTree(expectedTimeLinesJson), objectMapper.readTree(resultTimeLinesJson));
    }

    @Test
    public void testGetTimeSeriesGroupGivenTimeSeriesNames() throws JsonProcessingException {
        List<TimeSeries> timeSeries = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), List.of(TIME_SERIES_NAME_1));
        List<TimeSeries> timeLines = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_LINE_GROUP_UUID), List.of(TIME_LINE_NAME));

        // --- check result --- //
        // check time series
        // must contain only one element
        getLogger().info("Timeseries size = " + timeSeries.size());
        assertEquals(1, timeSeries.size());
        // content must be the same
        String expectedTimeSeriesJson = TimeSeries.toJson(database.get(TIME_SERIES_GROUP_UUID).stream().filter(series -> series.getMetadata().getName().equals(TIME_SERIES_NAME_1)).collect(Collectors.toList()));
        getLogger().info("expectedTimeSeriesJson = " + expectedTimeSeriesJson);
        String resultTimeSeriesJson = TimeSeries.toJson(timeSeries);
        getLogger().info("resultTimeSeriesJson = " + resultTimeSeriesJson);
        assertEquals(objectMapper.readTree(expectedTimeSeriesJson), objectMapper.readTree(resultTimeSeriesJson));

        // check timeline
        // must contain only one element
        getLogger().info("Timeline size = " + timeLines.size());
        assertEquals(1, timeLines.size());
        // content must be the same
        String expectedTimeLinesJson = TimeSeries.toJson(database.get(TIME_LINE_GROUP_UUID).stream().filter(series -> series.getMetadata().getName().equals(TIME_LINE_NAME)).collect(Collectors.toList()));
        getLogger().info("expectedTimeLinesJson = " + expectedTimeLinesJson);
        String resultTimeLinesJson = TimeSeries.toJson(timeLines);
        getLogger().info("resultTimeLinesJson = " + resultTimeLinesJson);
        assertEquals(objectMapper.readTree(expectedTimeLinesJson), objectMapper.readTree(resultTimeLinesJson));
    }

    @Test
    public void testGetTimeSeriesGroupMetadata() throws JsonProcessingException {
        TimeSeriesGroupInfos resultTimeSeriesGroupMetadata = timeSeriesClient.getTimeSeriesGroupMetadata(UUID.fromString(TIME_SERIES_GROUP_UUID));

        // --- check result --- //
        // metadata must be identical to expected
        String expectedTimeSeriesGroupMetadataJson = objectMapper.writeValueAsString(timeSeriesGroupInfos);
        String resultTimeSeriesGroupMetadataJson = objectMapper.writeValueAsString(resultTimeSeriesGroupMetadata);
        getLogger().info("expectedTimeSeriesGroupMetadataJson = " + expectedTimeSeriesGroupMetadataJson);
        getLogger().info("resultTimeSeriesGroupMetadataJson = " + resultTimeSeriesGroupMetadataJson);
        assertEquals(objectMapper.readTree(expectedTimeSeriesGroupMetadataJson), objectMapper.readTree(resultTimeSeriesGroupMetadataJson));
    }
}
