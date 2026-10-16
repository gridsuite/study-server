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
 * ReferenceContainer represents the information that makes it easy to locate the reference where it is used.
 * It depends on the type of reference:
 * STUDY_NODE: rootContainerId: studyId; containerId: nodeId
 * STUDY_NODE_NETWORK_MODIFICATION: rootContainerId: nodeId; containerId: parentCompositeId
 * DIRECTORY_NETWORK_MODIFICATION: rootContainerId: directoryId; containerId: parentCompositeId
 *
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ReferenceContainer {
    @NonNull private UUID rootContainerId;
    @NonNull private UUID containerId;
}
