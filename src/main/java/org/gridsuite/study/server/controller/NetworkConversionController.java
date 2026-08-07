/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/network-conversion")
public class NetworkConversionController {
    private final NetworkConversionService networkConversionService;

    public NetworkConversionController(NetworkConversionService networkConversionService) {
        this.networkConversionService = networkConversionService;
    }

    @GetMapping(value = "/cases/{caseUuid}/import-parameters")
    public ResponseEntity<String> getCaseImportParameters(@PathVariable UUID caseUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkConversionService.getCaseImportParameters(caseUuid));
    }
}
