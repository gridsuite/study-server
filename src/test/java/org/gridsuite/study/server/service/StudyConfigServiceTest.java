package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayout;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudyConfigServiceTest {

    @Test
    void createGridLayoutFromNadDiagram() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RemoteServicesProperties properties = mock(RemoteServicesProperties.class);
        when(properties.getServiceUri("study-config-server")).thenReturn("http://study-config");
        StudyConfigService service = new StudyConfigService(properties, restTemplate);

        UUID src = UUID.randomUUID();
        UUID clone = UUID.randomUUID();
        UUID expected = UUID.randomUUID();

        ArgumentCaptor<HttpEntity<DiagramGridLayout>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(eq("http://study-config/v1/diagram-grid-layout"),
                eq(HttpMethod.POST), captor.capture(), eq(UUID.class)))
            .thenReturn(ResponseEntity.ok(expected));

        assertEquals(expected, service.createGridLayoutFromNadDiagram(src, clone));

        DiagramGridLayout body = captor.getValue().getBody();
        NetworkAreaDiagramLayout layout = (NetworkAreaDiagramLayout) body.getDiagramLayouts().get(0);
        assertEquals(src, layout.getOriginalNadConfigUuid());
        assertEquals(clone, layout.getCurrentNadConfigUuid());
    }

    @Test
    void createGridLayoutFromNadDiagramNullSource() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RemoteServicesProperties properties = mock(RemoteServicesProperties.class);
        when(properties.getServiceUri("study-config-server")).thenReturn("http://study-config");
        StudyConfigService service = new StudyConfigService(properties, restTemplate);

        assertNull(service.createGridLayoutFromNadDiagram(null, UUID.randomUUID()));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void createGridLayoutFromNadDiagramRestError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RemoteServicesProperties properties = mock(RemoteServicesProperties.class);
        when(properties.getServiceUri("study-config-server")).thenReturn("http://study-config");
        StudyConfigService service = new StudyConfigService(properties, restTemplate);

        UUID src = UUID.randomUUID();
        UUID clone = UUID.randomUUID();

        when(restTemplate.exchange(eq("http://study-config/v1/diagram-grid-layout"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(UUID.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(StudyException.class, () -> service.createGridLayoutFromNadDiagram(src, clone));
    }
}
