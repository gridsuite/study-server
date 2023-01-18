/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow.impl;

import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NodeEntityActionChecker extends AbstractActionChecker<NodeEntity> {

    public static final String LOAD_FLOW_MUST_RUN_BEFORE_DYNAMIC_SIMULATION = "Load flow must run before dynamic simulation";
    private NetworkModificationTreeService networkModificationTreeService;

    public NodeEntityActionChecker(NetworkModificationTreeService networkModificationTreeService) {
        super();
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @Override
    protected void doInitActionRuleMap() {
        actionRuleMap.put(NodeEntityAction.RUN_DYNAMIC_SIMULATION_ACTION, this::canRun);
    }

    private String canRun(NodeEntity nodeEntity) {
        Optional<LoadFlowStatus> loadFlowStatusOpt = networkModificationTreeService.getLoadFlowStatus(nodeEntity.getIdNode());
        if (loadFlowStatusOpt.isPresent() && loadFlowStatusOpt.get() != LoadFlowStatus.CONVERGED) {
            return LOAD_FLOW_MUST_RUN_BEFORE_DYNAMIC_SIMULATION;
        }

        return Strings.EMPTY; // means OK
    }
}
