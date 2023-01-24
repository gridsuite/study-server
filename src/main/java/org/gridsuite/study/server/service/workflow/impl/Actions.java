/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow.impl;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.service.workflow.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.gridsuite.study.server.StudyException.Type.ACTION_NOT_FOUND;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public final class Actions {

    private final List<Action> actionList = new ArrayList<>();

    private Actions() {
    }

    // Bill Pugh Singleton pattern
    private static class ActionsSingletonHelper {
        private static final Actions INSTANCE = new Actions();
    }

    public static Actions getInstance() {
        return ActionsSingletonHelper.INSTANCE;
    }

    public void register(Action action) {
        actionList.add(action);
    }

    public Action getAction(String actionName) {
        return actionList.stream().filter(action -> Objects.equals(actionName, action.getName()))
                .findFirst().orElseThrow(() -> new StudyException(ACTION_NOT_FOUND, String.format("Action %s not found", actionName)));
    }

}
