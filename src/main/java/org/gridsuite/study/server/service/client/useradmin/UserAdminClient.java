/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.useradmin;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class UserAdminClient extends AbstractRestClient {
    public UserAdminClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("user-admin-server"), restTemplate);
    }

    public ResponseEntity<String> getUserDetail(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + "/users/{sub}/detail").buildAndExpand(sub).toUriString();
        return getRestTemplate().exchange(getBaseUri() + path, HttpMethod.GET, null, String.class);
    }

    public ResponseEntity<String> getCurrentAnnouncement() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + "/announcements/current").toUriString();
        return getRestTemplate().exchange(getBaseUri() + path, HttpMethod.GET, null, String.class);
    }
}
