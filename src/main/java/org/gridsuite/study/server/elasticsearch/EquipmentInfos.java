/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.Document;


/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString(callSuper = true)
@ApiModel("Basic study attributes after creation succeeded ")
@Document(indexName = "study-server")
@TypeAlias(value = "EquipmentInfos")
public class EquipmentInfos {
    String id;

    String name;
}
