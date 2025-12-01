/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import lombok.Getter;

import com.powsybl.iidm.network.VariantManagerConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.study.server.dto.elasticsearch.BasicEquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.*;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentInfosService.class);

    public enum FieldSelector {
        NAME, ID
    }

    private static final int PAGE_MAX_SIZE = 400;

    private static final int COMPOSITE_AGGREGATION_BATCH_SIZE = 1000;

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

    public Set<TombstonedEquipmentInfos> findTombstonedEquipmentInfosByIdIn(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull List<String> equipmentIds) {
        return tombstonedEquipmentInfosRepository.findByIdInAndNetworkUuidAndVariantId(
                        equipmentIds,
                        networkUuid,
                        variantId);
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

    private CompositeAggregation buildCompositeAggregation(String field, Map<String, FieldValue> afterKey) {
        List<Map<String, CompositeAggregationSource>> sources = List.of(
                Map.of(field, CompositeAggregationSource.of(s -> s.terms(t -> t.field(field + ".keyword")))
                )
        );

        CompositeAggregation.Builder compositeAggregationBuilder = new CompositeAggregation.Builder()
                .size(COMPOSITE_AGGREGATION_BATCH_SIZE)
                .sources(sources);

        if (afterKey != null) {
            compositeAggregationBuilder.after(afterKey);
        }

        return compositeAggregationBuilder.build();
    }

    /**
     * Constructs a NativeQuery with a composite aggregation.
     *
     * @param compositeName The name of the composite aggregation.
     * @param compositeAggregation The composite aggregation configuration.
     * @return A NativeQuery object configured with the specified composite aggregation.
     */
    private NativeQuery buildCompositeAggregationQuery(String compositeName, CompositeAggregation compositeAggregation) {
        Aggregation aggregation = Aggregation.of(a -> a.composite(compositeAggregation));

        return new NativeQueryBuilder()
                .withAggregation(compositeName, aggregation)
                .build();
    }

    /**
     * This method is used to extract the results of a composite aggregation from Elasticsearch search hits.
     *
     * @param searchHits The search hits returned from an Elasticsearch query.
     * @param compositeName The name of the composite aggregation.
     * @return A Pair consisting of two elements:
     *         The left element of the Pair is a list of maps, where each map represents a bucket's key. Each bucket is a result of the composite aggregation.
     *         The right element of the Pair is the afterKey map, which is used for pagination in Elasticsearch.
     *         If there are no more pages, the afterKey will be null.
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-composite-aggregation.html">Elasticsearch Composite Aggregation Documentation</a>
     */
    private Pair<List<Map<String, FieldValue>>, Map<String, FieldValue>> extractCompositeAggregationResults(SearchHits<EquipmentInfos> searchHits, String compositeName) {
        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();

        List<Map<String, FieldValue>> results = new ArrayList<>();
        if (aggregations != null) {
            Map<String, ElasticsearchAggregation> aggregationList = aggregations.aggregationsAsMap();
            if (!aggregationList.isEmpty()) {
                Aggregate aggregate = aggregationList.get(compositeName).aggregation().getAggregate();
                if (aggregate.isComposite() && aggregate.composite() != null) {
                    for (CompositeBucket bucket : aggregate.composite().buckets().array()) {
                        Map<String, FieldValue> key = bucket.key();
                        results.add(key);
                    }
                    return Pair.of(results, aggregate.composite().afterKey());
                }
            }
        }
        return Pair.of(results, null);
    }

    public List<UUID> getEquipmentInfosDistinctNetworkUuids() {
        List<UUID> networkUuids = new ArrayList<>();
        Map<String, FieldValue> afterKey = null;
        String compositeName = "composite_agg";
        String networkUuidField = BasicEquipmentInfos.Fields.networkUuid;

        do {
            CompositeAggregation compositeAggregation = buildCompositeAggregation(networkUuidField, afterKey);
            NativeQuery query = buildCompositeAggregationQuery(compositeName, compositeAggregation);

            SearchHits<EquipmentInfos> searchHits = elasticsearchOperations.search(query, EquipmentInfos.class);
            Pair<List<Map<String, FieldValue>>, Map<String, FieldValue>> searchResults = extractCompositeAggregationResults(searchHits, compositeName);

            searchResults.getLeft().stream()
                    .map(result -> result.get(networkUuidField))
                    .filter(Objects::nonNull)
                    .map(FieldValue::stringValue)
                    .map(UUID::fromString)
                    .forEach(networkUuids::add);

            afterKey = searchResults.getRight();
        } while (afterKey != null && !afterKey.isEmpty());

        return networkUuids;
    }

    public List<UUID> getOrphanEquipmentInfosNetworkUuids(List<UUID> networkUuidsInDatabase) {
        List<UUID> networkUuids = getEquipmentInfosDistinctNetworkUuids();
        networkUuids.removeAll(networkUuidsInDatabase);
        return networkUuids;
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

    public void deleteEquipmentIndexes(UUID networkUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        deleteAllByNetworkUuid(networkUuid);
        LOGGER.trace("Indexes deletion for network '{}' : {} seconds", networkUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
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
                                        .field(getSelectedEquipmentField(fieldSelector))
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

    private BoolQuery buildSearchEquipmentsQuery(String userInput, EquipmentInfosService.FieldSelector fieldSelector, UUID networkUuid, String variantId, String equipmentType) {
        // If search requires boolean logic or advanced text analysis, then use queryStringQuery.
        // Otherwise, use wildcardQuery for simple text search.
        WildcardQuery equipmentSearchQuery = Queries.wildcardQuery(getSelectedEquipmentField(fieldSelector), "*" + escapeLucene(userInput) + "*");
        TermQuery networkUuidSearchQuery = Queries.termQuery(NETWORK_UUID, networkUuid.toString());
        TermsQuery variantIdSearchQuery = variantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID) ?
                new TermsQuery.Builder().field(VARIANT_ID).terms(new TermsQueryField.Builder().value(List.of(FieldValue.of(VariantManagerConstants.INITIAL_VARIANT_ID))).build()).build() :
                new TermsQuery.Builder().field(VARIANT_ID).terms(new TermsQueryField.Builder().value(List.of(FieldValue.of(VariantManagerConstants.INITIAL_VARIANT_ID), FieldValue.of(variantId))).build()).build();

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

    private List<SortOptions> buildSearchEquipmentsSortOptions(String userInput, EquipmentInfosService.FieldSelector fieldSelector) {
        // Sort by score -> defined by equipmentType
        SortOptions scoreSort = SortOptions.of(s -> s
            .score(sc -> sc
                .order(SortOrder.Desc)
            )
        );

        // Then sort by elements starting by userInput
        SortOptions prefixSort = SortOptions.of(s -> s
            .script(sc -> sc
                .type(ScriptSortType.Number)
                .order(SortOrder.Asc)
                .script(Script.of(scr -> scr
                    .source("""
                        String value = doc[params.field].value;
                        return value.startsWith(params.prefix) ? 0 : 1;
                    """)
                    .params("field", JsonData.of(getSelectedEquipmentField(fieldSelector)))
                    .params("prefix", JsonData.of(userInput))
                ))
            )
        );

        // Then sort alphabetically
        SortOptions alphabeticalOrder = SortOptions.of(s -> s
            .field(sc -> sc
                .field(EQUIPMENT_ID)
                .order(SortOrder.Asc)
            )
        );

        // Sort order is important
        return List.of(scoreSort, prefixSort, alphabeticalOrder);
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
        List<String> equipmentIds = equipmentInfos.stream().map(EquipmentInfos::getId).toList();
        Set<String> tombstonedEquipmentIdsInVariant = findTombstonedEquipmentInfosByIdIn(networkUuid, variantId, equipmentIds)
                .stream()
                .map(TombstonedEquipmentInfos::getId)
                .collect(Collectors.toSet());

        return equipmentInfos
                .stream()
                .filter(ei -> !tombstonedEquipmentIdsInVariant.contains(ei.getId()) ||
                        // If the equipment has been recreated after the creation of a deletion hypothesis
                        !ei.getVariantId().equals(VariantManagerConstants.INITIAL_VARIANT_ID))
                .collect(Collectors.toList());
    }

    private List<EquipmentInfos> cleanModifiedAndRemovedEquipments(UUID networkUuid, String variantId, List<EquipmentInfos> equipmentInfos) {
        cleanModifiedEquipments(equipmentInfos);
        return cleanRemovedEquipments(networkUuid, variantId, equipmentInfos);
    }

    public List<EquipmentInfos> searchEquipments(@lombok.NonNull UUID networkUuid, @lombok.NonNull String variantId, @lombok.NonNull String userInput, @lombok.NonNull FieldSelector fieldSelector, String equipmentType) {
        String effectiveVariantId = variantId.isEmpty() ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;

        BoolQuery query = buildSearchEquipmentsQuery(userInput, fieldSelector, networkUuid,
                variantId, equipmentType);
        List<SortOptions> sortOptions = buildSearchEquipmentsSortOptions(userInput, fieldSelector);
        List<EquipmentInfos> equipmentInfos = searchEquipments(query, sortOptions);
        return effectiveVariantId.equals(VariantManagerConstants.INITIAL_VARIANT_ID) ? equipmentInfos : cleanModifiedAndRemovedEquipments(networkUuid, effectiveVariantId, equipmentInfos);
    }

    public List<EquipmentInfos> searchEquipments(@NonNull final BoolQuery query, @NonNull List<SortOptions> sortOptions) {
        NativeQuery nativeQuery = new NativeQueryBuilder()
            .withQuery(query._toQuery())
            .withSort(sortOptions)
            .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
            .build();

        return elasticsearchOperations.search(nativeQuery, EquipmentInfos.class)
                .stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList()); //.collect(Collectors.toList()) instead of .toList() to update list before returning
    }

    private String getSelectedEquipmentField(EquipmentInfosService.FieldSelector fieldSelector) {
        return fieldSelector == EquipmentInfosService.FieldSelector.NAME ? EQUIPMENT_NAME : EQUIPMENT_ID;
    }
}
