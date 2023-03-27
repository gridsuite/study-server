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
import org.springframework.boot.test.mock.mockito.MockBeans;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@MockBeans({@MockBean(EmbeddedElasticsearch.class), @MockBean(EquipmentInfosRepository.class),
    @MockBean(StudyInfosRepository.class), @MockBean(TombstonedEquipmentInfosRepository.class)})
public @interface DisableElasticsearch {
}
