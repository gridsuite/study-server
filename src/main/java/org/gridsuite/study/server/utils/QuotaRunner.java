/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.service.StudyService;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
public final class QuotaRunner {

    private QuotaRunner() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void runComputationWithQuota(
            StudyService studyService,
            ComputationType computationType,
            String userId,
            Consumer<UUID> computation) {
        UUID quotaId = studyService.consumeQuota(computationType, userId);
        boolean succeeded = false;
        try {
            computation.accept(quotaId);
            succeeded = true;
        } finally {
            if (!succeeded) {
                studyService.releaseQuotaOnFailure(userId, quotaId);
            }
        }
    }
}
