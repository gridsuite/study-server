/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Service
public class ScheduledNodeActivityCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledNodeActivityCleaner.class);

    private final NodeActivityService nodeActivityService;
    private final Duration abandonedAfter;

    public ScheduledNodeActivityCleaner(NodeActivityService nodeActivityService,
                                        @Value("${study.node-activity.abandoned-after:PT30M}") Duration abandonedAfter) {
        this.nodeActivityService = nodeActivityService;
        this.abandonedAfter = abandonedAfter;
    }

    @Scheduled(cron = "${study.cron.node-activity-cleanup:-}", zone = "UTC")
    public void removeAbandonedNodeActivities() {
        int released = nodeActivityService.removeAbandonedNodeActivities(Instant.now().minus(abandonedAfter));
        if (released > 0) {
            LOGGER.warn("Released {} node activities started more than {} ago", released, abandonedAfter);
        }
    }
}
