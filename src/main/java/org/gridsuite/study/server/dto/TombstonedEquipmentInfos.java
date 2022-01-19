/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Document(indexName = "#{@environment.getProperty('index.prefix')}tombstoned-equipments")
@Setting(settingPath = "elasticsearch_settings.json")
@TypeAlias(value = "TombstonedEquipmentInfos")
public class TombstonedEquipmentInfos {
    @Id
    String uniqueId;

    @MultiField(
            mainField = @Field(name = "equipmentId", type = FieldType.Text),
            otherFields = {
                    @InnerField(suffix = "fullascii", type = FieldType.Keyword, normalizer = "fullascii"),
                    @InnerField(suffix = "raw", type = FieldType.Keyword)
            }
    )
    String id;

    UUID networkUuid;

    String variantId;
}
