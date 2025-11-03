/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfigurationWithTestChannel
@DisableElasticsearch
class SupervisionServiceTest {

    @MockitoBean
    ElasticsearchOperations elasticsearchOperations;

    @MockitoBean
    IndexOperations indexOperations;

    @Autowired
    SupervisionService supervisionService;

    @Test
    void recreateStudyIndicesThrowsExceptionWhenDeleteFails() {
        when(elasticsearchOperations.indexOps(CreatedStudyBasicInfos.class)).thenReturn(indexOperations);
        when(elasticsearchOperations.indexOps(EquipmentInfos.class)).thenReturn(indexOperations);
        when(elasticsearchOperations.indexOps(TombstonedEquipmentInfos.class)).thenReturn(indexOperations);
        when(indexOperations.delete()).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> supervisionService.recreateStudyIndices());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        String reason = Objects.requireNonNull(exception.getReason());
        assertTrue(reason.contains("Failed to delete ElasticSearch index for"));
        verify(elasticsearchOperations, times(1)).indexOps(any(Class.class));
        verify(indexOperations, times(1)).delete();
        verify(indexOperations, never()).createWithMapping();
    }

    @Test
    void recreateStudyIndicesThrowsExceptionWhenCreateFails() {
        when(elasticsearchOperations.indexOps(CreatedStudyBasicInfos.class)).thenReturn(indexOperations);
        when(elasticsearchOperations.indexOps(EquipmentInfos.class)).thenReturn(indexOperations);
        when(elasticsearchOperations.indexOps(TombstonedEquipmentInfos.class)).thenReturn(indexOperations);
        when(indexOperations.delete()).thenReturn(true);
        when(indexOperations.createWithMapping()).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> supervisionService.recreateStudyIndices());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        String reason = Objects.requireNonNull(exception.getReason());
        assertTrue(reason.contains("Failed to create ElasticSearch index for"));
        verify(elasticsearchOperations, times(1)).indexOps(any(Class.class));
        verify(indexOperations, times(1)).delete();
        verify(indexOperations, times(1)).createWithMapping();
    }

    @Test
    void recreateStudyIndicesSuccess() {
        when(elasticsearchOperations.indexOps(CreatedStudyBasicInfos.class)).thenReturn(indexOperations);
        when(elasticsearchOperations.indexOps(EquipmentInfos.class)).thenReturn(indexOperations);
        when(elasticsearchOperations.indexOps(TombstonedEquipmentInfos.class)).thenReturn(indexOperations);
        when(indexOperations.delete()).thenReturn(true);
        when(indexOperations.createWithMapping()).thenReturn(true);

        supervisionService.recreateStudyIndices();

        verify(elasticsearchOperations, times(3)).indexOps(any(Class.class));
        verify(indexOperations, times(3)).delete();
        verify(indexOperations, times(3)).createWithMapping();
    }

    @AfterEach
    void verifyNoMoreInteractionsMocks() {
        verifyNoMoreInteractions(elasticsearchOperations);
        verifyNoMoreInteractions(indexOperations);
    }
}
