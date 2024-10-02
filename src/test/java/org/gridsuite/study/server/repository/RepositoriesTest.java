/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.repository.timepoint.TimePointEntity;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DisableElasticsearch
public class RepositoriesTest {
    @Autowired
    StudyRepository studyRepository;

    @Autowired
    StudyCreationRequestRepository studyCreationRequestRepository;

    private void cleanDB() {
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();
    }

    @After
    public void tearDown() {
        cleanDB();
    }

    @Test
    @Transactional
    public void testStudyRepository() {
        UUID shortCircuitParametersUuid1 = UUID.randomUUID();
        UUID shortCircuitParametersUuid2 = UUID.randomUUID();
        UUID shortCircuitParametersUuid3 = UUID.randomUUID();

        StudyEntity studyEntity = StudyEntity.builder()
                .id(UUID.randomUUID())
                .shortCircuitParametersUuid(shortCircuitParametersUuid1)
                .build();
        TimePointEntity timePointEntity1 = TimePointEntity.builder()
            .networkUuid(UUID.randomUUID())
            .networkId("networkId")
            .caseFormat("caseFormat")
            .caseName("caseName1")
            .caseUuid(UUID.randomUUID()).build();
        studyEntity.addTimePoint(timePointEntity1);
        StudyEntity studyEntity1 = studyRepository.save(studyEntity);

        StudyEntity studyEntity2 = studyRepository.save(StudyEntity.builder()
                .id(UUID.randomUUID())
                .timePoints(List.of(TimePointEntity.builder()
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
        TimePointEntity timePointEntity3 = TimePointEntity.builder()
            .networkUuid(UUID.randomUUID())
            .networkId("networkId3")
            .caseFormat("caseFormat3")
            .caseName("caseName3")
            .caseUuid(UUID.randomUUID())
            .build();
        studyEntity3.addTimePoint(timePointEntity3);
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
    public void testStudyCreationRequest() {
        UUID studyUuid = UUID.randomUUID();
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(studyUuid);
        studyCreationRequestRepository.save(studyCreationRequestEntity);
        assertThat(studyCreationRequestRepository.findAll()).singleElement().extracting(StudyCreationRequestEntity::getId).isEqualTo(studyUuid);
    }

    @Test
    @Transactional
    public void testStudyImportParameters() {
        Map<String, String> importParametersExpected = Map.of("param1", "changedValue1, changedValue2", "param2", "changedValue");
        StudyEntity studyEntityToSave = StudyEntity.builder()
                .id(UUID.randomUUID())
                .timePoints(List.of(TimePointEntity.builder()
                    .networkUuid(UUID.randomUUID())
                    .networkId("networkId")
                    .caseFormat("caseFormat")
                    .caseName("caseName")
                    .caseUuid(UUID.randomUUID()).build()))
                .importParameters(importParametersExpected)
                .build();

        studyRepository.save(studyEntityToSave);

        StudyEntity studyEntity = studyRepository.findAll().get(0);
        Map<String, String> savedImportParameters = studyEntity.getImportParameters();
        assertEquals(2, savedImportParameters.size());
        assertEquals("param1", "changedValue1, changedValue2", savedImportParameters.get("param1"));
        assertEquals("changedValue", savedImportParameters.get("param2"));
    }
}
