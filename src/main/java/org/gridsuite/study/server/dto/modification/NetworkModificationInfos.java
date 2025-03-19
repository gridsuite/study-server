/*
  Copyright (c) 2025, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.modification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.modification.dto.ModificationInfos;
import java.util.Map;
import java.util.UUID;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "network modification Infos with activation status by root network ")
public class NetworkModificationInfos {

    @Schema(description = "activation status By rootNetwork uuid")
    Map<UUID, Boolean> activationStatusByRootNetwork;

    @Schema(description = "Modification Infos")
    private ModificationInfos modificationInfos;
}
