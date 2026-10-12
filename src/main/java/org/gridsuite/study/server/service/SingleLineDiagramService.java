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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class SingleLineDiagramService {

    private final RestClient restClient;

    @Setter
    private String singleLineDiagramServerBaseUri;

    public SingleLineDiagramService(@Value("${powsybl.services.single-line-diagram-server.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
                                    RestClient restClient) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.restClient = restClient;
    }

    public List<String> getAvailableSvgComponentLibraries() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-component-libraries").toUriString();

        return restClient.get().uri(singleLineDiagramServerBaseUri + path)
            .retrieve().body(new ParameterizedTypeReference<List<String>>() {
            });
    }

    private URI buildUriFromPath(String path, String variantId, Object... uriVariables) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(singleLineDiagramServerBaseUri).path(path);

        if (!StringUtils.isBlank(variantId)) {
            builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        return builder.buildAndExpand(uriVariables).toUri();
    }

    public byte[] generateVoltageLevelSvg(UUID networkUuid, String variantId, String voltageLevelId, Map<String, Object> sldRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid, voltageLevelId)
            .toUriString();

        var requestSpec = restClient.post().uri(singleLineDiagramServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (sldRequestInfos != null) {
            requestSpec.body(sldRequestInfos);
        }
        return requestSpec.retrieve().body(byte[].class);
    }

    public String generateVoltageLevelSvgAndMetadata(UUID networkUuid, String variantId, String voltageLevelId, Map<String, Object> sldRequestInfos) {

        URI uri = buildUriFromPath(SINGLE_LINE_DIAGRAM_API_VERSION
                + "/svg-and-metadata/{networkUuid}/{voltageLevelId}", variantId, networkUuid, voltageLevelId);

        var requestSpec = restClient.post().uri(uri)
            .contentType(MediaType.APPLICATION_JSON);
        if (sldRequestInfos != null) {
            requestSpec.body(sldRequestInfos);
        }
        return requestSpec.retrieve().body(String.class);
    }

    public byte[] generateSubstationSvg(UUID networkUuid, String variantId, String substationId, Map<String, Object> sldRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid, substationId).toUriString();

        var requestSpec = restClient.post().uri(singleLineDiagramServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (sldRequestInfos != null) {
            requestSpec.body(sldRequestInfos);
        }
        return requestSpec.retrieve().body(byte[].class);
    }

    public String generateSubstationSvgAndMetadata(UUID networkUuid, String variantId, String substationId, Map<String, Object> sldRequestInfos) {

        URI uri = buildUriFromPath(SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg-and-metadata/{networkUuid}/{substationId}",
                variantId,
                networkUuid, substationId);

        var requestSpec = restClient.post().uri(uri)
            .contentType(MediaType.APPLICATION_JSON);
        if (sldRequestInfos != null) {
            requestSpec.body(sldRequestInfos);
        }
        return requestSpec.retrieve().body(String.class);
    }

    public String generateNetworkAreaDiagram(UUID networkUuid, String variantId, Map<String, Object> nadRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
            "/network-area-diagram/{networkUuid}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid)
            .toUriString();

        var requestSpec = restClient.post().uri(singleLineDiagramServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (nadRequestInfos != null) {
            requestSpec.body(nadRequestInfos);
        }
        return requestSpec.retrieve().body(String.class);
    }

    public void deleteDiagramConfigs(List<UUID> configUuids) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/network-area-diagram/configs")
            .buildAndExpand()
            .toUriString();

        var requestSpec = restClient.method(HttpMethod.DELETE)
            .uri(singleLineDiagramServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (configUuids != null) {
            requestSpec.body(configUuids);
        }
        requestSpec.retrieve().toBodilessEntity();
    }

}
