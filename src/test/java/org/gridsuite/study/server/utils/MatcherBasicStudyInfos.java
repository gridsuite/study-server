/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.dto.BasicStudyInfos;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherBasicStudyInfos<T extends BasicStudyInfos> extends TypeSafeMatcher<T> {
    T reference;

    public MatcherBasicStudyInfos(T val) {
        this.reference = val;
    }

    @Override
    public boolean matchesSafely(T s) {
        return reference.getStudyName().equals(s.getStudyName())
                && reference.getUserId().equals(s.getUserId())
                && s.getCreationDate().toEpochSecond() - reference.getCreationDate().toEpochSecond() < 2;
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
