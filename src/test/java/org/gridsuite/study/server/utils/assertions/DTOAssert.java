/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

/**
 *  @author Tristan Chuine <tristan.chuine at rte-france.com>
 *  @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class DTOAssert<T> extends AbstractAssert<DTOAssert<T>, T> {
    public DTOAssert(T actual) {
        super(actual, DTOAssert.class);
    }

    public DTOAssert<T> recursivelyEquals(T other) {
        isNotNull();
        usingRecursiveComparison(this.getRecursiveConfiguration()).isEqualTo(other);
        return myself;
    }

    private RecursiveComparisonConfiguration getRecursiveConfiguration() {
        return RecursiveComparisonConfiguration.builder()
            .withIgnoreAllOverriddenEquals(true)                                    // For equals test, need specific tests
            .withIgnoredFieldsOfTypes(UUID.class, Date.class, ZonedDateTime.class)  // For these types, need specific tests (uuid from db for example)
            .withIgnoreCollectionOrder(true)                                        // For collection order test, need specific tests
            .build();
    }
}
