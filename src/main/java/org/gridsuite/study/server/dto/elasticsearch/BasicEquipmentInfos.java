/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.elasticsearch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.AccessType;
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
@Setter
@ToString
@EqualsAndHashCode
@FieldNameConstants
@Schema(description = "Basic equipment infos")
public class BasicEquipmentInfos {
    @Id
    @AccessType(AccessType.Type.PROPERTY)
    @SuppressWarnings("unused")
    public String getUniqueId() {
        return networkUuid + "_" + variantId + "_" + id;
    }

    @SuppressWarnings("unused")
    public void setUniqueId(String uniqueId) {
        // No setter because it a composite value
    }

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
