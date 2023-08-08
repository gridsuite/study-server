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

import static org.gridsuite.study.server.StudyConstants.ACTIONS_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.powsybl.contingency.Contingency;

@Service
public class ActionsService {

    @Autowired
    private RestTemplate restTemplate;

    private static final String NETWORK_UUID = "networkUuid";

    private String actionsServerBaseUri;

    @Autowired
    public ActionsService(RemoteServicesProperties remoteServicesProperties) {
        this.actionsServerBaseUri = remoteServicesProperties.getServiceUri("actions-server");
    }

    public Integer getContingencyCount(UUID networkUuid, String variantId, List<String> contingencyListNames) {
        return contingencyListNames.stream().map(contingencyListName -> {
            var uriComponentsBuilder = UriComponentsBuilder
                    .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{contingencyListName}/export")
                    .queryParam(NETWORK_UUID, networkUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(contingencyListName).toUriString();

            List<Contingency> contingencies = restTemplate.exchange(actionsServerBaseUri + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Contingency>>() {
                    }).getBody();

            return contingencies == null ? 0 : contingencies.size();
        }).reduce(0, Integer::sum);
    }

    public void setActionsServerBaseUri(String actionsServerBaseUri) {
        this.actionsServerBaseUri = actionsServerBaseUri;
    }
}
