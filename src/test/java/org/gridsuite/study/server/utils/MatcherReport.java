/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.dto.Report;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Objects;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherReport extends TypeSafeMatcher<Report> {

    Report reference;

    public MatcherReport(Report report) {
        reference = report;
    }

    @Override
    public boolean matchesSafely(Report m) {
        return Objects.equals(reference.message(), m.message()) && Objects.equals(reference.severity(), m.severity());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
