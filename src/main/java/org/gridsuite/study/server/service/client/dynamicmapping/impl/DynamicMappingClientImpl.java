/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicmapping.impl;

import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;


/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicMappingClientImpl extends AbstractRestClient implements DynamicMappingClient {

    @Autowired
    public DynamicMappingClientImpl(@Value("${backing-services.dynamic-mapping-server.base-uri:http://dynamic-mapping-server/}") String baseUri,
                                    RestTemplate restTemplate) {
        super(baseUri, restTemplate);
    }

    @Override
    public List<MappingInfos> getAllMappings() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_MAPPING_END_POINT_MAPPING);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);

        // call dynamic-mapping REST API
        var responseEntity = getRestTemplate().exchange(uriBuilder.toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<List<MappingInfos>>() { });
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            return responseEntity.getBody();
        } else {
            return Collections.emptyList();
        }
    }
}
