/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.RestClientConfig;
import org.gridsuite.study.server.dto.ModificationReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class NetworkModificationServiceTest {

    private static final String NETWORK_MODIFICATION_SERVER_URI = "http://network-modification-server";
    private static final String USER_ID = "userId";
    private static final String RESPONSE = "{\"id\":\"modification\"}";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    private MockRestServiceServer server;

    @Mock
    private RootNetworkService rootNetworkService;

    private NetworkModificationService networkModificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        when(remoteServicesProperties.getServiceUri("network-modification-server")).thenReturn(NETWORK_MODIFICATION_SERVER_URI);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        networkModificationService = new NetworkModificationService(remoteServicesProperties,
            new RestClientConfig().studyRestClient(builder), objectMapper, rootNetworkService);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void testGetLineTypesCatalog() {
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.getLineTypesCatalog()).isEqualTo(RESPONSE);
    }

    @Test
    void testGetLineType() {
        UUID lineTypeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types/" + lineTypeUuid;
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.getLineType(lineTypeUuid)).isEqualTo(RESPONSE);
    }

    @Test
    void testGetLineTypeWithLimits() {
        UUID lineTypeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types/" + lineTypeUuid + "/with-limits?area=FR&temperature=25&shapeFactor=1.0";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.getLineTypeWithLimits(lineTypeUuid, "FR", "25", "1.0")).isEqualTo(RESPONSE);
    }

    @Test
    void testGetNetworkModificationsFromComposite() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/network-modifications?uuids=" + firstUuid + "&uuids=" + secondUuid + "&onlyMetadata=false";
        expectUserIdIsForwarded(expectedUrl);

        assertThat(networkModificationService.getNetworkModificationsFromComposite(List.of(firstUuid, secondUuid), false, USER_ID)).isEqualTo(RESPONSE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = USER_ID)
    void testGetNetworkModification(String userId) {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid;
        var expectation = server.expect(requestTo(expectedUrl)).andExpect(method(HttpMethod.GET));
        if (userId == null) {
            expectation.andExpect(headerDoesNotExist(HEADER_USER_ID));
        } else {
            expectation.andExpect(header(HEADER_USER_ID, userId));
        }
        expectation.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.getNetworkModification(modificationUuid, userId)).isEqualTo(RESPONSE);
    }

    @Test
    void testGetBusBarSectionsForNewCoupler() {
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI
            + "/v1/network-modifications/busbar-sections-for-new-coupler?voltageLevelId=VL1&busBarCount=2&sectionCount=4&switchKindList=BREAKER&switchKindList=DISCONNECTOR";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"))).isEqualTo(RESPONSE);
    }

    @Test
    void testUpdateNetworkModification() {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid;
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header(HEADER_USER_ID, USER_ID))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(RESPONSE))
            .andRespond(withNoContent());

        networkModificationService.updateNetworkModification(modificationUuid, RESPONSE, USER_ID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = USER_ID)
    void testUpdateModification(String userId) {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid;
        var expectation = server.expect(requestTo(expectedUrl)).andExpect(method(HttpMethod.PUT));
        if (userId == null) {
            expectation.andExpect(headerDoesNotExist(HEADER_USER_ID))
                .andExpect(headerDoesNotExist(HttpHeaders.CONTENT_TYPE))
                .andExpect(content().string(""));
        } else {
            expectation.andExpect(header(HEADER_USER_ID, userId))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(RESPONSE));
        }
        expectation.andRespond(withNoContent());

        networkModificationService.updateModification(RESPONSE, modificationUuid, userId);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = USER_ID)
    void testUpdateModificationsMetadataWithoutBody(String userId) {
        UUID groupUuid = UUID.randomUUID();
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications?uuids=" + modificationUuid + "&groupUuid=" + groupUuid;
        var expectation = server.expect(requestTo(expectedUrl)).andExpect(method(HttpMethod.PUT))
            .andExpect(headerDoesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));
        if (userId == null) {
            expectation.andExpect(headerDoesNotExist(HEADER_USER_ID));
        } else {
            expectation.andExpect(header(HEADER_USER_ID, userId));
        }
        expectation.andRespond(withNoContent());

        networkModificationService.updateModificationsMetadata(groupUuid, List.of(modificationUuid), null, userId);
    }

    @Test
    void testUpdateNetworkModificationsMetadata() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications?uuids=" + firstUuid + "&uuids=" + secondUuid;
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header(HEADER_USER_ID, USER_ID))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(RESPONSE))
            .andRespond(withNoContent());

        networkModificationService.updateNetworkModificationsMetadata(List.of(firstUuid, secondUuid), RESPONSE, USER_ID);
    }

    @Test
    void testDeleteModificationsGroups() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/groups";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("[\"" + firstUuid + "\",\"" + secondUuid + "\"]"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        networkModificationService.deleteModificationsGroups(List.of(firstUuid, secondUuid));
    }

    @Test
    void testDeleteStashedModificationsGroups() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/groups/stashed-modifications";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header(HEADER_USER_ID, USER_ID))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("[\"" + firstUuid + "\",\"" + secondUuid + "\"]"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        networkModificationService.deleteStashedModificationsFromGroups(List.of(firstUuid, secondUuid), USER_ID);
    }

    @Test
    void testGetReferences() throws Exception {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/references?uuids=" + firstUuid + "&uuids=" + secondUuid;
        List<ModificationReference> expected = List.of(new ModificationReference(firstUuid, UUID.randomUUID(), null));
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.getModificationReferences(List.of(firstUuid, secondUuid))).isEqualTo(expected);
    }

    @Test
    void testFindParentComposites() throws Exception {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        UUID compositeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-composite-modifications/parent-composites?uuids=" + firstUuid + "&uuids=" + secondUuid;
        Map<UUID, UUID> expected = Map.of(firstUuid, compositeUuid);
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.findParentComposites(List.of(firstUuid, secondUuid))).isEqualTo(expected);
    }

    @Test
    void testGetReferencesFromGroup() throws Exception {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/" + groupUuid + "/references";
        List<ModificationReference> expected = List.of(new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.getModificationReferences(groupUuid)).isEqualTo(expected);
    }

    @Test
    void testRenameRootNetworkTag() {
        UUID firstGroupUuid = UUID.randomUUID();
        UUID secondGroupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/root-network-tag"
                + "?groupUuids=" + firstGroupUuid + "&groupUuids=" + secondGroupUuid + "&oldTag=PH1&newTag=PH2";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withNoContent());

        networkModificationService.renameRootNetworkTag(List.of(firstGroupUuid, secondGroupUuid), "PH1", "PH2");

        // there is nothing to rename without a group
        networkModificationService.renameRootNetworkTag(List.of(), "PH1", "PH2");
    }

    @Test
    void testDeleteRootNetworkTags() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/root-network-tag"
                + "?groupUuids=" + groupUuid + "&rootNetworkTags=PH1&rootNetworkTags=PH2";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withNoContent());

        networkModificationService.deleteRootNetworkTags(List.of(groupUuid), List.of("PH1", "PH2"));

        // there is nothing to drop without a group or without a tag
        networkModificationService.deleteRootNetworkTags(List.of(), List.of("PH1"));
        networkModificationService.deleteRootNetworkTags(List.of(groupUuid), List.of());
    }

    @Test
    void testAssertReferencedModificationsAreWritable() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/references/authorized?uuids=" + groupUuid;
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HEADER_USER_ID, USER_ID))
            .andRespond(withNoContent());

        networkModificationService.assertReferencedModificationsAreWritable(List.of(groupUuid), USER_ID);

        // there is nothing to check without a container
        networkModificationService.assertReferencedModificationsAreWritable(List.of(), USER_ID);
    }

    @Test
    void testHasModificationReferences() {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/containers/references/exists?uuids=" + modificationUuid;
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

        assertThat(networkModificationService.hasModificationReferences(List.of(modificationUuid))).isTrue();
    }

    private void expectUserIdIsForwarded(String expectedUrl) {
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HEADER_USER_ID, USER_ID))
            .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
    }
}
