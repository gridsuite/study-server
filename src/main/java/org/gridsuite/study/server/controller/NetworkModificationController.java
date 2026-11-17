/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION)
public class NetworkModificationController {
    private final NetworkModificationRestService networkModificationRestService;

    public NetworkModificationController(NetworkModificationRestService networkModificationRestService) {
        this.networkModificationRestService = networkModificationRestService;
    }

    @GetMapping(value = "/network-modifications/catalog/line_types")
    @PreAuthorize("true")
    public ResponseEntity<String> getLineTypesCatalog() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationRestService.getLineTypesCatalog());
    }

    @GetMapping(value = "/network-modifications/catalog/line_types/{uuid}")
    @PreAuthorize("true")
    public ResponseEntity<String> getLineType(@PathVariable UUID uuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationRestService.getLineType(uuid));
    }

    @GetMapping(value = "/network-modifications/catalog/line_types/{uuid}/with-limits")
    @PreAuthorize("true")
    public ResponseEntity<String> getLineTypeWithLimits(@PathVariable UUID uuid, @RequestParam String area,
                                                        @RequestParam(required = false) String temperature,
                                                        @RequestParam(required = false) String shapeFactor) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationRestService.getLineTypeWithLimits(uuid, area, temperature, shapeFactor));
    }

    @GetMapping(value = "/network-modifications/{uuid}")
    @PreAuthorize("@authorizationService.canRead(#uuid)")
    public ResponseEntity<String> getNetworkModification(@PathVariable UUID uuid) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(networkModificationRestService.getNetworkModification(uuid, userId));
    }

    @GetMapping(value = "/network-modifications/busbar-sections-for-new-coupler")
    @PreAuthorize("true")
    public ResponseEntity<String> getBusBarSectionsForNewCoupler(@RequestParam String voltageLevelId, @RequestParam Integer busBarCount,
                                                                 @RequestParam Integer sectionCount,
                                                                 @RequestParam(name = "switchKindList", required = false) List<String> switchKindList) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(networkModificationRestService.getBusBarSectionsForNewCoupler(voltageLevelId, busBarCount, sectionCount, switchKindList));
    }

    @PutMapping(value = "/network-modifications/{uuid}")
    @PreAuthorize("@authorizationService.canWrite(#uuid)")
    public ResponseEntity<Void> updateNetworkModification(@PathVariable UUID uuid, @RequestBody String modificationInfos) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        networkModificationRestService.updateNetworkModification(uuid, modificationInfos, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/network-modifications")
    @PreAuthorize("@authorizationService.canWrite(#networkModificationUuids)")
    public ResponseEntity<Void> updateNetworkModificationsMetadata(@RequestParam("uuids") List<UUID> networkModificationUuids, @RequestBody String metadata) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        networkModificationRestService.updateNetworkModificationsMetadata(networkModificationUuids, metadata, userId);
        return ResponseEntity.ok().build();
    }
}
