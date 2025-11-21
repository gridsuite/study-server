/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.Setter;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.DIRECTORY_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class DirectoryService {

    private final RestTemplate restTemplate;

    @Setter
    private String directoryServerServerBaseUri;

    @Autowired
    public DirectoryService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.directoryServerServerBaseUri = remoteServicesProperties.getServiceUri("directory-server");
        this.restTemplate = restTemplate;
    }

    public String getElementName(UUID elementUuid) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements/{elementUuid}/name");
        String path = pathBuilder.buildAndExpand(elementUuid).toUriString();
        return restTemplate.getForObject(directoryServerServerBaseUri + path, String.class);
    }

    public void checkWritePermission(UUID elementUuid, String userId) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements");
        pathBuilder.queryParam("accessType", "WRITE");
        pathBuilder.queryParam("ids", List.of(elementUuid));
        String path = pathBuilder.buildAndExpand(elementUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(directoryServerServerBaseUri + path, HttpMethod.HEAD, new HttpEntity<>(headers), Void.class);
    }
}
