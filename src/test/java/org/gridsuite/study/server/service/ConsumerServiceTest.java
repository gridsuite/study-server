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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsumerServiceTest {

    @Mock
    private StudyConfigService studyConfigService;

    @InjectMocks
    private ConsumerService consumerService;

    private Method createDefaultDiagramGridLayout;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        createDefaultDiagramGridLayout = ConsumerService.class
                .getDeclaredMethod("createDefaultDiagramGridLayout", String.class, UserProfileInfos.class);
        createDefaultDiagramGridLayout.setAccessible(true);
    }

    @Test
    void whenProfileHasDiagramConfigThenDuplicateIsUsed() throws Exception {
        UUID configId = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(configId).build();
        when(studyConfigService.duplicateDiagramGridLayout(configId)).thenReturn(expected);

        UUID result = (UUID) createDefaultDiagramGridLayout.invoke(consumerService, "user", profile);

        assertEquals(expected, result);
        verify(studyConfigService).duplicateDiagramGridLayout(configId);
        verify(studyConfigService, never()).createDefaultDiagramGridLayout(any());
    }

    @Test
    void whenDuplicateFailsThenCreateDefaultIsUsed() throws Exception {
        UUID configId = UUID.randomUUID();
        UUID expected = UUID.randomUUID();
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(configId).name("name").build();
        when(studyConfigService.duplicateDiagramGridLayout(configId)).thenThrow(new RuntimeException("fail"));
        when(studyConfigService.createDefaultDiagramGridLayout(configId)).thenReturn(expected);

        UUID result = (UUID) createDefaultDiagramGridLayout.invoke(consumerService, "user", profile);

        assertEquals(expected, result);
        verify(studyConfigService).duplicateDiagramGridLayout(configId);
        verify(studyConfigService).createDefaultDiagramGridLayout(configId);
    }

    @Test
    void whenNoDiagramConfigThenCreateDefaultCalledWithNull() throws Exception {
        UUID expected = UUID.randomUUID();
        UserProfileInfos profile = UserProfileInfos.builder().diagramConfigId(null).build();
        when(studyConfigService.createDefaultDiagramGridLayout(null)).thenReturn(expected);

        UUID result = (UUID) createDefaultDiagramGridLayout.invoke(consumerService, "user", profile);

        assertEquals(expected, result);
        verify(studyConfigService, never()).duplicateDiagramGridLayout(any());
        verify(studyConfigService).createDefaultDiagramGridLayout(null);
    }
}
