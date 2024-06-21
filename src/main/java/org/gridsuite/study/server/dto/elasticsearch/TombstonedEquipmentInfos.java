/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.elasticsearch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import org.gridsuite.study.server.elasticsearch.ESConfig;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Tombstoned equipment infos")
// Keep indexname in sync with the value in SupervisionController
@Document(indexName = ESConfig.TOMBSTONED_EQUIPMENTS_INDEX_NAME)
@Setting(settingPath = "elasticsearch_settings.json")
@TypeAlias(value = "TombstonedEquipmentInfos")
public class TombstonedEquipmentInfos extends BasicEquipmentInfos {
}
