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
public class IdaSolverInfos extends AbstractSolverInfos {
    private int order;
    private double initStep;
    private double minStep;
    private double maxStep;
    private double absAccuracy;
    private double relAccuracy;

    /*
    @Override
    protected void writeJsonFields(JsonGenerator generator) {
        Objects.requireNonNull(generator);
        try {
            generator.writeFieldName("order");
            generator.writeNumber(order);
            generator.writeFieldName("initStep");
            generator.writeNumber(initStep);
            generator.writeFieldName("minStep");
            generator.writeNumber(minStep);
            generator.writeFieldName("maxStep");
            generator.writeNumber(maxStep);
            generator.writeFieldName("absAccuracy");
            generator.writeNumber(absAccuracy);
            generator.writeFieldName("relAccuracy");
            generator.writeNumber(relAccuracy);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    } */
}
