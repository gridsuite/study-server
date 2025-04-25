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

import java.util.List;
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

    private static final String UUID_PARAM = "/{uuid}";

    private static final String NETWORK_VISU_PARAMETERS_URI = "/network-visualizations-params";
    private static final String NETWORK_VISU_PARAMETERS_WITH_ID_URI = NETWORK_VISU_PARAMETERS_URI + UUID_PARAM;

    private static final String SPREADSHEET_CONFIG_COLLECTION_URI = "/spreadsheet-config-collections";
    private static final String SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI = SPREADSHEET_CONFIG_COLLECTION_URI + UUID_PARAM;

    private static final String SPREADSHEET_CONFIG_URI = "/spreadsheet-configs";
    private static final String SPREADSHEET_CONFIG_WITH_ID_URI = SPREADSHEET_CONFIG_URI + UUID_PARAM;

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
            throw handleHttpError(e, CREATE_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
        return uuid;
    }

    public UUID createSpreadsheetConfigCollection(String configCollection) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI)
                .buildAndExpand()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(configCollection, headers);
        UUID uuid;
        try {
            uuid = restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
        return uuid;
    }

    public void updateSpreadsheetConfigCollection(UUID collectionUuid, String configCollection) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI);
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(configCollection, headers);
        try {
            restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
    }

    public void appendSpreadsheetConfigCollection(UUID targetCollectionUuid, UUID sourceCollectionUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + "/append");
        String path = uriBuilder.queryParam("sourceCollection", sourceCollectionUuid).buildAndExpand(targetCollectionUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(headers);
        try {
            restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
    }

    public UUID addSpreadsheetConfigToCollection(UUID collectionUuid, String configurationDto) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + SPREADSHEET_CONFIG_URI);
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(configurationDto, headers);
        UUID newConfigUuid;
        try {
            newConfigUuid = restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
        return newConfigUuid;
    }

    public void removeSpreadsheetConfigFromCollection(UUID collectionUuid, UUID configUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + SPREADSHEET_CONFIG_URI + "/{configId}");
        String path = uriBuilder.buildAndExpand(collectionUuid, configUuid).toUriString();
        restTemplate.delete(studyConfigServerBaseUri + path);
    }

    public void reorderSpreadsheetConfigs(UUID collectionUuid, List<UUID> newOrder) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + "/reorder");
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<UUID>> httpEntity = new HttpEntity<>(newOrder, headers);
        try {
            restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SPREADSHEET_CONFIG_FAILED);
        }
    }

    public void reorderColumns(UUID configUuid, List<UUID> columnOrder) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/reorder");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<UUID>> httpEntity = new HttpEntity<>(columnOrder, headers);
        try {
            restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SPREADSHEET_CONFIG_FAILED);
        }
    }

    public UUID createColumn(UUID configUuid, String columnInfos) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(columnInfos, headers);
        UUID uuid;
        try {
            uuid = restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SPREADSHEET_CONFIG_COLLECTION_FAILED);
        }
        return uuid;
    }

    public void updateColumn(UUID configUuid, UUID columnUuid, String columnInfos) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(columnInfos, headers);
        try {
            restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SPREADSHEET_CONFIG_FAILED);
        }
    }

    public void deleteColumn(UUID configUuid, UUID columnUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        restTemplate.delete(studyConfigServerBaseUri + path);
    }

    public void renameSpreadsheetConfig(UUID configUuid, String newName) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/name");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(newName, headers);
        try {
            restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.PATCH, httpEntity, String.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SPREADSHEET_CONFIG_FAILED);
        }
    }
}
