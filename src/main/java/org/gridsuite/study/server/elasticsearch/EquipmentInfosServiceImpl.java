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
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.lang.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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

        SearchHits<EquipmentInfos> searchHits = elasticsearchOperations.search(nativeSearchQuery, EquipmentInfos.class);

        List<EquipmentInfos> infos = searchHits
            .stream()
            .map(SearchHit::getContent)
            .collect(Collectors.toList());

        return Stream.of(
                infos.stream().filter(e -> e.getType().equals(EquipmentType.SUBSTATION.name())).sorted(Comparator.comparing(EquipmentInfos::getId)),
                infos.stream().filter(e -> e.getType().equals(EquipmentType.VOLTAGE_LEVEL.name())).sorted(Comparator.comparing(EquipmentInfos::getId)),
                infos.stream().filter(e -> e.getType().equals(EquipmentType.LINE.name())).sorted(Comparator.comparing(EquipmentInfos::getId)),
                infos.stream().filter(e -> e.getType().equals(EquipmentType.TWO_WINDINGS_TRANSFORMER.name())).sorted(Comparator.comparing(EquipmentInfos::getId)),
                infos.stream().filter(e -> e.getType().equals(EquipmentType.THREE_WINDINGS_TRANSFORMER.name())).sorted(Comparator.comparing(EquipmentInfos::getId)),
                infos.stream().filter(e -> e.getType().equals(EquipmentType.HVDC.name())).sorted(Comparator.comparing(EquipmentInfos::getId)),
                infos.stream().filter(e -> EquipmentType.isInjectionType(e.getType())).sorted(Comparator.comparing(EquipmentInfos::getId)),
                infos.stream().filter(e -> EquipmentType.isSwitchType(e.getType())).sorted(Comparator.comparing(EquipmentInfos::getId))
            )
            .flatMap(e -> e)
            .collect(Collectors.toList());
    }
}
