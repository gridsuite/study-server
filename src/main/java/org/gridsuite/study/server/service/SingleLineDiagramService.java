package org.gridsuite.study.server.service;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyConstants.SINGLE_LINE_DIAGRAM_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.SVG_NOT_FOUND;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.DiagramParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class SingleLineDiagramService {

    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";
    static final String QUERY_PARAM_COMPONENT_LIBRARY = "componentLibrary";
    static final String QUERY_PARAM_USE_NAME = "useName";
    static final String QUERY_PARAM_CENTER_LABEL = "centerLabel";
    static final String QUERY_PARAM_DIAGONAL_LABEL = "diagonalLabel";
    static final String QUERY_PARAM_TOPOLOGICAL_COLORING = "topologicalColoring";
    static final String QUERY_PARAM_SUBSTATION_LAYOUT = "substationLayout";
    static final String QUERY_PARAM_DEPTH = "depth";
    static final String QUERY_PARAM_VOLTAGE_LEVELS_IDS = "voltageLevelsIds";
    static final String RESULT_UUID = "resultUuid";

    @Autowired
    private RestTemplate restTemplate;

    private String singleLineDiagramServerBaseUri;

    public SingleLineDiagramService(@Value("${backing-services.single-line-diagram.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
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
                .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring());
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
            .buildAndExpand(networkUuid, voltageLevelId)
            .toUriString();

        byte[] result;
        try {
            result = restTemplate.getForObject(singleLineDiagramServerBaseUri + path, byte[].class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SVG_NOT_FOUND, "Voltage level " + voltageLevelId + " not found");
            } else {
                throw e;
            }
        }
        return result;
    }
}
