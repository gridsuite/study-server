/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.networkmodification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.RootNetworkTagsCheckInfos;
import org.gridsuite.study.server.dto.SharedModificationsWriteAccess;
import org.gridsuite.study.server.service.RootNetworkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NetworkModificationServiceTest {

    private static final String NETWORK_MODIFICATION_SERVER_URI = "http://network-modification-server";
    private static final String USER_ID = "userId";
    private static final String RESPONSE = "{\"id\":\"modification\"}";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private RestClient restClient;

    @Mock
    private RootNetworkService rootNetworkService;

    private NetworkModificationRestService networkModificationService;

    @BeforeEach
    void setup() {
        when(remoteServicesProperties.getServiceUri("network-modification-server")).thenReturn(NETWORK_MODIFICATION_SERVER_URI);
        networkModificationService = new NetworkModificationRestService(remoteServicesProperties, restClient, new ObjectMapper(), rootNetworkService);
    }

    @Test
    void testGetLineTypesCatalog() {
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types";
        when(restClient.get().uri(expectedUrl).retrieve().body(String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getLineTypesCatalog()).isEqualTo(RESPONSE);
    }

    @Test
    void testGetLineType() {
        UUID lineTypeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types/" + lineTypeUuid;
        when(restClient.get().uri(expectedUrl).retrieve().body(String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getLineType(lineTypeUuid)).isEqualTo(RESPONSE);
    }

    @Test
    void testGetLineTypeWithLimits() {
        UUID lineTypeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types/" + lineTypeUuid + "/with-limits?area=FR&temperature=25&shapeFactor=1.0";
        when(restClient.get().uri(expectedUrl).retrieve().body(String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getLineTypeWithLimits(lineTypeUuid, "FR", "25", "1.0")).isEqualTo(RESPONSE);
    }

    @Test
    void testGetNetworkModification() {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid;
        expectUserIdIsForwarded(expectedUrl);

        assertThat(networkModificationService.getNetworkModification(modificationUuid, USER_ID)).isEqualTo(RESPONSE);
    }

    @Test
    void testGetBusBarSectionsForNewCoupler() {
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI
            + "/v1/network-modifications/busbar-sections-for-new-coupler?voltageLevelId=VL1&busBarCount=2&sectionCount=4&switchKindList=BREAKER&switchKindList=DISCONNECTOR";
        when(restClient.get().uri(expectedUrl).retrieve().body(String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"))).isEqualTo(RESPONSE);
    }

    @Test
    void testUpdateNetworkModification() {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid;
        var requestSpec = restClient.put().uri(expectedUrl);
        makeFluent(requestSpec);

        networkModificationService.updateNetworkModification(modificationUuid, RESPONSE, USER_ID);
        verify(requestSpec).contentType(MediaType.APPLICATION_JSON);
        verify(requestSpec).body(RESPONSE);
        assertHeaders(requestSpec, Map.of(HEADER_USER_ID, USER_ID));
    }

    @Test
    void testUpdateNetworkModificationsMetadata() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications?uuids=" + firstUuid + "&uuids=" + secondUuid;

        networkModificationService.updateNetworkModificationsMetadata(List.of(firstUuid, secondUuid), RESPONSE, "userId");

        verify(restClient.put().uri(expectedUrl).contentType(MediaType.APPLICATION_JSON).headers(any())).body(RESPONSE);
    }

    @Test
    void testUnshareModificationReference() {
        UUID modificationReferenceUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/unshare";
        var requestSpec = restClient.put().uri(expectedUrl);
        makeFluent(requestSpec);

        networkModificationService.unshareModificationReferences(List.of(modificationReferenceUuid), USER_ID);

        verify(requestSpec).contentType(MediaType.APPLICATION_JSON);
        verify(requestSpec).body(List.of(modificationReferenceUuid));
        assertHeaders(requestSpec, Map.of(HEADER_USER_ID, USER_ID));
    }

    @Test
    void testDeleteStashedModificationsGroups() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/groups/stashed-modifications";
        var requestSpec = restClient.method(HttpMethod.DELETE).uri(expectedUrl);
        makeFluent(requestSpec);

        networkModificationService.deleteStashedModificationsFromGroups(List.of(firstUuid, secondUuid), USER_ID);
        verify(requestSpec).contentType(MediaType.APPLICATION_JSON);
        verify(requestSpec).body("[\"" + firstUuid + "\",\"" + secondUuid + "\"]");
        assertHeaders(requestSpec, Map.of(HEADER_USER_ID, USER_ID));
    }

    @Test
    void testGetReferences() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/references?uuids=" + firstUuid + "&uuids=" + secondUuid;
        List<ModificationReference> expected = List.of(new ModificationReference(firstUuid, UUID.randomUUID(), null));
        var requestSpec = restClient.get().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().body(new ParameterizedTypeReference<List<ModificationReference>>() { })).thenReturn(expected);

        assertThat(networkModificationService.getModificationReferences(List.of(firstUuid, secondUuid))).isEqualTo(expected);
    }

    @Test
    void testFindParentComposites() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        UUID compositeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-composite-modifications/parent-composites?uuids=" + firstUuid + "&uuids=" + secondUuid;
        Map<UUID, UUID> expected = Map.of(firstUuid, compositeUuid);
        var requestSpec = restClient.get().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().body(new ParameterizedTypeReference<Map<UUID, UUID>>() { })).thenReturn(expected);

        assertThat(networkModificationService.findParentComposites(List.of(firstUuid, secondUuid))).isEqualTo(expected);
    }

    @Test
    void testGetReferencesFromGroup() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/" + groupUuid + "/references";
        List<ModificationReference> expected = List.of(new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        var requestSpec = restClient.get().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().body(new ParameterizedTypeReference<List<ModificationReference>>() { })).thenReturn(expected);

        assertThat(networkModificationService.getModificationReferences(groupUuid)).isEqualTo(expected);
    }

    @Test
    void testDeleteModificationReferences() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/groups/" + groupUuid + "/references";
        var requestSpec = restClient.delete().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());

        networkModificationService.deleteModificationReferences(groupUuid, "userId");

        assertHeaders(requestSpec, Map.of(HEADER_USER_ID, USER_ID));
        verify(requestSpec).retrieve();
    }

    @Test
    void testCreateSharingLinksWithRecreate() {
        UUID groupUuid = UUID.randomUUID();
        String baseUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/groups/sharing-links";

        networkModificationService.createSharingLinks(List.of(groupUuid), USER_ID);
        networkModificationService.createSharingLinks(List.of(groupUuid), true, USER_ID);

        verify(restTemplate).exchange(
            eq(baseUrl + "?recreate=true"),
            eq(HttpMethod.POST),
            argThat((HttpEntity<?> entity) -> USER_ID.equals(entity.getHeaders().getFirst(HEADER_USER_ID)) && List.of(groupUuid).equals(entity.getBody())),
            eq(Void.class)
        );
        verify(restTemplate).exchange(
            eq(baseUrl + "?recreate=true"),
            eq(HttpMethod.POST),
            argThat((HttpEntity<?> entity) -> USER_ID.equals(entity.getHeaders().getFirst(HEADER_USER_ID)) && List.of(groupUuid).equals(entity.getBody())),
            eq(Void.class));
    }

    @Test
    void testAddRootNetworkTag() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/root-network-tag"
                + "?groupUuids=" + groupUuid + "&newTag=PH2";

        networkModificationService.addRootNetworkTag(List.of(groupUuid), "PH2");

        verify(restClient.post().uri(URI.create(expectedUrl)).contentType(MediaType.APPLICATION_JSON).retrieve()).toBodilessEntity();
        clearInvocations(restClient);

        // there is nothing to add the tag to without a group
        networkModificationService.addRootNetworkTag(List.of(), "PH2");

        verifyNoMoreInteractions(restClient);
    }

    @Test
    void testRenameRootNetworkTag() {
        UUID firstGroupUuid = UUID.randomUUID();
        UUID secondGroupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/root-network-tag"
                + "?groupUuids=" + firstGroupUuid + "&groupUuids=" + secondGroupUuid + "&oldTag=PH1&newTag=PH2";

        networkModificationService.renameRootNetworkTag(List.of(firstGroupUuid, secondGroupUuid), "PH1", "PH2", USER_ID);

        verify(restClient.put().uri(URI.create(expectedUrl)).contentType(MediaType.APPLICATION_JSON).headers(any()).retrieve()).toBodilessEntity();
        clearInvocations(restClient);

        // there is nothing to rename without a group
        networkModificationService.renameRootNetworkTag(List.of(), "PH1", "PH2", USER_ID);

        verifyNoMoreInteractions(restClient);
    }

    @Test
    void testDeleteRootNetworkTags() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/root-network-tag"
                + "?groupUuids=" + groupUuid + "&rootNetworkTags=PH1&rootNetworkTags=PH2";
        var requestSpec = restClient.delete().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());

        networkModificationService.deleteRootNetworkTags(List.of(groupUuid), List.of("PH1", "PH2"));

        assertHeaders(requestSpec, Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));
        verify(requestSpec.retrieve()).toBodilessEntity();
        clearInvocations(restClient);

        // there is nothing to drop without a group or without a tag
        networkModificationService.deleteRootNetworkTags(List.of(), List.of("PH1"));
        networkModificationService.deleteRootNetworkTags(List.of(groupUuid), List.of());

        verifyNoMoreInteractions(restClient);
    }

    @Test
    void testDuplicateModificationsGroupAssociatesTheRootNetworkTags() {
        UUID sourceGroupUuid = UUID.randomUUID();
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/groups/" + sourceGroupUuid + "/duplicate"
                + "?groupUuid=" + groupUuid + "&rootNetworkTags=PH1&rootNetworkTags=A%26B%2BC";

        networkModificationService.duplicateModificationsGroup(sourceGroupUuid, groupUuid, List.of("PH1", "A&B+C"), "userId");

        verify(restClient.post().uri(URI.create(expectedUrl)).contentType(MediaType.APPLICATION_JSON).headers(any()).retrieve()).toBodilessEntity();
    }

    @Test
    void testAssertRootNetworkTagsAllowed() {
        UUID groupUuid = UUID.randomUUID();
        UUID modificationUuid = UUID.randomUUID();
        UUID sharedUuid = UUID.randomUUID();
        String url = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/root-network-tags/authorized";
        var requestSpec = restClient.post().uri(url);
        makeFluent(requestSpec);

        networkModificationService.assertRootNetworkTagsAllowed(List.of(groupUuid), List.of(), List.of(), List.of("PH1", "PH2"), USER_ID);
        verify(requestSpec).body(new RootNetworkTagsCheckInfos(List.of(groupUuid), List.of(), List.of(), List.of("PH1", "PH2")));

        // the modifications and the shared modifications are checked too, even alone
        networkModificationService.assertRootNetworkTagsAllowed(List.of(), List.of(modificationUuid), List.of(), List.of("PH1"), USER_ID);
        verify(requestSpec).body(new RootNetworkTagsCheckInfos(List.of(), List.of(modificationUuid), List.of(), List.of("PH1")));
        networkModificationService.assertRootNetworkTagsAllowed(List.of(), List.of(), List.of(sharedUuid), List.of("PH1"), USER_ID);
        verify(requestSpec).body(new RootNetworkTagsCheckInfos(List.of(), List.of(), List.of(sharedUuid), List.of("PH1")));
        verify(requestSpec, times(3)).contentType(MediaType.APPLICATION_JSON);
        verify(requestSpec, times(3)).headers(argThat(headersConsumer -> {
            HttpHeaders headers = new HttpHeaders();
            headersConsumer.accept(headers);
            return USER_ID.equals(headers.getFirst(HEADER_USER_ID));
        }));
        clearInvocations(restClient);

        // there is nothing to check without a tag, or without anything to check it on
        networkModificationService.assertRootNetworkTagsAllowed(List.of(groupUuid), List.of(), List.of(), List.of(), USER_ID);
        networkModificationService.assertRootNetworkTagsAllowed(List.of(), List.of(), List.of(), List.of("PH1"), USER_ID);

        verifyNoMoreInteractions(restClient);
    }

    @Test
    void testHasModificationReferences() {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/references/exists?uuids=" + modificationUuid;

        networkModificationService.hasModificationReferences(List.of(modificationUuid));
        verify(restClient.get().uri(expectedUrl).retrieve()).body(Boolean.class);
    }

    @Test
    void testGetReferencedModificationsWriteAccess() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/references/write-access?uuids=" + groupUuid;
        var requestSpec = restClient.get().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(any());
        when(requestSpec.retrieve().body(SharedModificationsWriteAccess.class)).thenReturn(SharedModificationsWriteAccess.NONE_WRITABLE);

        assertThat(networkModificationService.getReferencedModificationsWriteAccess(List.of(groupUuid), USER_ID))
            .isEqualTo(SharedModificationsWriteAccess.NONE_WRITABLE);
    }

    @Test
    void testGetContainersWithModificationReferences() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/reference-modifications";
        var requestSpec = restClient.post().uri(expectedUrl).contentType(MediaType.APPLICATION_JSON);
        when(requestSpec.body(List.of(groupUuid)).retrieve().body(new ParameterizedTypeReference<List<UUID>>() { })).thenReturn(List.of(groupUuid));
        clearInvocations(requestSpec);

        assertThat(networkModificationService.getContainersWithModificationReferences(List.of(groupUuid))).containsExactly(groupUuid);
        // there is nothing to ask without a container
        assertThat(networkModificationService.getContainersWithModificationReferences(List.of())).isEmpty();
        verify(requestSpec).body(List.of(groupUuid));
    }

    @Test
    void testUpdateNetworkModificationsNameAndDescription() {
        UUID modificationUuid = UUID.randomUUID();
        String metadata = "";
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid + "/name-and-description";
        var requestSpec = restClient.put().uri(expectedUrl);
        makeFluent(requestSpec);

        networkModificationService.updateNetworkModificationNameAndDescription(modificationUuid, metadata, USER_ID);
        verify(requestSpec).body(metadata);
    }

    private void expectUserIdIsForwarded(String expectedUrl) {
        var requestSpec = restClient.get().uri(expectedUrl);
        doReturn(requestSpec).when(requestSpec).headers(argThat(headersConsumer -> {
            HttpHeaders headers = new HttpHeaders();
            headersConsumer.accept(headers);
            return USER_ID.equals(headers.getFirst(HEADER_USER_ID));
        }));
        when(requestSpec.retrieve().body(String.class)).thenReturn(RESPONSE);
    }

    private void assertHeaders(RestClient.RequestHeadersSpec<?> requestSpec, Map<String, String> expectedHeaders) {
        verify(requestSpec).headers(argThat(headersConsumer -> {
            HttpHeaders headers = new HttpHeaders();
            headersConsumer.accept(headers);
            return expectedHeaders.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(headers.getFirst(entry.getKey())));
        }));
    }

    private void makeFluent(RestClient.RequestBodySpec requestSpec) {
        doReturn(requestSpec).when(requestSpec).headers(any());
        doReturn(requestSpec).when(requestSpec).contentType(any(MediaType.class));
    }
}
