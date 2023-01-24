/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.workflow.impl;

import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.service.workflow.Action;
import org.gridsuite.study.server.service.workflow.ActionChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.gridsuite.study.server.StudyException.Type.ACTION_RULE_NOT_FOUND;

public abstract class AbstractActionChecker<T> implements ActionChecker<T> {

    private final Map<Action, Function<T, String>> actionRuleMap = new HashMap<>();

    protected AbstractActionChecker() {
        initActionRuleMap();
    }

    protected abstract void doInitActionRuleMap();

    private void initActionRuleMap() {
        actionRuleMap.clear();
        doInitActionRuleMap();
    }

    public void addActionRule(Action action, Function<T, String> rule) {
        actionRuleMap.put(action, rule);
    }

    @Override
    public String canExecute(Action action, T entity) {
        Function<T, String> rule = actionRuleMap.get(action);

        if (rule == null) {
            throw new StudyException(ACTION_RULE_NOT_FOUND, String.format("Workflow action rule not found for action %s - entity %s", action, entity));
        }

        return rule.apply(entity);
    }

    @Override
    public List<Action> getAvailableActions(T entity) {

        List<Action> actions = new ArrayList<>();
        actionRuleMap.forEach((action, rule) -> {
            if (Strings.isBlank(rule.apply(entity))) {
                actions.add(action);
            }
        });

        return actions;
    }
}
