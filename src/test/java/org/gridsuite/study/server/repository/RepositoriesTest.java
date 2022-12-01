/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.shortcircuit.StudyType;
import org.apache.commons.collections4.map.HashedMap;
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
        Map<String, String> metrics = new HashedMap<>();
        metrics.put("key1", "value1");
        metrics.put("key2", "value2");
        LoadFlowResultEntity loadFlowResultEntity = new LoadFlowResultEntity(null, false, metrics, "logs", new ArrayList<>());
        LoadFlowResultEntity loadFlowResultEntity2 = new LoadFlowResultEntity(null, false, metrics, "logs2", new ArrayList<>());
        LoadFlowResultEntity loadFlowResultEntity3 = new LoadFlowResultEntity(null, true, metrics, "logs3", new ArrayList<>());

        ComponentResultEmbeddable componentResultEmbeddable1 = new ComponentResultEmbeddable(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 1, "slackBusId", 1.0, 1.3);
        ComponentResultEmbeddable componentResultEmbeddable2 = new ComponentResultEmbeddable(2, 2, LoadFlowResult.ComponentResult.Status.CONVERGED, 2, "slackBusId", 2.0, 1.4);

        loadFlowResultEntity.getComponentResults().add(componentResultEmbeddable1);
        loadFlowResultEntity.getComponentResults().add(componentResultEmbeddable2);

        ComponentResultEmbeddable componentResultEmbeddable3 = new ComponentResultEmbeddable(3, 3, LoadFlowResult.ComponentResult.Status.FAILED, 3, "slackBusId", 3.0, 1.5);
        ComponentResultEmbeddable componentResultEmbeddable4 = new ComponentResultEmbeddable(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 4, "slackBusId", 4.0, 1.6);

        loadFlowResultEntity2.getComponentResults().add(componentResultEmbeddable3);
        loadFlowResultEntity2.getComponentResults().add(componentResultEmbeddable4);

        ComponentResultEmbeddable componentResultEmbeddable5 = new ComponentResultEmbeddable(3, 3, LoadFlowResult.ComponentResult.Status.FAILED, 5, "slackBusId", 5.0, 1.7);
        ComponentResultEmbeddable componentResultEmbeddable6 = new ComponentResultEmbeddable(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 6, "slackBusId", 6.0, 1.8);

        loadFlowResultEntity3.getComponentResults().add(componentResultEmbeddable5);
        loadFlowResultEntity3.getComponentResults().add(componentResultEmbeddable6);

        Set<String> countriesTemp = new HashSet<>();
        countriesTemp.add("FR");
        LoadFlowParametersEntity loadFlowParametersEntity = new LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES,
                true, false, true, false, true,
                false, true, false,
                true, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD, true,
                countriesTemp, LoadFlowParameters.ConnectedComponentMode.MAIN, false);
        ShortCircuitParametersEntity shortCircuitParametersEntity = new ShortCircuitParametersEntity(false, false, false, StudyType.TRANSIENT, 1);

        countriesTemp.add("IT");
        LoadFlowParametersEntity loadFlowParametersEntity2 = new LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES,
                true, false, true, false, true,
                false, true, false,
                true, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD, true,
                countriesTemp, LoadFlowParameters.ConnectedComponentMode.MAIN, false);
        ShortCircuitParametersEntity shortCircuitParametersEntity2 = new ShortCircuitParametersEntity(true, true, true, StudyType.STEADY_STATE, 0);

        countriesTemp.add("DE");
        LoadFlowParametersEntity loadFlowParametersEntity3 = new LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES,
                true, false, true, false, true,
                false, true, false,
                true, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD, true,
                countriesTemp, LoadFlowParameters.ConnectedComponentMode.MAIN, false);
        ShortCircuitParametersEntity shortCircuitParametersEntity3 = new ShortCircuitParametersEntity(true, false, true, StudyType.SUB_TRANSIENT, 10);

        StudyEntity studyEntity1 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId")
                .caseFormat("caseFormat")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .loadFlowParameters(loadFlowParametersEntity)
                .shortCircuitParameters(shortCircuitParametersEntity)
                .build();

        StudyEntity studyEntity2 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId2")
                .caseFormat("caseFormat2")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .loadFlowParameters(loadFlowParametersEntity2)
                .shortCircuitParameters(shortCircuitParametersEntity2)
                .build();

        StudyEntity studyEntity3 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .networkUuid(UUID.randomUUID())
                .networkId("networkId3")
                .caseFormat("caseFormat3")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .loadFlowParameters(loadFlowParametersEntity3)
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
        savedStudyEntity1.setLoadFlowParameters(loadFlowParametersEntity);
        savedStudyEntity1.setShortCircuitParameters(shortCircuitParametersEntity);
        studyRepository.save(savedStudyEntity1);

        StudyEntity savedStudyEntity1Updated = studyRepository.findById(studyEntity1.getId()).get();
        assertNotNull(savedStudyEntity1Updated.getLoadFlowParameters());
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
                .casePrivate(true)
                .loadFlowParameters(LoadFlowParametersEntity.builder().build())
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
}
