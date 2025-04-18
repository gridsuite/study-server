/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author REHILI Ghazwa <ghazwa.rehili@rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(description = "Switch attributes")
public class SwitchStatusInfos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Switch ID")
    private String id;

    @Schema(description = "Switch Status")
    private boolean isOpen;
}
