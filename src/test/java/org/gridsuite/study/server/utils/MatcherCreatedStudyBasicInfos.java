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

import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherCreatedStudyBasicInfos<T extends CreatedStudyBasicInfos> extends MatcherBasicStudyInfos<T> {

    public static MatcherCreatedStudyBasicInfos<CreatedStudyBasicInfos> createMatcherCreatedStudyBasicInfos(UUID studyUuid, String studyName, String userId,
                                                                                                            String caseFormat, String description, boolean studyPrivate) {
        return new MatcherCreatedStudyBasicInfos<>(CreatedStudyBasicInfos.builder()
                .studyUuid(studyUuid)
                .studyName(studyName)
                .userId(userId)
                .caseFormat(caseFormat)
                .studyPrivate(studyPrivate)
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .description(description)
                .build());
    }

    protected MatcherCreatedStudyBasicInfos(T val) {
        super(val);
    }

    @Override
    public boolean matchesSafely(T s) {
        return super.matchesSafely(s)
                && reference.getCaseFormat().equals(s.getCaseFormat())
                && reference.getDescription().equals(s.getDescription())
                && reference.isStudyPrivate() == s.isStudyPrivate();
    }
}
