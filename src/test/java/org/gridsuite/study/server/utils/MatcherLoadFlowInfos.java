/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.dto.LoadFlowInfos;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class MatcherLoadFlowInfos extends TypeSafeMatcher<LoadFlowInfos> {
    LoadFlowInfos reference;

    public MatcherLoadFlowInfos(LoadFlowInfos infos) {
        this.reference = infos;
    }

    @Override
    public boolean matchesSafely(LoadFlowInfos infos) {
        return (reference.getLoadFlowStatus() == null && infos.getLoadFlowStatus() == null) ||
                (reference.getLoadFlowStatus() != null && reference.getLoadFlowStatus().equals(infos.getLoadFlowStatus()));
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
