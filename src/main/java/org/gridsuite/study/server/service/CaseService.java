/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import org.gridsuite.study.server.StudyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyException.Type.CASE_NOT_FOUND;

@Service
public class CaseService {

    private String caseServerBaseUri;

    private RestTemplate restTemplate;

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseService.class);

    @Autowired
    public CaseService(@Value("${powsybl.services.case-server.base-uri:http://case-server/}") String caseServerBaseUri, RestTemplate restTemplate) {
        this.caseServerBaseUri = caseServerBaseUri;
        this.restTemplate = restTemplate;
    }

    public Boolean caseExists(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/exists")
            .buildAndExpand(caseUuid)
            .toUriString();

        return restTemplate.exchange(caseServerBaseUri + path, HttpMethod.GET, null, Boolean.class, caseUuid).getBody();
    }

    /**
     * Sometimes we set an expiration delay to a newly created case, to be sure it will be removed if it's not used later.
     * Once the study creation is successful, we want to remove this expiration delay, to make the associated case persist.
     */
    public void disableCaseExpiration(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/disableExpiration")
                .buildAndExpand(caseUuid)
                .toUriString();

        restTemplate.exchange(caseServerBaseUri + path, HttpMethod.PUT, null, Void.class, caseUuid);
    }

    public void deleteCase(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}")
                .buildAndExpand(caseUuid)
                .toUriString();

        try {
            restTemplate.exchange(caseServerBaseUri + path, HttpMethod.DELETE, null, Void.class, caseUuid);
        } catch (RestClientException e) {
            LOGGER.error(String.format("Error while deleting case '%s' : %s", caseUuid, e.getMessage()), e);
        }
    }

    public UUID duplicateCase(UUID caseUuid, Boolean withExpiration) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/duplicate")
                .queryParam("withExpiration", withExpiration)
                .buildAndExpand(caseUuid)
                .toUriString();

        return restTemplate.exchange(caseServerBaseUri + path, HttpMethod.POST, null, UUID.class, caseUuid).getBody();
    }

    public void assertCaseExists(UUID caseUuid) {
        if (Boolean.FALSE.equals(caseExists(caseUuid))) {
            throw new StudyException(CASE_NOT_FOUND, "The case '" + caseUuid + "' does not exist");
        }
    }

    public void setCaseServerBaseUri(String caseServerBaseUri) {
        this.caseServerBaseUri = caseServerBaseUri;
    }
}
