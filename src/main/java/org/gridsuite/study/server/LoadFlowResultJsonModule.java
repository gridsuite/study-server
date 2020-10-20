/**
 * Copyright (c) 2018, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.json.LoadFlowResultDeserializer;
import com.powsybl.loadflow.json.LoadFlowResultSerializer;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
public class LoadFlowResultJsonModule extends SimpleModule {

    public LoadFlowResultJsonModule() {
        addDeserializer(LoadFlowResult.class, new LoadFlowResultDeserializer());
        addSerializer(LoadFlowResult.class, new LoadFlowResultSerializer());
    }
}
