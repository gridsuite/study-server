/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.TombstonedEquipmentInfos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@ConditionalOnExpression("'${spring.data.elasticsearch.enabled:false}' == 'true'")
@Lazy
public interface TombstonedEquipmentInfosRepository extends ElasticsearchRepository<TombstonedEquipmentInfos, String> {
    List<TombstonedEquipmentInfos> findAllByNetworkUuid(@NonNull UUID networkUuid);

    List<TombstonedEquipmentInfos> findAllByNetworkUuidAndVariantId(@NonNull UUID networkUuid, @NonNull String variantId);

    void deleteAllByNetworkUuid(UUID networkUuid);

    void deleteAllByNetworkUuidAndVariantId(UUID networkUuid, String variantId);
}
