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
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesMetadataRest;
import org.gridsuite.study.server.service.client.timeseries.impl.TimeSeriesClientImpl;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient.API_VERSION;
import static org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient.TIME_SERIES_END_POINT;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class TimeSeriesClientTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeSeriesClientTest.class);

    private static final String TIME_SERIES_BASE_URL = buildEndPointUrl("", API_VERSION, TIME_SERIES_END_POINT);

    private static final String TIME_SERIES_GROUP_UUID = "33333333-0000-0000-0000-000000000000";
    private static final String TIMELINE_GROUP_UUID = "44444444-0000-0000-0000-000000000000";

    private static final String TIME_SERIES_NAME_1 = "NETWORK__BUS____2-BUS____5-1_AC_iSide2";
    private static final String TIME_SERIES_NAME_2 = "NETWORK__BUS____1_TN_Upu_value";
    private static final String TIME_SERIES_NAME_UNKNOWN = "TIME_SERIES_NAME_UNKNOWN";
    private static final String TIMELINE_NAME = "Timeline";

    private final Map<String, List<TimeSeries<?, ?>>> database = new HashMap<>();

    private final TimeSeriesGroupRest timeSeriesGroupMetadata = new TimeSeriesGroupRest();

    private TimeSeriesClient timeSeriesClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @NotNull
    private Dispatcher getDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
                String path = Objects.requireNonNull(recordedRequest.getPath());
                String method = recordedRequest.getMethod();

                MockResponse response = new MockResponse(HttpStatus.NOT_FOUND.value());
                List<String> pathSegments = ListUtils.emptyIfNull(recordedRequest.getRequestUrl().pathSegments());

                // timeseries-group/{groupUuid}/xxx
                if ("GET".equals(method) && path.matches(TIME_SERIES_BASE_URL + "/.*")) {
                    String pathEnding = pathSegments.get(pathSegments.size() - 1);
                    if ("metadata".equals(pathEnding)) {  // timeseries-group/{groupUuid}/metadata
                        String groupUuid = pathSegments.stream().limit(pathSegments.size() - 1).reduce((first, second) -> second).orElse("");
                        try {
                            if (TIME_SERIES_GROUP_UUID.equals(groupUuid)) {
                                String timeSeriesGroupMetadataJson = null;

                                    timeSeriesGroupMetadataJson = objectMapper.writeValueAsString(timeSeriesGroupMetadata);
                                    response = new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), timeSeriesGroupMetadataJson);
                            }
                        } catch (JsonProcessingException e) {
                            return new MockResponse(HttpStatus.NOT_FOUND.value());
                        }
                    } else {
                        // take {groupUuid} at the last
                        String groupUuid = emptyIfNull(recordedRequest.getRequestUrl().pathSegments()).stream().reduce((first, second) -> second).orElse("");
                        String timeSeriesNames = recordedRequest.getRequestUrl().queryParameter("timeSeriesNames");
                        LOGGER.info("sent timeSeriesNames = {}", timeSeriesNames);
                        List<TimeSeries<?, ?>> timeseries;
                        if (timeSeriesNames == null) {
                            timeseries = database.get(groupUuid); // get all timeseries of the same groupUuid
                        } else {
                            // get only precise time series names
                            timeseries = database.get(groupUuid).stream().filter(series -> timeSeriesNames.contains(series.getMetadata().getName())).toList();
                        }
                        if (CollectionUtils.isEmpty(timeseries)) {
                            return new MockResponse(HttpStatus.NO_CONTENT.value());
                        }
                        response = new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), TimeSeries.toJson(timeseries));
                    }
                }
                return response;
            }
        };
    }

    @BeforeEach
    public void setup(final MockWebServer server) {
        // setup fake database
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<TimeSeries<?, ?>> timeSeries = new ArrayList<>(Arrays.asList(
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
        database.put(TIMELINE_GROUP_UUID, List.of(timeline));

        // group metadata for timeseries
        timeSeriesGroupMetadata.setId(UUID.fromString(TIME_SERIES_GROUP_UUID));
        timeSeriesGroupMetadata.setMetadatas(List.of(new TimeSeriesMetadataRest(TIME_SERIES_NAME_1), new TimeSeriesMetadataRest(TIME_SERIES_NAME_2)));

        // config client
        LOGGER.info("Mock server started at port = {}", server.getPort());
        server.setDispatcher(this.getDispatcher());

        // get base URL
        HttpUrl baseUrl = server.url("");
        remoteServicesProperties.setServiceUri("timeseries-server", baseUrl.toString());
        timeSeriesClient = new TimeSeriesClientImpl(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetTimeSeriesGroup() throws Exception {
        List<TimeSeries> timeSeries = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), null);
        List<TimeSeries> timelines = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIMELINE_GROUP_UUID), null);

        // --- check result --- //
        // check time series
        // must contain two elements
        LOGGER.info("Timeseries size = {}", timeSeries.size());
        assertThat(timeSeries).hasSize(2);
        // content must be the same
        String expectedTimeSeriesJson = TimeSeries.toJson(database.get(TIME_SERIES_GROUP_UUID));
        LOGGER.info("expectedTimeSeriesJson = {}", expectedTimeSeriesJson);
        String resultTimeSeriesJson = TimeSeries.toJson(timeSeries);
        LOGGER.info("resultTimeSeriesJson = {}", resultTimeSeriesJson);
        assertThat(objectMapper.readTree(resultTimeSeriesJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesJson));

        // check timeline
        // must contain only one element
        LOGGER.info("Timeline size = {}", timelines.size());
        assertThat(timelines).hasSize(1);
        // content must be the same
        String expectedTimelinesJson = TimeSeries.toJson(database.get(TIMELINE_GROUP_UUID));
        LOGGER.info("expectedTimelinesJson = {}", expectedTimelinesJson);
        String resultTimelinesJson = TimeSeries.toJson(timelines);
        LOGGER.info("resultTimelinesJson = {}", resultTimelinesJson);
        assertThat(objectMapper.readTree(resultTimelinesJson)).isEqualTo(objectMapper.readTree(expectedTimelinesJson));
    }

    @Test
    void testGetTimeSeriesGroupGivenTimeSeriesNames() throws Exception {
        List<TimeSeries> timeSeries = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), List.of(TIME_SERIES_NAME_1));
        List<TimeSeries> timeSeriesNameUnknown = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_GROUP_UUID), List.of(TIME_SERIES_NAME_UNKNOWN));
        List<TimeSeries> timelines = timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIMELINE_GROUP_UUID), List.of(TIMELINE_NAME));

        // --- check result --- //
        // check time series
        // must contain only one element
        LOGGER.info("Timeseries size = {}", timeSeries.size());
        assertThat(timeSeries).hasSize(1);
        // content must be the same
        String expectedTimeSeriesJson = TimeSeries.toJson(database.get(TIME_SERIES_GROUP_UUID).stream().filter(series -> series.getMetadata().getName().equals(TIME_SERIES_NAME_1)).toList());
        LOGGER.info("expectedTimeSeriesJson = {}", expectedTimeSeriesJson);
        String resultTimeSeriesJson = TimeSeries.toJson(timeSeries);
        LOGGER.info("resultTimeSeriesJson = {}", resultTimeSeriesJson);
        assertThat(objectMapper.readTree(resultTimeSeriesJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesJson));

        // check time series unknown
        // must contain only zero element
        LOGGER.info("Timeseries size = {}", timeSeriesNameUnknown.size());
        assertThat(timeSeriesNameUnknown).isEmpty();

        // check timeline
        // must contain only one element
        LOGGER.info("Timeline size = {}", timelines.size());
        assertThat(timelines).hasSize(1);
        // content must be the same
        String expectedTimelinesJson = TimeSeries.toJson(database.get(TIMELINE_GROUP_UUID).stream().filter(series -> series.getMetadata().getName().equals(TIMELINE_NAME)).toList());
        LOGGER.info("expectedTimelinesJson = {}", expectedTimelinesJson);
        String resultTimelinesJson = TimeSeries.toJson(timelines);
        LOGGER.info("resultTimelinesJson = {}", resultTimelinesJson);
        assertThat(objectMapper.readTree(resultTimelinesJson)).isEqualTo(objectMapper.readTree(expectedTimelinesJson));
    }

    @Test
    void testGetTimeSeriesGroupMetadata() throws Exception {
        TimeSeriesGroupRest resultTimeSeriesGroupMetadata = timeSeriesClient.getTimeSeriesGroupMetadata(UUID.fromString(TIME_SERIES_GROUP_UUID));

        // --- check result --- //
        // metadata must be identical to expected
        String expectedTimeSeriesGroupMetadataJson = objectMapper.writeValueAsString(timeSeriesGroupMetadata);
        String resultTimeSeriesGroupMetadataJson = objectMapper.writeValueAsString(resultTimeSeriesGroupMetadata);
        LOGGER.info("expectedTimeSeriesGroupMetadataJson = {}", expectedTimeSeriesGroupMetadataJson);
        LOGGER.info("resultTimeSeriesGroupMetadataJson = {}", resultTimeSeriesGroupMetadataJson);
        assertThat(objectMapper.readTree(resultTimeSeriesGroupMetadataJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesGroupMetadataJson));
    }
}
