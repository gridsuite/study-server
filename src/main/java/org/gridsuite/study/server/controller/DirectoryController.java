/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.DirectoryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/directory")
public class DirectoryController {
    private final DirectoryService directoryService;

    public DirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping(value = "/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getElements(@RequestParam("ids") List<UUID> ids,
                                              @RequestParam(name = "elementTypes", required = false, defaultValue = "") List<String> elementTypes,
                                              @RequestParam(name = "strictMode", defaultValue = "true") boolean strictMode,
                                              @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(directoryService.getElements(ids, elementTypes, strictMode, userId));
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/directories/{directoryUuid}/elements/{elementName}/types/{type}")
    public ResponseEntity<Void> elementExists(@PathVariable UUID directoryUuid,
                                              @PathVariable String elementName,
                                              @PathVariable String type) {
        return directoryService.elementExists(directoryUuid, elementName, type) ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }
}
