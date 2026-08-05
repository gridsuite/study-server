/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.computation;

import lombok.Builder;

import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Builder
public record StudyCreationParameterUUIDs(ComputationParameterUUIDs computationParameterUUIDs,
                                          UUID networkVisualizationParametersUuid,
                                          UUID spreadsheetConfigCollectionUuid,
                                          UUID workspacesConfigUuid) {
}
