/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import com.powsybl.loadflow.LoadFlowParameters;
import lombok.*;

import javax.persistence.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Jacques Borsenberger <Jacques.Borsenberger at rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "loadFlowParameters")
public class LoadFlowParametersEntity {

    public LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode voltageInitMode,
                                    boolean transformerVoltageControlOn, boolean noGeneratorReactiveLimits,
                                    boolean phaseShifterRegulationOn, boolean twtSplitShuntAdmittance,
                                    boolean simulShunt, boolean readSlackBus, boolean writeSlackBus, boolean dc,
                                    boolean distributedSlack, LoadFlowParameters.BalanceType balanceType) {
        this(null, voltageInitMode, transformerVoltageControlOn, noGeneratorReactiveLimits, phaseShifterRegulationOn, twtSplitShuntAdmittance,
                simulShunt, readSlackBus, writeSlackBus, dc, distributedSlack, balanceType);
    }

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "voltageInitMode")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.VoltageInitMode voltageInitMode;

    @Column(name = "transformerVoltageControlOn")
    private boolean transformerVoltageControlOn;

    @Column(name = "noGeneratorReactiveLimits")
    private boolean noGeneratorReactiveLimits;

    @Column(name = "phaseShifterRegulationOn")
    private boolean phaseShifterRegulationOn;

    @Column(name = "twtSplitShuntAdmittance")
    private boolean twtSplitShuntAdmittance;

    @Column(name = "simulShunt")
    private boolean simulShunt;

    @Column(name = "readSlackBus")
    private boolean readSlackBus;

    @Column(name = "writeSlackBus")
    private boolean writeSlackBus;

    @Column(name = "dc")
    private boolean dc;

    @Column(name = "distributedSlack")
    private boolean distributedSlack;

    @Column(name = "balanceType")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.BalanceType balanceType;
}
