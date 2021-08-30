/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString(callSuper = true)
@Schema(description = "Elementary modification attributes")
public class ElementaryModificationInfos extends ModificationInfos {

    @Schema(description = "Equipment ID")
    private String equipmentId;

    @Schema(description = "Substations ID")
    @Builder.Default
    private Set<String> substationIds = Set.of();

    @Schema(description = "Equipment attribute name")
    private String equipmentAttributeName;

    @Schema(description = "Equipment attribute value")
    private Object equipmentAttributeValue;
}
