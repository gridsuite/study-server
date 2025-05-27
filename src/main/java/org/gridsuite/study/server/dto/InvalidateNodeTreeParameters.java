/**
 * Copyright (c) 2025 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Schema(description = "Invalidation node parameters")
@Builder
public record InvalidateNodeTreeParameters(
    InvalidationMode invalidationMode,
    ComputationsInvalidationMode computationsInvalidationMode // Only for the first node (root node)
) {
    public static InvalidateNodeTreeParameters ALL = new InvalidateNodeTreeParameters(InvalidationMode.ALL, ComputationsInvalidationMode.ALL);
    public static InvalidateNodeTreeParameters ONLY_CHILDREN_BUILD_STATUS = new InvalidateNodeTreeParameters(InvalidationMode.ONLY_CHILDREN_BUILD_STATUS, ComputationsInvalidationMode.ALL);
    public static InvalidateNodeTreeParameters DEFAULT = ALL;

    public enum InvalidationMode {
        ALL, ONLY_CHILDREN, ONLY_CHILDREN_BUILD_STATUS;
    }

    public enum ComputationsInvalidationMode {
        ALL, PRESERVE_VOLTAGE_INIT_RESULTS;
    }

    public boolean isOnlyChildren() {
        return invalidationMode == InvalidationMode.ONLY_CHILDREN;
    }

    public boolean isOnlyChildrenBuildStatusMode() {
        return invalidationMode == InvalidationMode.ONLY_CHILDREN_BUILD_STATUS;
    }
}
