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
public class ReferenceId {
    @NonNull private UUID rootContainerId;
    @NonNull private UUID containerId;
}
