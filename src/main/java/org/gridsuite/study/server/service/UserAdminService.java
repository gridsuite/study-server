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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.GET_USER_PROFILE_FAILED;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class UserAdminService {
    private static final String USER_PROFILES_URI = "/profiles?sub={sub}";
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

    public UserProfileInfos getUserProfile(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + USER_PROFILES_URI)
            .buildAndExpand(sub).toUriString();
        try {
            List<UserProfileInfos> response = restTemplate.exchange(userAdminServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<UserProfileInfos>>() { }).getBody();
            return CollectionUtils.isEmpty(response) ? null : response.get(0);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, GET_USER_PROFILE_FAILED);
        }
    }
}
