/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
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

import java.util.Set;
import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Document(indexName = "#{@environment.getProperty('index.prefix')}equipments")
@TypeAlias(value = "EquipmentInfos")
public class EquipmentInfos {
    @Id
    String uniqueId;

    @Field("equipmentId")
    String id;

    @Field("equipmentName")
    String name;

    @Field("equipmentType")
    String type;

    @Field(type = FieldType.Nested, includeInParent = true)
    Set<VoltageLevelInfos> voltageLevels;

    UUID networkUuid;
}
