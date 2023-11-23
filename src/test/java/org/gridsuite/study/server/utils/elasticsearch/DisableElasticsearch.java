/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.elasticsearch;

import org.gridsuite.study.server.elasticsearch.EquipmentInfosRepository;
import org.gridsuite.study.server.elasticsearch.StudyInfosRepository;
import org.gridsuite.study.server.elasticsearch.TombstonedEquipmentInfosRepository;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@MockBean(EmbeddedElasticsearch.class)
@MockBean(EquipmentInfosRepository.class)
@MockBean(StudyInfosRepository.class)
@MockBean(TombstonedEquipmentInfosRepository.class)
@TestPropertySource(properties = DisableElasticsearch.PROPERTY_NAME+"=true")
public @interface DisableElasticsearch {
    String PROPERTY_NAME = "test.disable.elasticsearch";
}
