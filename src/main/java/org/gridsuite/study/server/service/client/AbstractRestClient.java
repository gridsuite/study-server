/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Getter
public abstract class AbstractRestClient implements RestClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final RestTemplate restTemplate;

    @Setter
    private String baseUri;

    protected AbstractRestClient(String baseUri, RestTemplate restTemplate) {
        this.baseUri = baseUri;
        this.restTemplate = restTemplate;
    }
}
