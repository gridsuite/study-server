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

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.DiagramParameters;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayoutDetails;
import org.gridsuite.study.server.dto.diagramgridlayout.nad.NadConfigInfos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SingleLineDiagramService {

    static final String QUERY_PARAM_COMPONENT_LIBRARY = "componentLibrary";
    static final String QUERY_PARAM_USE_NAME = "useName";
    static final String QUERY_PARAM_CENTER_LABEL = "centerLabel";
    static final String QUERY_PARAM_DIAGONAL_LABEL = "diagonalLabel";
    static final String QUERY_PARAM_TOPOLOGICAL_COLORING = "topologicalColoring";
    static final String QUERY_PARAM_SUBSTATION_LAYOUT = "substationLayout";
    static final String QUERY_PARAM_DEPTH = "depth";
    static final String QUERY_PARAM_INIT_WITH_GEO_DATA = "withGeoData";
    static final String QUERY_PARAM_ELEMENT_PARAMS = "elementParams";
    static final String NOT_FOUND = " not found";
    static final String QUERY_PARAM_DISPLAY_MODE = "sldDisplayMode";
    static final String LANGUAGE = "language";
    static final String VOLTAGE_LEVEL = "Voltage level ";
    static final String ELEMENT = "Element";

    private final RestTemplate restTemplate;

    private String singleLineDiagramServerBaseUri;

    public SingleLineDiagramService(@Value("${powsybl.services.single-line-diagram-server.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
                                    RestTemplate restTemplate) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.restTemplate = restTemplate;
    }

    public List<String> getAvailableSvgComponentLibraries() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-component-libraries").toUriString();

        return restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.GET, null,
            new ParameterizedTypeReference<List<String>>() {
            }).getBody();
    }

    public byte[] getVoltageLevelSvg(UUID networkUuid, String variantId, String voltageLevelId, DiagramParameters diagramParameters) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg/{networkUuid}/{voltageLevelId}")
            .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
            .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
            .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
            .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
            .queryParam(LANGUAGE, diagramParameters.getLanguage());
        addParameters(diagramParameters, uriComponentsBuilder, variantId);

        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid, voltageLevelId)
            .toUriString();

        byte[] result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + path, byte[].class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, VOLTAGE_LEVEL + voltageLevelId + NOT_FOUND);
            } else {
                throw e;
            }
        }
        return result;
    }

    public String getVoltageLevelSvgAndMetadata(UUID networkUuid, String variantId, String voltageLevelId, DiagramParameters diagramParameters) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION
                + "/svg-and-metadata/{networkUuid}/{voltageLevelId}")
            .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
            .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
            .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
            .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
            .queryParam(QUERY_PARAM_DISPLAY_MODE, diagramParameters.getSldDisplayMode())
            .queryParam(LANGUAGE, diagramParameters.getLanguage());
        addParameters(diagramParameters, uriComponentsBuilder, variantId);

        String result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + uriComponentsBuilder.build().toUriString(), String.class, networkUuid, voltageLevelId);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, VOLTAGE_LEVEL + voltageLevelId + NOT_FOUND);
            } else {
                throw e;
            }
        }
        return result;
    }

    public byte[] getSubstationSvg(UUID networkUuid, String variantId, String substationId, DiagramParameters diagramParameters, String substationLayout) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}")
            .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
            .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
            .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
            .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
            .queryParam(QUERY_PARAM_SUBSTATION_LAYOUT, substationLayout);
        addParameters(diagramParameters, uriComponentsBuilder, variantId);
        var path = uriComponentsBuilder.buildAndExpand(networkUuid, substationId).toUriString();

        byte[] result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + path, byte[].class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, "Substation " + substationId + NOT_FOUND);
            } else {
                throw e;
            }
        }
        return result;
    }

    public String getSubstationSvgAndMetadata(UUID networkUuid, String variantId, String substationId, DiagramParameters diagramParameters, String substationLayout) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg-and-metadata/{networkUuid}/{substationId}")
            .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
            .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
            .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
            .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
            .queryParam(QUERY_PARAM_SUBSTATION_LAYOUT, substationLayout)
            .queryParam(LANGUAGE, diagramParameters.getLanguage());
        addParameters(diagramParameters, uriComponentsBuilder, variantId);

        String result;
        try {
            result = restTemplate.getForEntity(singleLineDiagramServerBaseUri + uriComponentsBuilder.build().toUriString(), String.class, networkUuid, substationId).getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, "Substation " + substationId + NOT_FOUND);
            } else {
                throw e;
            }
        }
        return result;
    }

    public String getNetworkAreaDiagram(UUID networkUuid, String variantId, String nadRequestInfos) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
            "/network-area-diagram/{networkUuid}");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid)
            .toUriString();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(nadRequestInfos, headers);

        try {
            return restTemplate.postForObject(singleLineDiagramServerBaseUri + path, request, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, VOLTAGE_LEVEL + NOT_FOUND);
            } else {
                throw e;
            }
        }
    }

    public UUID createDiagramConfig(NetworkAreaDiagramLayoutDetails nadLayoutDetails) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/network-area-diagram/config")
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<NetworkAreaDiagramLayoutDetails> httpEntity = new HttpEntity<>(nadLayoutDetails, headers);

        return restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void createMultipleDiagramConfigs(List<NadConfigInfos> nadConfigs) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/network-area-diagram/configs")
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<NadConfigInfos>> httpEntity = new HttpEntity<>(nadConfigs, headers);

        restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.POST, httpEntity, Void.class);
    }

    public void deleteMultipleDiagramConfigs(List<UUID> configUuids) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/network-area-diagram/configs")
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<UUID>> httpEntity = new HttpEntity<>(configUuids, headers);

        restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.DELETE, httpEntity, Void.class);
    }

    public void setSingleLineDiagramServerBaseUri(String singleLineDiagramServerBaseUri) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
    }

    public void addParameters(DiagramParameters diagramParameters, UriComponentsBuilder uriComponentsBuilder, String variantId) {
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
    }

    public UUID duplicateNadConfig(UUID sourceNadConfigUuid) {
        Objects.requireNonNull(sourceNadConfigUuid);

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/network-area-diagram/config")
            .queryParam("duplicateFrom", sourceNadConfigUuid)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<NetworkAreaDiagramLayoutDetails> httpEntity = new HttpEntity<>(headers);

        try {
            return restTemplate.postForObject(singleLineDiagramServerBaseUri + path, httpEntity, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DUPLICATE_DIAGRAM_GRID_LAYOUT_FAILED);
        }
    }
}
