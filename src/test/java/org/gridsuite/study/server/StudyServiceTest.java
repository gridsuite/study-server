/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.dto.BasicStudyInfos;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyServiceTest.class);

    private WireMockServer wireMockServer;

    private WireMockStubs wireMockStubs;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private StudyService studyService;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoSpyBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private NetworkModificationService networkModificationService;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockStubs = new WireMockStubs(wireMockServer);

        // Start the server.
        wireMockServer.start();

        singleLineDiagramService.setSingleLineDiagramServerBaseUri(wireMockServer.baseUrl());
    }

    @Test
    void testImportCsv() throws Exception {
        String csvContent = """
                voltageLevelId;equipmentType;xPosition;yPosition;xLabelPosition;yLabelPosition
                VL1;4;100;200;110;210""";

        MockMultipartFile file = new MockMultipartFile(
                "file", "positions.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );
        UUID positionsFromCsvUuid = wireMockStubs.stubCreatePositionsFromCsv();
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/studies/network-visualizations/nad-positions-config")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
            .andExpect(status().isOk());

        // assert API calls have been made
        wireMockStubs.verifyStubCreatePositionsFromCsv(positionsFromCsvUuid);
    }

    @Test
    void testBuildFirstLevelChildren() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota not reached, all first level children of N1 will be built
        doReturn(Optional.of(10)).when(userAdminService).getUserMaxAllowedBuilds(userId);
        doReturn(0L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);
        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);
        mockNodeBuild(node3.getIdNode(), rootNetworkUuid);

        studyService.buildFirstLevelChildren(studyUuid, node1.getIdNode(), rootNetworkUuid, userId);

        verifyNodeBuild(node2.getIdNode(), rootNetworkUuid);
        verifyNodeBuild(node3.getIdNode(), rootNetworkUuid);
        // check n4 has actually not been built
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(3)).getUserMaxAllowedBuilds(userId);
        verify(networkModificationTreeService, times(3)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testBuildFirstLevelChildrenWithQuotaAlreadyReached() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota already reached, nothing will be built
        doReturn(Optional.of(10)).when(userAdminService).getUserMaxAllowedBuilds(userId);
        doReturn(10L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        studyService.buildFirstLevelChildren(studyUuid, node1.getIdNode(), rootNetworkUuid, userId);

        verify(networkModificationService, times(0)).buildNode(eq(node2.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        verify(userAdminService, times(1)).getUserMaxAllowedBuilds(userId);
        verify(networkModificationTreeService, times(1)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testBuildFirstLevelChildrenWithQuotaReached() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota will be reached, only one child will be built
        doReturn(Optional.of(10)).when(userAdminService).getUserMaxAllowedBuilds(userId);
        doReturn(9L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);

        studyService.buildFirstLevelChildren(studyUuid, node1.getIdNode(), rootNetworkUuid, userId);

        verifyNodeBuild(node2.getIdNode(), rootNetworkUuid);
        verify(networkModificationService, times(0)).buildNode(eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(2)).getUserMaxAllowedBuilds(userId);
        verify(networkModificationTreeService, times(2)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    private void mockNodeBuild(UUID nodeUuid, UUID rootNetworkUuid) {
        doReturn(new BuildInfos()).when(networkModificationTreeService).getBuildInfos(nodeUuid, rootNetworkUuid);
        doNothing().when(networkModificationTreeService).setModificationReports(eq(nodeUuid), eq(rootNetworkUuid), any());
        doNothing().when(networkModificationTreeService).updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
    }

    private void verifyNodeBuild(UUID nodeUuid, UUID rootNetworkUuid) {
        verify(networkModificationTreeService, times(1)).updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
        verify(networkModificationService, times(1)).buildNode(eq(nodeUuid), eq(rootNetworkUuid), any(), eq(null));
    }

    @AfterEach
    void tearDown() {
        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
