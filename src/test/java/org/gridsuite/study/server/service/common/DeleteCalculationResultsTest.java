/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeleteCalculationResultsTest {

    private static final String BASE_URI = "http://computation-server";

    private static final String PATH = "/v1/results";

    private final RestClient.Builder restClientBuilder = RestClient.builder();

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();

    private final RestClient restClient = restClientBuilder.build();

    @Test
    void testDeleteCalculationResultsWithoutUuids() {
        assertThrows(NullPointerException.class, () -> AbstractComputationRestService.deleteCalculationResults(null, PATH, restClient, BASE_URI));
        assertThrows(NullPointerException.class, () -> AbstractComputationRestService.deleteCalculationResults(null, PATH, restClient, BASE_URI, "userId"));
        AbstractComputationRestService.deleteCalculationResults(List.of(), PATH, restClient, BASE_URI);
        AbstractComputationRestService.deleteCalculationResults(List.of(), PATH, restClient, BASE_URI, "userId");
        server.verify();
    }

    @Test
    void testDeleteCalculationResults() {
        UUID resultUuid = UUID.randomUUID();
        server.expect(requestTo(BASE_URI + PATH + "?resultsUuids=" + resultUuid))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess());
        server.expect(requestTo(BASE_URI + PATH + "?resultsUuids=" + resultUuid))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header(HEADER_USER_ID, "userId"))
            .andRespond(withSuccess());

        AbstractComputationRestService.deleteCalculationResults(List.of(resultUuid), PATH, restClient, BASE_URI);
        AbstractComputationRestService.deleteCalculationResults(List.of(resultUuid), PATH, restClient, BASE_URI, "userId");
        server.verify();
    }
}
