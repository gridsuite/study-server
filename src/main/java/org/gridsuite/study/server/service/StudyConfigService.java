/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.Setter;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.STUDY_CONFIG_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author David BRAQUART <david.braquart at rte-france.com>
 */
@Service
public class StudyConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyConfigService.class);

    private static final String NETWORK_VISU_PARAMETERS_URI = "/network-visualizations-params";
    private static final String NETWORK_VISU_PARAMETERS_WITH_ID_URI = NETWORK_VISU_PARAMETERS_URI + "/{uuid}";

    private static final String SPREADSHEET_CONFIG_COLLECTION_URI = "/spreadsheet-config-collections";
    private static final String SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI = SPREADSHEET_CONFIG_COLLECTION_URI + "/{uuid}";

    private final RestTemplate restTemplate;

    @Setter
    private String studyConfigServerBaseUri;

    @Autowired
    public StudyConfigService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.studyConfigServerBaseUri = remoteServicesProperties.getServiceUri("study-config-server");
        this.restTemplate = restTemplate;
    }

    public void updateNetworkVisualizationParameters(UUID parametersUuid, String parameters) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_WITH_ID_URI);
        String path = uriBuilder.buildAndExpand(parametersUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);
        try {
            restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_NETWORK_VISUALIZATION_PARAMETERS_FAILED);
        }
    }

    public UUID duplicateNetworkVisualizationParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI)
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);
        try {
            return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_NETWORK_VISUALIZATION_PARAMETERS_FAILED);
        }
    }

    public String getNetworkVisualizationParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String parameters;
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_WITH_ID_URI)
                .buildAndExpand(parametersUuid).toUriString();
        try {
            parameters = restTemplate.getForObject(studyConfigServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NETWORK_VISUALIZATION_PARAMETERS_NOT_FOUND);
            }
            throw handleHttpError(e, GET_NETWORK_VISUALIZATION_PARAMETERS_FAILED);
        }
        return parameters;
    }

    public UUID getNetworkVisualizationParametersUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getNetworkVisualizationParametersUuid() == null) {
            studyEntity.setNetworkVisualizationParametersUuid(createDefaultNetworkVisualizationParameters());
        }
        return studyEntity.getNetworkVisualizationParametersUuid();
    }

    public void deleteNetworkVisualizationParameters(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_WITH_ID_URI)
            .buildAndExpand(uuid)
            .toUriString();
        restTemplate.delete(studyConfigServerBaseUri + path);
    }

    public UUID createDefaultNetworkVisualizationParameters() {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI + "/default")
                .buildAndExpand()
                .toUriString();
        UUID parametersUuid;
        try {
            parametersUuid = restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_NETWORK_VISUALIZATION_PARAMETERS_FAILED);
        }
        return parametersUuid;
    }

    public UUID createNetworkVisualizationParameters(String parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI)
                .buildAndExpand()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);
        UUID parametersUuid;
        try {
            parametersUuid = restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_NETWORK_VISUALIZATION_PARAMETERS_FAILED);
        }
        return parametersUuid;
    }

    // Spreadsheet Config Collection
    public UUID duplicateSpreadsheetConfigCollection(UUID sourceUuid) {
        Objects.requireNonNull(sourceUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI)
                .queryParam("duplicateFrom", sourceUuid)
                .buildAndExpand().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);
        try {
            return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DUPLICATE_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
    }

    public String getSpreadsheetConfigCollection(UUID uuid) {
        Objects.requireNonNull(uuid);
        String spreadsheetConfigCollection;
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI)
                .buildAndExpand(uuid).toUriString();
        try {
            spreadsheetConfigCollection = restTemplate.getForObject(studyConfigServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SPREADSHEET_CONFIG_COLLECTION_NOT_FOUND);
            }
            throw handleHttpError(e, GET_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
        return spreadsheetConfigCollection;
    }

    public UUID getSpreadsheetConfigCollectionUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getSpreadsheetConfigCollectionUuid() == null) {
            studyEntity.setSpreadsheetConfigCollectionUuid(createDefaultSpreadsheetConfigCollection());
        }
        return studyEntity.getSpreadsheetConfigCollectionUuid();
    }

    public void deleteSpreadsheetConfigCollection(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI)
            .buildAndExpand(uuid)
            .toUriString();
        restTemplate.delete(studyConfigServerBaseUri + path);
    }

    public UUID createDefaultSpreadsheetConfigCollection() {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI + "/default")
                .buildAndExpand()
                .toUriString();
        UUID uuid;
        try {
            uuid = restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            LOGGER.error("Error while creating spreadsheet default config collection", e);
            return null;
        }
        return uuid;
    }
}
