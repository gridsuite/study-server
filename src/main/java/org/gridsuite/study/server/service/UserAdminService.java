/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.service.client.useradmin.UserAdminClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.USER_ADMIN_API_VERSION;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class UserAdminService {
    private static final String USERS_PROFILE_URI = "/users/{sub}/profile";
    private static final String USERS_QUOTA_URI = "/users/{sub}/quota";
    private static final String USERS_MAX_QUOTA_URI = USERS_QUOTA_URI + "/max";
    private static final String USERS_CURRENT_QUOTA_URI = USERS_QUOTA_URI + "/current";
    private static final String USERS_START_QUOTA_URI = USERS_QUOTA_URI + "/{operation}/{operation_id}/start";
    private static final String USERS_END_QUOTA_URI = USERS_QUOTA_URI + "/{operation}/{operation_id}/end";

    private final RestTemplate restTemplate;
    private final UserAdminClient userAdminClient;
    private String userAdminServerBaseUri;

    public UserAdminService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate, UserAdminClient userAdminClient) {
        this.userAdminServerBaseUri = remoteServicesProperties.getServiceUri("user-admin-server");
        this.restTemplate = restTemplate;
        this.userAdminClient = userAdminClient;
    }

    public void setUserAdminServerBaseUri(String serverBaseUri) {
        this.userAdminServerBaseUri = serverBaseUri;
    }

    public UserProfileInfos getUserProfile(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_PROFILE_URI)
                .buildAndExpand(sub).toUriString();
        return restTemplate.getForObject(userAdminServerBaseUri + path, UserProfileInfos.class);
    }

    public Map<QuotaType, Integer> getUserMaxQuota(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_MAX_QUOTA_URI)
                .buildAndExpand(sub).toUriString();
        return restTemplate.exchange(
                userAdminServerBaseUri + path,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<QuotaType, Integer>>() {
                }).getBody();
    }

    public Map<QuotaType, Integer> getUserCurrentQuota(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_CURRENT_QUOTA_URI)
                .buildAndExpand(sub).toUriString();
        return restTemplate.exchange(
                userAdminServerBaseUri + path,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<QuotaType, Integer>>() {
                }).getBody();
    }

    public void startOperationWithQuota(String sub, QuotaType quotaType, UUID operationId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_START_QUOTA_URI)
                .buildAndExpand(sub, quotaType, operationId)
                .toUriString();
        restTemplate.postForEntity(userAdminServerBaseUri + path, null, Void.class);
    }

    public void endOperationWithQuota(String sub, QuotaType quotaType, UUID operationId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_END_QUOTA_URI)
                .buildAndExpand(sub, quotaType, operationId)
                .toUriString();
        restTemplate.postForEntity(userAdminServerBaseUri + path, null, Void.class);
    }

    public ResponseEntity<String> getUserDetail(String sub) {
        return userAdminClient.getUserDetail(sub);
    }

    public ResponseEntity<String> getCurrentAnnouncement() {
        return userAdminClient.getCurrentAnnouncement();
    }

}
