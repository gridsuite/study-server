/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.QuotaState;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.QuotaConsumptionEntity;
import org.gridsuite.study.server.repository.QuotaConsumptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.USER_ADMIN_API_VERSION;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.MAX_OPERATION_TYPE_EXCEEDED;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Service
public class UserAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAdminService.class);

    private static final String USERS_PROFILE_URI = "/users/{sub}/profile";
    private static final String USERS_QUOTA_URI = "/users/{sub}/quota";
    private static final String USERS_QUOTA_STATE_URI = USERS_QUOTA_URI + "/state";
    private static final String USERS_CONSUME_QUOTA_URI = USERS_QUOTA_URI + "/{operation}/consume";
    private static final String USERS_RELEASE_QUOTA_URI = USERS_QUOTA_URI + "/{quotaId}/release";

    private final RestTemplate restTemplate;
    private final QuotaConsumptionRepository quotaConsumptionRepository;
    private String userAdminServerBaseUri;

    public UserAdminService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate,
                            QuotaConsumptionRepository quotaConsumptionRepository) {
        this.userAdminServerBaseUri = remoteServicesProperties.getServiceUri("user-admin-server");
        this.restTemplate = restTemplate;
        this.quotaConsumptionRepository = quotaConsumptionRepository;
    }

    public void setUserAdminServerBaseUri(String serverBaseUri) {
        this.userAdminServerBaseUri = serverBaseUri;
    }

    public UserProfileInfos getUserProfile(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_PROFILE_URI)
                .buildAndExpand(sub).toUriString();
        return restTemplate.getForObject(userAdminServerBaseUri + path, UserProfileInfos.class);
    }

    public Map<QuotaType, QuotaState> getUserQuotaState(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_QUOTA_STATE_URI)
                .buildAndExpand(sub).toUriString();
        return restTemplate.exchange(
                userAdminServerBaseUri + path,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<QuotaType, QuotaState>>() {
                }).getBody();
    }

    public UUID consumeQuota(String sub, QuotaType quotaType) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_CONSUME_QUOTA_URI)
                .buildAndExpand(sub, quotaType)
                .toUriString();
        try {
            return restTemplate.postForObject(userAdminServerBaseUri + path, null, UUID.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new StudyException(MAX_OPERATION_TYPE_EXCEEDED, "Max number of " + quotaType.name() + " already reached");
        }
    }

    public void releaseQuotaId(String sub, UUID quotaId) {
        if (quotaId == null) {
            return;
        }
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + USERS_RELEASE_QUOTA_URI)
                .buildAndExpand(sub, quotaId)
                .toUriString();
        try {
            restTemplate.postForEntity(userAdminServerBaseUri + path, null, Void.class);
        } catch (Exception e) {
            LOGGER.error("Could not release quota '{}' for user '{}'", quotaId, sub, e);
        }
    }

    @Transactional
    public void registerQuotaConsumption(UUID resultUuid, UUID quotaId) {
        if (quotaId == null) {
            return;
        }
        quotaConsumptionRepository.save(new QuotaConsumptionEntity(resultUuid, quotaId));
    }

    @Transactional
    public void releaseQuota(String sub, UUID resultUuid) {
        quotaConsumptionRepository.findById(resultUuid).ifPresent(mapping -> {
            releaseQuotaId(sub, mapping.getQuotaId());
            quotaConsumptionRepository.delete(mapping);
        });
    }
}
