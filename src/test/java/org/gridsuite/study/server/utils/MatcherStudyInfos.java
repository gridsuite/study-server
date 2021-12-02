/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.StudyInfos;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherStudyInfos extends MatcherCreatedStudyBasicInfos<StudyInfos> {

    public static MatcherStudyInfos createMatcherStudyInfos(UUID studyUuid, String userId, String caseFormat,
                                                            boolean studyPrivate) {
        return createMatcherStudyInfos(studyUuid, userId, caseFormat, studyPrivate, LoadFlowStatus.NOT_DONE);
    }

    public static MatcherStudyInfos createMatcherStudyInfos(UUID studyUuid, String userId, String caseFormat,
                                                            boolean studyPrivate, LoadFlowStatus loadFlowStatus) {
        return new MatcherStudyInfos(StudyInfos.builder()
                .id(studyUuid)
                .userId(userId)
                .caseFormat(caseFormat)
                .studyPrivate(studyPrivate)
                .loadFlowStatus(loadFlowStatus)
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .build());
    }

    protected MatcherStudyInfos(StudyInfos val) {
        super(val);
    }

    @Override
    public boolean matchesSafely(StudyInfos s) {
        return super.matchesSafely(s)
                && reference.getLoadFlowStatus().equals(s.getLoadFlowStatus());
    }
}
