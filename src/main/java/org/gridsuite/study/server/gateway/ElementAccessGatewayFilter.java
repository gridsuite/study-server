/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.gateway;

import org.gridsuite.study.server.service.ElementAccessService;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.Map;
import java.util.function.Function;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

@Component
public class ElementAccessGatewayFilter {
    private final ElementAccessService elementAccessService;

    public ElementAccessGatewayFilter(ElementAccessService elementAccessService) {
        this.elementAccessService = elementAccessService;
    }

    public Function<ServerRequest, ServerRequest> checkElementAccess(String pathVariableName) {
        return request -> {
            Map<String, Object> pathVariables = MvcUtils.getUriTemplateVariables(request);
            elementAccessService.checkElementAccess((String) pathVariables.get(pathVariableName), request.headers().firstHeader(HEADER_USER_ID));
            return request;
        };
    }
}
