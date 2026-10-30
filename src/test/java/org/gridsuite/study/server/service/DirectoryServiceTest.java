/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ElementAttributes;
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

    private static final String DIRECTORY_SERVER_URI = "http://directory-server";
    private static final String USER_ID = "userId";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    @Mock
    private RestTemplate restTemplate;

    private DirectoryService directoryService;

    @BeforeEach
    void setup() {
        when(remoteServicesProperties.getServiceUri("directory-server")).thenReturn(DIRECTORY_SERVER_URI);
        directoryService = new DirectoryService(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetElements() {
        UUID elementUuid = UUID.randomUUID();
        String response = "[{\"name\":\"element\"}]";
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/elements?ids=" + elementUuid + "&elementTypes=STUDY&strictMode=false";
        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(eq(expectedUrl), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(response));

        assertThat(directoryService.getElements(List.of(elementUuid), List.of("STUDY"), false, USER_ID)).isEqualTo(response);

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst(HEADER_USER_ID)).isEqualTo(USER_ID);
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testElementExists() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        when(restTemplate.exchange(eq(expectedUrl), eq(HttpMethod.HEAD), org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(), eq(Void.class)))
            .thenReturn(ResponseEntity.ok().build());

        assertThat(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).isTrue();

        verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.HEAD), org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(), eq(Void.class));
    }

    @Test
    void testElementDoesNotExist() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        when(restTemplate.exchange(eq(expectedUrl), eq(HttpMethod.HEAD), org.mockito.ArgumentMatchers.<HttpEntity<Void>>any(), eq(Void.class)))
            .thenReturn(ResponseEntity.noContent().build());

        assertThat(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).isFalse();
    }

    @Test
    void testUpdateElementsReferences() {
        UUID studyUuid = UUID.randomUUID();
        UUID nodeUuid = UUID.randomUUID();
        ModificationReference rootReference = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), null);
        ModificationReference compositeReference = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        directoryService.updateSharingLinks(List.of(rootReference, compositeReference), studyUuid, nodeUuid, USER_ID);

        ArgumentCaptor<HttpEntity<ReferenceAttributes>> rootEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(referenceUrl(rootReference)), eq(HttpMethod.PUT), rootEntityCaptor.capture(), eq(Void.class));
        assertThat(rootEntityCaptor.getValue().getBody()).usingRecursiveComparison().isEqualTo(ReferenceAttributes.createReferenceAttributes(
            rootReference.modificationUuid(), studyUuid, nodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE));
        assertThat(rootEntityCaptor.getValue().getHeaders().getFirst(HEADER_USER_ID)).isEqualTo(USER_ID);

        ArgumentCaptor<HttpEntity<ReferenceAttributes>> compositeEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(referenceUrl(compositeReference)), eq(HttpMethod.PUT), compositeEntityCaptor.capture(), eq(Void.class));
        assertThat(compositeEntityCaptor.getValue().getBody()).usingRecursiveComparison().isEqualTo(ReferenceAttributes.createReferenceAttributes(
            compositeReference.modificationUuid(), nodeUuid, compositeReference.containerId(), ReferenceAttributes.ReferenceType.STUDY_NODE_NETWORK_MODIFICATION));
    }

    @Test
    void testRemoveElementsReferences() {
        ModificationReference reference1 = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), null);
        ModificationReference reference2 = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        directoryService.removeSharingLinks(List.of(reference1, reference2), USER_ID);

        ArgumentCaptor<HttpEntity<Object>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(referenceUrl(reference1)), eq(HttpMethod.DELETE), entityCaptor.capture(), eq(ElementAttributes.class));
        verify(restTemplate).exchange(eq(referenceUrl(reference2)), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(ElementAttributes.class));

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst(HEADER_USER_ID)).isEqualTo(USER_ID);
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testRemoveElementReferenceWithNullArguments() {
        UUID uuid = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> directoryService.removeSharingLink(uuid, null, USER_ID));
        assertThrows(NullPointerException.class, () -> directoryService.removeSharingLink(null, uuid, USER_ID));
        verifyNoInteractions(restTemplate);
    }

    private static String referenceUrl(ModificationReference reference) {
        return DIRECTORY_SERVER_URI + "/v1/elements/" + reference.referencedId() + "/references/" + reference.modificationUuid();
    }
}
