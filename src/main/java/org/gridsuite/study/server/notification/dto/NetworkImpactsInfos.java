/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.Set;

@AllArgsConstructor
@Setter
@Getter
@Builder
@NoArgsConstructor
@ToString
public class NetworkImpactsInfos {
    @Builder.Default
    private Set<String> impactedSubstationsIds = Set.of();

    @Builder.Default
    private Set<EquipmentDeletionInfos> deletedEquipments = Set.of();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Builder.Default
    private Set<String> impactedElementTypes = Set.of();
}

