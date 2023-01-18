/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow.impl;

import org.gridsuite.study.server.service.workflow.Action;

public enum NodeEntityAction implements Action {

    RUN_DYNAMIC_SIMULATION_ACTION("Run a dynamic simulation");

    private String description;

    NodeEntityAction(String description) {
        this.description = description;
        Actions.getInstance().register(this);
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getName() {
        return this.name();
    }
}
