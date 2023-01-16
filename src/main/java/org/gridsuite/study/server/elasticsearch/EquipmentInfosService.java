/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.TombstonedEquipmentInfos;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private final EquipmentInfosRepository equipmentInfosRepository;

    private final TombstonedEquipmentInfosRepository tombstonedEquipmentInfosRepository;

    private final ElasticsearchOperations elasticsearchOperations;

    public EquipmentInfosService(@Lazy EquipmentInfosRepository equipmentInfosRepository, @Lazy TombstonedEquipmentInfosRepository tombstonedEquipmentInfosRepository, ElasticsearchOperations elasticsearchOperations) {
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

    public void deleteAll(@NonNull UUID networkUuid) {
        equipmentInfosRepository.deleteAllByNetworkUuid(networkUuid);
        tombstonedEquipmentInfosRepository.deleteAllByNetworkUuid(networkUuid);

    }

    public List<EquipmentInfos> searchEquipments(@NonNull final String query) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.queryStringQuery(query))
                .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
                .build();
        return elasticsearchOperations.search(nativeSearchQuery, EquipmentInfos.class)
                .stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    public List<EquipmentInfos> searchEquipments(@NonNull final BoolQueryBuilder query) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQueryBuilder()
                .withQuery(query)
                .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
                .withSorts()
                .build();

        return elasticsearchOperations.search(nativeSearchQuery, EquipmentInfos.class)
                .stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    public List<TombstonedEquipmentInfos> searchTombstonedEquipments(@NonNull final String query) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.queryStringQuery(query))
                .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
                .build();

        return elasticsearchOperations.search(nativeSearchQuery, TombstonedEquipmentInfos.class)
                .stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }
}
