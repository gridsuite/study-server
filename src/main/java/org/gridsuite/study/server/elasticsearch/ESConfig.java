/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.net.InetSocketAddress;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * A class to configure DB elasticsearch client for indexation
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

@Configuration
@EnableElasticsearchRepositories
@Lazy
public class ESConfig extends AbstractElasticsearchConfiguration {

    @Value("#{'${spring.data.elasticsearch.embedded:false}' ? 'localhost' : '${spring.data.elasticsearch.host}'}")
    private String esHost;

    @Value("#{'${spring.data.elasticsearch.embedded:false}' ? '${spring.data.elasticsearch.embedded.port:}' : '${spring.data.elasticsearch.port}'}")
    private int esPort;

    @Bean
    @ConditionalOnExpression("'${spring.data.elasticsearch.enabled:false}' == 'true'")
    public StudyInfosService studyInfosServiceImpl(StudyInfosRepository studyInfosRepository, ElasticsearchOperations elasticsearchOperations) {
        return new StudyInfosServiceImpl(studyInfosRepository, elasticsearchOperations);
    }

    @Bean
    @ConditionalOnExpression("'${spring.data.elasticsearch.enabled:false}' == 'false'")
    public StudyInfosService studyInfosServiceMock() {
        return new StudyInfosServiceMock();
    }

    @Bean
    @Override
    public RestHighLevelClient elasticsearchClient() {
        ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(InetSocketAddress.createUnresolved(esHost, esPort))
                .build();

        return RestClients.create(clientConfiguration).rest();
    }

    @Bean
    @Override
    public ElasticsearchConverter elasticsearchEntityMapper(
            SimpleElasticsearchMappingContext elasticsearchMappingContext) {
        MappingElasticsearchConverter elasticsearchConverter = new MappingElasticsearchConverter(
                elasticsearchMappingContext);
        elasticsearchConverter.setConversions(elasticsearchCustomConversions());
        return elasticsearchConverter;
    }

    @Override
    public ElasticsearchCustomConversions elasticsearchCustomConversions() {
        return new ElasticsearchCustomConversions(Arrays.asList(DateToStringConverter.INSTANCE, StringToDateConverter.INSTANCE));
    }

    @WritingConverter
    enum DateToStringConverter implements Converter<ZonedDateTime, String> {
        INSTANCE;

        @Override
        public String convert(ZonedDateTime date) {
            return date.format(DateTimeFormatter.ISO_DATE_TIME);
        }
    }

    @ReadingConverter
    enum StringToDateConverter implements Converter<String, ZonedDateTime> {
        INSTANCE;

        @Override
        public ZonedDateTime convert(String s) {
            return ZonedDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME);
        }
    }
}
