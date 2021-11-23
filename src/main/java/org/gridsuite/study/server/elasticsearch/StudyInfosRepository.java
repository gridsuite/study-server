/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@ConditionalOnExpression("'${spring.data.elasticsearch.enabled:false}' == 'true'")
@Lazy
public interface StudyInfosRepository extends ElasticsearchRepository<CreatedStudyBasicInfos, String> {

    Page<CreatedStudyBasicInfos> findById(UUID id, Pageable pageable);

    void deleteById(UUID uuid);
}
