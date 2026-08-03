/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.networkmodification;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class NetworkModificationClient extends AbstractRestClient {
    public NetworkModificationClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("network-modification-server"), restTemplate);
    }

    public String getLineTypesCatalog() {
        return getRestTemplate().getForObject(getBaseUri() + DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-modifications/catalog/line_types", String.class);
    }

    public String getLineType(UUID lineTypeUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-modifications/catalog/line_types/{uuid}").buildAndExpand(lineTypeUuid).toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, String.class);
    }

    public String getLineTypeWithLimits(UUID lineTypeUuid, String area, String temperature, String shapeFactor) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-modifications/catalog/line_types/{uuid}/with-limits")
            .queryParam("area", area)
            .queryParamIfPresent("temperature", Optional.ofNullable(temperature))
            .queryParamIfPresent("shapeFactor", Optional.ofNullable(shapeFactor))
            .buildAndExpand(lineTypeUuid).toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, String.class);
    }

    public String getNetworkModificationsFromComposite(List<UUID> compositeModificationUuids, boolean onlyMetadata) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-composite-modifications/network-modifications")
            .queryParam(UUIDS, compositeModificationUuids)
            .queryParam("onlyMetadata", onlyMetadata)
            .build().toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, String.class);
    }

    public String getNetworkModification(UUID networkModificationUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-modifications/{uuid}").buildAndExpand(networkModificationUuid).toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, String.class);
    }

    public String getBusBarSectionsForNewCoupler(String voltageLevelId, Integer busBarCount, Integer sectionCount, List<String> switchKindList) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-modifications/busbar-sections-for-new-coupler")
            .queryParam("voltageLevelId", voltageLevelId)
            .queryParam("busBarCount", busBarCount)
            .queryParam("sectionCount", sectionCount)
            .queryParamIfPresent("switchKindList", Optional.ofNullable(switchKindList))
            .build().toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, String.class);
    }

    public void updateNetworkModification(UUID networkModificationUuid, String modificationInfos) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-modifications/{uuid}").buildAndExpand(networkModificationUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        getRestTemplate().exchange(getBaseUri() + path, HttpMethod.PUT, new HttpEntity<>(modificationInfos, headers), Void.class);
    }

    public void updateNetworkModificationsMetadata(List<UUID> networkModificationUuids, String metadata) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MODIFICATION_API_VERSION + "/network-modifications")
            .queryParam(UUIDS, networkModificationUuids).build().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        getRestTemplate().exchange(getBaseUri() + path, HttpMethod.PUT, new HttpEntity<>(metadata, headers), Void.class);
    }
}
