/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.study.server.dto.voltageinit.FilterEquipments;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SensibilityAnalysisHvdcParameterInfos {

    private String sensitivityType;

    List<FilterEquipments> monitoredBranches;

    List<FilterEquipments> hvdcs;

    List<FilterEquipments> contingencies;
}
