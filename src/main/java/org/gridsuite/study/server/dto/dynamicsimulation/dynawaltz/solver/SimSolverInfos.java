/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class SimSolverInfos extends AbstractSolverInfos {
    private double hMin;
    private double hMax;
    private double kReduceStep;
    private double nEff;
    private int nDeadband;
    private int maxRootRestart;
    private int maxNewtonTry;
    private String linearSolverName;
    private boolean recalculateStep;

    /*
    @Override
    protected void writeJsonFields(JsonGenerator generator) {
        Objects.requireNonNull(generator);
        try {
            generator.writeFieldName("hMin");
            generator.writeNumber(hMin);
            generator.writeFieldName("hMax");
            generator.writeNumber(hMax);
            generator.writeFieldName("kReduceStep");
            generator.writeNumber(kReduceStep);
            generator.writeFieldName("nEff");
            generator.writeNumber(nEff);
            generator.writeFieldName("nDeadband");
            generator.writeNumber(nDeadband);
            generator.writeFieldName("maxRootRestart");
            generator.writeNumber(maxRootRestart);
            generator.writeFieldName("maxNewtonTry");
            generator.writeNumber(maxNewtonTry);
            generator.writeFieldName("linearSolverName");
            generator.writeString(linearSolverName);
            generator.writeFieldName("recalculateStep");
            generator.writeBoolean(recalculateStep);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    } */
}
