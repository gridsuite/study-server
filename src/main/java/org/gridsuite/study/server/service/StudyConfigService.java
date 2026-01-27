/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.Setter;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.DiagramPosition;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayout;
import org.gridsuite.study.server.repository.StudyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.STUDY_CONFIG_API_VERSION;

/**
 * @author David BRAQUART <david.braquart at rte-france.com>
 */
@Service
public class StudyConfigService {
    private static final String UUID_PARAM = "/{uuid}";
    private static final String DUPLICATE_FROM_PARAM = "duplicateFrom";

    private static final String NETWORK_VISU_PARAMETERS_URI = "/network-visualizations-params";
    private static final String NETWORK_VISU_PARAMETERS_WITH_ID_URI = NETWORK_VISU_PARAMETERS_URI + UUID_PARAM;

    private static final String SPREADSHEET_CONFIG_COLLECTION_URI = "/spreadsheet-config-collections";
    private static final String SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI = SPREADSHEET_CONFIG_COLLECTION_URI + UUID_PARAM;

    private static final String SPREADSHEET_CONFIG_URI = "/spreadsheet-configs";
    private static final String SPREADSHEET_CONFIG_WITH_ID_URI = SPREADSHEET_CONFIG_URI + UUID_PARAM;

    private static final String DIAGRAM_GRID_LAYOUT_URI = "/diagram-grid-layout";
    private static final String DIAGRAM_GRID_LAYOUT_WITH_ID_URI = DIAGRAM_GRID_LAYOUT_URI + UUID_PARAM;

    private static final String WORKSPACES_CONFIG_URI = "/workspaces-configs";
    private static final String WORKSPACES_CONFIG_WITH_ID_URI = WORKSPACES_CONFIG_URI + UUID_PARAM;
    private static final String WORKSPACES_URI = "/workspaces";
    private static final String WORKSPACE_WITH_ID_URI = WORKSPACES_URI + "/{workspaceId}";
    private static final String NAME_URI = "/name";
    private static final String WORKSPACE_PANELS_URI = "/panels";
    private static final String DEFAULT_URI = "/default";

    private static final DiagramPosition DEFAULT_DIAGRAM_POSITION = new DiagramPosition(2, 2, 0, 0);

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
        restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
    }

    public UUID duplicateNetworkVisualizationParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI)
                .queryParam(DUPLICATE_FROM_PARAM, sourceParametersUuid)
                .buildAndExpand().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getNetworkVisualizationParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_WITH_ID_URI)
                .buildAndExpand(parametersUuid).toUriString();
        return restTemplate.getForObject(studyConfigServerBaseUri + path, String.class);
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
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI + DEFAULT_URI)
                .buildAndExpand()
                .toUriString();
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
    }

    public UUID createNetworkVisualizationParameters(String parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + NETWORK_VISU_PARAMETERS_URI)
                .buildAndExpand()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    // Spreadsheet Config Collection
    public UUID duplicateSpreadsheetConfigCollection(UUID sourceUuid) {
        Objects.requireNonNull(sourceUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI)
                .queryParam(DUPLICATE_FROM_PARAM, sourceUuid)
                .buildAndExpand().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getSpreadsheetConfigCollection(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI)
                .buildAndExpand(uuid).toUriString();
        return restTemplate.getForObject(studyConfigServerBaseUri + path, String.class);
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
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI + DEFAULT_URI)
                .buildAndExpand()
                .toUriString();
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
    }

    public UUID createSpreadsheetConfigCollection(String configCollection) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_URI)
                .buildAndExpand()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(configCollection, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void updateSpreadsheetConfigCollection(UUID collectionUuid, String configCollection) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI);
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(configCollection, headers);
        restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
    }

    public void appendSpreadsheetConfigCollection(UUID targetCollectionUuid, UUID sourceCollectionUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + "/append");
        String path = uriBuilder.queryParam("sourceCollection", sourceCollectionUuid).buildAndExpand(targetCollectionUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(headers);
        restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
    }

    public UUID addSpreadsheetConfigToCollection(UUID collectionUuid, String configurationDto) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_COLLECTION_WITH_ID_URI + SPREADSHEET_CONFIG_URI);
        String path = uriBuilder.buildAndExpand(collectionUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(configurationDto, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
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
        restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
    }

    public void reorderColumns(UUID configUuid, List<UUID> columnOrder) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/reorder");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<UUID>> httpEntity = new HttpEntity<>(columnOrder, headers);
        restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
    }

    public void updateColumnsStates(UUID configUuid, String columnStateUpdates) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/states");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(columnStateUpdates, headers);
        restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
    }

    public UUID createColumn(UUID configUuid, String columnInfos) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(columnInfos, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void updateColumn(UUID configUuid, UUID columnUuid, String columnInfos) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(columnInfos, headers);
        restTemplate.put(studyConfigServerBaseUri + path, httpEntity);
    }

    public void deleteColumn(UUID configUuid, UUID columnUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        restTemplate.delete(studyConfigServerBaseUri + path);
    }

    public void duplicateColumn(UUID configUuid, UUID columnUuid) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/columns/{colId}/duplicate");
        String path = uriBuilder.buildAndExpand(configUuid, columnUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(null, headers);
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, Void.class);
    }

    public void renameSpreadsheetConfig(UUID configUuid, String newName) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + NAME_URI);
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(newName, headers);
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.PUT, httpEntity, String.class);
    }

    public void updateSpreadsheetConfigSort(UUID configUuid, String sortConfig) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/sort");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(sortConfig, headers);
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.PUT, httpEntity, String.class);
    }

    public void updateSpreadsheetConfig(UUID configUuid, String spreadsheetConfigInfos) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI);
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(spreadsheetConfigInfos, headers);
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.PUT, httpEntity, Void.class);
    }

    public void setGlobalFilters(UUID configUuid, String globalFilters) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/global-filters");
        String path = uriBuilder.buildAndExpand(configUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(globalFilters, headers);
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, Void.class);
    }

    public void resetFilters(UUID configUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + SPREADSHEET_CONFIG_WITH_ID_URI + "/reset-filters")
                .buildAndExpand(configUuid).toUriString();
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.PUT, null, UUID.class);
    }

    public DiagramGridLayout getDiagramGridLayout(UUID diagramGridLayoutUuid) {
        Objects.requireNonNull(diagramGridLayoutUuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + DIAGRAM_GRID_LAYOUT_WITH_ID_URI)
            .buildAndExpand(diagramGridLayoutUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> httpEntity = new HttpEntity<>(null, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.GET, httpEntity, DiagramGridLayout.class).getBody();
    }

    public void deleteDiagramGridLayout(UUID diagramGridLayoutUuid) {
        Objects.requireNonNull(diagramGridLayoutUuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + DIAGRAM_GRID_LAYOUT_WITH_ID_URI)
            .buildAndExpand(diagramGridLayoutUuid).toUriString();

        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.DELETE, null, String.class);
    }

    public UUID saveDiagramGridLayout(DiagramGridLayout diagramGridLayout) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + DIAGRAM_GRID_LAYOUT_URI).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DiagramGridLayout> httpEntity = new HttpEntity<>(diagramGridLayout, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void updateDiagramGridLayout(UUID diagramGridLayoutUuid, DiagramGridLayout diagramGridLayout) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + DIAGRAM_GRID_LAYOUT_WITH_ID_URI)
            .buildAndExpand(diagramGridLayoutUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DiagramGridLayout> httpEntity = new HttpEntity<>(diagramGridLayout, headers);
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.PUT, httpEntity, UUID.class);
    }

    public UUID createGridLayoutFromNadDiagram(UUID sourceNadConfigUuid, UUID clonedNadConfigUuid, String nadDiagramConfigName) {
        if (sourceNadConfigUuid == null) {
            return null;
        }
        Map<String, DiagramPosition> diagramPositions = new HashMap<>();
        diagramPositions.put("lg", DEFAULT_DIAGRAM_POSITION);
        DiagramGridLayout diagramGridLayout = DiagramGridLayout.builder()
            .diagramLayouts(List.of(NetworkAreaDiagramLayout.builder()
                .diagramUuid(UUID.randomUUID())
                .diagramPositions(diagramPositions)
                .originalNadConfigUuid(sourceNadConfigUuid)
                .currentNadConfigUuid(clonedNadConfigUuid)
                .name(nadDiagramConfigName)
                .build()))
            .build();

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + DIAGRAM_GRID_LAYOUT_URI)
            .buildAndExpand()
            .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DiagramGridLayout> httpEntity = new HttpEntity<>(diagramGridLayout, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    // Workspaces Config
    public UUID createDefaultWorkspacesConfig() {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_URI + DEFAULT_URI)
                .toUriString();
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
    }

    public void deleteWorkspacesConfig(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI)
            .buildAndExpand(uuid)
            .toUriString();
        restTemplate.delete(studyConfigServerBaseUri + path);
    }

    public UUID duplicateWorkspacesConfig(UUID sourceUuid) {
        Objects.requireNonNull(sourceUuid);
        var path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_URI)
                .queryParam(DUPLICATE_FROM_PARAM, sourceUuid)
                .toUriString();
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
    }

    // Workspace methods
    public String getWorkspaces(UUID configId) {
        Objects.requireNonNull(configId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACES_URI)
                .buildAndExpand(configId).toUriString();
        return restTemplate.getForObject(studyConfigServerBaseUri + path, String.class);
    }

    public String getWorkspace(UUID configId, UUID workspaceId) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        return restTemplate.getForObject(studyConfigServerBaseUri + path, String.class);
    }

    public void renameWorkspace(UUID configId, UUID workspaceId, String name) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + NAME_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(name, headers);
        restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.PUT, httpEntity, Void.class);
    }

    public String getWorkspacePanels(UUID configId, UUID workspaceId, List<String> panelIds) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI);
        if (panelIds != null && !panelIds.isEmpty()) {
            builder.queryParam("panelIds", panelIds.toArray());
        }
        String path = builder.buildAndExpand(configId, workspaceId).toUriString();
        return restTemplate.getForObject(studyConfigServerBaseUri + path, String.class);
    }

    public String createOrUpdateWorkspacePanels(UUID configId, UUID workspaceId, String panelsDto) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(panelsDto, headers);
        return restTemplate.postForObject(studyConfigServerBaseUri + path, httpEntity, String.class);
    }

    public List<UUID> deleteWorkspacePanels(UUID configId, UUID workspaceId, String panelIds) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI)
                .buildAndExpand(configId, workspaceId).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(panelIds, headers);
        ResponseEntity<List<UUID>> response = restTemplate.exchange(
            studyConfigServerBaseUri + path,
            HttpMethod.DELETE,
            httpEntity,
            new ParameterizedTypeReference<>() { }
        );
        return response.getBody() != null ? response.getBody() : List.of();
    }

    public UUID saveWorkspacePanelNadConfig(UUID configId, UUID workspaceId, UUID panelId, Map<String, Object> nadConfigData) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(panelId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI + "/{panelId}/current-nad-config")
                .buildAndExpand(configId, workspaceId, panelId).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(nadConfigData, headers);
        return restTemplate.exchange(studyConfigServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void deleteWorkspacePanelNadConfig(UUID configId, UUID workspaceId, UUID panelId) {
        Objects.requireNonNull(configId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(panelId);
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_CONFIG_API_VERSION + WORKSPACES_CONFIG_WITH_ID_URI + WORKSPACE_WITH_ID_URI + WORKSPACE_PANELS_URI + "/{panelId}/current-nad-config")
                .buildAndExpand(configId, workspaceId, panelId).toUriString();
        restTemplate.delete(studyConfigServerBaseUri + path);
    }
}
