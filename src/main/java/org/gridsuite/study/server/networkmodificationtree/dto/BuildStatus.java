/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.dto;

import org.gridsuite.study.server.dto.modification.NetworkModificationResult;

import java.util.Collections;
import java.util.List;

/**
 * The order of the enum is important and is used to know if we need to update a status.
 * Values priority is based on their ordinal (higher ordinal => higher priority).
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public enum BuildStatus {
    NOT_BUILT,
    BUILDING,
    BUILT,
    BUILT_WITH_WARNING,
    BUILT_WITH_ERROR;

    public static BuildStatus fromApplicationStatus(NetworkModificationResult.ApplicationStatus status) {
        switch (status) {
            case WITH_ERRORS:
                return BuildStatus.BUILT_WITH_ERROR;
            case WITH_WARNINGS:
                return BuildStatus.BUILT_WITH_WARNING;
            default:
                return BuildStatus.BUILT;
        }
    }

    public static BuildStatus returnHigherSeverityStatus(BuildStatus... buildStatuses) {
        return Collections.max(List.of(buildStatuses));
    }

    public boolean isBuilt() {
        return ordinal() >= BUILT.ordinal();
    }
}
