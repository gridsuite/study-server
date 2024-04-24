/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.GET_USER_PROFILE_FAILED;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class UserAdminService {
    private static final String USERS_PROFILE_URI = "/users/{sub}/profile";
    private final RestTemplate restTemplate;
    private String userAdminServerBaseUri;

    @Autowired
    public UserAdminService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.userAdminServerBaseUri = remoteServicesProperties.getServiceUri("user-admin-server");
        this.restTemplate = restTemplate;
    }

    public void setUserAdminServerBaseUri(String serverBaseUri) {
        this.userAdminServerBaseUri = serverBaseUri;
    }

    public Optional<UserProfileInfos> getUserProfile(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_PROFILE_URI)
            .buildAndExpand(sub).toUriString();
        try {
            return Optional.of(restTemplate.getForObject(userAdminServerBaseUri + path, UserProfileInfos.class));
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty(); // a user may not have a profile
            }
            throw handleHttpError(e, GET_USER_PROFILE_FAILED);
        }
    }
}
