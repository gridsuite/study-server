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

import org.gridsuite.study.server.dto.BasicStudyInfos;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherBasicStudyInfos<T extends BasicStudyInfos> extends TypeSafeMatcher<T> {

    public static MatcherBasicStudyInfos<BasicStudyInfos> createMatcherStudyBasicInfos(UUID studyUuid) {
        return new MatcherBasicStudyInfos<>(BasicStudyInfos.builder()
                .id(studyUuid)
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .build());
    }

    T reference;

    protected MatcherBasicStudyInfos(T val) {
        this.reference = val;
    }

    @Override
    public boolean matchesSafely(T s) {
        return reference.getId().equals(s.getId())
                && s.getCreationDate().toEpochSecond() - reference.getCreationDate().toEpochSecond() < 2;
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
