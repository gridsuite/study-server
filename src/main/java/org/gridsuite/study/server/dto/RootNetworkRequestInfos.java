/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Builder
@Getter
public class RootNetworkRequestInfos {
    @Id
    private UUID id;

    private UUID studyUuid;

    private String userId;
}
