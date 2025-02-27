/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import java.util.UUID;

import org.gridsuite.study.server.dto.StudyInfos;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherStudyInfos extends MatcherCreatedStudyBasicInfos<StudyInfos> {

    public static MatcherStudyInfos createMatcherStudyInfos(UUID studyUuid) {
        return new MatcherStudyInfos(StudyInfos.builder()
                .id(studyUuid)
                .build());
    }

    protected MatcherStudyInfos(StudyInfos val) {
        super(val);
    }

    @Override
    public boolean matchesSafely(StudyInfos s) {
        return super.matchesSafely(s);
    }
}
