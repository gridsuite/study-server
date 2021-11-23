/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.google.common.collect.Iterables;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"spring.data.elasticsearch.enabled=false"})
public class StudyInfosServiceMockTests {

    @Autowired
    private StudyInfosService studyInfosService;

    @Test
    public void testAddDeleteStudyInfos() {
        MatcherCreatedStudyBasicInfos<CreatedStudyBasicInfos> matcher = MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos(UUID.fromString("11888888-0000-0000-0000-000000000000"), "userId", "UCTE", false);
        assertThat(studyInfosService.add(matcher.getReference()), matcher);
        assertEquals(0, Iterables.size(studyInfosService.findAll()));
    }
}
