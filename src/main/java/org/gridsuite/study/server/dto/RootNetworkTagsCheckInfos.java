/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import java.util.List;
import java.util.UUID;

/**
 * Modifications to check against root network tags.
 */
public record RootNetworkTagsCheckInfos(List<UUID> containerUuids, List<String> tags) {
}
