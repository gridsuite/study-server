/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.elasticsearch.index.query.QueryBuilders;
import org.gridsuite.study.server.dto.EquipmentInfos;
import org.gridsuite.study.server.dto.EquipmentType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.lang.NonNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class to implement elasticsearch indexing
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class EquipmentInfosServiceImpl implements EquipmentInfosService {

    private static final int PAGE_MAX_SIZE = 1000;

    private final EquipmentInfosRepository equipmentInfosRepository;

    private final ElasticsearchOperations elasticsearchOperations;

    public EquipmentInfosServiceImpl(EquipmentInfosRepository equipmentInfosRepository, ElasticsearchOperations elasticsearchOperations) {
        this.equipmentInfosRepository = equipmentInfosRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public EquipmentInfos add(@NonNull EquipmentInfos equipmentInfos) {
        return equipmentInfosRepository.save(equipmentInfos);
    }

    @Override
    public Iterable<EquipmentInfos> findAll(@NonNull UUID networkUuid) {
        return equipmentInfosRepository.findAllByNetworkUuid(networkUuid);
    }

    @Override
    public void deleteAll(@NonNull UUID networkUuid) {
        equipmentInfosRepository.deleteAllByNetworkUuid(networkUuid);
    }

    @Override
    public List<EquipmentInfos> search(@NonNull final String query) {
        NativeSearchQuery nativeSearchQuery = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.queryStringQuery(query))
            .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
            .build();

        Map<String, List<EquipmentInfos>> infos = elasticsearchOperations.search(nativeSearchQuery, EquipmentInfos.class)
            .stream()
            .map(SearchHit::getContent)
            .collect(Collectors.groupingBy(EquipmentInfos::getType));

        // Sorted by equipment type
        return Stream.of(
                infos.getOrDefault(EquipmentType.SUBSTATION.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.VOLTAGE_LEVEL.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.LINE.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.TWO_WINDINGS_TRANSFORMER.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.THREE_WINDINGS_TRANSFORMER.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.HVDC.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.GENERATOR.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.BATTERY.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.LOAD.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.SHUNT_COMPENSATOR.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.DANGLING_LINE.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.STATIC_VAR_COMPENSATOR.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.HVDC_CONVERTER_STATION.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.BUSBAR_SECTION.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.BREAKER.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.DISCONNECTOR.name(), Collections.emptyList()),
                infos.getOrDefault(EquipmentType.LOAD_BREAK_SWITCH.name(), Collections.emptyList())
            )
            .flatMap(Collection::stream)
            .sorted(Comparator.comparing(EquipmentInfos::getId))
            .collect(Collectors.toList());
    }
}
