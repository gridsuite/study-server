/**
 * Copyright (c) 2021 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@AllArgsConstructor
@Getter
public class DeleteStudyInfos {

    private List<RootNetworkInfos> rootNetworkInfosList;

    private List<UUID> modificationGroupUuids;
}
