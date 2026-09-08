/*
  Copyright (c) 2026, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ReferenceAttributes {
    public enum ReferenceType {
        STUDY_NODE,
        STUDY_NODE_NETWORK_MODIFICATION,
        DIRECTORY_NETWORK_MODIFICATION,
    }

    // id of the reference modification
    @NonNull private UUID referenceId;
    // Container where the reference is used (see ReferenceType for the meaning of its ids)
    @NonNull private ReferenceContainer referenceContainer;
    @NonNull private ReferenceType referenceType;

    public static ReferenceAttributes createReferenceAttributes(UUID referenceId, UUID rootContainerId, UUID containerId, ReferenceType referenceType) {
        return ReferenceAttributes.builder()
                .referenceId(referenceId)
                .referenceContainer(ReferenceContainer.builder().rootContainerId(rootContainerId).containerId(containerId).build())
                .referenceType(referenceType)
                .build();
    }
}

