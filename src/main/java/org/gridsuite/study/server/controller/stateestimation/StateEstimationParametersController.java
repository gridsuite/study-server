/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.stateestimation;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.stateestimation.StateEstimationService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/state-estimation")
public class StateEstimationParametersController {
    private final StateEstimationService stateEstimationService;

    public StateEstimationParametersController(StateEstimationService stateEstimationService) {
        this.stateEstimationService = stateEstimationService;
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return stateEstimationService.downloadDebugFile(resultUuid);
    }
}
