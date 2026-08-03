/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.pccmin;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.pccmin.PccMinService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/pcc-min")
public class PccMinParametersController {
    private final PccMinService pccMinService;

    public PccMinParametersController(PccMinService pccMinService) {
        this.pccMinService = pccMinService;
    }

    @GetMapping(value = "/parameters/{parameterUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getPccMinParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(pccMinService.getPccMinParametersByUuid(parameterUuid));
    }
}
