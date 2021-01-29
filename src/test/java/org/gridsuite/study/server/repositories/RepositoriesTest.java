/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowResult;
import org.apache.commons.collections4.map.HashedMap;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.entities.ComponentResultEntity;
import org.gridsuite.study.server.entities.LoadFlowResultEntity;
import org.gridsuite.study.server.entities.StudyCreationRequestEntity;
import org.gridsuite.study.server.entities.StudyEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
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
    ComponentResultRepository componentResultRepository;

    @Autowired
    StudyRepository studyRepository;

    @Autowired
    StudyCreationRequestRepository studyCreationRequestRepository;

    @Autowired
    ObjectMapper objectMapper;

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
        assertEquals(0,         loadFlowResultRepository.findAll().size());
        assertEquals(0,         componentResultRepository.findAll().size());
    }

    @Test
    @Transactional
    public void testStudyRepository() {
        studyRepository.deleteAll();
        Map<String, String> metrics = new HashedMap<>();
        metrics.put("key1", "value1");
        metrics.put("key2", "value2");
        LoadFlowResultEntity loadFlowResultEntity = new LoadFlowResultEntity(false, metrics, "logs", new ArrayList<>());

        StudyEntity studyEntity1 = StudyEntity.builder()
                .userId("chmits")
                .studyName("mystudy")
                .date(LocalDateTime.now())
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
                .userId("chmits2")
                .studyName("mystudy2")
                .date(LocalDateTime.now())
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

        StudyEntity savedStudyEntity1 = studyRepository.findAll().get(0);
        StudyEntity savedStudyEntity2 = studyRepository.findAll().get(1);

        assertEquals(2, studyRepository.findAll().size());

        assertEquals(studyEntity1.getUserId(), savedStudyEntity1.getUserId());
        assertEquals(studyEntity1.getStudyName(), savedStudyEntity1.getStudyName());

        assertEquals(studyEntity2.getUserId(), savedStudyEntity2.getUserId());
        assertEquals(studyEntity2.getStudyName(), savedStudyEntity2.getStudyName());

        assertTrue(studyRepository.findByUserIdAndStudyName("chmits", "mystudy").isPresent());
        assertEquals(1, studyRepository.findAllByUserId("chmits").size());

        // updates
        savedStudyEntity1.setLoadFlowResult(loadFlowResultEntity);
        studyRepository.save(savedStudyEntity1);

        StudyEntity savedStudyEntity1Updated = studyRepository.findByUserIdAndStudyName("chmits", "mystudy").get();
        assertNotNull(savedStudyEntity1Updated.getLoadFlowResult());

        int nb = studyRepository.updateLoadFlowStatus("mystudy", "chmits", LoadFlowStatus.CONVERGED);
        savedStudyEntity1Updated = studyRepository.findByUserIdAndStudyName("chmits", "mystudy").get();
        assertEquals(LoadFlowStatus.CONVERGED, savedStudyEntity1Updated.getLoadFlowStatus());

    }

    @Test
    public void testStudyCreationRequest() {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity("chmits", "mystudy", LocalDateTime.now());
        studyCreationRequestRepository.save(studyCreationRequestEntity);
        StudyCreationRequestEntity savedStudyCreationRequestEntity = studyCreationRequestRepository.findAll().get(0);
        assertEquals(1, studyCreationRequestRepository.findAll().size());
        assertEquals(savedStudyCreationRequestEntity.getUserId(), savedStudyCreationRequestEntity.getUserId());
        assertEquals(savedStudyCreationRequestEntity.getStudyName(), savedStudyCreationRequestEntity.getStudyName());
        assertEquals(savedStudyCreationRequestEntity.getDate(), savedStudyCreationRequestEntity.getDate());

        assertTrue(studyCreationRequestRepository.findByUserIdAndStudyName("chmits", "mystudy").isPresent());

    }

}
