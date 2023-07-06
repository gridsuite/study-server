/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.dto;

import org.gridsuite.study.server.dto.modification.NetworkModificationResult;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public enum BuildStatus {
    NOT_BUILT(-2),
    BUILDING(-1),
    BUILT(0),
    BUILT_WITH_WARNING(1),
    BUILT_WITH_ERROR(2);

    private final int severityLevel;

    BuildStatus(int severityLevel) {
        this.severityLevel = severityLevel;
    }

    static BuildStatus from(NetworkModificationResult.ApplicationStatus status) {
        switch (status) {
            case WITH_ERRORS:
                return BuildStatus.BUILT_WITH_ERROR;
            case WITH_WARNINGS:
                return BuildStatus.BUILT_WITH_WARNING;
            default:
                return BuildStatus.BUILT;
        }
    }

    public BuildStatus max(BuildStatus other) {
        return severityLevel >= other.severityLevel ? this : other;
    }

    public boolean isNotBuilt() {
        return this.severityLevel == -2;
    }

    public boolean isBuilding() {
        return this.severityLevel == -1;
    }

    public boolean isBuilt() {
        return this.severityLevel >= 0;
    }
}
