/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import java.util.List;
import java.util.Map;

import org.gridsuite.study.server.StudyConstants;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Caroline Jeandat <caroline.jeandat at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class SldRequestInfos {
    private boolean useName;
    private boolean centerLabel;
    private boolean diagonalLabel;
    private boolean topologicalColoring;
    private String componentLibrary;
    private StudyConstants.SldDisplayMode sldDisplayMode;
    private String language;
    private String substationLayout;
    private List<CurrentLimitViolationInfos> currentLimitViolationInfos;
    private List<Map<String, Object>> baseVoltagesConfigInfos;
    private Map<String, Double> busIdToIccValues;
}
