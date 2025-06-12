/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
//TriggerCI
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoriesTest {
    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

    @Autowired
    private RootNetworkRepository rootNetworkRepository;

    @AfterEach
    void tearDown() {
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();
    }

    @Test
    @Transactional
    void testStudyRepository() {
        UUID shortCircuitParametersUuid1 = UUID.randomUUID();
        UUID shortCircuitParametersUuid2 = UUID.randomUUID();
        UUID shortCircuitParametersUuid3 = UUID.randomUUID();

        StudyEntity studyEntity = StudyEntity.builder()
                .id(UUID.randomUUID())
                .shortCircuitParametersUuid(shortCircuitParametersUuid1)
                .build();
        RootNetworkEntity rootNetworkEntity1 = RootNetworkEntity.builder()
            .id(UUID.randomUUID())
            .name("rootNetworkName")
            .tag("rn1")
            .networkUuid(UUID.randomUUID())
            .networkId("networkId")
            .caseFormat("caseFormat")
            .caseName("caseName1")
            .caseUuid(UUID.randomUUID()).build();
        studyEntity.addRootNetwork(rootNetworkEntity1);
        StudyEntity studyEntity1 = studyRepository.save(studyEntity);

        StudyEntity studyEntity2 = studyRepository.save(StudyEntity.builder()
                .id(UUID.randomUUID())
                .rootNetworks(List.of(RootNetworkEntity.builder()
                    .id(UUID.randomUUID())
                    .name("rootNetworkName2")
                    .tag("rn2")
                    .networkUuid(UUID.randomUUID())
                    .networkId("networkId2")
                    .caseFormat("caseFormat2")
                    .caseName("caseName2")
                    .caseUuid(UUID.randomUUID())
                    .build()
                ))
                .shortCircuitParametersUuid(shortCircuitParametersUuid2)
                .build());

        StudyEntity studyEntity3 = StudyEntity.builder()
            .id(UUID.randomUUID())
            .shortCircuitParametersUuid(shortCircuitParametersUuid3)
            .build();
        RootNetworkEntity rootNetworkEntity3 = RootNetworkEntity.builder()
            .id(UUID.randomUUID())
            .name("rootNetworkName3")
            .tag("rn3")
            .networkUuid(UUID.randomUUID())
            .networkId("networkId3")
            .caseFormat("caseFormat3")
            .caseName("caseName3")
            .caseUuid(UUID.randomUUID())
            .build();
        studyEntity3.addRootNetwork(rootNetworkEntity3);
        studyRepository.save(studyEntity3);

        assertThat(studyEntity1).as("studyEntity1").extracting(StudyEntity::getId).isNotNull();
        assertThat(studyEntity2).as("studyEntity2").extracting(StudyEntity::getId).isNotNull();
        assertThat(studyRepository.findAll()).as("studyRepository").hasSize(3)
                .anyMatch(se -> studyEntity1.getId().equals(se.getId()))
                .anyMatch(se -> studyEntity2.getId().equals(se.getId()));

        // updates
        final UUID newShortCircuitParametersUuid = UUID.randomUUID();
        studyEntity1.setShortCircuitParametersUuid(newShortCircuitParametersUuid);
        studyRepository.save(studyEntity1);
        assertThat(studyRepository.findById(studyEntity1.getId())).isPresent().get()
                .extracting(StudyEntity::getShortCircuitParametersUuid).isNotNull().isEqualTo(newShortCircuitParametersUuid);
    }

    @Transactional
    @Test
    void testStudyCreationRequest() {
        UUID studyUuid = UUID.randomUUID();
        String firstRootNetworkName = "firstRootNetworkName";
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(studyUuid, firstRootNetworkName);
        studyCreationRequestRepository.save(studyCreationRequestEntity);
        assertThat(studyCreationRequestRepository.findAll()).singleElement().extracting(StudyCreationRequestEntity::getId).isEqualTo(studyUuid);
        assertThat(studyCreationRequestRepository.findAll()).singleElement().extracting(StudyCreationRequestEntity::getFirstRootNetworkName).isEqualTo(firstRootNetworkName);
    }

    @Test
    @Transactional
    void testStudyImportParameters() {
        Map<String, String> importParametersExpected = Map.of("param1", "changedValue1, changedValue2", "param2", "changedValue");
        StudyEntity studyEntityToSave = StudyEntity.builder()
                .id(UUID.randomUUID())
                .build();

        RootNetworkEntity rootNetworkEntity = RootNetworkEntity.builder()
            .id(UUID.randomUUID())
            .name("rootNetworkName")
            .tag("rn1")
            .networkUuid(UUID.randomUUID())
            .networkId("networkId")
            .caseFormat("caseFormat")
            .caseName("caseName")
            .importParameters(importParametersExpected)
            .caseUuid(UUID.randomUUID()).build();

        studyEntityToSave.addRootNetwork(rootNetworkEntity);

        StudyEntity newStudy = studyRepository.save(studyEntityToSave);

        RootNetworkEntity newRootNetworkEntity = rootNetworkRepository.findAllWithInfosByStudyId(newStudy.getId()).get(0);
        Map<String, String> savedImportParameters = newRootNetworkEntity.getImportParameters();
        assertEquals(2, savedImportParameters.size());
        assertEquals("changedValue1, changedValue2", savedImportParameters.get("param1"), "param1");
        assertEquals("changedValue", savedImportParameters.get("param2"));
    }
}
