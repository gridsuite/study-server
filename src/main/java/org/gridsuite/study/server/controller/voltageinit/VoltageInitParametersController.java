/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.voltageinit;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.gridsuite.study.server.service.voltageinit.VoltageInitService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/voltage-init")
public class VoltageInitParametersController {
    private final VoltageInitService voltageInitService;

    public VoltageInitParametersController(VoltageInitService voltageInitService) {
        this.voltageInitService = voltageInitService;
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return voltageInitService.downloadDebugFile(resultUuid);
    }

    @GetMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<VoltageInitParametersInfos> getParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(voltageInitService.getVoltageInitParametersByUuid(parameterUuid));
    }
}
