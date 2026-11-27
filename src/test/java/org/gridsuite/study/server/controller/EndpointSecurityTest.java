/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.controller;

import com.powsybl.ws.commons.security.restclient.AbstractEndpointSecurityTest;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SpringBootTest
class EndpointSecurityTest extends AbstractEndpointSecurityTest {

    protected List<String> getControllerPackages() {
        return List.of(
            "org.gridsuite.study.server.controller",
            "org.gridsuite.study.server.controller.asymmetricalload",
            "org.gridsuite.study.server.controller.dynamicmargincalculation",
            "org.gridsuite.study.server.controller.dynamicsecurityanalysis",
            "org.gridsuite.study.server.controller.dynamicsimulation",
            "org.gridsuite.study.server.controller.loadflow",
            "org.gridsuite.study.server.controller.logicalcontrols",
            "org.gridsuite.study.server.controller.pccmin",
            "org.gridsuite.study.server.controller.securityanalysis",
            "org.gridsuite.study.server.controller.sensitivityanalysis",
            "org.gridsuite.study.server.controller.shortcircuit",
            "org.gridsuite.study.server.controller.stateestimation",
            "org.gridsuite.study.server.controller.voltageinit"
            );
    }

    /**
     * Supervision endpoints are filtered by the gateway and are not directly accessible,
     * so we don't need to check permissions for them.
     */
    protected List<Class<?>> getExcludedControllers() {
        return List.of(SupervisionController.class);
    }
}
