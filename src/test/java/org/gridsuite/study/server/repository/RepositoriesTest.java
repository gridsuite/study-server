/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoriesTest {
    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

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

        StudyEntity studyEntity1 = studyRepository.save(StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId")
                .caseFormat("caseFormat")
                .caseUuid(UUID.randomUUID())
                .shortCircuitParametersUuid(shortCircuitParametersUuid1)
                .build());

        StudyEntity studyEntity2 = studyRepository.save(StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId2")
                .caseFormat("caseFormat2")
                .caseUuid(UUID.randomUUID())
                .shortCircuitParametersUuid(shortCircuitParametersUuid2)
                .build());

        studyRepository.save(StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId3")
                .caseFormat("caseFormat3")
                .caseUuid(UUID.randomUUID())
                .shortCircuitParametersUuid(shortCircuitParametersUuid3)
                .build());

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
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(studyUuid);
        studyCreationRequestRepository.save(studyCreationRequestEntity);
        assertThat(studyCreationRequestRepository.findAll()).singleElement().extracting(StudyCreationRequestEntity::getId).isEqualTo(studyUuid);
    }

    @Test
    @Transactional
    void testStudyImportParameters() {
        Map<String, String> importParametersExpected = Map.of("param1", "changedValue1, changedValue2", "param2", "changedValue");
        StudyEntity studyEntityToSave = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId")
                .caseFormat("caseFormat")
                .caseUuid(UUID.randomUUID())
                .importParameters(importParametersExpected)
                .build();

        studyRepository.save(studyEntityToSave);

        StudyEntity studyEntity = studyRepository.findAll().get(0);
        Map<String, String> savedImportParameters = studyEntity.getImportParameters();
        assertEquals(2, savedImportParameters.size());
        assertEquals("changedValue1, changedValue2", savedImportParameters.get("param1"), "param1");
        assertEquals("changedValue", savedImportParameters.get("param2"));
    }
}
