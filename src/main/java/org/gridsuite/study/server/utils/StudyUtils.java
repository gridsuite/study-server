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

import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_FILTERS;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_GLOBAL_FILTERS;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
public final class StudyUtils {

    private StudyUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void addPageableToQueryParams(UriComponentsBuilder builder, Pageable pageable) {
        builder.queryParam("page", pageable.getPageNumber()).queryParam("size", pageable.getPageSize());
        addSortToQueryParams(builder, pageable.getSort());
    }

    public static void addSortToQueryParams(UriComponentsBuilder builder, Sort sort) {
        if (sort != null) {
            for (Sort.Order order : sort) {
                builder.queryParam("sort", order.getProperty() + "," + order.getDirection());
            }
        }
    }

    public static void addFiltersToQueryParams(UriComponentsBuilder builder, String filters, String globalFilters) {
        if (filters != null && !filters.isEmpty()) {
            builder.queryParam(QUERY_PARAM_FILTERS, filters);
        }
        if (globalFilters != null && !globalFilters.isEmpty()) {
            builder.queryParam(QUERY_PARAM_GLOBAL_FILTERS, globalFilters);
        }
    }
}
