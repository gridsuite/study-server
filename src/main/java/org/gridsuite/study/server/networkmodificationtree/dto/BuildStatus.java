/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.dto;

import org.gridsuite.study.server.dto.modification.NetworkModificationResult;

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
        if (status.equals(NetworkModificationResult.ApplicationStatus.WITH_ERRORS)) {
            return BuildStatus.BUILT_WITH_ERROR;
        } else if (status.equals(NetworkModificationResult.ApplicationStatus.WITH_WARNINGS)) {
            return BuildStatus.BUILT_WITH_WARNING;
        } else {
            return BuildStatus.BUILT;
        }
    }

    public boolean isBuilt() {
        return ordinal() >= 2;
    }
}
