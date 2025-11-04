/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.google.common.collect.Iterables;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.elasticsearch.StudyInfosRepository;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfigurationWithTestChannel
class StudyInfosServiceTests {

    private static final UUID STUDY_UUID_1 = UUID.fromString("11888888-0000-0000-0000-111111111111");
    private static final UUID STUDY_UUID_2 = UUID.fromString("11888888-0000-0000-0000-22222222222");

    @Autowired
    private StudyInfosService studyInfosService;

    @Autowired
    private StudyInfosRepository studyInfosRepository;

    @AfterEach
    void setUp() {
        studyInfosRepository.deleteAll();
    }

    @Test
    void testAddDeleteStudyInfos() {
        MatcherCreatedStudyBasicInfos<CreatedStudyBasicInfos> matcher1 = MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos(STUDY_UUID_1);
        MatcherCreatedStudyBasicInfos<CreatedStudyBasicInfos> matcher2 = MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos(STUDY_UUID_2);
        assertThat(studyInfosService.add(matcher1.getReference()), matcher1);
        assertThat(studyInfosService.add(matcher2.getReference()), matcher2);
        assertEquals(2, Iterables.size(studyInfosService.findAll()));

        studyInfosService.deleteByUuid(matcher1.getReference().getId());
        assertEquals(1, Iterables.size(studyInfosService.findAll()));

        studyInfosService.deleteByUuid(matcher2.getReference().getId());
        assertEquals(0, Iterables.size(studyInfosService.findAll()));

        studyInfosService.recreateStudyInfos(matcher1.getReference());
        studyInfosService.recreateStudyInfos(matcher2.getReference());
        assertEquals(2, Iterables.size(studyInfosService.findAll()));
    }

    @Test
    void searchStudyInfos() {
        CreatedStudyBasicInfos studyInfos11 = CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111111")).userId("userId1").build();
        CreatedStudyBasicInfos studyInfos12 = CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111112")).userId("userId1").build();
        CreatedStudyBasicInfos studyInfos21 = CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-22222222221")).userId("userId2").build();
        CreatedStudyBasicInfos studyInfos22 = CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-22222222222")).userId("userId2").build();

        studyInfosService.add(studyInfos11);
        studyInfosService.add(studyInfos12);
        studyInfosService.add(studyInfos21);
        studyInfosService.add(studyInfos22);

        assertEquals(4, studyInfosService.search("*").size());

        Set<CreatedStudyBasicInfos> hits = new HashSet<>(studyInfosService.search("userId:(userId1)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(studyInfos11));
        assertTrue(hits.contains(studyInfos12));

        hits = new HashSet<>(studyInfosService.search("userId:(userId2)"));
        assertEquals(2, hits.size());
        assertTrue(hits.contains(studyInfos21));
        assertTrue(hits.contains(studyInfos22));

        studyInfosService.deleteByUuid(studyInfos11.getId());
        studyInfosService.deleteByUuid(studyInfos12.getId());
        studyInfosService.deleteByUuid(studyInfos21.getId());
        studyInfosService.deleteByUuid(studyInfos22.getId());
        assertEquals(0, Iterables.size(studyInfosService.findAll()));
    }
}
