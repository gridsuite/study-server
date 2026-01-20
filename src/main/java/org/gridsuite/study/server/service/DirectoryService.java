/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ElementAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.DIRECTORY_API_VERSION;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class DirectoryService {
    public static final String CASE = "CASE";

    private final RestTemplate restTemplate;

    @Setter
    @Getter
    private String directoryServerServerBaseUri;

    @Autowired
    public DirectoryService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.directoryServerServerBaseUri = remoteServicesProperties.getServiceUri("directory-server");
        this.restTemplate = restTemplate;
    }

    public String getElementName(UUID elementUuid) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements/{elementUuid}/name");
        String path = pathBuilder.buildAndExpand(elementUuid).toUriString();
        return restTemplate.getForObject(getDirectoryServerServerBaseUri() + path, String.class);
    }

    public boolean elementExists(UUID directoryUuid, String elementName, String type) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements/{elementName}/types/{type}");
        String path = pathBuilder.buildAndExpand(directoryUuid, elementName, type).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Void> response = restTemplate.exchange(getDirectoryServerServerBaseUri() + path, HttpMethod.HEAD, request, Void.class);
        return response.getStatusCode() == HttpStatus.OK;
    }

    public void createElement(UUID directoryUuid, String description, UUID elementUuid, String elementName, String type, String userId) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements");
        ElementAttributes elementAttributes = new ElementAttributes(elementUuid, elementName, type, userId, 0, description);
        String path = pathBuilder.buildAndExpand(directoryUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("userId", userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ElementAttributes> requestEntity = new HttpEntity<>(elementAttributes, headers);
        restTemplate.exchange(getDirectoryServerServerBaseUri() + path, HttpMethod.POST, requestEntity, ElementAttributes.class);
    }
}
