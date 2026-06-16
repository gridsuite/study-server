/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi_externe at rte-france.com>
 */
public record ModificationReceiver(
    UUID studyUuid,
    UUID nodeUuid,
    UUID originNodeUuid,
    List<UUID> rootNetworkUuids
) { }
