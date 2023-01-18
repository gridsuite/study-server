/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow;

import java.util.List;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface ActionChecker<T> {

    /**
     * Check an action on a given entity is allowed to execute
     * @param action
     * @param entity
     * @return a reason message, otherwise return empty that means ok
     */
    String canExecute(Action action, T entity);

    /**
     * get all available actions on a given entity
     * @param entity
     * @return a list of enable actions or an empty list
     */
    List<Action> getAvailableActions(T entity);
}
