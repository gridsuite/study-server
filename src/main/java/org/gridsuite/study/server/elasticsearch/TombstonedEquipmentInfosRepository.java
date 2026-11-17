/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.elasticsearch.BasicEquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.lang.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
public interface TombstonedEquipmentInfosRepository extends ElasticsearchRepository<TombstonedEquipmentInfos, String> {
    List<TombstonedEquipmentInfos> findAllByNetworkUuid(@NonNull UUID networkUuid);

    default Set<TombstonedEquipmentInfos> findByIdInAndNetworkUuidAndVariantId(@NonNull List<String> equipmentIds, @NonNull UUID networkUuid, @NonNull String variantId) {
        Set<TombstonedEquipmentInfos> tombstonedEquipmentInfos = new HashSet<>();
        findAllById(equipmentIds.stream().distinct().map(id -> BasicEquipmentInfos.buildUniqueId(networkUuid, variantId, id)).toList())
            .forEach(tombstonedEquipmentInfos::add);
        return tombstonedEquipmentInfos;
    }

    void deleteAllByNetworkUuid(@NonNull UUID networkUuid);

    void deleteAllByNetworkUuidAndVariantId(@NonNull UUID networkUuid, @NonNull String variantId);

    long countByNetworkUuid(@NonNull UUID networkUuid);
}
