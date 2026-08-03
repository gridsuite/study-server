/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ElementAttributes;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.service.client.directory.DirectoryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType.STUDY_NODE;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class DirectoryService {
    public static final String CASE = "CASE";
    public static final String PARAM_IDS = "ids";
    public static final String PARAM_ACCESS_TYPE = "accessType";
    public static final String PARAM_TARGET_DIRECTORY_UUID = "targetDirectoryUuid";
    public static final String PARAM_RECURSIVE_CHECK = "recursiveCheck";

    private final RestTemplate restTemplate;
    private final DirectoryClient directoryClient;

    @Setter
    @Getter
    private String directoryServerServerBaseUri;

    @Autowired
    public DirectoryService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate, DirectoryClient directoryClient) {
        this.directoryServerServerBaseUri = remoteServicesProperties.getServiceUri("directory-server");
        this.restTemplate = restTemplate;
        this.directoryClient = directoryClient;
    }

    public String getElementName(UUID elementUuid) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements/{elementUuid}/name");
        String path = pathBuilder.buildAndExpand(elementUuid).toUriString();
        return restTemplate.getForObject(getDirectoryServerServerBaseUri() + path, String.class);
    }

    public Map<UUID, String> getElementNames(Set<UUID> elementUuids) {
        Objects.requireNonNull(elementUuids);

        if (elementUuids.isEmpty()) {
            return Map.of();
        }

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements/names")
            .queryParam("ids", elementUuids)
            .queryParam("strictMode", "false") // to ignore non existing elements error
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            Map<UUID, String> elementNamesMap = restTemplate.exchange(
                getDirectoryServerServerBaseUri() + path,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<UUID, String>>() { }
            ).getBody();
            return elementNamesMap != null ? elementNamesMap : Map.of();

        } catch (RestClientException e) {
            return Map.of();
        }
    }

    public String getElements(List<UUID> elementUuids, List<String> elementTypes, boolean strictMode, String userId) {
        return directoryClient.getElements(elementUuids, elementTypes, strictMode, userId);
    }

    public boolean elementExists(UUID directoryUuid, String elementName, String type) {
        return directoryClient.elementExists(directoryUuid, elementName, type);
    }

    /**
     * creates references and add them to shared composite modifications stored in directory server
     * @param elementsUuids element uuids of the shared composites in directory server
     * @param userId id of the user who creates the references
     * @param targetNodeUuid where the new references will point
     */
    public void createsReferencesToSharedComposites(@NonNull List<UUID> elementsUuids, String userId, UUID targetNodeUuid) {
        // TODO : instead of multiple calls, an endpoint in directory server should be created to handle multiple references creation
        // OR if not, turn this into simultaneous asynchrone calls
        elementsUuids.forEach(elementUuid -> {
            var path = UriComponentsBuilder.fromPath(
                            DELIMITER + DIRECTORY_API_VERSION + DELIMITER + "elements/{elementUuid}/references")
                    .buildAndExpand(elementUuid)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_USER_ID, userId);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ReferenceAttributes referenceAttributes = new ReferenceAttributes(targetNodeUuid, STUDY_NODE);

            HttpEntity<ReferenceAttributes> requestEntity = new HttpEntity<>(referenceAttributes, headers);
            restTemplate.exchange(getDirectoryServerServerBaseUri() + path, HttpMethod.POST, requestEntity, ElementAttributes.class);
        });
    }

    /**
     * remove reference from the shared modification in directory server
     * @param referenceUuid uuid of the composite or group where the 'Modification reference' is located
     * @param userId id of the user who caused the unreferencing
     * @param sharedElementUuid uuid of the referenced shared element in the directory-server
     */
    public void removeReference(UUID referenceUuid, String userId, UUID sharedElementUuid) {
        Objects.requireNonNull(referenceUuid);
        Objects.requireNonNull(sharedElementUuid);

        var path = UriComponentsBuilder.fromPath(
                        DELIMITER + DIRECTORY_API_VERSION + DELIMITER + "elements/{elementUuid}/references/{referenceUuid}")
                .buildAndExpand(sharedElementUuid, referenceUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
        restTemplate.exchange(getDirectoryServerServerBaseUri() + path, HttpMethod.DELETE, requestEntity, ElementAttributes.class);
    }

    public void createElement(UUID directoryUuid, String description, UUID elementUuid, String elementName, String type, String userId) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements");
        ElementAttributes elementAttributes = new ElementAttributes(elementUuid, elementName, type, userId, 0, description);
        String path = pathBuilder.buildAndExpand(directoryUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ElementAttributes> requestEntity = new HttpEntity<>(elementAttributes, headers);
        restTemplate.exchange(getDirectoryServerServerBaseUri() + path, HttpMethod.POST, requestEntity, ElementAttributes.class);
    }

    public void checkPermission(List<UUID> elementUuids, UUID targetDirectoryUuid, String userId, PermissionType permissionType, boolean recursiveCheck) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HEADER_USER_ID, userId);

        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements/authorized")
            .queryParam(PARAM_ACCESS_TYPE, permissionType)
            .queryParam(PARAM_IDS, elementUuids)
            .queryParam(PARAM_TARGET_DIRECTORY_UUID, targetDirectoryUuid)
            .queryParam(PARAM_RECURSIVE_CHECK, recursiveCheck)
            .buildAndExpand()
            .toUriString();

        restTemplate.exchange(getDirectoryServerServerBaseUri() + path, HttpMethod.GET, new HttpEntity<>(headers), Void.class);
    }
}
