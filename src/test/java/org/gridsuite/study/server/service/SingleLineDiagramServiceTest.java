/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayoutDetails;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Mohamed BENREJEB <mohamed.ben-rejeb at rte-france.com>
 */
class SingleLineDiagramServiceTest {

    @Test
    void duplicateNadConfig() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SingleLineDiagramService service = new SingleLineDiagramService("http://single-line-diagram-server", restTemplate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<NetworkAreaDiagramLayoutDetails> httpEntity = new HttpEntity<>(headers);
        UUID source = UUID.randomUUID();
        UUID expected = UUID.randomUUID();

        when(restTemplate.postForObject(
            "http://single-line-diagram-server/v1/network-area-diagram/config?duplicateFrom=" + source,
            httpEntity, UUID.class)).thenReturn(expected);

        assertEquals(expected, service.duplicateNadConfig(source));
    }

    @Test
    void duplicateNadConfigError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SingleLineDiagramService service = new SingleLineDiagramService("http://single-line-diagram-server", restTemplate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<NetworkAreaDiagramLayoutDetails> httpEntity = new HttpEntity<>(headers);
        UUID source = UUID.randomUUID();

        when(restTemplate.postForObject(
            "http://single-line-diagram-server/v1/network-area-diagram/config?duplicateFrom=" + source,
            httpEntity, UUID.class)).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        StudyException e = assertThrows(StudyException.class, () -> service.duplicateNadConfig(source));
        assertEquals(StudyException.Type.DUPLICATE_DIAGRAM_GRID_LAYOUT_FAILED,
            ReflectionTestUtils.invokeMethod(e, "getType"));
    }
}

