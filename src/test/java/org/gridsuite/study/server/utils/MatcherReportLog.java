/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.dto.ReportLog;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Objects;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
public class MatcherReportLog extends TypeSafeMatcher<ReportLog> {

    ReportLog reference;

    public MatcherReportLog(ReportLog reportLog) {
        reference = reportLog;
    }

    @Override
    public boolean matchesSafely(ReportLog m) {
        return Objects.equals(reference.message(), m.message()) && Objects.equals(reference.severity(), m.severity()) && Objects.equals(reference.parentId(), m.parentId());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
