/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow;

import joptsimple.internal.Strings;
import org.gridsuite.study.server.StudyApplication;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.workflow.impl.NodeEntityActionChecker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.service.workflow.impl.NodeEntityAction.RUN_DYNAMIC_SIMULATION_ACTION;
import static org.gridsuite.study.server.service.workflow.impl.NodeEntityAction.STOP_DYNAMIC_SIMULATION_ACTION;
import static org.gridsuite.study.server.service.workflow.impl.NodeEntityActionChecker.DYNAMIC_SIMULATION_IS_NOT_RUNNING;
import static org.gridsuite.study.server.service.workflow.impl.NodeEntityActionChecker.DYNAMIC_SIMULATION_RESULT_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class WorkflowServiceNodeEntityActionCheckerTest {

    private static final String NODE_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);

    private static final String RESULT_UUID_STRING = "99999999-0000-0000-0000-000000000000";
    private static final UUID RESULT_UUID = UUID.fromString(RESULT_UUID_STRING);

    private NodeEntity nodeEntity;

    @MockBean
    NetworkModificationTreeService networkModificationTreeService;

    @MockBean
    DynamicSimulationService  dynamicSimulationService;

    @Autowired
    WorkflowService workflowService;

    @Before
    public void setupAll() {
        // mock the NodeEntity with NODE_UUID
        nodeEntity = new NodeEntity();
        nodeEntity.setIdNode(NODE_UUID);
    }

    @Test
    public void canExecuteRunDynamicSimulationGivenLoadFlowConverged() {
        // setup networkModificationTreeService mock for load flow converged
        given(networkModificationTreeService.getLoadFlowStatus(NODE_UUID)).willReturn(Optional.of(LoadFlowStatus.CONVERGED));

        // call method to be tested
        String message = workflowService.canExecute(RUN_DYNAMIC_SIMULATION_ACTION, nodeEntity);

        // check result
        assertEquals(Strings.EMPTY, message);
    }

    @Test
    public void canExecuteRunDynamicSimulationGivenLoadFlowNotConverged() {
        // setup networkModificationTreeService mock for load flow converged
        given(networkModificationTreeService.getLoadFlowStatus(NODE_UUID)).willReturn(Optional.of(LoadFlowStatus.DIVERGED));

        // call method to be tested
        String message = workflowService.canExecute(RUN_DYNAMIC_SIMULATION_ACTION, nodeEntity);

        // check result
        assertEquals(NodeEntityActionChecker.LOAD_FLOW_MUST_RUN_SUCCESSFULLY_BEFORE_RUNNING_DYNAMIC_SIMULATION, message);
    }

    @Test
    public void canExecuteStopDynamicSimulationGivenSimulationRunning() {
        // setup networkModificationTreeService mock for dynamic simulation result uuid
        given(networkModificationTreeService.getDynamicSimulationResultUuid(NODE_UUID)).willReturn(Optional.of(RESULT_UUID));
        // setup dynamicSimulationService mock for status of dynamic simulation result uuid
        given(dynamicSimulationService.getStatus(RESULT_UUID)).willReturn(DynamicSimulationStatus.RUNNING.name());

        // call method to be tested
        String message = workflowService.canExecute(STOP_DYNAMIC_SIMULATION_ACTION, nodeEntity);

        // check result
        assertEquals(Strings.EMPTY, message);
    }

    @Test
    public void canExecuteStopDynamicSimulationGivenSimulationNotRunning() {
        // setup networkModificationTreeService mock for dynamic simulation result uuid
        given(networkModificationTreeService.getDynamicSimulationResultUuid(NODE_UUID)).willReturn(Optional.of(RESULT_UUID));
        // setup dynamicSimulationService mock for status of dynamic simulation result uuid
        given(dynamicSimulationService.getStatus(RESULT_UUID)).willReturn(DynamicSimulationStatus.NOT_DONE.name());

        // call method to be tested
        String message = workflowService.canExecute(STOP_DYNAMIC_SIMULATION_ACTION, nodeEntity);

        // check result
        assertEquals(DYNAMIC_SIMULATION_IS_NOT_RUNNING, message);
    }

    @Test
    public void canExecuteStopDynamicSimulationGivenSimulationNotExist() {
        // setup networkModificationTreeService mock for dynamic simulation result uuid
        given(networkModificationTreeService.getDynamicSimulationResultUuid(NODE_UUID)).willReturn(Optional.empty());

        // call method to be tested
        String message = workflowService.canExecute(STOP_DYNAMIC_SIMULATION_ACTION, nodeEntity);

        // check result
        assertEquals(DYNAMIC_SIMULATION_RESULT_NOT_FOUND, message);
    }
}
