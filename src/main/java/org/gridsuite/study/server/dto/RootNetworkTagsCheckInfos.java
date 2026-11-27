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
 * Data sent to the network modification server to check whether the root network {@code tags} may be applied to the
 * modifications given by {@code containerUuids}, {@code modificationUuids} and {@code sharedUuids}: the check fails
 * when one of the shared modifications they lead to, that the user cannot write on, is not already associated with one
 * of the {@code tags}.
 *
 * @param containerUuids groups or composites: only the shared modifications their content refers to are checked
 * @param modificationUuids modifications: only the shared modifications they and their content refer to are checked
 * @param sharedUuids shared modifications: checked themselves, along with the shared modifications their content refers to
 * @param tags the root network tags to check
 * @param usedTags tags already used by other root networks of the study: not used by the check itself, only left out
 *                 of the allowed tags reported in the error when the check fails, as they cannot be given again
 */
public record RootNetworkTagsCheckInfos(List<UUID> containerUuids, List<UUID> modificationUuids, List<UUID> sharedUuids,
                                        List<String> tags, List<String> usedTags) {
    public RootNetworkTagsCheckInfos(List<UUID> containerUuids, List<UUID> modificationUuids, List<UUID> sharedUuids,
                                     List<String> tags) {
        this(containerUuids, modificationUuids, sharedUuids, tags, List.of());
    }
}
