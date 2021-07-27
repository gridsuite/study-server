/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.powsybl.commons.reporter.ReporterModel;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherReport extends TypeSafeMatcher<ReporterModel> {

    ReporterModel reference;

    public MatcherReport(ReporterModel report) {
        this.reference = report;
    }

    @Override
    public boolean matchesSafely(ReporterModel m) {
        return reference.getTaskKey().equals(m.getTaskKey()) &&
                reference.getDefaultName().equals(m.getDefaultName());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
