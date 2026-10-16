/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class NetworkModificationReferencingInfosUpdateTest {
    @Autowired
    private StudyRepository studyRepository;
    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private DirectoryService directoryService;
    @MockitoBean
    NotificationService notificationService;

    @MockitoSpyBean
    private StudyService studyService;

    private final UUID node1Uuid = UUID.randomUUID();
    private final UUID node2Uuid = UUID.randomUUID();
    private final UUID group1Uuid = UUID.randomUUID();
    private final UUID group2Uuid = UUID.randomUUID();
    private final UUID composite1Uuid = UUID.randomUUID();
    private final UUID composite2Uuid = UUID.randomUUID();
    private final UUID sharedModificationUuid = UUID.randomUUID();
    private final UUID modificationReferenceToMoveUuid = UUID.randomUUID();

    private final ModificationReference modificationReference = new ModificationReference(modificationReferenceToMoveUuid, sharedModificationUuid, composite1Uuid);

    private UUID studyUuid;

    @BeforeEach
    void setup() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.randomUUID(), UUID.randomUUID(), "caseName", "caseFormat", UUID.randomUUID());
        studyEntity = studyRepository.save(studyEntity);
        studyUuid = studyEntity.getId();

        when(networkModificationTreeService.getStudyUuidForNodeId(any(UUID.class))).thenReturn(studyUuid);
        when(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(any(UUID.class), any(UUID.class), any(UUID.class)))
            .thenReturn(new ModificationApplicationContext(UUID.randomUUID(), "variantId", UUID.randomUUID(), UUID.randomUUID(), "networkRootTag"));

        when(networkModificationService.getModificationReferences(List.of(modificationReferenceToMoveUuid))).thenReturn(List.of(modificationReference));

        when(networkModificationTreeService.getModificationGroupUuid(node1Uuid)).thenReturn(group1Uuid);
        when(networkModificationTreeService.getModificationGroupUuid(node2Uuid)).thenReturn(group2Uuid);
    }

    @AfterEach
    void tearDown() {
        studyRepository.deleteAll();
    }

    @Test
    void testMoveNetworkModification() {
        // Composite1 -> composite2
        ReferenceAttributes referenceAttributesExpected =
            ReferenceAttributes.createReferenceAttributes(modificationReferenceToMoveUuid, node1Uuid, composite2Uuid, ReferenceType.STUDY_NODE_NETWORK_MODIFICATION);
        testMoveNetworkModification(node1Uuid, node1Uuid, composite1Uuid, composite2Uuid, referenceAttributesExpected);

        // Group (node1) -> composite2
        referenceAttributesExpected =
            ReferenceAttributes.createReferenceAttributes(modificationReferenceToMoveUuid, node1Uuid, composite2Uuid, ReferenceType.STUDY_NODE_NETWORK_MODIFICATION);
        testMoveNetworkModification(node1Uuid, node1Uuid, null, composite2Uuid, referenceAttributesExpected);

        // Composite1 -> Group (node1)
        referenceAttributesExpected = ReferenceAttributes.createReferenceAttributes(modificationReferenceToMoveUuid, studyUuid, node1Uuid, ReferenceType.STUDY_NODE);
        testMoveNetworkModification(node1Uuid, node1Uuid, composite1Uuid, null, referenceAttributesExpected);

        // Group (node1) -> Group (node2)
        referenceAttributesExpected = ReferenceAttributes.createReferenceAttributes(modificationReferenceToMoveUuid, studyUuid, node2Uuid, ReferenceType.STUDY_NODE);
        testMoveNetworkModification(node1Uuid, node2Uuid, null, null, referenceAttributesExpected);
    }

    private void testMoveNetworkModification(UUID originNodeUuid, UUID targetNodeUuid, UUID sourceCompositeUuid,
                                             UUID targetCompositeUuid, ReferenceAttributes referenceAttributesExpected) {
        final String userId = "userId";
        boolean isTargetInDifferentNodeTree = false;
        reset(directoryService);

        ModificationMoveInfos modificationMoveInfos = new ModificationMoveInfos(modificationReferenceToMoveUuid, sourceCompositeUuid, targetCompositeUuid, null);
        studyService.moveNetworkModifications(studyUuid, originNodeUuid, targetNodeUuid, List.of(modificationMoveInfos), isTargetInDifferentNodeTree, userId);

        ArgumentCaptor<ReferenceAttributes> referenceAttributesCaptor = ArgumentCaptor.forClass(ReferenceAttributes.class);
        ArgumentCaptor<UUID> sharedModificationUuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(directoryService, times(1)).updateElementReference(sharedModificationUuidCaptor.capture(), referenceAttributesCaptor.capture(), anyString());

        assertEquals(sharedModificationUuid, sharedModificationUuidCaptor.getValue());

        assertNotNull(referenceAttributesCaptor.getValue());
        assertThat(referenceAttributesCaptor.getValue())
            .usingRecursiveComparison()
            .isEqualTo(referenceAttributesExpected);
    }
}
