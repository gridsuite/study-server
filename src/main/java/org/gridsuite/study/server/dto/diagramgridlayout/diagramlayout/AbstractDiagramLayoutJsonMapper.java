/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * MixIn interface for AbstractDiagramLayout to define polymorphic serialization
 * without creating circular dependencies in the class hierarchy.
 * This approach avoids circular dependencies that would occur with @JsonSubTypes
 * directly on the abstract class.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = NetworkAreaDiagramLayoutDetails.class, name = "network-area-diagram-details"),
    @JsonSubTypes.Type(value = NetworkAreaDiagramLayout.class, name = "network-area-diagram"),
    @JsonSubTypes.Type(value = SubstationDiagramLayout.class, name = "substation"),
    @JsonSubTypes.Type(value = VoltageLevelDiagramLayout.class, name = "voltage-level"),
    @JsonSubTypes.Type(value = MapLayout.class, name = "map"),
})
public interface AbstractDiagramLayoutJsonMapper {
}
