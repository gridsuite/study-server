/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.common;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeleteCalculationResultsTest {

    private static final String BASE_URI = "http://computation-server";

    private static final String PATH = "/v1/results";

    private final RestTemplate restTemplate = mock(RestTemplate.class);

    @Test
    void testDeleteCalculationResultsWithoutUuids() {
        assertThrows(NullPointerException.class, () -> AbstractComputationRestService.deleteCalculationResults(null, PATH, restTemplate, BASE_URI));
        assertThrows(NullPointerException.class, () -> AbstractComputationRestService.deleteCalculationResults(null, PATH, restTemplate, BASE_URI, "userId"));
        AbstractComputationRestService.deleteCalculationResults(List.of(), PATH, restTemplate, BASE_URI);
        AbstractComputationRestService.deleteCalculationResults(List.of(), PATH, restTemplate, BASE_URI, "userId");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void testDeleteCalculationResults() {
        UUID resultUuid = UUID.randomUUID();
        AbstractComputationRestService.deleteCalculationResults(List.of(resultUuid), PATH, restTemplate, BASE_URI);
        verify(restTemplate).delete(BASE_URI + PATH + "?resultsUuids=" + resultUuid);

        AbstractComputationRestService.deleteCalculationResults(List.of(resultUuid), PATH, restTemplate, BASE_URI, "userId");

        ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(BASE_URI + PATH + "?resultsUuids=" + resultUuid), eq(HttpMethod.DELETE), entityCaptor.capture(), eq(Void.class));
        assertEquals("userId", entityCaptor.getValue().getHeaders().getFirst(HEADER_USER_ID));
    }
}
