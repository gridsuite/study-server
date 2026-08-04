/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.proxy.EntryPointAuthorization;
import org.gridsuite.study.server.service.proxy.TransparentProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/dynamic-simulation")
public class DynamicSimulationController {
    private static final String DOWNSTREAM_SERVICE = "dynamic-simulation-server";

    private final TransparentProxyService transparentProxyService;
    private final List<EntryPointAuthorization> authorizationChecks;

    public DynamicSimulationController(TransparentProxyService transparentProxyService,
                                       List<EntryPointAuthorization> authorizationChecks) {
        this.transparentProxyService = transparentProxyService;
        this.authorizationChecks = authorizationChecks;
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request,
                                          @RequestBody(required = false) byte[] body) {
        authorizationChecks.forEach(authorization -> authorization.authorize(request));
        return transparentProxyService.forward(DOWNSTREAM_SERVICE, request, body);
    }
}
