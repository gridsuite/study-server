package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.UserProfileInfos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ConsumerServiceTest {

    @Mock
    private StudyConfigService studyConfigService;

    @InjectMocks
    private ConsumerService consumerService;

    private Method createGridLayoutFromNadDiagram;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        createGridLayoutFromNadDiagram = ConsumerService.class
                .getDeclaredMethod("createGridLayoutFromNadDiagram", String.class, UserProfileInfos.class);
        createGridLayoutFromNadDiagram.setAccessible(true);
    }

    @Test
    void whenProfileHasAssociatedNadConfig() throws Exception {
        UUID configId = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(configId).name("name").build();
        when(studyConfigService.createGridLayoutFromNadDiagram(configId)).thenReturn(expected);

        UUID result = (UUID) createGridLayoutFromNadDiagram.invoke(consumerService, "user", profile);

        assertEquals(expected, result);
        verify(studyConfigService).createGridLayoutFromNadDiagram(configId);
    }

    @Test
    void whenProfileHasNoAssociatedNadConfig() throws Exception {
        UUID expected = null;
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(null).build();
        when(studyConfigService.createGridLayoutFromNadDiagram(null)).thenReturn(expected);
        UUID result = (UUID) createGridLayoutFromNadDiagram.invoke(consumerService, "user", profile);
        assertEquals(expected, result);
    }
}
