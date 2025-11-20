/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicmapping.impl;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicMappingClientImpl extends AbstractRestClient implements DynamicMappingClient {

    public DynamicMappingClientImpl(RemoteServicesProperties remoteServicesProperties,
                                    RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-mapping-server"), restTemplate);
    }

    @Override
    public List<MappingInfos> getAllMappings() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_MAPPING_END_POINT_MAPPING);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + DELIMITER);

        return getRestTemplate().exchange(uriBuilder.toUriString(), HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<MappingInfos>>() { })
                    .getBody();
    }

    @Override
    public List<ModelInfos> getModels(String mapping) {
        if (StringUtils.isBlank(mapping)) {
            return null;
        }

        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_MAPPING_END_POINT_MAPPING);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/{mappingName}/models");

        var uriComponent = uriComponentsBuilder.buildAndExpand(mapping);

        return getRestTemplate().exchange(uriComponent.toUriString(), HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<ModelInfos>>() { })
                    .getBody();
    }
}
