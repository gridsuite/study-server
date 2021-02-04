/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repositories;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import org.apache.commons.collections4.map.HashedMap;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.entities.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;


/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@RunWith(SpringRunner.class)
@SpringBootTest
public class RepositoriesTest {

    @Autowired
    LoadFlowResultRepository loadFlowResultRepository;

    @Autowired
    StudyRepository studyRepository;

    @Autowired
    StudyCreationRequestRepository studyCreationRequestRepository;

    @Test
    public void testLoadFlowResultRepository() {
        Map<String, String> metrics = new HashedMap<>();
        metrics.put("key1", "value1");
        metrics.put("key2", "value2");
        LoadFlowResultEntity loadFlowResultEntity = new LoadFlowResultEntity(true, metrics, "logs", new ArrayList<>());

        ComponentResultEntity componentResultEntity1 = new ComponentResultEntity(1, LoadFlowResult.ComponentResult.Status.CONVERGED, 1, "slackBusId", 1.0, loadFlowResultEntity);
        ComponentResultEntity componentResultEntity2 = new ComponentResultEntity(2, LoadFlowResult.ComponentResult.Status.CONVERGED, 1, "slackBusId", 2.0, loadFlowResultEntity);

        loadFlowResultEntity.getComponentResults().add(componentResultEntity1);
        loadFlowResultEntity.getComponentResults().add(componentResultEntity2);

        // save loadFlowResultEntity
        LoadFlowResultEntity savedLoadFlowResultEntity  = loadFlowResultRepository.save(loadFlowResultEntity);

        assertEquals(loadFlowResultEntity.getLogs(), savedLoadFlowResultEntity.getLogs());
        assertEquals(loadFlowResultEntity.getMetrics(), savedLoadFlowResultEntity.getMetrics());
        assertEquals(savedLoadFlowResultEntity.getComponentResults().size(), loadFlowResultEntity.getComponentResults().size());

        // delete loadFlowResultEntity
        loadFlowResultRepository.deleteById(savedLoadFlowResultEntity.getId());
        assertEquals(0, loadFlowResultRepository.findAll().size());
    }

    @Test
    public void testStudyRepository() {
        studyRepository.deleteAll();
        Map<String, String> metrics = new HashedMap<>();
        metrics.put("key1", "value1");
        metrics.put("key2", "value2");
        LoadFlowResultEntity loadFlowResultEntity = new LoadFlowResultEntity(false, metrics, "logs", new ArrayList<>());

        LoadFlowParametersEntity loadFlowParametersEntity = new LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES,
                true, false, true, false, true,
                false, true, false,
                true, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);

        StudyEntity studyEntity1 = StudyEntity.builder()
                .userId("foo")
                .studyName("mystudy")
                .date(ZonedDateTime.now(ZoneId.of("UTC")))
                .networkUuid(UUID.randomUUID())
                .networkId("networkId")
                .description("description")
                .caseFormat("caseFormat")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .isPrivate(true)
                .loadFlowStatus(LoadFlowStatus.RUNNING)
                .loadFlowResult(null)
                .loadFlowParameters(null)
                .securityAnalysisResultUuid(UUID.randomUUID())
                .build();

        StudyEntity studyEntity2 = StudyEntity.builder()
                .userId("foo2")
                .studyName("mystudy2")
                .date(ZonedDateTime.now(ZoneId.of("UTC")))
                .networkUuid(UUID.randomUUID())
                .networkId("networkId2")
                .description("description2")
                .caseFormat("caseFormat2")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .isPrivate(true)
                .loadFlowStatus(LoadFlowStatus.RUNNING)
                .loadFlowResult(null)
                .loadFlowParameters(null)
                .securityAnalysisResultUuid(UUID.randomUUID())
                .build();

        studyRepository.save(studyEntity1);
        studyRepository.save(studyEntity2);
        assertEquals(2, studyRepository.findAll().size());

        StudyEntity savedStudyEntity1 = studyRepository.findAll().get(0);
        StudyEntity savedStudyEntity2 = studyRepository.findAll().get(1);

        assertEquals(studyEntity1.getUserId(), savedStudyEntity1.getUserId());
        assertEquals(studyEntity1.getStudyName(), savedStudyEntity1.getStudyName());

        assertEquals(studyEntity2.getUserId(), savedStudyEntity2.getUserId());
        assertEquals(studyEntity2.getStudyName(), savedStudyEntity2.getStudyName());

        assertTrue(studyRepository.findByUserIdAndStudyName("foo", "mystudy").isPresent());
        assertEquals(1, studyRepository.findAllByUserId("foo").size());

        // updates
        savedStudyEntity1.setLoadFlowResult(loadFlowResultEntity);
        savedStudyEntity1.setLoadFlowParameters(loadFlowParametersEntity);
        studyRepository.save(savedStudyEntity1);

        StudyEntity savedStudyEntity1Updated = studyRepository.findByUserIdAndStudyName("foo", "mystudy").get();
        assertNotNull(savedStudyEntity1Updated.getLoadFlowResult());
        assertNotNull(savedStudyEntity1Updated.getLoadFlowParameters());

        assertEquals(1, loadFlowResultRepository.findAll().size());

        int nbRowsUpdated = studyRepository.updateLoadFlowStatus("mystudy", "foo", LoadFlowStatus.CONVERGED);
        assertEquals(1, nbRowsUpdated);
        savedStudyEntity1Updated = studyRepository.findByUserIdAndStudyName("foo", "mystudy").orElse(null);
        assert savedStudyEntity1Updated != null;
        assertEquals(LoadFlowStatus.CONVERGED, savedStudyEntity1Updated.getLoadFlowStatus());

    }

    @Test
    public void testStudyCreationRequest() {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity("foo", "mystudy", ZonedDateTime.now(ZoneId.of("UTC")));
        studyCreationRequestRepository.save(studyCreationRequestEntity);
        StudyCreationRequestEntity savedStudyCreationRequestEntity = studyCreationRequestRepository.findAll().get(0);
        assertEquals(1, studyCreationRequestRepository.findAll().size());
        assertEquals(savedStudyCreationRequestEntity.getUserId(), savedStudyCreationRequestEntity.getUserId());
        assertEquals(savedStudyCreationRequestEntity.getStudyName(), savedStudyCreationRequestEntity.getStudyName());
        assertEquals(savedStudyCreationRequestEntity.getDate(), savedStudyCreationRequestEntity.getDate());
        assertTrue(studyCreationRequestRepository.findByUserIdAndStudyName("foo", "mystudy").isPresent());
    }

}
