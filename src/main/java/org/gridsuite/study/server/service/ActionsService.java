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

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ContingencyCount;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class ActionsService {

    private final RestClient restClient;

    private static final String NETWORK_UUID = "networkUuid";
    private static final String CONTINGENCY_LIST_IDS = "ids";
    private static final String EMPTY_JSON_ARRAY = "[]";

    public static final ContingencyCount EMPTY_CONTINGENCY_COUNT = new ContingencyCount(Map.of());

    @Setter
    private String actionsServerBaseUri;

    public ActionsService(RemoteServicesProperties remoteServicesProperties, RestClient restClient) {
        this.actionsServerBaseUri = remoteServicesProperties.getServiceUri("actions-server");
        this.restClient = restClient;
    }

    public ContingencyCount getContingencyCount(UUID networkUuid, String variantId, List<UUID> contingencyListIds) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/count")
                .queryParam(CONTINGENCY_LIST_IDS, contingencyListIds)
                .queryParam(NETWORK_UUID, networkUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        return restClient.get().uri(actionsServerBaseUri + uriComponentsBuilder.toUriString())
            .retrieve().body(ContingencyCount.class);
    }

    public List<UUID> getReferencedFilterUuids(Collection<UUID> contingencyListUuids) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/filter-uuids")
                .queryParam(CONTINGENCY_LIST_IDS, contingencyListUuids)
                .toUriString();
        return restClient.get().uri(actionsServerBaseUri + path)
            .retrieve().body(new ParameterizedTypeReference<List<UUID>>() { });
    }

    public String getContingencyLists(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return EMPTY_JSON_ARRAY;
        }
        return restClient.post().uri(actionsServerBaseUri + DELIMITER + ACTIONS_API_VERSION + "/contingency-lists")
            .body(ids)
            .retrieve().body(String.class);
    }
}
