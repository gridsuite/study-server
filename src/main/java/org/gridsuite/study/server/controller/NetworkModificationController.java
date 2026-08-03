/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION)
public class NetworkModificationController {
    private final NetworkModificationService networkModificationService;

    public NetworkModificationController(NetworkModificationService networkModificationService) {
        this.networkModificationService = networkModificationService;
    }

    @GetMapping(value = "/network-modifications/catalog/line_types", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getLineTypesCatalog() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationService.getLineTypesCatalog());
    }

    @GetMapping(value = "/network-modifications/catalog/line_types/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getLineType(@PathVariable UUID uuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationService.getLineType(uuid));
    }

    @GetMapping(value = "/network-modifications/catalog/line_types/{uuid}/with-limits", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getLineTypeWithLimits(@PathVariable UUID uuid, @RequestParam String area,
                                                        @RequestParam(required = false) String temperature,
                                                        @RequestParam(required = false) String shapeFactor) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationService.getLineTypeWithLimits(uuid, area, temperature, shapeFactor));
    }

    @GetMapping(value = "/network-composite-modifications/network-modifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNetworkModificationsFromComposite(@RequestParam("uuids") List<UUID> compositeModificationUuids,
                                                                       @RequestParam(name = "onlyMetadata", defaultValue = "true") boolean onlyMetadata) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationService.getNetworkModificationsFromComposite(compositeModificationUuids, onlyMetadata));
    }

    @GetMapping(value = "/network-modifications/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getNetworkModification(@PathVariable UUID uuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationService.getNetworkModification(uuid));
    }

    @GetMapping(value = "/network-modifications/busbar-sections-for-new-coupler", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getBusBarSectionsForNewCoupler(@RequestParam String voltageLevelId, @RequestParam Integer busBarCount,
                                                                 @RequestParam Integer sectionCount,
                                                                 @RequestParam(name = "switchKindList", required = false) List<String> switchKindList) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationService.getBusBarSectionsForNewCoupler(voltageLevelId, busBarCount, sectionCount, switchKindList));
    }

    @PutMapping(value = "/network-modifications/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateNetworkModification(@PathVariable UUID uuid, @RequestBody String modificationInfos) {
        networkModificationService.updateNetworkModification(uuid, modificationInfos);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/network-modifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateNetworkModificationsMetadata(@RequestParam("uuids") List<UUID> networkModificationUuids, @RequestBody String metadata) {
        networkModificationService.updateNetworkModificationsMetadata(networkModificationUuids, metadata);
        return ResponseEntity.ok().build();
    }
}
