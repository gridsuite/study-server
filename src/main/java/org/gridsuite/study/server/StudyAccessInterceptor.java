/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.gridsuite.study.server.service.ElementAccessService;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

@Component
public class StudyAccessInterceptor implements HandlerInterceptor {
    private static final String STUDY_UUID = "studyUuid";

    private final ElementAccessService elementAccessService;

    public StudyAccessInterceptor(ElementAccessService elementAccessService) {
        this.elementAccessService = elementAccessService;
    }

    @Override
    public boolean preHandle(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler) {

        String studyUuid = getPathVariables(request).get(STUDY_UUID);

        if (studyUuid != null) {
            elementAccessService.checkElementAccess(
                studyUuid,
                request.getHeader(HEADER_USER_ID)
            );
        }

        throw new IllegalStateException("Missing studyUuid path variable");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getPathVariables(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return attribute instanceof Map<?, ?> ? (Map<String, String>) attribute : Map.of();
    }
}
