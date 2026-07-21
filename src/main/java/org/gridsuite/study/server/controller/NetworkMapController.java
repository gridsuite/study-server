/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.NetworkMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/network-map")
public class NetworkMapController {
    public static final String APPLICATION_JSON_SCHEMA_VALUE = "application/schema+json";

    private final NetworkMapService networkMapService;

    public NetworkMapController(NetworkMapService networkMapService) {
        this.networkMapService = networkMapService;
    }

    @GetMapping(value = "/schemas/{elementType}/{infoType}", produces = APPLICATION_JSON_SCHEMA_VALUE)
    public ResponseEntity<String> getElementSchema(@PathVariable String elementType,
                                                   @PathVariable String infoType) {
        return ResponseEntity.ok().header("Content-Type", APPLICATION_JSON_SCHEMA_VALUE).body(networkMapService.getElementSchema(elementType, infoType));
    }
}
