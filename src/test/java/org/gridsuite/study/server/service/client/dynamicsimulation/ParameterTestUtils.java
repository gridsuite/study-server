/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.dynamicsimulation;

import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventPropertyInfos;
import org.gridsuite.study.server.utils.PropertyType;

import java.util.List;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public final class ParameterTestUtils {
    private ParameterTestUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static List<EventInfos> getEventInfosList() {
        return List.of(
                new EventInfos(null, null, "_BUS____1-BUS____5-1_AC", null, "Disconnect", List.of(
                        new EventPropertyInfos(null, "staticId", "_BUS____1-BUS____5-1_AC", PropertyType.STRING),
                        new EventPropertyInfos(null, "startTime", "10", PropertyType.FLOAT),
                        new EventPropertyInfos(null, "disconnectOnly", "TwoSides.TWO", PropertyType.ENUM)
                )),
                new EventInfos(null, null, "_BUS____1_TN", null, "FaultNode", List.of(
                        new EventPropertyInfos(null, "staticId", "_BUS____1_TN", PropertyType.STRING),
                        new EventPropertyInfos(null, "startTime", "20", PropertyType.FLOAT),
                        new EventPropertyInfos(null, "faultTime", "2", PropertyType.FLOAT),
                        new EventPropertyInfos(null, "rPu", "23", PropertyType.FLOAT),
                        new EventPropertyInfos(null, "xPu", "32", PropertyType.FLOAT)
                ))
        );
    }
}
