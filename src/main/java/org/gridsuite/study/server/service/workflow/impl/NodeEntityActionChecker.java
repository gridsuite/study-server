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

@Component
public class NodeEntityActionChecker extends AbstractActionChecker<NodeEntity> {

    public static final String LOAD_FLOW_MUST_RUN_SUCCESSFULLY_BEFORE_RUNNING_DYNAMIC_SIMULATION = "Load flow must run successfully before running dynamic simulation";
    private NetworkModificationTreeService networkModificationTreeService;
    private DynamicSimulationService dynamicSimulationService;

    public NodeEntityActionChecker(NetworkModificationTreeService networkModificationTreeService,
                                   DynamicSimulationService dynamicSimulationService) {
        super();
        this.networkModificationTreeService = networkModificationTreeService;
        this.dynamicSimulationService = dynamicSimulationService;
    }

    @Override
    protected void doInitActionRuleMap() {
        actionRuleMap.put(NodeEntityAction.RUN_DYNAMIC_SIMULATION_ACTION, this::canRun);
        actionRuleMap.put(NodeEntityAction.STOP_DYNAMIC_SIMULATION_ACTION, this::canStop);
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
            return "Dynamic simulation result not found";
        }

        String status = dynamicSimulationService.getStatus(resultUuidOpt.get());
        if (!Objects.equals(status, DynamicSimulationStatus.RUNNING)) {
            return "Dynamic simulation is not running";
        }

        return Strings.EMPTY; // means OK
    }
}
