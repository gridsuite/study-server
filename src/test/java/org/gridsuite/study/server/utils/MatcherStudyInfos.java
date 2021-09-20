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

import org.gridsuite.study.server.dto.IndexingStatus;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.StudyInfos;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherStudyInfos extends MatcherCreatedStudyBasicInfos<StudyInfos> {

    public static MatcherStudyInfos createMatcherStudyInfos(UUID studyUuid, String studyName, String userId, String caseFormat,
                                                            String description, boolean studyPrivate) {
        return createMatcherStudyInfos(studyUuid, studyName, userId, caseFormat, description, studyPrivate, LoadFlowStatus.NOT_DONE);
    }

    public static MatcherStudyInfos createMatcherStudyInfos(UUID studyUuid, String studyName, String userId, String caseFormat,
                                                            String description, boolean studyPrivate, LoadFlowStatus loadFlowStatus) {
        return new MatcherStudyInfos(StudyInfos.builder()
                .studyUuid(studyUuid)
                .studyName(studyName)
                .userId(userId)
                .caseFormat(caseFormat)
                .description(description)
                .studyPrivate(studyPrivate)
                .indexingStatus(IndexingStatus.DONE)
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
