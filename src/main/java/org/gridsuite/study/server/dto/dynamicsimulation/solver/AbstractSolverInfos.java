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
public abstract class AbstractSolverInfos implements SolverInfos {

    public static final String F_NORM_TOL_ALG = "fNormTolAlg";
    public static final String F_NORM_TOL_ALG_J = "fNormTolAlgJ";
    public static final String F_NORM_TOL_ALG_INIT = "fNormTolAlgInit";

    // Important note: must using @JsonProperty to precise property's name when serialize/deserialize
    // fields which begin by a minuscule following by a majuscule, for example 'hMxxx', otherwise jackson
    // mapper will serialize as 'hmxxx' by default

    private String id;
    private SolverTypeInfos type;

    @JsonProperty(F_NORM_TOL_ALG)
    private double fNormTolAlg;

    private double initialAddTolAlg;

    private double scStepTolAlg;

    private double mxNewTStepAlg;

    private int msbsetAlg;

    private int mxIterAlg;

    private int printFlAlg;

    @JsonProperty(F_NORM_TOL_ALG_J)
    private double fNormTolAlgJ;

    private double initialAddTolAlgJ;

    private double scStepTolAlgJ;

    private double mxNewTStepAlgJ;

    private int msbsetAlgJ;

    private int mxIterAlgJ;

    private int printFlAlgJ;

    @JsonProperty(F_NORM_TOL_ALG_INIT)
    private double fNormTolAlgInit;

    private double initialAddTolAlgInit;

    private double scStepTolAlgInit;

    private double mxNewTStepAlgInit;

    private int msbsetAlgInit;

    private int mxIterAlgInit;

    private int printFlAlgInit;

    private int maximumNumberSlowStepIncrease;

    private double minimalAcceptableStep;

}
