/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.ClientConfiguration.TerminalClientConfigurationBuilder;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.net.InetSocketAddress;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

/**
 * A class to configure DB elasticsearch client for indexation
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

@Configuration
@EnableElasticsearchRepositories
public class ESConfig extends AbstractElasticsearchConfiguration {

    @Value("#{'${spring.data.elasticsearch.embedded:false}' ? 'localhost' : '${spring.data.elasticsearch.host}'}")
    private String esHost;

    @Value("#{'${spring.data.elasticsearch.embedded:false}' ? '${spring.data.elasticsearch.embedded.port:}' : '${spring.data.elasticsearch.port}'}")
    private int esPort;

    @Value("${spring.data.elasticsearch.client.timeout:60}")
    int timeout;

    @Value("${spring.data.elasticsearch.username:#{null}}")
    private Optional<String> username;

    @Value("${spring.data.elasticsearch.password:#{null}}")
    private Optional<String> password;

    @Bean
    @Override
    @SuppressWarnings("squid:S2095")
    public RestHighLevelClient elasticsearchClient() {
        TerminalClientConfigurationBuilder clientConfiguration = ClientConfiguration.builder()
            .connectedTo(InetSocketAddress.createUnresolved(esHost, esPort))
            .withConnectTimeout(timeout * 1000L).withSocketTimeout(timeout * 1000L);

        if (username.isPresent() && password.isPresent()) {
            clientConfiguration = clientConfiguration.withBasicAuth(username.get(), password.get());
        }

        return RestClients.create(clientConfiguration.build()).rest();
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
