/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow.impl;

import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Component
public class NodeEntityActionChecker extends AbstractActionChecker<NodeEntity> {

    public static final String LOAD_FLOW_MUST_RUN_SUCCESSFULLY_BEFORE_RUNNING_DYNAMIC_SIMULATION = "Load flow must run successfully before running dynamic simulation";
    public static final String DYNAMIC_SIMULATION_IS_NOT_RUNNING = "Dynamic simulation is not running";
    public static final String DYNAMIC_SIMULATION_RESULT_NOT_FOUND = "Dynamic simulation result not found";

    private final NetworkModificationTreeService networkModificationTreeService;
    private final DynamicSimulationService dynamicSimulationService;

    public NodeEntityActionChecker(NetworkModificationTreeService networkModificationTreeService,
                                   DynamicSimulationService dynamicSimulationService) {
        super();
        this.networkModificationTreeService = networkModificationTreeService;
        this.dynamicSimulationService = dynamicSimulationService;
    }

    @Override
    protected void doInitActionRuleMap() {
        addActionRule(NodeEntityAction.RUN_DYNAMIC_SIMULATION_ACTION, this::canRun);
        addActionRule(NodeEntityAction.STOP_DYNAMIC_SIMULATION_ACTION, this::canStop);
    }

    private String canRun(NodeEntity nodeEntity) {
        Optional<LoadFlowStatus> loadFlowStatusOpt = networkModificationTreeService.getLoadFlowStatus(nodeEntity.getIdNode());
        if (loadFlowStatusOpt.isPresent() && loadFlowStatusOpt.get() != LoadFlowStatus.CONVERGED) {
            return LOAD_FLOW_MUST_RUN_SUCCESSFULLY_BEFORE_RUNNING_DYNAMIC_SIMULATION;
        }

        return Strings.EMPTY; // means OK
    }

    private String canStop(NodeEntity nodeEntity) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getDynamicSimulationResultUuid(nodeEntity.getIdNode());

        if (resultUuidOpt.isEmpty()) {
            return DYNAMIC_SIMULATION_RESULT_NOT_FOUND;
        }

        String status = dynamicSimulationService.getStatus(resultUuidOpt.get());
        if (!Objects.equals(status, DynamicSimulationStatus.RUNNING.name())) {
            return DYNAMIC_SIMULATION_IS_NOT_RUNNING;
        }

        return Strings.EMPTY; // means OK
    }
}
