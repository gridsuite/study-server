/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow.impl;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.service.workflow.Action;
import org.gridsuite.study.server.service.workflow.ActionChecker;
import org.gridsuite.study.server.service.workflow.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

import static org.gridsuite.study.server.StudyException.Type.ACTION_CHECKER_NOT_FOUND;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class WorkflowServiceImpl implements WorkflowService<Object> {

    private NodeEntityActionChecker nodeEntityActionChecker;

    public WorkflowServiceImpl(NodeEntityActionChecker nodeEntityActionChecker) {
        this.nodeEntityActionChecker = nodeEntityActionChecker;
    }

    @Override
    public String canExecute(Action action, Object entity) {
        Assert.notNull(action, "action is required");
        Assert.notNull(entity, "entity is required");

        ActionChecker actionChecker = getActionChecker(entity);

        if (actionChecker == null) {
            throw new StudyException(ACTION_CHECKER_NOT_FOUND, String.format("Workflow action checker not found for entity %s", entity));
        }

        return actionChecker.canExecute(action, entity);
    }

    @Override
    public List<Action> getAvailableActions(Object entity) {
        Assert.notNull(entity, "entity is required");

        ActionChecker actionChecker = getActionChecker(entity);

        if (actionChecker == null) {
            throw new StudyException(ACTION_CHECKER_NOT_FOUND, String.format("Workflow action checker not found for entity %s", entity));
        }

        return actionChecker.getAvailableActions(entity);
    }

    @Override
    public ActionChecker getActionChecker(Object entity) {

        if (entity instanceof NodeEntity) {
            return nodeEntityActionChecker;
        }

        // can add other checkers later for other entities

        return null;
    }
}
