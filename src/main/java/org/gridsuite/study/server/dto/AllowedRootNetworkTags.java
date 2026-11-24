/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import java.util.List;

/**
 * The root network tags a user may give to a root network.
*/
public record AllowedRootNetworkTags(boolean restricted, List<String> tags) {
    public static final AllowedRootNetworkTags UNRESTRICTED = new AllowedRootNetworkTags(false, List.of());

    public AllowedRootNetworkTags {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
