/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsecurityanalysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DynamicSecurityAnalysisParametersInfos {
    private UUID id;

    private String provider;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double scenarioDuration;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double contingenciesStartTime;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<UUID> contingencyListIds;

}
