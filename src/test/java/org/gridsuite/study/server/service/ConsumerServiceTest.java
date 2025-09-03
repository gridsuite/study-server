package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.UserProfileInfos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsumerServiceTest {

    private StudyConfigService studyConfigService;
    private SingleLineDiagramService singleLineDiagramService;
    private DirectoryService directoryService;
    private ConsumerService consumerService;

    @BeforeEach
    void setUp() {
        studyConfigService = mock(StudyConfigService.class);
        singleLineDiagramService = mock(SingleLineDiagramService.class);
        directoryService = mock(DirectoryService.class);
        consumerService = new ConsumerService(null, null, null, null, null,
                null, null, null, null, null,
                studyConfigService, directoryService, null, null, null, null,
                singleLineDiagramService);
    }

    @Test
    void createGridLayoutFromNadDiagram() {
        UUID diagramConfigId = UUID.randomUUID();
        UUID clonedConfigId = UUID.randomUUID();
        UUID gridLayoutUuid = UUID.randomUUID();
        String nadElementName = "N";
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(diagramConfigId).build();

        when(singleLineDiagramService.duplicateNadConfig(diagramConfigId)).thenReturn(clonedConfigId);
        when(directoryService.getElementName(diagramConfigId)).thenReturn(nadElementName);
        when(studyConfigService.createGridLayoutFromNadDiagram(diagramConfigId, clonedConfigId, nadElementName)).thenReturn(gridLayoutUuid);

        UUID result = ReflectionTestUtils.invokeMethod(consumerService, "createGridLayoutFromNadDiagram", "user", profile);

        assertEquals(gridLayoutUuid, result);
        verify(singleLineDiagramService).duplicateNadConfig(diagramConfigId);
        verify(studyConfigService).createGridLayoutFromNadDiagram(diagramConfigId, clonedConfigId, nadElementName);
    }

    @Test
    void createGridLayoutFromNadDiagramNoConfig() {
        UUID result = ReflectionTestUtils.invokeMethod(consumerService, "createGridLayoutFromNadDiagram", "user", UserProfileInfos.builder().build());

        assertNull(result);
        verifyNoInteractions(singleLineDiagramService, studyConfigService);
    }

    @Test
    void createGridLayoutFromNadDiagramCloneFailure() {
        UUID diagramConfigId = UUID.randomUUID();
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(diagramConfigId).build();
        when(singleLineDiagramService.duplicateNadConfig(diagramConfigId))
            .thenThrow(new RuntimeException("boom"));

        UUID result = ReflectionTestUtils.invokeMethod(consumerService, "createGridLayoutFromNadDiagram", "user", profile);

        assertNull(result);
        verify(singleLineDiagramService).duplicateNadConfig(diagramConfigId);
        verifyNoInteractions(studyConfigService);
    }
}
