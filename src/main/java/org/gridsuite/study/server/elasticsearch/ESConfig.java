/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

/**
 * A class to configure DB elasticsearch client for indexation
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

public final class ESConfig {
    private ESConfig() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // It's not simple SPEL but this syntax is managed by both ES and Spring
    public static final String STUDY_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}studies";
    public static final String EQUIPMENTS_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}equipments";
    public static final String TOMBSTONED_EQUIPMENTS_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}tombstoned-equipments";
}
