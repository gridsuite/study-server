/**
 * Copyright (c) 2023 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LimitViolationInfos {

    private String subjectId;

    private Double limit;

    private String limitName;

    private Integer acceptableDuration;

    private Double value;

    private String side;

    private String limitType;

}
