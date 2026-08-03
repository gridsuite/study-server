/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.directory;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class DirectoryClient extends AbstractRestClient {
    public DirectoryClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("directory-server"), restTemplate);
    }

    public String getElements(List<UUID> elementUuids, List<String> elementTypes, boolean strictMode, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements")
            .queryParam("ids", elementUuids)
            .queryParam("elementTypes", elementTypes)
            .queryParam("strictMode", strictMode)
            .build().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return getRestTemplate().exchange(getBaseUri() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    public boolean elementExists(UUID directoryUuid, String elementName, String type) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements/{elementName}/types/{type}")
            .buildAndExpand(directoryUuid, elementName, type).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> response = getRestTemplate().exchange(getBaseUri() + path, HttpMethod.HEAD, new HttpEntity<>(headers), Void.class);
        return response.getStatusCode() == HttpStatus.OK;
    }
}
