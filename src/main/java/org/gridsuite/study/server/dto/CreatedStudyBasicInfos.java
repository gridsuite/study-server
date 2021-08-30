/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.Document;


/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString(callSuper = true)
@Schema(description = "Basic study attributes after creation succeeded ")
@Document(indexName = "study-server")
@TypeAlias(value = "StudyInfos")
public class CreatedStudyBasicInfos extends BasicStudyInfos {
    String caseFormat;

    String description;
}
