/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.solver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class SimSolverInfos extends AbstractSolverInfos {

    public static final String H_MIN = "hMin";
    public static final String H_MAX = "hMax";
    public static final String K_REDUCE_STEP = "kReduceStep";
    public static final String F_NORM_TOL = "fNormTol";

    // Important note: must using @JsonProperty to precise property's name when serialize/deserialize
    // fields which begin by a minuscule following by a majuscule, for example 'hMxxx', otherwise jackson
    // mapper will serialize as 'hmxxx' by default
    @JsonProperty(H_MIN)
    private double hMin;

    @JsonProperty(H_MAX)
    private double hMax;

    @JsonProperty(K_REDUCE_STEP)
    private double kReduceStep;

    private int maxNewtonTry;

    private String linearSolverName;

    @JsonProperty(F_NORM_TOL)
    private double fNormTol;

    private double initialAddTol;

    private double scStepTol;

    private double mxNewTStep;

    private int msbset;

    private int mxIter;

    private int printFl;

    private boolean optimizeAlgebraicResidualsEvaluations;

    private boolean skipNRIfInitialGuessOK;

    private boolean enableSilentZ;

    private boolean optimizeReInitAlgebraicResidualsEvaluations;

    private String minimumModeChangeTypeForAlgebraicRestoration;

    private String minimumModeChangeTypeForAlgebraicRestorationInit;

}
