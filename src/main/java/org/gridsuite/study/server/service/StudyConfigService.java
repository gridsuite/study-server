/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.Setter;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.repository.StudyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.STUDY_CONFIG_API_VERSION;

/**
 * @author David BRAQUART <david.braquart at rte-france.com>
 */
@Service
public class StudyConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyConfigService.class);
    private static final String UUID_PARAM = "/{uuid}";

    private static final String NETWORK_VISU_PARAMETERS_URI = "/network-visualizations-params";
    private static final String NETWORK_VISU_PARAMETERS_WITH_ID_URI = NETWORK_VISU_PARAMETERS_URI + UUID_PARAM;

    private static final String SPREADSHEET_CONFIG_COLLECTION_URI = "/spreadsheet-config-collections";
    private static final String SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI = SPREADSHEET_CONFIG_COLLECTION_URI + UUID_PARAM;

    private static final String SPREADSHEET_CONFIG_URI = "/spreadsheet-configs";
    private static final String SPREADSHEET_CONFIG_WITH_ID_URI = SPREADSHEET_CONFIG_URI + UUID_PARAM;

    private static final String WORKSPACES_CONFIG_URI = "/workspaces-configs";
    private static final String WORKSPACES_CONFIG_WITH_ID_URI = WORKSPACES_CONFIG_URI + UUID_PARAM;
    private static final String WORKSPACES_URI = "/workspaces";
    private static final String WORKSPACE_WITH_ID_URI = WORKSPACES_URI + "/{workspaceId}";
    private static final String NAME_URI = "/name";
    private static final String WORKSPACE_PANELS_URI = "/panels";
    private static final String DEFAULT_URI = "/default";
    private static final String COMPUTATION_RESULT_FILTERS_URI = "/computation-result-filters";
    private static final String COMPUTATION_TYPE = "computationType";
    private static final String COMPUTATION_SUB_TYPE = "computationSubType";
    private static final String COMPUTATION_RESULT_FILTERS_ID = "computationResultFiltersId";

    private final RestClient restClient;

    @Setter
    private String studyConfigServerBaseUri;

    @Autowired
    public StudyConfigService(RemoteServicesProperties remoteServicesProperties, RestClient restClient) {
        this.studyConfigServerBaseUri = remoteServicesProperties.getServiceUri("study-config-server");
        this.restClient = restClient;
    }

    public void updateNetworkVisualizationParameters(UUID parametersUuid, String parameters) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_WITH_ID_URI);
        String path = uriBuilder.buildAndExpand(parametersUuid).toUriString();
        var requestSpec = restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (parameters != null) {
            requestSpec.body(parameters);
        }
        requestSpec.retrieve().toBodilessEntity();
    }

    public UUID duplicateNetworkVisualizationParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI + "/{uuid}/duplicate")
                .buildAndExpand(sourceParametersUuid).toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    public String getNetworkVisualizationParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_WITH_ID_URI)
                .buildAndExpand(parametersUuid).toUriString();
        return restClient.get().uri(studyConfigServerBaseUri + path)
            .retrieve().body(String.class);
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
        restClient.delete().uri(studyConfigServerBaseUri + path)
            .retrieve().toBodilessEntity();
    }

    public UUID createDefaultNetworkVisualizationParameters() {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI + DEFAULT_URI)
                .buildAndExpand()
                .toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .retrieve().body(UUID.class);
    }

    public UUID createNetworkVisualizationParameters(String parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI)
                .buildAndExpand()
                .toUriString();
        var requestSpec = restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (parameters != null) {
            requestSpec.body(parameters);
        }
        return requestSpec.retrieve().body(UUID.class);
    }

    // Spreadsheet Config Collection
    public UUID duplicateSpreadsheetConfigCollection(UUID sourceUuid) {
        Objects.requireNonNull(sourceUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI + DELIMITER + sourceUuid + "/duplicate")
                .buildAndExpand().toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    public String getSpreadsheetConfigCollection(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI)
                .buildAndExpand(uuid).toUriString();
        return restClient.get().uri(studyConfigServerBaseUri + path)
            .retrieve().body(String.class);
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
        restClient.delete().uri(studyConfigServerBaseUri + path)
            .retrieve().toBodilessEntity();
    }

    public UUID createDefaultSpreadsheetConfigCollection() {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI + DEFAULT_URI)
                .buildAndExpand()
                .toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .retrieve().body(UUID.class);
    }

    public UUID createSpreadsheetConfigCollection(String configCollection) {
        Objects.requireNonNull(configCollection);
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI)
                .buildAndExpand()
                .toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(configCollection)
            .retrieve().body(UUID.class);
    }

    public void updateSpreadsheetConfigCollection(UUID collectionUuid, String configCollection) {
        Objects.requireNonNull(configCollection);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI);
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(configCollection)
            .retrieve().toBodilessEntity();
    }

    public void appendSpreadsheetConfigCollection(UUID targetCollectionUuid, UUID sourceCollectionUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + "/append");
        String path = uriBuilder.queryParam("sourceCollection", sourceCollectionUuid).buildAndExpand(targetCollectionUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    public UUID addSpreadsheetConfigToCollection(UUID collectionUuid, String configurationDto) {
        Objects.requireNonNull(configurationDto);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + SPREADSHEET_CONFIG_URI);
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(configurationDto)
            .retrieve().body(UUID.class);
    }

    public void removeSpreadsheetConfigFromCollection(UUID collectionUuid, UUID configUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + SPREADSHEET_CONFIG_URI + "/{configId}");
        String path = uriBuilder.buildAndExpand(collectionUuid, configUuid).toUriString();
        restClient.delete().uri(studyConfigServerBaseUri + path)
            .retrieve().toBodilessEntity();
    }

    public void reorderSpreadsheetConfigs(UUID collectionUuid, List<UUID> newOrder) {
        Objects.requireNonNull(newOrder);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + "/reorder");
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(newOrder)
            .retrieve().toBodilessEntity();
    }

    public void reorderColumns(UUID configUuid, List<UUID> columnOrder) {
        Objects.requireNonNull(columnOrder);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/reorder");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(columnOrder)
            .retrieve().toBodilessEntity();
    }

    public void updateColumnsStates(UUID configUuid, String columnStateUpdates) {
        Objects.requireNonNull(columnStateUpdates);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/states");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(columnStateUpdates)
            .retrieve().toBodilessEntity();
    }

    public UUID createColumn(UUID configUuid, String columnInfos) {
        Objects.requireNonNull(columnInfos);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(columnInfos)
            .retrieve().body(UUID.class);
    }

    public void updateColumn(UUID configUuid, UUID columnUuid, String columnInfos) {
        Objects.requireNonNull(columnInfos);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(columnInfos)
            .retrieve().toBodilessEntity();
    }

    public void deleteColumn(UUID configUuid, UUID columnUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        restClient.delete().uri(studyConfigServerBaseUri + path)
            .retrieve().toBodilessEntity();
    }

    public void duplicateColumn(UUID configUuid, UUID columnUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}/duplicate");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    public void renameSpreadsheetConfig(UUID configUuid, String newName) {
        Objects.requireNonNull(newName);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + NAME_URI);
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(newName)
            .retrieve().toEntity(String.class);
    }

    public void updateSpreadsheetConfigSort(UUID configUuid, String sortConfig) {
        Objects.requireNonNull(sortConfig);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/sort");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(sortConfig)
            .retrieve().toEntity(String.class);
    }

    public void updateSpreadsheetConfig(UUID configUuid, String spreadsheetConfigInfos) {
        Objects.requireNonNull(spreadsheetConfigInfos);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI);
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(spreadsheetConfigInfos)
            .retrieve().toBodilessEntity();
    }

    public void setGlobalFilters(UUID configUuid, String globalFilters) {
        Objects.requireNonNull(globalFilters);
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/global-filters");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(globalFilters)
            .retrieve().toBodilessEntity();
    }

    public void resetFilters(UUID configUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/reset-filters")
                .buildAndExpand(configUuid).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .retrieve().toEntity(UUID.class);
    }

    // Workspaces Config
    public UUID createWorkspacesConfigFromWorkspaces(List<UUID> workspaceIds) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_URI);
        if (workspaceIds != null && !workspaceIds.isEmpty()) {
            builder.queryParam("createFrom", workspaceIds.toArray());
        }
        String path = builder.toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .retrieve().body(UUID.class);
    }

    public void deleteWorkspacesConfig(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI)
            .buildAndExpand(uuid)
            .toUriString();
        restClient.delete().uri(studyConfigServerBaseUri + path)
            .retrieve().toBodilessEntity();
    }

    public UUID duplicateWorkspacesConfig(UUID sourceUuid) {
        Objects.requireNonNull(sourceUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_URI + DELIMITER + sourceUuid + "/duplicate")
                .toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .retrieve().body(UUID.class);
    }

    // Workspace methods
    public String getWorkspaces(UUID configId) {
        Objects.requireNonNull(configId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACES_URI)
                .buildAndExpand(configId).toUriString();
        return restClient.get().uri(studyConfigServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public String getWorkspace(UUID configId, UUID workspaceId) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        return restClient.get().uri(studyConfigServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public void renameWorkspace(UUID configId, UUID workspaceId, String name) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + NAME_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(name)
            .retrieve().toBodilessEntity();
    }

    public String getWorkspacePanels(UUID configId, UUID workspaceId, List<String> panelIds) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI);
        if (panelIds != null && !panelIds.isEmpty()) {
            builder.queryParam("panelIds", panelIds.toArray());
        }
        String path = builder.buildAndExpand(configId, workspaceId).toUriString();
        return restClient.get().uri(studyConfigServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public String createOrUpdateWorkspacePanels(UUID configId, UUID workspaceId, String panelsDto) {
        Objects.requireNonNull(panelsDto);
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(panelsDto)
            .retrieve().body(String.class);
    }

    public List<UUID> deleteWorkspacePanels(UUID configId, UUID workspaceId, String panelIds) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        var requestSpec = restClient.method(HttpMethod.DELETE)
            .uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (panelIds != null) {
            requestSpec.body(panelIds);
        }
        ResponseEntity<List<UUID>> response = requestSpec.retrieve().toEntity(new ParameterizedTypeReference<>() { });
        return response.getBody() != null ? response.getBody() : List.of();
    }

    public UUID saveWorkspacePanelNadConfig(UUID configId, UUID workspaceId, UUID panelId, Map<String, Object> nadConfigData) {
        Objects.requireNonNull(nadConfigData);
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(panelId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI
                + "/{panelId}/current-nad-config")
                .buildAndExpand(configId, workspaceId, panelId).toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(nadConfigData)
            .retrieve().body(UUID.class);
    }

    public String getComputationResultGlobalFilters(UUID computationResultFiltersId, String computationType) {
        Objects.requireNonNull(computationResultFiltersId);
        Map<String, Object> uriVariables = Map.of(COMPUTATION_RESULT_FILTERS_ID, computationResultFiltersId, COMPUTATION_TYPE, computationType);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + COMPUTATION_RESULT_FILTERS_URI +
                "/{computationResultFiltersId}/{computationType}").buildAndExpand(uriVariables).toUriString();
        return restClient.get().uri(studyConfigServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public String getComputationResultColumnFilters(UUID computationResultFiltersId, String computationType, String computationSubType) {
        Objects.requireNonNull(computationResultFiltersId);
        Map<String, Object> uriVariables = Map.of(COMPUTATION_RESULT_FILTERS_ID, computationResultFiltersId, COMPUTATION_TYPE, computationType, COMPUTATION_SUB_TYPE, computationSubType);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + COMPUTATION_RESULT_FILTERS_URI +
                "/{computationResultFiltersId}/{computationType}/{computationSubType}").buildAndExpand(uriVariables).toUriString();
        return restClient.get().uri(studyConfigServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public void setGlobalFiltersForComputationResult(UUID computationResultFiltersUuid, String computationType, String globalFilters) {
        Objects.requireNonNull(globalFilters);
        Map<String, Object> uriVariables = Map.of(COMPUTATION_RESULT_FILTERS_ID, computationResultFiltersUuid, COMPUTATION_TYPE, computationType);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + COMPUTATION_RESULT_FILTERS_URI +
                "/{computationResultFiltersId}/{computationType}/global-filters").buildAndExpand(uriVariables).toUriString();
        restClient.post().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(globalFilters)
            .retrieve().toBodilessEntity();
    }

    public void updateColumns(UUID computationResultFiltersId, String computationType, String computationSubType, String columnInfos) {
        Objects.requireNonNull(columnInfos);
        Map<String, Object> uriVariables = Map.of(COMPUTATION_RESULT_FILTERS_ID, computationResultFiltersId, COMPUTATION_TYPE, computationType, COMPUTATION_SUB_TYPE, computationSubType);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + COMPUTATION_RESULT_FILTERS_URI +
                "/{computationResultFiltersId}/{computationType}/{computationSubType}/columns").buildAndExpand(uriVariables).toUriString();
        restClient.put().uri(studyConfigServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(columnInfos)
            .retrieve().toBodilessEntity();
    }

    public UUID createComputationResultsFiltersId() {
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + COMPUTATION_RESULT_FILTERS_URI + DEFAULT_URI)
                .buildAndExpand().toUriString();
        return restClient.post().uri(studyConfigServerBaseUri + path)
            .retrieve().body(UUID.class);
    }

    public UUID createDefaultNetworkVisualizationParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getNetworkVisualizationParameterId() != null) {
            // try to access/duplicate the user profile network visualization parameters
            try {
                return duplicateNetworkVisualizationParameters(userProfileInfos.getNetworkVisualizationParameterId());
            } catch (Exception e) {
                LOGGER.error(String.format("Could not duplicate network visualization parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getNetworkVisualizationParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        try {
            return createDefaultNetworkVisualizationParameters();
        } catch (final Exception e) {
            // TODO try to report a log in Root subreporter ?
            LOGGER.error("Error while creating network visualization default parameters", e);
            return null;
        }
    }

    public UUID createDefaultSpreadsheetConfigCollection(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getSpreadsheetConfigCollectionId() != null) {
            try {
                return duplicateSpreadsheetConfigCollection(userProfileInfos.getSpreadsheetConfigCollectionId());
            } catch (Exception e) {
                LOGGER.error(String.format("Could not duplicate spreadsheet config collection with id '%s' from user/profile '%s/%s'. Using default spreadsheet config collection",
                        userProfileInfos.getSpreadsheetConfigCollectionId(), userId, userProfileInfos.getName()), e);
            }
        }
        try {
            return createDefaultSpreadsheetConfigCollection();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default spreadsheet config collection", e);
            return null;
        }
    }
}
