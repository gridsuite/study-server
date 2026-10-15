/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.elasticsearch;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosRepository;
import org.gridsuite.study.server.elasticsearch.StudyInfosRepository;
import org.gridsuite.study.server.elasticsearch.TombstonedEquipmentInfosRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.TestPropertySource;
import java.lang.annotation.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@TestPropertySource(properties = {
    DisableElasticsearch.DISABLE_PROPERTY_NAME + "=true",
    "spring.autoconfigure.exclude=org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchRepositoriesAutoConfiguration",
})
@Import(DisableElasticsearch.MockConfig.class)
public @interface DisableElasticsearch {
    String DISABLE_PROPERTY_NAME = "test.disable.data-elasticsearch";

    @TestConfiguration(proxyBeanMethods = false)
    class MockConfig {
        @Bean
        public EmbeddedElasticsearch embeddedElasticsearch() {
            return Mockito.mock(EmbeddedElasticsearch.class);
        }

        @Bean
        public EquipmentInfosRepository equipmentInfosRepository() {
            return Mockito.mock(EquipmentInfosRepository.class);
        }

        @Bean
        public StudyInfosRepository studyInfosRepository() {
            return Mockito.mock(StudyInfosRepository.class);
        }

        @Bean
        public TombstonedEquipmentInfosRepository tombstonedEquipmentInfosRepository() {
            return Mockito.mock(TombstonedEquipmentInfosRepository.class);
        }

        @Bean
        public ElasticsearchOperations elasticsearchOperations() {
            return Mockito.mock(ElasticsearchOperations.class);
        }

        @Bean
        public Rest5Client restClient() {
            return Mockito.mock(Rest5Client.class);
        }
    }
}
