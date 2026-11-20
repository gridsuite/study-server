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
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class DirectoryService {
    public static final String CASE = "CASE";
    public static final String MODIFICATION = "MODIFICATION";
    public static final String PARAM_IDS = "ids";
    public static final String PARAM_ACCESS_TYPE = "accessType";
    public static final String PARAM_TARGET_DIRECTORY_UUID = "targetDirectoryUuid";
    public static final String PARAM_RECURSIVE_CHECK = "recursiveCheck";
    private final RestClient restClient;

    @Setter
    @Getter
    private String directoryServerServerBaseUri;

    @Autowired
    public DirectoryService(RemoteServicesProperties remoteServicesProperties, RestClient restClient) {
        this.directoryServerServerBaseUri = remoteServicesProperties.getServiceUri("directory-server");
        this.restClient = restClient;
    }

    public String getElementName(UUID elementUuid) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements/{elementUuid}/name");
        String path = pathBuilder.buildAndExpand(elementUuid).toUriString();
        return restClient.get().uri(getDirectoryServerServerBaseUri() + path)
            .retrieve().body(String.class);
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

        try {
            Map<UUID, String> elementNamesMap = restClient.get().uri(getDirectoryServerServerBaseUri() + path)
                .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                .retrieve().body(new ParameterizedTypeReference<Map<UUID, String>>() { });
            return elementNamesMap != null ? elementNamesMap : Map.of();

        } catch (RestClientException e) {
            return Map.of();
        }
    }

    public String getElements(List<UUID> elementUuids, List<String> elementTypes, boolean strictMode, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements")
            .queryParam("ids", elementUuids)
            .queryParam("elementTypes", elementTypes)
            .queryParam("strictMode", strictMode)
            .build().toUriString();
        return restClient.get().uri(getDirectoryServerServerBaseUri() + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
            .retrieve().body(String.class);
    }

    public boolean elementExists(UUID directoryUuid, String elementName, String type) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements/{elementName}/types/{type}");
        String path = pathBuilder.buildAndExpand(directoryUuid, elementName, type).toUriString();

        ResponseEntity<Void> response = restClient.head().uri(getDirectoryServerServerBaseUri() + path)
            .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
            .retrieve().toBodilessEntity();
        return response.getStatusCode() == HttpStatus.OK;
    }

    public void createSharingLinks(List<ModificationReference> references, UUID studyUuid, UUID nodeUuid, String userId) {
        createSharingLinks(references, studyUuid, nodeUuid, false, userId);
    }

    public void createSharingLinks(List<ModificationReference> references, UUID studyUuid, UUID nodeUuid, boolean recreate, String userId) {
        // one call per referenced shared element, carrying all its references
        for (Map.Entry<UUID, List<ReferenceAttributes>> entry : groupReferenceAttributesByReferencedId(references, studyUuid, nodeUuid).entrySet()) {
            createSharingLinks(entry.getKey(), entry.getValue(), recreate, userId);
        }
    }

    public void updateSharingLinks(List<ModificationReference> references, UUID studyUuid, UUID nodeUuid, String userId) {
        // one call per referenced shared element, carrying all its references
        for (Map.Entry<UUID, List<ReferenceAttributes>> entry : groupReferenceAttributesByReferencedId(references, studyUuid, nodeUuid).entrySet()) {
            updateSharingLinks(entry.getKey(), entry.getValue(), userId);
        }
    }

    public void removeSharingLinks(List<ModificationReference> references, String userId) {
        // one call per referenced shared element, carrying all its references
        Map<UUID, List<UUID>> referenceUuidsByReferencedId = new LinkedHashMap<>();
        for (ModificationReference ref : references) {
            referenceUuidsByReferencedId.computeIfAbsent(ref.referencedId(), k -> new ArrayList<>()).add(ref.modificationUuid());
        }
        for (Map.Entry<UUID, List<UUID>> entry : referenceUuidsByReferencedId.entrySet()) {
            removeSharingLinks(entry.getKey(), entry.getValue(), userId);
        }
    }

    private static Map<UUID, List<ReferenceAttributes>> groupReferenceAttributesByReferencedId(List<ModificationReference> references, UUID studyUuid, UUID nodeUuid) {
        Map<UUID, List<ReferenceAttributes>> referenceAttributesByReferencedId = new LinkedHashMap<>();
        for (ModificationReference ref : references) {
            referenceAttributesByReferencedId.computeIfAbsent(ref.referencedId(), k -> new ArrayList<>()).add(toReferenceAttributes(ref, studyUuid, nodeUuid));
        }
        return referenceAttributesByReferencedId;
    }

    /**
     * a modification-reference at the root of the node group is registered as used by the node of the study,
     * one nested in a composite as used by this composite of the node
     */
    private static ReferenceAttributes toReferenceAttributes(ModificationReference ref, UUID studyUuid, UUID nodeUuid) {
        boolean insideComposite = ref.containerId() != null;
        return ReferenceAttributes.createReferenceAttributes(
                ref.modificationUuid(),
                insideComposite ? nodeUuid : studyUuid,
                insideComposite ? ref.containerId() : nodeUuid,
                insideComposite ? ReferenceAttributes.ReferenceType.STUDY_NODE_NETWORK_MODIFICATION
                        : ReferenceAttributes.ReferenceType.STUDY_NODE);
    }

    public void createSharingLinks(@NonNull UUID elementUuid, @NonNull List<ReferenceAttributes> referencesAttributes, boolean recreate, String userId) {

        var path = UriComponentsBuilder.fromPath(
                DELIMITER + DIRECTORY_API_VERSION + DELIMITER + "elements/{elementUuid}/references")
            .queryParam("recreate", recreate)
            .buildAndExpand(elementUuid)
            .toUriString();

        restClient.post().uri(getDirectoryServerServerBaseUri() + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(referencesAttributes)
            .retrieve().toEntity(ElementAttributes.class);
    }

    /**
     * remove references from the shared modification in directory server in a single call
     * @param referenceUuids uuids of the 'Modification references' to remove
     * @param userId id of the user who caused the unreferencing
     * @param sharedElementUuid uuid of the referenced shared element in the directory-server
     */
    public void removeSharingLinks(UUID sharedElementUuid, List<UUID> referenceUuids, String userId) {
        Objects.requireNonNull(referenceUuids);
        Objects.requireNonNull(sharedElementUuid);

        var path = UriComponentsBuilder.fromPath(
                        DELIMITER + DIRECTORY_API_VERSION + DELIMITER + "elements/{elementUuid}/references")
                .queryParam(PARAM_IDS, referenceUuids)
                .buildAndExpand(sharedElementUuid)
                .toUriString();

        restClient.delete()
            .uri(getDirectoryServerServerBaseUri() + path)
            .headers(headers -> {
                headers.set(HEADER_USER_ID, userId);
                headers.setContentType(MediaType.APPLICATION_JSON);
            })
            .retrieve().toEntity(ElementAttributes.class);
    }

    public void createElement(UUID directoryUuid, String description, UUID elementUuid, String elementName, String type, String userId, boolean allowNewName) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements");
        if (allowNewName) {
            pathBuilder.queryParam("allowNewName", true);
        }
        ElementAttributes elementAttributes = new ElementAttributes(elementUuid, elementName, type, userId, 0, description);
        String path = pathBuilder.buildAndExpand(directoryUuid).toUriString();

        restClient.post().uri(getDirectoryServerServerBaseUri() + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(elementAttributes)
            .retrieve().toEntity(ElementAttributes.class);
    }

    public void checkPermission(List<UUID> elementUuids, UUID targetDirectoryUuid, String userId, PermissionType permissionType, boolean recursiveCheck) {

        String path = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements/authorized")
            .queryParam(PARAM_ACCESS_TYPE, permissionType)
            .queryParam(PARAM_IDS, elementUuids)
            .queryParam(PARAM_TARGET_DIRECTORY_UUID, targetDirectoryUuid)
            .queryParam(PARAM_RECURSIVE_CHECK, recursiveCheck)
            .buildAndExpand()
            .toUriString();

        restClient.get().uri(getDirectoryServerServerBaseUri() + path)
            .headers(headers -> headers.add(HEADER_USER_ID, userId))
            .retrieve().toBodilessEntity();
    }

    /**
     * update element sharing links in directory server in a single call, so they point to a new container after a move.
     * the rows to update are identified by {@code elementUuid} (the referenced shared element) and, for each entry,
     * the {@code referenceId} carried by its {@code ReferenceAttributes} (the modification-reference itself); each
     * {@code ReferenceAttributes} also carries the new location.
     * @param elementUuid uuid of the referenced shared element in the directory-server
     * @param referencesAttributes new attributes of the modification-references
     * @param userId id of the user who moves the references
     */
    public void updateSharingLinks(@NonNull UUID elementUuid, @NonNull List<ReferenceAttributes> referencesAttributes, String userId) {

        var path = UriComponentsBuilder.fromPath(
                DELIMITER + DIRECTORY_API_VERSION + DELIMITER + "elements/{elementUuid}/references")
            .buildAndExpand(elementUuid)
            .toUriString();

        restClient.put().uri(getDirectoryServerServerBaseUri() + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(referencesAttributes)
            .retrieve().toBodilessEntity();
    }
}
