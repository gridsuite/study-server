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
 * What the root network tags naming restrictions are computed from. Sent in the body rather than in the url, as the
 * uuids of a whole study tree would exceed the url length limit and the tags are free text.
 *
 * @param containerUuids groups or composites whose content may refer to shared modifications
 * @param usedTags       tags already used, left out of the allowed tags
 */
public record RootNetworkTagsNamingInfos(List<UUID> containerUuids, List<String> usedTags) {
}
