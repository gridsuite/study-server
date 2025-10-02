package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.UserProfileInfos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudyServiceCreateGridLayoutTest {

    private StudyConfigService studyConfigService;
    private SingleLineDiagramService singleLineDiagramService;
    private DirectoryService directoryService;
    private StudyService studyService;

    @BeforeEach
    void setUp() {
        studyConfigService = mock(StudyConfigService.class);
        singleLineDiagramService = mock(SingleLineDiagramService.class);
        directoryService = mock(DirectoryService.class);
        studyService = new StudyService(null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, singleLineDiagramService, null, null, null,
                null, null, null, null, null, null, null,
                null, studyConfigService, null, null, null, null, null,
                null, directoryService);
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

        UUID result = studyService.createGridLayoutFromNadDiagram("user", profile);

        assertEquals(gridLayoutUuid, result);
        verify(singleLineDiagramService).duplicateNadConfig(diagramConfigId);
        verify(studyConfigService).createGridLayoutFromNadDiagram(diagramConfigId, clonedConfigId, nadElementName);
    }

    @Test
    void createGridLayoutFromNadDiagramNoConfig() {
        UUID result = studyService.createGridLayoutFromNadDiagram("user", UserProfileInfos.builder().build());

        assertNull(result);
        verifyNoInteractions(singleLineDiagramService, studyConfigService);
    }

    @Test
    void createGridLayoutFromNadDiagramCloneFailure() {
        UUID diagramConfigId = UUID.randomUUID();
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(diagramConfigId).build();
        when(singleLineDiagramService.duplicateNadConfig(diagramConfigId))
                .thenThrow(new RuntimeException("boom"));

        UUID result = studyService.createGridLayoutFromNadDiagram("user", profile);

        assertNull(result);
        verify(singleLineDiagramService).duplicateNadConfig(diagramConfigId);
        verifyNoInteractions(studyConfigService);
    }
}
