/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(description = "Basic equipment infos")
public class BasicEquipmentInfos {
    @Id
    protected String uniqueId;

    @MultiField(
            mainField = @Field(name = "equipmentId", type = FieldType.Text),
            otherFields = {
                    @InnerField(suffix = "fullascii", type = FieldType.Keyword, normalizer = "fullascii"),
                    @InnerField(suffix = "raw", type = FieldType.Keyword)
            }
    )
    protected String id;

    protected UUID networkUuid;

    protected String variantId;
}
