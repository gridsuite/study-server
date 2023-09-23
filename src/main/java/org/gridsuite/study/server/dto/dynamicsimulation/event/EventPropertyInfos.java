/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.utils.PropertyType;

import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EventPropertyInfos {

    @JsonProperty("uuid")
    private UUID id;

    private String name;

    private String value;

    private PropertyType type;
}
