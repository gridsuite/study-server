/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import org.apache.commons.collections4.map.HashedMap;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    StudyRepository studyRepository;

    @Autowired
    StudyCreationRequestRepository studyCreationRequestRepository;

    @Test
    @Transactional
    public void testStudyRepository() {
        Map<String, String> metrics = new HashedMap<>();
        metrics.put("key1", "value1");
        metrics.put("key2", "value2");
        LoadFlowResultEntity loadFlowResultEntity = new LoadFlowResultEntity(null, false, metrics, "logs", new ArrayList<>());
        LoadFlowResultEntity loadFlowResultEntity2 = new LoadFlowResultEntity(null, false, metrics, "logs2", new ArrayList<>());
        LoadFlowResultEntity loadFlowResultEntity3 = new LoadFlowResultEntity(null, true, metrics, "logs3", new ArrayList<>());

        ComponentResultEmbeddable componentResultEmbeddable1 = new ComponentResultEmbeddable(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 1, "slackBusId", 1.0);
        ComponentResultEmbeddable componentResultEmbeddable2 = new ComponentResultEmbeddable(2, 2, LoadFlowResult.ComponentResult.Status.CONVERGED, 2, "slackBusId", 2.0);

        loadFlowResultEntity.getComponentResults().add(componentResultEmbeddable1);
        loadFlowResultEntity.getComponentResults().add(componentResultEmbeddable2);

        ComponentResultEmbeddable componentResultEmbeddable3 = new ComponentResultEmbeddable(3, 3, LoadFlowResult.ComponentResult.Status.FAILED, 3, "slackBusId", 3.0);
        ComponentResultEmbeddable componentResultEmbeddable4 = new ComponentResultEmbeddable(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 4, "slackBusId", 4.0);

        loadFlowResultEntity2.getComponentResults().add(componentResultEmbeddable3);
        loadFlowResultEntity2.getComponentResults().add(componentResultEmbeddable4);

        ComponentResultEmbeddable componentResultEmbeddable5 = new ComponentResultEmbeddable(3, 3, LoadFlowResult.ComponentResult.Status.FAILED, 5, "slackBusId", 5.0);
        ComponentResultEmbeddable componentResultEmbeddable6 = new ComponentResultEmbeddable(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 6, "slackBusId", 6.0);

        loadFlowResultEntity3.getComponentResults().add(componentResultEmbeddable5);
        loadFlowResultEntity3.getComponentResults().add(componentResultEmbeddable6);

        LoadFlowParametersEntity loadFlowParametersEntity = new LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES,
                true, false, true, false, true,
                false, true, false,
                true, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);

        LoadFlowParametersEntity loadFlowParametersEntity2 = new LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES,
                true, false, true, false, true,
                false, true, false,
                true, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);

        LoadFlowParametersEntity loadFlowParametersEntity3 = new LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES,
                true, false, true, false, true,
                false, true, false,
                true, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);

        StudyEntity studyEntity1 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .userId("foo")
                .studyName("mystudy")
                .date(LocalDateTime.now(ZoneOffset.UTC))
                .networkUuid(UUID.randomUUID())
                .networkId("networkId")
                .description("description")
                .caseFormat("caseFormat")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .isPrivate(true)
                .loadFlowStatus(LoadFlowStatus.RUNNING)
                .loadFlowResult(null)
                .loadFlowParameters(loadFlowParametersEntity)
                .securityAnalysisResultUuid(UUID.randomUUID())
                .build();

        StudyEntity studyEntity2 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .userId("foo2")
                .studyName("mystudy2")
                .date(LocalDateTime.now(ZoneOffset.UTC))
                .networkUuid(UUID.randomUUID())
                .networkId("networkId2")
                .description("description2")
                .caseFormat("caseFormat2")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .isPrivate(false)
                .loadFlowStatus(LoadFlowStatus.RUNNING)
                .loadFlowResult(loadFlowResultEntity2)
                .loadFlowParameters(loadFlowParametersEntity2)
                .securityAnalysisResultUuid(UUID.randomUUID())
                .build();

        StudyEntity studyEntity3 = StudyEntity.builder()
                .id(UUID.randomUUID())
                .userId("foo3")
                .studyName("mystudy3")
                .date(LocalDateTime.now(ZoneOffset.UTC))
                .networkUuid(UUID.randomUUID())
                .networkId("networkId3")
                .description("description3")
                .caseFormat("caseFormat3")
                .caseUuid(UUID.randomUUID())
                .casePrivate(true)
                .isPrivate(true)
                .loadFlowStatus(LoadFlowStatus.RUNNING)
                .loadFlowResult(loadFlowResultEntity3)
                .loadFlowParameters(loadFlowParametersEntity3)
                .securityAnalysisResultUuid(UUID.randomUUID())
                .build();

        studyRepository.save(studyEntity1);
        studyRepository.save(studyEntity2);
        studyRepository.save(studyEntity3);
        assertEquals(3, studyRepository.findAll().size());

        assertEquals(2, studyRepository.findByUserIdOrIsPrivate("foo", true).size());

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
        assertEquals(2, savedStudyEntity1Updated.getLoadFlowResult().getComponentResults().size());
        assertNotNull(savedStudyEntity1Updated.getLoadFlowParameters());

        savedStudyEntity1Updated.setLoadFlowStatus(LoadFlowStatus.CONVERGED);
        studyRepository.save(savedStudyEntity1Updated);
        savedStudyEntity1Updated = studyRepository.findByUserIdAndStudyName("foo", "mystudy").orElse(null);
        assert savedStudyEntity1Updated != null;
        assertEquals(LoadFlowStatus.CONVERGED, savedStudyEntity1Updated.getLoadFlowStatus());

    }

    @Test
    public void testStudyCreationRequest() {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(null, "foo", "mystudy", LocalDateTime.now(ZoneOffset.UTC), true);
        studyCreationRequestRepository.save(studyCreationRequestEntity);
        StudyCreationRequestEntity savedStudyCreationRequestEntity = studyCreationRequestRepository.findAll().get(0);
        assertEquals(1, studyCreationRequestRepository.findAll().size());
        assertEquals(savedStudyCreationRequestEntity.getUserId(), savedStudyCreationRequestEntity.getUserId());
        assertEquals(savedStudyCreationRequestEntity.getStudyName(), savedStudyCreationRequestEntity.getStudyName());
        assertEquals(savedStudyCreationRequestEntity.getDate(), savedStudyCreationRequestEntity.getDate());
        assertTrue(studyCreationRequestRepository.findByUserIdAndStudyName("foo", "mystudy").isPresent());
    }

}
