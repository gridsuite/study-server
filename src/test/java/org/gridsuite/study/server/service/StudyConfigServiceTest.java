package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayout;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudyConfigServiceTest {

    @Test
    void createGridLayoutFromNadDiagramShouldPostAndReturnUuid() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RemoteServicesProperties properties = mock(RemoteServicesProperties.class);
        when(properties.getServiceUri("study-config-server")).thenReturn("http://study-config");

        StudyConfigService service = new StudyConfigService(properties, restTemplate);

        UUID source = UUID.randomUUID();
        UUID clone = UUID.randomUUID();
        UUID expected = UUID.randomUUID();

        when(restTemplate.exchange(
            eq("http://study-config/v1/diagram-grid-layout"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(UUID.class)))
            .thenReturn(new ResponseEntity<>(expected, HttpStatus.OK));

        UUID result = service.createGridLayoutFromNadDiagram(source, clone);

        assertEquals(expected, result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<DiagramGridLayout>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("http://study-config/v1/diagram-grid-layout"), eq(HttpMethod.POST), captor.capture(), eq(UUID.class));

        DiagramGridLayout body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals(1, body.getDiagramLayouts().size());
        NetworkAreaDiagramLayout layout = (NetworkAreaDiagramLayout) body.getDiagramLayouts().get(0);
        assertEquals(source, layout.getOriginalNadConfigUuid());
        assertEquals(clone, layout.getCurrentNadConfigUuid());
    }

    @Test
    void createGridLayoutFromNadDiagramWithNullSourceReturnsNull() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RemoteServicesProperties properties = mock(RemoteServicesProperties.class);
        when(properties.getServiceUri("study-config-server")).thenReturn("http://study-config");

        StudyConfigService service = new StudyConfigService(properties, restTemplate);

        UUID result = service.createGridLayoutFromNadDiagram(null, UUID.randomUUID());

        assertNull(result);
        verifyNoInteractions(restTemplate);
    }
}
