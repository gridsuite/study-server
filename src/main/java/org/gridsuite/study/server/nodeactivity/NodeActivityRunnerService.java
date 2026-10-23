/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Service
public class NodeActivityRunnerService extends AbstractNodeActivityRunnerService {

    public NodeActivityRunnerService(NodeActivityService nodeActivityService) {
        super(nodeActivityService);
    }

    public void runWith(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Runnable action) {
        runWith(type, studyUuid, null, nodeUuids, action);
    }

    public void runBatchWith(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Runnable action) {
        runWith(type, studyUuid, null, nodeUuids, action);
    }
}
