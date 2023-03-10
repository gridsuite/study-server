/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class SimSolverInfos extends AbstractSolverInfos {

    // Important note: must using @JsonProperty to precise property's name when serialize/deserialize
    // fields which begin by a minuscule following by a majuscule, for example 'hMxxx', otherwise jackson
    // mapper will serialize as 'hmxxx' by default
    @JsonProperty("hMin")
    private double hMin;

    @JsonProperty("hMax")
    private double hMax;

    @JsonProperty("kReduceStep")
    private double kReduceStep;

    @JsonProperty("nEff")
    private int nEff;

    @JsonProperty("nDeadband")
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
