/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import lombok.Getter;

import com.powsybl.iidm.network.VariantManagerConstants;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.client.elc.Queries;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Map.entry;

/**
 * A class to implement elasticsearch indexing
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Service
public class EquipmentInfosService {

    public enum FieldSelector {
        NAME, ID
    }

    private static final int PAGE_MAX_SIZE = 400;

    public static final Map<String, Integer> EQUIPMENT_TYPE_SCORES = Map.ofEntries(
            entry("SUBSTATION", 15),
            entry("VOLTAGE_LEVEL", 14),
            entry("LINE", 13),
            entry("TWO_WINDINGS_TRANSFORMER", 12),
            entry("THREE_WINDINGS_TRANSFORMER", 11),
            entry("HVDC_LINE", 10),
            entry("GENERATOR", 9),
            entry("BATTERY", 8),
            entry("LOAD", 7),
            entry("SHUNT_COMPENSATOR", 6),
            entry("DANGLING_LINE", 5),
            entry("STATIC_VAR_COMPENSATOR", 4),
            entry("HVDC_CONVERTER_STATION", 3),
            entry("BUSBAR_SECTION", 2),
            entry("BUS", 1),
            entry("SWITCH", 0)
    );

    static final String NETWORK_UUID = "networkUuid.keyword";
    static final String VARIANT_ID = "variantId.keyword";
    static final String EQUIPMENT_NAME = "equipmentName.fullascii";
    static final String EQUIPMENT_ID = "equipmentId.fullascii";
    static final String EQUIPMENT_TYPE = "equipmentType.keyword";

    private final EquipmentInfosRepository equipmentInfosRepository;

    private final TombstonedEquipmentInfosRepository tombstonedEquipmentInfosRepository;

    private final ElasticsearchOperations elasticsearchOperations;

    @Value(ESConfig.STUDY_INDEX_NAME)
    @Getter
    private String studyIndexName;

    @Value(ESConfig.EQUIPMENTS_INDEX_NAME)
    @Getter
    private String equipmentsIndexName;

    @Value(ESConfig.TOMBSTONED_EQUIPMENTS_INDEX_NAME)
    @Getter
    private String tombstonedEquipmentsIndexName;

    public EquipmentInfosService(EquipmentInfosRepository equipmentInfosRepository, TombstonedEquipmentInfosRepository tombstonedEquipmentInfosRepository, ElasticsearchOperations elasticsearchOperations) {
        this.equipmentInfosRepository = equipmentInfosRepository;
        this.tombstonedEquipmentInfosRepository = tombstonedEquipmentInfosRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public EquipmentInfos addEquipmentInfos(@NonNull EquipmentInfos equipmentInfos) {
        return equipmentInfosRepository.save(equipmentInfos);
    }

    public TombstonedEquipmentInfos addTombstonedEquipmentInfos(TombstonedEquipmentInfos tombstonedEquipmentInfos) {
        return tombstonedEquipmentInfosRepository.save(tombstonedEquipmentInfos);
    }

    public List<EquipmentInfos> findAllEquipmentInfos(@NonNull UUID networkUuid) {
        return equipmentInfosRepository.findAllByNetworkUuid(networkUuid);
    }

    public List<TombstonedEquipmentInfos> findAllTombstonedEquipmentInfos(@NonNull UUID networkUuid) {
        return tombstonedEquipmentInfosRepository.findAllByNetworkUuid(networkUuid);
    }

    public void deleteVariants(@NonNull UUID networkUuid, List<String> variantIds) {
        variantIds.forEach(variantId -> {
            equipmentInfosRepository.deleteAllByNetworkUuidAndVariantId(networkUuid, variantId);
            tombstonedEquipmentInfosRepository.deleteAllByNetworkUuidAndVariantId(networkUuid, variantId);
        });
    }

    public long getEquipmentInfosCount() {
        return equipmentInfosRepository.count();
    }

    public long getTombstonedEquipmentInfosCount() {
        return tombstonedEquipmentInfosRepository.count();
    }

    public long getEquipmentInfosCount(@NonNull UUID networkUuid) {
        return equipmentInfosRepository.countByNetworkUuid(networkUuid);
    }

    public long getTombstonedEquipmentInfosCount(@NonNull UUID networkUuid) {
        return tombstonedEquipmentInfosRepository.countByNetworkUuid(networkUuid);
    }

    public void deleteAllByNetworkUuid(@NonNull UUID networkUuid) {
        equipmentInfosRepository.deleteAllByNetworkUuid(networkUuid);
        tombstonedEquipmentInfosRepository.deleteAllByNetworkUuid(networkUuid);

    }

    public static String escapeLucene(String s) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '+', '\\', '-', '!', '(', ')', ':', '^', '[', ']', '"', '{', '}', '~', '*', '?', '|', '&', '/', ' ': // white space has to be escaped, too
                    sb.append('\\');
                    break;
                default:
                    // do nothing but appease sonarlint
            }

            sb.append(c);
        }

        return sb.toString();
    }

    private List<FunctionScore> buildFunctionScores(EquipmentInfosService.FieldSelector fieldSelector, String userInput) {
        List<FunctionScore> functionScores = new ArrayList<>();
        FunctionScore functionScore = new FunctionScore.Builder()
                .filter(builder ->
                        builder.match(
                                matchBuilder -> matchBuilder
                                        .field(fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID)
                                        .query(FieldValue.of(escapeLucene(userInput))))
                )
                .weight((double) EQUIPMENT_TYPE_SCORES.size())
                .build();
        functionScores.add(functionScore);

        for (Map.Entry<String, Integer> equipmentTypeScore : EQUIPMENT_TYPE_SCORES.entrySet()) {
            functionScore = new FunctionScore.Builder()
                    .filter(builder ->
                            builder.match(
                                    matchBuilder -> matchBuilder
                                            .field("equipmentType")
                                            .query(FieldValue.of(equipmentTypeScore.getKey())))
                    )
                    .weight((double) equipmentTypeScore.getValue())
                    .build();
            functionScores.add(functionScore);
        }
        return functionScores;
    }

    private String buildTombstonedEquipmentSearchQuery(UUID networkUuid, String variantId) {
        return String.format(NETWORK_UUID + ":(%s) AND " + VARIANT_ID + ":(%s)", networkUuid, variantId);
    }

    private BoolQuery buildSearchEquipmentsQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String initialVariantId, String variantId, String equipmentType) {
        // If search requires boolean logic or advanced text analysis, then use queryStringQuery.
        // Otherwise, use wildcardQuery for simple text search.
        WildcardQuery equipmentSearchQuery = Queries.wildcardQuery(fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID, "*" + escapeLucene(userInput) + "*");
        TermQuery networkUuidSearchQuery = Queries.termQuery(NETWORK_UUID, networkUuid.toString());
        TermsQuery variantIdSearchQuery = variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID) ?
                new TermsQuery.Builder().field(VARIANT_ID).terms(new TermsQueryField.Builder().value(List.of(FieldValue.of(initialVariantId))).build()).build() :
                new TermsQuery.Builder().field(VARIANT_ID).terms(new TermsQueryField.Builder().value(List.of(FieldValue.of(initialVariantId), FieldValue.of(variantId))).build()).build();

        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder()
                .filter(
                        equipmentSearchQuery._toQuery(),
                        networkUuidSearchQuery._toQuery(),
                        variantIdSearchQuery._toQuery()
                );

        if (!StringUtils.isEmpty(equipmentType)) {
            boolQueryBuilder.filter(Queries.termQuery(EQUIPMENT_TYPE, equipmentType)._toQuery());
        } else {
            List<FunctionScore> functionScores = buildFunctionScores(fieldSelector, userInput);
            FunctionScoreQuery functionScoreQuery = new FunctionScoreQuery.Builder().functions(functionScores).build();
            boolQueryBuilder.must(functionScoreQuery._toQuery());
        }
        return boolQueryBuilder.build();
    }

    private void cleanModifiedEquipments(List<EquipmentInfos> equipmentInfos) {
        Set<EquipmentInfos> equipmentToDelete = new HashSet<>();
        Map<String, List<EquipmentInfos>> groupedById = equipmentInfos.stream()
                .collect(Collectors.groupingBy(EquipmentInfos::getId));

        groupedById.forEach((id, equipments) -> {
            if (equipments.size() > 1) {
                equipmentToDelete.addAll(
                        equipments.stream()
                                .filter(e -> VariantManagerConstants.INITIAL_VARIANT_ID.equals(e.getVariantId()))
                                .collect(Collectors.toList())
                );
            }
        });
        equipmentInfos.removeAll(equipmentToDelete);
    }

    private List<EquipmentInfos> cleanRemovedEquipments(UUID networkUuid, String variantId, List<EquipmentInfos> equipmentInfos) {
        String queryTombstonedEquipments = buildTombstonedEquipmentSearchQuery(networkUuid, variantId);
        Set<String> removedEquipmentIdsInVariant = searchTombstonedEquipments(queryTombstonedEquipments)
                .stream()
                .map(TombstonedEquipmentInfos::getId)
                .collect(Collectors.toSet());

        return equipmentInfos
                .stream()
                .filter(ei -> !removedEquipmentIdsInVariant.contains(ei.getId()))
                .collect(Collectors.toList());
    }

    private List<EquipmentInfos> cleanModifiedAndRemovedEquipments(UUID networkUuid, String variantId, List<EquipmentInfos> equipmentInfos) {
        cleanModifiedEquipments(equipmentInfos);
        return cleanRemovedEquipments(networkUuid, variantId, equipmentInfos);
    }

    public List<EquipmentInfos> searchEquipments(@lombok.NonNull UUID networkUuid, @lombok.NonNull String variantId, @lombok.NonNull String userInput, @lombok.NonNull FieldSelector fieldSelector, String equipmentType) {
        String effectiveVariantId = variantId.isEmpty() ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;

        BoolQuery query = buildSearchEquipmentsQuery(userInput, fieldSelector, networkUuid,
                VariantManagerConstants.INITIAL_VARIANT_ID, variantId, equipmentType);
        List<EquipmentInfos> equipmentInfos = searchEquipments(query);
        return variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID) ? equipmentInfos : cleanModifiedAndRemovedEquipments(networkUuid, effectiveVariantId, equipmentInfos);
    }

    public List<EquipmentInfos> searchEquipments(@NonNull final BoolQuery query) {
        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(query._toQuery())
                .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
                .build();

        return elasticsearchOperations.search(nativeQuery, EquipmentInfos.class)
                .stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList()); //.collect(Collectors.toList()) instead of .toList() to update list before returning
    }

    public List<TombstonedEquipmentInfos> searchTombstonedEquipments(@NonNull final String query) {
        NativeQuery nativeSearchQuery = new NativeQueryBuilder()
                .withQuery(QueryStringQuery.of(qs -> qs.query(query))._toQuery())
                .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
                .build();

        return elasticsearchOperations.search(nativeSearchQuery, TombstonedEquipmentInfos.class)
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }
}
