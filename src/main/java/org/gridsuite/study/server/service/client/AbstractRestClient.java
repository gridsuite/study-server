/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client;

import org.gridsuite.study.server.StudyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

import static org.gridsuite.study.server.StudyException.Type.URI_SYNTAX;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public abstract class AbstractRestClient implements RestClient {
    public final Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    private final RestTemplate restTemplate;

    private final String baseUri;

    protected AbstractRestClient(String baseUri, RestTemplate restTemplate) {
        this.baseUri = baseUri;
        this.restTemplate = restTemplate;
    }

    @Override
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    @Override
    public String getBaseUri() {
        return baseUri;
    }

    /**
     * Build endpoint url
     * @param apiVersion for example "v1" or empty
     * @param endPoint root endpoint
     * @return a normalized completed url to endpoint
     */
    protected String buildEndPointUrl(String apiVersion, String endPoint) {
        try {
            String url = new StringBuilder(getBaseUri())
                    .append(DELIMITER)
                    .append(apiVersion)
                    .append(DELIMITER)
                    .append(endPoint)
                    .append(DELIMITER)
                    .toString();
            // normalize before return
            return new URI(url).normalize().toString();
        } catch (URISyntaxException e) {
            throw new StudyException(URI_SYNTAX, e.getMessage());
        }
    }
}
