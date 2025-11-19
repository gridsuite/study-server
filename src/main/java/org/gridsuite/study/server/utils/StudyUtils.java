/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
public final class StudyUtils {

    private StudyUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void addPageableToQueryParams(UriComponentsBuilder builder, Pageable pageable) {
        builder.queryParam("page", pageable.getPageNumber()).queryParam("size", pageable.getPageSize());
        for (Sort.Order order : pageable.getSort()) {
            builder.queryParam("sort", order.getProperty() + "," + order.getDirection());
        }
    }
}
