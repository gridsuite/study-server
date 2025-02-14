/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.assertions;

import org.assertj.core.util.CheckReturnValue;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisParametersInfos;

/**
 *  @author Tristan Chuine <tristan.chuine at rte-france.com>
 * {@link org.assertj.core.api.Assertions Assertions} completed with our custom assertions classes.
 */
public class Assertions extends org.assertj.core.api.Assertions {
    @CheckReturnValue
    public static <T extends DynamicSecurityAnalysisParametersInfos> DTOAssert<DynamicSecurityAnalysisParametersInfos> assertThat(T actual) {
        return new DTOAssert<>(actual);
    }

}
