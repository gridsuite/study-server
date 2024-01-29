/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.StudyType;
import lombok.SneakyThrows;
import org.gridsuite.study.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.Assert.*;


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
        Set<String> countriesTemp = new HashSet<>();
        countriesTemp.add("FR");

        ShortCircuitParametersEntity shortCircuitParametersEntity = new ShortCircuitParametersEntity(false, false, false, false, StudyType.TRANSIENT, 1, false, false, false, false, InitialVoltageProfileMode.NOMINAL, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);

        countriesTemp.add("IT");

        ShortCircuitParametersEntity shortCircuitParametersEntity2 = new ShortCircuitParametersEntity(true, true, false, true, StudyType.STEADY_STATE, 0, false, false, false, false, InitialVoltageProfileMode.NOMINAL, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);

        countriesTemp.add("DE");

        ShortCircuitParametersEntity shortCircuitParametersEntity3 = new ShortCircuitParametersEntity(true, false, false, true, StudyType.SUB_TRANSIENT, 10, false, false, false, false, InitialVoltageProfileMode.NOMINAL, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);

        StudyEntity studyEntity1 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId")
                .caseFormat("caseFormat")
                .caseUuid(UUID.randomUUID())
                .shortCircuitParameters(shortCircuitParametersEntity)
                .build();

        StudyEntity studyEntity2 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId2")
                .caseFormat("caseFormat2")
                .caseUuid(UUID.randomUUID())
                .shortCircuitParameters(shortCircuitParametersEntity2)
                .build();

        StudyEntity studyEntity3 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId3")
                .caseFormat("caseFormat3")
                .caseUuid(UUID.randomUUID())
                .shortCircuitParameters(shortCircuitParametersEntity3)
                .build();

        studyRepository.save(studyEntity1);
        studyRepository.save(studyEntity2);
        studyRepository.save(studyEntity3);

        List<StudyEntity> findAllStudies = studyRepository.findAll();
        assertEquals(3, findAllStudies.size());

        StudyEntity savedStudyEntity1 = findAllStudies.stream().filter(e -> studyEntity1.getId() != null && studyEntity1.getId().equals(e.getId())).findFirst().orElse(null);
        StudyEntity savedStudyEntity2 = findAllStudies.stream().filter(e -> studyEntity2.getId() != null && studyEntity2.getId().equals(e.getId())).findFirst().orElse(null);

        assertNotNull(savedStudyEntity1);
        assertNotNull(savedStudyEntity2);

        // updates
        savedStudyEntity1.setShortCircuitParameters(shortCircuitParametersEntity);
        studyRepository.save(savedStudyEntity1);

        StudyEntity savedStudyEntity1Updated = studyRepository.findById(studyEntity1.getId()).get();
        assertNotNull(savedStudyEntity1Updated.getShortCircuitParameters());

        studyRepository.save(savedStudyEntity1Updated);
        savedStudyEntity1Updated = studyRepository.findById(studyEntity1.getId()).get();
        assertNotNull(savedStudyEntity1Updated);
    }

    @Test
    public void testStudyCreationRequest() {
        UUID studyUuid = UUID.randomUUID();
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(studyUuid);
        studyCreationRequestRepository.save(studyCreationRequestEntity);
        assertEquals(1, studyCreationRequestRepository.findAll().size());
        assertTrue(studyCreationRequestRepository.findById(studyUuid).isPresent());
    }

    @Test
    @Transactional
    public void testStudyDefaultShortCircuitParameters() {

        StudyEntity studyEntity1 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId")
                .caseFormat("caseFormat")
                .caseUuid(UUID.randomUUID())
                .shortCircuitParameters(null) // intentionally set to null
                .build();

        ShortCircuitParametersEntity shortCircuitParamFromEntity1 = studyEntity1.getShortCircuitParameters();
        assertNotNull(shortCircuitParamFromEntity1);

        assertEquals(20., shortCircuitParamFromEntity1.getMinVoltageDropProportionalThreshold(), 0.001); // 20 is the default value
        assertEquals(20., studyEntity1.getShortCircuitParameters().getMinVoltageDropProportionalThreshold(), 0.001);
        studyEntity1.getShortCircuitParameters().setMinVoltageDropProportionalThreshold(30.);
        assertEquals(30., studyEntity1.getShortCircuitParameters().getMinVoltageDropProportionalThreshold(), 0.001);

        studyRepository.save(studyEntity1);

        StudyEntity savedStudyEntity1 = studyRepository.findAll().get(0);
        assertNotNull(savedStudyEntity1.getShortCircuitParameters());
        assertEquals(30., savedStudyEntity1.getShortCircuitParameters().getMinVoltageDropProportionalThreshold(), 0.001);
    }

    @Test
    @SneakyThrows
    @Transactional
    public void testStudyImportParameters() {
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
        assertEquals("param1", "changedValue1, changedValue2", savedImportParameters.get("param1"));
        assertEquals("changedValue", savedImportParameters.get("param2"));
    }
}
