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

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyConstants.SINGLE_LINE_DIAGRAM_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.SVG_NOT_FOUND;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.DiagramParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
    static final String QUERY_PARAM_SELECTED_VOLTAGE_LEVEL = "selectedVoltageLevel";
    static final String QUERY_PARAM_INIT_WITH_GEO_DATA = "withGeoData";
    static final String QUERY_PARAM_NAD_CONFIG_UUID = "nadConfigUuid";
    static final String NOT_FOUND = " not found";
    static final String QUERY_PARAM_DISPLAY_MODE = "sldDisplayMode";
    static final String LANGUAGE = "language";
    static final String VOLTAGE_LEVEL = "Voltage level ";
    static final String NAD_CONFIG_UUID = "Nad config UUID ";

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

    public String getNetworkAreaDiagram(UUID networkUuid, String variantId, List<String> voltageLevelsIds, String selectedVoltageLevel, int depth, boolean withGeoData) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
                "/network-area-diagram/{networkUuid}")
                .queryParam(QUERY_PARAM_DEPTH, depth)
                .queryParam(QUERY_PARAM_INIT_WITH_GEO_DATA, withGeoData);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (!StringUtils.isBlank(selectedVoltageLevel)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_SELECTED_VOLTAGE_LEVEL, selectedVoltageLevel);
        }
        var path = uriComponentsBuilder
                .buildAndExpand(networkUuid)
                .toUriString();
        String result;
        try {
            result = restTemplate.postForObject(singleLineDiagramServerBaseUri + path, voltageLevelsIds, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, VOLTAGE_LEVEL + voltageLevelsIds + NOT_FOUND);
            } else {
                throw e;
            }
        }
        return result;
    }

    public String getNetworkAreaDiagram(UUID networkUuid, String variantId, UUID nadConfigUuid) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION +
                "/network-area-diagram/{networkUuid}")
                .queryParam(QUERY_PARAM_NAD_CONFIG_UUID, nadConfigUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
                .buildAndExpand(networkUuid)
                .toUriString();
        String result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, NAD_CONFIG_UUID + nadConfigUuid + NOT_FOUND);
            } else {
                throw e;
            }
        }
        return result;
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
}
