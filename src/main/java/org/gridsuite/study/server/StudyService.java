/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.EquipmentDeletionInfos;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@Service
public class StudyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyService.class);

    public static final String ROOT_CATEGORY_REACTOR = "reactor.";

    public static final String CATEGORY_BROKER_INPUT = StudyService.class.getName() + ".input-broker-messages";

    private static final String CATEGORY_BROKER_OUTPUT = StudyService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    static final String HEADER_USER_ID = "userId";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_NODE = "node";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_STUDIES = "studies";
    static final String UPDATE_TYPE_STUDY_DELETE = "deleteStudy";
    static final String UPDATE_TYPE_LOADFLOW = "loadflow";
    static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    static final String UPDATE_TYPE_SWITCH = "switch";
    static final String UPDATE_TYPE_LINE = "line";
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_RESULT = "securityAnalysisResult";
    static final String UPDATE_TYPE_SECURITY_ANALYSIS_STATUS = "securityAnalysis_status";
    static final String UPDATE_TYPE_BUILD_COMPLETED = "buildCompleted";
    static final String UPDATE_TYPE_BUILD_CANCELLED = "buildCancelled";
    static final String HEADER_ERROR = "error";
    static final String UPDATE_TYPE_STUDY = "study";
    static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID = "deletedEquipmentId";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE = "deletedEquipmentType";
    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";
    static final String QUERY_PARAM_COMPONENT_LIBRARY = "componentLibrary";
    static final String QUERY_PARAM_USE_NAME = "useName";
    static final String QUERY_PARAM_CENTER_LABEL = "centerLabel";
    static final String QUERY_PARAM_DIAGONAL_LABEL = "diagonalLabel";
    static final String QUERY_PARAM_TOPOLOGICAL_COLORING = "topologicalColoring";
    static final String QUERY_PARAM_SUBSTATION_LAYOUT = "substationLayout";
    static final String RESULT_UUID = "resultUuid";

    static final String QUERY_PARAM_RECEIVER = "receiver";

    static final String HEADER_RECEIVER = "receiver";

    static final String FIRST_VARIANT_ID = "first_variant_id";

    // Self injection for @transactional support in internal calls to other methods of this service
    @Autowired
    StudyService self;

    NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private RestTemplate restTemplate;
    private String caseServerBaseUri;
    private String singleLineDiagramServerBaseUri;
    private String networkConversionServerBaseUri;
    private String geoDataServerBaseUri;
    private String networkMapServerBaseUri;
    private String loadFlowServerBaseUri;
    private String securityAnalysisServerBaseUri;
    private String actionsServerBaseUri;
    private String defaultLoadflowProvider;

    private final StudyRepository studyRepository;
    private final StudyCreationRequestRepository studyCreationRequestRepository;
    private final NetworkService networkStoreService;
    private final NetworkModificationService networkModificationService;
    private final ReportService reportService;
    private final StudyInfosService studyInfosService;
    private final EquipmentInfosService equipmentInfosService;

    private final ObjectMapper objectMapper;

    @Autowired
    private StreamBridge studyUpdatePublisher;

    @Bean
    public Consumer<Message<String>> consumeSaResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Security analysis result '{}' available for node '{}'", resultUuid,
                            receiverObj.getNodeUuid());

                    // update DB
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);
                                // send notifications
                    UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
                    emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_RESULT);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Autowired
    public StudyService(@Value("${backing-services.case.base-uri:http://case-server/}") String caseServerBaseUri,
        @Value("${backing-services.single-line-diagram.base-uri:http://single-line-diagram-server/}") String singleLineDiagramServerBaseUri,
        @Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionServerBaseUri,
        @Value("${backing-services.geo-data.base-uri:http://geo-data-server/}") String geoDataServerBaseUri,
        @Value("${backing-services.network-map.base-uri:http://network-map-server/}") String networkMapServerBaseUri,
        @Value("${backing-services.loadflow.base-uri:http://loadflow-server/}") String loadFlowServerBaseUri,
        @Value("${backing-services.security-analysis-server.base-uri:http://security-analysis-server/}") String securityAnalysisServerBaseUri,
        @Value("${backing-services.actions-server.base-uri:http://actions-server/}") String actionsServerBaseUri,
        @Value("${loadflow.default-provider}") String defaultLoadflowProvider,
        StudyRepository studyRepository,
        StudyCreationRequestRepository studyCreationRequestRepository,
        NetworkService networkStoreService,
        NetworkModificationService networkModificationService,
        ReportService reportService,
        @Lazy StudyInfosService studyInfosService,
        @Lazy EquipmentInfosService equipmentInfosService,
        NetworkModificationTreeService networkModificationTreeService,
        ObjectMapper objectMapper) {
        this.caseServerBaseUri = caseServerBaseUri;
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
        this.geoDataServerBaseUri = geoDataServerBaseUri;
        this.networkMapServerBaseUri = networkMapServerBaseUri;
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
        this.actionsServerBaseUri = actionsServerBaseUri;
        this.studyRepository = studyRepository;
        this.studyCreationRequestRepository = studyCreationRequestRepository;
        this.networkStoreService = networkStoreService;
        this.networkModificationService = networkModificationService;
        this.reportService = reportService;
        this.studyInfosService = studyInfosService;
        this.equipmentInfosService = equipmentInfosService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.defaultLoadflowProvider = defaultLoadflowProvider;
        this.objectMapper = objectMapper;
    }

    private static StudyInfos toStudyInfos(StudyEntity entity) {
        return StudyInfos.builder()
                .id(entity.getId())
                .creationDate(ZonedDateTime.ofInstant(entity.getDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .userId(entity.getUserId())
                .caseFormat(entity.getCaseFormat())
                .build();
    }

    private static BasicStudyInfos toBasicStudyInfos(StudyCreationRequestEntity entity) {
        return BasicStudyInfos.builder()
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .userId(entity.getUserId())
                .id(entity.getId())
                .build();
    }

    private static CreatedStudyBasicInfos toCreatedStudyBasicInfos(StudyEntity entity) {
        return CreatedStudyBasicInfos.builder()
                .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
                .userId(entity.getUserId())
                .id(entity.getId())
                .caseFormat(entity.getCaseFormat())
                .build();
    }

    public List<CreatedStudyBasicInfos> getStudies() {
        return studyRepository.findAll().stream()
                .map(StudyService::toCreatedStudyBasicInfos)
                .sorted(Comparator.comparing(CreatedStudyBasicInfos::getCreationDate).reversed())
                .collect(Collectors.toList());
    }

    public List<CreatedStudyBasicInfos> getStudiesMetadata(List<UUID> uuids) {
        return studyRepository.findAllById(uuids).stream().map(StudyService::toCreatedStudyBasicInfos)
                .collect(Collectors.toList());
    }

    List<BasicStudyInfos> getStudiesCreationRequests() {
        return studyCreationRequestRepository.findAll().stream()
                .map(StudyService::toBasicStudyInfos)
                .sorted(Comparator.comparing(BasicStudyInfos::getCreationDate).reversed()).collect(Collectors.toList());
    }

    public BasicStudyInfos createStudy(UUID caseUuid, String userId, UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));
        new Thread(() -> {
            try {
                String caseFormat = getCaseFormat(caseUuid);
                NetworkInfos networkInfos = persistentStore(caseUuid, basicStudyInfos.getId(), userId);

                LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                insertStudy(basicStudyInfos.getId(), userId, networkInfos.getNetworkUuid(), networkInfos.getNetworkId(),
                        caseFormat, caseUuid, false, toEntity(loadFlowParameters));
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
            } finally {
                deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
                LOGGER.trace("Create study '{}' : {} seconds", basicStudyInfos.getId(),
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
            }
        }).start();
        return basicStudyInfos;
    }

    public BasicStudyInfos createStudy(MultipartFile caseFile, String userId, UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        BasicStudyInfos basicStudyInfos = StudyService.toBasicStudyInfos(insertStudyCreationRequest(userId, studyUuid));

        new Thread(() -> {
            try {
                UUID caseUuid = importCase(caseFile, basicStudyInfos.getId(), userId);
                if (caseUuid != null) {
                    String caseFormat = getCaseFormat(caseUuid);
                    NetworkInfos networkInfos = persistentStore(caseUuid, basicStudyInfos.getId(), userId);

                    LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                    insertStudy(basicStudyInfos.getId(), userId, networkInfos.getNetworkUuid(),
                            networkInfos.getNetworkId(), caseFormat, caseUuid, false, toEntity(loadFlowParameters));
                }
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
            } finally {
                deleteStudyIfNotCreationInProgress(basicStudyInfos.getId(), userId);
                LOGGER.trace("Create study '{}' : {} seconds", basicStudyInfos.getId(),
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
            }
        }).start();

        return basicStudyInfos;
    }

    public StudyInfos getStudyInfos(UUID studyUuid) {
        StudyEntity study = getStudy(studyUuid);
        if (study == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return StudyService.toStudyInfos(study);
    }

    @Transactional(readOnly = true)
    public StudyEntity doGetStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElse(null);
    }

    public StudyEntity getStudy(UUID studyUuid) {
        return self.doGetStudy(studyUuid);
    }

    List<CreatedStudyBasicInfos> searchStudies(@NonNull String query) {
        return studyInfosService.search(query);
    }

    public static String escapeLucene(String s) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '+':
                case '\\':
                case '-':
                case '!':
                case '(':
                case ')':
                case ':':
                case '^':
                case '[':
                case ']':
                case '"':
                case '{':
                case '}':
                case '~':
                case '*':
                case '?':
                case '|':
                case '&':
                case '/':

                case ' ': // white space has to be escaped, too
                    sb.append('\\');
                    break;
                default:
                    // do nothing but appease sonarlint
            }

            sb.append(c);
        }

        return sb.toString();
    }

    List<EquipmentInfos> searchEquipments(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull String userInput,
                                          @NonNull EquipmentInfosService.FieldSelector fieldSelector, String equipmentType,
                                          boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuidToSearchIn);

        if (variantId.isEmpty()) {
            variantId = VariantManagerConstants.INITIAL_VARIANT_ID;
        }

        String queryInitialVariant = buildEquipmentSearchQuery(userInput, fieldSelector, networkUuid,
                VariantManagerConstants.INITIAL_VARIANT_ID, equipmentType);
        List<EquipmentInfos> equipmentInfosInInitVariant = equipmentInfosService.searchEquipments(queryInitialVariant);

        return (variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID)) ? equipmentInfosInInitVariant
                : completeSearchWithCurrentVariant(networkUuid, variantId, userInput, fieldSelector,
                        equipmentInfosInInitVariant, equipmentType);
    }

    private List<EquipmentInfos> completeSearchWithCurrentVariant(UUID networkUuid, String variantId, String userInput,
                                                                  EquipmentInfosService.FieldSelector fieldSelector, List<EquipmentInfos> equipmentInfosInInitVariant,
                                                                  String equipmentType) {
        String queryTombstonedEquipments = buildTombstonedEquipmentSearchQuery(networkUuid, variantId);
        Set<String> removedEquipmentIdsInVariant = equipmentInfosService.searchTombstonedEquipments(queryTombstonedEquipments)
                .stream()
                .map(TombstonedEquipmentInfos::getId)
                .collect(Collectors.toSet());

        String queryVariant = buildEquipmentSearchQuery(userInput, fieldSelector, networkUuid, variantId,
                equipmentType);
        List<EquipmentInfos> addedEquipmentInfosInVariant = equipmentInfosService.searchEquipments(queryVariant);

        List<EquipmentInfos> equipmentInfos = equipmentInfosInInitVariant
                .stream()
                .filter(ei -> !removedEquipmentIdsInVariant.contains(ei.getId()))
                .collect(Collectors.toList());

        equipmentInfos.addAll(addedEquipmentInfosInVariant);

        return equipmentInfos;
    }

    private String buildEquipmentSearchQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String variantId, String equipmentType) {
        String query = "networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND %s:(*%s*)"
                + (equipmentType == null ? "" : " AND equipmentType.keyword:(%s)");
        return String.format(query, networkUuid, variantId,
                fieldSelector == EquipmentInfosService.FieldSelector.NAME ? "equipmentName.fullascii" : "equipmentId.fullascii",
                escapeLucene(userInput), equipmentType);
    }

    private String buildTombstonedEquipmentSearchQuery(UUID networkUuid, String variantId) {
        return String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s)", networkUuid, variantId);
    }

    @Transactional
    public Optional<DeleteStudyInfos> doDeleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        Optional<StudyCreationRequestEntity> studyCreationRequestEntity = studyCreationRequestRepository.findById(studyUuid);
        UUID networkUuid = null;
        List<UUID> groupsUuids = new ArrayList<>();
        if (studyCreationRequestEntity.isEmpty()) {
            networkUuid = networkStoreService.doGetNetworkUuid(studyUuid).orElse(null);
            groupsUuids = networkModificationTreeService.getAllModificationGroupUuids(studyUuid);
            studyRepository.findById(studyUuid).ifPresent(s -> {
                networkModificationTreeService.doDeleteTree(studyUuid);
                studyRepository.deleteById(studyUuid);
                studyInfosService.deleteByUuid(studyUuid);
            });
        } else {
            studyCreationRequestRepository.deleteById(studyCreationRequestEntity.get().getId());
        }
        emitStudyDelete(studyUuid, userId);

        return networkUuid != null ? Optional.of(new DeleteStudyInfos(networkUuid, groupsUuids)) : Optional.empty();
    }

    public void deleteStudyIfNotCreationInProgress(UUID studyUuid, String userId) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        try {
            Optional<DeleteStudyInfos> deleteStudyInfosOpt = self.doDeleteStudyIfNotCreationInProgress(studyUuid,
                    userId);
            if (deleteStudyInfosOpt.isPresent()) {
                DeleteStudyInfos deleteStudyInfos = deleteStudyInfosOpt.get();
                startTime.set(System.nanoTime());

                // TODO : gérer parallélisation
                deleteStudyInfos.getGroupsUuids().forEach(networkModificationService::deleteModifications);
                deleteEquipmentIndexes(deleteStudyInfos.getNetworkUuid());
                reportService.deleteReport(deleteStudyInfos.getNetworkUuid());
                networkStoreService.deleteNetwork(deleteStudyInfos.getNetworkUuid());

                if (startTime.get() != null) {
                    LOGGER.trace("Delete study '{}' : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
                }
            }
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }

    public void deleteEquipmentIndexes(UUID networkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        equipmentInfosService.deleteAll(networkUuid);
        LOGGER.trace("Indexes deletion for network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
    }

    private CreatedStudyBasicInfos insertStudy(UUID studyUuid, String userId, UUID networkUuid, String networkId,
            String caseFormat, UUID caseUuid, boolean casePrivate, LoadFlowParametersEntity loadFlowParameters) {
        CreatedStudyBasicInfos createdStudyBasicInfos = StudyService.toCreatedStudyBasicInfos(insertStudyEntity(
                studyUuid, userId, networkUuid, networkId, caseFormat, caseUuid, casePrivate, loadFlowParameters));
        studyInfosService.add(createdStudyBasicInfos);

        emitStudiesChanged(studyUuid, userId);

        return createdStudyBasicInfos;
    }

    private StudyCreationRequestEntity insertStudyCreationRequest(String userId, UUID studyUuid) {
        StudyCreationRequestEntity newStudy = insertStudyCreationRequestEntity(userId, studyUuid);
        emitStudiesChanged(newStudy.getId(), userId);
        return newStudy;
    }

    private String getCaseFormat(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/format")
            .buildAndExpand(caseUuid)
            .toUriString();

        return restTemplate.getForObject(caseServerBaseUri + path, String.class);
    }

    private StudyException handleStudyCreationError(UUID studyUuid, String userId, String errorMessage,
            HttpStatus httpStatusCode, String serverName) {
        String errorToParse = errorMessage == null ? "{\"message\": \"" + serverName + ": " + httpStatusCode + "\"}"
                : errorMessage;

        try {
            JsonNode node = new ObjectMapper().readTree(errorToParse).path("message");
            if (!node.isMissingNode()) {
                emitStudyCreationError(studyUuid, userId, node.asText());
            } else {
                emitStudyCreationError(studyUuid, userId, errorToParse);
            }
        } catch (JsonProcessingException e) {
            if (!errorToParse.isEmpty()) {
                emitStudyCreationError(studyUuid, userId, errorToParse);
            }
        }

        return new StudyException(STUDY_CREATION_FAILED);
    }

    UUID importCase(MultipartFile multipartFile, UUID studyUuid, String userId) {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        UUID caseUuid = null;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            multipartBodyBuilder.part("file", multipartFile.getBytes()).filename(multipartFile.getOriginalFilename());
            HttpEntity<MultiValueMap<String, HttpEntity<?>>> request = new HttpEntity<MultiValueMap<String, HttpEntity<?>>>(
                    multipartBodyBuilder.build(), headers);

            try {
                caseUuid = restTemplate.postForObject(caseServerBaseUri + "/" + CASE_API_VERSION + "/cases/private",
                        request, UUID.class);
            } catch (HttpStatusCodeException e) {
                throw handleStudyCreationError(studyUuid, userId, e.getResponseBodyAsString(), e.getStatusCode(),
                        "case-server");
            }
        } catch (Exception e) {
            if (!(e instanceof StudyException)) {
                emitStudyCreationError(studyUuid, userId, e.getMessage());
            }
        }

        return caseUuid;
    }

    byte[] getVoltageLevelSvg(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
            UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

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

        return restTemplate.getForObject(singleLineDiagramServerBaseUri + path, byte[].class);
    }

    String getVoltageLevelSvgAndMetadata(UUID studyUuid, String voltageLevelId, DiagramParameters diagramParameters,
            UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION
                        + "/svg-and-metadata/{networkUuid}/{voltageLevelId}")
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

        return restTemplate.getForObject(singleLineDiagramServerBaseUri + path, String.class);
    }

    private NetworkInfos persistentStore(UUID caseUuid, UUID studyUuid, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks")
                .queryParam(CASE_UUID, caseUuid).queryParam(QUERY_PARAM_VARIANT_ID, FIRST_VARIANT_ID).buildAndExpand()
                .toUriString();

        ResponseEntity<NetworkInfos> networkInfosResponse;

        try {
            networkInfosResponse = restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, null,
                    NetworkInfos.class);
        } catch (HttpStatusCodeException e) {
            throw handleStudyCreationError(studyUuid, userId, e.getResponseBodyAsString(), e.getStatusCode(),
                    "network-conversion-server");
        }

        NetworkInfos networkInfos = networkInfosResponse.getBody();
        return networkInfos;
    }

    String getLinesGraphics(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/lines")
            .queryParam(NETWORK_UUID, networkUuid)
            .buildAndExpand()
            .toUriString();

        return restTemplate.getForObject(geoDataServerBaseUri + path, String.class);
    }

    String getSubstationsGraphics(UUID networkUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/substations")
            .queryParam(NETWORK_UUID, networkUuid)
            .buildAndExpand()
            .toUriString();

        return restTemplate.getForObject(geoDataServerBaseUri + path, String.class);
    }

    Boolean caseExists(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}/exists")
            .buildAndExpand(caseUuid)
            .toUriString();

        return restTemplate.exchange(caseServerBaseUri + path, HttpMethod.GET, null, Boolean.class, caseUuid).getBody();
    }

    String getEquipmentsMapData(UUID networkUuid, String variantId, List<String> substationsIds, String equipmentPath) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath);
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        return restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
    }

    String getEquipmentMapData(UUID networkUuid, String variantId, String equipmentPath, String equipmentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath + "/{equipmentUuid}");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, equipmentId).toUriString();

        String equipmentMapData;
        try {
            equipmentMapData = restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(EQUIPMENT_NOT_FOUND);
            } else {
                throw e;
            }
        }
        return equipmentMapData;
    }

    String getSubstationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "substations");
    }

    String getSubstationMapData(UUID studyUuid, UUID nodeUuid, String substationId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "substations", substationId);
    }

    String getLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "lines");
    }

    String getLineMapData(UUID studyUuid, UUID nodeUuid, String lineId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "lines", lineId);
    }

    String getTwoWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "2-windings-transformers");
    }

    String getTwoWindingsTransformerMapData(UUID studyUuid, UUID nodeUuid, String twoWindingsTransformerId,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "2-windings-transformers", twoWindingsTransformerId);
    }

    String getThreeWindingsTransformersMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "3-windings-transformers");
    }

    String getGeneratorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "generators");
    }

    String getGeneratorMapData(UUID studyUuid, UUID nodeUuid, String generatorId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "generators", generatorId);
    }

    String getBatteriesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "batteries");
    }

    String getDanglingLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "dangling-lines");
    }

    String getHvdcLinesMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "hvdc-lines");
    }

    String getLccConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "lcc-converter-stations");
    }

    String getVscConverterStationsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "vsc-converter-stations");
    }

    String getLoadsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                substationsIds, "loads");
    }

    String getLoadMapData(UUID studyUuid, UUID nodeUuid, String loadId, boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "loads", loadId);
    }

    String getShuntCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "shunt-compensators");
    }

    String getShuntCompensatorMapData(UUID studyUuid, UUID nodeUuid, String shuntCompensatorId,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }

        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "shunt-compensators", shuntCompensatorId);
    }

    String getStaticVarCompensatorsMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "static-var-compensators");
    }

    String getVoltageLevelMapData(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
            boolean inUpstreamBuiltParentNode) {
        UUID nodeUuidToSearchIn = nodeUuid;
        if (inUpstreamBuiltParentNode) {
            nodeUuidToSearchIn = networkModificationTreeService.doGetLastParentNodeBuilt(nodeUuid);
        }
        return getEquipmentMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuidToSearchIn),
                "voltage-levels", voltageLevelId);
    }

    String getAllMapData(UUID studyUuid, UUID nodeUuid, List<String> substationsIds) {
        return getEquipmentsMapData(networkStoreService.getNetworkUuid(studyUuid), getVariantId(nodeUuid),
                substationsIds, "all");
    }

    void changeSwitchState(UUID studyUuid, String switchId, boolean open, UUID nodeUuid) {
        UUID groupUuid = getModificationGroupUuid(nodeUuid);
        String variantId = getVariantId(nodeUuid);

        List<EquipmentModificationInfos> equipmentModificationsInfos = networkModificationService
                .changeSwitchState(studyUuid, switchId, open, groupUuid, variantId);
        Set<String> substationIds = getSubstationIds(equipmentModificationsInfos);

        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid);
        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_SWITCH);
    }

    public void applyGroovyScript(UUID studyUuid, String groovyScript, UUID nodeUuid) {
        UUID groupUuid = getModificationGroupUuid(nodeUuid);
        String variantId = getVariantId(nodeUuid);

        List<ModificationInfos> modificationsInfos = networkModificationService.applyGroovyScript(studyUuid,
                groovyScript, groupUuid, variantId);

        Set<String> substationIds = getSubstationIds(modificationsInfos);

        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid);
    }

    private LoadFlowStatus computeLoadFlowStatus(LoadFlowResult result) {
        return result.getComponentResults().stream()
                .filter(cr -> cr.getConnectedComponentNum() == 0 && cr.getSynchronousComponentNum() == 0
                        && cr.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED)
                .collect(Collectors.toList()).isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    void runLoadFlow(UUID studyUuid, UUID nodeUuid) {
        LoadFlowResult result;
        setLoadFlowRunning(studyUuid, nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getLoadFlowProvider(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                .queryParam("reportId", networkUuid.toString()).queryParam("reportName", "loadflow")
                .queryParam("overwrite", true);
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoadFlowParameters> httpEntity = new HttpEntity<LoadFlowParameters>(getLoadFlowParameters(studyUuid),
                headers);

        try {
            ResponseEntity<LoadFlowResult> resp = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.PUT,
                    httpEntity, LoadFlowResult.class);
            result = resp.getBody();
            updateLoadFlowResultAndStatus(nodeUuid, result, computeLoadFlowStatus(result), false);
        } catch (HttpClientErrorException e) {
            updateLoadFlowStatus(nodeUuid, LoadFlowStatus.NOT_DONE);
        } finally {
            emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LOADFLOW);
        }
        // TODO: traiter oncancel ?
    }

    private void setLoadFlowRunning(UUID studyUuid, UUID nodeUuid) {
        updateLoadFlowStatus(nodeUuid, LoadFlowStatus.RUNNING);
        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LOADFLOW_STATUS);
    }

    public Collection<String> getExportFormats() {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/export/formats")
            .toUriString();

        ParameterizedTypeReference<Collection<String>> typeRef = new ParameterizedTypeReference<>() {
        };

        return restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.GET, null, typeRef).getBody();
    }

    public ExportNetworkInfos exportNetwork(UUID studyUuid, String format) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export/{format}")
                .buildAndExpand(networkUuid, format).toUriString();

        ResponseEntity<byte[]> responseEntity = restTemplate.getForEntity(networkConversionServerBaseUri + path,
                byte[].class);

        byte[] bytes = responseEntity.getBody();
        String filename = responseEntity.getHeaders().getContentDisposition().getFilename();
        return new ExportNetworkInfos(filename, bytes);
    }

    public void changeLineStatus(@NonNull UUID studyUuid, @NonNull String lineId, @NonNull String status,
            @NonNull UUID nodeUuid) {
        UUID groupUuid = getModificationGroupUuid(nodeUuid);
        String variantId = getVariantId(nodeUuid);

        List<ModificationInfos> modificationInfosList = networkModificationService.changeLineStatus(studyUuid, lineId,
                status, groupUuid, variantId);

        Set<String> substationIds = getSubstationIds(modificationInfosList);

        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid);
        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LINE);
    }

    private void emitStudiesChanged(UUID studyUuid, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
            .build());
    }

    private void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_NODE, nodeUuid)
            .setHeader(HEADER_UPDATE_TYPE, updateType)
            .build());
    }

    private void emitStudyCreationError(UUID studyUuid, String userId, String errorMessage) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
            .setHeader(HEADER_ERROR, errorMessage)
            .build());
    }

    private void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType, Set<String> substationsIds) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_NODE, nodeUuid)
            .setHeader(HEADER_UPDATE_TYPE, updateType)
            .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
            .build());
    }

    private void emitStudyDelete(UUID studyUuid, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDY_DELETE)
            .build());
    }

    private void emitStudyEquipmentDeleted(UUID studyUuid, UUID nodeUuid, String updateType, Set<String> substationsIds, String equipmentType, String equipmentId) {
        sendUpdateMessage(MessageBuilder.withPayload("").setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_NODE, nodeUuid)
            .setHeader(HEADER_UPDATE_TYPE, updateType)
            .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
            .setHeader(HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE, equipmentType)
            .setHeader(HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, equipmentId)
            .build());
    }

    public void assertCaseExists(UUID caseUuid) {
        Boolean caseExists = caseExists(caseUuid);
        if (Boolean.FALSE.equals(caseExists)) {
            throw new StudyException(CASE_NOT_FOUND);
        }
    }

    public void assertLoadFlowRunnable(UUID nodeUuid) {
        Optional<LoadFlowStatus> lfStatus = getLoadFlowStatus(nodeUuid);
        if (lfStatus.isEmpty()) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }

        if (!LoadFlowStatus.NOT_DONE.equals(lfStatus.get())) {
            throw new StudyException(LOADFLOW_NOT_RUNNABLE);
        }
    }

    private void assertLoadFlowNotRunning(UUID nodeUuid) {
        Optional<LoadFlowStatus> lfStatus = getLoadFlowStatus(nodeUuid);
        if (lfStatus.isEmpty()) {
            throw new StudyException(ELEMENT_NOT_FOUND);
        }

        if (LoadFlowStatus.RUNNING.equals(lfStatus.get())) {
            throw new StudyException(LOADFLOW_RUNNING);
        }
    }

    private void assertSecurityAnalysisNotRunning(UUID nodeUuid) {
        String sas = getSecurityAnalysisStatus(nodeUuid);
        if (SecurityAnalysisStatus.RUNNING.name().equals(sas)) {
            throw new StudyException(SECURITY_ANALYSIS_RUNNING);
        }
    }

    public void assertComputationNotRunning(UUID nodeUuid) {
        assertLoadFlowNotRunning(nodeUuid);
        assertSecurityAnalysisNotRunning(nodeUuid);
    }

    public void assertCanModifyNode(UUID nodeUuid) {
        Boolean isReadOnly = networkModificationTreeService.isReadOnly(nodeUuid).orElse(Boolean.FALSE);
        if (isReadOnly) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public static LoadFlowParametersEntity toEntity(LoadFlowParameters parameters) {
        Objects.requireNonNull(parameters);
        return new LoadFlowParametersEntity(parameters.getVoltageInitMode(),
                parameters.isTransformerVoltageControlOn(),
                parameters.isNoGeneratorReactiveLimits(),
                parameters.isPhaseShifterRegulationOn(),
                parameters.isTwtSplitShuntAdmittance(),
                parameters.isShuntCompensatorVoltageControlOn(),
                parameters.isReadSlackBus(),
                parameters.isWriteSlackBus(),
                parameters.isDc(),
                parameters.isDistributedSlack(),
                parameters.getBalanceType());
    }

    public static LoadFlowParameters fromEntity(LoadFlowParametersEntity entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowParameters(entity.getVoltageInitMode(),
            entity.isTransformerVoltageControlOn(),
            entity.isNoGeneratorReactiveLimits(),
            entity.isPhaseShifterRegulationOn(),
            entity.isTwtSplitShuntAdmittance(),
            entity.isSimulShunt(),
            entity.isReadSlackBus(),
            entity.isWriteSlackBus(),
            entity.isDc(),
            entity.isDistributedSlack(),
            entity.getBalanceType(),
            true, // FIXME to persist
            EnumSet.noneOf(Country.class), // FIXME to persist
            LoadFlowParameters.ConnectedComponentMode.MAIN, // FIXME to persist
            true// FIXME to persist
            );
    }

    public static LoadFlowResultEntity toEntity(LoadFlowResult result) {
        return result != null
                ? new LoadFlowResultEntity(result.isOk(),
                      result.getMetrics(),
                      result.getLogs(),
                      result.getComponentResults().stream().map(StudyService::toEntity).collect(Collectors.toList())) : null;
    }

    public static LoadFlowResult fromEntity(LoadFlowResultEntity entity) {
        LoadFlowResult result = null;
        if (entity != null) {
            // This is a workaround to prepare the componentResultEmbeddables which will be used later in the webflux pipeline
            // The goal is to avoid LazyInitializationException
            @SuppressWarnings("unused")
            int ignoreSize = entity.getComponentResults().size();
            @SuppressWarnings("unused")
            int ignoreSize2 = entity.getMetrics().size();

            result = new LoadFlowResultImpl(entity.isOk(),
                    entity.getMetrics(),
                    entity.getLogs(),
                    entity.getComponentResults().stream().map(StudyService::fromEntity).collect(Collectors.toList()));
        }
        return result;
    }

    public static ComponentResultEmbeddable toEntity(LoadFlowResult.ComponentResult componentResult) {
        Objects.requireNonNull(componentResult);
        return new ComponentResultEmbeddable(componentResult.getConnectedComponentNum(),
            componentResult.getSynchronousComponentNum(),
            componentResult.getStatus(),
            componentResult.getIterationCount(),
            componentResult.getSlackBusId(),
            componentResult.getSlackBusActivePowerMismatch());
    }

    public static LoadFlowResult.ComponentResult fromEntity(ComponentResultEmbeddable entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowResultImpl.ComponentResultImpl(entity.getConnectedComponentNum(),
            entity.getSynchronousComponentNum(),
            entity.getStatus(),
            entity.getIterationCount(),
            entity.getSlackBusId(),
            entity.getSlackBusActivePowerMismatch());
    }

    @Transactional(readOnly = true)
    public LoadFlowParameters doGetLoadFlowParameters(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .map(studyEntity -> fromEntity(studyEntity.getLoadFlowParameters()))
            .orElse(null);
    }

    public LoadFlowParameters getLoadFlowParameters(UUID studyUuid) {
        return self.doGetLoadFlowParameters(studyUuid);
    }

    void setLoadFlowParameters(UUID studyUuid, LoadFlowParameters parameters) {
        updateLoadFlowParameters(studyUuid, toEntity(parameters != null ? parameters : LoadFlowParameters.load()));
        invalidateLoadFlowStatusOnAllNodes(studyUuid);
        emitStudyChanged(studyUuid, null, UPDATE_TYPE_LOADFLOW_STATUS);
        invalidateSecurityAnalysisStatusOnAllNodes(studyUuid);
        emitStudyChanged(studyUuid, null, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
    }

    public void invalidateLoadFlowStatusOnAllNodes(UUID studyUuid) {
        networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE);
    }

    @Transactional(readOnly = true)
    public String doGetLoadFlowProvider(UUID studyUuid) {
        return studyRepository.findById(studyUuid)
            .map(StudyEntity::getLoadFlowProvider)
            .orElse("");
    }

    public String getLoadFlowProvider(UUID studyUuid) {
        return self.doGetLoadFlowProvider(studyUuid);
    }

    @Transactional
    public void doUpdateLoadFlowProvider(UUID studyUuid, String provider) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setLoadFlowProvider(provider != null ? provider : defaultLoadflowProvider));
    }

    public void updateLoadFlowProvider(UUID studyUuid, String provider) {
        self.doUpdateLoadFlowProvider(studyUuid, provider);
        networkModificationTreeService.updateStudyLoadFlowStatus(studyUuid, LoadFlowStatus.NOT_DONE);
        emitStudyChanged(studyUuid, null, UPDATE_TYPE_LOADFLOW_STATUS);
    }

    public UUID runSecurityAnalysis(UUID studyUuid, List<String> contingencyListNames, String parameters,
            UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(nodeUuid);

        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String provider = getLoadFlowProvider(studyUuid);
        String variantId = getVariantId(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save");
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.queryParam("contingencyListName", contingencyListNames)
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<String>(parameters, headers);

        UUID result = restTemplate
                .exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();

        updateSecurityAnalysisResultUuid(nodeUuid, result);
        emitStudyChanged(studyUuid, nodeUuid, StudyService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        return result;
    }

    public String getSecurityAnalysisResult(UUID nodeUuid, List<String> limitTypes) {
        Objects.requireNonNull(limitTypes);
        String result = null;
        Optional<UUID> resultUuidOpt = getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
                .queryParam("limitType", limitTypes).buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            } else {
                throw e;
            }
        }

        return result;
    }

    public Integer getContingencyCount(UUID studyUuid, List<String> contingencyListNames, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(contingencyListNames);
        Objects.requireNonNull(nodeUuid);

        UUID uuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        return contingencyListNames.stream().map(contingencyListName -> {
            var uriComponentsBuilder = UriComponentsBuilder
                    .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{contingencyListName}/export")
                    .queryParam("networkUuid", uuid);
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(contingencyListName).toUriString();

            List<Contingency> contingencies = restTemplate.exchange(actionsServerBaseUri + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Contingency>>() {
                    }).getBody();

            return contingencies.size();
        }).reduce(0, Integer::sum);
    }

    byte[] getSubstationSvg(UUID studyUuid, String substationId, DiagramParameters diagramParameters,
            String substationLayout, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/substation-svg/{networkUuid}/{substationId}")
                .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
                .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
                .queryParam(QUERY_PARAM_SUBSTATION_LAYOUT, substationLayout);
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid, substationId).toUriString();

        return restTemplate.getForObject(singleLineDiagramServerBaseUri + path, byte[].class);
    }

    String getSubstationSvgAndMetadata(UUID studyUuid, String substationId, DiagramParameters diagramParameters,
            String substationLayout, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION
                        + "/substation-svg-and-metadata/{networkUuid}/{substationId}")
                .queryParam(QUERY_PARAM_USE_NAME, diagramParameters.isUseName())
                .queryParam(QUERY_PARAM_CENTER_LABEL, diagramParameters.isLabelCentered())
                .queryParam(QUERY_PARAM_DIAGONAL_LABEL, diagramParameters.isDiagonalLabel())
                .queryParam(QUERY_PARAM_TOPOLOGICAL_COLORING, diagramParameters.isTopologicalColoring())
                .queryParam(QUERY_PARAM_SUBSTATION_LAYOUT, substationLayout);
        if (diagramParameters.getComponentLibrary() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_COMPONENT_LIBRARY, diagramParameters.getComponentLibrary());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid, substationId).toUriString();

        return restTemplate.getForObject(singleLineDiagramServerBaseUri + path, String.class);
    }

    public String getSecurityAnalysisStatus(UUID nodeUuid) {
        String result = null;
        Optional<UUID> resultUuidOpt = getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
                .buildAndExpand(resultUuidOpt.get()).toUriString();

        try {
            result = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }

        return result;
    }

    private void invalidateSaStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void invalidateSecurityAnalysisStatus(UUID nodeUuid) {
        invalidateSaStatus(networkModificationTreeService.getSecurityAnalysisResultUuidsFromNode(nodeUuid));
    }

    public void invalidateSecurityAnalysisStatusOnAllNodes(UUID studyUuid) {
        invalidateSaStatus(networkModificationTreeService.getStudySecurityAnalysisResultUuids(studyUuid));
    }

    void setCaseServerBaseUri(String caseServerBaseUri) {
        this.caseServerBaseUri = caseServerBaseUri;
    }

    void setNetworkConversionServerBaseUri(String networkConversionServerBaseUri) {
        this.networkConversionServerBaseUri = networkConversionServerBaseUri;
    }

    void setGeoDataServerBaseUri(String geoDataServerBaseUri) {
        this.geoDataServerBaseUri = geoDataServerBaseUri;
    }

    void setSingleLineDiagramServerBaseUri(String singleLineDiagramServerBaseUri) {
        this.singleLineDiagramServerBaseUri = singleLineDiagramServerBaseUri;
    }

    void setNetworkMapServerBaseUri(String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }

    void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }

    public void setSecurityAnalysisServerBaseUri(String securityAnalysisServerBaseUri) {
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
    }

    public void setActionsServerBaseUri(String actionsServerBaseUri) {
        this.actionsServerBaseUri = actionsServerBaseUri;
    }

    public void stopSecurityAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new Receiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
    }

    @Bean
    public Consumer<Message<String>> consumeSaStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Security analysis stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete security analysis result in database
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                    // send notification for stopped computation
                    UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);

                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    private StudyEntity insertStudyEntity(UUID uuid, String userId, UUID networkUuid, String networkId,
            String caseFormat, UUID caseUuid, boolean casePrivate, LoadFlowParametersEntity loadFlowParameters) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(networkId);
        Objects.requireNonNull(caseFormat);
        Objects.requireNonNull(caseUuid);
        Objects.requireNonNull(loadFlowParameters);

        StudyEntity studyEntity = new StudyEntity(uuid, userId, LocalDateTime.now(ZoneOffset.UTC), networkUuid, networkId, caseFormat, caseUuid, casePrivate, defaultLoadflowProvider, loadFlowParameters);
        return insertStudy(studyEntity);
    }

    @Transactional
    public StudyEntity insertStudy(StudyEntity studyEntity) {
        var study = studyRepository.save(studyEntity);
        // create 2 nodes : root node, modification node 0
        NodeEntity rootNodeEntity = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode modificationNode = NetworkModificationNode
            .builder()
            .name("modification node 0")
            .variantId(FIRST_VARIANT_ID)
            .loadFlowStatus(LoadFlowStatus.NOT_DONE)
            .buildStatus(BuildStatus.BUILT)
            .build();
        networkModificationTreeService.createNode(studyEntity.getId(), rootNodeEntity.getIdNode(), modificationNode, InsertMode.AFTER);

        return study;
    }

    void updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        networkModificationTreeService.updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid);
    }

    void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        networkModificationTreeService.updateLoadFlowStatus(nodeUuid, loadFlowStatus);
    }

    private StudyCreationRequestEntity insertStudyCreationRequestEntity(String userId, UUID studyUuid) {
        StudyCreationRequestEntity studyCreationRequestEntity = new StudyCreationRequestEntity(
                studyUuid == null ? UUID.randomUUID() : studyUuid, userId, LocalDateTime.now(ZoneOffset.UTC));
        return studyCreationRequestRepository.save(studyCreationRequestEntity);
    }

    private void updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult,
            LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        networkModificationTreeService.updateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus,
                updateChildren);
    }

    private void updateLoadFlowParameters(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity) {
        self.doUpdateLoadFlowParameters(studyUuid, loadFlowParametersEntity);
    }

    @Transactional
    public void doUpdateLoadFlowParameters(UUID studyUuid, LoadFlowParametersEntity loadFlowParametersEntity) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        studyEntity.ifPresent(studyEntity1 -> studyEntity1.setLoadFlowParameters(loadFlowParametersEntity));
    }

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        studyUpdatePublisher.send("publishStudyUpdate-out-0", message);
    }

    List<String> getAvailableSvgComponentLibraries() {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SINGLE_LINE_DIAGRAM_API_VERSION + "/svg-component-libraries").toUriString();

        return restTemplate.exchange(singleLineDiagramServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<String>>() {
                }).getBody();
    }

    UUID getModificationGroupUuid(UUID nodeUuid) {
        return networkModificationTreeService.getModificationGroupUuid(nodeUuid);
    }

    public String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    public void createEquipment(UUID studyUuid, String createEquipmentAttributes, ModificationType modificationType,
            UUID nodeUuid) {
        UUID groupUuid = getModificationGroupUuid(nodeUuid);
        String variantId = getVariantId(nodeUuid);

        List<EquipmentModificationInfos> equipmentModificationInfosList = networkModificationService
                .createEquipment(studyUuid, createEquipmentAttributes, groupUuid, modificationType, variantId);
        Set<String> substationIds = getSubstationIds(equipmentModificationInfosList);

        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid);
    }

    public void modifyEquipment(UUID studyUuid, String modifyEquipmentAttributes, ModificationType modificationType,
            UUID nodeUuid) {
        UUID groupUuid = getModificationGroupUuid(nodeUuid);
        String variantId = getVariantId(nodeUuid);

        List<EquipmentModificationInfos> equipmentModificationInfosList = networkModificationService
                .modifyEquipment(studyUuid, modifyEquipmentAttributes, groupUuid, modificationType, variantId);
        Set<String> substationIds = getSubstationIds(equipmentModificationInfosList);

        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, substationIds);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid);
    }

    public void updateEquipmentCreation(UUID studyUuid, String createEquipmentAttributes,
            ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {
        try {
            networkModificationService.updateEquipmentCreation(createEquipmentAttributes, modificationType,
                    modificationUuid);
            networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        } finally {
            updateStatuses(studyUuid, nodeUuid, false);
        }
    }

    public void updateEquipmentModification(UUID studyUuid, String modifyEquipmentAttributes, ModificationType modificationType, UUID nodeUuid, UUID modificationUuid) {
        networkModificationService.updateEquipmentModification(modifyEquipmentAttributes, modificationType, modificationUuid);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid, false);
    }

    void deleteEquipment(UUID studyUuid, String equipmentType, String equipmentId, UUID nodeUuid) {
        UUID groupUuid = getModificationGroupUuid(nodeUuid);
        String variantId = getVariantId(nodeUuid);

        List<EquipmentDeletionInfos> equipmentDeletionInfosList = networkModificationService.deleteEquipment(studyUuid,
                equipmentType, equipmentId, groupUuid, variantId);

        equipmentDeletionInfosList.forEach(deletionInfo -> {
            emitStudyEquipmentDeleted(studyUuid, nodeUuid, UPDATE_TYPE_STUDY, deletionInfo.getSubstationIds(),
                    deletionInfo.getEquipmentType(), deletionInfo.getEquipmentId());
        });

        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid);
    }

    List<VoltageLevelInfos> getVoltageLevels(UUID studyUuid, UUID nodeUuid) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/voltage-levels");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid).toUriString();

        List<VoltageLevelMapData> voltageLevelsMapData = restTemplate.exchange(networkMapServerBaseUri + path,
                HttpMethod.GET, null, new ParameterizedTypeReference<List<VoltageLevelMapData>>() {
                }).getBody();

        return voltageLevelsMapData.stream().map(e -> VoltageLevelInfos.builder().id(e.getId()).name(e.getName())
                .substationId(e.getSubstationId()).build()).collect(Collectors.toList());
    }

    List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId,
            String busPath) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + busPath);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, voltageLevelId).toUriString();

        return restTemplate.exchange(networkMapServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<IdentifiableInfos>>() {
                }).getBody();
    }

    List<IdentifiableInfos> getVoltageLevelBuses(UUID studyUuid, UUID nodeUuid, String voltageLevelId) {
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuid, voltageLevelId, "configured-buses");
    }

    List<IdentifiableInfos> getVoltageLevelBusbarSections(UUID studyUuid, UUID nodeUuid, String voltageLevelId) {
        return getVoltageLevelBusesOrBusbarSections(studyUuid, nodeUuid, voltageLevelId, "busbar-sections");
    }

    public Optional<LoadFlowStatus> getLoadFlowStatus(UUID nodeUuid) {
        return networkModificationTreeService.getLoadFlowStatus(nodeUuid);
    }

    public Optional<UUID> getSecurityAnalysisResultUuid(UUID nodeUuid) {
        return networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);
    }

    @Transactional(readOnly = true)
    public UUID getStudyUuidFromNodeUuid(UUID nodeUuid) {
        return networkModificationTreeService.getStudyUuidForNodeId(nodeUuid);
    }

    public LoadFlowInfos getLoadFlowInfos(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<LoadFlowInfos> lfInfos = networkModificationTreeService.getLoadFlowInfos(nodeUuid);

        if (lfInfos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return lfInfos.get();
    }

    private BuildInfos getBuildInfos(UUID nodeUuid) {
        return networkModificationTreeService.getBuildInfos(nodeUuid);
    }

    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        BuildInfos buildInfos = getBuildInfos(nodeUuid);
        networkModificationService.buildNode(studyUuid, nodeUuid, buildInfos);
        updateBuildStatus(nodeUuid, BuildStatus.BUILDING);
    }

    public void stopBuild(@NonNull UUID studyUuid, @NonNull UUID nodeUuid) {
        networkModificationService.stopBuild(studyUuid, nodeUuid);
    }

    @Bean
    public Consumer<Message<String>> consumeBuildResult() {
        return message -> {
            Set<String> substationsIds = Stream.of(message.getPayload().trim().split(",")).collect(Collectors.toSet());
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Build completed for node '{}'", receiverObj.getNodeUuid());

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.BUILT);

                    UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_BUILD_COMPLETED, substationsIds);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
            return;
        };
    }

    @Bean
    public Consumer<Message<String>> consumeBuildStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

                    LOGGER.info("Build stopped for node '{}'", receiverObj.getNodeUuid());

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.NOT_BUILT);
                    // send notification
                    UUID studyUuid = self.getStudyUuidFromNodeUuid(receiverObj.getNodeUuid());
                    emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), UPDATE_TYPE_BUILD_CANCELLED);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
            return;
        };
    }

    void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        networkModificationTreeService.updateBuildStatus(nodeUuid, buildStatus);
    }

    void invalidateBuildStatus(UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        networkModificationTreeService.invalidateBuildStatus(nodeUuid, invalidateOnlyChildrenBuildStatus);
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid) {
        updateStatuses(studyUuid, nodeUuid, true);
    }

    private void updateStatuses(UUID studyUuid, UUID nodeUuid, boolean invalidateOnlyChildrenBuildStatus) {
        updateLoadFlowStatus(nodeUuid, LoadFlowStatus.NOT_DONE);
        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_LOADFLOW_STATUS);
        invalidateSecurityAnalysisStatus(nodeUuid);
        emitStudyChanged(studyUuid, nodeUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        invalidateBuildStatus(nodeUuid, invalidateOnlyChildrenBuildStatus);
    }

    public void changeModificationActiveState(@NonNull UUID studyUuid, @NonNull UUID nodeUuid,
            @NonNull UUID modificationUuid, boolean active) {
        if (!self.getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
        networkModificationTreeService.handleExcludeModification(nodeUuid, modificationUuid, active);
        updateStatuses(studyUuid, nodeUuid, false);
    }

    @Transactional
    public void deleteModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids) {
        if (!getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }

        UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
        networkModificationService.deleteModifications(groupId, modificationsUuids);
        networkModificationTreeService.removeModificationsToExclude(nodeUuid, modificationsUuids);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
        updateStatuses(studyUuid, nodeUuid, false);
    }

    private void deleteSaResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(securityAnalysisServerBaseUri + path);
    }

    @Transactional
    public DeleteNodeInfos doDeleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren) {
        DeleteNodeInfos deleteNodeInfos = new DeleteNodeInfos();
        deleteNodeInfos.setNetworkUuid(networkStoreService.doGetNetworkUuid(studyUuid).orElse(null));
        networkModificationTreeService.doDeleteNode(studyUuid, nodeId, deleteChildren, deleteNodeInfos);
        return deleteNodeInfos;
    }

    public void deleteNode(UUID studyUuid, UUID nodeId, boolean deleteChildren) {
        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());
        DeleteNodeInfos deleteNodeInfos = self.doDeleteNode(studyUuid, nodeId, deleteChildren);

        try {
            deleteNodeInfos.getModificationGroupUuids().forEach(networkModificationService::deleteModifications);
            deleteNodeInfos.getSecurityAnalysisResultUuids().forEach(this::deleteSaResult);
            networkStoreService.deleteVariants(deleteNodeInfos.getNetworkUuid(), deleteNodeInfos.getVariantIds());
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }

        if (startTime.get() != null) {
            LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId, studyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
    }

    public void reindexStudy(UUID studyUuid) {
        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        if (studyEntity.isPresent()) {
            StudyEntity study = studyEntity.get();

            CreatedStudyBasicInfos studyInfos = toCreatedStudyBasicInfos(study);
            UUID networkUuid = study.getNetworkUuid();

            // reindex study in elasticsearch
            studyInfosService.recreateStudyInfos(studyInfos);

            // reindex study network equipments in elasticsearch
            String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/reindex-all")
                .buildAndExpand(networkUuid)
                .toUriString();

            try {
                restTemplate.exchange(networkConversionServerBaseUri + path, HttpMethod.POST, null, Void.class);
            } catch (HttpStatusCodeException e) {
                LOGGER.error(e.toString(), e);
                throw e;
            }
            invalidateBuildStatus(networkModificationTreeService.getStudyRootNodeUuid(studyUuid), false);
            LOGGER.info("Study with id = '{}' has been reindexed", studyUuid);
        } else {
            throw new StudyException(STUDY_NOT_FOUND);
        }
    }

    public void reorderModification(UUID studyUuid, UUID nodeUuid, UUID modificationUuid, UUID beforeUuid) {
        checkStudyContainsNode(studyUuid, nodeUuid);
        UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
        networkModificationService.reorderModification(groupUuid, modificationUuid, beforeUuid);
        updateStatuses(studyUuid, nodeUuid);
        networkModificationTreeService.notifyModificationNodeChanged(studyUuid, nodeUuid);
    }

    private void checkStudyContainsNode(UUID studyUuid, UUID nodeUuid) {
        if (!getStudyUuidFromNodeUuid(nodeUuid).equals(studyUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public String getDefaultLoadflowProviderValue() {
        return defaultLoadflowProvider;
    }

    private Set<String> getSubstationIds(List<? extends ModificationInfos> modificationInfosList) {
        return modificationInfosList.stream().flatMap(modification -> modification.getSubstationIds().stream())
                .collect(Collectors.toSet());
    }
}
