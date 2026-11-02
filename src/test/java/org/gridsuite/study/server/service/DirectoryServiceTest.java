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
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
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
        var requestSpec = restClient.get().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().body(String.class)).thenReturn(response);

        assertThat(directoryService.getElements(List.of(elementUuid), List.of("STUDY"), false, USER_ID)).isEqualTo(response);

        verify(requestSpec).headers(argThat(headers -> {
            HttpHeaders requestHeaders = new HttpHeaders();
            headers.accept(requestHeaders);
            return USER_ID.equals(requestHeaders.getFirst(HEADER_USER_ID));
        }));
        verify(requestSpec).headers(argThat(headers -> {
            HttpHeaders requestHeaders = new HttpHeaders();
            headers.accept(requestHeaders);
            return MediaType.APPLICATION_JSON.equals(requestHeaders.getContentType());
        }));
    }

    @Test
    void testElementExists() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        var requestSpec = restClient.head().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().toBodilessEntity()).thenReturn(ResponseEntity.ok().<Void>build());

        assertThat(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).isTrue();
    }

    @Test
    void testElementDoesNotExist() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        var requestSpec = restClient.head().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().toBodilessEntity()).thenReturn(ResponseEntity.noContent().<Void>build());

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

        var rootRequest = restClient.put().uri(referenceUrl(rootReference)).headers(any()).contentType(MediaType.APPLICATION_JSON);
        verify(rootRequest).body(argThat((Object body) -> {
            assertThat(body).usingRecursiveComparison().isEqualTo(ReferenceAttributes.createReferenceAttributes(
                rootReference.modificationUuid(), studyUuid, nodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE));
            return true;
        }));

        var compositeRequest = restClient.put().uri(referenceUrl(compositeReference)).headers(any()).contentType(MediaType.APPLICATION_JSON);
        verify(compositeRequest).body(argThat((Object body) -> {
            assertThat(body).usingRecursiveComparison().isEqualTo(ReferenceAttributes.createReferenceAttributes(
                compositeReference.modificationUuid(), nodeUuid, compositeReference.containerId(), ReferenceAttributes.ReferenceType.STUDY_NODE_NETWORK_MODIFICATION));
            return true;
        }));
    }

    @Test
    void testRemoveElementsReferences() {
        ModificationReference reference1 = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), null);
        ModificationReference reference2 = new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        makeFluent(restClient.method(org.springframework.http.HttpMethod.DELETE).uri(referenceUrl(reference1)));
        makeFluent(restClient.method(org.springframework.http.HttpMethod.DELETE).uri(referenceUrl(reference2)));

        directoryService.removeSharingLinks(List.of(reference1, reference2), USER_ID);

        verify(restClient.method(org.springframework.http.HttpMethod.DELETE).uri(referenceUrl(reference1))).headers(any());
        verify(restClient.method(org.springframework.http.HttpMethod.DELETE).uri(referenceUrl(reference2))).headers(any());
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
