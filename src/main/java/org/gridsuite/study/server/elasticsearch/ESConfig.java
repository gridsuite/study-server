/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.elasticsearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * A class to configure DB elasticsearch client for indexation
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

@Configuration
@EnableElasticsearchRepositories
public class ESConfig extends ElasticsearchConfiguration {

    // It's not simple SPEL but this syntax is managed by both ES and Spring
    public static final String STUDY_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}studies";
    public static final String EQUIPMENTS_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}equipments";
    public static final String TOMBSTONED_EQUIPMENTS_INDEX_NAME = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}tombstoned-equipments";

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

    //It should work without having to specify the bean name but it doesn't. To investigate.
    @Bean(name = "elasticsearchClientConfiguration")
    @Override
    @SuppressWarnings("squid:S2095")
    @Nonnull
    public ClientConfiguration clientConfiguration() {
        var clientConfiguration = ClientConfiguration.builder()
                .connectedTo(esHost + ":" + esPort)
                .withConnectTimeout(timeout * 1000L).withSocketTimeout(timeout * 1000L);

        if (username.isPresent() && password.isPresent()) {
            clientConfiguration.withBasicAuth(username.get(), password.get());
        }

        return clientConfiguration.build();
    }
}
