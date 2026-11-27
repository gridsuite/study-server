/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.loadflow.LoadFlowParameters;
import lombok.NonNull;
import org.gridsuite.filter.globalfilter.GlobalFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.ModificationsSearchResultByNode;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;
import org.gridsuite.study.server.dto.networkexport.NodeExportInfos;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisRestService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitRestService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.gridsuite.study.server.service.voltageinit.VoltageInitRestService;
import org.gridsuite.study.server.utils.ElementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.BUS_ID_TO_ICC_VALUES;
import static org.gridsuite.study.server.StudyConstants.CURRENT_LIMIT_VIOLATIONS_INFOS;
import static org.gridsuite.study.server.dto.ComputationType.*;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.*;
import static org.gridsuite.study.server.service.StudyService.STUDY_NOT_FOUND;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class RootNetworkStudyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootNetworkStudyService.class);

    private final RootNetworkService rootNetworkService;
    private final StudyRepository studyRepository;
    private final NotificationService notificationService;
    private final NetworkModificationRestService networkModificationRestService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final CaseService caseService;
    private final NetworkConversionService networkConversionService;
    private final NetworkService networkStoreService;
    private final SingleLineDiagramService singleLineDiagramService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final ShortCircuitRestService shortCircuitRestService;
    private final LoadFlowRestService loadflowRestService;
    private final NetworkMapService networkMapService;
    private final GeoDataService geoDataService;
    private final LoadFlowService loadFlowService; //TODO : remove from here if possible
    private final FilterService filterService;
    private final ObjectMapper objectMapper;
    private final ActionsService actionsService;
    private final SecurityAnalysisRestService securityAnalysisRestService;
    private final EquipmentInfosService equipmentInfosService;
    private final VoltageInitRestService voltageInitRestService;
    private final DirectoryService directoryService;
    private final ReportService reportService;

    public RootNetworkStudyService(RootNetworkService rootNetworkService,
                                   StudyRepository studyRepository,
                                   NotificationService notificationService,
                                   NetworkModificationRestService networkModificationRestService,
                                   NetworkModificationTreeService networkModificationTreeService,
                                   CaseService caseService,
                                   NetworkConversionService networkConversionService,
                                   NetworkService networkStoreService,
                                   SingleLineDiagramService singleLineDiagramService,
                                   RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                   ShortCircuitRestService shortCircuitRestService,
                                   LoadFlowRestService loadflowRestService,
                                   NetworkMapService networkMapService,
                                   GeoDataService geoDataService,
                                   LoadFlowService loadFlowService,
                                   FilterService filterService,
                                   ObjectMapper objectMapper,
                                   ActionsService actionsService,
                                   SecurityAnalysisRestService securityAnalysisRestService,
                                   EquipmentInfosService equipmentInfosService,
                                   VoltageInitRestService voltageInitRestService,
                                   DirectoryService directoryService,
                                   ReportService reportService) {
        this.rootNetworkService = rootNetworkService;
        this.studyRepository = studyRepository;
        this.notificationService = notificationService;
        this.networkModificationRestService = networkModificationRestService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.caseService = caseService;
        this.networkConversionService = networkConversionService;
        this.networkStoreService = networkStoreService;
        this.singleLineDiagramService = singleLineDiagramService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.shortCircuitRestService = shortCircuitRestService;
        this.loadflowRestService = loadflowRestService;
        this.networkMapService = networkMapService;
        this.geoDataService = geoDataService;
        this.loadFlowService = loadFlowService;
        this.filterService = filterService;
        this.objectMapper = objectMapper;
        this.actionsService = actionsService;
        this.securityAnalysisRestService = securityAnalysisRestService;
        this.equipmentInfosService = equipmentInfosService;
        this.voltageInitRestService = voltageInitRestService;
        this.directoryService = directoryService;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public List<BasicRootNetworkInfos> getAllBasicRootNetworkInfos(UUID studyUuid) {
        return Stream
                .concat(
                        getExistingRootNetworkInfos(studyUuid).stream(),
                        rootNetworkService.geRootNetworkRequests(studyUuid).stream().filter(
                                s -> s.getActionRequest() == RootNetworkAction.ROOT_NETWORK_CREATION).map(RootNetworkRequestEntity::toBasicDto))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BasicRootNetworkInfos> getExistingBasicRootNetworkInfos(UUID studyUuid) {
        return getExistingRootNetworkInfos(studyUuid);
    }

    private List<BasicRootNetworkInfos> getExistingRootNetworkInfos(UUID studyUuid) {
        return rootNetworkService.getStudyRootNetworks(studyUuid).stream().map(RootNetworkEntity::toBasicDto).toList();
    }

    private void assertIsStudyExist(UUID studyUuid) {
        if (!studyRepository.existsById(studyUuid)) {
            throw new StudyException(NOT_FOUND, STUDY_NOT_FOUND);
        }
    }

    private StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, STUDY_NOT_FOUND));
    }

    private List<UUID> getStudyModificationGroupUuids(UUID studyUuid) {
        return networkModificationTreeService.getAllNetworkModificationNodeInfos(studyUuid).stream()
                .map(NetworkModificationNodeInfoEntity::getModificationGroupUuid)
                .toList();
    }

    @Transactional
    public void deleteRootNetworks(UUID studyUuid, List<UUID> rootNetworksUuids, String userId) {
        assertIsStudyExist(studyUuid);
        StudyEntity studyEntity = getStudy(studyUuid);
        List<RootNetworkEntity> allRootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);
        if (rootNetworksUuids.size() >= allRootNetworkEntities.size()) {
            throw new StudyException(ROOT_NETWORK_DELETE_FORBIDDEN);
        }
        if (!allRootNetworkEntities.stream().map(RootNetworkEntity::getId).collect(Collectors.toSet()).containsAll(rootNetworksUuids)) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        notificationService.emitRootNetworksDeletionStarted(studyUuid, rootNetworksUuids);

        List<String> deletedTags = allRootNetworkEntities.stream()
                .filter(rootNetworkEntity -> rootNetworksUuids.contains(rootNetworkEntity.getId()))
                .map(RootNetworkEntity::getTag)
                .filter(Objects::nonNull)
                .toList();

        rootNetworkService.deleteRootNetworks(studyEntity, rootNetworksUuids.stream());

        networkModificationRestService.deleteRootNetworkTags(getStudyModificationGroupUuids(studyUuid), deletedTags);

        notificationService.emitRootNetworksUpdated(studyUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    /**
     * Recreates study network from <caseUuid> and <importParameters>
     * @param caseUuid
     * @param userId
     * @param studyUuid
     * @param importParameters
     */
    public void recreateNetwork(UUID caseUuid, String userId, UUID studyUuid, UUID rootNetworkUuid, String caseFormat, Map<String, Object> importParameters) {
        RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().caseInfos(new CaseInfos(caseUuid,
                caseUuid, null, caseFormat)).id(rootNetworkUuid).build();
        recreateNetwork(rootNetworkInfos, studyUuid, userId, importParameters, false, UUID.randomUUID());
    }

    /**
     * Recreates study network from existing case and import parameters
     * @param userId
     * @param studyUuid
     */
    public void recreateNetwork(String userId, UUID studyUuid, UUID rootNetworkUuid, String caseFormat) {
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        if (rootNetwork.getLoadStatus() == RootNetworkLoadStatus.LOADING) {
            LOGGER.warn("Root network '{}' is already loading, skipping recreateNetwork", rootNetworkUuid);
            return;
        }
        rootNetworkService.updateNetworkLoadStatus(rootNetworkUuid, RootNetworkLoadStatus.LOADING);
        UUID caseUuid = rootNetwork.getCaseUuid();
        UUID originalCaseUuid = rootNetwork.getOriginalCaseUuid();
        RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().id(rootNetworkUuid).caseInfos(new CaseInfos(caseUuid, originalCaseUuid, null, caseFormat)).build();

        recreateNetwork(rootNetworkInfos, studyUuid, userId, null, true, rootNetwork.getReportUuid());
    }

    private void recreateNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String userId, Map<String, Object> importParameters, boolean shouldLoadPreviousImportParameters, UUID reportId) {
        caseService.assertCaseExists(rootNetworkInfos.getCaseInfos().getCaseUuid());
        Map<String, Object> importParametersToUse = shouldLoadPreviousImportParameters
                ? new HashMap<>(rootNetworkService.getImportParameters(rootNetworkInfos.getId()))
                : importParameters;

        persistNetwork(rootNetworkInfos, studyUuid, null, userId, importParametersToUse, CaseImportAction.NETWORK_RECREATION, reportId);
    }

    private void persistNetwork(RootNetworkInfos rootNetworkInfos, UUID studyUuid, String variantId, String userId,
                                Map<String, Object> importParameters, CaseImportAction caseImportAction, UUID reportId) {
        networkConversionService.persistNetwork(rootNetworkInfos, studyUuid, variantId, userId, reportId, importParameters, caseImportAction);
    }

    @Transactional
    public RootNetworkIndexationStatus getRootNetworkIndexationStatus(UUID studyUuid, UUID rootNetworkUuid) {
        StudyEntity study = getStudy(studyUuid);
        RootNetworkEntity rootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found"));
        if (rootNetwork.getIndexationStatus() == RootNetworkIndexationStatus.INDEXED
                && !networkConversionService.checkStudyIndexationStatus(rootNetworkService.getNetworkUuid(rootNetworkUuid))) {
            updateRootNetworkIndexationStatus(study, rootNetwork, RootNetworkIndexationStatus.NOT_INDEXED);
        }
        return rootNetwork.getIndexationStatus();
    }

    private StudyEntity updateRootNetworkIndexationStatus(StudyEntity studyEntity, RootNetworkEntity rootNetworkEntity, RootNetworkIndexationStatus indexationStatus) {
        rootNetworkEntity.setIndexationStatus(indexationStatus);
        notificationService.emitRootNetworkIndexationStatusChanged(studyEntity.getId(), rootNetworkEntity.getId(), indexationStatus);
        return studyEntity;
    }

    public byte[] generateVoltageLevelSvg(String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.generateVoltageLevelSvg(networkUuid, variantId, voltageLevelId, populateSldRequestInfos(sldRequestInfos, voltageLevelId, nodeUuid, rootNetworkUuid));
        } else {
            return null;
        }
    }

    public String generateVoltageLevelSvgAndMetadata(String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            return singleLineDiagramService.generateVoltageLevelSvgAndMetadata(networkUuid, variantId, voltageLevelId, populateSldRequestInfos(sldRequestInfos, voltageLevelId, nodeUuid,
                    rootNetworkUuid));
        } else {
            return null;
        }
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId,
                                                                        String busPath) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return networkMapService.getVoltageLevelBusesOrBusbarSections(networkUuid, variantId, voltageLevelId, busPath);
    }

    public String getVoltageLevelTopologyInfos(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId,
                                               String path) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return networkMapService.getVoltageLevelTopologyInfos(networkUuid, variantId, voltageLevelId, path);
    }

    public String getVoltageLevelSubstationId(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        return networkMapService.getVoltageLevelSubstationId(networkUuid, variantId, voltageLevelId);
    }

    public String getLinesGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> linesIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getLinesGraphics(networkUuid, variantId, linesIds);
    }

    @Transactional
    public String getNetworkElementsInfosFromFilters(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, EquipmentType equipmentType, String infoType, List<UUID> filterUuids) {
        // Get the requested info for the filtered equipment ids
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, true);
        StudyEntity studyEntity = getStudy(studyUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        // Get the list of equipment ids that match the filter
        List<String> equipmentIds = filterService.evaluateFiltersToNetworkElementIds(networkUuid, filterUuids, variantId);
        LoadFlowParameters loadFlowParameters = loadFlowService.getCommonParameters(studyEntity);

        return networkMapService.getElementsInfosByIds(
                networkUuid,
                variantId,
                String.valueOf(equipmentType),
                infoType,
                getOptionalParameters(String.valueOf(equipmentType), studyEntity, loadFlowParameters),
                equipmentIds
        );
    }

    private static Map<String, String> getOptionalParameters(String elementType, StudyEntity studyEntity, LoadFlowParameters loadFlowParameters) {
        Map<String, String> additionalParameters = new HashMap<>();
        additionalParameters.put(InfoTypeParameters.QUERY_PARAM_DC_POWERFACTOR, String.valueOf(loadFlowParameters.getDcPowerFactor()));
        switch (elementType.toLowerCase()) {
            case "branch" -> additionalParameters.put(
                    InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                    String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBranchOperationalLimitGroup()));
            case "line" -> {
                additionalParameters.put(
                        InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadLineOperationalLimitGroup()));
                additionalParameters.put(
                        InfoTypeParameters.QUERY_PARAM_LOAD_BUS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadLineBus()));
            }
            case "two_windings_transformer" -> additionalParameters.put(
                    InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                    String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadTwtOperationalLimitGroup()));
            case "generator" -> {
                additionalParameters.put(
                        InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadGeneratorRegulatingTerminal()));
                additionalParameters.put(
                        InfoTypeParameters.QUERY_PARAM_LOAD_BUS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadGeneratorBus()));
            }
            case "battery" -> additionalParameters.put(
                    InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                    String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBatteryRegulatingTerminal()));
            case "bus" -> additionalParameters.put(
                    InfoTypeParameters.QUERY_PARAM_LOAD_NETWORK_COMPONENTS,
                    String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBusNetworkComponents()));
        }
        return additionalParameters;
    }

    @Transactional
    public String getNetworkElementInfos(UUID studyUuid,
                                         UUID nodeUuid,
                                         UUID rootNetworkUuid,
                                         String elementType,
                                         InfoTypeParameters infoTypeParameters,
                                         String elementId,
                                         boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = loadFlowService.getCommonParameters(studyEntity);
        return networkMapService.getElementInfos(
                rootNetworkService.getNetworkUuid(rootNetworkUuid),
                networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                elementType,
                infoTypeParameters.getInfoType(),
                getSingleElementOptionalParameters(elementId, elementType, studyEntity, nodeUuid, rootNetworkUuid, loadFlowParameters),
                elementId);
    }

    private Map<String, String> getSingleElementOptionalParameters(String elementId, String elementType, StudyEntity studyEntity, UUID nodeUuid, UUID rootNetworkUuid,
                                                                   LoadFlowParameters loadFlowParameters) {
        Map<String, String> additionalParameters = getOptionalParameters(elementType, studyEntity, loadFlowParameters);

        if ("voltage_level".equalsIgnoreCase(elementType)) {
            try {
                additionalParameters.put(
                        InfoTypeParameters.QUERY_PARAM_BUS_ID_TO_ICC_VALUES,
                        UriUtils.encode(objectMapper.writeValueAsString(getBusIdToIccValuesMap(elementId, nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8)
                );
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        return additionalParameters;
    }

    @Transactional
    public String getNetworkElementsInfos(UUID studyUuid,
                                          UUID nodeUuid,
                                          UUID rootNetworkUuid,
                                          List<String> substationsIds,
                                          String infoType,
                                          String elementType,
                                          boolean inUpstreamBuiltParentNode,
                                          List<Double> nominalVoltages) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = loadFlowService.getCommonParameters(studyEntity);
        return networkMapService.getElementsInfos(
                rootNetworkService.getNetworkUuid(rootNetworkUuid),
                networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                substationsIds,
                elementType,
                nominalVoltages,
                infoType,
                getOptionalParameters(elementType, studyEntity, loadFlowParameters));
    }

    public String getHvdcLineShuntCompensators(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String hvdcId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return networkMapService.getHvdcLineShuntCompensators(networkUuid, variantId, hvdcId);
    }

    public String getSubstationsGraphics(UUID networkUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return geoDataService.getSubstationsGraphics(networkUuid, variantId, substationsIds);
    }

    public String getNetworkElementsIds(UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds, boolean inUpstreamBuiltParentNode, String equipmentType, List<Double> nominalVoltages) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getElementsIds(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                substationsIds, equipmentType, nominalVoltages);
    }

    public String getVoltageLevelTopologyInfos(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode, String path) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelTopologyInfos(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId, path);
    }

    public String getVoltageLevelSubstationId(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelSubstationId(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId);
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID nodeUuid, UUID rootNetworkUuid, String voltageLevelId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return getVoltageLevelBusesOrBusbarSections(nodeUuidToSearchIn, rootNetworkUuid, voltageLevelId, "buses-or-busbar-sections");
    }

    private Map<String, Object> populateSldRequestInfos(Map<String, Object> sldRequestInfos, String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid) {
        List<CurrentLimitViolationInfos> violations = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
        Map<String, Double> busIdToIccValues = getBusIdToIccValuesMap(voltageLevelId, nodeUuid, rootNetworkUuid);
        sldRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, violations);
        sldRequestInfos.put(BUS_ID_TO_ICC_VALUES, busIdToIccValues);
        return sldRequestInfos;
    }

    public String getNetworkNominalVoltages(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getNominalVoltages(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid));
    }

    public String getBranchOr3WTVoltageLevelId(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String equipmentId, ThreeSides side) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return networkMapService.getBranchOr3WTVoltageLevelId(networkUuid, variantId, equipmentId, side);
    }

    public String getVoltageLevelEquipments(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String voltageLevelId) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        String equipmentPath = "voltage-levels" + StudyConstants.DELIMITER + voltageLevelId + StudyConstants.DELIMITER + "equipments";
        return networkMapService.getEquipmentsMapData(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid),
                null, equipmentPath);
    }

    @Transactional
    public RootNetworkRequestInfos createRootNetworkRequest(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        rootNetworkService.assertCanCreateRootNetwork(studyUuid, rootNetworkInfos.getName(), rootNetworkInfos.getTag());
        if (rootNetworkInfos.getTag() != null) {
            assertCanAddRootNetworkTag(studyUuid, rootNetworkInfos.getTag(), userId);
        }
        StudyEntity studyEntity = getStudy(studyUuid);

        rootNetworkInfos.setId(UUID.randomUUID());
        RootNetworkRequestEntity rootNetworkCreationRequestEntity = rootNetworkService.insertCreationRequest(studyEntity.getId(), rootNetworkInfos, userId);
        try {
            UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), true);
            rootNetworkInfos.getCaseInfos().setCaseUuid(clonedCaseUuid);
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParameters(), CaseImportAction.ROOT_NETWORK_CREATION, UUID.randomUUID());
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkCreationRequestEntity);
            throw e;
        }

        notificationService.emitRootNetworksUpdated(studyUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
        return rootNetworkCreationRequestEntity.toDto();
    }

    @Transactional
    public void updateRootNetworkRequest(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        rootNetworkService.assertCanModifyRootNetwork(studyUuid, rootNetworkInfos.getId(), rootNetworkInfos.getName(), rootNetworkInfos.getTag());
        String newTag = rootNetworkInfos.getTag();
        if (newTag != null && !newTag.equals(rootNetworkService.getRootNetworkTag(rootNetworkInfos.getId()))) {
            assertCanAddRootNetworkTag(studyUuid, newTag, userId);
        }
        StudyEntity studyEntity = getStudy(studyUuid);

        if (rootNetworkInfos.hasCaseToImport()) {
            RootNetworkRequestEntity requestEntity = rootNetworkService.insertModificationRequest(studyEntity.getId(), rootNetworkInfos, userId);
            updateRootNetworkCaseInfos(studyEntity.getId(), rootNetworkInfos, userId, requestEntity);
        } else {
            updateRootNetworkBasicInfos(studyEntity.getId(), rootNetworkInfos, false, userId);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void modifyRootNetwork(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId) {
        handleInvalidateStudyRootNetwork(studyUuid, rootNetworkInfos.getId(), userId, true);
        updateRootNetworkBasicInfos(studyUuid, rootNetworkInfos, true, userId);
    }

    @Transactional
    public void invalidateStudyRootNetwork(UUID studyUuid, UUID rootNetworkUuid, String userId, boolean updateCase) {
        handleInvalidateStudyRootNetwork(studyUuid, rootNetworkUuid, userId, updateCase);
    }

    private void handleInvalidateStudyRootNetwork(UUID studyUuid, UUID rootNetworkUuid, String userId, boolean updateCase) {
        rootNetworkService.assertIsRootNetworkInStudy(studyUuid, rootNetworkUuid);
        var rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        // First we unbuild all nodes
        networkModificationTreeService.doUnbuildNodeTree(studyUuid, rootNodeUuid, true, userId);
        // Then we erase data linked to root node on all root networks
        rootNetworkService.invalidateRootNetworkRemoteInfos(List.of(rootNetworkService.getRootNetworkInfos(rootNetworkUuid)), true, false);
        if (!updateCase) {
            rootNetworkService.updateRootNetworkIndexationStatus(studyUuid, rootNetworkUuid, RootNetworkIndexationStatus.NOT_INDEXED);
        }
        notificationService.emitRootNetworksUpdated(studyUuid);
    }

    private void updateRootNetworkBasicInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, boolean updateCase, String userId) {
        String previousTag = rootNetworkService.getRootNetworkTag(rootNetworkInfos.getId());
        rootNetworkService.updateRootNetwork(rootNetworkInfos, updateCase);
        // the entity says what the tag really became, unlike the DTO
        String newTag = rootNetworkService.getRootNetworkTag(rootNetworkInfos.getId());
        renameRootNetworkTagInApplicabilities(studyUuid, previousTag, newTag, userId);
        postRootNetworkUpdate(studyUuid, rootNetworkInfos.getId(), updateCase);
    }

    private void renameRootNetworkTagInApplicabilities(UUID studyUuid, String previousTag, String newTag, String userId) {
        if (previousTag == null || previousTag.equals(newTag)) {
            return;
        }
        networkModificationRestService.renameRootNetworkTag(getStudyModificationGroupUuids(studyUuid), previousTag, newTag, userId);
    }

    private void postRootNetworkUpdate(UUID studyUuid, UUID rootNetworkUuid, boolean updateCase) {
        if (updateCase) {
            Optional<RootNetworkRequestEntity> rootNetworkModificationRequestEntityOpt = rootNetworkService.getRootNetworkRequest(rootNetworkUuid);
            rootNetworkModificationRequestEntityOpt.ifPresent(rootNetworkService::deleteRootNetworkRequest);
            notificationService.emitRootNetworkUpdated(studyUuid, rootNetworkUuid);
        } else {
            notificationService.emitRootNetworksUpdated(studyUuid);
        }
    }

    @Transactional
    public String getAllMapData(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, List<String> substationsIds) {
        StudyEntity studyEntity = getStudy(studyUuid);
        LoadFlowParameters loadFlowParameters = loadFlowService.getCommonParameters(studyEntity);
        Map<String, Map<String, String>> optionalParameters = new HashMap<>();
        Stream.of(
                String.valueOf(ElementType.BRANCH),
                String.valueOf(ElementType.LINE),
                String.valueOf(ElementType.TIE_LINE),
                String.valueOf(ElementType.TWO_WINDINGS_TRANSFORMER),
                String.valueOf(ElementType.BATTERY)
        ).forEach(type -> optionalParameters.put(
                type,
                new HashMap<>(Map.of(InfoTypeParameters.QUERY_PARAM_DC_POWERFACTOR, String.valueOf(loadFlowParameters.getDcPowerFactor())))
        ));
        optionalParameters.get(String.valueOf(ElementType.BRANCH)).put(
                InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBranchOperationalLimitGroup()));
        optionalParameters.get(String.valueOf(ElementType.LINE)).put(
                InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadLineOperationalLimitGroup()));
        optionalParameters.get(String.valueOf(ElementType.LINE)).put(
                InfoTypeParameters.QUERY_PARAM_LOAD_BUS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadLineBus()));
        optionalParameters.get(String.valueOf(ElementType.TWO_WINDINGS_TRANSFORMER)).put(
                InfoTypeParameters.QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS,
                String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadTwtOperationalLimitGroup()));
        optionalParameters.put(String.valueOf(ElementType.GENERATOR),
                Map.of(
                        InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadGeneratorRegulatingTerminal()),
                        InfoTypeParameters.QUERY_PARAM_LOAD_BUS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadGeneratorBus())));
        optionalParameters.put(String.valueOf(ElementType.BATTERY),
                Map.of(
                        InfoTypeParameters.QUERY_PARAM_LOAD_REGULATING_TERMINALS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBatteryRegulatingTerminal())));
        optionalParameters.put(String.valueOf(ElementType.BUS),
                Map.of(
                        InfoTypeParameters.QUERY_PARAM_LOAD_NETWORK_COMPONENTS,
                        String.valueOf(studyEntity.getSpreadsheetParameters().isSpreadsheetLoadBusNetworkComponents())));
        return networkMapService.getAllElementsInfos(
                rootNetworkService.getNetworkUuid(rootNetworkUuid),
                networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid),
                substationsIds,
                optionalParameters);
    }

    /**
     * A root network tag of a study must be associated with all its shared modifications: the ones the user cannot
     * write on, which they cannot associate with a new tag, must already be.
     */
    private void assertCanAddRootNetworkTag(UUID studyUuid, String newTag, String userId) {
        networkModificationRestService.assertRootNetworkTagsAllowed(getStudyModificationGroupUuids(studyUuid), List.of(), List.of(), List.of(newTag), userId);
    }

    private void updateRootNetworkCaseInfos(UUID studyUuid, RootNetworkInfos rootNetworkInfos, String userId, RootNetworkRequestEntity rootNetworkModificationRequestEntity) {
        UUID clonedCaseUuid = caseService.duplicateCase(rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), true);
        rootNetworkInfos.getCaseInfos().setCaseUuid(clonedCaseUuid);
        try {
            persistNetwork(rootNetworkInfos, studyUuid, null, userId, rootNetworkInfos.getImportParameters(), CaseImportAction.ROOT_NETWORK_MODIFICATION, UUID.randomUUID());
        } catch (Exception e) {
            rootNetworkService.deleteRootNetworkRequest(rootNetworkModificationRequestEntity);
            throw e;
        }
    }

    public ContingencyCount getContingencyCount(UUID studyUuid, List<UUID> contingencyListIds, UUID nodeUuid, UUID rootNetworkUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListIds);
        Objects.requireNonNull(nodeUuid);

        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);

        return actionsService.getContingencyCount(networkuuid, variantId, contingencyListIds);
    }

    public List<LimitViolationInfos> getLimitViolations(@NonNull UUID nodeUuid, UUID rootNetworkUuid, String filters, String globalFilters, Sort sort) {
        UUID networkuuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        return loadflowRestService.getLimitViolations(resultUuid, filters, globalFilters, sort, networkuuid, variantId);
    }

    public List<String> getResultEnumValues(UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType, String enumName) {
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(enumName);
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, computationType);
        if (resultUuid != null) {
            return switch (computationType) {
                case LOAD_FLOW -> loadflowRestService.getEnumValues(enumName, resultUuid);
                case SECURITY_ANALYSIS -> securityAnalysisRestService.getEnumValues(enumName, resultUuid);
                case SHORT_CIRCUIT, SHORT_CIRCUIT_ONE_BUS -> shortCircuitRestService.getEnumValues(enumName, resultUuid);
                default -> throw new StudyException(NOT_ALLOWED);
            };
        } else {
            return new ArrayList<>();
        }
    }

    public byte[] generateSubstationSvg(String substationId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            List<CurrentLimitViolationInfos> violations = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
            sldRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, violations);
            return singleLineDiagramService.generateSubstationSvg(networkUuid, variantId, substationId, sldRequestInfos);
        } else {
            return null;
        }
    }

    public String generateSubstationSvgAndMetadata(String substationId, UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> sldRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            List<CurrentLimitViolationInfos> violations = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
            sldRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, violations);
            return singleLineDiagramService.generateSubstationSvgAndMetadata(networkUuid, variantId, substationId, sldRequestInfos);
        } else {
            return null;
        }
    }

    public String generateNetworkAreaDiagram(UUID nodeUuid, UUID rootNetworkUuid, Map<String, Object> nadRequestInfos) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        if (networkUuid == null) {
            throw new StudyException(NOT_FOUND, "Root network not found");
        }
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        if (networkStoreService.existVariant(networkUuid, variantId)) {
            List<CurrentLimitViolationInfos> currentLimitViolationInfos = getCurrentLimitViolations(nodeUuid, rootNetworkUuid);
            nadRequestInfos.put(CURRENT_LIMIT_VIOLATIONS_INFOS, currentLimitViolationInfos);
            return singleLineDiagramService.generateNetworkAreaDiagram(networkUuid, variantId, nadRequestInfos);
        } else {
            return null;
        }
    }

    public List<EquipmentInfos> searchEquipments(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userInput,
                                                 @NonNull EquipmentInfosService.FieldSelector fieldSelector, String equipmentType,
                                                 boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return equipmentInfosService.searchEquipments(networkUuid, variantId, userInput, fieldSelector, equipmentType);
    }

    public List<ModificationsSearchResultByNode> searchModifications(@NonNull UUID rootNetworkUuid, @NonNull String userInput) {
        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        Map<UUID, Object> modificationsByGroup = networkModificationRestService.searchModifications(networkUuid, userInput);
        return networkModificationTreeService.getNetworkModificationsByNodeInfos(modificationsByGroup);
    }

    @Transactional
    public void unbuildStudyNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        if (networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isNotBuilt()) {
            return;
        }

        // if loadflow was run on a security node, all children node might have been impacted with loadflow modifications
        // we need to invalidate them all
        if (networkModificationTreeService.isSecurityNodeWithLoadFlowDone(nodeUuid, rootNetworkUuid)) {
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid);
        } else {
            networkModificationTreeService.invalidateNode(studyUuid, nodeUuid, rootNetworkUuid);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    public void stopBuild(@NonNull UUID nodeUuid, UUID rootNetworkUuid) {
        networkModificationRestService.stopBuild(nodeUuid, rootNetworkUuid);
    }

    @Transactional
    public void insertVoltageInitModifications(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, String.format("Missing results for rootNetwork %s on node %s", rootNetworkUuid, nodeUuid));
        }
        UUID voltageInitModificationsGroupUuid = voltageInitRestService.getModificationsGroupUuid(nodeUuid, resultUuid);

        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            networkModificationTreeService.assertStudyContainsNode(studyUuid, nodeUuid);

            networkModificationTreeService.invalidateNodeTreeWithLF(studyUuid, nodeUuid, rootNetworkUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode.PRESERVE_VOLTAGE_INIT_RESULTS);

            // voltageInit modification should apply only on the root network where the computation has been made:
            // - application context will point to the computation root network only
            // - after creation, we deactivate the new modification for all other root networks
            List<RootNetworkEntity> studyRootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);
            List<ModificationApplicationContext> modificationApplicationContexts = new ArrayList<>();
            List<String> rootNetworkTagsToDeactivate = new ArrayList<>();
            studyRootNetworkEntities.forEach(rootNetworkEntity -> {
                if (rootNetworkUuid.equals(rootNetworkEntity.getId())) {
                    modificationApplicationContexts.add(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), nodeUuid, rootNetworkEntity.getNetworkUuid()));
                } else {
                    rootNetworkTagsToDeactivate.add(rootNetworkEntity.getTag());
                }
            });
            // duplicate the modification created by voltageInit server into the current node
            NetworkModificationsResult networkModificationResults = networkModificationRestService.duplicateModificationsFromGroup(networkModificationTreeService.getModificationGroupUuid(nodeUuid),
                    voltageInitModificationsGroupUuid, Pair.of(List.of(), modificationApplicationContexts), userId);

            // We expect a single voltageInit modification in the result list
            if (networkModificationResults != null && networkModificationResults.modificationUuids().size() == 1) {
                List<UUID> createdModificationUuids = List.of(networkModificationResults.modificationUuids().getFirst());
                rootNetworkTagsToDeactivate.forEach(rootNetworkTag ->
                        networkModificationRestService.updateRootNetworkApplicability(createdModificationUuids, rootNetworkTag, false, userId));
                // The modification was applied only on rootNetworkUuid, so the single result must be attributed to it
                networkModificationResults.modificationResults().getFirst()
                        .ifPresent(result -> networkModificationTreeService.handleNetworkModificationApplyResult(studyUuid, nodeUuid, rootNetworkUuid, result));
            }

            voltageInitRestService.resetModificationsGroupUuid(resultUuid);

            // send notification voltage init result has changed
            notificationService.emitStudyChanged(studyUuid, nodeUuid, rootNetworkUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional(readOnly = true)
    public String evaluateFilter(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode, String filter) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return filterService.evaluateFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid), filter);
    }

    @Transactional(readOnly = true)
    public List<String> evaluateGlobalFilter(@NonNull final UUID nodeUuid, @NonNull final UUID rootNetworkUuid,
                                             @NonNull final List<EquipmentType> equipmentTypes, @NonNull final GlobalFilter filter) {
        return filterService.evaluateGlobalFilter(
                rootNetworkService.getNetworkUuid(rootNetworkUuid),
                networkModificationTreeService.getVariantId(getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, true), rootNetworkUuid),
                equipmentTypes,
                filter
        );
    }

    @Transactional(readOnly = true)
    public String exportFilter(UUID rootNetworkUuid, UUID filterUuid) {
        return filterService.exportFilter(rootNetworkService.getNetworkUuid(rootNetworkUuid), filterUuid);
    }

    public String exportFilters(UUID rootNetworkUuid, List<UUID> filtersUuid, UUID nodeUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        String variantId = networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid);
        return filterService.exportFilters(rootNetworkService.getNetworkUuid(rootNetworkUuid), filtersUuid, variantId);
    }

    public Map<ComputationType, String> getAllComputationsStatus(@NonNull UUID studyUuid, @NonNull UUID rootNetworkUuid, @NonNull UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        Map<ComputationType, String> allComputationStatus = new EnumMap<>(ComputationType.class);
        allComputationStatus.put(LOAD_FLOW, rootNetworkNodeInfoService.getLoadFlowStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(SECURITY_ANALYSIS, rootNetworkNodeInfoService.getSecurityAnalysisStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(PCC_MIN, rootNetworkNodeInfoService.getPccMinStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(ASYMMETRICAL_LOAD, rootNetworkNodeInfoService.getAsymmetricalLoadStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(LOGICAL_CONTROLS, rootNetworkNodeInfoService.getLogicalControlsStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(DYNAMIC_MARGIN_CALCULATION, rootNetworkNodeInfoService.getDynamicMarginCalculationStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(DYNAMIC_SECURITY_ANALYSIS, rootNetworkNodeInfoService.getDynamicSecurityAnalysisStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(DYNAMIC_SIMULATION, rootNetworkNodeInfoService.getDynamicSimulationStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(STATE_ESTIMATION, rootNetworkNodeInfoService.getStateEstimationStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(SENSITIVITY_ANALYSIS, rootNetworkNodeInfoService.getSensitivityAnalysisStatus(nodeUuid, rootNetworkUuid));
        allComputationStatus.put(SHORT_CIRCUIT_ONE_BUS, rootNetworkNodeInfoService.getShortCircuitAnalysisStatus(nodeUuid, rootNetworkUuid, ShortcircuitAnalysisType.ONE_BUS));
        allComputationStatus.put(SHORT_CIRCUIT, rootNetworkNodeInfoService.getShortCircuitAnalysisStatus(nodeUuid, rootNetworkUuid, ShortcircuitAnalysisType.ALL_BUSES));
        allComputationStatus.put(VOLTAGE_INITIALIZATION, rootNetworkNodeInfoService.getVoltageInitStatus(nodeUuid, rootNetworkUuid));
        return allComputationStatus;
    }

    public void assertRootNodeOrBuiltNode(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid) {
        if (!(networkModificationTreeService.getStudyRootNodeUuid(studyUuid).equals(nodeUuid)
                || networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt())) {
            throw new StudyException(NODE_NOT_BUILT);
        }
    }

    public UUID exportNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, NodeExportInfos exportInfos, String format, CompressionType compression, String userId, String parametersJson) {
        // Checks if we can write on target directory in gridexplore
        if (exportInfos.exportToGridExplore()) {
            directoryService.checkPermission(List.of(), exportInfos.directoryUuid(), userId, PermissionType.WRITE, false);
            if (directoryService.elementExists(exportInfos.directoryUuid(), exportInfos.fileName(), DirectoryService.CASE)) {
                throw new StudyException(ELEMENT_ALREADY_EXISTS, "export file name " + exportInfos.fileName() + " already exists in directory", Map.of("fileName", exportInfos.fileName()));
            }
        }

        UUID networkUuid = rootNetworkService.getNetworkUuid(rootNetworkUuid);
        String variantId = networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
        UUID exportUuid = networkConversionService.exportNetwork(networkUuid, studyUuid, variantId,
                new NodeExportInfos(exportInfos.exportToGridExplore(), exportInfos.directoryUuid(), exportInfos.fileName(), exportInfos.description()), format, compression, userId, parametersJson);

        networkModificationTreeService.updateExportNetworkStatus(nodeUuid, exportUuid, ExportNetworkStatus.RUNNING);
        return exportUuid;
    }

    @Transactional(readOnly = true)
    public List<Report> getParentNodesReport(UUID nodeUuid, UUID rootNetworkUuid, boolean nodeOnlyReport, StudyService.ReportType reportType, Set<String> severityLevels) {
        AbstractNode nodeInfos = networkModificationTreeService.getNode(nodeUuid, rootNetworkUuid);

        if (isNonRootNodeWithComputationReportType(nodeInfos, reportType)) {
            UUID reportUuid = getReportUuidForNode(nodeUuid, rootNetworkUuid, reportType);
            return reportUuid != null ? List.of(reportService.getReport(reportUuid, nodeUuid.toString(), severityLevels)) : Collections.emptyList();
        } else if (nodeOnlyReport) {
            return getNodeOnlyReport(nodeUuid, rootNetworkUuid, severityLevels);
        } else {
            return getAllModificationReports(nodeUuid, rootNetworkUuid, severityLevels);
        }
    }

    private boolean isNonRootNodeWithComputationReportType(AbstractNode nodeInfos, StudyService.ReportType reportType) {
        return nodeInfos.getType() != NodeType.ROOT && reportType != StudyService.ReportType.NETWORK_MODIFICATION;
    }

    private UUID getReportUuidForNode(UUID nodeUuid, UUID rootNetworkUuid, StudyService.ReportType reportType) {
        return networkModificationTreeService.getComputationReports(nodeUuid, rootNetworkUuid).get(reportType.name());
    }

    private List<Report> getNodeOnlyReport(UUID nodeUuid, UUID rootNetworkUuid, Set<String> severityLevels) {
        return networkModificationTreeService.getReportUuid(nodeUuid, rootNetworkUuid)
                .map(uuid -> List.of(reportService.getReport(uuid, nodeUuid.toString(), severityLevels)))
                .orElse(Collections.emptyList());
    }

    private List<Report> getAllModificationReports(UUID nodeUuid, UUID rootNetworkUuid, Set<String> severityLevels) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        List<Report> modificationReports = new ArrayList<>();
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        for (UUID nodeId : nodeIds) {
            Optional<UUID> reportId = Optional.ofNullable(modificationReportsMap.get(nodeId))
                    .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid));

            reportId.ifPresent(uuid ->
                    modificationReports.add(reportService.getReport(uuid, nodeId.toString(), severityLevels))
            );
        }

        return modificationReports;
    }

    @Transactional(readOnly = true)
    public ReportPage getReportLogs(UUID nodeUuid, UUID rootNetworkUuid, UUID reportId, String messageFilter, Set<String> severityLevels, boolean paged, Pageable pageable) {
        if (reportId != null) {
            return reportService.getPagedReportLogs(reportId, messageFilter, severityLevels, paged, pageable);
        }
        return getParentNodesReportLogs(nodeUuid, rootNetworkUuid, messageFilter, severityLevels, paged, pageable);
    }

    private ReportPage getParentNodesReportLogs(UUID nodeUuid, UUID rootNetworkUuid, String messageFilter, Set<String> severityLevels, boolean paged, Pageable pageable) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        List<UUID> reportUuids = nodeIds.stream()
                .map(nodeId -> Optional.ofNullable(modificationReportsMap.get(nodeId))
                        .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        return reportService.getPagedMultipleReportLogs(reportUuids, messageFilter, severityLevels, paged, pageable);
    }

    @Transactional(readOnly = true)
    public String getSearchTermMatchesInFilteredLogs(UUID nodeUuid, UUID rootNetworkUuid, UUID reportId, Set<String> severityLevels, String messageFilter, String searchTerm, int pageSize) {
        if (reportId != null) {
            return reportService.getSearchTermMatchesInFilteredLogs(reportId, severityLevels, messageFilter, searchTerm, pageSize);
        }
        return getSearchTermMatchesInParentNodesFilteredLogs(nodeUuid, rootNetworkUuid, severityLevels, messageFilter, searchTerm, pageSize);
    }

    private String getSearchTermMatchesInParentNodesFilteredLogs(UUID nodeUuid, UUID rootNetworkUuid, Set<String> severityLevels, String messageFilter, String searchTerm, int pageSize) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        List<UUID> reportUuids = nodeIds.stream()
                .map(nodeId -> Optional.ofNullable(modificationReportsMap.get(nodeId))
                        .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        return reportService.getSearchTermMatchesInMultipleFilteredLogs(reportUuids, severityLevels, messageFilter, searchTerm, pageSize);
    }

    @Transactional(readOnly = true)
    public Set<String> getAggregatedReportSeverities(UUID nodeUuid, UUID rootNetworkUuid, UUID reportId) {
        if (reportId != null) {
            return reportService.getReportAggregatedSeverities(reportId);
        }
        return getParentNodesAggregatedReportSeverities(nodeUuid, rootNetworkUuid);
    }

    private Set<String> getParentNodesAggregatedReportSeverities(UUID nodeUuid, UUID rootNetworkUuid) {
        List<UUID> nodeIds = nodesTree(nodeUuid);
        Set<String> severities = new HashSet<>();
        Map<UUID, UUID> modificationReportsMap = networkModificationTreeService.getModificationReports(nodeUuid, rootNetworkUuid);

        for (UUID nodeId : nodeIds) {
            Optional<UUID> reportId = Optional.ofNullable(modificationReportsMap.get(nodeId))
                    .or(() -> networkModificationTreeService.getReportUuid(nodeId, rootNetworkUuid));

            reportId.ifPresent(uuid ->
                    severities.addAll(reportService.getReportAggregatedSeverities(uuid))
            );
        }
        return severities;
    }

    private List<UUID> nodesTree(UUID nodeUuid) {
        List<UUID> nodeIds = new ArrayList<>();
        nodeIds.add(nodeUuid);
        Optional<UUID> parentUuid = networkModificationTreeService.getParentNodeUuid(nodeUuid);

        while (parentUuid.isPresent()) {
            nodeIds.add(parentUuid.get());
            parentUuid = networkModificationTreeService.getParentNodeUuid(parentUuid.get());
        }

        Collections.reverse(nodeIds);
        return nodeIds;
    }

    public StudyEntity updateRootNetworkIndexationStatus(UUID studyUuid, UUID rootNetworkUuid, RootNetworkIndexationStatus indexationStatus) {
        return updateRootNetworkIndexationStatus(getStudy(studyUuid), rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")),
                indexationStatus);
    }

    @Transactional(readOnly = true)
    public String getVoltageInitModifications(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        // get modifications group uuid associated to voltage init results
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.VOLTAGE_INITIALIZATION);
        if (resultUuid == null) {
            throw new StudyException(NO_VOLTAGE_INIT_RESULTS_FOR_NODE, String.format("Missing results for rootNetwork %s on node %s", rootNetworkUuid, nodeUuid));
        }
        UUID voltageInitModificationsGroupUuid = voltageInitRestService.getModificationsGroupUuid(nodeUuid, resultUuid);
        return networkModificationRestService.getModifications(voltageInitModificationsGroupUuid, false, false, null);
    }

    @Transactional
    public void reindexRootNetwork(UUID studyUuid, UUID rootNetworkUuid) {
        rootNetworkService.reindexRootNetwork(getStudy(studyUuid), rootNetworkUuid);
    }

    private UUID getNodeUuidToSearchIn(UUID initialNodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = initialNodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuiltUuid(initialNodeUuid, rootNetworkUuid);
        }
        return nodeUuidToSearchIn;
    }

    private List<CurrentLimitViolationInfos> getCurrentLimitViolations(UUID nodeUuid, UUID rootNetworkUuid) {
        UUID resultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, LOAD_FLOW);
        if (resultUuid == null) {
            return List.of();
        }
        return loadflowRestService.getCurrentLimitViolations(resultUuid)
                .stream()
                .map(l -> new CurrentLimitViolationInfos(l.getSubjectId(), null))
                .toList();
    }

    private Map<String, Double> getBusIdToIccValuesMap(String voltageLevelId, UUID nodeUuid, UUID rootNetworkUuid) {
        UUID shortCircuitResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(nodeUuid, rootNetworkUuid, SHORT_CIRCUIT);
        return shortCircuitResultUuid != null ?
                shortCircuitRestService.getVoltageLevelIccValues(shortCircuitResultUuid, voltageLevelId) : Map.of();
    }

    public String getNetworkCountries(UUID nodeUuid, UUID rootNetworkUuid, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = getNodeUuidToSearchIn(nodeUuid, rootNetworkUuid, inUpstreamBuiltParentNode);
        return networkMapService.getCountries(rootNetworkService.getNetworkUuid(rootNetworkUuid), networkModificationTreeService.getVariantId(nodeUuidToSearchIn, rootNetworkUuid));
    }
}
