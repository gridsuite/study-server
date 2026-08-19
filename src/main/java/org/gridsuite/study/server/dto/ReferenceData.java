/*
  Copyright (c) 2026, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import java.util.UUID;

/**
 * One occurrence of a network modification referencing a shared composite modification stored in directory-server.
 *
 * @param modificationUuid uuid of the modification-reference itself
 * @param referenceId uuid of the referenced shared composite in directory-server
 * @param containerId uuid of the composite containing the reference, null if the modification-reference is at the root level
 * @author RTE
 */
public record ReferenceData(UUID modificationUuid, UUID referenceId, UUID containerId) {
}
