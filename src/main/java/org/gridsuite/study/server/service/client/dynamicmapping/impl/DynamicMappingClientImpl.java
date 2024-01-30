/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicmapping.impl;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.MessageFormat;
import java.util.List;

import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_MAPPING_NOT_FOUND;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicMappingClientImpl extends AbstractRestClient implements DynamicMappingClient {

    @Autowired
    public DynamicMappingClientImpl(RemoteServicesProperties remoteServicesProperties,
                                    RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-mapping-server"), restTemplate);
    }

    @Override
    public List<MappingInfos> getAllMappings() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_MAPPING_END_POINT_MAPPING);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);

        // call dynamic-mapping REST API
        try {
            var responseEntity = getRestTemplate().exchange(uriBuilder.toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<List<MappingInfos>>() { });
            logger.debug(MessageFormat.format("dynamic-mapping REST API called succesfully {0}", uriBuilder.toUriString()));
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_MAPPING_NOT_FOUND);
            }
            throw e;
        }
    }

    @Override
    public List<ModelInfos> getModels(String mapping) {
        if (StringUtils.isBlank(mapping)) {
            return List.of();
        }

        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_MAPPING_END_POINT_MAPPING);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{mappingName}/models");

        var uriComponent = uriComponentsBuilder.buildAndExpand(mapping);

        // call dynamic-mapping REST API
        try {
            var responseEntity = getRestTemplate().exchange(uriComponent.toUriString(), HttpMethod.GET, null, new ParameterizedTypeReference<List<ModelInfos>>() { });
            logger.debug(MessageFormat.format("dynamic-mapping REST API called succesfully {0}", uriComponent.toUriString()));
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_MAPPING_NOT_FOUND);
            }
            throw e;
        }
    }
}
