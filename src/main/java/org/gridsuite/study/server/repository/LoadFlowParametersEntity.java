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
import java.util.Set;
import java.util.UUID;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Jacques Borsenberger <Jacques.Borsenberger at rte-france.com>
 */

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "loadFlowParameters")
public class LoadFlowParametersEntity {

    public LoadFlowParametersEntity(LoadFlowParameters.VoltageInitMode voltageInitMode,
                                    boolean transformerVoltageControlOn, boolean useReactiveLimits,
                                    boolean phaseShifterRegulationOn, boolean twtSplitShuntAdmittance,
                                    boolean shuntCompensatorVoltageControlOn, boolean readSlackBus, boolean writeSlackBus, boolean dc,
                                    boolean distributedSlack, LoadFlowParameters.BalanceType balanceType, boolean dcUseTransformerRatio,
                                    Set<String> countriesToBalance, LoadFlowParameters.ConnectedComponentMode connectedComponentMode,
                                    boolean hvdcAcEmulation) {
        this(null, voltageInitMode, transformerVoltageControlOn, useReactiveLimits, phaseShifterRegulationOn, twtSplitShuntAdmittance,
                shuntCompensatorVoltageControlOn, readSlackBus, writeSlackBus, dc, distributedSlack, balanceType, dcUseTransformerRatio, countriesToBalance, connectedComponentMode, hvdcAcEmulation);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "voltageInitMode")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.VoltageInitMode voltageInitMode;

    @Column(name = "transformerVoltageControlOn", columnDefinition = "boolean default false")
    private boolean transformerVoltageControlOn;

    @Column(name = "useReactiveLimits", columnDefinition = "boolean default true")
    private boolean useReactiveLimits;

    @Column(name = "phaseShifterRegulationOn", columnDefinition = "boolean default false")
    private boolean phaseShifterRegulationOn;

    @Column(name = "twtSplitShuntAdmittance", columnDefinition = "boolean default false")
    private boolean twtSplitShuntAdmittance;

    @Column(name = "shuntCompensatorVoltageControlOn", columnDefinition = "boolean default false")
    private boolean shuntCompensatorVoltageControlOn;

    @Column(name = "readSlackBus", columnDefinition = "boolean default true")
    private boolean readSlackBus;

    @Column(name = "writeSlackBus", columnDefinition = "boolean default false")
    private boolean writeSlackBus;

    @Column(name = "dc", columnDefinition = "boolean default false")
    private boolean dc;

    @Column(name = "distributedSlack", columnDefinition = "boolean default true")
    private boolean distributedSlack;

    @Column(name = "balanceType")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.BalanceType balanceType;

    @Column(name = "dcUseTransformerRatio", columnDefinition = "boolean default true")
    private boolean dcUseTransformerRatio;

    @Column(name = "countriesToBalance")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "loadFlowParametersEntity_countriesToBalance_fk1"),
            indexes = {@Index(name = "loadFlowParametersEntity_countriesToBalance_idx1",
                    columnList = "load_flow_parameters_entity_id")})
    private Set<String> countriesToBalance;

    @Column(name = "connectedComponentMode")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.ConnectedComponentMode connectedComponentMode;

    @Column(name = "hvdcAcEmulation", columnDefinition = "boolean default true")
    private boolean hvdcAcEmulation;
}
