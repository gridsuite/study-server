/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

    private static final String DIRECTORY_SERVER_URI = "http://directory-server";
    private static final String USER_ID = "userId";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private DirectoryService directoryService;

    @BeforeEach
    void setup() {
        when(remoteServicesProperties.getServiceUri("directory-server")).thenReturn(DIRECTORY_SERVER_URI);
        directoryService = new DirectoryService(remoteServicesProperties, restClient);
    }

    @Test
    void testGetElements() {
        UUID elementUuid = UUID.randomUUID();
        String response = "[{\"name\":\"element\"}]";
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/elements?ids=" + elementUuid + "&elementTypes=STUDY&strictMode=false";
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        var requestSpec = restClient.get().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(headersCaptor.capture());
        when(requestSpec.retrieve().body(String.class)).thenReturn(response);

        assertThat(directoryService.getElements(List.of(elementUuid), List.of("STUDY"), false, USER_ID)).isEqualTo(response);

        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getAllValues().forEach(consumer -> consumer.accept(headers));
        assertThat(headers.getFirst(HEADER_USER_ID)).isEqualTo(USER_ID);
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testElementExists() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        var requestSpec = restClient.head().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        assertThat(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).isTrue();

        verify(requestSpec.retrieve()).toBodilessEntity();
    }

    @Test
    void testElementDoesNotExist() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        var requestSpec = restClient.head().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().toBodilessEntity()).thenReturn(ResponseEntity.noContent().build());

        assertThat(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).isFalse();
    }

    @Test
    void testUpdateElementsReferences() {
        UUID studyUuid = UUID.randomUUID();
        UUID nodeUuid = UUID.randomUUID();
        ModificationReference rootReference = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), null);
        ModificationReference compositeReference = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        makeFluent(restClient.put().uri(referenceUrl(rootReference)));
        makeFluent(restClient.put().uri(referenceUrl(compositeReference)));

        directoryService.updateSharingLinks(List.of(rootReference, compositeReference), studyUuid, nodeUuid, USER_ID);

        ArgumentCaptor<ReferenceAttributes> rootBodyCaptor = ArgumentCaptor.forClass(ReferenceAttributes.class);
        var rootRequest = restClient.put().uri(referenceUrl(rootReference));
        verify(rootRequest).body(rootBodyCaptor.capture());
        assertThat(rootBodyCaptor.getValue()).usingRecursiveComparison().isEqualTo(ReferenceAttributes.createReferenceAttributes(
            rootReference.modificationUuid(), studyUuid, nodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE));
        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(rootRequest).headers(headersCaptor.capture());
        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertThat(headers.getFirst(HEADER_USER_ID)).isEqualTo(USER_ID);

        ArgumentCaptor<ReferenceAttributes> compositeBodyCaptor = ArgumentCaptor.forClass(ReferenceAttributes.class);
        verify(restClient.put().uri(referenceUrl(compositeReference))).body(compositeBodyCaptor.capture());
        assertThat(compositeBodyCaptor.getValue()).usingRecursiveComparison().isEqualTo(ReferenceAttributes.createReferenceAttributes(
            compositeReference.modificationUuid(), nodeUuid, compositeReference.containerId(), ReferenceAttributes.ReferenceType.STUDY_NODE_NETWORK_MODIFICATION));
    }

    @Test
    void testRemoveElementsReferences() {
        ModificationReference reference1 = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), null);
        ModificationReference reference2 = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var request1 = restClient.delete().uri(referenceUrl(reference1));
        var request2 = restClient.delete().uri(referenceUrl(reference2));
        doReturn(request1).when(request1).headers(any());
        doReturn(request2).when(request2).headers(any());

        directoryService.removeSharingLinks(List.of(reference1, reference2), USER_ID);

        ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(request1).headers(headersCaptor.capture());
        verify(request2).retrieve();

        HttpHeaders headers = new HttpHeaders();
        headersCaptor.getValue().accept(headers);
        assertThat(headers.getFirst(HEADER_USER_ID)).isEqualTo(USER_ID);
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void testRemoveElementReferenceWithNullArguments() {
        UUID uuid = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> directoryService.removeSharingLink(uuid, null, USER_ID));
        assertThrows(NullPointerException.class, () -> directoryService.removeSharingLink(null, uuid, USER_ID));
        verifyNoInteractions(restClient);
    }

    private static String referenceUrl(ModificationReference reference) {
        return DIRECTORY_SERVER_URI + "/v1/elements/" + reference.referencedId() + "/references/" + reference.modificationUuid();
    }

    private static void makeFluent(RestClient.RequestBodySpec requestSpec) {
        doReturn(requestSpec).when(requestSpec).headers(any());
        doReturn(requestSpec).when(requestSpec).contentType(any());
    }
}
