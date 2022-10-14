/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public final class ShortCircuitAnalysisService {

    private ShortCircuitAnalysisService() { }

    public static ShortCircuitParametersEntity toEntity(ShortCircuitParameters parameters) {
        Objects.requireNonNull(parameters);
        return new ShortCircuitParametersEntity(parameters.isWithLimitViolations(),
                parameters.isWithVoltageMap(),
                parameters.isWithFeederResult(),
                parameters.getStudyType(),
                parameters.getMinVoltageDropProportionalThreshold());
    }

    public static ShortCircuitParameters fromEntity(ShortCircuitParametersEntity entity) {
        Objects.requireNonNull(entity);
        return newShortCircuitParameters(entity.getStudyType(), entity.getMinVoltageDropProportionalThreshold(), entity.isWithFeederResult(), entity.isWithLimitViolations(), entity.isWithVoltageMap());
    }

    public static ShortCircuitParameters copy(ShortCircuitParameters shortCircuitParameters) {
        return newShortCircuitParameters(shortCircuitParameters.getStudyType(), shortCircuitParameters.getMinVoltageDropProportionalThreshold(), shortCircuitParameters.isWithFeederResult(), shortCircuitParameters.isWithLimitViolations(), shortCircuitParameters.isWithVoltageMap());
    }

    private static ShortCircuitParameters newShortCircuitParameters(StudyType studyType, double minVoltageDropProportionalThreshold, boolean withFeederResult, boolean withLimitViolations, boolean withVoltageMap) {
        ShortCircuitParameters shortCircuitParametersCopy = new ShortCircuitParameters();
        shortCircuitParametersCopy.setStudyType(studyType);
        shortCircuitParametersCopy.setMinVoltageDropProportionalThreshold(minVoltageDropProportionalThreshold);
        shortCircuitParametersCopy.setWithFeederResult(withFeederResult);
        shortCircuitParametersCopy.setWithLimitViolations(withLimitViolations);
        shortCircuitParametersCopy.setWithVoltageMap(withVoltageMap);
        return shortCircuitParametersCopy;
    }

    public static ShortCircuitParameters getDefaultShortCircuitParameters() {
        return newShortCircuitParameters(StudyType.TRANSIENT, 20, true, true, true);
    }
}
