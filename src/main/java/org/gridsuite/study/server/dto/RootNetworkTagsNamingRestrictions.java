/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The restrictions on the root network tags a user may give to a root network.
 *
 * @param restricted  whether some shared modifications the user cannot write on restrict the tags
 * @param allowedTags only when restricted, the only tags allowed; null otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RootNetworkTagsNamingRestrictions(boolean restricted, List<String> allowedTags) {
    public static final RootNetworkTagsNamingRestrictions UNRESTRICTED = new RootNetworkTagsNamingRestrictions(false, null);

    public RootNetworkTagsNamingRestrictions {
        if (restricted) {
            allowedTags = allowedTags == null ? List.of() : List.copyOf(allowedTags);
        } else {
            allowedTags = null;
        }
    }
}
