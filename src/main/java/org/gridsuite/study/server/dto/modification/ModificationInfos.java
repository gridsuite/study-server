/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "GROOVY_SCRIPT"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "LOAD_CREATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "LOAD_MODIFICATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "GENERATOR_CREATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "GENERATOR_MODIFICATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "LINE_CREATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "SUBSTATION_CREATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "VOLTAGE_LEVEL_CREATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "SHUNT_COMPENSATOR_CREATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "TWO_WINDINGS_TRANSFORMER_CREATION"),
    @JsonSubTypes.Type(value = EquipmentDeletionInfos.class, name = "EQUIPMENT_DELETION"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "LINE_SPLIT_WITH_VOLTAGE_LEVEL"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "LINE_ATTACH_TO_VOLTAGE_LEVEL"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "LINES_ATTACH_TO_SPLIT_LINES"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "OPERATING_STATUS_MODIFICATION"),
    @JsonSubTypes.Type(value = EquipmentModificationInfos.class, name = "EQUIPMENT_ATTRIBUTE_MODIFICATION"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "LOAD_SCALING"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "DELETE_VOLTAGE_LEVEL_ON_LINE"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "DELETE_ATTACHING_LINE"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "GENERATOR_SCALING"),
    @JsonSubTypes.Type(value = ModificationInfos.class, name = "BALANCES_ADJUSTMENT_MODIFICATION")
})
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@ToString
@Schema(description = "Modification attributes")
public class ModificationInfos {
    @Schema(description = "Modification id")
    private UUID uuid;

    @Schema(description = "Modification date")
    Instant date;

    @Schema(description = "Modification type")
    ModificationType type;

    @Schema(description = "Substations ID")
    @Builder.Default
    private Set<String> substationIds = Set.of();
}
