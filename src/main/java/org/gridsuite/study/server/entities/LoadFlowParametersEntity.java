/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.entities;

import com.powsybl.loadflow.LoadFlowParameters;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Chamseddoine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Jacques Borsenberger <Jacques.Borsenberger at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table("loadFlowParameters")
public class LoadFlowParametersEntity implements Serializable {
    @Id
    @Column("id")
    private UUID id;

    private LoadFlowParameters.VoltageInitMode voltageInitMode;

    private boolean transformerVoltageControlOn;

    private boolean noGeneratorReactiveLimits;

    private boolean phaseShifterRegulationOn;

    private boolean twtSplitShuntAdmittance;

    private boolean simulShunt;

    private boolean readSlackBus;

    private boolean writeSlackBus;

    private boolean dc;

    private boolean distributedSlack;

    private LoadFlowParameters.BalanceType balanceType;
}
