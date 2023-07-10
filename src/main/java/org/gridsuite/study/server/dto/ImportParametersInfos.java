/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.gridsuite.study.server.repository.ImportParametersEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@AllArgsConstructor
@Getter
public class ImportParametersInfos extends HashMap<String, String> {
    public ImportParametersInfos(Map<String, String> values) {
        super(values);
    }

    public ImportParametersEntity toEntity() {
        return new ImportParametersEntity(null, this);
    }
}
