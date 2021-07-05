/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.annotations.ApiModel;
import lombok.*;
import java.util.UUID;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@AllArgsConstructor
@Getter
@Builder
@ApiModel("Basic study attributes")
public class DirectoryElement {
    UUID elementUuid;
    String elementName;
    String type;
    AccessRightsAttributes accessRights;
    String owner;
}

