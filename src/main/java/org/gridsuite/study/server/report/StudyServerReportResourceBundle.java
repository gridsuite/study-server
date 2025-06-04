/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.gridsuite.study.server.report;

import com.google.auto.service.AutoService;
import com.powsybl.commons.report.ReportResourceBundle;

/**
 * @author Charly Boutier {@literal <charly.boutier at rte-france.com>}
 */
@AutoService(ReportResourceBundle.class)
public final class StudyServerReportResourceBundle implements ReportResourceBundle {

    public static final String BASE_NAME = "org.gridsuite.study.server.reports";

    public String getBaseName() {
        return BASE_NAME;
    }
}
