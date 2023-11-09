/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.solver;

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
    private String id;
    private SolverTypeInfos type;

    private double fnormtolAlg;

    private double initialaddtolAlg;

    private double scsteptolAlg;

    private double mxnewtstepAlg;

    private int msbsetAlg;

    private int mxiterAlg;

    private int printflAlg;

    private double fnormtolAlgJ;

    private double initialaddtolAlgJ;

    private double scsteptolAlgJ;

    private double mxnewtstepAlgJ;

    private int msbsetAlgJ;

    private int mxiterAlgJ;

    private int printflAlgJ;

    private double fnormtolAlgInit;

    private double initialaddtolAlgInit;

    private double scsteptolAlgInit;

    private double mxnewtstepAlgInit;

    private int msbsetAlgInit;

    private int mxiterAlgInit;

    private int printflAlgInit;

    private int maximumNumberSlowStepIncrease;

    private double minimalAcceptableStep;

}
